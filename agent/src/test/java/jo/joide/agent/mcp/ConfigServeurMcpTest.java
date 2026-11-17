package jo.joide.agent.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonValue;

/**
 * La configuration `.mcp.json` (lot R) : le format Claude
 * Code/Cursor passe inchangé — les trois transports, l'inférence
 * sans type, l'identité (URL ou commande) qui lie approbation et
 * signatures, l'écriture inverse lisible par eux.
 */
class ConfigServeurMcpTest {

    private static final String FICHIER = """
            {
              "mcpServers": {
                "linear": { "type": "http", "url": "https://mcp.example/mcp" },
                "docs":   { "type": "sse", "url": "https://example.com/sse",
                            "headers": { "Authorization": "Bearer abc" } },
                "local":  { "type": "stdio", "command": "npx",
                            "args": ["-y", "@modelcontextprotocol/server-fs", "."] },
                "auto":   { "url": "https://inferre.example" },
                "casse":  { "url": "https://desactive.example", "disabled": true },
                "vide":   { }
              }
            }""";

    @Test
    void leFormatClaudeCodePasseInchange() {
        List<ConfigServeurMcp> serveurs =
                ConfigServeurMcp.analyserFichier(FICHIER);
        assertEquals(5, serveurs.size(),
                "l'entrée sans sens est sautée, pas une erreur");
        ConfigServeurMcp linear = serveurs.get(0);
        assertEquals("linear", linear.nom);
        assertEquals(ConfigServeurMcp.Transport.HTTP, linear.transport);
        assertEquals("https://mcp.example/mcp", linear.url);
        assertEquals("url:https://mcp.example/mcp", linear.identite(),
                "l'identité est l'URL : pointer ailleurs est un "
                        + "AUTRE serveur");
        ConfigServeurMcp docs = serveurs.get(1);
        assertEquals(ConfigServeurMcp.Transport.SSE, docs.transport);
        assertEquals("Bearer abc", docs.entetes.get("Authorization"));
        ConfigServeurMcp local = serveurs.get(2);
        assertEquals(ConfigServeurMcp.Transport.STDIO, local.transport);
        assertEquals("stdio:npx -y @modelcontextprotocol/server-fs .",
                local.identite(),
                "l'identité stdio est la commande et ses arguments");
        assertTrue(local.desactive == false);
        ConfigServeurMcp casse = serveurs.get(4);
        assertTrue(casse.desactive);
    }

    @Test
    void lesVariantesDeTypeEtLInferenceSontAcceptees() {
        List<ConfigServeurMcp> serveurs = ConfigServeurMcp
                .analyserFichier("""
                        { "servers": {
                          "a": { "type": "streamable-http", "url": "https://a" },
                          "b": { "type": "STREAMABLEHTTP", "url": "https://b" },
                          "c": { "command": "uvx", "args": ["x"] }
                        } }""");
        assertEquals(ConfigServeurMcp.Transport.HTTP,
                serveurs.get(0).transport);
        assertEquals(ConfigServeurMcp.Transport.HTTP,
                serveurs.get(1).transport,
                "la casse et les variantes du type passent");
        assertEquals(ConfigServeurMcp.Transport.STDIO,
                serveurs.get(2).transport,
                "une commande sans type implique stdio");
    }

    @Test
    void unFichierCasseeRendUneListeVide() {
        assertTrue(ConfigServeurMcp.analyserFichier(
                "not json at all").isEmpty());
        assertTrue(ConfigServeurMcp.analyserFichier("").isEmpty());
        assertTrue(ConfigServeurMcp.analyserFichier("{\"x\":1}")
                .isEmpty(), "ni mcpServers ni servers : vide");
    }

    @Test
    void lEcritureInverseEstLisibleParClaudeCode() {
        List<ConfigServeurMcp> serveurs = ConfigServeurMcp
                .analyserFichier(FICHIER);
        String ecrit = ConfigServeurMcp.ecrireFichier(serveurs);
        List<ConfigServeurMcp> relus =
                ConfigServeurMcp.analyserFichier(ecrit);
        assertEquals(serveurs.size(), relus.size());
        for (int k = 0; k < serveurs.size(); k++) {
            assertEquals(serveurs.get(k).identite(),
                    relus.get(k).identite(),
                    "l'aller-retour du fichier préserve l'identité");
            assertEquals(serveurs.get(k).desactive,
                    relus.get(k).desactive);
        }
    }

    @Test
    void leSchemaDeTaskStatusEstValide() {
        // la pseudo-outil des jobs longs porte un schéma qui se parse
        JsonValue schema = Json.parse("{\"type\":\"object\","
                + "\"properties\":{\"id\":{\"type\":\"string\"}},"
                + "\"required\":[\"id\"]}").root;
        assertNotNull(schema.member("properties"));
        assertNull(Json.parse("not json").root);
    }
}
