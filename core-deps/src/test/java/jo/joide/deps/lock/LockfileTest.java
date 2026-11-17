package jo.joide.deps.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jo.joide.deps.Coordinate;
import org.junit.jupiter.api.Test;

/**
 * Lockfile : lecture, sérialisation, recherches, doublons.
 */
class LockfileTest {

    private static LockfileEntry entry(String ga, String version, String ext,
                                        String sha256, List<LockfileEdge> edges) {
        String[] parts = ga.split(":");
        return new LockfileEntry(
                Coordinate.of(parts[0], parts[1],
                        jo.joide.deps.Version.parse(version), null, ext),
                sha256, 1234, "central", edges);
    }

    private static final String SHA =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void serializeThenParseRoundTrips() {
        LockfileEntry lib = entry("org.example:lib", "1.0", "jar", SHA,
                List.of(new LockfileEdge(
                        Coordinate.parse("org.dep:api:2.0@jar"), EdgeKind.API),
                        new LockfileEdge(
                                Coordinate.parse("org.dep:run:2.1@jar"),
                                EdgeKind.RUNTIME)));
        LockfileEntry api = entry("org.dep:api", "2.0", "jar", SHA, List.of());
        Lockfile lock = Lockfile.of(List.of(api, lib));
        String text = lock.serialize();
        assertTrue(text.startsWith("joide.lock v1\n"));
        // L'ordre est trié par coordonnée : org.dep:api avant org.example:lib.
        int apiAt = text.indexOf("org.dep:api:2.0@jar");
        int libAt = text.indexOf("org.example:lib:1.0@jar");
        assertTrue(apiAt >= 0 && libAt > apiAt);

        Lockfile.ParseResult parsed = Lockfile.parse(text);
        assertTrue(parsed.problems.isEmpty(), () -> parsed.problems.toString());
        assertEquals(2, parsed.lockfile.entries().size());
        LockfileEntry parsedLib = parsed.lockfile.find("org.example:lib",
                "1.0");
        assertEquals(SHA, parsedLib.sha256);
        assertEquals(2, parsedLib.edges.size());
        // Les arêtes sont triées : api avant run.
        assertEquals("api", parsedLib.edges.get(0).target.artifactId());
        assertEquals(EdgeKind.API, parsedLib.edges.get(0).kind);
        assertEquals(EdgeKind.RUNTIME, parsedLib.edges.get(1).kind);
        // Re-sérialisation : identité octet pour octet.
        assertEquals(text, parsed.lockfile.serialize());
    }

    @Test
    void findAndByGaSeeClassifiers() {
        LockfileEntry main = entry("org.example:lib", "1.0", "jar", SHA,
                List.of());
        LockfileEntry sources = new LockfileEntry(
                Coordinate.parse("org.example:lib:1.0:sources@jar"), SHA, 999,
                "central", List.of());
        Lockfile lock = Lockfile.of(List.of(main, sources));
        assertEquals(2, lock.byGa().get("org.example:lib").size());
        assertEquals(main, lock.find("org.example:lib", "1.0"));
        assertNull(lock.find("org.example:lib", "2.0"));
    }

    @Test
    void duplicatesAreRejected() {
        LockfileEntry first = entry("org.example:lib", "1.0", "jar", SHA,
                List.of());
        LockfileEntry twin = entry("org.example:lib", "1.0", "jar", SHA,
                List.of());
        assertThrows(IllegalArgumentException.class,
                () -> Lockfile.of(List.of(first, twin)));
    }

    @Test
    void malformedLinesAreProblems() {
        String text = "joide.lock v1\n"
                + "pas une entrée\n"
                + "org.example:lib:1.0@jar sha256=" + SHA + " size=1 repo=x\n"
                + "  -> ligne cassée\n"
                + "  -> org.dep:api:2.0@jar api\n";
        Lockfile.ParseResult parsed = Lockfile.parse(text);
        assertEquals(2, parsed.problems.size());
        // L'entrée saine et son arête saine sont lues quand même.
        LockfileEntry lib = parsed.lockfile.find("org.example:lib", "1.0");
        assertEquals(1, lib.edges.size());
        assertEquals("api", lib.edges.get(0).target.artifactId());
    }

    @Test
    void badSha256IsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> entry("org.example:lib", "1.0", "jar", "courte",
                        List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> entry("org.example:lib", "1.0", "jar",
                        SHA.toUpperCase(), List.of()));
    }
}
