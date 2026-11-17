package jo.joide.deps;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Plages de versions : bornes incluses/exclues, formes ouvertes.
 */
class VersionRangeTest {

    @Test
    void closedRangeContainsBoundsOnlyInclusively() {
        VersionRange r = VersionRange.parse("[1.0,2.0]");
        assertTrue(r.contains(Version.parse("1.0")));
        assertTrue(r.contains(Version.parse("1.5")));
        assertTrue(r.contains(Version.parse("2.0")));
        assertFalse(r.contains(Version.parse("2.0.1")));
        assertFalse(r.contains(Version.parse("0.9")));
    }

    @Test
    void halfOpenUpperExcludesTheBound() {
        VersionRange r = VersionRange.parse("[1.0,2.0)");
        assertTrue(r.contains(Version.parse("1.9.9")));
        assertFalse(r.contains(Version.parse("2.0")));
        // Les prépublications de 2.0 sont dans la plage (elles sont
        // inférieures à 2.0), mais hors « latest.release » au choix
        // des métadonnées — c'est au sélecteur d'écarter.
        assertTrue(r.contains(Version.parse("2.0-alpha")));
    }

    @Test
    void openBoundsAreUnbounded() {
        VersionRange low = VersionRange.parse("(,1.0]");
        assertTrue(low.contains(Version.parse("0.1")));
        assertTrue(low.contains(Version.parse("1.0")));
        assertFalse(low.contains(Version.parse("1.0.1")));
        VersionRange high = VersionRange.parse("[1.0,)");
        assertTrue(high.contains(Version.parse("99.0")));
        assertTrue(high.contains(Version.parse("1.0")));
        assertFalse(high.contains(Version.parse("0.9")));
    }

    @Test
    void exactBracketFormPinsBothBounds() {
        VersionRange r = VersionRange.parse("[1.2.3]");
        assertTrue(r.contains(Version.parse("1.2.3")));
        assertFalse(r.contains(Version.parse("1.2.4")));
        assertFalse(r.contains(Version.parse("1.2.2")));
    }

    @Test
    void invalidFormsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> VersionRange.parse("1.0,2.0"));
        assertThrows(IllegalArgumentException.class,
                () -> VersionRange.parse("[1.0"));
        assertThrows(IllegalArgumentException.class,
                () -> VersionRange.parse("[2.0,1.0)"));
        assertThrows(IllegalArgumentException.class,
                () -> VersionRange.parse("[1.0,2.0"));
        assertThrows(IllegalArgumentException.class,
                () -> VersionRange.parse("[]"));
    }
}
