package jo.joide.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.platform.Clock;
import jo.joide.platform.Problem;
import jo.joide.platform.RootName;
import jo.joide.platform.RootSet;
import jo.joide.platform.RootedPath;
import jo.joide.store.BuildStore;
import jo.joide.vfs.DirtySet;
import jo.joide.vfs.TreeScanner;
import jo.joide.vfs.VfsSnapshot;

/**
 * Régression des chaînages intra-build : une tâche aval dont
 * l'entrée de dossier recouvre une sortie écrite par une tâche amont
 * de la même passe doit re-hacher à frais — l'instantané d'avant
 * build est périmé pour ces chemins. Découvert par le pipeline JVM
 * P3 (jar consommant le dossier de classes réécrit).
 */
class IntraBuildChainTest {

    @TempDir
    Path tempDir;

    private ExecutorService pool;
    private Path project;
    private Path out;

    @BeforeEach
    void setUp() throws IOException {
        pool = Executors.newFixedThreadPool(2);
        project = Files.createDirectories(tempDir.resolve("project"));
        out = Files.createDirectories(tempDir.resolve("out"));
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    private RootSet roots() {
        Map<RootName, Path> anchors = new LinkedHashMap<>();
        anchors.put(RootName.PROJECT, project);
        anchors.put(RootName.OUT, out);
        return RootSet.of(anchors);
    }

    /**
     * La tâche productrice écrit un fichier de classes dont le
     * contenu dépend d'une propriété ; la tâche consommatrice
     * déclare le dossier producteur comme entrée.
     */
    @Test
    void dirInputCoveringInBuildOutputIsRehashed() throws Exception {
        AtomicInteger generation = new AtomicInteger(1);
        RootedPath classesDir = RootedPath.of(RootName.OUT, "classes");
        RootedPath marker = RootedPath.of(RootName.OUT, "result.txt");
        TestingTasks.RecordingTask consumer = new TestingTasks.RecordingTask(
                "consumer", "jar",
                TaskInputs.builder().dir(classesDir).build(),
                TaskOutputs.builder().file(marker).build(),
                ResourceClaim.LIGHT).acts(context -> {
            try {
                Files.writeString(context.resolve(marker), "done");
                return TaskExecution.ok();
            } catch (IOException e) {
                return TaskExecution.failed(List.of(Problem.error(
                        "write-error", e.toString())));
            }
        });
        BuildStore store = new jo.joide.tasks.EngineMemoryStore();
        BuildEngine engine = BuildEngine.builder()
                .roots(roots())
                .store(store)
                .clock(Clock.system())
                .executor(pool)
                .outputCacheDir(tempDir.resolve("cache"))
                .build();
        TaskGraph graph = TaskGraph.builder()
                .register(producer(generation, 1))
                .register(consumer)
                .dependsOn("consumer", "producer")
                .build();
        BuildReport first = engine.build(graph, emptyDirty(), snapshots(),
                jo.joide.platform.CancelToken.NEVER);
        assertTrue(first.successful(), () -> first.failures().toString());
        assertEquals(TaskStatus.SUCCEEDED, status(first, "producer"));
        assertEquals(TaskStatus.SUCCEEDED, status(first, "consumer"));

        // le producteur écrit un contenu différent : le consommateur
        // doit re-s'exécuter malgré un instantané d'avant-build
        // identique pour les deux builds (l'écriture a lieu PENDANT
        // la passe)
        generation.set(2);
        TaskGraph graph2 = TaskGraph.builder()
                .register(producer(generation, 2))
                .register(consumer)
                .dependsOn("consumer", "producer")
                .build();
        BuildReport second = engine.build(graph2, emptyDirty(), snapshots(),
                jo.joide.platform.CancelToken.NEVER);
        assertTrue(second.successful(), () -> second.failures().toString());
        assertEquals(TaskStatus.SUCCEEDED, status(second, "producer"));
        assertEquals(TaskStatus.SUCCEEDED, status(second, "consumer"),
                "consumer must re-run: its dir input was rewritten"
                        + " during the build");
    }

    private TestingTasks.RecordingTask producer(
            AtomicInteger generation, int declared) {
        RootedPath classesDir = RootedPath.of(RootName.OUT, "classes");
        return new TestingTasks.RecordingTask(
                "producer", "compile",
                TaskInputs.builder().property("gen",
                        String.valueOf(declared)).build(),
                TaskOutputs.builder().dir(classesDir).build(),
                ResourceClaim.LIGHT).acts(context -> {
            try {
                Path dir = context.resolve(classesDir);
                Path file = dir.resolve("com/example/A.txt");
                Files.createDirectories(file.getParent());
                Files.writeString(file,
                        "generation " + generation.get());
                return TaskExecution.ok();
            } catch (IOException e) {
                return TaskExecution.failed(List.of(Problem.error(
                        "write-error", e.toString())));
            }
        });
    }

    private static TaskStatus status(BuildReport report, String task) {
        return report.results().stream()
                .filter(r -> r.name().equals(task))
                .findFirst().orElseThrow().status();
    }

    private Map<RootName, DirtySet> emptyDirty() {
        Map<RootName, DirtySet> dirty = new LinkedHashMap<>();
        dirty.put(RootName.PROJECT, DirtySet.empty());
        dirty.put(RootName.OUT, DirtySet.empty());
        return dirty;
    }

    private Map<RootName, VfsSnapshot> snapshots() throws IOException {
        Map<RootName, VfsSnapshot> snapshots = new LinkedHashMap<>();
        for (Map.Entry<RootName, Path> anchor : roots().asMap().entrySet()) {
            snapshots.put(anchor.getKey(), VfsSnapshot.of(TreeScanner.scan(
                    anchor.getValue(), pool,
                    jo.joide.platform.CancelToken.NEVER)));
        }
        return snapshots;
    }
}
