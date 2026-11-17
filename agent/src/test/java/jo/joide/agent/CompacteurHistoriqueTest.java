package jo.joide.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Le compacteur à hystérésis (ADR-É9, économie) : rien sous
 * 120 000 caractères, une passe unique vers 60 000 en tronquant à
 * 4 000 les résultats d'outils plus vieux que les quatre plus
 * récents, décisions PERMANENTES, textes utilisateur et assistant
 * jamais touchés, mode délégué inactif.
 */
class CompacteurHistoriqueTest {

    private static MessageAgent resultat(String id, String texte) {
        return new MessageAgent(MessageAgent.Role.UTILISATEUR,
                List.of(PartieContenu.resultat(id, texte, false)));
    }

    private static MessageAgent appel(String id) {
        return new MessageAgent(MessageAgent.Role.ASSISTANT,
                List.of(PartieContenu.appel(id, "read_file", "{}")));
    }

    @Test
    void sousLeSeuilRienNEstElague() {
        List<MessageAgent> historique = List.of(
                MessageAgent.utilisateur("coucou"),
                resultat("a1", "x".repeat(50_000)));
        assertEquals(120_000,
                CompacteurHistorique.SEUIL_DECLENCHEMENT);
        CompacteurHistorique.Passe passe = CompacteurHistorique.passer(
                historique, Set.of(), false);
        assertFalse(passe.passage);
        assertEquals(historique, passe.historique);
    }

    @Test
    void auDessusDuSeuilLesVieuxSontTronquesLesQuatreRecentsEpargnes() {
        List<MessageAgent> historique = new java.util.ArrayList<>();
        historique.add(MessageAgent.utilisateur("gros projet"));
        for (int i = 1; i <= 12; i++) {
            historique.add(appel("a" + i));
            historique.add(resultat("a" + i, "x".repeat(20_000)));
        }
        // 240 000 caractères de résultats : bien au-dessus du seuil
        int avant = CompacteurHistorique.caracteres(historique);
        assertTrue(avant > CompacteurHistorique.SEUIL_DECLENCHEMENT);
        CompacteurHistorique.Passe passe = CompacteurHistorique.passer(
                historique, Set.of(), false);
        assertTrue(passe.passage);
        List<PartieContenu> resultats = passe.historique.stream()
                .flatMap(m -> m.parties.stream())
                .filter(p -> p.genre
                        == PartieContenu.Genre.RESULTAT_OUTIL)
                .toList();
        // les quatre plus récents sont épargnés (20 000 intacts)
        assertEquals("x".repeat(20_000), resultats.get(11).texte);
        assertEquals("x".repeat(20_000), resultats.get(8).texte);
        // a8 et plus vieux : tronqués à la limite, troncature ANNOCÉE
        assertTrue(resultats.get(7).texte.length()
                        <= CompacteurHistorique.LIMITE_RESULTAT + 60,
                "a8 est plus vieux que les quatre récents : tronqué");
        assertTrue(resultats.get(7).texte.contains("permanent"),
                "la troncature S'ANNONCE dans le texte");
        assertTrue(passe.compactes.contains("a8"));
        assertFalse(passe.compactes.contains("a11"));
        // la passe a réellement diminué l'historique
        assertTrue(CompacteurHistorique.caracteres(passe.historique)
                < avant);
    }

    @Test
    void avecDesRecentsPetitsLaPasseAtteintLaCible() {
        List<MessageAgent> historique = new java.util.ArrayList<>();
        historique.add(MessageAgent.utilisateur("projet"));
        for (int i = 1; i <= 10; i++) {
            historique.add(appel("a" + i));
            historique.add(resultat("a" + i, "x".repeat(14_000)));
        }
        // 140 000 caractères : au-dessus du seuil
        assertTrue(CompacteurHistorique.caracteres(historique)
                > CompacteurHistorique.SEUIL_DECLENCHEMENT);
        CompacteurHistorique.Passe passe = CompacteurHistorique.passer(
                historique, Set.of(), false);
        assertTrue(passe.passage);
        // 6 vieux tronqués à 4 000 + 4 récents épargnés à 14 000 :
        // 24 000 + 56 000 = 80 000 — vers la cible, en une passe
        int apres = CompacteurHistorique.caracteres(
                passe.historique);
        assertTrue(apres < CompacteurHistorique.SEUIL_DECLENCHEMENT,
                "l'hystérésis repose sous le seuil : " + apres);
    }

    @Test
    void lesTextesUtilisateurEtAssistantNeSontJamaisTouches() {
        String longueReponse = "y".repeat(100_000);
        List<MessageAgent> historique =
                new java.util.ArrayList<>();
        historique.add(MessageAgent.utilisateur(longueReponse));
        historique.add(MessageAgent.assistant(longueReponse));
        for (int i = 1; i <= 6; i++) {
            historique.add(appel("a" + i));
            historique.add(resultat("a" + i, "z".repeat(20_000)));
        }
        CompacteurHistorique.Passe passe = CompacteurHistorique.passer(
                historique, Set.of(), false);
        assertTrue(passe.passage);
        assertEquals(longueReponse,
                passe.historique.get(0).parties.get(0).texte,
                "le texte utilisateur ne se touche jamais");
        assertEquals(longueReponse,
                passe.historique.get(1).parties.get(0).texte,
                "le texte assistant ne se touche jamais");
        assertTrue(passe.compactes.contains("a1"));
        assertTrue(passe.compactes.contains("a2"));
        assertFalse(passe.compactes.contains("a5"));
    }

    @Test
    void lesDecisionsSontPermanentesJamaisReelargies() {
        List<MessageAgent> historique = List.of(
                MessageAgent.utilisateur("projet"),
                appel("a1"),
                resultat("a1", "v".repeat(30_000)));
        // déjà tronqué à une passe précédente : la décision reste
        String tronque = "v".repeat(CompacteurHistorique.LIMITE_RESULTAT)
                + "\n[tool output truncated to 4000 characters — "
                + "permanent]";
        List<MessageAgent> dejaCompacte = List.of(
                MessageAgent.utilisateur("projet"),
                appel("a1"),
                resultat("a1", tronque));
        CompacteurHistorique.Passe passe = CompacteurHistorique.passer(
                dejaCompacte, Set.of("a1"), false);
        // sous le seuil après troncature : aucune passe ne redéclenche
        assertFalse(passe.passage);
        // et même au-dessus du seuil, a1 ne serait PAS re-tronqué :
        // il est déjà dans les décisions permanentes
        passe = CompacteurHistorique.passer(List.of(
                        MessageAgent.utilisateur(
                                "x".repeat(200_000)),
                        appel("a1"), resultat("a1", tronque)),
                Set.of("a1"), false);
        assertFalse(passe.passage,
                "rien à tronquer : la décision permanente protège "
                        + "le résultat et les textes ne se touchent "
                        + "pas");
        for (PartieContenu partie : passe.historique.stream()
                .flatMap(m -> m.parties.stream())
                .filter(p -> p.genre
                        == PartieContenu.Genre.RESULTAT_OUTIL)
                .toList()) {
            assertEquals(tronque, partie.texte,
                    "un résultat déjà tronqué n'est JAMAIS "
                            + "réélargi ni re-tronqué");
        }
    }

    @Test
    void leModeDelegueNeFaitRien() {
        List<MessageAgent> historique = List.of(
                MessageAgent.utilisateur("x".repeat(200_000)));
        CompacteurHistorique.Passe passe = CompacteurHistorique.passer(
                historique, Set.of(), true);
        assertFalse(passe.passage,
                "le fournisseur gère le contexte : deux élagueurs se "
                        + "battraient sur le même préfixe");
        assertEquals(historique, passe.historique);
    }
}
