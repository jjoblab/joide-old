package jo.joide.agent.mcp;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jo.joide.agent.TransportHttp;
import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonEcrire;
import jo.joide.deps.json.JsonValue;
import jo.joide.platform.CancelToken;

/**
 * Le transport Streamable HTTP de MCP (lot R, ADR-063) : chaque
 * message est un POST sur l'URL unique, {@code Accept: application/
 * json, text/event-stream} ; la réponse vient SUR le POST (corps
 * JSON ou flux d'événements qui se termine avec elle) ; la lecture du
 * flux s'arrête dès que la réponse à NOTRE identifiant est passée (le
 * serveur peut garder le flux ouvert après). L'identifiant de session
 * émis à l'initialize revient en en-tête {@code Mcp-Session-Id} à
 * chaque requête, et {@code MCP-Protocol-Version} après négociation.
 *
 * <p>PAS de flux GET permanent : l'agent n'a besoin que des réponses
 * à ses propres requêtes — un {@code tools/list_changed} envoyé en
 * cours de requête arrive sur le flux de cette requête. Un DELETE
 * best-effort dit au serveur que la session est finie.</p>
 *
 * <p>Le transport passe par le PORT {@link TransportHttp} (les mêmes
 * règles que les fournisseurs : les échecs hors 2xx remontent en
 * {@link ErreurMcp} classée, le corps d'erreur n'est jamais jeté) —
 * il se teste hors ligne par le faux transport, comme tout le module.</p>
 */
public final class TransportMcpFlux implements TransportMcp {

    private final String url;
    private final FabriqueEntetes entetes;
    private final TransportHttp transport;
    private final CancelToken jeton;

    /** Les en-têtes de chaque requête (demandés à chaque envoi : une signature rafraîchie sans reconnexion). */
    public interface FabriqueEntetes {
        /** @return les en-têtes de la requête */
        Map<String, String> entetes();
    }

    private volatile String sessionId;
    private volatile String version;
    private volatile TransportMcp.Remettre remettre =
            message -> {
            };

    /**
     * @param url       l'URL du point de terminaison MCP
     * @param entetes   la fabrique des en-têtes
     * @param transport le transport HTTP partagé
     * @param jeton     l'annulation (le flux se ferme)
     */
    public TransportMcpFlux(String url, FabriqueEntetes entetes,
                            TransportHttp transport,
                            CancelToken jeton) {
        this.url = url;
        this.entetes = entetes;
        this.transport = transport;
        this.jeton = jeton == null ? CancelToken.NEVER : jeton;
    }

    @Override
    public void demarrer(TransportMcp.Remettre remettre,
                         TransportMcp.OnFerme onFerme) {
        this.remettre = remettre;
        // les réponses reviennent sur les POST eux-mêmes : rien à
        // ouvrir en fond, la fermeture par lecteur n'existe pas ici
    }

    @Override
    public void envoyer(String message) throws IOException {
        boolean initialisation = estInitialisation(message);
        String idRequete = idDe(message);
        Map<String, String> entetesRequete = new LinkedHashMap<>(
                entetes.entetes());
        entetesRequete.put("accept",
                "application/json, text/event-stream");
        if (sessionId != null) {
            entetesRequete.put("Mcp-Session-Id", sessionId);
        }
        if (version != null) {
            entetesRequete.put("MCP-Protocol-Version", version);
        }
        entetesRequete.putIfAbsent("content-type",
                "application/json");
        TransportHttp.ReponseOuverte reponse;
        try {
            reponse = transport.postOuvert(url, entetesRequete,
                    message, jeton);
        } catch (TransportHttp.EchecHttp echec) {
            throw classeEchec(echec,
                    !initialisation && sessionId != null);
        }
        String sessionIdRecu = reponse.entete("Mcp-Session-Id");
        if (sessionIdRecu != null
                && (initialisation || sessionId == null)) {
            sessionId = sessionIdRecu;
        }
        String type = reponse.entete("content-type");
        String typeBas = type == null ? "" : type.toLowerCase();
        if (typeBas.contains("text/event-stream")) {
            lireFlux(reponse, idRequete);
        } else {
            lireCorps(reponse);
        }
    }

    /** Le flux d'événements : chaque trame remise, jusqu'à NOTRE réponse. */
    private void lireFlux(TransportHttp.ReponseOuverte reponse,
                          String idRequete) throws IOException {
        String evenement = "message";
        StringBuilder donnees = new StringBuilder();
        boolean aDonnees = false;
        String ligne;
        while ((ligne = reponse.ligne()) != null) {
            jeton.checkCancelled();
            if (ligne.isEmpty()) {
                if (aDonnees) {
                    String trame = donnees.toString();
                    boolean stoppe = idRequete != null
                            && porteLaReponse(trame, idRequete);
                    remettreEtAnalyser(trame);
                    if (stoppe) {
                        reponse.fermer();
                        return;
                    }
                }
                evenement = "message";
                donnees.setLength(0);
                aDonnees = false;
                continue;
            }
            if (ligne.startsWith(":")) {
                continue;
            }
            int deuxPoints = ligne.indexOf(':');
            String champ = deuxPoints < 0 ? ligne
                    : ligne.substring(0, deuxPoints);
            String valeur = deuxPoints < 0 ? ""
                    : ligne.substring(deuxPoints + 1);
            if (valeur.startsWith(" ")) {
                valeur = valeur.substring(1);
            }
            if ("event".equals(champ)) {
                evenement = valeur;
            } else if ("data".equals(champ)) {
                if (aDonnees) {
                    donnees.append('\n');
                }
                donnees.append(valeur);
                aDonnees = true;
            }
        }
        if (aDonnees) {
            remettreEtAnalyser(donnees.toString());
        }
    }

    /** Un corps JSON complet : un objet, ou un tableau de messages. */
    private void lireCorps(TransportHttp.ReponseOuverte reponse)
            throws IOException {
        StringBuilder corps = new StringBuilder();
        String ligne;
        while ((ligne = reponse.ligne()) != null) {
            corps.append(ligne).append('\n');
        }
        String texte = corps.toString().trim();
        if (texte.isEmpty()) {
            // 202 Accepted : une notification, pas de réponse
            return;
        }
        remettreEtAnalyser(texte);
    }

    /** Remet le message (objet seul ou tableau), en analysant les erreurs de transport. */
    private void remettreEtAnalyser(String texte) {
        Json.Document document = Json.parse(texte);
        JsonValue racine = document.root;
        if (racine == null || !document.problems.isEmpty()) {
            return;
        }
        if (racine.type() == JsonValue.Type.ARRAY) {
            for (JsonValue item : racine.items()) {
                if (item.type() == JsonValue.Type.OBJECT) {
                    remettre.message(JsonEcrire.valeur(item));
                }
            }
            return;
        }
        remettre.message(JsonEcrire.valeur(racine));
    }

    /** Le message porte-t-il la réponse à l'identifiant demandé ? */
    private static boolean porteLaReponse(String trame,
                                          String idRequete) {
        Json.Document document = Json.parse(trame);
        JsonValue racine = document.root;
        if (racine == null || racine.type() != JsonValue.Type.OBJECT) {
            return false;
        }
        return idRequete.equals(idDe(JsonEcrire.valeur(racine)))
                && racine.member("method").isNull();
    }

    /** L'erreur classée d'un échec HTTP (la session expirée d'abord)
     *  — jetée par les deux transports. */
    static ErreurMcp classeEchec(TransportHttp.EchecHttp echec,
                                 boolean sessionAttendue) {
        int statut = echec.statut;
        Map<String, java.util.List<String>> entetes = echec.entetes;
        String defi = valeur(entetes, "WWW-Authenticate");
        if (statut == 401) {
            return new ErreurMcp.AuthRequise(defi, null);
        }
        if (statut == 403 && defi != null
                && defi.contains("insufficient_scope")) {
            return new ErreurMcp.AuthRequise(defi,
                    "This server needs more permissions. Sign in "
                            + "again.");
        }
        if (statut == 404 && sessionAttendue) {
            return new ErreurMcp.SessionPerimee();
        }
        String detail = echec.corpsBrut == null ? ""
                : echec.corpsBrut.trim();
        if (detail.length() > 300) {
            detail = detail.substring(0, 300);
        }
        return new ErreurMcp.EchecHttp(statut,
                "The server answered HTTP " + statut
                        + (detail.isEmpty() ? "." : ": " + detail));
    }

    private static String valeur(Map<String, java.util.List<String>> entetes,
                                 String nom) {
        if (entetes == null) {
            return null;
        }
        for (Map.Entry<String, java.util.List<String>> entree
                : entetes.entrySet()) {
            if (nom.equalsIgnoreCase(entree.getKey())
                    && !entree.getValue().isEmpty()) {
                return entree.getValue().get(0);
            }
        }
        return null;
    }

    private static boolean estInitialisation(String message) {
        Json.Document document = Json.parse(message);
        JsonValue racine = document.root;
        return racine != null
                && "initialize".equals(racine.member("method")
                .asString());
    }

    /** L'identifiant de la requête envoyée (pour repérer notre réponse dans le flux). */
    static String idDe(String message) {
        Json.Document document = Json.parse(message);
        JsonValue racine = document.root;
        if (racine == null || racine.type() != JsonValue.Type.OBJECT) {
            return null;
        }
        JsonValue id = racine.member("id");
        if (id.isNull() || racine.member("method").isNull()) {
            return null;
        }
        if (id.type() == JsonValue.Type.NUMBER
                || id.type() == JsonValue.Type.STRING) {
            return id.asNumber() != null ? id.asNumber()
                    : id.asString();
        }
        return null;
    }

    @Override
    public void negocie(String versionProtocole) {
        this.version = versionProtocole;
    }

    @Override
    public void close() {
        String id = sessionId;
        sessionId = null;
        if (id == null) {
            return;
        }
        // best-effort, hors du fil appelant : un serveur qui garde
        // ses sessions est prévenu que celle-ci est finie
        Thread fermant = new Thread(() -> {
            try {
                Map<String, String> entetesRequete =
                        new LinkedHashMap<>(entetes.entetes());
                entetesRequete.put("Mcp-Session-Id", id);
                transport.supprimer(url, entetesRequete, jeton);
            } catch (Exception e) {
                // la fermeture ne jette jamais
            }
        }, "mcp-fermeture-session");
        fermant.setDaemon(true);
        fermant.start();
    }
}
