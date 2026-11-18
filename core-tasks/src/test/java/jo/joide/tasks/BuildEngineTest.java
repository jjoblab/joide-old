package jo.joide.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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
 * Acceptation du moteur de build : chaîne de décision à jour,
 * relocalisation des empreintes, cache de sorties, erreurs de lecture
 * explicites, delta d'entrées, modes d'échec et build sans changement
 * au chaud sans accès au store.
 */
class BuildEngineTest {

    private static final RootName PROJECT = RootName.PROJECT;
    private static final RootName OUT = RootName.OUT;

    @TempDir
    Path tempDir;

    private ExecutorService pool;
    private Clock clock;
    private Path project;
    private Path out;
    private Path cacheDir;

    @BeforeEach
    void setUp() throws IOException {
        pool = Executors.newFixedThreadPool(4);
        clock = Clock.system();
        project = Files.createDirectories(tempDir.resolve("project"));
        out = Files.createDirectories(tempDir.resolve("out"));
        cacheDir = Files.createDirectories(tempDir.resolve("cache"));
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    private RootSet roots() {
        Map<RootName, Path> anchors = new LinkedHashMap<>();
        anchors.put(PROJECT, project);
        anchors.put(OUT, out);
        return RootSet.of(anchors);
    }

    private RootSet rootsAt(Path projectDir, Path outDir) {
        Map<RootName, Path> anchors = new LinkedHashMap<>();
        anchors.put(PROJECT, projectDir);
        anchors.put(OUT, outDir);
        return RootSet.of(anchors);
    }

    private Map<RootName, VfsSnapshot> snapshots(RootSet roots)
            throws IOException {
        Map<RootName, VfsSnapshot> snapshots = new LinkedHashMap<>();
        for (Map.Entry<RootName, Path> anchor : roots.asMap().entrySet()) {
            if (Files.isDirectory(anchor.getValue())) {
                snapshots.put(anchor.getKey(), VfsSnapshot.of(
                        TreeScanner.scan(anchor.getValue(), pool,
                                jo.joide.platform.CancelToken.NEVER)));
            }
        }
        return snapshots;
    }

    private static Map<RootName, DirtySet> noDirty() {
        Map<RootName, DirtySet> dirty = new LinkedHashMap<>();
        dirty.put(PROJECT, DirtySet.empty());
        dirty.put(OUT, DirtySet.empty());
        return dirty;
    }

    private static Map<RootName, DirtySet> dirty(RootName root,
                                                 Set<String> added,
                                                 Set<String> modified,
                                                 Set<String> removed) {
        Map<RootName, DirtySet> dirty = noDirty();
        dirty.put(root, DirtySet.of(added, modified, removed));
        return dirty;
    }

    private void write(String relative, String content) throws IOException {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /**
     * Tâche qui copie son unique entrée vers sa sortie ; le contenu
     * copié est contrôlable pour les tests de falsification.
     */
    private TestingTasks.RecordingTask copyTask(String name, String input,
                                                String output) {
        RootedPath in = RootedPath.of(PROJECT, input);
        RootedPath outPath = RootedPath.of(OUT, output);
        return new TestingTasks.RecordingTask(name, "copy",
                TaskInputs.builder().file(in).build(),
                TaskOutputs.builder().file(outPath).build(),
                ResourceClaim.LIGHT).acts(context -> {
            try {
                Path source = context.resolve(in);
                Path target = context.resolve(outPath);
                Files.createDirectories(target.getParent());
                Files.copy(source, target,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return TaskExecution.ok();
            } catch (IOException unreadable) {
                return TaskExecution.failed(List.of(Problem.error(
                        "copy-error", unreadable.toString()).at(name)));
            }
        });
    }

    private BuildEngine engine(BuildStore store, RootSet rootSet) {
        return BuildEngine.builder()
                .roots(rootSet)
                .store(store)
                .clock(clock)
                .executor(pool)
                .outputCacheDir(cacheDir)
                .outputCacheMaxBytes(64L * 1024 * 1024)
                .build();
    }

    @Test
    void firstBuildExecutesThenSecondIsUpToDate() throws IOException {
        write("src/a.txt", "contenu-a");
        write("src/b.txt", "contenu-b");
        TaskGraph graph = TaskGraph.builder()
                .register(copyTask("copy-a", "src/a.txt", "a.txt"))
                .register(copyTask("copy-b", "src/b.txt", "b.txt"))
                .build();
        BuildStore store = new EngineMemoryStore();
        BuildEngine engine = engine(store, roots());

        BuildReport first = engine.build(graph, noDirty(), snapshots(roots()),
                null);
        assertEquals(2, first.count(TaskStatus.SUCCEEDED), first.toString());
        assertEquals(Files.readString(out.resolve("a.txt")), "contenu-a");

        BuildReport second = engine.build(graph, noDirty(), snapshots(roots()),
                null);
        assertEquals(2, second.count(TaskStatus.UP_TO_DATE),
                second.toString());
        assertEquals(0, second.count(TaskStatus.SUCCEEDED));
    }

    @Test
    void modifiedInputReExecutesOnlyImpactedTask() throws IOException {
        write("src/a.txt", "contenu-a");
        write("src/b.txt", "contenu-b");
        TaskGraph graph = TaskGraph.builder()
                .register(copyTask("copy-a", "src/a.txt", "a.txt"))
                .register(copyTask("copy-b", "src/b.txt", "b.txt"))
                .build();
        BuildStore store = new EngineMemoryStore();
        BuildEngine engine = engine(store, roots());
        engine.build(graph, noDirty(), snapshots(roots()), null);

        write("src/b.txt", "contenu-b2");
        BuildReport third = engine.build(graph,
                dirty(PROJECT, Set.of(), Set.of("src/b.txt"), Set.of()),
                snapshots(roots()), null);
        assertEquals(1, third.count(TaskStatus.UP_TO_DATE), third.toString());
        assertEquals(1, third.count(TaskStatus.SUCCEEDED));
        assertEquals("copy-b", third.results().stream()
                .filter(result -> result.status() == TaskStatus.SUCCEEDED)
                .findFirst().orElseThrow().name());
        assertEquals("contenu-b2", Files.readString(out.resolve("b.txt")));
    }

    @Test
    void toolVersionChangeInvalidates() throws IOException {
        write("src/a.txt", "contenu-a");
        TestingTasks.RecordingTask task = copyTask("copy-a", "src/a.txt",
                "a.txt");
        TaskGraph graph = TaskGraph.builder().register(task).build();
        BuildStore store = new EngineMemoryStore();
        BuildEngine engine = engine(store, roots());
        engine.build(graph, noDirty(), snapshots(roots()), null);

        // même fichier, autre version d'outil : la tâche rejoue
        TaskDefinition otherVersion = new TaskDefinition() {
            @Override
            public String name() {
                return "copy-a";
            }

            @Override
            public String taskType() {
                return "copy";
            }

            @Override
            public TaskInputs inputs() {
                return task.inputs();
            }

            @Override
            public TaskOutputs outputs() {
                return task.outputs();
            }

            @Override
            public String toolVersion() {
                return "test-tool-2";
            }

            @Override
            public String optionsFingerprint() {
                return "options-1";
            }

            @Override
            public TaskExecution execute(TaskContext context)
                    throws InterruptedException {
                try {
                    Files.writeString(context.resolve(
                            RootedPath.of(OUT, "a.txt")), "contenu-a");
                    return TaskExecution.ok();
                } catch (IOException unreadable) {
                    return TaskExecution.failed(List.of(Problem.error(
                            "write-error", unreadable.toString())));
                }
            }
        };
        TaskGraph rebuilt = TaskGraph.builder().register(otherVersion).build();
        BuildReport report = engine.build(rebuilt, noDirty(),
                snapshots(roots()), null);
        assertEquals(1, report.count(TaskStatus.SUCCEEDED), report.toString());
    }

    @Test
    void fingerprintsAreRelocatable() throws IOException {
        write("src/a.txt", "contenu-a");
        write("src/b.txt", "contenu-b");
        TaskGraph graph = TaskGraph.builder()
                .register(copyTask("copy-a", "src/a.txt", "a.txt"))
                .register(copyTask("copy-b", "src/b.txt", "b.txt"))
                .build();
        BuildStore store = new EngineMemoryStore();
        RootSet firstRoots = roots();
        BuildEngine engine = engine(store, firstRoots);
        engine.build(graph, noDirty(), snapshots(firstRoots), null);

        // copie intégrale du projet vers un autre emplacement
        Path project2 = tempDir.resolve("project2");
        Path out2 = tempDir.resolve("out2");
        copyRecursively(project, project2);
        copyRecursively(out, out2);

        // même store (copié avec le projet), moteur neuf : tout doit
        // être à jour — les empreintes ne contiennent pas de chemin
        // absolu, le hachage de rattrapage retrouve les mêmes contenus
        RootSet secondRoots = rootsAt(project2, out2);
        BuildEngine relocated = engine(store, secondRoots);
        BuildReport report = relocated.build(graph, noDirty(),
                snapshots(secondRoots), null);
        assertEquals(2, report.count(TaskStatus.UP_TO_DATE),
                "relocated build must be fully up to date: " + report);
    }

    @Test
    void cleanedOutputsRestoreFromCache() throws IOException {
        write("src/a.txt", "contenu-a");
        TaskGraph graph = TaskGraph.builder()
                .register(copyTask("copy-a", "src/a.txt", "a.txt"))
                .build();
        BuildStore store = new EngineMemoryStore();
        BuildEngine engine = engine(store, roots());
        engine.build(graph, noDirty(), snapshots(roots()), null);

        // nettoyage des sorties : le journal les signale supprimées
        Files.delete(out.resolve("a.txt"));
        BuildReport report = engine.build(graph,
                dirty(OUT, Set.of(), Set.of(), Set.of("a.txt")),
                snapshots(roots()), null);
        assertEquals(1, report.count(TaskStatus.FROM_CACHE), report.toString());
        assertEquals("contenu-a", Files.readString(out.resolve("a.txt")),
                "restored output must match the original");
    }

    @Test
    void relocatedCleanOutputsRestoreFromCache() throws IOException {
        write("src/a.txt", "contenu-a");
        TaskGraph graph = TaskGraph.builder()
                .register(copyTask("copy-a", "src/a.txt", "a.txt"))
                .build();
        BuildStore store = new EngineMemoryStore();
        BuildEngine engine = engine(store, roots());
        engine.build(graph, noDirty(), snapshots(roots()), null);

        Path project2 = tempDir.resolve("project2");
        copyRecursively(project, project2);
        Files.createDirectories(tempDir.resolve("out2"));

        // moteur neuf, sorties absentes : restauration depuis le
        // cache partagé, adressé par empreinte (sans chemin absolu)
        RootSet secondRoots = rootsAt(project2, tempDir.resolve("out2"));
        BuildEngine relocated = engine(store, secondRoots);
        BuildReport report = relocated.build(graph,
                dirty(OUT, Set.of(), Set.of(), Set.of("a.txt")),
                snapshots(secondRoots), null);
        assertEquals(1, report.count(TaskStatus.FROM_CACHE), report.toString());
        assertEquals("contenu-a",
                Files.readString(tempDir.resolve("out2/a.txt")));
    }

    /** Lot XX/g — le daemon ne signale JAMAIS les sorties supprimées
     *  (les racines de sorties portent un ensemble sale vide : « les
     *  sorties relisent fraîches ») : le moteur doit SE VÉRIFIER
     *  LUI-MÊME sur le disque. Le terrain du 2026-11-18 : generateRJar
     *  « à jour » pour toujours, r.jar manquant pour toujours,
     *  compileKotlin « Classpath entry points to a non-existent
     *  location: app/r.jar » à CHAQUE build — un hit à-jour dont une
     *  sortie déclarée a disparu doit tomber dans la restauration du
     *  cache (ici) ou la réexécution (cache purgé aussi). */
    @Test
    void silentlyCleanedOutputsRestoreWithoutHints() throws IOException {
        write("src/a.txt", "contenu-a");
        TaskGraph graph = TaskGraph.builder()
                .register(copyTask("copy-a", "src/a.txt", "a.txt"))
                .build();
        BuildStore store = new EngineMemoryStore();
        BuildEngine engine = engine(store, roots());
        engine.build(graph, noDirty(), snapshots(roots()), null);

        // le « Clean » silencieux : les sorties partent, aucune hint
        // n'arrive au moteur (le daemon ne comptabilise pas out/)
        Files.delete(out.resolve("a.txt"));
        BuildReport report = engine.build(graph, noDirty(),
                snapshots(roots()), null);
        assertTrue(report.count(TaskStatus.FROM_CACHE) == 1
                        || report.count(TaskStatus.SUCCEEDED) == 1,
                "la sortie disparue doit se restaurer ou réexécuter : "
                        + report);
        assertEquals("contenu-a", Files.readString(out.resolve("a.txt")),
                "la sortie doit être de retour sur le disque");
    }

    @Test
    void unreadableInputFailsExplicitly() throws IOException {
        // lien symbolique pendant : la lecture échoue, l'erreur est
        // explicite, jamais une empreinte vide silencieuse
        Path dangling = project.resolve("src/dangling.txt");
        Files.createDirectories(dangling.getParent());
        Files.createSymbolicLink(dangling,
                project.resolve("src/inexistant.txt"));

        RootedPath in = RootedPath.of(PROJECT, "src/dangling.txt");
        TestingTasks.RecordingTask task = new TestingTasks.RecordingTask(
                "read", "copy", TaskInputs.builder().file(in).build(),
                TaskOutputs.builder()
                        .file(RootedPath.of(OUT, "lu.txt")).build(),
                ResourceClaim.LIGHT).acts(context -> TaskExecution.ok());
        TaskGraph graph = TaskGraph.builder().register(task).build();
        BuildStore store = new EngineMemoryStore();
        BuildEngine engine = engine(store, roots());

        BuildReport report = engine.build(graph, noDirty(), snapshots(roots()),
                null);
        assertEquals(1, report.count(TaskStatus.FAILED), report.toString());
        TaskResult failure = report.failures().get(0);
        assertEquals("input-read-error", failure.diagnostics().get(0).code(),
                failure.toString());
        assertTrue(failure.diagnostics().get(0).message().contains("dangling"),
                "message names the input: "
                        + failure.diagnostics().get(0).message());
    }

    @Test
    void directoryWhereFileExpectedFailsExplicitly() throws IOException {
        Files.createDirectories(project.resolve("src/dossier.txt"));
        RootedPath in = RootedPath.of(PROJECT, "src/dossier.txt");
        TestingTasks.RecordingTask task = new TestingTasks.RecordingTask(
                "read", "copy", TaskInputs.builder().file(in).build(),
                TaskOutputs.builder()
                        .file(RootedPath.of(OUT, "lu.txt")).build(),
                ResourceClaim.LIGHT).acts(context -> TaskExecution.ok());
        TaskGraph graph = TaskGraph.builder().register(task).build();
        BuildStore store = new EngineMemoryStore();
        BuildEngine engine = engine(store, roots());

        BuildReport report = engine.build(graph, noDirty(), snapshots(roots()),
                null);
        assertEquals(1, report.count(TaskStatus.FAILED));
        assertEquals("input-read-error",
                report.failures().get(0).diagnostics().get(0).code());
    }

    @Test
    void taskExposesPerFileDelta() throws IOException {
        write("src/a.txt", "a");
        write("src/b.txt", "b");
        write("src/c.txt", "c");
        List<InputDelta> observed = new ArrayList<>();
        RootedPath a = RootedPath.of(PROJECT, "src/a.txt");
        RootedPath b = RootedPath.of(PROJECT, "src/b.txt");
        RootedPath c = RootedPath.of(PROJECT, "src/c.txt");
        RootedPath d = RootedPath.of(PROJECT, "src/d.txt");
        write("src/d.txt", "d");
        TestingTasks.RecordingTask task = new TestingTasks.RecordingTask(
                "delta", "copy",
                TaskInputs.builder().file(a).file(b).file(c).build(),
                TaskOutputs.builder()
                        .file(RootedPath.of(OUT, "delta.txt")).build(),
                ResourceClaim.LIGHT).acts(context -> {
            observed.add(context.inputDelta());
            try {
                Path target = context.resolve(RootedPath.of(OUT, "delta.txt"));
                Files.createDirectories(target.getParent());
                Files.writeString(target, "delta");
            } catch (IOException unreadable) {
                return TaskExecution.failed(List.of(Problem.error(
                        "write-error", unreadable.toString())));
            }
            return TaskExecution.ok();
        });
        TaskGraph graph = TaskGraph.builder().register(task).build();
        BuildStore store = new EngineMemoryStore();
        BuildEngine engine = engine(store, roots());
        BuildReport first = engine.build(graph, noDirty(), snapshots(roots()),
                null);
        assertEquals(1, first.count(TaskStatus.SUCCEEDED), first.toString());
        assertEquals(1, observed.size());
        assertEquals(Set.of(a, b, c), observed.get(0).added(),
                "first run sees every declared input as added");
        assertTrue(observed.get(0).modified().isEmpty());
        assertTrue(observed.get(0).removed().isEmpty());

        // b modifié, c supprimé, d ajouté aux entrées déclarées
        write("src/b.txt", "b2");
        Files.delete(project.resolve("src/c.txt"));
        TestingTasks.RecordingTask withD = new TestingTasks.RecordingTask(
                "delta", "copy",
                TaskInputs.builder().file(a).file(b).file(d).build(),
                task.outputs(), ResourceClaim.LIGHT).acts(context -> {
            observed.add(context.inputDelta());
            try {
                Path target = context.resolve(RootedPath.of(OUT, "delta.txt"));
                Files.createDirectories(target.getParent());
                Files.writeString(target, "delta2");
            } catch (IOException unreadable) {
                return TaskExecution.failed(List.of(Problem.error(
                        "write-error", unreadable.toString())));
            }
            return TaskExecution.ok();
        });
        TaskGraph second = TaskGraph.builder().register(withD).build();
        BuildReport report = engine.build(second,
                dirty(PROJECT, Set.of(), Set.of("src/b.txt"),
                        Set.of("src/c.txt")),
                snapshots(roots()), null);
        assertEquals(1, report.count(TaskStatus.SUCCEEDED), report.toString());
        assertEquals(2, observed.size());
        InputDelta delta = observed.get(1);
        assertEquals(Set.of(b), delta.modified(), "modified: " + delta);
        assertEquals(Set.of(d), delta.added(), "added: " + delta);
        assertEquals(Set.of(c), delta.removed(), "removed: " + delta);
    }

    @Test
    void tamperedOutputIsReplaced() throws IOException {
        write("src/a.txt", "contenu-a");
        TaskGraph graph = TaskGraph.builder()
                .register(copyTask("copy-a", "src/a.txt", "a.txt"))
                .build();
        BuildStore store = new EngineMemoryStore();
        BuildEngine engine = engine(store, roots());
        engine.build(graph, noDirty(), snapshots(roots()), null);

        // falsification de la sortie, signalée par le journal
        Files.writeString(out.resolve("a.txt"), "falsifié");
        BuildReport report = engine.build(graph,
                dirty(OUT, Set.of(), Set.of("a.txt"), Set.of()),
                snapshots(roots()), null);
        assertEquals(1, report.count(TaskStatus.FROM_CACHE), report.toString());
        assertEquals("contenu-a", Files.readString(out.resolve("a.txt")),
                "tampered output must be replaced by the recorded one");
    }

    @Test
    void failFastCancelsDownstream() throws IOException {
        write("src/a.txt", "a");
        RootedPath in = RootedPath.of(PROJECT, "src/a.txt");
        TestingTasks.RecordingTask failing = new TestingTasks.RecordingTask(
                "failing", "copy", TaskInputs.builder().file(in).build(),
                TaskOutputs.builder().build(), ResourceClaim.LIGHT)
                .acts(context -> TaskExecution.failed(List.of(
                        Problem.error("boom", "explosé").at("failing"))));
        TestingTasks.RecordingTask dependent = copyTask("dependent",
                "src/a.txt", "dependent.txt");
        TestingTasks.RecordingTask independent = copyTask("independent",
                "src/a.txt", "independent.txt");
        TaskGraph graph = TaskGraph.builder()
                .register(failing)
                .register(dependent)
                .register(independent)
                .dependsOn("dependent", "failing")
                .build();
        BuildStore store = new EngineMemoryStore();
        BuildEngine engine = engine(store, roots());
        BuildReport report = engine.build(graph, noDirty(), snapshots(roots()),
                null);
        assertEquals(1, report.count(TaskStatus.FAILED));
        assertEquals(TaskStatus.CANCELLED,
                report.results().stream()
                        .filter(result -> result.name().equals("dependent"))
                        .findFirst().orElseThrow().status());
    }

    @Test
    void keepGoingSkipsOnlyDownstream() throws IOException {
        write("src/a.txt", "a");
        RootedPath in = RootedPath.of(PROJECT, "src/a.txt");
        TestingTasks.RecordingTask failing = new TestingTasks.RecordingTask(
                "failing", "copy", TaskInputs.builder().file(in).build(),
                TaskOutputs.builder().build(), ResourceClaim.LIGHT)
                .acts(context -> TaskExecution.failed(List.of(
                        Problem.error("boom", "explosé").at("failing"))));
        TestingTasks.RecordingTask dependent = copyTask("dependent",
                "src/a.txt", "dependent.txt");
        TestingTasks.RecordingTask independent = copyTask("independent",
                "src/a.txt", "independent.txt");
        TaskGraph graph = TaskGraph.builder()
                .register(failing)
                .register(dependent)
                .register(independent)
                .dependsOn("dependent", "failing")
                .build();
        BuildStore store = new EngineMemoryStore();
        BuildEngine engine = BuildEngine.builder()
                .roots(roots())
                .store(store)
                .clock(clock)
                .executor(pool)
                .mode(SchedulingMode.KEEP_GOING)
                .outputCacheDir(cacheDir)
                .build();
        BuildReport report = engine.build(graph, noDirty(), snapshots(roots()),
                null);
        assertEquals(1, report.count(TaskStatus.FAILED));
        assertEquals(1, report.count(TaskStatus.SKIPPED_UPSTREAM));
        assertEquals(1, report.count(TaskStatus.SUCCEEDED));
        assertTrue(Files.exists(out.resolve("independent.txt")),
                "independent task must have run");
    }

    @Test
    void buildScanExportContainsEveryTask() throws IOException {
        write("src/a.txt", "contenu-a");
        TaskGraph graph = TaskGraph.builder()
                .register(copyTask("copy-a", "src/a.txt", "a.txt"))
                .build();
        BuildStore store = new EngineMemoryStore();
        BuildEngine engine = engine(store, roots());
        BuildReport report = engine.build(graph, noDirty(), snapshots(roots()),
                null);

        Path jsonFile = tempDir.resolve("scan/build.json");
        Path summaryFile = tempDir.resolve("scan/summary.txt");
        BuildScanWriter.writeJson(report, jsonFile);
        BuildScanWriter.writeSummary(report, summaryFile);
        String json = Files.readString(jsonFile);
        assertTrue(json.contains("\"copy-a\""), json);
        assertTrue(json.contains("\"SUCCEEDED\""), json);
        assertTrue(json.contains("\"fingerprintNanos\""), json);
        assertTrue(json.contains("\"peakHeapBytes\""), json);
        String summary = Files.readString(summaryFile);
        assertTrue(summary.contains("1 exécutées"), summary);
        assertTrue(summary.contains("copy-a"), summary);
    }

    @Test
    void prioritiesRespectCriticalPathOrdering() throws Exception {
        // deux tâches prêtes, un seul fil : la tâche au chemin
        // critique le plus lourd part en premier
        write("src/a.txt", "a");
        TestingTasks.RecordingTask heavy = new TestingTasks.RecordingTask(
                "heavy", "compile", TaskInputs.builder().build(),
                TaskOutputs.builder().build(), ResourceClaim.LIGHT);
        TestingTasks.RecordingTask light = new TestingTasks.RecordingTask(
                "light", "compile", TaskInputs.builder().build(),
                TaskOutputs.builder().build(), ResourceClaim.LIGHT);
        TaskGraph graph = TaskGraph.builder()
                .register(heavy)
                .register(light)
                .build();
        Map<String, Long> priorities = new HashMap<>();
        priorities.put("heavy", 5000L);
        priorities.put("light", 100L);
        ExecutorService single = Executors.newFixedThreadPool(1);
        try {
            TestingTasks.EventLog events = new TestingTasks.EventLog(clock);
            TaskScheduler scheduler = new TaskScheduler(single,
                    SchedulingMode.FAIL_FAST, events);
            scheduler.run(graph, priorities, new ResourceBudget(4, 4096), null,
                    (task, cancel) -> new TaskResult(task.name(),
                            task.taskType(), TaskStatus.SUCCEEDED, List.of(),
                            TaskTimings.ZERO, 0));
            List<String> order = events.startOrder();
            assertEquals(List.of("heavy", "light"), order,
                    "heaviest critical path runs first");
        } finally {
            single.shutdownNow();
        }
    }

    @Test
    void mustRunAfterOrdersIndependentTasks() throws IOException {
        write("src/a.txt", "a");
        TestingTasks.RecordingTask first = copyTask("first", "src/a.txt",
                "first.txt");
        TestingTasks.RecordingTask second = copyTask("second", "src/a.txt",
                "second.txt");
        TaskGraph graph = TaskGraph.builder()
                .register(first)
                .register(second)
                .mustRunAfter("second", "first")
                .build();
        BuildStore store = new EngineMemoryStore();
        TestingTasks.EventLog events = new TestingTasks.EventLog(clock);
        BuildEngine engine = BuildEngine.builder()
                .roots(roots())
                .store(store)
                .clock(clock)
                .executor(pool)
                .events(events)
                .outputCacheDir(cacheDir)
                .build();
        engine.build(graph, noDirty(), snapshots(roots()), null);
        List<String> order = events.startOrder();
        assertTrue(order.indexOf("first") < order.indexOf("second"),
                "second runs after first: " + order);
    }

    @Test
    void pureOrderCycleDoesNotDeadlock() throws Exception {
        write("src/a.txt", "a");
        TestingTasks.RecordingTask x = copyTask("x", "src/a.txt", "x.txt");
        TestingTasks.RecordingTask y = copyTask("y", "src/a.txt", "y.txt");
        TaskGraph graph = TaskGraph.builder()
                .register(x)
                .register(y)
                .mustRunAfter("x", "y")
                .mustRunAfter("y", "x")
                .build();
        BuildStore store = new EngineMemoryStore();
        BuildEngine engine = engine(store, roots());
        java.util.concurrent.FutureTask<BuildReport> guarded =
                new java.util.concurrent.FutureTask<>(
                        () -> engine.build(graph, noDirty(), snapshots(roots()),
                                null));
        Thread buildThread = new Thread(guarded);
        buildThread.start();
        BuildReport report = guarded.get(60, TimeUnit.SECONDS);
        assertEquals(2, report.count(TaskStatus.SUCCEEDED),
                "a pure ordering cycle is best effort, never a deadlock: "
                        + report);
    }

    private static void copyRecursively(Path source, Path target)
            throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path file : walk.sorted().toList()) {
                Path relative = source.relativize(file);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(file)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(file, destination);
                }
            }
        }
    }
}
