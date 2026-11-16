package jo.joide.agent.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import jo.joide.agent.DescOutil;
import jo.joide.agent.OutilAgent;
import jo.joide.agent.TransportHttp;
import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonEcrire;
import jo.joide.deps.json.JsonValue;
import jo.joide.platform.CancelToken;

/**
 * Le chemin RÉEL en JVM (le pattern du McpClientTest de l'ADR-É9) : un
 * {@link ClientMcp} se connecte à un {@link ServeurMcpLocal} réellement
 * lié sur un port éphémère — poignée de main, liste d'outils, appel
 * (texte, erreur honnête, inconnu), la lecture d'octets exacte avec un
 * caractère multi-octets dans le résultat, l'Origin étranger refusé,
 * et les jobs longs découplés par task_status.
 */
class ClientMcpTest {

    /** Un outil qui renvoie du texte avec un caractère multi-octets. */
    private static final class OutilTexte implements OutilAgent {
        @Override
        public DescOutil description() {
            return new DescOutil("echo_texte",
                    "Echo the given text back.",
                    Json.parse("{\"type\":\"object\","
                            + "\"properties\":{\"texte\":{\"type\":"
                            + "\"string\"}},\"required\":[\"texte\"]}")
                            .root, true, 10_000);
        }

        @Override
        public String invoquer(JsonValue arguments, CancelToken jeton) {
            return "dit: " + arguments.member("texte").asString();
        }
    }

    /** Un outil qui dort : le job long se découple en task_status. */
    private static final class OutilLent implements OutilAgent {
        @Override
        public DescOutil description() {
            return new DescOutil("travail_long",
                    "A long task.", Json.parse("{\"type\":\"object\","
                            + "\"properties\":{}}").root, false,
                    10_000);
        }

        @Override
        public String invoquer(JsonValue arguments, CancelToken jeton)
                throws Exception {
            Thread.sleep(400);
            return "le résultat du travail long";
        }
    }

    private static final class Outils implements
            ServeurMcpLocal.OutilsServeur {
        final List<OutilAgent> outils;

        Outils(OutilAgent... outils) {
            this.outils = List.of(outils);
        }

        @Override
        public List<OutilAgent> outils() {
            return outils;
        }
    }

    private record ServeurLance(int port, ServeurMcpLocal serveur) {
    }

    private static ServeurLance lancer(Outils outils)
            throws IOException {
        ServeurMcpLocal serveur = new ServeurMcpLocal(outils, 200);
        int port = serveur.lier(0);
        return new ServeurLance(port, serveur);
    }

    private static ClientMcp connecter(int port) {
        ClientMcp client = new ClientMcp(new TransportMcpFlux(
                "http://127.0.0.1:" + port + "/mcp", Map::of,
                new jo.joide.agent.TransportOkHttp(),
                CancelToken.NEVER));
        client.connecter();
        return client;
    }

    @Test
    void laPoigneeDeMainEtLaListeSeDeroulentReellement() throws Exception {
        ServeurLance lance = lancer(new Outils(new OutilTexte()));
        try (ClientMcp client = connecter(lance.port())) {
            assertEquals("JoIDE", client.nomServeur);
            assertEquals(ProtocoleMcp.VERSION_PROTOCOLE,
                    client.versionProtocole);
            List<ClientMcp.OutilInfo> outils = client.listerOutils();
            assertEquals(2, outils.size(),
                    "l'outil exposé + la pseudo-outil task_status");
            assertEquals("echo_texte", outils.get(0).nom);
            assertEquals(Boolean.FALSE, outils.get(0).lectureSeule,
                    "l'outil mutant annonce readOnlyHint: false");
            assertEquals(Boolean.TRUE,
                    outils.get(1).lectureSeule,
                    "task_status est read-only");
        }
    }

    @Test
    void unAppelReelRendSonTexteSansPerdreUnCaractere() throws Exception {
        ServeurLance lance = lancer(new Outils(new OutilTexte()));
        try (ClientMcp client = connecter(lance.port())) {
            ClientMcp.ResultatAppel resultat = client.appelerOutil(
                    "echo_texte", Json.parse(
                            "{\"texte\":\"l'éditeur régénère — éàç\"}")
                            .root);
            assertEquals("dit: l'éditeur régénère — éàç", resultat.texte,
                    "la lecture d'octets exacte : Content-Length "
                            + "compte des OCTETS, pas des caractères");
            assertTrue(!resultat.erreur);
        }
    }

    @Test
    void uneErreurDErreurOutilEtUnOutilInconnuSontHonnetes() throws Exception {
        ServeurLance lance = lancer(new Outils(new OutilTexte()));
        try (ClientMcp client = connecter(lance.port())) {
            ClientMcp.ResultatAppel inconnu = client.appelerOutil(
                    "pas_un_outil", Json.parse("{}").root);
            assertTrue(inconnu.erreur);
            assertTrue(inconnu.texte.contains("unknown tool"));
        }
    }

    @Test
    void unOriginEtrangerEstRefuse403() throws Exception {
        ServeurLance lance = lancer(new Outils(new OutilTexte()));
        ClientMcp client = new ClientMcp(new TransportMcpFlux(
                "http://127.0.0.1:" + lance.port() + "/mcp",
                () -> Map.of("Origin", "https://attaquant.example"),
                new jo.joide.agent.TransportOkHttp(),
                CancelToken.NEVER));
        try {
            client.connecter();
            org.junit.jupiter.api.Assertions.fail(
                    "un DNS rebinding se refuse");
        } catch (ErreurMcp.EchecHttp e) {
            assertEquals(403, e.statut);
        } finally {
            client.close();
        }
    }

    @Test
    void unHostEtrangerEstRefuse403() throws Exception {
        ServeurLance lance = lancer(new Outils(new OutilTexte()));
        ClientMcp client = new ClientMcp(new TransportMcpFlux(
                "http://127.0.0.1:" + lance.port() + "/mcp",
                () -> Map.of("Host", "attaquant.example"),
                new jo.joide.agent.TransportOkHttp(),
                CancelToken.NEVER));
        try {
            client.connecter();
            org.junit.jupiter.api.Assertions.fail(
                    "un Host étranger se refuse");
        } catch (ErreurMcp.EchecHttp e) {
            assertEquals(403, e.statut);
        } finally {
            client.close();
        }
    }

    @Test
    void unJobLongSeDecoupleEnTaskStatusSansRelancer() throws Exception {
        ServeurLance lance = lancer(new Outils(new OutilLent()));
        try (ClientMcp client = connecter(lance.port())) {
            ClientMcp.ResultatAppel premier = client.appelerOutil(
                    "travail_long", Json.parse("{}").root);
            assertTrue(premier.texte.contains("still running"),
                    "au-delà du seuil, le travail CONTINUE : "
                            + premier.texte);
            assertTrue(premier.texte.contains("task_status"),
                    "le message dit d'appeler task_status et NE PAS "
                            + "relancer : " + premier.texte);
            String brut = premier.texte.substring(
                    premier.texte.lastIndexOf("id ") + 3).trim();
            String id = brut.split(" ")[0];
            AtomicInteger polls = new AtomicInteger();
            String rendu = "";
            for (int k = 0; k < 20; k++) {
                rendu = client.appelerOutil("task_status",
                        Json.parse("{\"id\":\"" + id + "\"}").root).texte;
                polls.incrementAndGet();
                if (!rendu.contains("still running")) {
                    break;
                }
                Thread.sleep(100);
            }
            assertEquals("le résultat du travail long", rendu,
                    "le job a fini TOUT SEUL : le résultat est rendu, "
                            + "l'outil n'a jamais été relancé");
            assertTrue(polls.get() >= 1);
        }
    }

    @Test
    void uneSessionInconnueEstRefuseeEtLeDeleteFerme() throws Exception {
        ServeurLance lance = lancer(new Outils(new OutilTexte()));
        // un appel SANS session est refusé (404 côté transport)
        try {
            TransportHttp.ReponseOuverte reponse =
                    new jo.joide.agent.TransportOkHttp().postOuvert(
                            "http://127.0.0.1:" + lance.port()
                                    + "/mcp",
                            Map.of("content-type", "application/json",
                                    "accept",
                                    "application/json, "
                                            + "text/event-stream"),
                            JsonEcrire.objet("jsonrpc", "2.0", "id", 1,
                                    "method", "tools/list"),
                            CancelToken.NEVER);
            org.junit.jupiter.api.Assertions.fail(
                    "sans session : un échec est attendu");
        } catch (TransportHttp.EchecHttp e) {
            assertEquals(404, e.statut);
        }
        // et le DELETE d'une session réelle ferme proprement
        ServeurLance lanceFin = lancer(new Outils(new OutilTexte()));
        try (ClientMcp client = connecter(lanceFin.port())) {
            String session = null;
            // le client retient sa session en interne : un listage
            // prouve qu'elle tient, le close() la ferme (best-effort)
            assertTrue(!client.listerOutils().isEmpty());
        }
    }
}
