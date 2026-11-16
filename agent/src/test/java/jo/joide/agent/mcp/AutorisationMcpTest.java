package jo.joide.agent.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jo.joide.agent.TransportHttp;
import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonValue;
import jo.joide.platform.CancelToken;

/**
 * L'autorisation MCP (lot R) : le défi du 401 se parse, la ressource
 * canonique se déduit, le PKCE S256 tombe sur le vecteur de test de
 * la RFC 7636, l'échange part en form-encodé (les secrets JAMAIS dans
 * une URL), la rotation du refresh est tolérée.
 */
class AutorisationMcpTest {

    @Test
    void leDefiBearerSeParse() {
        Map<String, String> defi = AutorisationMcp.analyserDefi(
                "Bearer realm=\"x\", resource_metadata="
                        + "\"https://example.com/.well-known/x\", "
                        + "scope=\"a b\"");
        assertEquals("https://example.com/.well-known/x",
                defi.get("resource_metadata"));
        assertEquals("a b", defi.get("scope"));
        assertTrue(AutorisationMcp.analyserDefi(null).isEmpty());
        assertTrue(AutorisationMcp.analyserDefi("").isEmpty());
    }

    @Test
    void laRessourceCanoniqueNormaliseLAudience() {
        assertEquals("https://serveur.example/mcp",
                AutorisationMcp.ressourceCanonique(
                        "HTTPS://SERVEUR.example/mcp/"));
        assertEquals("http://127.0.0.1:8765/mcp",
                AutorisationMcp.ressourceCanonique(
                        "http://127.0.0.1:8765/mcp"),
                "un port non standard fait partie de l'audience");
        assertEquals("https://exemple.fr",
                AutorisationMcp.ressourceCanonique(
                        "https://exemple.fr"),
                "le port par défaut n'écrit pas");
    }

    @Test
    void leChallengePkceTombeSurLeVecteurDeLaRfc7636() {
        // le vecteur A.4 de la RFC 7636 (S256)
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                AutorisationMcp.defiPcke(
                        "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
    }

    @Test
    void lEchangeCodeContreJetonsPartEnFormEncode() {
        FauxAuth transport = new FauxAuth(
                "{\"access_token\":\"acces-1\","
                        + "\"refresh_token\":\"refresh-1\","
                        + "\"expires_in\":3600}");
        AutorisationMcp.Tentative tentative = new AutorisationMcp
                .Tentative("https://auth.example/authorize", "etat",
                        "verificateur", "http://127.0.0.1:0/callback",
                        "https://auth.example/token", "client-1", null,
                        "https://serveur.example/mcp");
        JetonsMcp jetons = AutorisationMcp.echanger(tentative, "code-1",
                transport, CancelToken.NEVER);
        assertEquals("acces-1", jetons.acces);
        assertEquals("refresh-1", jetons.refresh);
        assertEquals("client-1", jetons.clientId);
        assertEquals("https://serveur.example/mcp", jetons.ressource);
        // le corps est du form-encodé, PAS de JSON : les secrets ne
        // voyagent jamais dans une URL (le faux retient l'URL entière)
        String corps = transport.requetes.get(0).corps();
        assertTrue(corps.contains("grant_type=authorization_code"));
        assertTrue(corps.contains("code_verifier=verificateur"));
        assertTrue(corps.contains("resource=https%3A%2F%2Fserveur"
                + ".example%2Fmcp"),
                "le paramètre resource (RFC 8707) repart");
        assertEquals("https://auth.example/token",
                transport.requetes.get(0).url());
    }

    @Test
    void leRefreshTolereLaRotation() {
        FauxAuth transport = new FauxAuth(
                "{\"access_token\":\"acces-2\"}");
        JetonsMcp anciens = new JetonsMcp("acces-1", "refresh-1", null,
                "https://auth.example/token", "client-1", null,
                "https://serveur.example/mcp");
        JetonsMcp renouves = AutorisationMcp.rafraichir(anciens,
                transport, CancelToken.NEVER);
        assertEquals("acces-2", renouves.acces);
        assertEquals("refresh-1", renouves.refresh,
                "sans nouveau refresh, l'ANCIEN tient (rotation "
                        + "tolérée)");
        String corps = transport.requetes.get(0).corps();
        assertTrue(corps.contains("grant_type=refresh_token"));
        assertTrue(corps.contains("resource="), "la ressource repart");
    }

    @Test
    void unServeurSansS256EstRefuseAvecUnConseil() {
        FauxAuth transport = new FauxAuth(null);
        // le serveur expose ses métadonnées mais sans S256 — deux
        // fois : la sonde de ressource protégée puis celle du
        // serveur d'autorisation
        String metadonnees = "{\"authorization_endpoint\":"
                + "\"https://auth.example/authorize\","
                + "\"token_endpoint\":\"https://auth.example/token\","
                + "\"registration_endpoint\":"
                + "\"https://auth.example/register\","
                + "\"code_challenge_methods_supported\":[\"plain\"]}";
        transport.reponsesGet.add(metadonnees);
        transport.reponsesGet.add(metadonnees);
        try {
            AutorisationMcp.commencer("https://serveur.example/mcp",
                    "http://127.0.0.1:0/callback", null, transport,
                    CancelToken.NEVER);
            org.junit.jupiter.api.Assertions.fail(
                    "l'absence de S256 doit refuser");
        } catch (ErreurMcp e) {
            assertTrue(e.getMessage().contains("PKCE"),
                    e.getMessage());
            assertTrue(e.getMessage().contains("headers"),
                    "le conseil : le jeton en en-tête");
        }
    }

    /**
     * Un faux transport pour les flux d'autorisation : le GET sert
     * des métadonnées en file, le POST de jetons répond la charge
     * scriptée.
     */
    private static final class FauxAuth implements TransportHttp {
        final List<Capt> requetes = new ArrayList<>();
        final List<String> reponsesGet = new ArrayList<>();
        private final String corpsPost;

        FauxAuth(String corpsPost) {
            this.corpsPost = corpsPost;
        }

        @Override
        public ReponseSse postSse(String url,
                                  Map<String, String> entetes,
                                  String corps, CancelToken jeton)
                throws java.io.IOException {
            throw new java.io.IOException("not needed");
        }

        @Override
        public Reponse post(String url, Map<String, String> entetes,
                            String corps, CancelToken jeton)
                throws java.io.IOException {
            requetes.add(new Capt(url, corps));
            if (corpsPost == null) {
                throw new EchecHttp(400, Map.of(), "{}");
            }
            return new ReponseFixe(corpsPost);
        }

        @Override
        public Reponse get(String url, Map<String, String> entetes,
                           CancelToken jeton)
                throws java.io.IOException {
            requetes.add(new Capt(url, ""));
            if (reponsesGet.isEmpty()) {
                throw new java.io.IOException("no metadata scripted");
            }
            return new ReponseFixe(reponsesGet.remove(0));
        }

        private record Capt(String url, String corps) {
        }

        private static final class ReponseFixe implements Reponse {
            private final String corps;

            ReponseFixe(String corps) {
                this.corps = corps;
            }

            @Override
            public int statut() {
                return 200;
            }

            @Override
            public String entete(String nom) {
                return null;
            }

            @Override
            public String corps() {
                return corps;
            }
        }
    }
}
