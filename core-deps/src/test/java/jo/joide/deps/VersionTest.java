package jo.joide.deps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Ordre des versions : la spécification de l'ADR-029, cas par cas.
 */
class VersionTest {

    @Test
    void orderingFollowsTheDocumentedRules() {
        // Série croissante représentative.
        assertTrue(less("1.0-alpha", "1.0-beta"));
        assertTrue(less("1.0-beta", "1.0-milestone"));
        assertTrue(less("1.0-milestone", "1.0-rc"));
        assertTrue(less("1.0-rc", "1.0-SNAPSHOT"));
        assertTrue(less("1.0-SNAPSHOT", "1.0"));
        assertTrue(less("1.0", "1.0-sp"));
        assertTrue(less("1.0-a", "1.0-beta"));
        assertTrue(less("1.0-cr", "1.0"));
        assertTrue(less("1.0-ga", "1.0.1"));
        assertTrue(less("1.0", "1.0.1"));
        assertTrue(less("1.0.1", "1.0.2"));
        assertTrue(less("1.0.2", "1.1"));
        assertTrue(less("1.1", "2.0"));
        // Le numérique gagne face au qualificatif ; la fin de liste
        // vaut publication face aux prépublications connues — mais
        // un inconnu (rang au-dessus de sp) dépasse la publication.
        assertTrue(less("1.0-alpha", "1.0.1"));
        assertTrue(less("1.0", "1.0-zebra"));
        assertTrue(less("1.0.1-alpha", "1.0.1"));
        // Inconnu au-dessus de sp, lexicographique entre inconnus.
        assertTrue(less("1.0-sp", "1.0-zebra"));
        assertTrue(less("1.0-aaa2", "1.0-zebra"));
        // Instantané distant concret au-dessus de l'instantané local.
        assertTrue(less("1.0-SNAPSHOT", "1.0-20260101.123456-3"));
    }

    @Test
    void equalityIgnoresTrailingZerosAndReleaseAliases() {
        assertEquals(Version.parse("1.0"), Version.parse("1.0.0"));
        assertEquals(Version.parse("1.0"), Version.parse("1.0.0.0"));
        assertEquals(Version.parse("1.0"), Version.parse("1.0-ga"));
        assertEquals(Version.parse("1.0"), Version.parse("1.0-final"));
        assertEquals(Version.parse("1.0.1"), Version.parse("1.0.1.0"));
        // « a » et « alpha » sont le même qualificatif ; « cr » = « rc ».
        assertEquals(Version.parse("1.0-a"), Version.parse("1.0-alpha"));
        assertEquals(Version.parse("1.0-cr"), Version.parse("1.0-rc"));
        assertEquals(Version.parse("1.0").hashCode(),
                Version.parse("1.0.0").hashCode());
    }

    @Test
    void snapshotFlagComesFromTheLiteralSuffix() {
        assertTrue(Version.parse("1.0-SNAPSHOT").isSnapshot());
        assertTrue(Version.parse("1.0-snapshot").isSnapshot());
        assertFalse(Version.parse("1.0").isSnapshot());
        assertFalse(Version.parse("1.0-20260101.123456-3").isSnapshot());
        assertFalse(Version.parse("1.0-alpha").isSnapshot());
    }

    @Test
    void totalOrderIsConsistentOnAShuffle() {
        String[] raws = {
                "1.0-alpha", "1.0-beta", "1.0-SNAPSHOT", "1.0", "1.0-sp",
                "1.0.1", "1.1", "2.0", "1.0-20260101.123456-3", "0.9",
                "1.0-zebra", "1.0.0", "1.0-ga"
        };
        List<Version> versions = new ArrayList<>();
        for (String raw : raws) {
            versions.add(Version.parse(raw));
        }
        // Trier par paires et vérifier l'antisymétrie du tri complet.
        versions.sort(null);
        for (int i = 1; i < versions.size(); i++) {
            final int k = i;
            assertTrue(versions.get(k - 1).compareTo(versions.get(k)) <= 0,
                    () -> "order broken at " + k + ": "
                            + versions.get(k - 1) + " > " + versions.get(k));
        }
    }

    @Test
    void invalidFormsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Version.parse(""));
        assertThrows(IllegalArgumentException.class, () -> Version.parse(" "));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("-"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse(".."));
    }

    private static boolean less(String a, String b) {
        Version left = Version.parse(a);
        Version right = Version.parse(b);
        assertTrue(left.compareTo(right) < 0, () -> a + " < " + b);
        assertTrue(right.compareTo(left) > 0, () -> b + " > " + a);
        assertFalse(left.equals(right));
        return true;
    }
}
