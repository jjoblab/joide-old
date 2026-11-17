package jo.joide.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jo.joide.platform.CancelToken;
import jo.joide.platform.Clock;
import jo.joide.tasks.TestingTasks.EventLog;

/**
 * Propriétés de l'ordonnanceur sur graphes aléatoires : jamais deux
 * tâches dépendantes simultanées, aucun interblocage, résultat
 * déterministe, annulation sans orpheline.
 *
 * <p>Chaque exécution passe par l'exécuteur turbulent à graine fixe
 * (perturbation des délais) ; les graines sont affichées dans les
 * messages d'échec. Le total des exécutions de l'ensemble des
 * méthodes dépasse mille (320 + 200 + 480 + 40), conformément à la
 * stratégie de tests de concurrence du projet.</p>
 */
class SchedulerPropertyTest {

    private static final long SEED = 20260115L;

    private ExecutorService pool;
    private ExecutorService watchdog;
    private Clock clock;

    @BeforeEach
    void setUp() {
        pool = Executors.newFixedThreadPool(4);
        watchdog = Executors.newSingleThreadExecutor();
        clock = Clock.system();
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        watchdog.shutdownNow();
    }

    /**
     * Lance un build dans un fil dédié avec garde-fou de temps : un
     * interblocage fait échouer le test au lieu de le suspendre.
     */
    private Map<String, TaskResult> runWithWatchdog(long seed, TaskGraph graph,
                                                    TaskScheduler scheduler,
                                                    ResourceBudget budget) {
        return runWithWatchdog(seed, graph, scheduler, budget,
                (task, cancel) -> new TaskResult(task.name(),
                        task.taskType(), TaskStatus.SUCCEEDED, List.of(),
                        TaskTimings.ZERO, 0));
    }

    private Map<String, TaskResult> runWithWatchdog(long seed, TaskGraph graph,
                                                    TaskScheduler scheduler,
                                                    ResourceBudget budget,
                                                    TaskScheduler.TaskStep step) {
        Future<Map<String, TaskResult>> future = watchdog.submit(() ->
                scheduler.run(graph, prioritiesZero(graph), budget, null,
                        step));
        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException stuck) {
            future.cancel(true);
            fail("build deadlocked (seed " + seed + "): " + stuck);
            return Map.of();
        } catch (Exception failure) {
            fail("build failed (seed " + seed + "): " + failure);
            return Map.of();
        }
    }

    private static Map<String, Long> prioritiesZero(TaskGraph graph) {
        Map<String, Long> priorities = new HashMap<>();
        for (String name : graph.names()) {
            priorities.put(name, 0L);
        }
        return priorities;
    }

    @Test
    void randomGraphsAlwaysComplete() {
        int totalRuns = 0;
        for (int round = 0; round < 160; round++) {
            TestingTasks.Generated generated = TestingTasks.randomDag(
                    SEED + round, 12, false);
            TaskGraph graph = generated.graph();
            for (int run = 0; run < 2; run++) {
                totalRuns++;
                ExecutorService turbulent = new TurbulentExecutor(pool,
                        SEED + round * 100 + run);
                TaskScheduler scheduler = new TaskScheduler(turbulent,
                        SchedulingMode.FAIL_FAST, BuildEvents.NOOP);
                Map<String, TaskResult> results = runWithWatchdog(
                        SEED + round * 100 + run, graph, scheduler,
                        new ResourceBudget(8, 4096));
                assertEquals(graph.size(), results.size(),
                        "every task must have a result");
                for (TaskResult result : results.values()) {
                    assertEquals(TaskStatus.SUCCEEDED, result.status(),
                            "task " + result.name() + " should succeed");
                }
            }
        }
        assertTrue(totalRuns >= 320, "runs counted: " + totalRuns);
    }

    @Test
    void dependentsNeverRunBeforeDependenciesFinish() {
        for (int round = 0; round < 100; round++) {
            TestingTasks.Generated generated = TestingTasks.randomDag(
                    SEED + 1000 + round, 14, false);
            TaskGraph graph = generated.graph();
            for (int run = 0; run < 2; run++) {
                long seed = SEED + 1000 + round * 100 + run;
                ExecutorService turbulent = new TurbulentExecutor(pool, seed);
                EventLog events = new EventLog(clock);
                TaskScheduler scheduler = new TaskScheduler(turbulent,
                        SchedulingMode.FAIL_FAST, events);
                Map<String, TaskResult> results = runWithWatchdog(seed, graph,
                        scheduler, new ResourceBudget(8, 4096));
                assertEquals(graph.size(), results.size());
                for (String name : graph.names()) {
                    assertTrue(events.hasStarted(name), "started: " + name);
                    assertTrue(events.hasFinished(name), "finished: " + name);
                    for (String dep : graph.hardDepsOf(name)) {
                        assertTrue(events.finishedAt(dep) <= events.startedAt(name),
                                "task " + name + " started before its dependency "
                                        + dep + " finished (seed " + seed + ")");
                    }
                }
            }
        }
    }

    @Test
    void resultsAreDeterministicUnderTurbulence() {
        for (int round = 0; round < 120; round++) {
            Map<String, TaskStatus> reference = null;
            for (int run = 0; run < 4; run++) {
                long seed = SEED + 2000 + round * 100 + run;
                TestingTasks.Generated generated = TestingTasks.randomDag(
                        SEED + 2000 + round, 16, false);
                TaskGraph graph = generated.graph();
                ExecutorService turbulent = new TurbulentExecutor(pool, seed);
                TaskScheduler scheduler = new TaskScheduler(turbulent,
                        SchedulingMode.KEEP_GOING, BuildEvents.NOOP);
                Map<String, TaskResult> results = runWithWatchdog(seed, graph,
                        scheduler, new ResourceBudget(3, 2048),
                        (task, cancel) -> {
                            if (task.name().endsWith("7")) {
                                return new TaskResult(task.name(),
                                        task.taskType(), TaskStatus.FAILED,
                                        List.of(), TaskTimings.ZERO, 0);
                            }
                            return new TaskResult(task.name(), task.taskType(),
                                    TaskStatus.SUCCEEDED, List.of(),
                                    TaskTimings.ZERO, 0);
                        });
                Map<String, TaskStatus> statuses = new HashMap<>();
                results.forEach((name, result) ->
                        statuses.put(name, result.status()));
                // les statuts attendus : réussite partout sauf la
                // chaîne aval des tâches en « *7 » qui échouent
                for (String name : graph.names()) {
                    TaskResult result = results.get(name);
                    if (name.endsWith("7")) {
                        assertEquals(TaskStatus.FAILED, result.status(),
                                "task " + name + " must fail (seed " + seed + ")");
                    }
                }
                if (reference == null) {
                    reference = statuses;
                } else {
                    assertEquals(reference, statuses,
                            "results differ between runs (round " + round
                                    + ", run " + run + ", seed " + seed + ")");
                }
            }
        }
    }

    @Test
    void cancellationLeavesNoOrphan() throws Exception {
        for (int round = 0; round < 40; round++) {
            long seed = SEED + 3000 + round;
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            TestingTasks.Generated generated = TestingTasks.randomDag(seed, 12,
                    false);
            TaskGraph graph = generated.graph();
            TaskScheduler scheduler = new TaskScheduler(pool,
                    SchedulingMode.FAIL_FAST, BuildEvents.NOOP);
            CancelToken.Cancellation cancellation = CancelToken.create();
            final boolean[] observedCancellation = {false};
            Future<Map<String, TaskResult>> build = watchdog.submit(() ->
                    scheduler.run(graph, prioritiesZero(graph),
                            new ResourceBudget(4, 1024), cancellation.token(),
                            (task, cancel) -> {
                                started.countDown();
                                // la libération vient de l'annulation :
                                // écouteur coopératif ou interruption
                                cancel.onCancel(release::countDown);
                                try {
                                    release.await();
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                }
                                if (cancel.isCancelled()) {
                                    observedCancellation[0] = true;
                                }
                                return new TaskResult(task.name(),
                                        task.taskType(), TaskStatus.CANCELLED,
                                        List.of(), TaskTimings.ZERO, 0);
                            }));
            assertTrue(started.await(30, TimeUnit.SECONDS),
                    "a task must start (seed " + seed + ")");
            cancellation.cancel();
            Map<String, TaskResult> results;
            try {
                results = build.get(60, TimeUnit.SECONDS);
            } catch (Exception failure) {
                fail("run() must return after cancellation (seed " + seed
                        + "): " + failure);
                return;
            }
            assertEquals(graph.size(), results.size(),
                    "every task must be terminal (seed " + seed + ")");
            assertTrue(observedCancellation[0],
                    "the in-flight task must have observed the cancellation "
                            + "(seed " + seed + ")");
            // aucune orpheline : l'exécuteur répond immédiatement
            Future<?> probe = pool.submit(() -> {
            });
            probe.get(30, TimeUnit.SECONDS);
        }
    }
}
