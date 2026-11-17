package jo.joide.store;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mesure comparative des deux implémentations de BuildStore sur les
 * charges typiques du moteur de tâches : empreintes, durées, états et
 * index de cache au tier L, instantané de fichiers au tier XL.
 *
 * <p>Le test affiche les mesures sans les vérifier : les seuils
 * temporels ne s'appliquent que sur la machine de référence. Les
 * chiffres alimentent la décision d'architecture du store
 * alternatif.</p>
 */
class StoreBenchmarkTest {

    private static final int TASK_COUNT = 1200;
    private static final int STATE_COUNT = 6000;
    private static final int SNAPSHOT_FILES = 30_000;

    @TempDir
    Path dir;

    @Test
    void measuresBothStoresOnEngineWorkloads() throws IOException {
        System.out.println("[bench] charge moteur : " + TASK_COUNT
                + " tâches, " + SNAPSHOT_FILES + " fichiers");
        measure("journal", JournalBuildStore.open(dir.resolve("journal")));
        measure("sqlite", SqliteStores.open(dir.resolve("sqlite.db")));
    }

    private void measure(String name, BuildStore store) throws IOException {
        Random random = new Random(20260115L);
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < TASK_COUNT; i++) {
            keys.add("mod" + (i / 6) + ":task" + i);
        }

        // 1. empreintes + durées + états, une transaction par tâche
        long start = System.nanoTime();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            store.inTransaction(tx -> {
                tx.putFingerprint(key, hash64(key));
                tx.putDuration("compile", key, 100 + key.length());
                return null;
            });
        }
        long fingerprintsNanos = System.nanoTime() - start;

        // 2. lecture de toutes les empreintes
        start = System.nanoTime();
        int found = 0;
        for (String key : keys) {
            if (store.findFingerprint(key) != null) {
                found++;
            }
        }
        long readsNanos = System.nanoTime() - start;
        assertTrue(found == keys.size(), "toutes les empreintes sont lues");

        // 3. index du cache de sorties
        start = System.nanoTime();
        store.inTransaction(tx -> {
            for (int i = 0; i < STATE_COUNT; i++) {
                tx.recordCacheEntry("fp-" + i, 1024);
            }
            return null;
        });
        long cacheNanos = System.nanoTime() - start;

        // 4. instantané complet de 30 000 fichiers, puis relecture
        List<FileStamp> stamps = new ArrayList<>(SNAPSHOT_FILES);
        for (int i = 0; i < SNAPSHOT_FILES; i++) {
            stamps.add(new FileStamp("src/mod" + (i % 200) + "/f" + i + ".txt",
                    100, 200L + i, "inode-" + i, null));
        }
        start = System.nanoTime();
        store.replaceSnapshot("PROJECT", stamps);
        long snapshotNanos = System.nanoTime() - start;
        start = System.nanoTime();
        List<FileStamp> loaded = store.loadSnapshot("PROJECT");
        long loadNanos = System.nanoTime() - start;
        assertTrue(loaded.size() == SNAPSHOT_FILES, "instantané relu complet");

        // 5. réouverture à froid
        store.close();
        start = System.nanoTime();
        BuildStore reopened = "journal".equals(name)
                ? JournalBuildStore.open(dir.resolve("journal"))
                : SqliteStores.open(dir.resolve("sqlite.db"));
        long reopenNanos = System.nanoTime() - start;
        assertTrue(reopened.findFingerprint(keys.get(0)) != null);
        assertTrue(reopened.loadSnapshot("PROJECT").size() == SNAPSHOT_FILES);
        reopened.close();

        System.out.printf("[bench] %-7s écritures tâches   : %6d ms%n",
                name, fingerprintsNanos / 1_000_000);
        System.out.printf("[bench] %-7s lectures empreintes: %6d ms%n",
                name, readsNanos / 1_000_000);
        System.out.printf("[bench] %-7s index cache %d   : %6d ms%n",
                name, STATE_COUNT, cacheNanos / 1_000_000);
        System.out.printf("[bench] %-7s instantané %d  : %6d ms%n",
                name, SNAPSHOT_FILES, snapshotNanos / 1_000_000);
        System.out.printf("[bench] %-7s lecture instantané: %6d ms%n",
                name, loadNanos / 1_000_000);
        System.out.printf("[bench] %-7s réouverture      : %6d ms%n",
                name, reopenNanos / 1_000_000);
    }

    private static String hash64(String seed) {
        Random random = new Random(seed.hashCode());
        return Long.toHexString(random.nextLong())
                + Long.toHexString(random.nextLong());
    }
}
