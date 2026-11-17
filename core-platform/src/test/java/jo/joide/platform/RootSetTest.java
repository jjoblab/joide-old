package jo.joide.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrôle de l'ancrage des racines nommées sur le système de fichiers.
 */
class RootSetTest {

    @Test
    void resolveAnchors(@TempDir Path dir) {
        Map<RootName, Path> anchors = new HashMap<>();
        anchors.put(RootName.PROJECT, dir.resolve("projet"));
        anchors.put(RootName.OUT, dir.resolve("projet").resolve("build"));
        RootSet roots = RootSet.of(anchors);
        Path expected = dir.resolve("projet").toAbsolutePath()
                .resolve("src").resolve("Main.java");
        assertEquals(expected,
                roots.resolve(RootedPath.parse("$PROJECT/src/Main.java")));
    }

    @Test
    void relativizePrefersDeepestAnchor(@TempDir Path dir) {
        Map<RootName, Path> anchors = new HashMap<>();
        anchors.put(RootName.PROJECT, dir.resolve("projet"));
        anchors.put(RootName.OUT, dir.resolve("projet").resolve("build"));
        RootSet roots = RootSet.of(anchors);
        assertEquals(RootedPath.parse("$OUT/classes/App.class"),
                roots.relativize(dir.resolve("projet/build/classes/App.class")));
        assertEquals(RootedPath.parse("$PROJECT/README.md"),
                roots.relativize(dir.resolve("projet/README.md")));
    }

    @Test
    void rejectsUnknownRootAndOutside(@TempDir Path dir) {
        RootSet roots = RootSet.of(Map.of(RootName.PROJECT, dir.resolve("p")));
        assertThrows(IllegalArgumentException.class,
                () -> roots.resolve(RootedPath.parse("$M2/x")));
        assertThrows(IllegalArgumentException.class,
                () -> roots.relativize(dir.resolve("ailleurs")));
        Map<RootName, Path> withNull = new HashMap<>();
        withNull.put(RootName.PROJECT, null);
        assertThrows(NullPointerException.class, () -> RootSet.of(withNull));
    }
}
