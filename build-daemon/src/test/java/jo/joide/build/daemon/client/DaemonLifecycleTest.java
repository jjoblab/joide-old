package jo.joide.build.daemon.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.build.daemon.protocol.BuildSpec;
import jo.joide.build.daemon.protocol.Json;

/**
 * Cycle de vie du démon de bureau : lancement, salutation, build
 * complet d'un projet JVM, compilateurs chauds au second build,
 * arrêt propre.
 */
class DaemonLifecycleTest {

    @TempDir
    Path temp;

    private static DaemonClient spawnDaemon(int heapMb) throws IOException {
        return DaemonClient.spawn(
                Path.of(DaemonClient.currentClasspath()),
                "jo.joide.build.daemon.desktop.DaemonMain", heapMb);
    }

    private static BuildSpec jvmSpec(Path project, Path out, Path m2,
                                     int heapMb) {
        // heapMb = budget d'admission : la tâche de compilation réclame
        // 320 Mo (revendication du graphe JVM), le budget la couvre
        return new BuildSpec(BuildSpec.Kind.JVM,
                project.toString(), out.toString(), m2.toString(), "17",
                4, heapMb,
                List.of(new BuildSpec.ModuleSpec("app", false,
                        List.of("app/src/main/java"), List.of(), null,
                        null, List.of(), List.of(), List.of())),
                List.of());
    }

    private static void writeProject(Path project, String greeting)
            throws IOException {
        Path sources = project.resolve("app/src/main/java/demo");
        Files.createDirectories(sources);
        Files.writeString(sources.resolve("App.java"), """
                package demo;

                public class App {
                    public static String greet() {
                        return "%s";
                    }
                }
                """.formatted(greeting));
    }

    @Test
    void buildsRunAndSecondBuildKeepsCompilersHot() throws Exception {
        Path project = Files.createDirectories(temp.resolve("projet"));
        Path out = temp.resolve("out");
        Path m2 = Files.createDirectories(temp.resolve("m2"));
        writeProject(project, "bonjour");

        try (DaemonClient daemon = spawnDaemon(512)) {
            DaemonClient.Event hello = daemon.await("hello", 60);
            assertEquals("joide", hello.body.member("daemon").asString());
            assertTrue(hello.body.member("maxHeapBytes").asLong() > 0);

            daemon.build(1, jvmSpec(project, out, m2, 448));
            List<String> statuses = new ArrayList<>();
            while (true) {
                DaemonClient.Event event = daemon.next(300);
                if ("build-finished".equals(event.type)) {
                    assertTrue(event.body.member("success").asBoolean(),
                            () -> event.toString());
                    assertTrue(event.body.member("executed").asLong() >= 2,
                            "compile + jar at least");
                    long firstCompilers
                            = event.body.member("hotCompilers").asLong();
                    assertTrue(firstCompilers >= 1);
                    break;
                }
                if ("task".equals(event.type)) {
                    statuses.add(event.body.member("name").asString());
                }
            }
            assertTrue(statuses.contains("app:compileJava"),
                    () -> statuses.toString());
            assertTrue(Files.isRegularFile(out.resolve(
                    "jvm/app/libs/app.jar")), "jar must be produced");

            // second build, rien n'a changé : tout à jour, zéro travail
            daemon.build(2, jvmSpec(project, out, m2, 448));
            DaemonClient.Event second = daemon.await("build-finished", 300);
            assertTrue(second.body.member("success").asBoolean());
            assertEquals(0L, second.body.member("executed").asLong(),
                    "nothing to do on a hot rebuild");
            // la fabrique (et son pilote ecj) n'a pas été recréée :
            // les compilateurs restent chauds
            assertEquals(1L, second.body.member("hotCompilers").asLong(),
                    "the jvm factory must be reused, not recreated");

            daemon.shutdown();
        }
    }

    @Test
    void inProcessModeRunsTheSameRunner() throws Exception {
        // mode équivalent sans processus, choix explicite du câbleur :
        // le même exécuteur, dans la machine virtuelle de l'appelant
        Path project = Files.createDirectories(temp.resolve("projet"));
        Path out = temp.resolve("out");
        Path m2 = Files.createDirectories(temp.resolve("m2"));
        writeProject(project, "en processus");
        try (jo.joide.build.daemon.runner.BuildRunner runner =
                new jo.joide.build.daemon.runner.BuildRunner(
                        dir -> jo.joide.store.JournalBuildStore.open(
                                dir.resolve("journal")), 2)) {
            jo.joide.build.daemon.runner.BuildRunner.Outcome outcome =
                    runner.run(jvmSpec(project, out, m2, 448), null, null,
                            jo.joide.platform.CancelToken.NEVER);
            assertTrue(outcome.success, () -> outcome.problems.toString());
            assertTrue(Files.isRegularFile(out.resolve(
                    "jvm/app/libs/app.jar")));
            assertFalse(outcome.cancelled);
        }
    }

    @Test
    void memoryBudgetAboveHeapIsAdjustedAndTheBuildRuns() throws Exception {
        Path project = Files.createDirectories(temp.resolve("projet"));
        Path out = temp.resolve("out");
        Path m2 = Files.createDirectories(temp.resolve("m2"));
        writeProject(project, "ajuste");
        try (DaemonClient daemon = spawnDaemon(128)) {
            daemon.await("hello", 60);
            // lot U/7 : budget au-dessus du tas du démon — le budget
            // s'AJUSTE au tas et le build TOURNE (la réclamation de la
            // compilation se plafonne au budget) ; refuser fermait le
            // build entier sur les appareils à 256 Mio (rapport du
            // 2026-11-17 : « build refused: memory budget 448 MiB
            // exceeds daemon heap 256 MiB »). La manière éprouvée :
            // le matériel borne, l'outil s'adapte.
            daemon.build(1, jvmSpec(project, out, m2, 4096));
            DaemonClient.Event finished =
                    daemon.await("build-finished", 300);
            assertTrue(finished.body.member("success").asBoolean(),
                    () -> finished.toString());
            assertTrue(Files.isRegularFile(out.resolve(
                    "jvm/app/libs/app.jar")),
                    "the clamped build must still produce the jar");
            daemon.shutdown();
        }
    }
}
