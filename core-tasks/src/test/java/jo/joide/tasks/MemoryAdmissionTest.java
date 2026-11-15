package jo.joide.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jo.joide.platform.Clock;
import jo.joide.tasks.TestingTasks.EventLog;

/**
 * Admission de ressources : avec un budget artificiel, la somme des
 * revendications en cours ne dépasse jamais le budget ; une tâche
 * trop coûteuse pour la place restante attend la libération ; une
 * tâche exclusive s'exécute seule.
 *
 * <p>Les preuves sont structurelles : l'ordre des événements observés
 * suffit, sans fenêtre d'attente ni mesure de temps.</p>
 */
class MemoryAdmissionTest {

    private ExecutorService pool;
    private Clock clock;

    @BeforeEach
    void setUp() {
        pool = Executors.newFixedThreadPool(6);
        clock = Clock.system();
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    @Test
    void inFlightClaimsNeverExceedBudget() {
        // budget artificiel de 300 Mo : les tâches réclament 100 Mo
        // chacune, au plus trois simultanées ; on en lance vingt
        ResourceBudget budget = new ResourceBudget(8, 300);
        int taskCount = 20;
        TaskGraph.Builder builder = TaskGraph.builder();
        for (int i = 0; i < taskCount; i++) {
            builder.register(new TestingTasks.RecordingTask(
                    "mem" + i, "noop", TaskInputs.builder().build(),
                    TaskOutputs.builder().build(),
                    new ResourceClaim(0, 100, false)));
        }
        TaskGraph graph = builder.build();

        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        AtomicLong maxClaimedMemory = new AtomicLong();
        TaskScheduler scheduler = new TaskScheduler(pool, SchedulingMode.FAIL_FAST,
                BuildEvents.NOOP);
        List<String> names = graph.names();
        java.util.Map<String, TaskResult> results = scheduler.run(graph,
                TestingTasks.zeroPriorities(graph), budget, null,
                (task, cancel) -> {
                    int now = concurrent.incrementAndGet();
                    maxConcurrent.accumulateAndGet(now, Math::max);
                    maxClaimedMemory.accumulateAndGet(
                            budget.usedMemoryMb(), Math::max);
                    concurrent.decrementAndGet();
                    return new TaskResult(task.name(), task.taskType(),
                            TaskStatus.SUCCEEDED, List.of(), TaskTimings.ZERO, 0);
                });

        assertEquals(taskCount, results.size());
        for (String name : names) {
            assertEquals(TaskStatus.SUCCEEDED, results.get(name).status());
        }
        assertTrue(maxConcurrent.get() <= 3,
                "at most 3 tasks of 100 MB fit in a 300 MB budget, observed "
                        + maxConcurrent.get());
        assertTrue(maxClaimedMemory.get() <= 300,
                "claimed memory " + maxClaimedMemory.get()
                        + " MB must never exceed the 300 MB budget");
    }

    @Test
    void taskTooLargeForRemainingBudgetWaitsForRelease() throws Exception {
        ResourceBudget budget = new ResourceBudget(8, 100);
        CountDownLatch heavyStarted = new CountDownLatch(1);
        CountDownLatch releaseHeavy = new CountDownLatch(1);
        EventLog events = new EventLog(clock);

        TaskDefinition heavy = new TestingTasks.RecordingTask(
                "heavy", "noop", TaskInputs.builder().build(),
                TaskOutputs.builder().build(), new ResourceClaim(0, 80, false))
                .acts(context -> {
                    heavyStarted.countDown();
                    try {
                        releaseHeavy.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return TaskExecution.ok();
                });
        TaskDefinition small = new TestingTasks.RecordingTask(
                "small", "noop", TaskInputs.builder().build(),
                TaskOutputs.builder().build(), new ResourceClaim(0, 30, false));

        TaskGraph graph = TaskGraph.builder()
                .register(heavy)
                .register(small)
                .build();
        TaskScheduler scheduler = new TaskScheduler(pool, SchedulingMode.FAIL_FAST,
                events);

        Thread buildThread = new Thread(() -> scheduler.run(graph,
                TestingTasks.zeroPriorities(graph), budget, null,
                (task, cancel) -> {
                    try {
                        return TestingTasks.resultOf(task, task.execute(null));
                    } catch (InterruptedException interrupted) {
                        return new TaskResult(task.name(), task.taskType(),
                                TaskStatus.CANCELLED, List.of(),
                                TaskTimings.ZERO, 0);
                    }
                }));
        buildThread.start();

        // la tâche lourde (80 Mo) tient seule dans les 100 Mo : elle
        // démarre ; la légère (30 Mo) attend la libération
        assertTrue(heavyStarted.await(30, TimeUnit.SECONDS),
                "heavy task must start (it fits alone)");
        releaseHeavy.countDown();
        buildThread.join(TimeUnit.SECONDS.toMillis(30));

        assertTrue(events.hasFinished("small"),
                "small task must run after heavy releases its claim");
        assertTrue(events.finishedAt("heavy") <= events.startedAt("small"),
                "small must start only after heavy finished: heavy finished at "
                        + events.finishedAt("heavy") + ", small started at "
                        + events.startedAt("small"));
    }

    @Test
    void exclusiveTaskRunsAlone() throws Exception {
        ResourceBudget budget = new ResourceBudget(8, 10_000);
        CountDownLatch exclusiveStarted = new CountDownLatch(1);
        CountDownLatch releaseExclusive = new CountDownLatch(1);
        AtomicInteger othersDuringExclusive = new AtomicInteger();
        EventLog events = new EventLog(clock);

        TaskDefinition exclusive = new TestingTasks.RecordingTask(
                "exclusive", "noop", TaskInputs.builder().build(),
                TaskOutputs.builder().build(), new ResourceClaim(1, 64, true))
                .acts(context -> {
                    exclusiveStarted.countDown();
                    try {
                        releaseExclusive.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return TaskExecution.ok();
                });
        TaskGraph.Builder builder = TaskGraph.builder().register(exclusive);
        for (int i = 0; i < 5; i++) {
            builder.register(new TestingTasks.RecordingTask(
                    "other" + i, "noop", TaskInputs.builder().build(),
                    TaskOutputs.builder().build(), new ResourceClaim(1, 64, false))
                    .acts(context -> {
                        if (exclusiveStarted.getCount() == 0
                                && releaseExclusive.getCount() > 0) {
                            othersDuringExclusive.incrementAndGet();
                        }
                        return TaskExecution.ok();
                    }));
        }
        TaskGraph graph = builder.build();
        TaskScheduler scheduler = new TaskScheduler(pool, SchedulingMode.FAIL_FAST,
                events);

        Thread buildThread = new Thread(() -> scheduler.run(graph,
                TestingTasks.zeroPriorities(graph), budget, null,
                (task, cancel) -> {
                    try {
                        return TestingTasks.resultOf(task, task.execute(null));
                    } catch (InterruptedException interrupted) {
                        return new TaskResult(task.name(), task.taskType(),
                                TaskStatus.CANCELLED, List.of(),
                                TaskTimings.ZERO, 0);
                    }
                }));
        buildThread.start();

        assertTrue(exclusiveStarted.await(30, TimeUnit.SECONDS),
                "exclusive task must start");
        releaseExclusive.countDown();
        buildThread.join(TimeUnit.SECONDS.toMillis(30));

        assertEquals(0, othersDuringExclusive.get(),
                "no other task may run while the exclusive task holds "
                        + "the budget");
        assertEquals(graph.size(), events.startOrder().size(),
                "all tasks started");
        for (String name : graph.names()) {
            assertTrue(events.hasFinished(name), "finished: " + name);
        }
    }
}
