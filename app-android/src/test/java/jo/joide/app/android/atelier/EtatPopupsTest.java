package jo.joide.app.android.atelier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.atelier.EtatAtelier.ActionRetour;

/**
 * L'état des popups de l'atelier éprouvé en JVM pur (lot A7, ADR-085
 * décision 12) : le popup n'est JAMAIS restauré — ce qui compte vit
 * ICI. Deux vérités :
 *
 * <ul>
 * <li><b>l'ordre du retour système</b> : un popup ouvert se ferme EN
 * PREMIER — AVANT le tiroir agent (la surcouche la plus récente) et
 * AVANT toutes les surcouches de l'état (feuille, tiroir, aperçu,
 * confirmation de fermeture, sortie) ; sans popup, l'ordre d'hier
 * (ADR-É5, précisé ADR-É11 point 1) est inchangé ;</li>
 * <li><b>les brouillons de saisie</b> : le texte frappé dans un popup
 * de saisie survit à la fermeture du popup (jamais restauré, jamais
 * perdu — la vue est jetable, l'état reste).</li>
 * </ul>
 */
class EtatPopupsTest {

    /** L'état des popups de l'atelier. */
    private final EtatPopups popups = new EtatPopups();

    @Test
    void lePopupOuvertSeFermeEnPremierMemeAvecTiroirAgentEtFeuille() {
        EtatAtelier etat = new EtatAtelier(false);
        etat.setProjetMonte(true);
        // TOUT est ouvert : tiroir agent, feuille (console), tiroir,
        // aperçu plein écran — et un popup
        etat.deplierConsole();
        etat.ouvrirTiroir();
        etat.basculerApercuPleinEcran();
        assertEquals(ActionRetour.FERMER_POPUP,
                popups.surRetour(true, true, etat),
                "le popup se ferme EN PREMIER, avant toute surcouche");
    }

    @Test
    void sansPopupLeTiroirAgentSeFermeAvantLesSurcouchesDeLetat() {
        EtatAtelier etat = new EtatAtelier(false);
        etat.setProjetMonte(true);
        etat.deplierConsole();
        etat.ouvrirTiroir();
        assertEquals(ActionRetour.FERMER_TIROIR_AGENT,
                popups.surRetour(false, true, etat),
                "le tiroir agent (surcouche la plus récente) passe "
                        + "avant les surcouches de l'état");
        // le tiroir agent fermé, l'ordre d'hier reprend
        assertEquals(ActionRetour.FERMER_CONSOLE,
                popups.surRetour(false, false, etat));
    }

    @Test
    void sansPopupSansTiroirAgentLordreDHierEstInchange() {
        EtatAtelier etat = new EtatAtelier(false);
        etat.setProjetMonte(true);
        etat.deplierConsole();
        etat.ouvrirTiroir();
        etat.basculerApercuPleinEcran();
        // l'ordre d'EtatAtelier.surRetour() passe TEL QUEL
        assertEquals(ActionRetour.FERMER_CONSOLE,
                popups.surRetour(false, false, etat));
        etat.replierConsole();
        assertEquals(ActionRetour.FERMER_TIROIR,
                popups.surRetour(false, false, etat));
        etat.fermerTiroir();
        assertEquals(ActionRetour.FERMER_APERCU,
                popups.surRetour(false, false, etat));
        etat.fermerApercuPleinEcran();
        assertEquals(ActionRetour.DEMANDER_FERMETURE,
                popups.surRetour(false, false, etat),
                "la confirmation de fermeture reste la dernière "
                        + "surcouche avant la sortie");
    }

    @Test
    void unPopupOuvertPasseAvantLaDemandeDeFermetureEtLaSortie() {
        EtatAtelier etat = new EtatAtelier(false);
        etat.setProjetMonte(true);
        assertEquals(ActionRetour.DEMANDER_FERMETURE, etat.surRetour());
        assertEquals(ActionRetour.FERMER_POPUP,
                popups.surRetour(true, false, etat),
                "même la confirmation de fermeture cède au popup");
        EtatAtelier vierge = new EtatAtelier(false);
        assertEquals(ActionRetour.QUITTER, vierge.surRetour());
        assertEquals(ActionRetour.FERMER_POPUP,
                popups.surRetour(true, false, vierge),
                "même la sortie cède au popup");
    }

    @Test
    void leBrouillonDeSaisieSurvitALaFermetureDuPopup() {
        assertNull(popups.brouillon("fichiers.nouveau"),
                "jamais frappé : le défaut du cas s'applique");
        popups.poserBrouillon("fichiers.nouveau", "Config");
        assertEquals("Config", popups.brouillon("fichiers.nouveau"));
        // chaque frappe remplace (le TextWatcher délègue, la vue ne
        // garde rien)
        popups.poserBrouillon("fichiers.nouveau", "Configuration");
        assertEquals("Configuration", popups.brouillon("fichiers.nouveau"));
        // un brouillon vide reste un brouillon FRAPPÉ (pas le défaut)
        popups.poserBrouillon("fichiers.nouveau", "");
        assertEquals("", popups.brouillon("fichiers.nouveau"));
        // la saisie validée s'oublie (la vérité vit dans le store)
        popups.oublierBrouillon("fichiers.nouveau");
        assertNull(popups.brouillon("fichiers.nouveau"));
    }

    @Test
    void leBrouillonNullSeNormaliseJamaisDeNullVolant() {
        popups.poserBrouillon("agent.modele", "gpt-5.1");
        popups.poserBrouillon("agent.modele", null);
        assertEquals("", popups.brouillon("agent.modele"),
                "le null se normalise en vide — jamais de null volant "
                        + "vers un appelant qui préremplirait");
    }

    // ------------------------------------------------------------------
    // Lot A8 (décision 10) : export/import des brouillons — le même
    // passage que l'export d'EtatAtelier (la saisie vit dans l'état,
    // le popup n'est jamais restauré)
    // ------------------------------------------------------------------

    @Test
    void lallerRetourDesBrouillonsSurvitALaRecreation() {
        popups.poserBrouillon("fichiers.nouveau", "Config");
        popups.poserBrouillon("agent.modele", "gpt-5.1");
        popups.poserBrouillon("agent.url", "");
        EtatPopups revenue = new EtatPopups();
        revenue.importerBrouillons(popups.exporterBrouillons());
        assertEquals("Config", revenue.brouillon("fichiers.nouveau"));
        assertEquals("gpt-5.1", revenue.brouillon("agent.modele"));
        assertEquals("", revenue.brouillon("agent.url"),
                "un brouillon vide reste un brouillon FRAPPÉ");
        // l'aller-retour rend une copie ÉGALE (l'ordre de frappe compris)
        assertEquals(popups.exporterBrouillons(),
                revenue.exporterBrouillons());
    }

    @Test
    void lImportDeBrouillonsAmaigrisOuNulsNeCasseRien() {
        popups.poserBrouillon("fichiers.nouveau", "Config");
        popups.importerBrouillons(null);
        assertNull(popups.brouillon("fichiers.nouveau"),
                "un import nul efface tout (aucune recréation n'a "
                        + "encore frappé)");
        Map<String, String> amaigri = new java.util.LinkedHashMap<>();
        amaigri.put("agent.modele", null);
        amaigri.put(null, "cle nulle saute");
        popups.importerBrouillons(amaigri);
        assertEquals("", popups.brouillon("agent.modele"),
                "la valeur nulle se normalise en vide");
    }
}
