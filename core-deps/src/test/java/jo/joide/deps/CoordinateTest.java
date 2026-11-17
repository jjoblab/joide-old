package jo.joide.deps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Coordonnées : analyse, forme canonique, ordre.
 */
class CoordinateTest {

    @Test
    void parseAcceptsTheMavenForms() {
        Coordinate c = Coordinate.parse("org.example:lib:1.2.3");
        assertEquals("org.example", c.groupId());
        assertEquals("lib", c.artifactId());
        assertEquals("jar", c.extension());
        assertTrue(c.classifier() == null);

        Coordinate classified = Coordinate.parse(
                "org.example:lib:1.2.3:sources@jar");
        assertEquals("sources", classified.classifier());
        assertEquals("jar", classified.extension());

        Coordinate aar = Coordinate.parse("com.android:app:2.0@aar");
        assertEquals("aar", aar.extension());

        Coordinate pom = Coordinate.parse("org.example:lib:1.2.3@pom");
        assertEquals("pom", pom.extension());
    }

    @Test
    void toStringIsCanonicalWithExplicitExtension() {
        assertEquals("org.example:lib:1.2.3@jar",
                Coordinate.parse("org.example:lib:1.2.3").toString());
        assertEquals("org.example:lib:1.2.3:sources@jar",
                Coordinate.parse("org.example:lib:1.2.3:sources@jar")
                        .toString());
        assertEquals("com.android:app:2.0@aar",
                Coordinate.parse("com.android:app:2.0@aar").toString());
    }

    @Test
    void gaAndKeyIdentifyTheArtifact() {
        Coordinate c = Coordinate.parse("org.example:lib:1.2.3:sources");
        assertEquals("org.example:lib", c.ga());
        assertEquals("1.2.3:sources", c.key());
        assertEquals("1.2.3", Coordinate.parse("org.example:lib:1.2.3").key());
    }

    @Test
    void orderIsGroupThenArtifactThenVersionThenRest() {
        assertTrue(Coordinate.parse("a:b:1.0")
                .compareTo(Coordinate.parse("a:b:2.0")) < 0);
        assertTrue(Coordinate.parse("a:b:1.0")
                .compareTo(Coordinate.parse("a:c:0.1")) < 0);
        assertTrue(Coordinate.parse("a:b:1.0")
                .compareTo(Coordinate.parse("b:a:0.1")) < 0);
        // Même g:a:v : sans classifieur avant avec classifieur.
        assertTrue(Coordinate.parse("a:b:1.0")
                .compareTo(Coordinate.parse("a:b:1.0:sources")) < 0);
        // Même g:a:v:c : aar avant jar (ordre alphabétique).
        assertTrue(Coordinate.parse("a:b:1.0@aar")
                .compareTo(Coordinate.parse("a:b:1.0@jar")) < 0);
    }

    @Test
    void invalidFormsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Coordinate.parse("org.example:lib"));
        assertThrows(IllegalArgumentException.class,
                () -> Coordinate.parse("org.example:lib:"));
        assertThrows(IllegalArgumentException.class,
                () -> Coordinate.parse("::1.0"));
        assertThrows(IllegalArgumentException.class,
                () -> Coordinate.parse("org.example:lib:1.0:"));
        assertThrows(IllegalArgumentException.class,
                () -> Coordinate.parse("org.example:lib:1.0@"));
    }
}
