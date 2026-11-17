package jo.joide.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import jo.joide.platform.CancelToken;

/**
 * L'adaptateur OpenAI-compatible contre des trames enregistrées :
 * deltas de contenu, appels d'outils assemblés index par index
 * (nom et arguments fragmentés), usage avec jetons cachés, le
 * [DONE] obligatoire, l'URL de base nettoyée et l'en-tête
 * d'authentification omis pour les serveurs locaux.
 */
class FournisseurOpenAiCompatibleTest {

    private static List<TransportHttp.TrameSse> tourComplet() {
        return List.of(
                trame("{\"choices\":[{\"index\":0,\"delta\":"
                        + "{\"role\":\"assistant\",\"content\":\"Sa\"}}]}"),
                trame("{\"choices\":[{\"index\":0,\"delta\":"
                        + "{\"content\":\"lut\"}}]}"),
                trame("{\"choices\":[{\"index\":0,\"delta\":"
                        + "{\"tool_calls\":[{\"index\":0,\"id\":"
                        + "\"appel-1\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"read_file\","
                        + "\"arguments\":\"{\\\"path\\\":\"}}]}}]}"),
                trame("{\"choices\":[{\"index\":0,\"delta\":"
                        + "{\"tool_calls\":[{\"index\":0,"
                        + "\"function\":{\"arguments\":"
                        + "\"\\\"src/A.java\\\"}\"}}]}}]}"),
                trame("{\"choices\":[{\"index\":0,\"delta\":{},"
                        + "\"finish_reason\":\"tool_calls\"}]}"),
                trame("{\"choices\":[],\"usage\":"
                        + "{\"prompt_tokens\":80,"
                        + "\"completion_tokens\":12,"
                        + "\"prompt_tokens_details\":"
                        + "{\"cached_tokens\":400}}}"),
                trame("[DONE]"));
    }

    private static TransportHttp.TrameSse trame(String donnees) {
        return new TransportHttp.TrameSse(null, donnees);
    }

    @Test
    void unTourCompletAssembleTexteEtAppelFragmente() {
        FauxTransport transport = new FauxTransport(
                List.of(tourComplet()));
        FournisseurOpenAiCompatible fournisseur =
                new FournisseurOpenAiCompatible(null, "cle");
        List<EvenementFlux> evenements = new ArrayList<>();
        MessageAgent assistant = fournisseur.envoyer(transport,
                new RequeteLlm("gpt-x", "SYS", List.of(), "NOTE",
                        List.of(), 128),
                CancelToken.NEVER, evenements::add);
        assertEquals(2, assistant.parties.size());
        assertEquals("Salut", assistant.parties.get(0).texte);
        PartieContenu appel = assistant.parties.get(1);
        assertEquals("appel-1", appel.idAppel);
        assertEquals("read_file", appel.nomOutil);
        assertEquals("{\"path\":\"src/A.java\"}", appel.arguments,
                "les arguments fragmentés se réassemblent tels quels");
        EvenementFlux usage = evenements.stream()
                .filter(e -> e.genre == EvenementFlux.Genre.USAGE)
                .reduce((a, b) -> b).orElse(null);
        assertEquals(80, usage.usage.entrees);
        assertEquals(12, usage.usage.sorties);
        assertEquals(400, usage.usage.lecturesCache);
    }

    @Test
    void leRaisonnementEstCaptureEnSignatureTagueeOpenAi() {
        List<TransportHttp.TrameSse> tour = List.of(
                trame("{\"choices\":[{\"index\":0,\"delta\":"
                        + "{\"reasoning_content\":\"je pense\"}}]}"),
                trame("{\"choices\":[{\"index\":0,\"delta\":"
                        + "{\"content\":\"donc\"}}]}"),
                trame("{\"choices\":[{\"index\":0,\"delta\":{},"
                        + "\"finish_reason\":\"stop\"}]}"),
                trame("[DONE]"));
        FournisseurOpenAiCompatible fournisseur =
                new FournisseurOpenAiCompatible(null, "cle");
        MessageAgent assistant = fournisseur.envoyer(
                new FauxTransport(List.of(tour)),
                new RequeteLlm("gpt-x", "S", List.of(), "", List.of(),
                        16),
                CancelToken.NEVER, null);
        assertEquals(2, assistant.parties.size());
        assertEquals(PartieContenu.Genre.RAISONNEMENT,
                assistant.parties.get(0).genre);
        assertTrue(assistant.parties.get(0).signature
                .startsWith("openai:"),
                "taguée openai:* : jamais confondue avec une autre");
        assertEquals("je pense", assistant.parties.get(0).texte);
        // et elle REPART au fournisseur qui l'a émise
        String corps = jo.joide.deps.json.JsonEcrire.valeur(
                FournisseurOpenAiCompatible.messagesOpenAi(
                        List.of(assistant)));
        assertTrue(corps.contains("\"reasoning_content\":\"je pense\""));
    }

    @Test
    void laNoteVolatileEstLeDernierMessageSysteme() {
        FauxTransport transport = new FauxTransport(
                List.of(tourComplet()));
        FournisseurOpenAiCompatible fournisseur =
                new FournisseurOpenAiCompatible(null, "cle");
        fournisseur.envoyer(transport, new RequeteLlm("gpt-x",
                        "ANCRAGE", List.of(), "NOTE VOLATILE",
                        List.of(), 128),
                CancelToken.NEVER, null);
        String corps = transport.requetes.get(0).corps();
        int ancrage = corps.indexOf("ANCRAGE");
        int note = corps.indexOf("NOTE VOLATILE");
        assertTrue(ancrage >= 0 && note > ancrage,
                "l'ancrage part en premier, la note en fin — le "
                        + "préfixe ne bouge pas");
        assertTrue(corps.contains("\"stream_options\""));
        assertTrue(corps.contains("\"include_usage\":true"));
    }

    @Test
    void lUrlDeBaseSeNettoieDesCopierColler() {
        assertEquals("https://api.openai.com/v1",
                FournisseurOpenAiCompatible.nettoyerUrl(null));
        assertEquals("https://api.openai.com/v1",
                FournisseurOpenAiCompatible.nettoyerUrl("  "));
        assertEquals("http://localhost:1234/v1",
                FournisseurOpenAiCompatible.nettoyerUrl(
                        "http://localhost:1234/v1/"));
        assertEquals("http://localhost:1234/v1",
                FournisseurOpenAiCompatible.nettoyerUrl(
                        "http://localhost:1234/v1/chat/completions"));
        assertEquals("https://passerelle.exemple/v1",
                FournisseurOpenAiCompatible.nettoyerUrl(
                        "https://passerelle.exemple/v1/"));
    }

    @Test
    void sansCleLEnteteDAuthentificationEstOmis() {
        FauxTransport transport = new FauxTransport(
                List.of(tourComplet()));
        FournisseurOpenAiCompatible local =
                new FournisseurOpenAiCompatible("http://localhost:1234",
                        "");
        local.envoyer(transport, new RequeteLlm("modele-local", "S",
                        List.of(), "", List.of(), 16),
                CancelToken.NEVER, null);
        assertFalse(transport.requetes.get(0).entetes()
                        .containsKey("authorization"),
                "un serveur local sans authentification n'en reçoit "
                        + "pas");
        assertEquals("http://localhost:1234/chat/completions",
                transport.requetes.get(0).url());
    }

    @Test
    void sansDoneLeFluxEstIllisiblePasTermine() {
        List<TransportHttp.TrameSse> sansDone = List.of(
                trame("{\"choices\":[{\"index\":0,\"delta\":"
                        + "{\"content\":\"par\"}}]}"));
        FournisseurOpenAiCompatible fournisseur =
                new FournisseurOpenAiCompatible(null, "cle");
        try {
            fournisseur.envoyer(new FauxTransport(List.of(sansDone)),
                    new RequeteLlm("gpt-x", "S", List.of(), "",
                            List.of(), 1),
                    CancelToken.NEVER, null);
            throw new AssertionError("devrait lever");
        } catch (ErreursLlm.ErreurLlm erreur) {
            assertEquals(ErreursLlm.Genre.REPONSE_ILLISIBLE,
                    erreur.genre);
        }
    }

    @Test
    void uneErreur5xxEstClasseeRetentable() {
        FauxTransport.Erreur transport = new FauxTransport.Erreur(503,
                java.util.Map.of(), "overloaded");
        FournisseurOpenAiCompatible fournisseur =
                new FournisseurOpenAiCompatible(null, "cle");
        try {
            fournisseur.envoyer(transport, new RequeteLlm("gpt-x",
                            "S", List.of(), "", List.of(), 1),
                    CancelToken.NEVER, null);
            throw new AssertionError("devrait lever");
        } catch (ErreursLlm.ErreurLlm erreur) {
            assertEquals(ErreursLlm.Genre.ERREUR_SERVEUR, erreur.genre);
            assertTrue(erreur.genre.retentable);
        }
    }

    @Test
    void uneImageUtilisateurPartEnMessageDataUri() {
        FauxTransport transport = new FauxTransport(List.of());
        FournisseurOpenAiCompatible fournisseur =
                new FournisseurOpenAiCompatible("", "cle-test");
        RequeteLlm requete = new RequeteLlm("modele-x", "SYS",
                List.of(new MessageAgent(MessageAgent.Role.UTILISATEUR,
                        List.of(PartieContenu.resultat("appel-1", "ok",
                                false),
                                PartieContenu.image("image/jpeg",
                                        "Zm9vYmFy")))), "",
                List.of(), 128);
        try {
            fournisseur.envoyer(transport, requete, CancelToken.NEVER,
                    null);
        } catch (RuntimeException e) {
            // le flux scripté est vide : seule la REQUÊTE compte
        }
        String corps = transport.requetes.get(0).corps();
        assertTrue(corps.contains("data:image/jpeg;base64,Zm9vYmFy"),
                "l'image part en data URI d'un message utilisateur");
    }
}
