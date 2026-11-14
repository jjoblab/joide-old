package jo.joide.vfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import jo.joide.platform.CancelToken;
import jo.joide.platform.WorkerPools;
import jo.joide.store.FileStamp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

/**
 * Parcours à froid d'un arbre de cent mille fichiers : la cible de
 * budget est de cinq secondes sur machine de bureau.
 *
 * <p>La préparation de l'arbre (création des fichiers) n'entre pas
 * dans la mesure : seul le parcours de statuts est chronométré, sur
 * un système de fichiers déjà tiède. La mesure est affichée pour le
 * rapport de phase.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ColdScanTest {

    private static final int DIRECTORIES = 100;
    private static final int FILES_PER_DIRECTORY = 1000;
    private static final long BUDGET_MS = 5_000L;

    private ExecutorService pool;
    private Path tree;

    private Path work;

    @BeforeAll
    void growTree(@TempDir Path given) throws IOException {
        work = given;
        pool = WorkerPools.newFixed("cold-scan", WorkerPools.cpuParallelism());
        tree = Files.createDirectory(work.resolve("arbre"));
        byte[] content = new byte[64];
        for (int d = 0; d < DIRECTORIES; d++) {
            Path dir = tree.resolve("d" + d);
            Files.createDirectory(dir);
            for (int f = 0; f < FILES_PER_DIRECTORY; f++) {
                Files.write(dir.resolve("f" + f + ".java"), content);
            }
        }
    }

    @AfterAll
    void stopPool() {
        pool.shutdownNow();
    }

    @Test
    void coldScanOfHundredThousandFilesWithinBudget() throws IOException {
        long start = System.nanoTime();
        List<FileStamp> stamps = TreeScanner.scan(tree, pool, CancelToken.NEVER);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertEquals(DIRECTORIES * FILES_PER_DIRECTORY, stamps.size(),
                "tous les fichiers doivent etre parcourus");
        assertTrue(elapsedMs <= BUDGET_MS,
                "cold-scan de 100 000 fichiers : " + elapsedMs
                        + " ms (budget " + BUDGET_MS + " ms)");
        System.out.println("[cold-scan] 100000 fichiers parcourus en "
                + elapsedMs + " ms");
    }

    @Test
    void snapshotBuildOfHundredThousandFilesWithinBudget() throws IOException {
        List<FileStamp> stamps = TreeScanner.scan(tree, pool, CancelToken.NEVER);
        long buildStart = System.nanoTime();
        VfsSnapshot snapshot = VfsSnapshot.of(stamps);
        long buildMs = (System.nanoTime() - buildStart) / 1_000_000L;
        assertEquals(DIRECTORIES * FILES_PER_DIRECTORY, snapshot.fileCount());
        assertEquals(DIRECTORIES + 1, snapshot.dirHashes().size());
        assertTrue(buildMs <= BUDGET_MS,
                "construction du snapshot : " + buildMs + " ms");
        System.out.println("[cold-scan] snapshot de 100000 fichiers construit en "
                + buildMs + " ms");
    }
}
