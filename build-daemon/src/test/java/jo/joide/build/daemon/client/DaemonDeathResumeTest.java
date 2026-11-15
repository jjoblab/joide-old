package jo.joide.build.daemon.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.build.daemon.protocol.BuildSpec;

/**
 * Preuves de mort du démon : kill -9 (en plein build et au repos) et
 * OOM forcé. Dans les deux cas le client survit (la mort du démon est
 * un événement, pas celle de l'appelant), un nouveau démon reprend le
 * contexte depuis le disque — le build repart correct, sans
 * recompilation inutile au second passage.
 */
class DaemonDeathResumeTest {

    @TempDir
    Path temp;

    private static final String MAIN =
            "jo.joide.build.daemon.desktop.DaemonMain";

    /** Budget d'admission des specs : couvre la revendication de la
     *  compilation (320 Mo) en laissant de la marge sous le tas du
     *  démon (512 Mo) — jamais la frontière exacte. */
    private static final int BUILD_HEAP_MB = 448;

    private static DaemonClient spawnDaemon(int heapMb, String... jvmArgs)
            throws IOException {
        return DaemonClient.spawn(
                Path.of(DaemonClient.currentClasspath()), MAIN, heapMb,
                jvmArgs);
    }

    /** Démon à cœur unique : la compilation n'affame pas la machine
     *  de test, les fenêtres de kill sont franches. */
    private static DaemonClient spawnSingleThreadDaemon(int heapMb,
                                                         String... jvmArgs)
            throws IOException {
        List<String> all = new ArrayList<>(List.of(
                "-Djoide.daemon.threads=1"));
        all.addAll(List.of(jvmArgs));
        return spawnDaemon(heapMb, all.toArray(new String[0]));
    }

    /**
     * Projet déterministe en chaîne : petits modules d'échauffement
     * puis gros modules — le point de kill (démarrage du premier gros
     * module) laisse plusieurs secondes de compilation en aval, la
     * fenêtre du kill -9 ne peut pas être manquée même sur une
     * machine chargée. Le pivot de chaque module référence celui du
     * module amont.
     */
    private static void writeProject(Path project, int smallModules,
                                     int smallFiles, int bigModules,
                                     int bigFiles, int seed)
            throws IOException {
        int modules = smallModules + bigModules;
        for (int m = 0; m < modules; m++) {
            int files = m < smallModules ? smallFiles : bigFiles;
            Path sources = project.resolve("mod" + m
                    + "/src/main/java/demo/m" + m);
            Files.createDirectories(sources);
            for (int f = 0; f < files; f++) {
                StringBuilder code = new StringBuilder();
                code.append("package demo.m").append(m).append(";\n\n");
                code.append("public class C").append(f).append(" {\n");
                if (m > 0 && f == 0) {
                    code.append("    public static int id() {\n");
                    code.append("        return demo.m").append(m - 1)
                            .append(".C0.id() + ").append(m * 7
                                    + seed * 1000).append(";\n");
                    code.append("    }\n");
                } else {
                    // corps assez lourd pour que la compilation d'un
                    // gros module prenne son temps ; l'édition change
                    // la taille des fichiers (comme une vraie édition
                    // d'IDE) : la détection par signaux est garantie
                    for (int method = 0; method < 3; method++) {
                        code.append("    public static int v")
                                .append(method).append("() {\n");
                        code.append("        int total = ")
                                .append(m * 1000 + f).append(";\n");
                        code.append("        for (int i = 0; i < 3; i++) {\n");
                        code.append("            total += i * ")
                                .append(method + 1 + seed * 100)
                                .append(";\n");
                        code.append("        }\n");
                        code.append("        return total;\n");
                        code.append("    }\n\n");
                    }
                    code.append("    public static int id() {\n");
                    code.append("        return v0() + v1() + v2();\n");
                    code.append("    }\n");
                }
                code.append("}\n");
                Files.writeString(sources.resolve("C" + f + ".java"),
                        code.toString());
            }
        }
    }

    /** Réécrit uniquement les fichiers du module 0 avec un corps
     *  différent (l'édition d'IDE type : une classe, sa taille
     *  change). */
    private static void editModuleZero(Path project, int smallFiles,
                                       int seed) throws IOException {
        Path sources = project.resolve("mod0/src/main/java/demo/m0");
        for (int f = 0; f < smallFiles; f++) {
            StringBuilder code = new StringBuilder();
            code.append("package demo.m0;\n\n");
            code.append("public class C").append(f).append(" {\n");
            for (int method = 0; method < 3; method++) {
                code.append("    public static int v").append(method)
                        .append("() {\n");
                code.append("        int total = ").append(f)
                        .append(";\n");
                code.append("        for (int i = 0; i < 3; i++) {\n");
                code.append("            total += i * ")
                        .append(method + 1 + seed * 100)
                        .append(";\n");
                code.append("        }\n");
                code.append("        return total;\n");
                code.append("    }\n\n");
            }
            code.append("    public static int id() {\n");
            code.append("        return v0() + v1() + v2();\n");
            code.append("    }\n");
            code.append("}\n");
            Files.writeString(sources.resolve("C" + f + ".java"),
                    code.toString());
        }
    }

    private static BuildSpec spec(Path project, Path out, Path m2,
                                  int modules, int heapMb) {
        List<BuildSpec.ModuleSpec> specs = new ArrayList<>();
        for (int m = 0; m < modules; m++) {
            List<String> deps = m == 0 ? List.of()
                    : List.of("mod" + (m - 1));
            specs.add(new BuildSpec.ModuleSpec("mod" + m, false,
                    List.of("mod" + m + "/src/main/java"), List.of(),
                    null, null, deps, List.of(), List.of()));
        }
        return new BuildSpec(BuildSpec.Kind.JVM,
                project.toString(), out.toString(), m2.toString(), "17",
                4, heapMb, specs, List.of());
    }

    /** Construit et attend la fin ; les événements de tâches sont
     *  ignorés. */
    private static DaemonClient.Event buildAndWait(DaemonClient daemon,
                                                   long id, BuildSpec spec)
            throws IOException {
        daemon.build(id, spec);
        while (true) {
            DaemonClient.Event event = daemon.next(300);
            if ("build-finished".equals(event.type)) {
                return event;
            }
        }
    }

    @Test
    void killNineDuringBuildIsResumedFromDisk() throws Exception {
        Path project = Files.createDirectories(temp.resolve("projet"));
        Path out = temp.resolve("out");
        Path m2 = Files.createDirectories(temp.resolve("m2"));
        // 10 petits modules puis 6 gros : le kill sur le premier gros
        // laisse cinq gros modules (2 000 fichiers) en aval
        final int small = 10;
        final int big = 6;
        writeProject(project, small, 10, big, 400, 0);
        BuildSpec build = spec(project, out, m2, small + big,
                BUILD_HEAP_MB);
        String killPoint = "mod" + (small + 2) + ":compileJava";

        // premier démon : build tué en plein vol par kill -9. La
        // chaîne est séquentielle : attendre la fin du troisième gros
        // module laisse trois gros modules (1 200 fichiers) en aval —
        // à cœur unique, des secondes entières de travail
        try (DaemonClient daemon = spawnSingleThreadDaemon(512)) {
            daemon.await("hello", 60);
            daemon.build(1, build);
            daemon.await("build-started", 60);
            while (true) {
                DaemonClient.Event event = daemon.next(120);
                if ("task".equals(event.type)
                        && "SUCCEEDED".equals(
                                event.body.member("status").asString())
                        && killPoint.equals(
                                event.body.member("name").asString())) {
                    break;
                }
            }
            daemon.killNow();
            boolean finishedBeforeDeath = false;
            try {
                while (true) {
                    DaemonClient.Event event = daemon.next(30);
                    if ("build-finished".equals(event.type)) {
                        finishedBeforeDeath = true;
                        break;
                    }
                }
            } catch (DaemonClient.DaemonDiedException expected) {
                // mort brutale constatée par le client, qui survit
            }
            assertFalse(finishedBeforeDeath,
                    "le kill -9 doit surprendre le build en vol");
            // kill -9 : signal non interceptable, sortie non nulle
            assertTrue(daemon.awaitExit(30) != 0,
                    "un kill -9 ne peut pas donner une sortie nulle");
        }

        // second démon : reprise depuis le disque — l'état des tâches
        // terminées avant le kill est durable (persisté par lot), la
        // reprise ne compile que la queue : les trois gros modules
        // restants au minimum, pas les treize déjà faits
        try (DaemonClient daemon = spawnSingleThreadDaemon(512)) {
            daemon.await("hello", 60);
            DaemonClient.Event resumed = buildAndWait(daemon, 2, build);
            assertTrue(resumed.body.member("success").asBoolean(),
                    () -> resumed.toString());
            long executed = resumed.body.member("executed").asLong();
            assertTrue(executed >= 3 && executed <= small + big,
                    "la reprise compile la queue, pas tout : "
                            + executed + " tâches");
            assertTrue(Files.isRegularFile(out.resolve(
                    "jvm/mod" + (small + big - 1) + "/libs/mod"
                            + (small + big - 1) + ".jar")));
            byte[] jarBefore = Files.readAllBytes(out.resolve(
                    "jvm/mod0/libs/mod0.jar"));

            // sans changement : zéro travail à chaud
            DaemonClient.Event hot = buildAndWait(daemon, 3, build);
            assertTrue(hot.body.member("success").asBoolean());
            assertEquals(0L, hot.body.member("executed").asLong(),
                    "rien à faire après un build complet");

            // édition du corps du module 0 seul : ses signatures
            // publiques (l'ABI) ne changent pas, les dépendants
            // restent à jour — seul mod0 est recompilé + réempaqueté
            editModuleZero(project, 10, 1);
            DaemonClient.Event edited = buildAndWait(daemon, 4, build);
            assertTrue(edited.body.member("success").asBoolean(),
                    () -> edited.toString());
            assertTrue(edited.body.member("executed").asLong() <= 2,
                    "une édition de corps ne recompile pas la chaîne : "
                            + edited.body.member("executed").asLong()
                            + " tâches");
            byte[] jarAfter = Files.readAllBytes(out.resolve(
                    "jvm/mod0/libs/mod0.jar"));
            assertNotEquals(java.util.Arrays.hashCode(jarBefore),
                    java.util.Arrays.hashCode(jarAfter),
                    "le jar du module édité doit changer (executed="
                            + edited.body.member("executed").asLong()
                            + ", upToDate="
                            + edited.body.member("upToDate").asLong()
                            + ")");

            // kill -9 au repos, puis reprise : le contexte du disque
            // épargne tout re-travail
            daemon.killNow();
        }
        try (DaemonClient daemon = spawnSingleThreadDaemon(512)) {
            daemon.await("hello", 60);
            DaemonClient.Event afterIdleKill =
                    buildAndWait(daemon, 5, build);
            assertTrue(afterIdleKill.body.member("success").asBoolean());
            assertEquals(0L, afterIdleKill.body.member("executed")
                    .asLong(),
                    "le kill au repos ne doit rien coûter au retour");
            assertTrue(afterIdleKill.body.member("upToDate").asLong()
                    >= small + big,
                    "au moins une tâche à jour par module");
            daemon.shutdown();
        }
    }

    @Test
    void forcedOomKillsDaemonClientSurvivesAndDiskResumes()
            throws Exception {
        Path project = Files.createDirectories(temp.resolve("projet"));
        Path out = temp.resolve("out");
        Path m2 = Files.createDirectories(temp.resolve("m2"));
        writeProject(project, 4, 10, 0, 0, 0);
        BuildSpec build = spec(project, out, m2, 4, BUILD_HEAP_MB);

        // démon avec injection de pannes armée : le tas couvre le
        // budget d'admission du build (la compilation réclame
        // 320 Mo), l'OOM forcé saturera ce même tas
        try (DaemonClient daemon = spawnDaemon(512,
                "-Djoide.daemon.faults=1")) {
            daemon.await("hello", 60);
            DaemonClient.Event baseline =
                    buildAndWait(daemon, 1, build);
            assertTrue(baseline.body.member("success").asBoolean(),
                    () -> baseline.toString());

            // OOM forcé : le tas est réellement saturé, l'erreur
            // s'échappe, la machine virtuelle meurt
            daemon.crash(99, "oom");
            org.junit.jupiter.api.Assertions.assertThrows(
                    DaemonClient.DaemonDiedException.class,
                    () -> daemon.next(60));
            // le démon meurt sur l'erreur non rattrapée : sortie non
            // nulle, code récolté (la fermeture des flux peut précéder
            // la collecte du statut de quelques instants)
            assertTrue(daemon.awaitExit(30) != 0,
                    "l'OOM forcé doit tuer la machine virtuelle");
        }

        // un nouveau démon reprend depuis le disque : tout est à jour,
        // aucun travail refait
        try (DaemonClient daemon = spawnDaemon(512)) {
            daemon.await("hello", 60);
            DaemonClient.Event resumed = buildAndWait(daemon, 2, build);
            assertTrue(resumed.body.member("success").asBoolean(),
                    () -> resumed.toString());
            assertEquals(0L, resumed.body.member("executed").asLong(),
                    "l'état du disque épargne le re-travail après OOM");
            daemon.shutdown();
        }
    }

    @Test
    void faultInjectionIsRefusedWhenNotArmed() throws Exception {
        Path project = Files.createDirectories(temp.resolve("projet"));
        Path out = temp.resolve("out");
        Path m2 = Files.createDirectories(temp.resolve("m2"));
        writeProject(project, 2, 5, 0, 0, 0);
        BuildSpec build = spec(project, out, m2, 2, BUILD_HEAP_MB);
        try (DaemonClient daemon = spawnDaemon(512)) {
            daemon.await("hello", 60);
            // démon de production : l'injection est refusée, le démon
            // répond et survit
            daemon.crash(7, "oom");
            DaemonClient.Event refused = daemon.await("crash-refused",
                    60);
            assertEquals(7L, refused.id);
            assertTrue(daemon.alive(),
                    "le démon sain refuse l'injection et survit");
            DaemonClient.Event after =
                    buildAndWait(daemon, 8, build);
            assertTrue(after.body.member("success").asBoolean());
            daemon.shutdown();
        }
    }
}
