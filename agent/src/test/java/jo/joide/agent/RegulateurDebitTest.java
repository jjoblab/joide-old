package jo.joide.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import jo.joide.deps.json.Json;

/**
 * Le régulateur préventif à horloge fausse (lot Q) : la fenêtre de
 * 60 s sur requêtes et jetons, les limites du plus spécifique au plus
 * général (configurée, énoncée, observée), et l'apprentissage qui
 * SURVIT aux tours suivants (critère : « les limites apprises
 * survivent à la conversation »).
 */
class RegulateurDebitTest {

    @Test
    void sansLimiteConnueLeRegulateurNAttendJamais() {
        RegulateurDebit regulateur = new RegulateurDebit(null, null,
                new HorlogeFausse());
        assertEquals(0, regulateur.delaiPour(10_000));
        assertNull(regulateur.rpm());
        assertNull(regulateur.tpm());
    }

    @Test
    void laConfigurationPrimeSurLAprentissage() {
        HorlogeFausse horloge = new HorlogeFausse();
        RegulateurDebit regulateur = new RegulateurDebit(10, 50_000L,
                horloge);
        for (int k = 0; k < 10; k++) {
            regulateur.enregistrer(1_000);
        }
        assertTrue(regulateur.delaiPour(1_000) > 0,
                "la fenêtre requêtes est pleine");
        assertEquals(10, regulateur.rpm());
        assertEquals(50_000, regulateur.tpm());
    }

    @Test
    void laLimiteEnonceeDevientLaBorne() {
        HorlogeFausse horloge = new HorlogeFausse();
        RegulateurDebit regulateur = new RegulateurDebit(null, null,
                horloge);
        regulateur.apprendre(new QuotaFournisseur(
                QuotaFournisseur.Metrique.JETONS_ENTREE,
                QuotaFournisseur.Fenetre.MINUTE, 250_000));
        assertEquals(250_000, regulateur.tpm(),
                "une limite énoncée est adoptée telle quelle");
        // elle survit au tour suivant (même objet, pas d'oubli)
        for (int k = 0; k < 5; k++) {
            regulateur.enregistrer(10_000);
        }
        assertTrue(regulateur.delaiPour(250_000) > 0,
                "la fenêtre jetons tient sous la borne apprise");
    }

    @Test
    void sansEnonceLePlafondObserveDevientLaBorne() {
        HorlogeFausse horloge = new HorlogeFausse();
        RegulateurDebit regulateur = new RegulateurDebit(null, null,
                horloge);
        // quatre requêtes en vol déclenchent un 429 sans limite
        // énoncée : la borne requêtes devient 3 (en vol − 1)
        for (int k = 0; k < 4; k++) {
            regulateur.enregistrer(100);
        }
        regulateur.apprendre(new QuotaFournisseur(
                QuotaFournisseur.Metrique.REQUETES,
                QuotaFournisseur.Fenetre.MINUTE, -1));
        assertEquals(3, regulateur.rpm(),
                "rester juste sous ce qui a déclenché");
        // jetons : en vol ×9/10
        RegulateurDebit autre = new RegulateurDebit(null, null,
                horloge);
        for (int k = 0; k < 4; k++) {
            autre.enregistrer(10_000);
        }
        autre.apprendre(new QuotaFournisseur(
                QuotaFournisseur.Metrique.JETONS_ENTREE,
                QuotaFournisseur.Fenetre.MINUTE, -1));
        assertEquals(36_000, autre.tpm(), "4 × 10 000 × 9/10");
    }

    @Test
    void laFenetreGlissanteLibereLePassage() {
        HorlogeFausse horloge = new HorlogeFausse();
        RegulateurDebit regulateur = new RegulateurDebit(2, null,
                horloge);
        regulateur.enregistrer(100);
        regulateur.enregistrer(100);
        long delai = regulateur.delaiPour(100);
        assertTrue(delai > 0, "la fenêtre requêtes est pleine");
        horloge.avancer(60_001);
        assertEquals(0, regulateur.delaiPour(100),
                "la fenêtre a glissé : le passage est libre");
    }

    @Test
    void uneErreurDeFenetreJourNAprendRien() {
        HorlogeFausse horloge = new HorlogeFausse();
        RegulateurDebit regulateur = new RegulateurDebit(null, null,
                horloge);
        regulateur.apprendre(new QuotaFournisseur(
                QuotaFournisseur.Metrique.REQUETES,
                QuotaFournisseur.Fenetre.JOUR, 1_000));
        assertNull(regulateur.rpm(),
                "la fenêtre du régulateur est la minute : un plafond "
                        + "journalier ne dit rien sur elle");
    }

    @Test
    void lesOccupationsPortentLesBandes() {
        HorlogeFausse horloge = new HorlogeFausse();
        RegulateurDebit regulateur = new RegulateurDebit(10, 1_000L,
                horloge);
        assertEquals(0, regulateur.occupationRpm());
        assertEquals(0, regulateur.occupationTpm(0));
        for (int k = 0; k < 8; k++) {
            regulateur.enregistrer(100);
        }
        assertEquals(0.8, regulateur.occupationRpm(), 1e-9);
        assertEquals(0.8, regulateur.occupationTpm(0), 1e-9);
        assertEquals(0.9, regulateur.occupationTpm(100), 1e-9,
                "la requête suivante est comptée : prévenir AVANT");
    }

    @Test
    void lestimationCompteSystemeOutilsMessagesEtImages() {
        RequeteLlm requete = new RequeteLlm("gemini-x", "0123456789",
                jo.joide.agent.HistoriqueAgent.vide().messages(), "",
                List.of(new DescOutil("outil", "0123456789",
                        Json.parse("{\"type\":\"object\"}").root,
                        false, 100)), 128);
        long estimes = RegulateurDebit.estimerJetons(requete);
        // système 10 + nom 5 + description 10 + schéma 17 = 42 → 10
        assertEquals(10, estimes);
        // une image vaut 1 500
        RequeteLlm avecImage = new RequeteLlm("gemini-x", "",
                List.of(new MessageAgent(MessageAgent.Role.UTILISATEUR,
                        List.of(PartieContenu.image("image/png",
                                "Zm9vYmFy")))), "", List.of(), 128);
        assertEquals(1_500, RegulateurDebit.estimerJetons(avecImage));
    }
}
