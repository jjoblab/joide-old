package jo.joide.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.platform.RootName;
import jo.joide.platform.RootSet;
import jo.joide.platform.RootedPath;

/**
 * Cache de sorties adressé par contenu : stockage, restauration,
 * éviction avec suppression des fichiers et écarts.
 */
class OutputCacheTest {

    @TempDir
    Path tempDir;

    @Test
    void storeAndRestoreRoundTrip() throws IOException {
        EngineMemoryStore store = new EngineMemoryStore();
        OutputCache cache = open(store, Long.MAX_VALUE);
        Path produced = Files.createDirectories(tempDir.resolve("produced"));
        Files.writeString(produced.resolve("a.txt"), "contenu-a");
        RootedPath outFile = RootedPath.of(RootName.OUT, "a.txt");

        cache.store("fp-1", TaskOutputs.builder().file(outFile).build(),
                Map.of(outFile, produced.resolve("a.txt")));
        assertTrue(cache.has("fp-1"));

        // restauration vers un autre emplacement : le contenu suit
        Path out2 = Files.createDirectories(tempDir.resolve("out2"));
        cache.restore("fp-1",
                TaskOutputs.builder()
                        .file(RootedPath.of(RootName.OUT, "a.txt")).build(),
                RootSet.of(Map.of(RootName.OUT, out2)));
        assertEquals("contenu-a", Files.readString(out2.resolve("a.txt")));
    }

    @Test
    void foreignEntryIsDiscoveredFromDisk() throws IOException {
        // un projet range une entrée sous une empreinte réelle ; un
        // second projet, au store vierge, ouvre le même répertoire de
        // cache : l'entrée est découverte par balayage disque et
        // restaurable — le cache est partageable entre projets
        String fingerprint = "ab".repeat(32);
        EngineMemoryStore storeA = new EngineMemoryStore();
        OutputCache cacheA = open(storeA, Long.MAX_VALUE);
        Path produced = Files.createDirectories(tempDir.resolve("produced"));
        Files.writeString(produced.resolve("shared.txt"), "partage");
        RootedPath outFile = RootedPath.of(RootName.OUT, "shared.txt");
        cacheA.store(fingerprint,
                TaskOutputs.builder().file(outFile).build(),
                Map.of(outFile, produced.resolve("shared.txt")));
        assertTrue(cacheA.has(fingerprint));

        EngineMemoryStore storeB = new EngineMemoryStore();
        OutputCache cacheB = open(storeB, Long.MAX_VALUE);
        assertTrue(cacheB.has(fingerprint),
                "a fresh store must discover foreign entries on disk");
        Path target = Files.createDirectories(tempDir.resolve("cible"));
        cacheB.restore(fingerprint,
                TaskOutputs.builder()
                        .file(RootedPath.of(RootName.OUT, "shared.txt"))
                        .build(),
                RootSet.of(Map.of(RootName.OUT, target)));
        assertEquals("partage", Files.readString(target.resolve("shared.txt")));
    }

    @Test
    void directoryOutputsAreCachedRecursively() throws IOException {
        EngineMemoryStore store = new EngineMemoryStore();
        OutputCache cache = open(store, Long.MAX_VALUE);
        Path produced = Files.createDirectories(tempDir.resolve("produced"));
        Files.createDirectories(produced.resolve("com/exemple"));
        Files.writeString(produced.resolve("com/exemple/A.txt"), "A");
        Files.writeString(produced.resolve("com/exemple/B.txt"), "B");
        RootedPath dirOut = RootedPath.of(RootName.OUT, "classes");

        cache.store("fp-dir", TaskOutputs.builder().dir(dirOut).build(),
                Map.of(dirOut, produced));
        assertTrue(cache.has("fp-dir"));

        Path out2 = Files.createDirectories(tempDir.resolve("out2"));
        cache.restore("fp-dir", TaskOutputs.builder().dir(dirOut).build(),
                RootSet.of(Map.of(RootName.OUT, out2)));
        assertEquals("A",
                Files.readString(out2.resolve("classes/com/exemple/A.txt")));
        assertEquals("B",
                Files.readString(out2.resolve("classes/com/exemple/B.txt")));
    }

    @Test
    void evictionRemovesOldestEntryFiles() throws IOException {
        EngineMemoryStore store = new EngineMemoryStore();
        OutputCache cache = open(store, Long.MAX_VALUE);
        Path produced = Files.createDirectories(tempDir.resolve("produced"));
        RootedPath out = RootedPath.of(RootName.OUT, "x.txt");

        Files.writeString(produced.resolve("x.txt"), "premier");
        long oldSize = cache.store("fp-old",
                TaskOutputs.builder().file(out).build(),
                Map.of(out, produced.resolve("x.txt")));
        cache.persistWithinTransaction(store);

        Files.writeString(produced.resolve("x.txt"), "second beaucoup plus gros");
        long newSize = cache.store("fp-new",
                TaskOutputs.builder().file(out).build(),
                Map.of(out, produced.resolve("x.txt")));
        cache.persistWithinTransaction(store);
        assertTrue(newSize > oldSize, "second entry is larger");

        // plafond juste assez large pour la seule entrée récente :
        // la plus ancienne est évincée et ses fichiers disparaissent
        OutputCache strict = open(store, newSize);
        assertTrue(strict.has("fp-old"));
        assertTrue(strict.has("fp-new"));
        strict.persistWithinTransaction(store);

        assertTrue(strict.has("fp-new"), "the most recent entry survives");
        assertFalse(strict.has("fp-old"), "the oldest entry is evicted");
        long files = countFiles(tempDir.resolve("cache"));
        assertEquals(1, files, "evicted entry files are deleted");
    }

    @Test
    void dropRemovesEntry() throws IOException {
        EngineMemoryStore store = new EngineMemoryStore();
        OutputCache cache = open(store, Long.MAX_VALUE);
        Path produced = Files.createDirectories(tempDir.resolve("produced"));
        RootedPath out = RootedPath.of(RootName.OUT, "x.txt");
        Files.writeString(produced.resolve("x.txt"), "x");
        cache.store("fp-x", TaskOutputs.builder().file(out).build(),
                Map.of(out, produced.resolve("x.txt")));
        assertTrue(cache.has("fp-x"));
        cache.drop("fp-x");
        assertFalse(cache.has("fp-x"));
        assertEquals(0, cache.totalBytes());
    }

    private OutputCache open(EngineMemoryStore store, long maxBytes)
            throws IOException {
        OutputCache cache = new OutputCache(tempDir.resolve("cache"), maxBytes);
        cache.open(store);
        return cache;
    }

    private static long countFiles(Path directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).count();
        }
    }
}
