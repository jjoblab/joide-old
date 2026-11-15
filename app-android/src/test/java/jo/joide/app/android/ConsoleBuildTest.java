package jo.joide.app.android;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import jo.joide.deps.json.Json;

/**
 * Formatage des lignes de console : chaque type d'événement du
 * protocole P6 devient une ligne lisible — pure fonction du cadre.
 */
class ConsoleBuildTest {

    private static jo.joide.deps.json.JsonValue corps(String json) {
        return jo.joide.deps.json.Json.parse(json).root;
    }

    @Test
    void salutationBuildEtTaches() {
        String salutation = ConsoleBuild.ligne("hello", -1,
                corps("{\"type\":\"hello\",\"maxHeapBytes\":"
                        + (512L * 1024L * 1024L) + "}"));
        assertEquals("démon connecté (tas 512 Mio)", salutation);

        assertEquals("build #3 démarré",
                ConsoleBuild.ligne("build-started", 3,
                        corps("{\"type\":\"build-started\",\"id\":3}")));

        assertEquals("  app:compileJava : réussie",
                ConsoleBuild.ligne("task", 3, corps(
                        "{\"type\":\"task\",\"name\":\"app:compileJava\","
                                + "\"status\":\"SUCCEEDED\"}")));

        assertEquals("  :app:jar : restaurée du cache",
                ConsoleBuild.ligne("task", 3, corps(
                        "{\"type\":\"task\",\"name\":\":app:jar\","
                                + "\"status\":\"FROM_CACHE\"}")));
    }

    @Test
    void finDeBuildAvecProblemes() {
        String reussi = ConsoleBuild.ligne("build-finished", 3, corps(
                "{\"type\":\"build-finished\",\"success\":true,"
                        + "\"executed\":2,\"fromCache\":1,\"upToDate\":0,"
                        + "\"wallMillis\":1500,\"problems\":[]}"));
        assertTrue(reussi.startsWith("build #3 réussi"));
        assertTrue(reussi.contains("2 exécutée(s), 1 du cache"));
        assertTrue(reussi.contains("en 1.5 s"));

        String echoue = ConsoleBuild.ligne("build-finished", 4, corps(
                "{\"type\":\"build-finished\",\"success\":false,"
                        + "\"executed\":0,\"fromCache\":0,\"upToDate\":0,"
                        + "\"problems\":[\"app:compileJava : unknown"
                        + " type Foo\"]}"));
        assertTrue(echoue.startsWith("build #4 échoué"));
        assertTrue(echoue.contains("  ! app:compileJava : unknown"
                + " type Foo"));
    }

    @Test
    void refusEtErreursSansMortDuCanal() {
        assertEquals("build refusé (memory) : warmup above daemon heap",
                ConsoleBuild.ligne("build-refused", 5, corps(
                        "{\"type\":\"build-refused\",\"reason\":\"memory\","
                                + "\"message\":\"warmup above daemon"
                                + " heap\"}")));
        assertEquals("erreur du démon : unknown request type: nimporte",
                ConsoleBuild.ligne("error", 7, corps(
                        "{\"type\":\"error\",\"message\":\"unknown"
                                + " request type: nimporte\"}")));
        assertEquals("inconnu #9",
                ConsoleBuild.ligne("inconnu", 9,
                        corps("{\"type\":\"inconnu\"}")));
    }
}
