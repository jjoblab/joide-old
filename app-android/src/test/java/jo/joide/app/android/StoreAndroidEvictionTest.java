package jo.joide.app.android;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import jo.joide.store.CacheEntry;

/**
 * Politique d'éviction du store Android (partie pure) : les entrées
 * de la plus récemment utilisée à la plus ancienne, on garde tant que
 * le plafond tient, le reste part de la moins récemment utilisée à la
 * plus récente.
 */
class StoreAndroidEvictionTest {

    private static CacheEntry empreinte(String nom, long taille,
                                        long dernierAcces) {
        return new CacheEntry(nom, taille, 0, dernierAcces);
    }

    @Test
    void evictionDeLaMoinsRecentementUtilisee() {
        // ordre du plus récent (a) au plus ancien (d)
        List<CacheEntry> entrees = List.of(
                empreinte("a", 100, 40),
                empreinte("b", 100, 30),
                empreinte("c", 100, 20),
                empreinte("d", 100, 10));

        // plafond 250 : a+b gardées (200), c ferait 300 → c et d
        // évictées, de la moins récente (d) à la plus récente (c)
        List<CacheEntry> evincees = StoreAndroid.eviction(entrees, 250);
        assertEquals(List.of("d", "c"),
                evincees.stream().map(CacheEntry::fingerprint).toList());

        // plafond généreux : rien ne part
        assertTrue(StoreAndroid.eviction(entrees, 1000).isEmpty());

        // plafond nul : tout part, de la moins récente à la plus récente
        assertEquals(List.of("d", "c", "b", "a"),
                StoreAndroid.eviction(entrees, 0).stream()
                        .map(CacheEntry::fingerprint).toList());
    }

    @Test
    void entreePlusGrosseQueLePlafondSeuleEvincee() {
        List<CacheEntry> entrees = List.of(
                empreinte("petite", 10, 20),
                empreinte("enorme", 10_000, 10));
        // plafond 100 : petite gardée, énorme évictée
        List<CacheEntry> evincees = StoreAndroid.eviction(entrees, 100);
        assertEquals(List.of("enorme"),
                evincees.stream().map(CacheEntry::fingerprint).toList());
    }
}
