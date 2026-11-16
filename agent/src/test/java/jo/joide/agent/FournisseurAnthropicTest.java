package jo.joide.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jo.joide.platform.CancelToken;

/**
 * Les adaptateurs contre des trames SSE ENREGISTRÉES (la méthode de
 * l'ADR-É9) : un tour Anthropic complet — battement de cœur, texte,
 * raisonnement signé, appel d'outil, usage avec lectures de cache —
 * doit se traduire en événements neutres sans perdre un octet, et la
 * REQUÊTE construite doit porter le préfixe stable avec son point de
 * rupture de cache et la note volatile en second bloc système.
 */
class FournisseurAnthropicTest {

    private static List<TransportHttp.TrameSse> tourComplet() {
        return List.of(
                trame(null, "{\"type\":\"message_start\",\"message\":"
                        + "{\"usage\":{\"input_tokens\":100,"
                        + "\"cache_read_input_tokens\":250}}}"),
                trame(null, "{\"type\":\"ping\"}"),
                trame(null, "{\"type\":\"content_block_start\","
                        + "\"index\":0,\"content_block\":"
                        + "{\"type\":\"thinking\"}}"),
                trame(null, "{\"type\":\"content_block_delta\","
                        + "\"index\":0,\"delta\":"
                        + "{\"type\":\"thinking_delta\","
                        + "\"thinking\":\"je réfléchis\"}}"),
                trame(null, "{\"type\":\"content_block_delta\","
                        + "\"index\":0,\"delta\":"
                        + "{\"type\":\"signature_delta\","
                        + "\"signature\":\"sig-opaque-123\"}}"),
                trame(null, "{\"type\":\"content_block_stop\","
                        + "\"index\":0}"),
                trame(null, "{\"type\":\"content_block_start\","
                        + "\"index\":1,\"content_block\":"
                        + "{\"type\":\"text\"}}"),
                trame(null, "{\"type\":\"content_block_delta\","
                        + "\"index\":1,\"delta\":"
                        + "{\"type\":\"text_delta\",\"text\":\"Voici\"}}"),
                trame(null, "{\"type\":\"content_block_delta\","
                        + "\"index\":1,\"delta\":"
                        + "{\"type\":\"text_delta\",\"text\":\" ça\"}}"),
                trame(null, "{\"type\":\"content_block_stop\","
                        + "\"index\":1}"),
                trame(null, "{\"type\":\"content_block_start\","
                        + "\"index\":2,\"content_block\":"
                        + "{\"type\":\"tool_use\",\"id\":\"outil-1\","
                        + "\"name\":\"read_file\"}}"),
                trame(null, "{\"type\":\"content_block_delta\","
                        + "\"index\":2,\"delta\":"
                        + "{\"type\":\"input_json_delta\","
                        + "\"partial_json\":\"{\\\"path\\\":\"}}"),
                trame(null, "{\"type\":\"content_block_delta\","
                        + "\"index\":2,\"delta\":"
                        + "{\"type\":\"input_json_delta\","
                        + "\"partial_json\":\"\\\"src/A.java\\\"}\"}}"),
                trame(null, "{\"type\":\"content_block_stop\","
                        + "\"index\":2}"),
                trame(null, "{\"type\":\"message_delta\",\"delta\":"
                        + "{\"stop_reason\":\"tool_use\"},"
                        + "\"usage\":{\"output_tokens\":42}}"),
                trame(null, "{\"type\":\"message_stop\"}"));
    }

    private static TransportHttp.TrameSse trame(String evenement,
                                                String donnees) {
        return new TransportHttp.TrameSse(evenement, donnees);
    }

    @Test
    void unTourCompletSeTraduitSansPerdreUnOctet() {
        FauxTransport transport = new FauxTransport(
                List.of(tourComplet()));
        FournisseurAnthropic fournisseur =
                new FournisseurAnthropic(null, "cle-test");
        List<EvenementFlux> evenements = new java.util.ArrayList<>();
        MessageAgent assistant = fournisseur.envoyer(transport,
                new RequeteLlm("claude-x", "SYS", List.of(), "NOTE",
                        List.of(), 128),
                CancelToken.NEVER, evenements::add);
        assertEquals(3, assistant.parties.size());
        assertEquals(PartieContenu.Genre.RAISONNEMENT,
                assistant.parties.get(0).genre);
        assertEquals("je réfléchis", assistant.parties.get(0).texte);
        assertEquals("sig-opaque-123",
                assistant.parties.get(0).signature,
                "la signature opaque voyage telle quelle");
        assertEquals("Voici ça", assistant.parties.get(1).texte);
        PartieContenu appel = assistant.parties.get(2);
        assertEquals(PartieContenu.Genre.APPEL_OUTIL, appel.genre);
        assertEquals("outil-1", appel.idAppel);
        assertEquals("read_file", appel.nomOutil);
        assertEquals("{\"path\":\"src/A.java\"}", appel.arguments);
        // les deltas vivants sont bien passés à l'écouteur
        assertTrue(evenements.stream().anyMatch(
                e -> e.genre == EvenementFlux.Genre.TEXTE_DELTA
                        && "Voici".equals(e.texte)));
        assertTrue(evenements.stream().anyMatch(
                e -> e.genre == EvenementFlux.Genre.RAISONNEMENT_DELTA));
        // l'usage : entrées 100, sorties 42, lectures de cache 250
        EvenementFlux usage = evenements.stream()
                .filter(e -> e.genre == EvenementFlux.Genre.USAGE)
                .reduce((a, b) -> b).orElse(null);
        assertEquals(100, usage.usage.entrees);
        assertEquals(42, usage.usage.sorties);
        assertEquals(250, usage.usage.lecturesCache);
    }

    @Test
    void laRequetePorteLePrefixeAvecRuptureDeCacheEtLaNoteApres() {
        FauxTransport transport = new FauxTransport(
                List.of(tourComplet()));
        FournisseurAnthropic fournisseur =
                new FournisseurAnthropic("https://passerelle.exemple",
                        "cle-test");
        fournisseur.envoyer(transport, new RequeteLlm("claude-x",
                        "ANCRAGE", List.of(), "NOTE VOLATILE",
                        List.of(), 128),
                CancelToken.NEVER, null);
        FauxTransport.RequeteCapturee requete = transport.requetes
                .get(0);
        assertEquals("https://passerelle.exemple/v1/messages",
                requete.url());
        assertEquals("cle-test", requete.entetes().get("x-api-key"));
        assertTrue(requete.corps().contains("\"cache_control\""));
        // le préfixe stable AVANT la note volatile : l'ordre compte
        int positionRupture = requete.corps()
                .indexOf("\"cache_control\":{\"type\":\"ephemeral\"}");
        int positionNote = requete.corps().indexOf("NOTE VOLATILE");
        assertTrue(positionRupture >= 0 && positionNote >= 0);
        assertTrue(positionRupture < positionNote,
                "la note volatile vit APRÈS le point de rupture : "
                        + "rafaîchie sans invalider le cache");
        assertTrue(requete.corps().contains("ANCRAGE"));
        assertTrue(requete.corps().contains("\"stream\":true"));
    }

    @Test
    void unAppelSansCleLeveLErreurActionnable() {
        FournisseurAnthropic fournisseur =
                new FournisseurAnthropic(null, "");
        try {
            fournisseur.envoyer(new FauxTransport(List.of()),
                    new RequeteLlm("claude-x", "S", List.of(), "",
                            List.of(), 1),
                    CancelToken.NEVER, null);
            throw new AssertionError("devrait lever");
        } catch (ErreursLlm.ErreurLlm erreur) {
            assertEquals(ErreursLlm.Genre.CLE_MANQUANTE, erreur.genre);
            assertTrue(erreur.getMessage().contains("settings"));
        }
    }

    @Test
    void uneErreur429EstClasseeAvecSonDelai() {
        FauxTransport.Erreur transport = new FauxTransport.Erreur(429,
                Map.of("retry-after", List.of("12")),
                "{\"type\":\"error\",\"error\":"
                        + "{\"type\":\"rate_limit_error\"}}");
        FournisseurAnthropic fournisseur =
                new FournisseurAnthropic(null, "cle");
        try {
            fournisseur.envoyer(transport, new RequeteLlm("claude-x",
                            "S", List.of(), "", List.of(), 1),
                    CancelToken.NEVER, null);
            throw new AssertionError("devrait lever");
        } catch (ErreursLlm.ErreurLlm erreur) {
            assertEquals(ErreursLlm.Genre.LIMITE_DEBIT, erreur.genre);
            assertEquals(12, erreur.delaiSecondes);
        }
    }

    @Test
    void unFluxSansMessageStopEstInterrompuPasIllisible() {
        List<TransportHttp.TrameSse> sansFin = List.of(
                trame(null, "{\"type\":\"message_start\",\"message\":"
                        + "{\"usage\":{\"input_tokens\":10}}}"),
                trame(null, "{\"type\":\"content_block_start\","
                        + "\"index\":0,\"content_block\":"
                        + "{\"type\":\"text\"}}"),
                trame(null, "{\"type\":\"content_block_delta\","
                        + "\"index\":0,\"delta\":"
                        + "{\"type\":\"text_delta\",\"text\":\"par\"}}"));
        FauxTransport transport = new FauxTransport(List.of(sansFin));
        FournisseurAnthropic fournisseur =
                new FournisseurAnthropic(null, "cle");
        try {
            fournisseur.envoyer(transport, new RequeteLlm("claude-x",
                            "S", List.of(), "", List.of(), 1),
                    CancelToken.NEVER, null);
            throw new AssertionError("devrait lever");
        } catch (ErreursLlm.ErreurLlm erreur) {
            assertEquals(ErreursLlm.Genre.REPONSE_ILLISIBLE,
                    erreur.genre,
                    "sans message_stop le message est incomplet");
        }
    }

    @Test
    void leResultatOutilVoyageEnMessageUtilisateurAnthropic() {
        List<Object> messages = FournisseurAnthropic
                .messagesAnthropic(List.of(
                        MessageAgent.utilisateur("fais-le"),
                        new MessageAgent(
                                MessageAgent.Role.ASSISTANT,
                                List.of(PartieContenu.appel("id-1",
                                        "write_file", "{\"path\":\"a\"}"))),
                        new MessageAgent(
                                MessageAgent.Role.UTILISATEUR,
                                List.of(PartieContenu.resultat("id-1",
                                        "written", false)))));
        String corps = jo.joide.deps.json.JsonEcrire.valeur(messages);
        assertTrue(corps.contains("\"tool_use\""));
        assertTrue(corps.contains("\"tool_result\""));
        assertTrue(corps.contains("\"tool_use_id\":\"id-1\""));
        assertTrue(corps.contains("\"role\":\"assistant\""));
        assertTrue(corps.contains("\"role\":\"user\""));
    }
}
