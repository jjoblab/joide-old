package jo.joide.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Preuve de reproductibilité du générateur : deux générations
 * complètes à graine égale produisent la même
 * racine Merkle, octet pour octet, sur les tiers S et M.
 */
class ReproducibilityTest {

    @TempDir
    Path temp;

    @Test
    void tierSGeneratesTwiceWithIdenticalTreeHash() throws IOException {
        ProjectGenerator.generate(TierSpec.S, dir("s1"));
        ProjectGenerator.generate(TierSpec.S, dir("s2"));
        TreeHasher.Result first = TreeHasher.hashTree(dir("s1"));
        TreeHasher.Result second = TreeHasher.hashTree(dir("s2"));
        assertEquals(first.root, second.root,
                "two generations of tier S must be identical");
        assertEquals(TierSpec.S.totalFiles, first.files,
                "tier S must write exactly its file budget");
        assertEquals(TierSpec.S.totalFiles, second.files);
        assertEquals(first.bytes, second.bytes);
    }

    @Test
    void tierMGeneratesTwiceWithIdenticalTreeHash() throws IOException {
        ProjectGenerator.generate(TierSpec.M, dir("m1"));
        ProjectGenerator.generate(TierSpec.M, dir("m2"));
        TreeHasher.Result first = TreeHasher.hashTree(dir("m1"));
        TreeHasher.Result second = TreeHasher.hashTree(dir("m2"));
        assertEquals(first.root, second.root,
                "two generations of tier M must be identical");
        assertEquals(TierSpec.M.totalFiles, first.files,
                "tier M must write exactly its file budget");
        assertEquals(first.bytes, second.bytes);
    }

    @Test
    void differentSeedsProduceDifferentTrees() throws IOException {
        // Contre-preuve : la graine pilote bien le contenu (S != M).
        ProjectGenerator.generate(TierSpec.S, dir("d1"));
        ProjectGenerator.generate(TierSpec.M, dir("d2"));
        TreeHasher.Result small = TreeHasher.hashTree(dir("d1"));
        TreeHasher.Result medium = TreeHasher.hashTree(dir("d2"));
        assertNotEquals(small.root, medium.root);
    }

    private Path dir(String name) throws IOException {
        Path path = temp.resolve(name);
        Files.createDirectories(path);
        return path;
    }
}
