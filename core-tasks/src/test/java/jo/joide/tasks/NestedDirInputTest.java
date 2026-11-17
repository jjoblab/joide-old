package jo.joide.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import jo.joide.store.BuildStore;
import jo.joide.vfs.DirtySet;
import jo.joide.vfs.TreeScanner;
import jo.joide.vfs.VfsSnapshot;

/**
 * Régression du balayage des entrées de dossier imbriquées : à
 * profondeur 2 et au-delà, le moteur résolvait le chemin relatif du
 * sous-dossier contre le dossier courant et échouait sur un chemin
 * aux segments dupliqués (a/a/b). Découvert par le pipeline JVM P3
 * (sorties classes-java/com/example/lib).
 */
class NestedDirInputTest {

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
     * Arborescence à profondeur 3 : le dossier d'entrée contient
     * a/b/c/f.txt et a/g.txt — le balayage doit les trouver tous les
     * deux sans erreur.
     */
    @Test
    void deeplyNestedInputDirectoryScansWithoutError() throws Exception {
        write("a/b/c/f.txt", "contenu");
        write("a/g.txt", "autre");
        RootedPath input = RootedPath.of(RootName.PROJECT, "a");
        TestingTasks.RecordingTask task = new TestingTasks.RecordingTask(
                "nested", "copy",
                TaskInputs.builder().dir(input).build(),
                TaskOutputs.builder()
                        .file(RootedPath.of(RootName.OUT, "out.txt")).build(),
                ResourceClaim.LIGHT).acts(context -> {
                    try {
                        Path target = context.resolve(
                                RootedPath.of(RootName.OUT, "out.txt"));
                        Files.writeString(target, "done");
                        return TaskExecution.ok();
                    } catch (IOException e) {
                        return TaskExecution.failed(List.of(
                                jo.joide.platform.Problem.error(
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
        TaskGraph graph = TaskGraph.builder().register(task).build();
        Map<RootName, DirtySet> dirty = new LinkedHashMap<>();
        dirty.put(RootName.PROJECT, DirtySet.of(
                Set.of("a/b/c/f.txt", "a/g.txt"), Set.of(), Set.of()));
        dirty.put(RootName.OUT, DirtySet.empty());
        Map<RootName, VfsSnapshot> snapshots = new LinkedHashMap<>();
        for (Map.Entry<RootName, Path> anchor : roots().asMap().entrySet()) {
            snapshots.put(anchor.getKey(), VfsSnapshot.of(TreeScanner.scan(
                    anchor.getValue(), pool,
                    jo.joide.platform.CancelToken.NEVER)));
        }
        BuildReport first = engine.build(graph, dirty, snapshots,
                jo.joide.platform.CancelToken.NEVER);
        assertTrue(first.successful(), () -> first.failures().toString());
        assertEquals(TaskStatus.SUCCEEDED, first.results().get(0).status());

        // second build sans changement : la relecture du hachage
        // d'arbre doit aussi réussir (chemins relatifs stables)
        BuildReport second = engine.build(graph,
                new LinkedHashMap<>(Map.of(
                        RootName.PROJECT, DirtySet.empty(),
                        RootName.OUT, DirtySet.empty())),
                snapshots, jo.joide.platform.CancelToken.NEVER);
        assertTrue(second.successful(), () -> second.failures().toString());
        assertEquals(TaskStatus.UP_TO_DATE,
                second.results().get(0).status());
    }

    private void write(String relative, String content) throws IOException {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
