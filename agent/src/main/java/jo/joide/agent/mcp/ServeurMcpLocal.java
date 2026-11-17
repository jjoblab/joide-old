package jo.joide.agent.mcp;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonEcrire;
import jo.joide.deps.json.JsonValue;
import jo.joide.platform.CancelToken;
import jo.joide.platform.WorkerPools;

/**
 * Le serveur MCP local de JoIDE (lot R, ADR-063) : les outils de
 * l'espace de travail exposés au protocole MCP sur un serveur
 * HTTP/1.1 maison sans conteneur — un {@link ServerSocket} lié au
 * BOUCLAGE UNIQUEMENT (non configurable par intention : le transport
 * n'authentifie personne et ses outils éditent le projet ouvert, le
 * port ne doit pas être atteignable hors de l'appareil).
 *
 * <p>La sécurité de transport :</p>
 * <ul>
 *   <li>la vérification LOCALE couvre le cas que la liaison ne couvre
 *       pas — un navigateur braqué sur le port par un nom d'hôte
 *       rebondi (DNS rebinding) : l'en-tête {@code Host} doit être
 *       local et un {@code Origin} PRÉSENT doit être local (un client
 *       natif n'en envoie pas : absent = accepté, présent et étranger
 *       = refusé 403) ;</li>
 *   <li>le corps est plafonné (16 Mio — un Content-Length mensonger
 *       ne doit pas allouer le processus jusqu'à l'OOM) et la socket a
 *       30 s de délai (un pair qui se connecte puis bloque ne cloue
 *       pas un fil) ;</li>
 *   <li>la lecture d'en-tête est OCTET PAR OCTET en ISO-8859-1 et le
 *       corps prend EXACTEMENT {@code Content-Length} octets : ce
 *       compteur compte des OCTETS, et décoder d'abord laissait tout
 *       corps à caractère non ASCII court de la différence (la leçon
 *       de l'ADR-É9 — un emoji dans un edit bloquait la lecture
 *       jusqu'au délai de socket).</li>
 * </ul>
 *
 * <p>Sessions créées au premier {@code initialize} et routées par
 * l'en-tête {@code Mcp-Session-Id} ; réponses en corps JSON-RPC sur
 * le POST lui-même (mode requête-réponse) ; le GET reçoit 405 (les
 * clients SDK replient) ; le DELETE ferme la session. Les JOBS LONGS
 * sont découplés : au-delà du seuil, le travail CONTINUE en fond et
 * le client reçoit « toujours en cours — appelez {@code task_status},
 * ne le relancez pas » (un premier build qui dépassait le délai d'un
 * client mourait avec son résultat).</p>
 */
public final class ServeurMcpLocal implements AutoCloseable {

    /** La source d'outils exposés (l'hôte y met ses OutilAgent). */
    public interface OutilsServeur {
        /** @return les outils proposés */
        List<jo.joide.agent.OutilAgent> outils();
    }

    private final OutilsServeur outils;
    private final long seuilJobLongMs;
    private final Map<String, Boolean> sessions = new ConcurrentHashMap<>();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final java.util.concurrent.ExecutorService executeurJobs =
            WorkerPools.newFixed("mcp-jobs", 2);
    private ServerSocket socket;
    private Thread accepteur;
    private final Object verrou = new Object();

    /** Un job long en cours. */
    private static final class Job {
        volatile String resultat;
        volatile boolean fini;
        volatile String erreur;
    }

    /** @param outils la source d'outils @param seuilJobLongMs le seuil des jobs longs (les tests raccourcissent) */
    public ServeurMcpLocal(OutilsServeur outils, long seuilJobLongMs) {
        this.outils = outils;
        this.seuilJobLongMs = seuilJobLongMs;
    }

    /** Le serveur au seuil standard (90 s). */
    public ServeurMcpLocal(OutilsServeur outils) {
        this(outils, ProtocoleMcp.SEUIL_JOB_LONG_MS);
    }

    /**
     * Lie le port (0 : éphémère) et démarre l'accepteur.
     *
     * @param port le port demandé
     * @return le port lié
     * @throws IOException si la liaison échoue
     */
    public int lier(int port) throws IOException {
        synchronized (verrou) {
            if (socket != null) {
                throw new IllegalStateException(
                        "already bound to port " + portLie());
            }
            ServerSocket cree = new ServerSocket();
            cree.setReuseAddress(true);
            // le bouclage SEUL : jamais une interface externe
            cree.bind(new InetSocketAddress("127.0.0.1", port));
            socket = cree;
            accepteur = new Thread(this::accepter,
                    "mcp-http-accepteur");
            accepteur.setDaemon(true);
            accepteur.start();
            return cree.getLocalPort();
        }
    }

    /** @return le port lié, ou -1 */
    public int portLie() {
        ServerSocket courante = socket;
        return courante == null ? -1 : courante.getLocalPort();
    }

    private void accepter() {
        while (true) {
            ServerSocket courante;
            synchronized (verrou) {
                courante = socket;
            }
            if (courante == null || courante.isClosed()) {
                return;
            }
            Socket client;
            try {
                client = courante.accept();
            } catch (SocketException e) {
                return;
            } catch (IOException e) {
                return;
            }
            Thread servante = new Thread(() -> servir(client),
                    "mcp-http-conn");
            servante.setDaemon(true);
            servante.start();
        }
    }

    private void servir(Socket client) {
        try {
            client.setSoTimeout(30_000);
            Requete requete = lireRequete(client.getInputStream());
            Reponse reponse;
            if (!estLocal(requete)) {
                reponse = new Reponse(403, Map.of(), "Forbidden");
            } else if ("POST".equals(requete.methode)) {
                reponse = poster(requete.corps,
                        requete.entete("mcp-session-id"));
            } else if ("DELETE".equals(requete.methode)) {
                reponse = supprimerSession(
                        requete.entete("mcp-session-id"));
            } else {
                reponse = new Reponse(405, Map.of(),
                        "Method not allowed");
            }
            ecrire(client.getOutputStream(), reponse);
        } catch (Exception e) {
            // client parti, délai dépassé : rien à envoyer
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                // la fermeture ne jette jamais
            }
        }
    }

    /**
     * La demande est-elle locale ? La liaison bouclage couvre le
     * réseau ; un Origin PRÉSENT et étranger est un DNS rebinding
     * (refus), un Origin ABSENT est un client natif (accepté).
     */
    static boolean estLocal(Requete requete) {
        String hote = requete.entete("host");
        if (hote != null) {
            String net;
            if (hote.startsWith("[")) {
                int crochet = hote.indexOf(']');
                net = crochet > 0 ? hote.substring(1, crochet) : "";
            } else {
                int deuxPoints = hote.lastIndexOf(':');
                net = deuxPoints > 0 ? hote.substring(0, deuxPoints)
                        : hote;
            }
            if (!LOCAUX.contains(net)) {
                return false;
            }
        }
        String origine = requete.entete("origin");
        if (origine == null) {
            return true;
        }
        try {
            URI local = URI.create(origine);
            String hoteOrigine = local.getHost();
            if (hoteOrigine == null) {
                return false;
            }
            return LOCAUX.contains(hoteOrigine.trim()
                    .replace("[", "").replace("]", ""));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** La requête POST : l'initialize crée la session, le reste est routé par l'en-tête. */
    Reponse poster(String corps, String sessionIdEntete) {
        Json.Document document = Json.parse(corps == null ? ""
                : corps);
        JsonValue racine = document.root;
        if (racine == null || !document.problems.isEmpty()
                || racine.type() != JsonValue.Type.OBJECT) {
            return erreurJsonRpc(400,
                    "Invalid message format: the body is not "
                            + "JSON-RPC");
        }
        String methode = racine.member("method").asString();
        JsonValue id = racine.member("id");
        if (methode == null) {
            return erreurJsonRpc(400,
                    "Invalid message format: no method");
        }
        if ("initialize".equals(methode)) {
            return initialiser(id);
        }
        if (id.isNull()) {
            // une notification : acceptée silencieusement
            return new Reponse(202, Map.of(), "");
        }
        String session = sessionIdEntete == null ? "" : sessionIdEntete
                .trim();
        if (session.isEmpty()) {
            return erreurJsonRpc(404,
                    "Session ID required in mcp-session-id header");
        }
        if (!sessions.containsKey(session)) {
            return erreurJsonRpc(400,
                    "Session not found: " + session);
        }
        JsonValue parametres = racine.member("params");
        switch (methode) {
            case "tools/list" -> {
                List<Map<String, Object>> liste = new ArrayList<>();
                for (jo.joide.agent.OutilAgent outil : outils
                        .outils()) {
                    jo.joide.agent.DescOutil desc = outil.description();
                    Map<String, Object> entree = new LinkedHashMap<>();
                    entree.put("name", desc.nom);
                    entree.put("description", desc.description);
                    entree.put("inputSchema", desc.schema);
                    Map<String, Object> annotations =
                            new LinkedHashMap<>();
                    annotations.put("readOnlyHint", !desc.mutant);
                    entree.put("annotations", annotations);
                    liste.add(entree);
                }
                // la pseudo-outil des jobs longs : « appelez
                // task_status, ne relancez pas » (ADR-063)
                Map<String, Object> taskStatus = new LinkedHashMap<>();
                taskStatus.put("name", "task_status");
                taskStatus.put("description",
                        "Poll the result of a long-running task "
                                + "instead of starting it again.");
                taskStatus.put("inputSchema", Json.parse(
                        "{\"type\":\"object\","
                                + "\"properties\":{\"id\":"
                                + "{\"type\":\"string\"}},"
                                + "\"required\":[\"id\"]}").root);
                Map<String, Object> annotationsStatus =
                        new LinkedHashMap<>();
                annotationsStatus.put("readOnlyHint", true);
                taskStatus.put("annotations", annotationsStatus);
                liste.add(taskStatus);
                return reponse(id, Json.parse(JsonEcrire.objet(
                        "tools", liste)).root);
            }
            case "tools/call" -> {
                return appelerOutil(id, parametres);
            }
            case "task_status" -> {
                String job = parametres.member("id").asString();
                if (job == null) {
                    job = parametres.member("arguments")
                            .member("id").asString();
                }
                return taskStatus(id, job);
            }
            case "ping" -> {
                return reponse(id, Json.parse("{}").root);
            }
            default -> {
                return erreurJsonRpc(ProtocoleMcp.METHODE_INCONNUE,
                        "JoIDE does not support " + methode, id);
            }
        }
    }

    /** L'initialize : une session nouvelle, le protocole fixé. */
    private Reponse initialiser(JsonValue id) {
        String session = "mcp-" + java.util.UUID.randomUUID();
        sessions.put(session, Boolean.TRUE);
        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("protocolVersion", ProtocoleMcp.VERSION_PROTOCOLE);
        resultat.put("capabilities", Map.of("tools", Map.of()));
        resultat.put("serverInfo", Map.of("name", "JoIDE",
                "version", "1.0"));
        Map<String, String> entetes = Map.of("Mcp-Session-Id", session);
        return new Reponse(200, entetes,
                JsonEcrire.objet("jsonrpc", "2.0", "id", id, "result",
                        Json.parse(JsonEcrire.valeur(resultat)).root));
    }

    /** L'appel d'outil : borné par l'exécuteur, les longs partent en task_status. */
    private Reponse appelerOutil(JsonValue id, JsonValue parametres) {
        String nom = parametres.member("name").asString();
        JsonValue arguments = parametres.member("arguments");
        if (nom == null) {
            return erreurJsonRpc(400, "tools/call without a name", id);
        }
        if ("task_status".equals(nom)) {
            String job = arguments.member("id").asString();
            return taskStatus(id, job);
        }
        jo.joide.agent.OutilAgent cible = null;
        for (jo.joide.agent.OutilAgent outil : outils.outils()) {
            if (outil.description().nom.equals(nom)) {
                cible = outil;
                break;
            }
        }
        if (cible == null) {
            return reponse(id, Json.parse(JsonEcrire.objet("content",
                    List.of(Map.of("type", "text", "text",
                            "unknown tool: " + nom)),
                    "isError", true)).root);
        }
        JsonValue argumentsNet = arguments.isNull()
                ? Json.parse("{}").root : arguments;
        // l'exécution tourne sur un pool borné : l'appelant n'attend
        // jamais plus que le seuil
        jo.joide.agent.OutilAgent outilCible = cible;
        java.util.concurrent.Future<String> travail =
                executeurJobs.submit(() -> outilCible.invoquer(
                        argumentsNet, CancelToken.NEVER));
        String resultat;
        boolean erreur;
        try {
            resultat = travail.get(seuilJobLongMs,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            erreur = false;
        } catch (java.util.concurrent.TimeoutException e) {
            // le travail CONTINUE : le client reçoit task_status et
            // NE RELANCE PAS (un premier build qui dépassait le délai
            // d'un client mourait avec son résultat)
            String job = "task-" + java.util.UUID.randomUUID();
            Job enCours = new Job();
            jobs.put(job, enCours);
            executeurJobs.submit(() -> {
                try {
                    String rendu = travail.get(300,
                            java.util.concurrent.TimeUnit.SECONDS);
                    enCours.resultat = rendu;
                } catch (Exception echec) {
                    enCours.erreur = String.valueOf(echec
                            .getMessage());
                } finally {
                    enCours.fini = true;
                }
            });
            Map<String, Object> contenu = new LinkedHashMap<>();
            contenu.put("type", "text");
            contenu.put("text", "This task is still running (over "
                    + seuilJobLongMs / 1000 + " s). Do NOT start it "
                    + "again: call the task_status tool with id "
                    + job + " to poll its result.");
            return reponse(id, Json.parse(JsonEcrire.objet("content",
                    List.of(contenu))).root);
        } catch (Exception e) {
            resultat = "tool failed: " + e.getMessage();
            erreur = true;
        }
        return reponse(id, Json.parse(JsonEcrire.objet("content",
                List.of(Map.of("type", "text", "text", resultat)),
                "isError", erreur)).root);
    }

    /** Le DELETE : la session est finie. */
    private Reponse supprimerSession(String sessionIdEntete) {
        String session = sessionIdEntete == null ? "" : sessionIdEntete
                .trim();
        if (session.isEmpty()) {
            return erreurJsonRpc(404,
                    "Session ID required in mcp-session-id header");
        }
        if (sessions.remove(session) == null) {
            return erreurJsonRpc(400,
                    "Session not found: " + session);
        }
        return new Reponse(200, Map.of(), "");
    }

    /** task_status : consulte un job découplé (le serveur l'offre comme outil). */
    private Reponse taskStatus(JsonValue id, String job) {
        Job enCours = jobs.get(job);
        if (enCours == null) {
            return reponse(id, Json.parse(JsonEcrire.objet("content",
                    List.of(Map.of("type", "text", "text",
                            "no such task: " + job)),
                    "isError", true)).root);
        }
        if (!enCours.fini) {
            return reponse(id, Json.parse(JsonEcrire.objet("content",
                    List.of(Map.of("type", "text", "text",
                            "still running — do not re-run the "
                                    + "original task, call "
                                    + "task_status again.")))).root);
        }
        String rendu = enCours.erreur != null
                ? enCours.erreur : enCours.resultat;
        return reponse(id, Json.parse(JsonEcrire.objet("content",
                List.of(Map.of("type", "text", "text",
                        rendu == null ? "(no output)" : rendu)),
                "isError", enCours.erreur != null)).root);
    }

    /** Une réponse JSON-RPC de succès. */
    private static Reponse reponse(JsonValue id, JsonValue resultat) {
        return new Reponse(200, Map.of(), JsonEcrire.objet("jsonrpc",
                "2.0", "id", id, "result", resultat));
    }

    /** Une erreur de transport en enveloppe JSON-RPC (id null écrit à la main). */
    private static Reponse erreurJsonRpc(int statut, String message) {
        return erreurJsonRpc(statut, message, null);
    }

    private static Reponse erreurJsonRpc(int statut, String message,
                                         JsonValue id) {
        String echappe = message.replace("\\", "\\\\")
                .replace("\"", "\\\"");
        String corps = "{\"jsonrpc\":\"2.0\",\"id\":"
                + (id == null || id.isNull() ? "null"
                : JsonEcrire.valeur(id))
                + ",\"error\":{\"code\":"
                + ProtocoleMcp.METHODE_INCONNUE
                + ",\"message\":\"" + echappe + "\"}}";
        return new Reponse(statut, Map.of(), corps);
    }

    /** La lecture d'octets exacte : l'en-tête en ISO-8859-1, le corps de Content-Length octets. */
    static Requete lireRequete(InputStream entree) throws IOException {
        BufferedInputStream tampon = new BufferedInputStream(entree);
        String ligneDemande = lireLigneEntete(tampon);
        if (ligneDemande == null) {
            throw new IOException("empty request");
        }
        String[] morceaux = ligneDemande.split(" ");
        String methode = morceaux.length > 0 ? morceaux[0] : "";
        String chemin = morceaux.length > 1 ? morceaux[1] : "/";
        Map<String, String> entetes = new LinkedHashMap<>();
        while (true) {
            String ligne = lireLigneEntete(tampon);
            if (ligne == null || ligne.isEmpty()) {
                break;
            }
            int deuxPoints = ligne.indexOf(':');
            if (deuxPoints > 0) {
                entetes.put(ligne.substring(0, deuxPoints).trim()
                        .toLowerCase(),
                        ligne.substring(deuxPoints + 1).trim());
            }
        }
        int longueur = 0;
        String longueurs = entetes.get("content-length");
        if (longueurs != null) {
            try {
                longueur = Integer.parseInt(longueurs.trim());
            } catch (NumberFormatException e) {
                longueur = 0;
            }
        }
        if (longueur > ProtocoleMcp.PLAFOND_CORPS_OCTETS) {
            throw new IOException("payload too large");
        }
        byte[] corps = new byte[longueur];
        int lus = 0;
        while (lus < longueur) {
            int n = tampon.read(corps, lus, longueur - lus);
            if (n < 0) {
                break;
            }
            lus += n;
        }
        return new Requete(methode, chemin, entetes, new String(corps,
                0, lus, StandardCharsets.UTF_8));
    }

    /** Une ligne d'en-tête terminée par CRLF (ou LF seul), en ISO-8859-1. */
    private static String lireLigneEntete(InputStream entree)
            throws IOException {
        StringBuilder ligne = new StringBuilder();
        while (true) {
            int b = entree.read();
            if (b < 0) {
                return ligne.length() == 0 ? null : ligne.toString();
            }
            if (b == '\n') {
                String rendu = ligne.toString();
                return rendu.endsWith("\r") ? rendu.substring(0,
                        rendu.length() - 1) : rendu;
            }
            ligne.append((char) b);
        }
    }

    private static void ecrire(OutputStream sortie, Reponse reponse)
            throws IOException {
        byte[] corps = reponse.corps
                .getBytes(StandardCharsets.UTF_8);
        String entetes = new StringBuilder()
                .append("HTTP/1.1 ").append(reponse.statut)
                .append(' ').append(raison(reponse.statut))
                .append("\r\n")
                .append("Connection: close\r\n")
                .append("Content-Type: application/json\r\n")
                .append("Content-Length: ").append(corps.length)
                .append("\r\n")
                .toString();
        sortie.write(entetes.getBytes(StandardCharsets.ISO_8859_1));
        for (Map.Entry<String, String> entree : reponse.entetes
                .entrySet()) {
            sortie.write((entree.getKey() + ": " + entree.getValue()
                    + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        }
        sortie.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
        sortie.write(corps);
        sortie.flush();
    }

    private static String raison(int statut) {
        return switch (statut) {
            case 200 -> "OK";
            case 202 -> "Accepted";
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 413 -> "Payload Too Large";
            case 500 -> "Internal Server Error";
            default -> "Error";
        };
    }

    /** Une requête lue (les en-têtes en bas de casse) — une classe, pas un record : l'API 28 n'a pas de records (ADR-009). */
    static final class Requete {

        /** La méthode HTTP. */
        final String methode;
        /** Le chemin demandé. */
        final String chemin;
        /** Les en-têtes en bas de casse. */
        final Map<String, String> entetes;
        /** Le corps (décodé après lecture d'octets exacte). */
        final String corps;

        Requete(String methode, String chemin,
                Map<String, String> entetes, String corps) {
            this.methode = methode;
            this.chemin = chemin;
            this.entetes = entetes;
            this.corps = corps;
        }

        String entete(String nom) {
            return entetes.get(nom);
        }
    }

    /** Une réponse à écrire — une classe, pas un record (ADR-009). */
    static final class Reponse {

        /** Le code HTTP. */
        final int statut;
        /** Les en-têtes additionnels (la session). */
        final Map<String, String> entetes;
        /** Le corps. */
        final String corps;

        Reponse(int statut, Map<String, String> entetes,
                String corps) {
            this.statut = statut;
            this.entetes = entetes;
            this.corps = corps;
        }
    }

    /** Les hôtes qu'une demande peut prétendre être et rester locale. */
    static final List<String> LOCAUX = List.of("127.0.0.1", "localhost",
            "::1", "0:0:0:0:0:0:0:1");

    @Override
    public void close() {
        synchronized (verrou) {
            try {
                ServerSocket courante = socket;
                socket = null;
                if (courante != null) {
                    courante.close();
                }
            } catch (IOException e) {
                // la fermeture ne jette jamais
            }
        }
        executeurJobs.shutdownNow();
        sessions.clear();
        jobs.clear();
    }
}
