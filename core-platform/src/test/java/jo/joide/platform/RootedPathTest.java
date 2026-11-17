package jo.joide.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Contrôle des chemins ancrés à une racine : analyse, construction,
 * ascendance et rejets.
 */
class RootedPathTest {

    @Test
    void parseAndToString() {
        assertEquals("$PROJECT", RootedPath.parse("$PROJECT").toString());
        assertEquals("$PROJECT", RootedPath.parse("$PROJECT/").toString());
        assertEquals("$OUT/classes", RootedPath.parse("$OUT/classes").toString());
        assertEquals("a/b/c", RootedPath.parse("$M2/a/b/c").relative());
        assertEquals(RootName.SDK, RootedPath.parse("$SDK").root());
    }

    @Test
    void buildAndNavigate() {
        RootedPath base = RootedPath.of(RootName.OUT, "");
        RootedPath classes = base.resolve("classes");
        RootedPath file = classes.resolve("Main.class");
        assertEquals(2, file.depth());
        assertEquals(classes, file.parent());
        assertEquals("Main.class", file.fileName());
        assertEquals(RootedPath.of(RootName.OUT, "classes"), classes);
    }

    @Test
    void orderingIsStable() {
        RootedPath a = RootedPath.parse("$PROJECT/a");
        RootedPath b = RootedPath.parse("$PROJECT/b");
        RootedPath c = RootedPath.parse("$OUT/a");
        assertEquals(-1, Integer.signum(a.compareTo(b)));
        assertEquals(-1, Integer.signum(c.compareTo(a)));
    }

    @Test
    void rejectsMalformed() {
        assertThrows(IllegalArgumentException.class,
                () -> RootedPath.of(RootName.PROJECT, "/absolu"));
        assertThrows(IllegalArgumentException.class,
                () -> RootedPath.of(RootName.PROJECT, "a//b"));
        assertThrows(IllegalArgumentException.class,
                () -> RootedPath.of(RootName.PROJECT, "a/../b"));
        assertThrows(IllegalArgumentException.class,
                () -> RootedPath.of(RootName.PROJECT, "a\\b"));
        assertThrows(IllegalArgumentException.class,
                () -> RootedPath.parse("PROJECT/a"));
        assertThrows(IllegalArgumentException.class,
                () -> RootedPath.parse("$project/a"));
        assertThrows(IllegalArgumentException.class,
                () -> RootedPath.parse("$PROJECT/a").resolve("b/c"));
        assertThrows(IllegalStateException.class,
                () -> RootedPath.parse("$PROJECT").parent());
    }
}
