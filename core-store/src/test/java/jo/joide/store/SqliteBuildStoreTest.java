package jo.joide.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrôle du store de bureau : CRUD, transactions atomiques,
 * réouverture, migrations et reconstruction après corruption.
 */
class SqliteBuildStoreTest {

    private BuildStore openStore(Path dir) {
        return SqliteStores.open(dir.resolve("build.joide.db"));
    }

    @Test
    void fingerprintsRoundTrip(@TempDir Path dir) {
        try (BuildStore store = openStore(dir)) {
            assertNull(store.findFingerprint("compile:app"));
            store.putFingerprint("compile:app", "abc123");
            assertEquals("abc123", store.findFingerprint("compile:app"));
            store.putFingerprint("compile:app", "def456");
            assertEquals("def456", store.findFingerprint("compile:app"));
            assertTrue(store.healthy());
        }
    }

    @Test
    void durationsAndStateRoundTrip(@TempDir Path dir) {
        try (BuildStore store = openStore(dir)) {
            store.putDuration("compile", "app", 4242L);
            assertEquals(4242L, store.findDuration("compile", "app"));
            store.putState("app", "abi", new byte[] {1, 2, 3});
            assertArrayEquals(new byte[] {1, 2, 3}, store.findState("app", "abi"));
            assertNull(store.findDuration("compile", "absent"));
            assertNull(store.findState("app", "absent"));
        }
    }

    @Test
    void snapshotReplaceAndDelta(@TempDir Path dir) {
        try (BuildStore store = openStore(dir)) {
            store.replaceSnapshot("PROJECT", List.of(
                    new FileStamp("a.txt", 1, 10, "k1", null),
                    new FileStamp("b.txt", 2, 20, "k2", "cafe")));
            store.updateSnapshot("PROJECT",
                    List.of(new FileStamp("b.txt", 3, 30, "k2", "beef"),
                            new FileStamp("c.txt", 4, 40, null, null)),
                    List.of("a.txt"));
            List<FileStamp> snapshot = store.loadSnapshot("PROJECT");
            assertEquals(2, snapshot.size());
            assertEquals("b.txt", snapshot.get(0).path());
            assertEquals("beef", snapshot.get(0).contentHash());
            assertEquals("c.txt", snapshot.get(1).path());
            assertEquals(List.of(), store.loadSnapshot("ABSENT"));
        }
    }

    @Test
    void transactionIsAllOrNothing(@TempDir Path dir) {
        try (BuildStore store = openStore(dir)) {
            store.putFingerprint("base", "v0");
            assertThrows(StoreException.class, () ->
                    store.inTransaction(s -> {
                        s.putFingerprint("base", "v1");
                        s.putFingerprint("autre", "x");
                        throw new StoreException("echec volontaire", null);
                    }));
            assertEquals("v0", store.findFingerprint("base"));
            assertNull(store.findFingerprint("autre"));
        }
    }

    @Test
    void nestedTransactionJoinsOuter(@TempDir Path dir) {
        try (BuildStore store = openStore(dir)) {
            String outcome = store.inTransaction(outer -> {
                outer.putFingerprint("k", "v");
                return outer.inTransaction(inner -> {
                    inner.putDuration("compile", "k", 10L);
                    return "ok";
                });
            });
            assertEquals("ok", outcome);
            assertEquals("v", store.findFingerprint("k"));
            assertEquals(10L, store.findDuration("compile", "k"));
        }
    }

    @Test
    void stateSurvivesReopen(@TempDir Path dir) {
        try (BuildStore first = openStore(dir)) {
            first.replaceSnapshot("PROJECT", List.of(
                    new FileStamp("x/y.java", 42, 4242, "inode-7", "deadbeef")));
            first.putFingerprint("t1", "f1");
        }
        try (BuildStore second = openStore(dir)) {
            List<FileStamp> snapshot = second.loadSnapshot("PROJECT");
            assertEquals(1, snapshot.size());
            assertEquals("deadbeef", snapshot.get(0).contentHash());
            assertEquals(4242L, snapshot.get(0).mtimeNs());
            assertEquals("f1", second.findFingerprint("t1"));
        }
    }

    @Test
    void corruptedDatabaseIsRebuiltEmpty(@TempDir Path dir) throws IOException {
        Path dbFile = dir.resolve("build.joide.db");
        Files.write(dbFile, new byte[] {
                83, 81, 76, 105, 116, 101, 32, 33, // en-tête plausible
                12, 34, 56, 78, 90, -1, -2, -3,
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        try (BuildStore store = SqliteStores.open(dbFile)) {
            assertTrue(store.healthy());
            assertNull(store.findFingerprint("anything"));
            store.putFingerprint("apres", "reconstruction");
            assertEquals("reconstruction", store.findFingerprint("apres"));
        }
    }

    @Test
    void cacheIndexEvictsLeastRecentlyUsed(@TempDir Path dir) throws InterruptedException {
        try (BuildStore store = openStore(dir)) {
            store.recordCacheEntry("f-a", 100);
            Thread.sleep(2L);
            store.recordCacheEntry("f-b", 300);
            Thread.sleep(2L);
            store.recordCacheEntry("f-c", 200);
            store.touchCacheEntry("f-a");
            Thread.sleep(2L);
            // plafond 400 : les plus récemment utilisés sont a (touché)
            // puis c ; b, le plus ancien, est évincé (100 + 200 <= 400)
            long freed = store.evictCacheBeyond(400);
            assertEquals(300, freed);
            assertEquals(100L, store.cacheEntrySize("f-a"));
            assertEquals(200L, store.cacheEntrySize("f-c"));
            assertNull(store.cacheEntrySize("f-b"));
        }
    }
}
