package jo.joide.app.android.atelier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.atelier.EtatAtelier.ActionRetour;
import jo.joide.app.android.atelier.EtatAtelier.Ouverture;
import jo.joide.app.android.atelier.EtatAtelier.RaisonRefus;

/**
 * La machine à états de l'atelier (lot J, ADR-É5) : onglets à état
 * faible, surcouches, ordre de fermeture du retour systeme, pilule de
 * build — le critere du lot J exige ces tests JVM.
 */
class EtatAtelierTest {

    @Test
    void seuilGrandEcran720dp() {
        assertTrue(EtatAtelier.estGrandEcran(720));
        assertTrue(EtatAtelier.estGrandEcran(1024));
        assertFalse(EtatAtelier.estGrandEcran(719));
        assertFalse(EtatAtelier.estGrandEcran(360));
    }

    @Test
    void ouvrirDejaOuvertActiveSansDupliquer() {
        EtatAtelier etat = new EtatAtelier(false);
        etat.ouvrir("app/src/main/java/A.java");
        etat.ouvrir("joide.toml");
        Ouverture encore = etat.ouvrir("app/src/main/java/A.java");
        assertTrue(encore.faite());
        assertNull(encore.evince());
        assertEquals(List.of("app/src/main/java/A.java", "joide.toml"),
                etat.onglets());
        assertEquals("app/src/main/java/A.java", etat.actif());
    }

    @Test
    void fermerLActifActiveLeVoisinDeGauche() {
        EtatAtelier etat = new EtatAtelier(false);
        etat.ouvrir("a.txt");
        etat.ouvrir("b.txt");
        etat.ouvrir("c.txt");
        assertTrue(etat.fermer("c.txt"));
        assertEquals("b.txt", etat.actif());
        assertTrue(etat.fermer("b.txt"));
        assertEquals("a.txt", etat.actif());
        assertTrue(etat.fermer("a.txt"));
        assertNull(etat.actif());
        assertTrue(etat.onglets().isEmpty());
    }

    @Test
    void fermerLePremierActiveLeSuivant() {
        EtatAtelier etat = new EtatAtelier(false);
        etat.ouvrir("a.txt");
        etat.ouvrir("b.txt");
        etat.fermer("a.txt");
        assertEquals("b.txt", etat.actif());
    }

    @Test
    void pointDeModificationCompteLesTouchesDeLaSession() {
        EtatAtelier etat = new EtatAtelier(false);
        etat.ouvrir("a.txt");
        etat.ouvrir("b.txt");
        etat.marquerModifie("b.txt");
        assertTrue(etat.estModifie("b.txt"));
        assertFalse(etat.estModifie("a.txt"));
        assertEquals(1, etat.nombreModifies());
        etat.marquerModifie("b.txt");
        assertEquals(1, etat.nombreModifies(),
                "le point ne se cumule pas");
        etat.fermer("b.txt");
        assertFalse(etat.estModifie("b.txt"),
                "fermer l'onglet efface son point");
    }

    @Test
    void plafondDongletsEvicteLePlusAncienNonModifie() {
        EtatAtelier etat = new EtatAtelier(false);
        for (int i = 0; i < EtatAtelier.ONGLETS_MAX; i++) {
            etat.ouvrir("f" + i + ".txt");
        }
        etat.marquerModifie("f0.txt");
        Ouverture onzieme = etat.ouvrir("onzieme.txt");
        assertTrue(onzieme.faite());
        assertEquals("f1.txt", onzeniemeEvince(onzieme),
                "le plus ancien NON modifie est évincé, f0 est protege");
        assertFalse(etat.estOuvert("f1.txt"));
        assertTrue(etat.estOuvert("f0.txt"));
        assertEquals("onzieme.txt", etat.actif());
        assertEquals(EtatAtelier.ONGLETS_MAX, etat.onglets().size());
    }

    private static String onzeniemeEvince(Ouverture ouverture) {
        return ouverture.evince();
    }

    @Test
    void plafondRefuseOuvertureSiToutEstModifie() {
        EtatAtelier etat = new EtatAtelier(false);
        for (int i = 0; i < EtatAtelier.ONGLETS_MAX; i++) {
            etat.ouvrir("f" + i + ".txt");
            etat.marquerModifie("f" + i + ".txt");
        }
        Ouverture refusee = etat.ouvrir("trop.txt");
        assertFalse(refusee.faite());
        assertEquals(RaisonRefus.TOUT_MODIFIE, refusee.raison());
        assertFalse(etat.estOuvert("trop.txt"));
    }

    @Test
    void activerUnFichierNonOuvertEstRefuse() {
        EtatAtelier etat = new EtatAtelier(false);
        assertThrows(IllegalArgumentException.class,
                () -> etat.activer("inconnu.txt"));
    }

    @Test
    void retourFermeLesSurcouchesUneParUneDansLOrdre() {
        EtatAtelier etat = new EtatAtelier(false);
        etat.setProjetMonte(true);
        assertEquals(ActionRetour.DEMANDER_FERMETURE, etat.surRetour());

        etat.ouvrirTiroir();
        assertEquals(ActionRetour.FERMER_TIROIR, etat.surRetour());
        etat.fermerTiroir();

        etat.deplierConsole();
        etat.ouvrirTiroir();
        etat.basculerApercuPleinEcran();
        assertEquals(ActionRetour.FERMER_CONSOLE, etat.surRetour(),
                "la console se replie d'abord");
        etat.replierConsole();
        assertEquals(ActionRetour.FERMER_TIROIR, etat.surRetour());
        etat.fermerTiroir();
        assertEquals(ActionRetour.FERMER_APERCU, etat.surRetour());
        etat.fermerApercuPleinEcran();
        assertEquals(ActionRetour.DEMANDER_FERMETURE, etat.surRetour());
    }

    @Test
    void retourEnGrandEcranIgnoreLeTiroirAmarre() {
        EtatAtelier etat = new EtatAtelier(true);
        etat.setProjetMonte(true);
        etat.ouvrirTiroir();
        assertEquals(ActionRetour.DEMANDER_FERMETURE, etat.surRetour(),
                "l'arborescence amarree n'est pas une surcouche");
        etat.basculerApercuPleinEcran();
        assertEquals(ActionRetour.FERMER_APERCU, etat.surRetour());
    }

    @Test
    void retourQuitteDirectementSansProjetMonte() {
        EtatAtelier etat = new EtatAtelier(false);
        etat.deplierConsole();
        assertEquals(ActionRetour.FERMER_CONSOLE, etat.surRetour());
        etat.replierConsole();
        assertEquals(ActionRetour.QUITTER, etat.surRetour());
    }

    @Test
    void voletApercuSuitLaMiseEnPagePuisSeFerme() {
        EtatAtelier etat = new EtatAtelier(true);
        etat.ouvrir("app/src/main/res/layout/main.xml");
        assertFalse(etat.voletApercu());
        etat.surActifEstMiseEnPage(true);
        assertTrue(etat.voletApercu(),
                "une mise en page active ouvre le volet cote à cote");
        etat.surActifEstMiseEnPage(false);
        assertTrue(etat.voletApercu(),
                "un autre onglet ne ferme pas le volet : l'utilisateur decide");
        etat.fermerVoletApercu();
        assertFalse(etat.voletApercu());
    }

    @Test
    void fermerLeVoletQuitteAUssiLePleinEcran() {
        EtatAtelier etat = new EtatAtelier(false);
        etat.basculerApercuPleinEcran();
        assertTrue(etat.apercuPleinEcran());
        etat.fermerVoletApercu();
        assertFalse(etat.apercuPleinEcran());
    }

    @Test
    void piluleDeBuildRaconteNumeroPhasePuisIssue() {
        EtatAtelier etat = new EtatAtelier(false);
        assertFalse(etat.buildEnCours());
        assertNull(etat.build());
        etat.commencerBuild(4);
        assertTrue(etat.buildEnCours());
        assertEquals(4, etat.build().numero());
        assertNull(etat.build().phase());
        etat.phaseBuild("compilation");
        assertEquals("compilation", etat.build().phase());
        assertTrue(etat.build().enCours());
        etat.terminerBuild(true);
        assertFalse(etat.buildEnCours());
        assertTrue(etat.build().reussi());
        assertFalse(etat.build().interrompu());
    }

    @Test
    void piluleIntituleSurMortDuServiceJamaisUnZombie() {
        EtatAtelier etat = new EtatAtelier(false);
        etat.commencerBuild(7);
        etat.interrompreBuild();
        assertFalse(etat.buildEnCours());
        assertTrue(etat.build().interrompu());
        assertFalse(etat.build().reussi());
    }

    @Test
    void phaseApresFinNEcrimePasLeResultat() {
        EtatAtelier etat = new EtatAtelier(false);
        etat.commencerBuild(1);
        etat.terminerBuild(false);
        etat.phaseBuild("tardive");
        assertFalse(etat.build().reussi());
        assertFalse(etat.build().enCours(),
                "un événement tardif ne ranime pas le build");
    }

    // ------------------------------------------------------------------
    // Lot A8 (décision 10) : export/import de l'état d'UI minimal —
    // l'aller-retour des recréations restantes (thème, langue, police)
    // ------------------------------------------------------------------

    @Test
    void lallerRetourExportImportExportRendUnEtatEgal() {
        EtatAtelier etat = new EtatAtelier(false);
        etat.ouvrir("app/src/main/java/A.java");
        etat.ouvrir("joide.toml");
        etat.marquerModifie("joide.toml");
        etat.deplierConsole();
        etat.ouvrirTiroir();
        etat.basculerApercuPleinEcran();
        etat.surActifEstMiseEnPage(true);
        etat.setProjetMonte(true);

        EtatAtelier.Export export = etat.exporter(
                EtatFeuille.Section.JOURNAL);
        EtatAtelier revenue = new EtatAtelier(false);
        revenue.importer(export);
        assertEquals(export, revenue.exporter(
                        EtatFeuille.Section.JOURNAL),
                "export → import → export : l'état survit à la "
                        + "recréation (les onglets et l'ordre, l'actif, "
                        + "les points de modification, les surcouches)");
        assertEquals(List.of("app/src/main/java/A.java", "joide.toml"),
                revenue.onglets());
        assertEquals("joide.toml", revenue.actif(),
                "l'ACTIF survit : le dernier onglet ouvert reste actif");
        assertTrue(revenue.estModifie("joide.toml"));
        assertTrue(revenue.consoleDepliee());
        assertTrue(revenue.tiroirOuvert());
        assertTrue(revenue.apercuPleinEcran());
        assertTrue(revenue.voletApercu());
        assertTrue(revenue.projetMonte());
        assertEquals(1, revenue.nombreModifies());
    }

    @Test
    void lImportCopeAvecUnBundleAmaigri() {
        EtatAtelier revenue = new EtatAtelier(false);
        // un export construit à la main (listes absentes du Bundle) ne
        // casse jamais l'import : tout se lit vide, l'actif reste nul
        revenue.importer(new EtatAtelier.Export(
                null, null, null, false, false, false, false, false,
                EtatFeuille.Section.CONSOLE));
        assertTrue(revenue.onglets().isEmpty());
        assertNull(revenue.actif());
        assertEquals(0, revenue.nombreModifies());
        assertFalse(revenue.projetMonte());
    }

    @Test
    void laGeometrieSeRePoseElleNeSImportePas() {
        EtatAtelier etat = new EtatAtelier(false);
        etat.ouvrir("a.txt");
        EtatAtelier.Export export = etat.exporter(
                EtatFeuille.Section.CONSOLE);
        // la recréation relit la géométrie de la CONFIGURATION (pas de
        // l'export) : un grand écran restauré reste grand écran
        EtatAtelier grand = new EtatAtelier(true);
        grand.importer(export);
        assertTrue(grand.grandEcran(),
                "la géométrie ne s'importe pas — elle se relit de la "
                        + "configuration à la recréation");
        // la ré-inflation (changement de classe) re-pose la géométrie
        // de l'état COURANT, l'état d'UI reste
        grand.poserGrandEcran(false);
        assertFalse(grand.grandEcran());
        assertEquals(List.of("a.txt"), grand.onglets(),
                "re-poser la géométrie ne touche PAS l'état d'UI");
    }
}
