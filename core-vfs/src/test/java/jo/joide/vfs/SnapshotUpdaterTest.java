package jo.joide.vfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import jo.joide.platform.CancelToken;
import jo.joide.platform.WorkerPools;
import jo.joide.store.BuildStore;
import jo.joide.store.FileStamp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cycle complet du snapshot : parcours initial, détection des
 * changements, empreintes paresseuses et relecture depuis le store.
 */
class SnapshotUpdaterTest {

    private BuildStore store;
    private ExecutorService pool;
    private SnapshotUpdater updater;

    @BeforeEach
    void createDependencies(@TempDir Path dir) {
        store = new MemoryStore();
        pool = WorkerPools.newFixed("updater-test", 2);
        updater = new SnapshotUpdater(store, pool);
    }

    @AfterEach
    void closeDependencies() {
        pool.shutdownNow();
        store.close();
    }

    @Test
    void initialScanIsAllAdded(@TempDir Path project) throws IOException {
        Files.writeString(project.resolve("a.txt"), "aaa");
        Files.createDirectories(project.resolve("src"));
        Files.writeString(project.resolve("src/F.java"), "class F {}");

        SnapshotUpdater.UpdateResult result = updater.refresh(
                "PROJECT", VfsSnapshot.empty(), project, CancelToken.NEVER);

        assertEquals(java.util.Set.of("a.txt", "src/F.java"),
                result.dirty().added());
        assertNull(result.snapshot().entry("a.txt").contentHash(),
                "aucune empreinte au premier parcours");
    }

    @Test
    void signalsDecideDirtinessNotHashes(@TempDir Path project) throws IOException {
        Files.writeString(project.resolve("a.txt"), "v1");
        SnapshotUpdater.UpdateResult first = updater.refresh(
                "PROJECT", VfsSnapshot.empty(), project, CancelToken.NEVER);
        SnapshotUpdater.HashResult hashed = updater.ensureHashed(
                "PROJECT", first.snapshot(), project,
                List.of("a.txt"), new StableHasher(1L));
        assertNotNull(hashed.snapshot().entry("a.txt").contentHash());

        // aucun changement : sale nulle part, empreinte conservée
        SnapshotUpdater.UpdateResult second = updater.refresh(
                "PROJECT", hashed.snapshot(), project, CancelToken.NEVER);
        assertTrue(second.dirty().isEmpty());
        assertEquals(hashed.snapshot().entry("a.txt").contentHash(),
                second.snapshot().entry("a.txt").contentHash());

        // contenu change : le fichier est modifié et son empreinte redevient
        // paresseuse
        Files.writeString(project.resolve("a.txt"), "v2 plus long");
        SnapshotUpdater.UpdateResult third = updater.refresh(
                "PROJECT", second.snapshot(), project, CancelToken.NEVER);
        assertEquals(java.util.Set.of("a.txt"), third.dirty().modified());
        assertNull(third.snapshot().entry("a.txt").contentHash());
    }

    @Test
    void addModifyDeleteInOnePass(@TempDir Path project) throws IOException {
        Files.writeString(project.resolve("garde.txt"), "ok");
        Files.writeString(project.resolve("parti.txt"), "bye");
        SnapshotUpdater.UpdateResult base = updater.refresh(
                "PROJECT", VfsSnapshot.empty(), project, CancelToken.NEVER);
        VfsSnapshot before = base.snapshot();

        Files.writeString(project.resolve("nouveau.txt"), "fresh");
        Files.writeString(project.resolve("garde.txt"), "ok change");
        Files.delete(project.resolve("parti.txt"));

        SnapshotUpdater.UpdateResult result = updater.refresh(
                "PROJECT", before, project, CancelToken.NEVER);

        assertEquals(java.util.Set.of("nouveau.txt"), result.dirty().added());
        assertEquals(java.util.Set.of("garde.txt"), result.dirty().modified());
        assertEquals(java.util.Set.of("parti.txt"), result.dirty().removed());
        assertEquals(2, result.snapshot().fileCount());
    }

    @Test
    void snapshotSurvivesStoreReopen(@TempDir Path project) throws IOException {
        Files.createDirectories(project.resolve("src"));
        Files.writeString(project.resolve("src/A.java"), "class A {}");
        SnapshotUpdater.UpdateResult first = updater.refresh(
                "PROJECT", VfsSnapshot.empty(), project, CancelToken.NEVER);
        SnapshotUpdater.HashResult hashed = updater.ensureHashed(
                "PROJECT", first.snapshot(), project,
                List.of("src/A.java"), new StableHasher(1L));

        SnapshotUpdater reloaded = new SnapshotUpdater(store, pool);
        VfsSnapshot fromStore = reloaded.load("PROJECT");
        assertEquals(1, fromStore.fileCount());
        assertEquals(hashed.snapshot().entry("src/A.java").contentHash(),
                fromStore.entry("src/A.java").contentHash());
        assertEquals(hashed.snapshot().dirHash("src"), fromStore.dirHash("src"),
                "l'empreinte d'arbre persiste aussi");
    }

    @Test
    void warmNoChangeDrainCostsNothing(@TempDir Path project) throws IOException {
        Files.writeString(project.resolve("a.txt"), "stable");
        SnapshotUpdater.UpdateResult base = updater.refresh(
                "PROJECT", VfsSnapshot.empty(), project, CancelToken.NEVER);
        VfsSnapshot snapshot = base.snapshot();

        // démon chaud : journal vide, vidage immédiat sans accès disque
        ChangeLog log = new ChangeLog(64);
        ChangeLog.DrainResult drained = log.drain(snapshot);
        assertTrue(drained.dirty().isEmpty());
        assertEquals(0, drained.drainedEvents());
    }

    @Test
    void floodThenRescanRecoversCorrectDirtySet(@TempDir Path project)
            throws IOException {
        Files.writeString(project.resolve("a.txt"), "a");
        Files.writeString(project.resolve("b.txt"), "b");
        SnapshotUpdater.UpdateResult base = updater.refresh(
                "PROJECT", VfsSnapshot.empty(), project, CancelToken.NEVER);
        VfsSnapshot before = base.snapshot();

        // inondation : plus d'événements que la capacité du journal
        ChangeLog log = new ChangeLog(32);
        for (int i = 0; i < 200; i++) {
            log.record("bruit" + i + ".txt", FileChange.MODIFIED);
        }
        ChangeLog.DrainResult drained = log.drain(before);
        assertTrue(drained.rescanRequired());

        // pendant ce temps, le disque a réellement changé
        Files.writeString(project.resolve("a.txt"), "a2");
        Files.writeString(project.resolve("c.txt"), "c");
        Files.delete(project.resolve("b.txt"));

        // le re-scan exigé par le débordement retrouve l'état exact
        SnapshotUpdater.UpdateResult rescan = updater.refresh(
                "PROJECT", before, project, CancelToken.NEVER);
        assertEquals(java.util.Set.of("c.txt"), rescan.dirty().added());
        assertEquals(java.util.Set.of("a.txt"), rescan.dirty().modified());
        assertEquals(java.util.Set.of("b.txt"), rescan.dirty().removed());
    }

    @Test
    void hashingAnUnreadableFileReportsAProblem(@TempDir Path project)
            throws IOException {
        Files.writeString(project.resolve("a.txt"), "a");
        SnapshotUpdater.UpdateResult base = updater.refresh(
                "PROJECT", VfsSnapshot.empty(), project, CancelToken.NEVER);
        Files.delete(project.resolve("a.txt"));

        SnapshotUpdater.HashResult result = updater.ensureHashed(
                "PROJECT", base.snapshot(), project,
                List.of("a.txt"), new StableHasher(1L));
        assertTrue(!result.problems().isEmpty(),
                "un fichier disparu doit produire un probleme, pas un silence");
    }
}
