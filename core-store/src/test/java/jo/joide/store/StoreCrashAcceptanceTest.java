package jo.joide.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Résilience du store aux meurtres de processus : une machine virtuelle
 * fille écrit sans fin des transactions connues, le parent la tue par
 * signal à un instant aléatoire, puis rouvre le store et vérifie que
 * l'état est exactement celui d'une suite de transactions validées,
 * sans trou ni écriture à moitié appliquée.
 *
 * <p>Deux cents meurtres au total : cent pendant des transactions
 * d'empreintes (chacune dans la fenêtre des mille premières), cent
 * pendant des remplacements complets de snapshot. Après chaque
 * meurtre, le store réouvert est à nouveau écrit pour prouver la
 * reprise.</p>
 */
class StoreCrashAcceptanceTest {

    /** Graine fixe : les instants de meurtre sont reproductibles. */
    private static final long SEED = 20260115L;

    /** Meurtres par mode (deux modes : deux cents au total). */
    private static final int KILLS_PER_MODE = 100;

    @Test
    void storeSurvivesKillDuringThousandTransactions(@TempDir Path work) throws Exception {
        runCrashSeries(work, "fingerprints");
    }

    @Test
    void storeSurvivesKillDuringSnapshotReplacement(@TempDir Path work) throws Exception {
        runCrashSeries(work, "snapshot");
    }

    private void runCrashSeries(Path work, String mode) throws Exception {
        SplittableRandom random = new SplittableRandom(SEED);
        for (int round = 0; round < KILLS_PER_MODE; round++) {
            Path dir = Files.createDirectory(work.resolve("rond-" + round));
            try {
                killOnceAndVerify(dir, mode, 50 + random.nextInt(950), round);
            } catch (AssertionError | RuntimeException e) {
                throw new AssertionError("mode " + mode + ", meurtre " + round
                        + " (graine " + SEED + ") : " + e.getMessage(), e);
            }
        }
    }

    /**
     * Lance la fille, la tue après un nombre aléatoire de transactions
     * vues (entre 50 et 1000), puis vérifie la cohérence du store.
     */
    private void killOnceAndVerify(Path dir, String mode, int killAfter, int round)
            throws Exception {
        Path dbFile = dir.resolve("crash.joide.db");
        Process child;
        try {
            child = JvmSpawn.spawn(StoreCrashChild.class.getName(),
                    mode, dbFile.toString());
        } catch (IOException e) {
            throw new IllegalStateException("cannot spawn crash child", e);
        }
        int seen = 0;
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(
                child.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (seen < killAfter && (line = lines.readLine()) != null) {
                assertTrue(line.startsWith(mode.equals("snapshot") ? "snap " : "tx "),
                        "ligne inattendue : " + line);
                seen++;
            }
            child.destroyForcibly();
        } finally {
            assertTrue(child.waitFor(10, TimeUnit.SECONDS),
                    "la fille " + round + " ne meurt pas après le signal");
        }
        verifyAfterCrash(dbFile, mode, seen);
    }

    /**
     * Rouvre le store tué et vérifie l'état, puis prouve la reprise
     * en écrivant de nouveau.
     */
    private void verifyAfterCrash(Path dbFile, String mode, int seen) {
        try (BuildStore store = SqliteStores.open(dbFile)) {
            assertTrue(store.healthy(), "le store doit être intègre après le signal");
            if ("fingerprints".equals(mode)) {
                verifyFingerprintPrefix(store, seen);
            } else {
                verifySnapshotGeneration(store, seen);
            }
        }
    }

    /**
     * Les empreintes forment un préfixe contigu : la transaction i est
     * présente avec sa valeur et sa durée, ou absente, sans trou.
     */
    private void verifyFingerprintPrefix(BuildStore store, int seen) {
        int committed = 0;
        while (store.findFingerprint(StoreCrashChild.key(committed)) != null) {
            assertEquals(StoreCrashChild.value(committed),
                    store.findFingerprint(StoreCrashChild.key(committed)),
                    "valeur de la transaction " + committed);
            assertEquals((Long) (long) committed,
                    store.findDuration("bench", StoreCrashChild.key(committed)),
                    "durée de la transaction " + committed);
            committed++;
        }
        assertTrue(committed >= seen,
                committed + " transactions validées mais " + seen + " lignes lues");
        assertNull(store.findFingerprint(StoreCrashChild.key(committed + 5000)),
                "aucune clé folle au-delà du préfixe");
        // reprise : écrire la transaction suivante doit marcher
        final int next = committed;
        store.inTransaction(s -> {
            s.putFingerprint(StoreCrashChild.key(next),
                    StoreCrashChild.value(next));
            return null;
        });
        assertEquals(StoreCrashChild.value(committed),
                store.findFingerprint(StoreCrashChild.key(committed)));
    }

    /**
     * Le snapshot est exactement celui d'une génération complète : soit
     * vide (aucune génération validée), soit l'état intégral d'une
     * génération, jamais un mélange.
     */
    private void verifySnapshotGeneration(BuildStore store, int seen) {
        List<FileStamp> entries = store.loadSnapshot("PROJECT");
        if (entries.isEmpty()) {
            assertTrue(seen == 0,
                    "des lignes ont été lues mais aucun snapshot validé");
        } else {
            assertEquals(StoreCrashChild.SNAPSHOT_ENTRIES, entries.size(),
                    "une génération complète ou rien");
            int generation = (int) entries.get(0).size();
            assertEquals(StoreCrashChild.snapshotContent(generation), entries,
                    "le snapshot doit être exactement la génération " + generation);
            assertTrue(generation >= seen - 1,
                    "génération " + generation + " mais " + seen + " lignes lues");
        }
        // reprise : un nouveau remplacement complet doit marcher
        store.inTransaction(s -> {
            s.replaceSnapshot("PROJECT", StoreCrashChild.snapshotContent(0));
            return null;
        });
        assertEquals(StoreCrashChild.snapshotContent(0), store.loadSnapshot("PROJECT"));
    }
}
