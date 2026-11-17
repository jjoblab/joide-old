package jo.joide.agent.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonEcrire;
import jo.joide.deps.json.JsonValue;

/**
 * Un client Model Context Protocol (lot R, ADR-063) : la poignée de
 * main initialize, la liste des outils SUIVANTE mais BORNÉE, et les
 * appels d'outils — « délibérément le sous-ensemble qu'un agent
 * demande ». Le client ne déclare AUCUNE capacité (pas de sampling,
 * roots, elicitation) : un serveur bien élevé ne demande rien, un
 * serveur qui demande quand même reçoit un method-not-found, et un
 * {@code ping} reçoit sa réponse vide.
 *
 * <p>Les réponses se jumellent par identifiant (les demandes du
 * serveur répondent sur un exécuteur à elles) ; les notifications
 * {@code tools/list_changed} remontent à l'hôte ; la déconnexion
 * complète tous les appels en attente avec la cause — jamais un
 * appel qui attend sans fin.</p>
 */
public final class ClientMcp implements AutoCloseable {

    /** Un outil tel que le serveur le liste (les indications readOnly/destructive sont LES SIENNES ; null quand il n'en a pas donné). */
    public static final class OutilInfo {

        /** Le nom de l'outil chez le serveur. */
        public final String nom;
        /** Le titre (le sien, ou celui d'une annotation). */
        public final String titre;
        /** La description. */
        public final String description;
        /** Le schéma JSON des arguments. */
        public final JsonValue schema;
        /** L'indication readOnlyHint du serveur, ou null. */
        public final Boolean lectureSeule;
        /** L'indication destructiveHint du serveur, ou null. */
        public final Boolean destructif;

        OutilInfo(String nom, String titre, String description,
                  JsonValue schema, Boolean lectureSeule,
                  Boolean destructif) {
            this.nom = nom;
            this.titre = titre;
            this.description = description;
            this.schema = schema;
            this.lectureSeule = lectureSeule;
            this.destructif = destructif;
        }
    }

    /** Ce qu'un appel d'outil a rendu, aplati pour le modèle. */
    public static final class ResultatAppel {

        /** Le texte (blocs joints, ressources incluses, bornés). */
        public final String texte;
        /** Les images (type de média, base64). */
        public final List<String[]> images;
        /** Vrai si l'outil a échoué honnêtement (isError du serveur). */
        public final boolean erreur;

        ResultatAppel(String texte, List<String[]> images,
                      boolean erreur) {
            this.texte = texte;
            this.images = List.copyOf(images);
            this.erreur = erreur;
        }
    }

    private final TransportMcp transport;
    private final AtomicLong identifiants = new AtomicLong();
    private final Map<String, CompletableFuture<JsonValue>> enAttente =
            new ConcurrentHashMap<>();
    private volatile boolean ferme;
    private volatile Throwable fermePar;

    /** Le nom du serveur (renseigné par l'initialize). */
    public volatile String nomServeur;
    /** Sa version. */
    public volatile String versionServeur;
    /** Ses instructions d'usage, envoyées avec l'initialize (pour le prompt système). */
    public volatile String instructions;
    /** La révision de protocole que le serveur a fixée. */
    public volatile String versionProtocole;

    /** Appelé quand le serveur dit que sa liste d'outils a changé. */
    public volatile Runnable outilsChanges;
    /** Appelé une fois, quand le transport s'en va sous le client. */
    public volatile java.util.function.Consumer<Throwable> deconnecte;

    /** @param transport le transport sous le client */
    public ClientMcp(TransportMcp transport) {
        this.transport = transport;
    }

    /**
     * Ouvre le transport et déroule la poignée de main initialize.
     *
     * @throws ErreurMcp classée si la poignée échoue
     */
    public void connecter() {
        connecter(ProtocoleMcp.DELAI_CONNEXION_MS);
    }

    /** @param delaiMs le délai de la poignée de main */
    public void connecter(long delaiMs) {
        try {
            transport.demarrer(this::messageRecu, this::transportFerme);
        } catch (ErreurMcp e) {
            throw e;
        } catch (Exception e) {
            throw new ErreurMcp("The transport failed to open: "
                    + e.getMessage(), e);
        }
        JsonValue resultat = requete("initialize", Json.parse(
                JsonEcrire.objet(
                        "protocolVersion", ProtocoleMcp.VERSION_PROTOCOLE,
                        "capabilities", JsonEcrire.objet(),
                        "clientInfo", JsonEcrire.objet(
                                "name", "JoIDE",
                                "version", "1.0"))).root, delaiMs);
        String version = resultat.member("protocolVersion").asString();
        this.versionProtocole = version == null
                ? ProtocoleMcp.VERSION_PROTOCOLE : version;
        JsonValue info = resultat.member("serverInfo");
        if (info.type() == JsonValue.Type.OBJECT) {
            nomServeur = info.member("name").asString();
            versionServeur = info.member("version").asString();
        }
        String note = resultat.member("instructions").asString();
        instructions = note == null || note.isBlank() ? null : note;
        transport.negocie(this.versionProtocole);
        notifier("notifications/initialized");
    }

    /**
     * Tous les outils du serveur, sa pagination suivie mais BORNÉE.
     *
     * @return les outils dans l'ordre du serveur
     */
    public List<OutilInfo> listerOutils() {
        return listerOutils(ProtocoleMcp.DELAI_CONNEXION_MS);
    }

    /** @param delaiMs le délai par page */
    public List<OutilInfo> listerOutils(long delaiMs) {
        List<OutilInfo> outils = new ArrayList<>();
        String curseur = null;
        for (int page = 0; page < ProtocoleMcp.PAGES_MAX; page++) {
            JsonValue parametres = curseur == null ? null : Json.parse(
                    JsonEcrire.objet("cursor", curseur)).root;
            JsonValue resultat = requete("tools/list", parametres,
                    delaiMs);
            for (JsonValue outil : resultat.member("tools").items()) {
                if (outil.type() != JsonValue.Type.OBJECT) {
                    continue;
                }
                String nom = outil.member("name").asString();
                if (nom == null) {
                    continue;
                }
                JsonValue annotations = outil.member("annotations");
                String titre = outil.member("title").asString();
                if (titre == null
                        && annotations.type() == JsonValue.Type.OBJECT) {
                    titre = annotations.member("title").asString();
                }
                Boolean lecture = annotations.type()
                        == JsonValue.Type.OBJECT
                        ? annotations.member("readOnlyHint")
                        .asBoolean() : null;
                Boolean destructif = annotations.type()
                        == JsonValue.Type.OBJECT
                        ? annotations.member("destructiveHint")
                        .asBoolean() : null;
                outils.add(new OutilInfo(nom, titre,
                        outil.member("description").asString(),
                        outil.member("inputSchema"), lecture,
                        destructif));
            }
            String suivant = resultat.member("nextCursor").asString();
            curseur = suivant == null || suivant.isEmpty() ? null
                    : suivant;
            if (curseur == null) {
                return outils;
            }
        }
        return outils;
    }

    /**
     * Appelle un outil du serveur.
     *
     * @param nom       le nom de l'outil chez le serveur
     * @param arguments les arguments en objet JSON
     * @return le résultat aplati et borné
     */
    public ResultatAppel appelerOutil(String nom, JsonValue arguments) {
        return appelerOutil(nom, arguments, ProtocoleMcp.DELAI_APPEL_MS);
    }

    /** @param nom le nom @param arguments les arguments @param delaiMs le délai de l'appel */
    public ResultatAppel appelerOutil(String nom, JsonValue arguments,
                                      long delaiMs) {
        JsonValue resultat = requete("tools/call", Json.parse(
                JsonEcrire.objet("name", nom,
                        "arguments", arguments)).root, delaiMs);
        return aplatir(resultat);
    }

    /**
     * Envoie une requête et attend sa réponse ; une erreur JSON-RPC
     * devient une {@link ErreurMcp.RefusServeur}, un silence un
     * {@link ErreurMcp.DelaiDepasse}.
     */
    public JsonValue requete(String methode, JsonValue parametres,
                             long delaiMs) {
        Throwable fermeture = fermePar;
        if (fermeture != null) {
            throw new ErreurMcp("The MCP connection is closed: "
                    + fermeture.getMessage(), fermeture);
        }
        if (ferme) {
            throw new ErreurMcp("The MCP connection is closed.");
        }
        String id = String.valueOf(identifiants.incrementAndGet());
        CompletableFuture<JsonValue> attendue = new CompletableFuture<>();
        enAttente.put(id, attendue);
        try {
            String corps = parametres == null
                    ? JsonEcrire.objet("jsonrpc", "2.0", "id", id,
                    "method", methode)
                    : JsonEcrire.objet("jsonrpc", "2.0", "id", id,
                            "method", methode, "params", parametres);
            try {
                transport.envoyer(corps);
            } catch (ErreurMcp e) {
                throw e;
            } catch (Exception e) {
                throw new ErreurMcp("The message could not be sent: "
                        + e.getMessage(), e);
            }
            JsonValue reponse;
            try {
                reponse = attendue.get(delaiMs,
                        TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                throw new ErreurMcp.DelaiDepasse(methode, delaiMs);
            } catch (java.util.concurrent.ExecutionException e) {
                throw new ErreurMcp("The request failed: "
                        + e.getCause().getMessage(), e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ErreurMcp("The request was interrupted.", e);
            }
            JsonValue erreur = reponse.member("error");
            if (erreur.type() == JsonValue.Type.OBJECT) {
                throw new ErreurMcp.RefusServeur(
                        chaine(erreur.member("message"),
                                "The server returned an error."),
                        erreur.member("code").asLong() >= 0
                                ? (int) erreur.member("code").asLong()
                                : null);
            }
            JsonValue resultat = reponse.member("result");
            return resultat.isNull() ? Json.parse("{}").root : resultat;
        } finally {
            enAttente.remove(id);
        }
    }

    /** Une notification (sans réponse attendue). */
    public void notifier(String methode) {
        notifier(methode, null);
    }

    /** @param methode la notification @param parametres les paramètres */
    public void notifier(String methode, JsonValue parametres) {
        String corps = parametres == null
                ? JsonEcrire.objet("jsonrpc", "2.0", "method", methode)
                : JsonEcrire.objet("jsonrpc", "2.0", "method", methode,
                        "params", parametres);
        try {
            transport.envoyer(corps);
        } catch (Exception e) {
            // une notification perdue n'interrompt rien
        }
    }

    /** La réception d'un message : réponse, demande du serveur, ou notification. */
    private void messageRecu(String message) {
        Json.Document document = Json.parse(message);
        JsonValue racine = document.root;
        if (racine == null || !document.problems.isEmpty()
                || racine.type() != JsonValue.Type.OBJECT) {
            return;
        }
        String methode = racine.member("method").asString();
        JsonValue id = racine.member("id");
        if (methode == null && !id.isNull()) {
            CompletableFuture<JsonValue> attendue = enAttente
                    .get(cleDeId(id));
            if (attendue != null) {
                attendue.complete(racine);
            }
            return;
        }
        if (methode != null && !id.isNull()) {
            repondreAuServeur(id, methode);
            return;
        }
        if ("notifications/tools/list_changed".equals(methode)
                && outilsChanges != null) {
            outilsChanges.run();
        }
    }

    /** {@code ping} reçoit sa réponse vide ; le reste est une capacité non déclarée (method-not-found). */
    private void repondreAuServeur(JsonValue id, String methode) {
        String corps = "ping".equals(methode)
                ? JsonEcrire.objet("jsonrpc", "2.0", "id",
                JsonEcrire.valeur(id),
                "result", JsonEcrire.objet())
                : JsonEcrire.objet("jsonrpc", "2.0", "id",
                        JsonEcrire.valeur(id),
                        "error", JsonEcrire.objet("code",
                                ProtocoleMcp.METHODE_INCONNUE,
                                "message", "JoIDE does not support "
                                        + methode));
        try {
            transport.envoyer(corps);
        } catch (Exception e) {
            // le serveur délais sa requête : rien d'autre à faire
        }
    }

    private void transportFerme(Throwable cause) {
        if (fermePar != null) {
            return;
        }
        fermePar = cause == null
                ? new ErreurMcp("The MCP server closed the connection.")
                : cause;
        for (CompletableFuture<JsonValue> attendue
                : enAttente.values()) {
            attendue.completeExceptionally(
                    new ErreurMcp("The MCP server disconnected: "
                            + fermePar.getMessage(), fermePar));
        }
        Runnable hote = null;
        if (!ferme && deconnecte != null) {
            hote = () -> deconnecte.accept(cause);
        }
        if (hote != null) {
            hote.run();
        }
    }

    /** La clé d'un identifiant reçu (nombre ou chaîne). */
    private static String cleDeId(JsonValue id) {
        if (id.type() == JsonValue.Type.NUMBER) {
            return id.asNumber();
        }
        if (id.type() == JsonValue.Type.STRING) {
            return id.asString();
        }
        return JsonEcrire.valeur(id);
    }

    private static String chaine(JsonValue valeur, String defaut) {
        String t = valeur.asString();
        return t == null ? defaut : t;
    }

    /**
     * Le {@code tools/call} aplati : les blocs texte joints, une
     * ressource qui contribue son texte (ou sa note binaire), un lien
     * de ressource et un audio DÉCRITS plutôt que perdus (le modèle
     * sait qu'ils existent), le contenu structuré employé seulement
     * s'il n'y a ni texte ni image, le tout BORNÉ.
     */
    static ResultatAppel aplatir(JsonValue resultat) {
        StringBuilder texte = new StringBuilder();
        List<String[]> images = new ArrayList<>();
        JsonValue blocs = resultat.member("content");
        if (blocs.type() == JsonValue.Type.ARRAY) {
            for (JsonValue bloc : blocs.items()) {
                if (bloc.type() != JsonValue.Type.OBJECT) {
                    continue;
                }
                String type = bloc.member("type").asString();
                if ("text".equals(type)) {
                    blocDeTexte(texte, bloc.member("text").asString());
                } else if ("image".equals(type)) {
                    String donnees = bloc.member("data").asString();
                    String mime = bloc.member("mimeType").asString();
                    if (mime == null) {
                        mime = "image/png";
                    }
                    if (donnees != null && IMAGES_SUPPORTEES.contains(mime)) {
                        images.add(new String[]{mime, donnees});
                    } else {
                        blocDeTexte(texte, "[image: " + mime + "]");
                    }
                } else if ("resource".equals(type)) {
                    JsonValue ressource = bloc.member("resource");
                    String corps = ressource.member("text").asString();
                    String uri = ressource.member("uri").asString();
                    blocDeTexte(texte, corps == null
                            ? "[binary resource: " + uri + "]"
                            : "<resource uri=\"" + uri + "\">\n" + corps
                            + "\n</resource>");
                } else if ("resource_link".equals(type)) {
                    String nom = bloc.member("name").asString();
                    String uri = bloc.member("uri").asString();
                    blocDeTexte(texte, "[resource: "
                            + (nom == null ? uri : nom) + " " + uri
                            + "]");
                } else if ("audio".equals(type)) {
                    String mime = bloc.member("mimeType").asString();
                    blocDeTexte(texte, "[audio: "
                            + (mime == null ? "audio" : mime) + "]");
                }
            }
        }
        if (texte.length() == 0 && images.isEmpty()) {
            JsonValue structure = resultat.member("structuredContent");
            if (!structure.isNull()) {
                texte.append(JsonEcrire.valeur(structure));
            }
        }
        boolean erreur = Boolean.TRUE.equals(
                resultat.member("isError").asBoolean());
        String sortie = texte.toString();
        if (sortie.length() > ProtocoleMcp.PLAFOND_RESULTAT) {
            sortie = sortie.substring(0, ProtocoleMcp.PLAFOND_RESULTAT)
                    + "\n… (cut at " + ProtocoleMcp.PLAFOND_RESULTAT
                    + " characters; ask the tool for less)";
        }
        if (sortie.isEmpty() && images.isEmpty()) {
            sortie = erreur ? "The tool failed without a message."
                    : "(no output)";
        }
        return new ResultatAppel(sortie, images, erreur);
    }

    private static void blocDeTexte(StringBuilder texte, String bloc) {
        if (texte.length() > 0) {
            texte.append("\n\n");
        }
        texte.append(bloc == null ? "" : bloc);
    }

    private static final List<String> IMAGES_SUPPORTEES = List.of(
            "image/png", "image/jpeg", "image/gif", "image/webp");

    @Override
    public void close() {
        if (ferme) {
            return;
        }
        ferme = true;
        for (CompletableFuture<JsonValue> attendue
                : enAttente.values()) {
            attendue.completeExceptionally(new ErreurMcp(
                    "The MCP connection was closed."));
        }
        transport.close();
    }
}
