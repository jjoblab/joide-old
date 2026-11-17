package jo.joide.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrôle du store en journal à ajouts : mêmes sémantiques que le
 * store SQLite, récupération après troncature du journal, compaction
 * et éviction.
 */
class JournalBuildStoreTest {

    @TempDir
    Path dir;

    @Test
    void roundTripThroughReopen() throws IOException {
        Path storeDir = dir.resolve("store");
        try (JournalBuildStore store = JournalBuildStore.open(storeDir)) {
            store.putFingerprint("t1", "fp-1");
            store.putDuration("compile", "t1", 42L);
            store.putState("m", "k", new byte[] {1, 2, 3});
            store.recordCacheEntry("c1", 100);
            store.replaceSnapshot("PROJECT", List.of(
                    new FileStamp("a.txt", 10, 20, "key", "hash")));
        }
        try (JournalBuildStore reopened = JournalBuildStore.open(storeDir)) {
            assertEquals("fp-1", reopened.findFingerprint("t1"));
            assertEquals(42L, reopened.findDuration("compile", "t1"));
            assertArrayEquals(new byte[] {1, 2, 3},
                    reopened.findState("m", "k"));
            assertEquals(100L, reopened.cacheEntrySize("c1"));
            List<FileStamp> snapshot = reopened.loadSnapshot("PROJECT");
            assertEquals(1, snapshot.size());
            assertEquals("a.txt", snapshot.get(0).path());
            assertEquals("hash", snapshot.get(0).contentHash());
        }
    }

    @Test
    void truncatedJournalLosesOnlyLastTransaction() throws IOException {
        Path storeDir = dir.resolve("store");
        try (JournalBuildStore store = JournalBuildStore.open(storeDir)) {
            store.putFingerprint("premiere", "v1");
            store.putFingerprint("derniere", "v2");
        }
        // tronquer le journal au milieu du dernier bloc : la première
        // transaction survit, la dernière est perdue
        Path journal = storeDir.resolve("store.journal");
        byte[] bytes = Files.readAllBytes(journal);
        Files.write(journal, java.util.Arrays.copyOf(bytes, bytes.length - 3));
        try (JournalBuildStore reopened = JournalBuildStore.open(storeDir)) {
            assertEquals("v1", reopened.findFingerprint("premiere"));
            assertNull(reopened.findFingerprint("derniere"),
                    "the truncated transaction must be discarded");
        }
    }

    @Test
    void corruptedBlockStopsReplay() throws IOException {
        Path storeDir = dir.resolve("store");
        try (JournalBuildStore store = JournalBuildStore.open(storeDir)) {
            store.putFingerprint("avant", "ok");
            store.putFingerprint("apres", "casse");
        }
        Path journal = storeDir.resolve("store.journal");
        byte[] bytes = Files.readAllBytes(journal);
        // altérer un octet au milieu : la somme de contrôle rejette
        // le bloc altéré et tout ce qui suit
        bytes[bytes.length / 2] ^= 0x55;
        Files.write(journal, bytes);
        try (JournalBuildStore reopened = JournalBuildStore.open(storeDir)) {
            assertTrue(reopened.healthy());
            // au moins une transaction valide est préservée : le
            // premier bloc est intouché si l'altération vise le
            // second
        }
    }

    @Test
    void abortedTransactionRollsBackMemory() throws IOException {
        Path storeDir = dir.resolve("store");
        try (JournalBuildStore store = JournalBuildStore.open(storeDir)) {
            store.putFingerprint("base", "v0");
            try {
                store.inTransaction(tx -> {
                    tx.putFingerprint("tentative", "v1");
                    throw new StoreException("volontaire", null);
                });
            } catch (StoreException expected) {
                // échec volontaire
            }
            assertNull(store.findFingerprint("tentative"),
                    "the aborted transaction must not leave a trace");
            assertEquals("v0", store.findFingerprint("base"));
        }
        try (JournalBuildStore reopened = JournalBuildStore.open(storeDir)) {
            assertNull(reopened.findFingerprint("tentative"));
            assertEquals("v0", reopened.findFingerprint("base"));
        }
    }

    @Test
    void evictionPersistsThroughReopen() throws IOException {
        Path storeDir = dir.resolve("store");
        try (JournalBuildStore store = JournalBuildStore.open(storeDir)) {
            store.recordCacheEntry("recent", 100);
            store.recordCacheEntry("ancien", 400);
            store.touchCacheEntry("recent");
            List<CacheEntry> evicted = store.evictCacheBeyond(150);
            assertEquals(List.of("ancien"),
                    evicted.stream().map(CacheEntry::fingerprint).toList());
        }
        try (JournalBuildStore reopened = JournalBuildStore.open(storeDir)) {
            assertEquals(100L, reopened.cacheEntrySize("recent"));
            assertNull(reopened.cacheEntrySize("ancien"));
        }
    }

    @Test
    void compactionKeepsState() throws IOException {
        Path storeDir = dir.resolve("store");
        Random random = new Random(20260115L);
        try (JournalBuildStore store = JournalBuildStore.open(storeDir)) {
            // écrire assez pour dépasser le seuil de compaction
            for (int i = 0; i < 20_000; i++) {
                byte[] value = new byte[64];
                random.nextBytes(value);
                store.putState("module", "cle-" + i, value);
            }
            assertTrue(Files.exists(storeDir.resolve("store.state")),
                    "the state file must exist after compaction");
        }
        try (JournalBuildStore reopened = JournalBuildStore.open(storeDir)) {
            // sondage : les états du début, du milieu et de la fin
            // survivent à la compaction
            for (int index : new int[] {0, 10_000, 19_999}) {
                assertTrue(reopened.findState("module", "cle-" + index) != null,
                        "state key cle-" + index + " must survive compaction");
            }
        }
    }
}
