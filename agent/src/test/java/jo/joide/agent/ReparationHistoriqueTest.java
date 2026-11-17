package jo.joide.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * La réparation d'historique (ADR-É9, leçon 4) : une conversation
 * interrompue reste toujours valide pour le fournisseur — l'appel
 * sans réponse reçoit son résultat « interrompu » au bon endroit, le
 * résultat orphelin part. Quatre scénarios, dont la conversation
 * cassée par une ancienne version.
 */
class ReparationHistoriqueTest {

    @Test
    void unHistoriqueSainNeChangePas() {
        List<MessageAgent> sain = List.of(
                MessageAgent.utilisateur("fais"),
                new MessageAgent(MessageAgent.Role.ASSISTANT,
                        List.of(PartieContenu.appel("a1", "read_file",
                                "{}"))),
                new MessageAgent(MessageAgent.Role.UTILISATEUR,
                        List.of(PartieContenu.resultat("a1", "ok",
                                false))));
        ReparationHistorique.Reparation reparation =
                ReparationHistorique.reparer(sain);
        assertFalse(reparation.change);
        assertEquals(sain, reparation.historique);
    }

    @Test
    void unAppelSansReponseRecoitSonResultatInterrompu() {
        List<MessageAgent> interrompu = List.of(
                MessageAgent.utilisateur("fais"),
                new MessageAgent(MessageAgent.Role.ASSISTANT,
                        List.of(PartieContenu.appel("a1", "write_file",
                                "{}"))));
        ReparationHistorique.Reparation reparation =
                ReparationHistorique.reparer(interrompu);
        assertTrue(reparation.change);
        List<MessageAgent> repaired = reparation.historique;
        PartieContenu resultat = dernierResultat(repaired);
        assertEquals("a1", resultat.idAppel);
        assertTrue(resultat.erreur);
        assertTrue(resultat.texte.contains("interrupted"),
                "le résultat DIT l'interruption — le modèle sait que "
                        + "l'outil n'a peut-être pas été exécuté");
        // l'historique réparé est à son tour sain
        assertFalse(ReparationHistorique.reparer(repaired).change);
    }

    @Test
    void unResultatOrphelinEstRetire() {
        List<MessageAgent> orphelin = List.of(
                MessageAgent.utilisateur("bonjour"),
                new MessageAgent(MessageAgent.Role.UTILISATEUR,
                        List.of(PartieContenu.resultat("inconnu",
                                "résultat", false))));
        ReparationHistorique.Reparation reparation =
                ReparationHistorique.reparer(orphelin);
        assertTrue(reparation.change);
        assertEquals(1, reparation.historique.size(),
                "le message utilisateur ne porte plus que le texte");
    }

    @Test
    void uneConversationCasseeParUneAncienneVersionSeRepare() {
        // deux appels, une seule réponse : le second reçoit son
        // résultat interrompu APRÈS la réponse existante (au bon
        // endroit, l'ordre des résultats suit l'ordre des appels)
        List<MessageAgent> cassee = List.of(
                MessageAgent.utilisateur("fais tout"),
                new MessageAgent(MessageAgent.Role.ASSISTANT,
                        List.of(
                                PartieContenu.appel("a1", "read_file",
                                        "{}"),
                                PartieContenu.appel("a2", "edit_file",
                                        "{}"))),
                new MessageAgent(MessageAgent.Role.UTILISATEUR,
                        List.of(PartieContenu.resultat("a1", "contenu",
                                false))));
        ReparationHistorique.Reparation reparation =
                ReparationHistorique.reparer(cassee);
        assertTrue(reparation.change);
        List<PartieContenu> resultats = resultats(
                reparation.historique);
        assertEquals(2, resultats.size());
        assertEquals("a1", resultats.get(0).idAppel);
        assertEquals("a2", resultats.get(1).idAppel);
        assertFalse(resultats.get(0).erreur);
        assertTrue(resultats.get(1).erreur);
    }

    private static PartieContenu dernierResultat(
            List<MessageAgent> historique) {
        List<PartieContenu> tous = resultats(historique);
        return tous.get(tous.size() - 1);
    }

    private static List<PartieContenu> resultats(
            List<MessageAgent> historique) {
        return historique.stream()
                .flatMap(m -> m.parties.stream())
                .filter(p -> p.genre
                        == PartieContenu.Genre.RESULTAT_OUTIL)
                .toList();
    }
}
