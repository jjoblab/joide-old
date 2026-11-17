package jo.joide.vfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import jo.joide.platform.Hash128;
import jo.joide.store.FileStamp;
import org.junit.jupiter.api.Test;

/**
 * Contrôle des empreintes d'arbre : déterminisme, sensibilité au
 * changement et recalcul partiel des dossiers touchés.
 */
class MerkleTreesTest {

    private static FileStamp stamp(String path, long size, long mtimeNs) {
        return new FileStamp(path, size, mtimeNs, "k-" + path, null);
    }

    @Test
    void identicalTreesShareHashes() {
        VfsSnapshot first = VfsSnapshot.of(List.of(
                stamp("a.txt", 1, 10),
                stamp("src/Foo.java", 2, 20),
                stamp("src/sub/Bar.java", 3, 30)));
        VfsSnapshot second = VfsSnapshot.of(List.of(
                stamp("src/sub/Bar.java", 3, 30),
                stamp("src/Foo.java", 2, 20),
                stamp("a.txt", 1, 10)));
        assertEquals(first.dirHashes(), second.dirHashes(),
                "l'empreinte ne depend pas de l'ordre de decouverte");
        assertEquals(Set.of("", "src", "src/sub"), first.dirHashes().keySet());
    }

    @Test
    void changingAFileChangesItsAncestors() {
        VfsSnapshot before = VfsSnapshot.of(List.of(
                stamp("a.txt", 1, 10),
                stamp("src/Foo.java", 2, 20)));
        VfsSnapshot after = VfsSnapshot.of(List.of(
                stamp("a.txt", 1, 10),
                stamp("src/Foo.java", 2, 21)));
        assertNotEquals(before.dirHash("src"), after.dirHash("src"));
        assertNotEquals(before.dirHash(""), after.dirHash(""));
    }

    @Test
    void untouchedSiblingDirKeepsItsHash() {
        VfsSnapshot before = VfsSnapshot.of(List.of(
                stamp("a/x.txt", 1, 10),
                stamp("b/y.txt", 1, 10)));
        VfsSnapshot after = before.withUpdates(
                List.of(stamp("a/x.txt", 1, 11)), Set.of());
        assertEquals(before.dirHash("b"), after.dirHash("b"),
                "un dossier non touche garde son empreinte");
        assertNotEquals(before.dirHash("a"), after.dirHash("a"));
        assertNotEquals(before.dirHash(""), after.dirHash(""));
    }

    @Test
    void deletingAFileUpdatesAncestors() {
        VfsSnapshot before = VfsSnapshot.of(List.of(
                stamp("a/x.txt", 1, 10),
                stamp("a/y.txt", 2, 20)));
        VfsSnapshot after = before.withUpdates(List.of(), Set.of("a/y.txt"));
        assertEquals(1, after.fileCount());
        assertNotEquals(before.dirHash("a"), after.dirHash("a"));
    }

    @Test
    void contentHashParticipatesInTheDigest() {
        VfsSnapshot plain = VfsSnapshot.of(List.of(
                new FileStamp("a.txt", 1, 10, "k", null)));
        VfsSnapshot hashed = VfsSnapshot.of(List.of(
                new FileStamp("a.txt", 1, 10, "k", "cafe")));
        assertNotEquals(plain.dirHash(""), hashed.dirHash(""));
    }

    @Test
    void emptySnapshotHasRootOnly() {
        Map<String, Hash128> hashes = MerkleTrees.computeAll(new java.util.TreeMap<>());
        assertEquals(1, hashes.size());
        assertTrue(hashes.containsKey(""));
        assertNull(VfsSnapshot.empty().dirHash("inconnu"));
    }
}
