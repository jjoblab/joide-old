package jo.joide.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.platform.Clock;
import jo.joide.platform.RootName;
import jo.joide.platform.RootSet;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.EngineMemoryStore.Guard;
import jo.joide.vfs.DirtySet;
import jo.joide.vfs.TreeScanner;
import jo.joide.vfs.VfsSnapshot;

/**
 * Build sans changement sur démon chaud au tier L (graphes de tâches
 * factices) : aucune écriture ni lecture du store, décision complète
 * en moins de cent millisecondes.
 *
 * <p>Échelle L : 200 modules de six tâches, 1 200 tâches, 3 600
 * fichiers d'entrée. Le build à chaud rejoue les mêmes instantanés en
 * mémoire et un ensemble sale vide : le store est enveloppé d'une
 * garde qui fait échouer le test au premier accès, et la durée mur
 * est mesurée à l'horloge injectée.</p>
 */
class HotNoChangeTest {

    private static final int MODULE_COUNT = 200;
    private static final int TASKS_PER_MODULE = 6;
    private static final int INPUTS_PER_TASK = 3;
    private static final long HOT_BUDGET_MILLIS = 100;

    @TempDir
    Path tempDir;

    private ExecutorService pool;
    private Path project;
    private Path out;
    private Path cacheDir;

    @BeforeEach
    void setUp() throws IOException {
        pool = Executors.newFixedThreadPool(4);
        project = Files.createDirectories(tempDir.resolve("project"));
        out = Files.createDirectories(tempDir.resolve("out"));
        cacheDir = Files.createDirectories(tempDir.resolve("cache"));
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    @Test
    void hotNoChangeBuildTouchesNoStoreAndStaysWithinBudget()
            throws IOException {
        Clock clock = Clock.system();
        // 1. générer le tier L : 200 modules, 6 tâches, 3 entrées
        // chacune, une sortie par tâche ; chaîne par module, premier
        // module central
        TaskGraph.Builder builder = TaskGraph.builder();
        List<TestingTasks.RecordingTask> tasks = new ArrayList<>();
        String[] kinds = {"generate", "compile", "process", "merge", "dex",
                "package"};
        for (int module = 0; module < MODULE_COUNT; module++) {
            for (int kind = 0; kind < TASKS_PER_MODULE; kind++) {
                String name = "mod" + module + ":" + kinds[kind];
                TaskInputs.Builder inputs = TaskInputs.builder();
                for (int f = 0; f < INPUTS_PER_TASK; f++) {
                    String relative = "src/mod" + module + "/k" + kind
                            + "/f" + f + ".txt";
                    write(project.resolve(relative),
                            "contenu " + module + " " + kind + " " + f);
                    inputs.file(RootedPath.of(RootName.PROJECT, relative));
                }
                String outputRelative = "mod" + module + "/" + kinds[kind]
                        + ".out";
                RootedPath output = RootedPath.of(RootName.OUT, outputRelative);
                int moduleIndex = module;
                int kindIndex = kind;
                TestingTasks.RecordingTask task = new TestingTasks.RecordingTask(
                        name, kinds[kind], inputs.build(),
                        TaskOutputs.builder().file(output).build(),
                        ResourceClaim.LIGHT).acts(context -> {
                    try {
                        Path target = context.resolve(output);
                        Files.createDirectories(target.getParent());
                        Files.writeString(target,
                                "sortie " + moduleIndex + " " + kindIndex);
                        return TaskExecution.ok();
                    } catch (IOException unreadable) {
                        return TaskExecution.failed(List.of(jo.joide.platform.Problem
                                .error("write-error", unreadable.toString())
                                .at(name)));
                    }
                });
                tasks.add(task);
                builder.register(task);
                if (kind > 0) {
                    builder.dependsOn(name,
                            "mod" + module + ":" + kinds[kind - 1]);
                } else if (module > 0) {
                    // chaque module attend le module précédent : une
                    // chaîne profonde de 200 modules
                    builder.dependsOn(name,
                            "mod" + (module - 1) + ":" + kinds[TASKS_PER_MODULE - 1]);
                }
            }
        }
        TaskGraph graph = builder.build();
        assertEquals(MODULE_COUNT * TASKS_PER_MODULE, graph.size());

        RootSet roots = RootSet.of(Map.of(
                RootName.PROJECT, project,
                RootName.OUT, out));
        Map<RootName, VfsSnapshot> snapshots = snapshots(roots);
        Map<RootName, DirtySet> noDirty = new LinkedHashMap<>();
        noDirty.put(RootName.PROJECT, DirtySet.empty());
        noDirty.put(RootName.OUT, DirtySet.empty());

        // 2. premier build : tout s'exécute, le store est rempli
        EngineMemoryStore realStore = new EngineMemoryStore();
        Guard guard = new Guard(realStore);
        BuildEngine engine = BuildEngine.builder()
                .roots(roots)
                .store(guard)
                .clock(clock)
                .executor(pool)
                .outputCacheDir(cacheDir)
                .build();
        BuildReport first = engine.build(graph, noDirty, snapshots, null);
        assertEquals(graph.size(), first.count(TaskStatus.SUCCEEDED),
                "first build must execute everything: " + first);
        for (TestingTasks.RecordingTask task : tasks) {
            assertEquals(1, task.executions(), task.name());
        }

        // 3. build à chaud sans changement : la garde armée prouve
        // l'absence d'accès au store, la durée est mesurée
        guard.arm();
        BuildReport hot = engine.build(graph, noDirty, snapshots, null);
        assertEquals(0, guard.violations(),
                "the hot build must not touch the store at all");
        assertEquals(graph.size(), hot.count(TaskStatus.UP_TO_DATE),
                "everything must be up to date: " + hot);
        for (TestingTasks.RecordingTask task : tasks) {
            assertEquals(1, task.executions(),
                    "no task may re-execute: " + task.name());
        }
        long wallMillis = hot.wallNanos() / 1_000_000;
        System.out.println("[hot] tier L (" + graph.size() + " tâches, "
                + (MODULE_COUNT * TASKS_PER_MODULE * INPUTS_PER_TASK)
                + " fichiers) : build à chaud en " + wallMillis + " ms, "
                + guard.violations() + " accès au store, budget "
                + HOT_BUDGET_MILLIS + " ms");
        assertTrue(wallMillis <= HOT_BUDGET_MILLIS,
                "hot no-change build took " + wallMillis + " ms, budget "
                        + HOT_BUDGET_MILLIS + " ms");
    }

    private Map<RootName, VfsSnapshot> snapshots(RootSet roots)
            throws IOException {
        Map<RootName, VfsSnapshot> snapshots = new LinkedHashMap<>();
        for (Map.Entry<RootName, Path> anchor : roots.asMap().entrySet()) {
            snapshots.put(anchor.getKey(), VfsSnapshot.of(TreeScanner.scan(
                    anchor.getValue(), pool,
                    jo.joide.platform.CancelToken.NEVER)));
        }
        return snapshots;
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
