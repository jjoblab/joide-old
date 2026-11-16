package jo.joide.agent.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonEcrire;
import jo.joide.deps.json.JsonValue;

/**
 * Un serveur MCP configuré par l'utilisateur (lot R, ADR-063) — le
 * format de fichier est celui de `.mcp.json` AU FORMAT CLAUDE
 * CODE/CURSOR : une config écrite pour eux marche ici inchangée.
 *
 * <pre>
 * { "mcpServers": {
 *     "linear": { "type": "http", "url": "https://mcp.example/mcp" },
 *     "docs":   { "type": "sse",  "url": "https://example.com/sse",
 *                 "headers": { "Authorization": "Bearer …" } }
 * } }
 * </pre>
 *
 * <p>Sans {@code type}, une {@code url} implique HTTP et une
 * {@code command} implique stdio ; les entrées sans sens (ni URL ni
 * commande) sont sautées. L'IDENTITÉ — ce qui lie approbation et
 * signatures stockées — est l'URL (http/sse) ou la commande et ses
 * arguments (stdio) : pointer ailleurs est un AUTRE serveur, qui
 * exige une nouvelle approbation (la leçon de sécurité de l'ADR-É9).</p>
 */
public final class ConfigServeurMcp {

    /** Comment l'application atteint le serveur. */
    public enum Transport {
        /** Streamable HTTP (le repli SSE historique est tenté par le gestionnaire). */
        HTTP,
        /** Le vieux transport HTTP+SSE seulement. */
        SSE,
        /** Un serveur local en commande — bureau seulement : sur Android, état NON_SUPPORTE honnête. */
        STDIO
    }

    /** Le nom du serveur (la clé du fichier). */
    public final String nom;
    /** Le transport. */
    public final Transport transport;
    /** L'URL (HTTP/SSE), ou null. */
    public final String url;
    /** Les en-têtes configurés (une signature en en-tête reste un choix de l'utilisateur). */
    public final Map<String, String> entetes;
    /** La commande (stdio), ou null. */
    public final String commande;
    /** Les arguments de la commande (stdio). */
    public final List<String> arguments;
    /** Les variables d'environnement (stdio). */
    public final Map<String, String> environnement;
    /** Vrai si l'entrée reste dans le fichier sans être connectée. */
    public final boolean desactive;

    /** @param nom le nom @param transport le transport @param url l'URL @param entetes les en-têtes @param commande la commande @param arguments les arguments @param environnement l'environnement @param desactive désactivé */
    public ConfigServeurMcp(String nom, Transport transport,
                            String url, Map<String, String> entetes,
                            String commande, List<String> arguments,
                            Map<String, String> environnement,
                            boolean desactive) {
        this.nom = Objects.requireNonNull(nom, "name");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.url = url;
        this.entetes = entetes == null ? Map.of() : Map.copyOf(entetes);
        this.commande = commande;
        this.arguments = arguments == null ? List.of()
                : List.copyOf(arguments);
        this.environnement = environnement == null ? Map.of()
                : Map.copyOf(environnement);
        this.desactive = desactive;
    }

    /** Ce qui identifie le serveur pour la confiance et les signatures stockées. */
    public String identite() {
        if (transport == Transport.STDIO) {
            List<String> tout = new ArrayList<>();
            if (commande != null) {
                tout.add(commande);
            }
            tout.addAll(arguments);
            return "stdio:" + String.join(" ", tout);
        }
        return "url:" + (url == null ? "" : url);
    }

    /**
     * Analyse un fichier `.mcp.json` : les entrées sans sens sont
     * sautées, jamais une exception sur un fichier tiers.
     *
     * @param texte le contenu du fichier
     * @return les serveurs, dans l'ordre du fichier
     */
    public static List<ConfigServeurMcp> analyserFichier(String texte) {
        List<ConfigServeurMcp> serveurs = new ArrayList<>();
        Json.Document document = Json.parse(texte == null ? ""
                : texte);
        JsonValue racine = document.root;
        if (racine == null || !document.problems.isEmpty()
                || racine.type() != JsonValue.Type.OBJECT) {
            return serveurs;
        }
        JsonValue table = racine.member("mcpServers");
        if (table.type() != JsonValue.Type.OBJECT) {
            table = racine.member("servers");
        }
        if (table.type() != JsonValue.Type.OBJECT) {
            return serveurs;
        }
        for (String nom : table.keys()) {
            JsonValue entree = table.member(nom);
            if (entree.type() != JsonValue.Type.OBJECT) {
                continue;
            }
            ConfigServeurMcp serveur = analyserEntree(nom, entree);
            if (serveur != null) {
                serveurs.add(serveur);
            }
        }
        return serveurs;
    }

    /** Une entrée du fichier, ou null si elle n'a pas de sens. */
    static ConfigServeurMcp analyserEntree(String nom,
                                           JsonValue entree) {
        String url = texte(entree, "url");
        String commande = texte(entree, "command");
        String type = texte(entree, "type");
        Transport transport;
        if ("sse".equalsIgnoreCase(type)) {
            transport = Transport.SSE;
        } else if ("stdio".equalsIgnoreCase(type)) {
            transport = Transport.STDIO;
        } else if ("http".equalsIgnoreCase(type)
                || "streamable-http".equalsIgnoreCase(type)
                || "streamablehttp".equalsIgnoreCase(type)) {
            transport = Transport.HTTP;
        } else {
            transport = url != null ? Transport.HTTP
                    : commande != null ? Transport.STDIO : null;
        }
        if (transport == null) {
            return null;
        }
        if (transport == Transport.STDIO && commande == null) {
            return null;
        }
        if (transport != Transport.STDIO && url == null) {
            return null;
        }
        return new ConfigServeurMcp(nom, transport, url,
                tableDe(entree, "headers"), commande,
                listeDe(entree, "args"), tableDe(entree, "env"),
                Boolean.TRUE.equals(entree.member("disabled")
                        .asBoolean()));
    }

    /**
     * Écrit les serveurs au même format que celui analysé — la
     * configuration du projet reste lisible par Claude Code/Cursor.
     *
     * @param serveurs les serveurs
     * @return le texte JSON du fichier
     */
    public static String ecrireFichier(
            List<ConfigServeurMcp> serveurs) {
        StringBuilder mcp = new StringBuilder("{\n  \"mcpServers\": {");
        boolean premier = true;
        for (ConfigServeurMcp serveur : serveurs) {
            if (!premier) {
                mcp.append(',');
            }
            premier = false;
            mcp.append("\n    ").append(entre(serveur.nom)).append(": ");
            Map<String, Object> entree = new LinkedHashMap<>();
            if (serveur.transport == Transport.STDIO) {
                entree.put("type", "stdio");
                entree.put("command", serveur.commande == null ? ""
                        : serveur.commande);
                if (!serveur.arguments.isEmpty()) {
                    entree.put("args", serveur.arguments);
                }
                if (!serveur.environnement.isEmpty()) {
                    entree.put("env", serveur.environnement);
                }
            } else {
                entree.put("type",
                        serveur.transport == Transport.SSE ? "sse"
                                : "http");
                entree.put("url", serveur.url == null ? "" : serveur.url);
                if (!serveur.entetes.isEmpty()) {
                    entree.put("headers", serveur.entetes);
                }
            }
            if (serveur.desactive) {
                entree.put("disabled", true);
            }
            mcp.append(JsonEcrire.valeur(entree));
        }
        mcp.append(mcp.length() == "{\n  \"mcpServers\": {".length()
                ? "}}\n" : "\n  }}\n");
        return mcp.toString();
    }

    private static String entre(String nom) {
        return JsonEcrire.valeur(nom);
    }

    private static String texte(JsonValue objet, String cle) {
        String t = objet.member(cle).asString();
        if (t == null) {
            return null;
        }
        String net = t.trim();
        return net.isEmpty() ? null : net;
    }

    private static Map<String, String> tableDe(JsonValue objet,
                                               String cle) {
        JsonValue table = objet.member(cle);
        if (table.type() != JsonValue.Type.OBJECT) {
            return Map.of();
        }
        Map<String, String> sortie = new LinkedHashMap<>();
        for (String k : table.keys()) {
            String valeur = table.member(k).asString();
            if (valeur != null) {
                sortie.put(k, valeur);
            }
        }
        return sortie;
    }

    private static List<String> listeDe(JsonValue objet, String cle) {
        JsonValue liste = objet.member(cle);
        if (liste.type() != JsonValue.Type.ARRAY) {
            return List.of();
        }
        List<String> sortie = new ArrayList<>();
        for (JsonValue item : liste.items()) {
            String valeur = item.asString();
            if (valeur != null) {
                sortie.add(valeur);
            }
        }
        return sortie;
    }

    @Override
    public String toString() {
        return "ConfigServeurMcp[" + nom + "/" + transport + "]";
    }
}
