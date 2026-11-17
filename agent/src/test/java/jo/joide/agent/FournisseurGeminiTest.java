package jo.joide.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonEcrire;
import jo.joide.deps.json.JsonValue;
import jo.joide.platform.CancelToken;

/**
 * L'adaptateur Gemini contre des trames SSE enregistrées (la méthode
 * de l'ADR-É9) : appels ENTIERS avec corrélation par nom, signature
 * de réflexion écho, usage qui soustrait le cache, note volatile
 * pliée en system-reminder du dernier tour utilisateur, refus par
 * finishReason, et le cache de contexte serveur à la politique
 * conservatrice (rien au premier tour, échec mémorisé, TTL rafraîchi
 * une minute avant).
 */
class FournisseurGeminiTest {

    private static TransportHttp.TrameSse trame(String donnees) {
        return new TransportHttp.TrameSse(null, donnees);
    }

    /** Un tour complet : texte, réflexion, appel avec signature. */
    private static List<TransportHttp.TrameSse> tourComplet() {
        return List.of(
                trame("{\"candidates\":[{\"content\":{\"parts\":["
                        + "{\"text\":\"Voici\",\"thought\":true}]}}]}"),
                trame("{\"candidates\":[{\"content\":{\"parts\":["
                        + "{\"text\":\"la réponse\"}]}}]}"),
                trame("{\"candidates\":[{\"content\":{\"parts\":["
                        + "{\"functionCall\":{\"name\":\"read_file\","
                        + "\"args\":{\"path\":\"src/A.java\"}},"
                        + "\"thoughtSignature\":\"sig-gemini-1\"}],"
                        + "\"role\":\"model\"},"
                        + "\"finishReason\":\"STOP\"}],"
                        + "\"usageMetadata\":{\"promptTokenCount\":1000,"
                        + "\"candidatesTokenCount\":42,"
                        + "\"cachedContentTokenCount\":300}}"));
    }

    private static RequeteLlm requete() {
        return new RequeteLlm("gemini-3.8-flash", "SYS",
                HistoriqueAgent.vide().messages(), "NOTE", List.of(),
                4096);
    }

    @Test
    void unTourCompletSeTraduitSansPerdreUnOctet() {
        FauxTransport transport = new FauxTransport(List.of(
                tourComplet()));
        FournisseurGemini fournisseur =
                new FournisseurGemini("cle-test");
        List<EvenementFlux> evenements = new ArrayList<>();
        MessageAgent assistant = fournisseur.envoyer(transport,
                requete(), CancelToken.NEVER, evenements::add);
        // réflexion, texte, appel — dans l'ordre du flux
        assertEquals(3, assistant.parties.size());
        assertEquals(PartieContenu.Genre.RAISONNEMENT,
                assistant.parties.get(0).genre);
        assertEquals("Voici", assistant.parties.get(0).texte);
        assertEquals("gemini:thought",
                assistant.parties.get(0).signature);
        assertEquals(PartieContenu.Genre.TEXTE,
                assistant.parties.get(1).genre);
        assertEquals("la réponse", assistant.parties.get(1).texte);
        PartieContenu appel = assistant.parties.get(2);
        assertEquals(PartieContenu.Genre.APPEL_OUTIL, appel.genre);
        assertEquals("read_file", appel.idAppel,
                "le nom sert de corrélation (pas d'identifiant Gemini)");
        assertEquals("read_file", appel.nomOutil);
        assertEquals("{\"path\":\"src/A.java\"}", appel.arguments);
        assertEquals("sig-gemini-1", appel.signature,
                "la thoughtSignature voyage avec l'appel — elle doit "
                        + "revenir au tour suivant");
        // l'usage : les entrées facturées = prompt − cache
        UsageLlm usage = evenements.stream()
                .filter(e -> e.genre == EvenementFlux.Genre.USAGE)
                .findFirst().orElseThrow().usage;
        assertEquals(700, usage.entrees);
        assertEquals(42, usage.sorties);
        assertEquals(300, usage.lecturesCache);
    }

    @Test
    void laRequetePlieLaNoteEnSystemReminderDuDernierTour() {
        FauxTransport transport = new FauxTransport(List.of(
                tourComplet()));
        FournisseurGemini fournisseur =
                new FournisseurGemini("cle-test");
        fournisseur.envoyer(transport, requete(), CancelToken.NEVER,
                null);
        String corps = transport.requetes.get(0).corps();
        JsonValue envoye = Json.parse(corps).root;
        JsonValue dernier = envoye.member("contents").items().get(
                envoye.member("contents").items().size() - 1);
        String part = JsonEcrire.valeur(dernier);
        assertTrue(part.contains("<system-reminder>")
                        && part.contains("NOTE"),
                "la note volatile se plie dans le dernier tour "
                        + "utilisateur : " + part);
        assertEquals("cle-test",
                transport.requetes.get(0).entetes().get("x-goog-api-key"),
                "la clé part par en-tête, jamais dans l'URL");
    }

    @Test
    void laRequetePorteSystemeEtOutilsEtLePlafondAvecMarge() {
        FauxTransport transport = new FauxTransport(List.of(
                tourComplet()));
        FournisseurGemini fournisseur = new FournisseurGemini(
                FournisseurGemini.URL_DEFAUT, "cle-test");
        RequeteLlm avecOutil = new RequeteLlm("gemini-3.8-flash",
                "SYS", List.of(), "",
                List.of(new DescOutil("read_file", "Lit un fichier",
                        Json.parse("{\"type\":\"object\","
                                + "\"properties\":{\"path\":"
                                + "{\"type\":\"string\"}},"
                                + "\"additionalProperties\":false}")
                                .root, false, 100)), 4096);
        fournisseur.envoyer(transport, avecOutil, CancelToken.NEVER,
                null);
        String corps = transport.requetes.get(0).corps();
        assertTrue(corps.contains("system_instruction"));
        assertTrue(corps.contains("function_declarations"));
        assertTrue(corps.contains("read_file"));
        // additionalProperties strippé (Gemini le rejette)
        assertTrue(!corps.contains("additionalProperties"));
        // plafond 4 096 + marge défaut 16 384 = 20 480
        assertTrue(corps.contains("\"maxOutputTokens\":20480"),
                corps);
    }

    @Test
    void leNiveauReflexionSuitLaGenerationDuModele() {
        // Gemini 3 : le NIVEAU ; la famille 2.5 : le BUDGET
        FournisseurGemini gen3 = new FournisseurGemini(
                FournisseurGemini.URL_DEFAUT, "cle", "low",
                new CacheContexteGemini());
        FournisseurGemini gen25 = new FournisseurGemini(
                FournisseurGemini.URL_DEFAUT, "cle", "low",
                new CacheContexteGemini());
        FauxTransport t3 = new FauxTransport(List.of(tourComplet()));
        FauxTransport t25 = new FauxTransport(List.of(tourComplet()));
        gen3.envoyer(t3, new RequeteLlm("gemini-3.8-flash", "SYS",
                List.of(), "", List.of(), 4096), CancelToken.NEVER,
                null);
        gen25.envoyer(t25, new RequeteLlm("gemini-2.5-flash", "SYS",
                List.of(), "", List.of(), 4096), CancelToken.NEVER,
                null);
        assertTrue(t3.requetes.get(0).corps()
                .contains("\"thinkingLevel\":\"low\""), t3.requetes
                .get(0).corps());
        assertTrue(t25.requetes.get(0).corps()
                .contains("\"thinkingBudget\":2048"), t25.requetes
                .get(0).corps());
        // sur un Pro 3, minimal n'existe pas : low
        FauxTransport tpro = new FauxTransport(List.of(tourComplet()));
        FournisseurGemini pro = new FournisseurGemini(
                FournisseurGemini.URL_DEFAUT, "cle", "minimal",
                new CacheContexteGemini());
        pro.envoyer(tpro, new RequeteLlm("gemini-3.1-pro", "SYS",
                List.of(), "", List.of(), 4096), CancelToken.NEVER,
                null);
        assertTrue(tpro.requetes.get(0).corps()
                .contains("\"thinkingLevel\":\"low\""), tpro.requetes
                .get(0).corps());
        // sur un Pro 2.5, le minimum 128 force le budget 0
        FauxTransport tpro25 = new FauxTransport(
                List.of(tourComplet()));
        FournisseurGemini pro25 = new FournisseurGemini(
                FournisseurGemini.URL_DEFAUT, "cle", "minimal",
                new CacheContexteGemini());
        pro25.envoyer(tpro25, new RequeteLlm("gemini-2.5-pro", "SYS",
                List.of(), "", List.of(), 4096), CancelToken.NEVER,
                null);
        assertTrue(tpro25.requetes.get(0).corps()
                .contains("\"thinkingBudget\":128"), tpro25.requetes
                .get(0).corps());
    }

    @Test
    void laSignatureRepartSurLaFunctionCall() {
        // le tour 2 rejoue l'appel : la functionCall porte la
        // thoughtSignature reçue au tour 1
        FauxTransport transport = new FauxTransport(List.of(
                tourComplet(), tourComplet()));
        FournisseurGemini fournisseur =
                new FournisseurGemini("cle-test");
        MessageAgent assistant = fournisseur.envoyer(transport,
                requete(), CancelToken.NEVER, null);
        HistoriqueAgent historique = new HistoriqueAgent(List.of());
        historique.ajouter(assistant);
        historique.ajouter(new MessageAgent(
                MessageAgent.Role.UTILISATEUR,
                List.of(PartieContenu.resultat("read_file",
                        "contenu du fichier", false))));
        RequeteLlm requete2 = new RequeteLlm("gemini-3.8-flash", "SYS",
                historique.messages(), "", List.of(), 4096);
        fournisseur.envoyer(transport, requete2, CancelToken.NEVER,
                null);
        String corps2 = transport.requetes.get(1).corps();
        assertTrue(corps2.contains("thoughtSignature")
                && corps2.contains("sig-gemini-1"),
                "la signature écho : " + corps2);
        assertTrue(corps2.contains("functionResponse"),
                "le résultat part en functionResponse");
        assertTrue(corps2.contains("\"name\":\"read_file\""),
                "le nom de corrélation avant #");
    }

    @Test
    void lesCorrelationsSeDedoublonnentParSuffixe() {
        List<TransportHttp.TrameSse> tour = List.of(
                trame("{\"candidates\":[{\"content\":{\"parts\":["
                        + "{\"functionCall\":{\"name\":\"outil\",\"args\":{}}},"
                        + "{\"functionCall\":{\"name\":\"outil\",\"args\":{}}}]}}]}"));
        FauxTransport transport = new FauxTransport(List.of(tour));
        FournisseurGemini fournisseur =
                new FournisseurGemini("cle-test");
        MessageAgent assistant = fournisseur.envoyer(transport,
                requete(), CancelToken.NEVER, null);
        assertEquals("outil", assistant.parties.get(0).idAppel);
        assertEquals("outil#1", assistant.parties.get(1).idAppel);
    }

    @Test
    void unRefusGeminiEstUneErreurActionnable() {
        List<TransportHttp.TrameSse> tour = List.of(
                trame("{\"candidates\":[{\"finishReason\":\"SAFETY\","
                        + "\"content\":{\"parts\":[],\"role\":"
                        + "\"model\"}}]}"));
        FauxTransport transport = new FauxTransport(List.of(tour));
        FournisseurGemini fournisseur =
                new FournisseurGemini("cle-test");
        ErreursLlm.ErreurLlm erreur = assertThrows(
                ErreursLlm.ErreurLlm.class,
                () -> fournisseur.envoyer(transport, requete(),
                        CancelToken.NEVER, null));
        assertEquals(ErreursLlm.Genre.CONTENU_REFUSE, erreur.genre);
    }

    @Test
    void unFluxVideEstIllisible() {
        FauxTransport transport = new FauxTransport(List.of(List.of()));
        FournisseurGemini fournisseur =
                new FournisseurGemini("cle-test");
        ErreursLlm.ErreurLlm erreur = assertThrows(
                ErreursLlm.ErreurLlm.class,
                () -> fournisseur.envoyer(transport, requete(),
                        CancelToken.NEVER, null));
        assertEquals(ErreursLlm.Genre.REPONSE_ILLISIBLE, erreur.genre);
    }

    @Test
    void uneImageUtilisateurPartEnInlineData() {
        FauxTransport transport = new FauxTransport(List.of(
                tourComplet()));
        FournisseurGemini fournisseur =
                new FournisseurGemini("cle-test");
        RequeteLlm avecImage = new RequeteLlm("gemini-3.8-flash", "SYS",
                List.of(new MessageAgent(MessageAgent.Role.UTILISATEUR,
                        List.of(PartieContenu.resultat("outil", "ok",
                                false),
                                PartieContenu.image("image/png",
                                        "Zm9vYmFy")))), "", List.of(),
                4096);
        fournisseur.envoyer(transport, avecImage, CancelToken.NEVER,
                null);
        String corps = transport.requetes.get(0).corps();
        assertTrue(corps.contains("inline_data") && corps
                .contains("image/png"), "l'image part en inline_data");
    }

    @Test
    void leCacheDeContexteSuitSaPolitiqueConservatrice() {
        HorlogeFausse horloge = new HorlogeFausse();
        CacheContexteGemini cache = new CacheContexteGemini(horloge);
        List<String> creations = new ArrayList<>();
        CacheContexteGemini.Createur createur = () -> {
            creations.add("cachedContents/1");
            return "cachedContents/1";
        };
        // premier tour : rien (un chat à un tour ne paie pas un cache)
        assertNull(cache.resoudre("cle-a", 5_000, createur));
        assertTrue(creations.isEmpty());
        // deuxième tour : création
        assertEquals("cachedContents/1",
                cache.resoudre("cle-a", 5_000, createur));
        assertEquals(1, creations.size());
        // même clé : référence posée, pas de création
        assertEquals("cachedContents/1",
                cache.resoudre("cle-a", 5_000, createur));
        assertEquals(1, creations.size());
        // échec mémorisé : la même charge utile ne retente jamais
        CacheContexteGemini echoue = new CacheContexteGemini(horloge);
        assertNull(echoue.resoudre("cle-b", 5_000, () -> null),
                "tour 1 : la politique sans cache");
        assertNull(echoue.resoudre("cle-b", 5_000, () -> null),
                "tour 2 : la création échoue");
        assertNull(echoue.resoudre("cle-b", 5_000, () -> "cache-x"),
                "tour 3 : l'échec est mémorisé, jamais de retente");
        // sous le minimum cachable : pas de création, échec mémorisé
        CacheContexteGemini petit = new CacheContexteGemini(horloge);
        assertNull(petit.resoudre("cle-c", 100, createur),
                "tour 1 : la politique sans cache");
        assertNull(petit.resoudre("cle-c", 100, createur),
                "tour 2 : sous le minimum, pas de création");
        assertNull(petit.resoudre("cle-c", 5_000, createur),
                "tour 3 : le payload a mémorisé son échec");
        // TTL : rafraîchissement une minute avant l'expiration
        horloge.avancer(CacheContexteGemini.TTL_SECONDES * 1000
                - CacheContexteGemini.MARGE_EXPIRATION_MS + 1);
        assertEquals("cachedContents/1",
                cache.resoudre("cle-a", 5_000, createur));
        assertEquals(2, creations.size(),
                "recréé une minute avant l'expiration");
    }

    @Test
    void laRequeteDeuxiemeTourReferenceLeCacheServeur() {
        HorlogeFausse horloge = new HorlogeFausse();
        CacheContexteGemini cache = new CacheContexteGemini(horloge);
        // le transport scripte la création de cache par sa file de
        // réponses POST (reponsesPost, additif du lot Q)
        FauxTransport transport = new FauxTransport(
                List.of(tourComplet(), tourComplet()));
        transport.reponsesPost.add("{\"name\":\"cachedContents/9\"}");
        FournisseurGemini fournisseur = new FournisseurGemini(
                FournisseurGemini.URL_DEFAUT, "cle-test", null, cache);
        // un système LARGE : au-dessus du minimum cachable (2 048
        // jetons estimés), sinon le cache ne se crée jamais
        String grosSysteme = "S".repeat(9_000);
        // tour 1 : inline (system_instruction dans la requête)
        fournisseur.envoyer(transport,
                new RequeteLlm("gemini-3.8-flash", grosSysteme,
                        List.of(), "", List.of(), 4096),
                CancelToken.NEVER, null);
        assertTrue(transport.requetes.get(0).corps()
                .contains("system_instruction"));
        // tour 2 : le cache est créé puis référencé — le système ne
        // repart PAS inline aussi (rejeté)
        fournisseur.envoyer(transport,
                new RequeteLlm("gemini-3.8-flash", grosSysteme,
                        List.of(), "", List.of(), 4096),
                CancelToken.NEVER, null);
        assertEquals(
                FournisseurGemini.URL_DEFAUT
                        + "/v1beta/cachedContents",
                transport.requetes.get(1).url(),
                "la création de cache a frappé /v1beta/cachedContents");
        String corps2 = transport.requetes.get(
                transport.requetes.size() - 1).corps();
        assertTrue(corps2.contains("\"cachedContent\":"
                        + "\"cachedContents/9\""),
                "le nom du cache est référencé : " + corps2);
        assertTrue(!corps2.contains("system_instruction"),
                "le système vit dans le cache, jamais en double");
    }

}
