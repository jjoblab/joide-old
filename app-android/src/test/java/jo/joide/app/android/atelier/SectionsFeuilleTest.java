package jo.joide.app.android.atelier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Lot L3 de l'atelier v2 (ADR-083) — les états purs des sections :
 *  Problèmes (compteurs, première erreur du fichier actif, saut
 *  « problème suivant » avec bouclage), Journal (bornage, filtres,
 *  sources) et Logcat (pause, filtre, histogramme du débit). */
class SectionsFeuilleTest {

    // ---- EtatProblemes ----------------------------------------------

    @Test
    void lesDiagnosticsSeGroupentParFichierAvecCompteurs() {
        EtatProblemes etat = new EtatProblemes();
        etat.poserFichier("a.java", "A.java", List.of(
                new EtatProblemes.Probleme("a.java", "A.java", 19,
                        "numero introuvable", true),
                new EtatProblemes.Probleme("a.java", "A.java", 8,
                        "champ inutilisé", false)));
        etat.poserFichier("b.java", "B.java", List.of(
                new EtatProblemes.Probleme("b.java", "B.java", 7,
                        "types incompatibles", true)));
        assertEquals(2, etat.erreurs());
        assertEquals(1, etat.avertissements());
        assertEquals(2, etat.parFichier().size());
    }

    @Test
    void remplacerUnFichierNeDoublonnePas() {
        EtatProblemes etat = new EtatProblemes();
        etat.poserFichier("a.java", "A.java", List.of(
                new EtatProblemes.Probleme("a.java", "A.java", 19,
                        "x", true)));
        etat.poserFichier("a.java", "A.java", List.of(
                new EtatProblemes.Probleme("a.java", "A.java", 21,
                        "y", false)));
        assertEquals(0, etat.erreurs());
        assertEquals(1, etat.avertissements());
    }

    @Test
    void laPremiereErreurDuFichierActifPrime() {
        EtatProblemes etat = new EtatProblemes();
        etat.poserFichier("a.java", "A.java", List.of(
                new EtatProblemes.Probleme("a.java", "A.java", 8,
                        "avertissement", false),
                new EtatProblemes.Probleme("a.java", "A.java", 19,
                        "erreur", true)));
        assertEquals(19, etat.premierDuFichier("a.java").ligne());
        assertNull(etat.premierDuFichier("b.java"));
    }

    @Test
    void leSautProblemeSuivantBoucleEtNePrendQueLesErreurs() {
        EtatProblemes etat = new EtatProblemes();
        etat.poserFichier("a.java", "A.java", List.of(
                new EtatProblemes.Probleme("a.java", "A.java", 19,
                        "e1", true),
                new EtatProblemes.Probleme("a.java", "A.java", 8,
                        "a1", false)));
        etat.poserFichier("b.java", "B.java", List.of(
                new EtatProblemes.Probleme("b.java", "B.java", 7,
                        "e2", true)));
        // depuis la ligne 19 de a.java : l'erreur de b.java
        assertEquals("b.java", etat.suivant("a.java", 19).chemin());
        // depuis b.java : BOUCLAGE sur la première erreur de a.java
        assertEquals("a.java",
                etat.suivant("b.java", 7).chemin());
        // un fichier avec SEULEMENT des avertissements : le ↓ SAUTE
        // quand même à l'erreur suivante (les erreurs sont globales,
        // §3.6 — le saut ne regarde pas le fichier courant pour
        // décider, il part de sa position)
        etat.poserFichier("c.java", "C.java", List.of(
                new EtatProblemes.Probleme("c.java", "C.java", 1,
                        "a2", false)));
        assertEquals("a.java", etat.suivant("c.java", 1).chemin());
        // et s'il n'y a AUCUNE erreur : pas de saut
        etat.poserFichier("a.java", "A.java", List.of());
        etat.poserFichier("b.java", "B.java", List.of());
        assertNull(etat.suivant("c.java", 1));
    }

    // ---- EtatJournal -------------------------------------------------

    @Test
    void leJournalSeBorneEtFiltreParNiveau() {
        EtatJournal etat = new EtatJournal();
        for (int i = 0; i < EtatJournal.PLAFOND + 10; i++) {
            etat.ajouter(EtatJournal.Source.INDEX,
                    EtatJournal.Niveau.INFO, "e" + i);
        }
        assertEquals(EtatJournal.PLAFOND, etat.taille());
        // les plus anciens sont sortis : le premier visible est e10
        assertEquals("e10", etat.visibles().get(0).message());
        etat.ajouter(EtatJournal.Source.MCP,
                EtatJournal.Niveau.AVERTISSEMENT, "injoignable");
        etat.poserFiltre(EtatJournal.Niveau.AVERTISSEMENT);
        assertEquals(1, etat.visibles().size());
        assertEquals(EtatJournal.Source.MCP,
                etat.visibles().get(0).source());
        assertEquals("injoignable", etat.dernier().message());
        etat.vider();
        assertEquals(0, etat.taille());
    }

    // ---- EtatLogcat ---------------------------------------------------

    @Test
    void laPauseSauteLesLignesLeFiltreNeLesChangePas() {
        EtatLogcat etat = new EtatLogcat();
        etat.recevoir(EtatLogcat.Niveau.D, "JoCompta", "chargée", 0);
        etat.recevoir(EtatLogcat.Niveau.E, "AndroidRuntime",
                "NullPointerException", 100);
        etat.poserFiltre("android");
        assertEquals(1, etat.visibles().size());
        assertEquals(EtatLogcat.Niveau.E,
                etat.derniere().niveau());
        etat.poserFiltre(null);
        assertEquals(2, etat.visibles().size());
        // en pause : les lignes SAUTENT
        etat.poserPause(true);
        etat.recevoir(EtatLogcat.Niveau.I, "ActivityManager",
                "start", 200);
        assertEquals(2, etat.taille());
        assertTrue(etat.enPause());
    }

    @Test
    void lHistogrammePorteLeDebitEtLaBarreErreur() {
        EtatLogcat etat = new EtatLogcat(9000);
        long maintenant = 10_000;
        // 4 lignes dans la DERNIÈRE seconde (âge 0..999 ms) dont une E
        for (int i = 0; i < 3; i++) {
            etat.recevoir(EtatLogcat.Niveau.I, "T", "m" + i,
                    maintenant - i);
        }
        etat.recevoir(EtatLogcat.Niveau.E, "Runtime", "boom",
                maintenant - 500);
        EtatLogcat.Histogramme histogramme =
                etat.histogramme(maintenant);
        int derniere = EtatLogcat.BARRES_HISTOGRAMME - 1;
        assertTrue(histogramme.hauteurs()[derniere] > 0);
        assertTrue(histogramme.erreurs()[derniere]);
        // une ligne trop ancienne sort de la fenêtre : l'histogramme
        // ne change pas (elle reste dans le transcript, pas dans le débit)
        int[] avant = histogramme.hauteurs().clone();
        etat.recevoir(EtatLogcat.Niveau.I, "T", "vieille", 0);
        assertTrue(java.util.Arrays.equals(avant,
                etat.histogramme(maintenant).hauteurs()),
                "une ligne hors fenêtre ne change pas le débit");
        assertEquals(5, etat.taille());
    }

    @Test
    void leFiltreDuTiroirRetientLesFichiersEtLeursAncetres() {
        List<String> chemins = List.of(
                "app/src/main/java/jo/compta/Facture.java",
                "app/src/main/java/jo/compta/Client.java",
                "app/src/main/res/layout/main.xml",
                "joide.toml");
        List<String> retenus = Arborescence.cheminsFiltres(chemins,
                "facture");
        assertEquals(1, retenus.size());
        assertTrue(retenus.get(0).endsWith("Facture.java"));
        // vide : tout
        assertEquals(4, Arborescence.cheminsFiltres(chemins, "")
                .size());
        // les ancêtres de Facture.java
        var ancetres = Arborescence.ancetresDe(retenus);
        assertTrue(ancetres.contains("app/"));
        assertTrue(ancetres.contains("app/src/"));
        assertTrue(ancetres.contains(
                "app/src/main/java/jo/compta/"));
        assertFalse(ancetres.contains("joide.toml"));
    }

    @Test
    void lEtatVideEstHonnete() {
        EtatLogcat etat = new EtatLogcat();
        assertNull(etat.derniere());
        EtatLogcat.Histogramme histogramme = etat.histogramme(0);
        for (int i = 0; i < EtatLogcat.BARRES_HISTOGRAMME; i++) {
            assertEquals(0, histogramme.hauteurs()[i]);
            assertFalse(histogramme.erreurs()[i]);
        }
    }
}
