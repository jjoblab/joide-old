package jo.joide.deps.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import jo.joide.deps.Checksums;
import jo.joide.platform.WorkerPools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cache disque partagé : écriture idempotente, lecture vérifiée,
 * corruption retirée, écritures concurrentes, zone d'attente.
 */
class DependencyCacheTest {

    @TempDir
    Path tmp;

    @Test
    void writeThenReadRoundTrips() throws IOException {
        DependencyCache cache = new DependencyCache(tmp.resolve("cache"));
        byte[] content = "contenu déterministe".getBytes();
        String sha = cache.write(content);
        assertEquals(Checksums.sha256Hex(content), sha);
        assertTrue(cache.has(sha));
        assertArrayEquals(content, cache.read(sha));
        // Réécriture : idempotente.
        assertEquals(sha, cache.write(content));
        assertEquals(1, tmp.resolve("cache/files")
                .resolve(sha.substring(0, 2)).toFile().list().length);
    }

    @Test
    void corruptedEntryIsDroppedOnRead() throws IOException {
        DependencyCache cache = new DependencyCache(tmp.resolve("cache"));
        byte[] content = "v1".getBytes();
        String sha = cache.write(content);
        // Corrompre le fichier stocké sous la clé : la lecture doit
        // le retirer et répondre absent.
        Files.write(cache.fileOf(sha), "corrompu".getBytes());
        assertNull(cache.read(sha));
        assertFalse(cache.has(sha));
    }

    @Test
    void concurrentWritesOfTheSameContentAgree() throws Exception {
        DependencyCache cache = new DependencyCache(tmp.resolve("cache"));
        byte[] content = new byte[64 * 1024];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i * 31);
        }
        ExecutorService pool = WorkerPools.newFixed("cache-test", 6);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                futures.add(pool.submit(() -> cache.write(content)));
            }
            String expected = Checksums.sha256Hex(content);
            for (Future<String> f : futures) {
                assertEquals(expected, f.get());
            }
        } finally {
            pool.shutdown();
        }
        assertArrayEquals(content, cache.read(
                Checksums.sha256Hex(content)));
    }

    @Test
    void stagingAreaIsCleanedAndAarDirsReported() throws IOException {
        DependencyCache cache = new DependencyCache(tmp.resolve("cache"));
        // Un reste dans la zone d'attente.
        Files.write(cache.root().resolve("tmp").resolve("reste"),
                "x".getBytes());
        cache.clearStaging();
        assertFalse(Files.exists(cache.root().resolve("tmp/reste")));
        // AAR non extrait : hasAar faux ; après création, vrai.
        String sha = Checksums.sha256OfText("aar");
        assertFalse(cache.hasAar(sha));
        Files.createDirectories(cache.aarDir(sha));
        assertTrue(cache.hasAar(sha));
    }
}
