package jo.joide.app.android.atelier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Lot L3 de l'atelier v2 (ADR-083) — le cycle de détentes,
 *  l'ancrage au plus proche, le fling, le bouclage des sections et
 *  le saut « problème suivant » de {@link EtatFeuille} (§3.6). */
class EtatFeuilleTest {

    private static final int UTILE = 800;

    @Test
    void leCyclePoigneeReplieMiPleinReplie() {
        EtatFeuille feuille = new EtatFeuille();
        assertEquals(EtatFeuille.Detente.REPLIE, feuille.detente());
        assertEquals(EtatFeuille.Detente.MI, feuille.cycle());
        assertEquals(EtatFeuille.Detente.PLEINE, feuille.cycle());
        assertEquals(EtatFeuille.Detente.REPLIE, feuille.cycle());
    }

    @Test
    void lesHauteursSuiventLesFractionsAnnonces() {
        assertEquals(72, EtatFeuille.hauteurDe(
                EtatFeuille.Detente.REPLIE, UTILE));
        assertEquals(440, EtatFeuille.hauteurDe(
                EtatFeuille.Detente.MI, UTILE));
        assertEquals(720, EtatFeuille.hauteurDe(
                EtatFeuille.Detente.PLEINE, UTILE));
    }

    @Test
    void lAncragePrendLaDetenteLaPlusProche() {
        EtatFeuille feuille = new EtatFeuille();
        // 300 dp : à 40 de mi (440) et 228 du replié → mi
        assertEquals(EtatFeuille.Detente.MI,
                feuille.ancrer(300, UTILE));
        // 90 dp : à 18 du replié et 350 de mi → replié
        assertEquals(EtatFeuille.Detente.REPLIE,
                feuille.ancrer(90, UTILE));
        // borné : 90 % et au-delà → plein
        assertEquals(EtatFeuille.Detente.PLEINE,
                feuille.ancrer(760, UTILE));
        // sous la barre → replié
        assertEquals(EtatFeuille.Detente.REPLIE,
                feuille.ancrer(10, UTILE));
    }

    @Test
    void leFlingSousLeSeuilDemandeLAncrage() {
        EtatFeuille feuille = new EtatFeuille();
        assertNull(feuille.fling(800));
        assertNull(feuille.fling(-1199));
        assertEquals(EtatFeuille.Detente.REPLIE, feuille.detente());
    }

    @Test
    void leFlingAuSeuilSauteLaDetenteSuivanteDansLeSensDuGeste() {
        EtatFeuille feuille = new EtatFeuille();
        assertEquals(EtatFeuille.Detente.MI, feuille.fling(1200));
        assertEquals(EtatFeuille.Detente.PLEINE, feuille.fling(1400));
        // au plein, un fling vers le haut ne dépasse pas le plein
        assertEquals(EtatFeuille.Detente.PLEINE, feuille.fling(2000));
        // vers le bas : plein → mi
        assertEquals(EtatFeuille.Detente.MI, feuille.fling(-1300));
    }

    @Test
    void lesSectionsBouclentDansLesDeuxSens() {
        EtatFeuille feuille = new EtatFeuille();
        assertEquals(EtatFeuille.Section.CONSOLE, feuille.section());
        assertEquals(EtatFeuille.Section.PROBLEMES,
                feuille.sectionSuivante());
        assertEquals(EtatFeuille.Section.JOURNAL,
                feuille.sectionSuivante());
        assertEquals(EtatFeuille.Section.LOGCAT,
                feuille.sectionSuivante());
        // BOUCLAGE : logcat → console
        assertEquals(EtatFeuille.Section.CONSOLE,
                feuille.sectionSuivante());
        // et dans l'autre sens : console → logcat
        assertEquals(EtatFeuille.Section.LOGCAT,
                feuille.sectionPrecedente());
        assertEquals(EtatFeuille.Section.JOURNAL,
                feuille.sectionPrecedente());
    }

    @Test
    void laDetenteSePoseDirectementDepuisLEcran() {
        EtatFeuille feuille = new EtatFeuille();
        feuille.poserDetente(EtatFeuille.Detente.MI);
        assertEquals(EtatFeuille.Detente.MI, feuille.detente());
        feuille.poserDetente(null);
        assertEquals(EtatFeuille.Detente.REPLIE, feuille.detente());
    }

    @Test
    void leFabSeCacheDesQueLaFeuilleDepasseLaBarre() {
        EtatFeuille feuille = new EtatFeuille();
        assertFalse(feuille.cacheLeFab());
        feuille.cycle();
        assertTrue(feuille.cacheLeFab());
    }

    @Test
    void laSectionSePoseDepuisLeRail() {
        EtatFeuille feuille = new EtatFeuille();
        feuille.poserSection(EtatFeuille.Section.LOGCAT);
        assertEquals(EtatFeuille.Section.LOGCAT, feuille.section());
        feuille.poserSection(null);
        assertEquals(EtatFeuille.Section.CONSOLE, feuille.section());
    }
}
