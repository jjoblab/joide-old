package jo.joide.build.jvm.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.platform.RootName;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.TaskGraph;
import jo.joide.tasks.TaskStatus;

/**
 * Acceptation du pipeline JVM : build propre multi-modules Java+Kotlin,
 * build sans changement entièrement à jour, corps de méthode amont
 * sans recompilation aval, signature publique recompilant les
 * dépendants et eux seuls.
 */
class PipelineAcceptanceTest {

    @TempDir
    Path tempDir;

    private static Path stdlibJar;

    @BeforeAll
    static void locateStdlib() {
        String classpath = System.getProperty("java.class.path");
        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            if (entry.contains("kotlin-stdlib")) {
                stdlibJar = Path.of(entry);
                return;
            }
        }
        stdlibJar = null;
    }

    private List<JvmTaskFactory.ModuleSpec> modules(Path project) {
        JvmTaskFactory.ModuleSpec lib = new JvmTaskFactory.ModuleSpec("lib");
        lib.sourceRoots.add(RootedPath.of(RootName.PROJECT,
                "lib/src/main/java"));
        lib.sourceRoots.add(RootedPath.of(RootName.PROJECT,
                "lib/src/main/kotlin"));
        JvmTaskFactory.ModuleSpec app = new JvmTaskFactory.ModuleSpec("app");
        app.sourceRoots.add(RootedPath.of(RootName.PROJECT,
                "app/src/main/java"));
        app.sourceRoots.add(RootedPath.of(RootName.PROJECT,
                "app/src/main/kotlin"));
        app.moduleDependencies.add("lib");
        List<JvmTaskFactory.ModuleSpec> modules = new ArrayList<>();
        modules.add(lib);
        modules.add(app);
        return modules;
    }

    @Test
    void cleanBuildThenNoChangeIsEntirelyUpToDate() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        Path out = Files.createDirectories(tempDir.resolve("out"));
        PipelineHarness.writeTwoModuleProject(project);
        try (PipelineHarness harness = new PipelineHarness(project, out,
                modules(project), stdlibJar)) {
            TaskGraph graph = harness.graph();
            var report = harness.build(graph, PipelineHarness.dirty(
                    PipelineHarness.allProjectFiles(project), Set.of(),
                    Set.of()));
            PipelineHarness.assertSuccessful(report);
            assertTrue(Files.exists(out.resolve(
                    "jvm/lib/classes-java/com/example/lib/Lib.class")));
            assertTrue(Files.exists(out.resolve(
                    "jvm/lib/classes-kotlin/com/example/lib/Tool.class")));
            assertTrue(Files.exists(out.resolve(
                    "jvm/app/classes-java/com/example/app/App.class")));
            assertTrue(Files.exists(out.resolve(
                    "jvm/app/libs/app.jar")));
            assertTrue(Files.exists(out.resolve(
                    "jvm/lib/abi-java/current")));

            var second = harness.build(graph, PipelineHarness.dirty(
                    Set.of(), Set.of(), Set.of()));
            PipelineHarness.assertSuccessful(second);
            assertEquals(0, PipelineHarness.executedTasks(second).size(),
                    () -> PipelineHarness.executedTasks(second).toString());
            assertEquals(second.results().size(),
                    PipelineHarness.upToDateTasks(second).size());
        }
    }

    @Test
    void methodBodyChangeUpstreamKeepsDownstreamUpToDate() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        Path out = Files.createDirectories(tempDir.resolve("out"));
        PipelineHarness.writeTwoModuleProject(project);
        try (PipelineHarness harness = new PipelineHarness(project, out,
                modules(project), stdlibJar)) {
            TaskGraph graph = harness.graph();
            PipelineHarness.assertSuccessful(harness.build(graph,
                    PipelineHarness.dirty(
                            PipelineHarness.allProjectFiles(project),
                            Set.of(), Set.of())));

            // corps de méthode modifié en amont : même ABI
            PipelineHarness.write(project,
                    "lib/src/main/java/com/example/lib/Lib.java", """
                            package com.example.lib;

                            public class Lib {
                                public static final int LIMIT = 10;

                                public String process(String input) {
                                    return "(" + input + " changed body)";
                                }

                                public int counter(int seed) {
                                    return seed + 2;
                                }
                            }
                            """);
            var report = harness.build(graph, PipelineHarness.dirty(
                    Set.of(), Set.of("lib/src/main/java/com/example/lib/Lib.java"),
                    Set.of()));
            PipelineHarness.assertSuccessful(report);

            // la compilation aval ne RECOMPILE aucune classe : les
            // tâches aval sont à jour (manifeste ABI inchangé)
            PipelineHarness.assertStatus(report, "app:compileJava",
                    TaskStatus.UP_TO_DATE);
            PipelineHarness.assertStatus(report, "app:compileKotlin",
                    TaskStatus.UP_TO_DATE);
            PipelineHarness.assertStatus(report, "app:jar",
                    TaskStatus.UP_TO_DATE);
            PipelineHarness.assertStatus(report, "lib:compileJava",
                    TaskStatus.SUCCEEDED);
            PipelineHarness.assertStatus(report, "lib:jar",
                    TaskStatus.SUCCEEDED);
        }
    }

    @Test
    void publicSignatureChangeRecompilesDependentsOnly() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        Path out = Files.createDirectories(tempDir.resolve("out"));
        PipelineHarness.writeTwoModuleProject(project);
        try (PipelineHarness harness = new PipelineHarness(project, out,
                modules(project), stdlibJar)) {
            TaskGraph graph = harness.graph();
            PipelineHarness.assertSuccessful(harness.build(graph,
                    PipelineHarness.dirty(
                            PipelineHarness.allProjectFiles(project),
                            Set.of(), Set.of())));

            // signature publique modifiée en amont
            PipelineHarness.write(project,
                    "lib/src/main/java/com/example/lib/Lib.java", """
                            package com.example.lib;

                            public class Lib {
                                public static final int LIMIT = 10;

                                public String process(String input) {
                                    return "[" + input + "]";
                                }

                                public long counter(int seed) {
                                    return seed + 1L;
                                }
                            }
                            """);
            var report = harness.build(graph, PipelineHarness.dirty(
                    Set.of(), Set.of("lib/src/main/java/com/example/lib/Lib.java"),
                    Set.of()));
            PipelineHarness.assertSuccessful(report);

            // l'aval s'exécute (l'ABI a changé) et recompile les
            // dépendants : App.java (Java) et Extra.kt (Kotlin)
            PipelineHarness.assertStatus(report, "app:compileJava",
                    TaskStatus.SUCCEEDED);
            PipelineHarness.assertStatus(report, "app:compileKotlin",
                    TaskStatus.SUCCEEDED);
            PipelineHarness.assertStatus(report, "lib:compileJava",
                    TaskStatus.SUCCEEDED);
            // le jar aval est refait, preuve que ses entrées ont changé
            PipelineHarness.assertStatus(report, "app:jar",
                    TaskStatus.SUCCEEDED);
        }
    }

    @Test
    void jarIsDeterministicAcrossCleanBuilds() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        PipelineHarness.writeTwoModuleProject(project);
        Path out1 = Files.createDirectories(tempDir.resolve("out1"));
        Path out2 = Files.createDirectories(tempDir.resolve("out2"));
        try (PipelineHarness harness1 = new PipelineHarness(project, out1,
                modules(project), stdlibJar);
                PipelineHarness harness2 = new PipelineHarness(project, out2,
                        modules(project), stdlibJar)) {
            PipelineHarness.assertSuccessful(harness1.build(harness1.graph(),
                    PipelineHarness.dirty(
                            PipelineHarness.allProjectFiles(project),
                            Set.of(), Set.of())));
            PipelineHarness.assertSuccessful(harness2.build(harness2.graph(),
                    PipelineHarness.dirty(
                            PipelineHarness.allProjectFiles(project),
                            Set.of(), Set.of())));
            byte[] jar1 = Files.readAllBytes(
                    out1.resolve("jvm/app/libs/app.jar"));
            byte[] jar2 = Files.readAllBytes(
                    out2.resolve("jvm/app/libs/app.jar"));
            assertEquals(jar1.length, jar2.length);
            assertNotEquals(0, jar1.length);
            assertTrue(java.util.Arrays.equals(jar1, jar2),
                    "jars of identical builds must be byte-identical");
        }
    }

    @Test
    void compileErrorFailsExplicitlyWithDiagnostics() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        Path out = Files.createDirectories(tempDir.resolve("out"));
        PipelineHarness.writeTwoModuleProject(project);
        PipelineHarness.write(project,
                "app/src/main/java/com/example/app/Broken.java", """
                        package com.example.app;

                        public class Broken {
                            int broken() {
                                return "pas un entier";
                            }
                        }
                        """);
        try (PipelineHarness harness = new PipelineHarness(project, out,
                modules(project), stdlibJar)) {
            Set<String> all = new TreeSet<>(
                    PipelineHarness.allProjectFiles(project));
            var report = harness.build(harness.graph(),
                    PipelineHarness.dirty(all, Set.of(), Set.of()));
            assertTrue(!report.successful());
            assertTrue(report.failures().stream().anyMatch(
                    r -> r.diagnostics().stream().anyMatch(
                            p -> p.code().startsWith("ecj."))),
                    () -> report.failures().toString());
        }
    }

}
