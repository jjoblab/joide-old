package jo.joide.agent.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import jo.joide.agent.OutilAgent;
import jo.joide.platform.CancelToken;
import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonEcrire;
import jo.joide.deps.json.JsonValue;

/**
 * La règle de sécurité centrale du gestionnaire (lot R, critère du
 * lot : « un outil mutant distant n'est jamais lancé deux fois ») : la
 * session expirée SE retente (le serveur a prouvé qu'il n'a pas
 * exécuté), la rupture en cours d'appel ne se retente JAMAIS (l'outil
 * a pu s'exécuter ou non — le message le dit), les états se publient
 * sans déduction, les transports stdio sont NON_SUPPORTE honnêtes.
 */
class GestionnaireMcpTest {

    /** Un transport MCP factice : la scène de chaque message envoyé. */
    private static final class FauxMcpTransport implements TransportMcp {
        enum Scene { SESSION_PERIMEE, RUPTURE_EN_COURS, REPONSE }

        final List<Scene> scenes;
        final Runnable aLaFermeture;
        private Remettre remettre = message -> {
        };
        int index;

        FauxMcpTransport(List<Scene> scenes, Runnable aLaFermeture) {
            this.scenes = scenes;
            this.aLaFermeture = aLaFermeture;
        }

        @Override
        public void demarrer(Remettre remettre, OnFerme onFerme) {
            this.remettre = remettre;
        }

        @Override
        public void envoyer(String message) throws java.io.IOException {
            // les notifications ne consomment pas de scène : les
            // scènes décrivent les REQUÊTES attendues
            JsonValue demande = Json.parse(message).root;
            if (demande.member("id").isNull()) {
                return;
            }
            Scene scene = index < scenes.size()
                    ? scenes.get(index) : Scene.REPONSE;
            index++;
            switch (scene) {
                case SESSION_PERIMEE -> throw new ErreurMcp
                        .SessionPerimee();
                case RUPTURE_EN_COURS -> throw new java.io.IOException(
                        "connection reset during call");
                case REPONSE -> remettre.message(JsonEcrire.objet(
                        "jsonrpc", "2.0", "id",
                        demande.member("id"), "result",
                        resultat(message)));
            }
        }

        private static JsonValue resultat(String message) {
            JsonValue demande = Json.parse(message).root;
            String methode = demande.member("method").asString();
            if ("initialize".equals(methode)) {
                return Json.parse("{\"protocolVersion\":\""
                        + ProtocoleMcp.VERSION_PROTOCOLE
                        + "\",\"serverInfo\":{\"name\":\"faux\","
                        + "\"version\":\"1\"}}").root;
            }
            if ("tools/list".equals(methode)) {
                return Json.parse("{\"tools\":[{\"name\":\"outil\","
                        + "\"inputSchema\":{\"type\":\"object\","
                        + "\"properties\":{}}},{\"name\":\"lecteur\","
                        + "\"annotations\":{\"readOnlyHint\":true},"
                        + "\"inputSchema\":{\"type\":\"object\","
                        + "\"properties\":{}}}]}").root;
            }
            if ("tools/call".equals(methode)) {
                return Json.parse("{\"content\":[{\"type\":\"text\","
                        + "\"text\":\"rendu\"}]}").root;
            }
            return Json.parse("{}").root;
        }

        @Override
        public void close() {
            if (aLaFermeture != null) {
                aLaFermeture.run();
            }
        }
    }

    /** Un gestionnaire synchronisé (les connexions attendues par les tests). */
    private static GestionnaireMcp gestionnaire(
            List<FauxMcpTransport.Scene> scenes,
            AtomicInteger connexions) {
        return new GestionnaireMcp(null, null, CancelToken.NEVER,
                true, null, config -> new FauxMcpTransport(scenes,
                connexions::incrementAndGet));
    }

    private static ConfigServeurMcp configHttp() {
        return new ConfigServeurMcp("faux",
                ConfigServeurMcp.Transport.HTTP,
                "http://faux.example/mcp", Map.of(), null, List.of(),
                Map.of(), false);
    }

    private static void attendreConnecte(GestionnaireMcp gestionnaire,
                                         String nom) throws Exception {
        for (int k = 0; k < 50; k++) {
            for (GestionnaireMcp.EtatServeur etat : gestionnaire
                    .etats()) {
                if (etat.nom.equals(nom)
                        && etat.statut == GestionnaireMcp.Statut.CONNECTE) {
                    return;
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("le serveur ne se connecte jamais : "
                + gestionnaire.etats());
    }

    @Test
    void laSessionPerimeeSeRetenteUneFoisPuisRend() throws Exception {
        AtomicInteger connexions = new AtomicInteger();
        AtomicInteger generation = new AtomicInteger();
        // la scène : le PREMIER tools/call tombe sur une session
        // périmée (PROUVÉ non exécuté) ; la session RECONNECTÉE rend
        // le résultat — chaque génération de connexion a ses scènes
        GestionnaireMcp gestionnaire = new GestionnaireMcp(null, null,
                CancelToken.NEVER, true, null, config -> {
            int g = generation.incrementAndGet();
            connexions.incrementAndGet();
            return new FauxMcpTransport(g == 1
                    ? List.of(FauxMcpTransport.Scene.REPONSE,
                    FauxMcpTransport.Scene.REPONSE,
                    FauxMcpTransport.Scene.SESSION_PERIMEE)
                    : List.of(FauxMcpTransport.Scene.REPONSE,
                    FauxMcpTransport.Scene.REPONSE,
                    FauxMcpTransport.Scene.REPONSE),
                    null);
        });
        try (gestionnaire) {
            gestionnaire.maj(List.of(configHttp()));
            attendreConnecte(gestionnaire, "faux");
            List<OutilAgent> outils = gestionnaire.outils();
            assertEquals(2, outils.size());
            String texte = ((OutilMcpDistant) outils.get(0)).invoquer(
                    Json.parse("{}").root, CancelToken.NEVER);
            assertEquals("rendu", texte,
                    "après la reconnexion prouvée, l'outil rend");
            assertEquals(2, connexions.get(),
                    "UNE reconnexion : le serveur a prouvé qu'il "
                            + "n'avait pas exécuté l'outil");
        }
    }

    @Test
    void uneRuptureEnCoursDAppelNeRelanceJamaisLeMutant() throws Exception {
        AtomicInteger connexions = new AtomicInteger();
        List<FauxMcpTransport.Scene> scenes = new ArrayList<>();
        scenes.add(FauxMcpTransport.Scene.REPONSE); // initialize
        scenes.add(FauxMcpTransport.Scene.REPONSE); // tools/list
        scenes.add(FauxMcpTransport.Scene.RUPTURE_EN_COURS);
        GestionnaireMcp gestionnaire = gestionnaire(scenes, connexions);
        try (gestionnaire) {
            gestionnaire.maj(List.of(configHttp()));
            attendreConnecte(gestionnaire, "faux");
            OutilMcpDistant outil = (OutilMcpDistant) gestionnaire
                    .outils().get(0);
            ErreurMcp erreur = null;
            try {
                outil.invoquer(Json.parse("{}").root,
                        CancelToken.NEVER);
            } catch (ErreurMcp e) {
                erreur = e;
            }
            assertTrue(erreur != null
                            && erreur.getMessage().contains("may or may "
                            + "not have run"),
                    "l'échec avoue l'ambiguïté : "
                            + (erreur == null ? "aucun" : erreur
                            .getMessage()));
            assertEquals(1, connexions.get(),
                    "AUCUNE reconnexion après la rupture : relancer "
                            + "le mutant le lancerait deux fois");
        }
    }

    @Test
    void lesEtatsSePublientEtLaVersionSeVersionne() throws Exception {
        AtomicInteger connexions = new AtomicInteger();
        GestionnaireMcp gestionnaire = gestionnaire(List.of(),
                connexions);
        try (gestionnaire) {
            int avant = gestionnaire.versionOutils();
            gestionnaire.maj(List.of(configHttp()));
            attendreConnecte(gestionnaire, "faux");
            List<GestionnaireMcp.EtatServeur> etats = gestionnaire
                    .etats();
            assertEquals(1, etats.size());
            assertEquals(GestionnaireMcp.Statut.CONNECTE,
                    etats.get(0).statut);
            assertEquals("faux", etats.get(0).nomAnnonce);
            assertTrue(gestionnaire.versionOutils() > avant,
                    "la surface d'outils s'incrémente à la connexion");
        }
    }

    @Test
    void stdioEstNonSupporteHonnetementSurCetAppareil() throws Exception {
        AtomicInteger connexions = new AtomicInteger();
        GestionnaireMcp gestionnaire = new GestionnaireMcp(null, null,
                CancelToken.NEVER, false, null,
                config -> new FauxMcpTransport(List.of(), null));
        try (gestionnaire) {
            gestionnaire.maj(List.of(new ConfigServeurMcp("local",
                    ConfigServeurMcp.Transport.STDIO, null, Map.of(),
                    "npx", List.of("-y", "paquet"), Map.of(), false)));
            for (int k = 0; k < 50; k++) {
                if (gestionnaire.etats().get(0).statut
                        == GestionnaireMcp.Statut.NON_SUPPORTE) {
                    break;
                }
                Thread.sleep(20);
            }
            GestionnaireMcp.EtatServeur etat = gestionnaire.etats()
                    .get(0);
            assertEquals(GestionnaireMcp.Statut.NON_SUPPORTE,
                    etat.statut);
            assertTrue(etat.motif.contains("desktop only"),
                    "le motif dit le vrai : " + etat.motif);
            assertTrue(gestionnaire.outils().isEmpty());
        }
    }

    @Test
    void lesNomsDOutilsSeDedoublonnentEntreServeurs() throws Exception {
        AtomicInteger connexions = new AtomicInteger();
        GestionnaireMcp gestionnaire = new GestionnaireMcp(null, null,
                CancelToken.NEVER, true, null,
                config -> new FauxMcpTransport(List.of(), null));
        try (gestionnaire) {
            gestionnaire.maj(List.of(
                    new ConfigServeurMcp("a-b", ConfigServeurMcp
                            .Transport.HTTP, "http://a", Map.of(),
                            null, List.of(), Map.of(), false),
                    new ConfigServeurMcp("a_b", ConfigServeurMcp
                            .Transport.HTTP, "http://b", Map.of(),
                            null, List.of(), Map.of(), false)));
            attendreConnecte(gestionnaire, "a-b");
            attendreConnecte(gestionnaire, "a_b");
            List<OutilAgent> outils = gestionnaire.outils();
            assertEquals(4, outils.size(),
                    "deux serveurs × deux outils : les noms se "
                            + "nettoient pareil mais TOUS survivent");
            List<String> noms = new ArrayList<>();
            for (OutilAgent outil : outils) {
                noms.add(outil.description().nom);
            }
            assertEquals(4, noms.stream().distinct().count(),
                    "les noms dédoublonnés : " + noms);
        }
    }

    @Test
    void lesDescriptionsPortentLeServeurEtLeMarqueurMutant() throws Exception {
        AtomicInteger connexions = new AtomicInteger();
        GestionnaireMcp gestionnaire = gestionnaire(List.of(),
                connexions);
        try (gestionnaire) {
            gestionnaire.maj(List.of(configHttp()));
            attendreConnecte(gestionnaire, "faux");
            List<OutilAgent> outils = gestionnaire.outils();
            OutilAgent mutant = outils.get(0);
            assertTrue(mutant.description().nom.startsWith("mcp__"),
                    "le préfixe mcp__ : "
                            + mutant.description().nom);
            assertTrue(mutant.description().description
                    .contains("[MCP server \"faux\"]"),
                    "la description est préfixée du serveur");
            assertTrue(mutant.description().mutant,
                    "mutant par défaut (sans readOnlyHint)");
            OutilAgent lecteur = outils.get(1);
            assertTrue(!lecteur.description().mutant,
                    "readOnlyHint=true : l'outil est read-only");
        }
    }
}
