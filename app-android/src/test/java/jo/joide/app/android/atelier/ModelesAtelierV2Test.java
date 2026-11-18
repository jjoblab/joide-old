package jo.joide.app.android.atelier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Les tests JVM du lot L2 (atelier v2, ADR-083). */
class ModelesAtelierV2Test {

    // ---- ModeleOnglets ----------------------------------------------

    @Test
    void lesBadgesSuiventLExtension() {
        assertEquals(ModeleOnglets.Badge.JAVA,
                ModeleOnglets.Badge.de("Facture.java"));
        assertEquals(ModeleOnglets.Badge.KOTLIN,
                ModeleOnglets.Badge.de("MainActivity.kt"));
        assertEquals(ModeleOnglets.Badge.XML,
                ModeleOnglets.Badge.de("activity_main.xml"));
        assertEquals(ModeleOnglets.Badge.CONFIG,
                ModeleOnglets.Badge.de("joide.toml"));
    }

    @Test
    void ouvrirActiveEtEviteLeDoublon() {
        ModeleOnglets modele = new ModeleOnglets();
        modele.ouvrir("a.java", "A.java", false, false);
        modele.ouvrir("b.xml", "b.xml", false, false);
        modele.ouvrir("a.java", "A.java", true, false);
        assertEquals(2, modele.onglets().size());
        assertTrue(modele.actif().endsWith("a.java"));
        assertTrue(modele.onglets().get(0).modifie);
    }

    @Test
    void fermerReporteLActifAuVoisinDeGauche() {
        ModeleOnglets modele = new ModeleOnglets();
        modele.ouvrir("a.java", "A.java", false, false);
        modele.ouvrir("b.java", "B.java", false, false);
        modele.ouvrir("c.java", "C.java", false, false);
        modele.activer("b.java");
        assertEquals("a.java", modele.fermer("b.java"));
        assertEquals("a.java", modele.actif());
        assertEquals("a.java", modele.fermer("c.java"));
        assertEquals("a.java", modele.actif());
        assertNull(modele.fermer("a.java"));
        assertNull(modele.actif());
        assertTrue(modele.onglets().isEmpty());
    }

    @Test
    void fermerAutresGardeLeCheminDemande() {
        ModeleOnglets modele = new ModeleOnglets();
        modele.ouvrir("a.java", "A.java", false, false);
        modele.ouvrir("b.java", "B.java", false, false);
        modele.ouvrir("c.java", "C.java", false, false);
        modele.fermerAutres("c.java");
        assertEquals(1, modele.onglets().size());
        assertEquals("c.java", modele.actif());
    }

    @Test
    void lePointDiagnosticEstPorteParLonglet() {
        ModeleOnglets modele = new ModeleOnglets();
        modele.ouvrir("a.java", "A.java", true, true);
        assertTrue(modele.onglets().get(0).erreur);
        assertTrue(modele.onglets().get(0).modifie);
        modele.mettreAJour("a.java", false, false);
        assertFalse(modele.onglets().get(0).erreur);
        assertFalse(modele.onglets().get(0).modifie);
    }

    // ---- FormateurFilAriane -----------------------------------------

    @Test
    void lesSegmentsSeDcoupentEtLeSymboleTermine() {
        List<FormateurFilAriane.Segment> segments =
                FormateurFilAriane.segments("app", "jo.compta",
                        "Facture", "libelle()");
        assertEquals(5, segments.size());
        assertEquals("app", segments.get(0).texte());
        assertEquals("jo", segments.get(1).texte());
        assertEquals("compta", segments.get(2).texte());
        assertEquals("Facture", segments.get(3).texte());
        assertTrue(segments.get(4).symbole());
        assertEquals("libelle()", segments.get(4).texte());
        for (int i = 0; i < 4; i++) {
            assertFalse(segments.get(i).symbole());
        }
    }

    @Test
    void lesElementsVidesSontOmisEtLeSymboleSeulEstAccentue() {
        List<FormateurFilAriane.Segment> segments =
                FormateurFilAriane.segments("", "jo.compta",
                        "Facture", "");
        assertEquals(3, segments.size());
        assertTrue(segments.get(2).texte().equals("Facture"));
        assertFalse(segments.get(2).symbole());
    }

    @Test
    void leCheminSeDecoupeEnSegmentsDariane() {
        List<FormateurFilAriane.Segment> segments =
                FormateurFilAriane.segmentsDepuisChemin(
                        "app/src/main/java/jo/compta/Facture.java",
                        "libelle()");
        assertEquals(5, segments.size());
        assertEquals("app", segments.get(0).texte());
        assertEquals("jo", segments.get(1).texte());
        assertEquals("compta", segments.get(2).texte());
        assertEquals("Facture", segments.get(3).texte());
        assertTrue(segments.get(4).symbole());
    }

    // ---- ModeleBarreSymboles ----------------------------------------

    @Test
    void lesTouchesSuiventLOrdreDeLaMaquette() {
        List<ModeleBarreSymboles.Touche> touches =
                ModeleBarreSymboles.touches();
        assertEquals(9, touches.size());
        assertTrue(touches.get(0).tab);
        assertEquals("{", touches.get(1).glyphe);
        assertEquals("⋯", touches.get(8).glyphe);
        assertTrue(touches.get(8).actions);
    }

    @Test
    void lInsertionIntelligenteFermeLesPaires() {
        assertEquals("{}",
                ModeleBarreSymboles.insertionDe(
                        ModeleBarreSymboles.ACCOLADE_OUVRANTE));
        assertEquals("\"\"",
                ModeleBarreSymboles.insertionDe(
                        ModeleBarreSymboles.GUILLEMET));
        assertEquals(";",
                ModeleBarreSymboles.insertionDe(
                        ModeleBarreSymboles.POINT_VIRGULE));
        assertEquals("\t",
                ModeleBarreSymboles.insertionDe(
                        ModeleBarreSymboles.TAB));
    }

    @Test
    void leFlickHautDonneLaVariante() {
        assertEquals(")",
                ModeleBarreSymboles.varianteDe(
                        ModeleBarreSymboles.PARENTHESE_OUVRANTE));
        assertEquals(":",
                ModeleBarreSymboles.varianteDe(
                        ModeleBarreSymboles.POINT_VIRGULE));
        assertNull(ModeleBarreSymboles.varianteDe(
                ModeleBarreSymboles.EGAL));
    }
}
