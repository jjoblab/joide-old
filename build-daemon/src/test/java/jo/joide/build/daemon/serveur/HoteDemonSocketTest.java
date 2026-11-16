package jo.joide.build.daemon.serveur;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.build.daemon.client.DaemonClient;
import jo.joide.build.daemon.protocol.BuildSpec;

/**
 * L'hôte socket du démon (ADR-043) sur le transport par flux du
 * client : salutation, build JVM complet d'un projet réel sur la
 * socket, reconnexion avec compilateurs chauds, mort du démon lue
 * comme fin de canal (le client survit).
 */
class HoteDemonSocketTest {

    @TempDir
    Path temp;

    private static BuildSpec jvmSpec(Path project, Path out, Path m2) {
        // 448 Mo : la tâche de compilation réclame 320 Mo (revendication
        // du graphe JVM), le budget la couvre
        return new BuildSpec(BuildSpec.Kind.JVM,
                project.toString(), out.toString(), m2.toString(), "17",
                2, 448,
                List.of(new BuildSpec.ModuleSpec("app", false,
                        List.of("app/src/main/java"), List.of(), null,
                        null, List.of(), List.of(), List.of())),
                List.of());
    }

    private static void ecrireProjet(Path project) throws IOException {
        Path sources = project.resolve("app/src/main/java/demo");
        Files.createDirectories(sources);
        Files.writeString(sources.resolve("App.java"), """
                package demo;

                public class App {
                    public static String greet() {
                        return "bonjour";
                    }
                }
                """);
    }

    private DaemonClient connecter(int port) throws IOException {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setTcpNoDelay(true);
        return DaemonClient.over(socket.getInputStream(),
                socket.getOutputStream());
    }

    @Test
    void buildCompletSurSocketPuisReconnexionCompilateursChauds()
            throws Exception {
        Path project = Files.createDirectories(temp.resolve("projet"));
        Path out = temp.resolve("out");
        Path m2 = Files.createDirectories(temp.resolve("m2"));
        ecrireProjet(project);

        try (HoteDemonSocket hote = new HoteDemonSocket(
                dir -> jo.joide.store.JournalBuildStore.open(
                        dir.resolve("journal")), 2)) {
            int port = hote.demarrer();
            long chaudsPremier = -1;

            try (DaemonClient client = connecter(port)) {
                DaemonClient.Event hello = client.await("hello", 60);
                assertEquals("joide",
                        hello.body.member("daemon").asString());
                assertTrue(
                        hello.body.member("maxHeapBytes").asLong() > 0);

                client.build(1, jvmSpec(project, out, m2));
                List<String> taches = new ArrayList<>();
                while (true) {
                    DaemonClient.Event event = client.next(300);
                    if ("build-finished".equals(event.type)) {
                        assertTrue(
                                event.body.member("success").asBoolean(),
                                () -> event.toString());
                        assertTrue(event.body.member("executed")
                                .asLong() >= 2, "compile + jar at least");
                        chaudsPremier = event.body.member(
                                "hotCompilers").asLong();
                        break;
                    }
                    if ("task".equals(event.type)) {
                        taches.add(event.body.member("name").asString());
                    }
                }
                assertTrue(taches.contains("app:compileJava"),
                        () -> taches.toString());
                assertTrue(Files.isRegularFile(out.resolve(
                        "jvm/app/libs/app.jar")),
                        "jar must be produced");
            }

            // le client se déconnecte, se reconnecte : le runner (et
            // ses compilateurs chauds) survit à la reconnexion
            try (DaemonClient second = connecter(port)) {
                second.await("hello", 60);
                second.build(2, jvmSpec(project, out, m2));
                DaemonClient.Event fini = second.await(
                        "build-finished", 300);
                assertTrue(fini.body.member("success").asBoolean());
                assertEquals(0L, fini.body.member("executed").asLong(),
                        "nothing to do on a hot rebuild");
                assertEquals(chaudsPremier,
                        fini.body.member("hotCompilers").asLong(),
                        "the runner must be reused across connections");
            }
        }
    }

    @Test
    void mortDuDemonLueCommeFinDeCanal() throws Exception {
        Files.createDirectories(temp.resolve("vide"));
        HoteDemonSocket hote = new HoteDemonSocket(
                dir -> jo.joide.store.JournalBuildStore.open(
                        dir.resolve("journal")), 2);
        int port = hote.demarrer();

        DaemonClient client = connecter(port);
        client.await("hello", 60);
        assertTrue(client.alive());

        // le démon meurt brutalement : la socket d'écoute se ferme,
        // le canal du client tombe — jamais un état zombie
        hote.close();
        boolean mort = false;
        try {
            client.ping(1);
            for (int i = 0; i < 50 && !mort; i++) {
                client.next(5);
            }
        } catch (DaemonClient.DaemonDiedException e) {
            mort = true;
        }
        assertTrue(mort, "the daemon death must surface as "
                + "DaemonDiedException");
        // le fil de lecture meurt avec le canal : attendre sa fin,
        // jamais un demi-mort qui répondrait « vivant »
        assertEquals(0, client.awaitExit(10));
        assertFalse(client.alive());
        client.close();
    }

    @Test
    void budgetAjusteAuTasLeCanalSurvitEtLeBuildTourne() throws Exception {
        Path project = Files.createDirectories(temp.resolve("projet"));
        Path out = temp.resolve("out");
        Path m2 = Files.createDirectories(temp.resolve("m2"));
        ecrireProjet(project);

        try (HoteDemonSocket hote = new HoteDemonSocket(
                dir -> jo.joide.store.JournalBuildStore.open(
                        dir.resolve("journal")), 2)) {
            int port = hote.demarrer();
            try (DaemonClient client = connecter(port)) {
                client.await("hello", 60);
                // lot U/7 : budget d'admission au-dessus du tas réel du
                // démon — le budget s'AJUSTE au tas et le build TOURNE
                // (réclamations plafonnées), le canal sert toujours ;
                // l'ancien refus fermait le build entier sur les
                // appareils à 256 Mio (rapport du 2026-11-17)
                int budgetExcessif = (int) Math.min(Integer.MAX_VALUE,
                        Runtime.getRuntime().maxMemory()
                                / (1024L * 1024L) + 64L);
                BuildSpec tropGros = new BuildSpec(BuildSpec.Kind.JVM,
                        project.toString(), out.toString(),
                        m2.toString(), "17", 2,
                        budgetExcessif,
                        List.of(new BuildSpec.ModuleSpec("app", false,
                                List.of("app/src/main/java"), List.of(),
                                null, null, List.of(), List.of(),
                                List.of())),
                        List.of());
                client.build(1, tropGros);
                DaemonClient.Event fini = client.await(
                        "build-finished", 300);
                assertTrue(fini.body.member("success").asBoolean(),
                        () -> fini.toString());
                assertTrue(Files.isRegularFile(out.resolve(
                        "jvm/app/libs/app.jar")),
                        "the clamped build must still produce the jar");
                // le canal survit au build
                client.ping(9);
                DaemonClient.Event pong = client.await("pong", 60);
                assertEquals(9L, pong.id);
            }
        }
    }
}
