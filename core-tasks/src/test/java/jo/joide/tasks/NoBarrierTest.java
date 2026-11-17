package jo.joide.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jo.joide.platform.Clock;
import jo.joide.tasks.TestingTasks.EventLog;

/**
 * Absence de barrière par niveau : sur un graphe où le premier niveau
 * contient une tâche lente et une tâche rapide dont dépend une
 * chaîne longue, la chaîne démarre pendant que la tâche lente tourne
 * encore.
 *
 * <p>Preuve structurelle exacte : le démarrage de la chaîne précède
 * la fin de la tâche lente — une barrière de niveau l'interdirait.
 * Mesure chiffrée : le temps mur reste sous le chemin critique plus
 * une marge de 50 % (marge documentée pour le planificateur partagé
 * à deux cœurs du bac à sable), là où une barrière imposerait au
 * moins la somme des deux niveaux.</p>
 */
class NoBarrierTest {

    /** Travail propre de la tâche lente : 2 secondes. */
    private static final long SLOW_SPIN_NANOS = 2_000_000_000L;

    /** Travail propre de chaque maillon de la chaîne : 250 ms. */
    private static final long STEP_SPIN_NANOS = 250_000_000L;

    /** Longueur de la chaîne dépendant de la tâche rapide. */
    private static final int CHAIN_LENGTH = 5;

    private ExecutorService pool;
    private Clock clock;

    @BeforeEach
    void setUp() {
        pool = Executors.newFixedThreadPool(3);
        clock = Clock.system();
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    private static void spin(long nanos) {
        long end = System.nanoTime() + nanos;
        while (System.nanoTime() < end) {
            // attente active mesurée : la durée cible est atteinte
            // même sous charge processeur variable
        }
    }

    @Test
    void longChainStartsWhileSlowTaskStillRuns() {
        // niveau 1 : slow (2 s de travail propre) et first (250 ms)
        // chaîne : first -> c1 -> c2 -> c3 -> c4 -> c5 (250 ms chacun)
        TaskGraph.Builder builder = TaskGraph.builder();
        builder.register(stepTask("slow", SLOW_SPIN_NANOS));
        builder.register(stepTask("first", STEP_SPIN_NANOS));
        builder.dependsOn("c1", "first");
        for (int i = 1; i <= CHAIN_LENGTH; i++) {
            if (i > 1) {
                builder.register(stepTask("c" + i, STEP_SPIN_NANOS));
                builder.dependsOn("c" + i, "c" + (i - 1));
            } else {
                builder.register(stepTask("c" + i, STEP_SPIN_NANOS));
            }
        }
        TaskGraph graph = builder.build();

        EventLog events = new EventLog(clock);
        TaskScheduler scheduler = new TaskScheduler(pool, SchedulingMode.FAIL_FAST,
                events);
        long start = clock.nanoTime();
        Map<String, TaskResult> results = scheduler.run(graph,
                prioritiesZero(graph), new ResourceBudget(4, 4096), null,
                (task, cancel) -> {
                    long spinNanos = "slow".equals(task.name())
                            ? SLOW_SPIN_NANOS : STEP_SPIN_NANOS;
                    spin(spinNanos);
                    return new TaskResult(task.name(), task.taskType(),
                            TaskStatus.SUCCEEDED, List.of(), TaskTimings.ZERO, 0);
                });
        long wallMillis = (clock.nanoTime() - start) / 1_000_000;

        assertEquals(graph.size(), results.size());
        for (TaskResult result : results.values()) {
            assertEquals(TaskStatus.SUCCEEDED, result.status(),
                    "task " + result.name() + " must succeed");
        }

        // preuve structurelle : la chaîne a démarré pendant que la
        // tâche lente tournait encore — impossible avec une barrière
        // de niveau, qui attend la fin de tout le niveau 1
        assertTrue(events.startedAt("c1") < events.finishedAt("slow"),
                "c1 started at " + events.startedAt("c1")
                        + " must precede slow finish at "
                        + events.finishedAt("slow"));

        System.out.println("[barriere] mur " + wallMillis + " ms ; chemin "
                + "critique nominal 2000 ms ; une barrière de niveau "
                + "exigerait au moins 3500 ms ; c1 démarrée à "
                + events.startedAt("c1") + " ns, slow terminée à "
                + events.finishedAt("slow") + " ns");
        // mesure chiffrée : chemin critique = max(2 s, 6 x 250 ms)
        // = 2 s ; barrière de niveau = 2 s + 1,5 s = 3,5 s au moins
        long criticalPathMillis = Math.max(
                SLOW_SPIN_NANOS / 1_000_000,
                (CHAIN_LENGTH + 1) * STEP_SPIN_NANOS / 1_000_000);
        long barrierMillis = SLOW_SPIN_NANOS / 1_000_000
                + (CHAIN_LENGTH + 1) * STEP_SPIN_NANOS / 1_000_000;
        assertTrue(wallMillis >= criticalPathMillis,
                "wall " + wallMillis + " ms cannot beat the critical path "
                        + criticalPathMillis + " ms");
        assertTrue(wallMillis <= criticalPathMillis + criticalPathMillis / 2,
                "wall " + wallMillis + " ms exceeds critical path "
                        + criticalPathMillis + " ms by more than the 50% "
                        + "margin (a level barrier would give at least "
                        + barrierMillis + " ms)");
    }

    private static TaskDefinition stepTask(String name, long spinNanos) {
        return new TaskDefinition() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String taskType() {
                return "noop";
            }

            @Override
            public TaskInputs inputs() {
                return TaskInputs.builder().build();
            }

            @Override
            public TaskOutputs outputs() {
                return TaskOutputs.builder().build();
            }

            @Override
            public String toolVersion() {
                return "test";
            }

            @Override
            public String optionsFingerprint() {
                return name;
            }

            @Override
            public TaskExecution execute(TaskContext context) {
                throw new UnsupportedOperationException(
                        "handled by the step function");
            }
        };
    }

    private static Map<String, Long> prioritiesZero(TaskGraph graph) {
        Map<String, Long> priorities = new HashMap<>();
        for (String name : graph.names()) {
            priorities.put(name, 0L);
        }
        return priorities;
    }
}
