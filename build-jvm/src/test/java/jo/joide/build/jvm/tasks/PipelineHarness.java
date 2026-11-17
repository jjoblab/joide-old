package jo.joide.build.jvm.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jo.joide.platform.Clock;
import jo.joide.platform.RootName;
import jo.joide.platform.RootSet;
import jo.joide.tasks.BuildEngine;
import jo.joide.tasks.BuildReport;
import jo.joide.tasks.TaskResult;
import jo.joide.tasks.TaskGraph;
import jo.joide.tasks.TaskStatus;
import jo.joide.vfs.DirtySet;
import jo.joide.vfs.TreeScanner;
import jo.joide.vfs.VfsSnapshot;

/**
 * Harnais du pipeline JVM : projet multi-modules sur disque, fabrique
 * de tâches, moteur complet (P2), ensembles sales explicites comme le
 * démon les fournira.
 */
final class PipelineHarness implements AutoCloseable {

    final Path project;
    final Path out;
    final ExecutorService pool;
    private final MemoryBuildStore store;
    private final RootSet roots;
    private final JvmTaskFactory factory;

    final Path m2;

    PipelineHarness(Path project, Path out,
                    List<JvmTaskFactory.ModuleSpec> modules,
                    Path stdlibJar) throws IOException {
        this.project = project;
        this.out = out;
        this.m2 = Files.createDirectories(out.resolve("../m2")
                .toAbsolutePath().normalize());
        this.pool = Executors.newFixedThreadPool(4);
        this.store = new MemoryBuildStore();
        Path stdlibInM2 = null;
        if (stdlibJar != null) {
            stdlibInM2 = m2.resolve(stdlibJar.getFileName().toString());
            if (!Files.exists(stdlibInM2)) {
                Files.copy(stdlibJar, stdlibInM2);
            }
        }
        Map<RootName, Path> anchors = new LinkedHashMap<>();
        anchors.put(RootName.PROJECT, project);
        anchors.put(RootName.OUT, out);
        anchors.put(RootName.M2, m2);
        this.roots = RootSet.of(anchors);
        this.factory = new JvmTaskFactory(out, m2, "17")
                .withSourceAnchors(roots);
        for (JvmTaskFactory.ModuleSpec spec : modules) {
            if (stdlibInM2 != null && !spec.externalJars.contains(stdlibInM2)
                    && hasKotlinSource(spec)) {
                spec.externalJars.add(stdlibInM2);
            }
            factory.module(spec);
        }
    }

    private static boolean hasKotlinSource(JvmTaskFactory.ModuleSpec spec) {
        return spec.sourceRoots.stream().anyMatch(
                root -> root.relative().contains("/kotlin")
                        || root.relative().endsWith("kotlin"));
    }

    TaskGraph graph() throws java.io.IOException {
        return factory.buildGraph();
    }

    /** Les racines ancrées du harnais (racine projet « OUT » doublée). */
    RootSet roots() {
        return roots;
    }

    BuildReport build(TaskGraph graph, DirtySet dirty)
            throws IOException {
        BuildEngine engine = BuildEngine.builder()
                .roots(roots)
                .store(store)
                .clock(Clock.system())
                .executor(pool)
                .mode(jo.joide.tasks.SchedulingMode.FAIL_FAST)
                .budget(new jo.joide.tasks.ResourceBudget(8, 4096))
                .outputCacheDir(out.resolveSibling("cache"))
                .build();
        Map<RootName, VfsSnapshot> snapshots = new LinkedHashMap<>();
        Map<RootName, DirtySet> dirtyMap = new LinkedHashMap<>();
        for (Map.Entry<RootName, Path> anchor : roots.asMap().entrySet()) {
            if (Files.isDirectory(anchor.getValue())) {
                snapshots.put(anchor.getKey(), VfsSnapshot.of(
                        TreeScanner.scan(anchor.getValue(), pool,
                                jo.joide.platform.CancelToken.NEVER)));
                dirtyMap.put(anchor.getKey(),
                        anchor.getKey().equals(RootName.PROJECT)
                                ? dirty : DirtySet.empty());
            }
        }
        return engine.build(graph, dirtyMap, snapshots,
                jo.joide.platform.CancelToken.NEVER);
    }

    static DirtySet dirty(Set<String> added, Set<String> modified,
                          Set<String> removed) {
        return DirtySet.of(added, modified, removed);
    }

    /** Construit un projet à deux modules : bibliothèque et application. */
    static void writeTwoModuleProject(Path project) throws IOException {
        write(project, "lib/src/main/java/com/example/lib/Lib.java", """
                package com.example.lib;

                public class Lib {
                    public static final int LIMIT = 10;

                    public String process(String input) {
                        return "[" + input + "]";
                    }

                    public int counter(int seed) {
                        return seed + 1;
                    }
                }
                """);
        write(project, "lib/src/main/kotlin/com/example/lib/Tool.kt", """
                package com.example.lib

                const val MAGIC = 7

                inline fun tag(value: String): String = "<$value>"

                class Tool(val seed: Int) {
                    fun describe(): String = "seed=$seed"
                }
                """);
        write(project, "app/src/main/java/com/example/app/App.java", """
                package com.example.app;

                import com.example.lib.Lib;
                import com.example.lib.Tool;

                public class App {
                    public String render(String name) {
                        Tool tool = new Tool(3);
                        return Lib.LIMIT + tool.describe() + name;
                    }

                    public int useKotlinInline(String value) {
                        return com.example.lib.ToolKt.tag(value).length();
                    }
                }
                """);
        write(project, "app/src/main/kotlin/com/example/app/Extra.kt", """
                package com.example.app

                import com.example.lib.Lib

                class Extra(private val api: Lib) {
                    fun run(input: String): String = api.process(input)
                }
                """);
    }

    static void write(Path project, String relative, String content)
            throws IOException {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /** Chemins relatifs à la racine projet de tous les fichiers. */
    static Set<String> allProjectFiles(Path project) throws IOException {
        Set<String> files = new TreeSet<>();
        try (var stream = Files.walk(project)) {
            stream.filter(Files::isRegularFile).forEach(
                    p -> files.add(project.relativize(p).toString()
                            .replace('\\', '/')));
        }
        return files;
    }

    static List<String> executedTasks(BuildReport report) {
        List<String> executed = new ArrayList<>();
        for (TaskResult result : report.results()) {
            if (result.status() == TaskStatus.SUCCEEDED
                    || result.status() == TaskStatus.FAILED) {
                executed.add(result.name());
            }
        }
        return executed;
    }

    static List<String> upToDateTasks(BuildReport report) {
        List<String> upToDate = new ArrayList<>();
        for (TaskResult result : report.results()) {
            if (result.status() == TaskStatus.UP_TO_DATE) {
                upToDate.add(result.name());
            }
        }
        return upToDate;
    }

    static void assertSuccessful(BuildReport report) {
        assertTrue(report.successful(),
                () -> report.failures().toString());
    }

    static void assertStatus(BuildReport report, String task,
                             TaskStatus status) {
        for (TaskResult result : report.results()) {
            if (result.name().equals(task)) {
                assertEquals(status, result.status(),
                        () -> result.diagnostics().toString());
                return;
            }
        }
        throw new AssertionError("task not found: " + task);
    }

    @Override
    public void close() {
        pool.shutdownNow();
        try {
            pool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        store.close();
    }
}
