package jo.joide.vfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import jo.joide.store.FileStamp;
import org.junit.jupiter.api.Test;

/**
 * Contrôle du journal de changements fusionné : priorité au plus
 * récent, déduplication des sources, ensemble vide en temps constant
 * et débordement exigeant un re-scan.
 */
class ChangeLogTest {

    private static VfsSnapshot snapshotOf(String... paths) {
        return VfsSnapshot.of(java.util.Arrays.stream(paths)
                .map(p -> new FileStamp(p, 1, 1, null, null))
                .toList());
    }

    @Test
    void emptyDrainIsImmediateAndReusable() {
        ChangeLog log = new ChangeLog(100);
        ChangeLog.DrainResult first = log.drain(snapshotOf("a.txt"));
        assertTrue(first.dirty().isEmpty());
        assertFalse(first.rescanRequired());
        ChangeLog.DrainResult second = log.drain(snapshotOf("a.txt"));
        assertTrue(second.dirty().isEmpty());
        assertEquals(0, second.drainedEvents());
    }

    @Test
    void latestChangeWinsPerPath() {
        ChangeLog log = new ChangeLog(100);
        log.record("a.txt", FileChange.CREATED);
        log.record("a.txt", FileChange.DELETED);
        log.record("b.txt", FileChange.MODIFIED);
        ChangeLog.DrainResult result = log.drain(snapshotOf("a.txt", "b.txt"));
        assertEquals(Set.of(), result.dirty().added());
        assertEquals(Set.of("b.txt"), result.dirty().modified());
        assertEquals(Set.of("a.txt"), result.dirty().removed());
    }

    @Test
    void createdThenDeletedVanishes() {
        ChangeLog log = new ChangeLog(100);
        log.record("nouveau.txt", FileChange.CREATED);
        log.record("nouveau.txt", FileChange.DELETED);
        ChangeLog.DrainResult result = log.drain(VfsSnapshot.empty());
        assertTrue(result.dirty().isEmpty());
    }

    @Test
    void modifiedUnknownFileBecomesAdded() {
        ChangeLog log = new ChangeLog(100);
        log.record("c.txt", FileChange.MODIFIED);
        ChangeLog.DrainResult result = log.drain(VfsSnapshot.empty());
        assertEquals(Set.of("c.txt"), result.dirty().added());
    }

    @Test
    void editorAndWatcherDeduplicate() {
        ChangeLog log = new ChangeLog(100);
        log.record("a.txt", FileChange.MODIFIED); // éditeur
        log.record("a.txt", FileChange.MODIFIED); // surveilleur
        log.record("b.txt", FileChange.DELETED);  // éditeur
        ChangeLog.DrainResult result = log.drain(snapshotOf("a.txt", "b.txt"));
        assertEquals(3, result.drainedEvents(),
                "trois evenements lus, fusionnes en deux chemins");
        assertEquals(Set.of("a.txt"), result.dirty().modified());
        assertEquals(Set.of("b.txt"), result.dirty().removed());
        assertEquals(2, result.dirty().size());
    }

    @Test
    void floodingBeyondCapacityRequiresRescan() {
        ChangeLog log = new ChangeLog(64);
        for (int i = 0; i < 500; i++) {
            log.record("f" + i + ".txt", FileChange.MODIFIED);
        }
        assertTrue(log.needsRescan());
        ChangeLog.DrainResult result = log.drain(snapshotOf());
        assertTrue(result.rescanRequired(),
                "une inondation d'evenements doit exiger un re-scan");
        // la file s'arrête un événement après la capacité : le 65e
        // déclenche le débordement, les suivants sont refusés
        assertEquals(65, result.drainedEvents());
        assertFalse(log.needsRescan(),
                "l'exigence est consommee par le vidage");
    }

    @Test
    void watcherOverflowIsPropagated() {
        ChangeLog log = new ChangeLog(64);
        log.markOverflow();
        ChangeLog.DrainResult result = log.drain(snapshotOf("a.txt"));
        assertTrue(result.rescanRequired());
    }
}
