package jo.joide.agent.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jo.joide.agent.OutilAgent;
import jo.joide.agent.TransportHttp;
import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonValue;
import jo.joide.platform.CancelToken;
import jo.joide.platform.WorkerPools;

/**
 * Les connexions vivantes aux serveurs MCP configurés, et leurs
 * outils vus comme des {@link OutilAgent} (lot R, ADR-063).
 *
 * <p>{@link #maj} reçoit l'ensemble ENTIER voulu : les serveurs dont
 * la config est inchangée gardent leur connexion, les autres ferment
 * ou ouvrent. La connexion va EN ARRIÈRE-PLAN — un serveur lent ou
 * mort ne retient jamais le chat, ses outils apparaissent quand il
 * répond. Un appel sur une connexion tombée reconnecte UNE fois ; une
 * signature expirée se rafraîchit UNE fois ; un serveur qui refuse
 * encore est marqué auth requise.</p>
 *
 * <p>LA RÈGLE DE SÉCURITÉ CENTRALE : un appel n'est retenté que si le
 * serveur a PROVÉ qu'il n'a pas exécuté l'outil (session expirée,
 * identifiants refusés avant examen) — une rupture EN COURS d'appel
 * répond honnêtement « il a pu s'exécuter ou non » et un outil mutant
 * n'est JAMAIS lancé deux fois.</p>
 */
public final class GestionnaireMcp implements AutoCloseable {

    /** L'état d'un serveur tel que l'UI le montre. */
    public enum Statut {
        /** L'entrée est désactivée dans le fichier. */
        DESACTIVE,
        /** La connexion se tente en arrière-plan. */
        CONNEXION,
        /** Connecté : les outils sont listés. */
        CONNECTE,
        /** Le serveur exige une signature de l'utilisateur. */
        AUTH_REQUISE,
        /** Non connecté (échec de transport). */
        ECHEC,
        /** Le transport n'existe pas ici (stdio sur Android). */
        NON_SUPPORTE
    }

    /** L'état publié d'un serveur. */
    public static final class EtatServeur {

        /** Le nom configuré. */
        public final String nom;
        /** Le statut. */
        public final Statut statut;
        /** Le motif d'échec ou de signature demandée, ou null. */
        public final String motif;
        /** Vrai si une signature est stockée pour cette identité. */
        public final boolean signe;
        /** Le nom de serveur annoncé par l'initialize, ou null. */
        public final String nomAnnonce;

        EtatServeur(String nom, Statut statut, String motif,
                    boolean signe, String nomAnnonce) {
            this.nom = nom;
            this.statut = statut;
            this.motif = motif;
            this.signe = signe;
            this.nomAnnonce = nomAnnonce;
        }

        @Override
        public String toString() {
            return "EtatServeur[" + nom + "/" + statut
                    + (motif == null ? "" : "/" + motif) + "]";
        }
    }

    /** Une connexion vivante et son état. */
    private static final class Vivante {
        final ConfigServeurMcp config;
        final Object verrou = new Object();
        volatile ClientMcp client;
        volatile List<ClientMcp.OutilInfo> outils = List.of();
        volatile String defiAuth;
        volatile Statut statut = Statut.CONNEXION;
        volatile String motif;

        Vivante(ConfigServeurMcp config) {
            this.config = config;
        }
    }

    /** La fabrique des transports (les tests injectent les scripts ; la production, les transports réels). */
    public interface FabriqueTransport {
        /** @param config la configuration du serveur @return le transport sous le client */
        TransportMcp transport(ConfigServeurMcp config);
    }

    private final Map<String, Vivante> serveurs = new LinkedHashMap<>();
    private final StoreJetonsMcp jetons;
    private final TransportHttp transport;
    private final CancelToken jeton;
    private final boolean stdioAutorise;
    private final List<String> journal;
    private final java.util.concurrent.ExecutorService connexions;
    private final AtomicInteger versionOutils = new AtomicInteger();
    private final Set<String> nomsOutilsPris =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final FabriqueTransport fabriqueTransport;

    /**
     * @param jetons        le store des signatures (chiffré côté hôte)
     * @param transport     le transport HTTP partagé
     * @param jeton         l'annulation partagée
     * @param stdioAutorise vrai si les serveurs locaux peuvent tourner
     *                      (bureau ; Android : NON_SUPPORTE honnête)
     * @param journal       le journal de diagnostic (nullable)
     */
    public GestionnaireMcp(StoreJetonsMcp jetons,
                           TransportHttp transport,
                           CancelToken jeton, boolean stdioAutorise,
                           List<String> journal) {
        this(jetons, transport, jeton, stdioAutorise, journal,
                null);
    }

    /** @param fabriqueTransport la fabrique des transports (null : les transports réels) */
    public GestionnaireMcp(StoreJetonsMcp jetons,
                           TransportHttp transport,
                           CancelToken jeton, boolean stdioAutorise,
                           List<String> journal,
                           FabriqueTransport fabriqueTransport) {
        this.jetons = jetons;
        this.transport = transport;
        this.jeton = jeton;
        this.stdioAutorise = stdioAutorise;
        this.journal = journal;
        this.fabriqueTransport = fabriqueTransport;
        this.connexions = WorkerPools.newFixed("mcp-connexions", 2);
    }

    /**
     * Remplace l'ensemble des serveurs par exactement [configs] :
     * inchangés gardés, les autres fermés ou ouverts ; l'ordre
     * configuré est conservé.
     *
     * @param configs les serveurs voulus (noms uniques)
     */
    public synchronized void maj(
            List<ConfigServeurMcp> configs) {
        Map<String, ConfigServeurMcp> voulus = new LinkedHashMap<>();
        for (ConfigServeurMcp config : configs) {
            voulus.put(config.nom, config);
        }
        List<String> aRetirer = new ArrayList<>();
        for (Map.Entry<String, Vivante> entree : serveurs.entrySet()) {
            ConfigServeurMcp suivant = voulus.get(entree.getKey());
            Vivante vivante = entree.getValue();
            if (suivant == null || !suivant.identite()
                    .equals(vivante.config.identite())
                    || suivant.desactive != vivante.config.desactive
                    || (suivant.transport
                    == ConfigServeurMcp.Transport.STDIO)) {
                fermerVivante(vivante);
                aRetirer.add(entree.getKey());
            }
        }
        for (String nom : aRetirer) {
            serveurs.remove(nom);
        }
        for (ConfigServeurMcp config : configs) {
            if (serveurs.containsKey(config.nom)) {
                continue;
            }
            Vivante vivante = new Vivante(config);
            serveurs.put(config.nom, vivante);
            connecterFond(vivante);
        }
        // l'ordre configuré
        Map<String, Vivante> ordonnes = new LinkedHashMap<>();
        for (ConfigServeurMcp config : configs) {
            Vivante v = serveurs.get(config.nom);
            if (v != null) {
                ordonnes.put(config.nom, v);
            }
        }
        serveurs.clear();
        serveurs.putAll(ordonnes);
        nomsOutilsPris.clear();
    }

    /** Reconnecte un serveur nommé (après une signature, un échec). */
    public void reconnecter(String nom) {
        Vivante vivante = serveurs.get(nom);
        if (vivante == null) {
            return;
        }
        fermerVivante(vivante);
        vivante.client = null;
        vivante.outils = List.of();
        connecterFond(vivante);
        versionOutils.incrementAndGet();
    }

    /** Tous les outils des serveurs connectés, nommés sans collision. */
    public synchronized List<OutilAgent> outils() {
        List<OutilAgent> sortie = new ArrayList<>();
        for (Vivante vivante : serveurs.values()) {
            if (vivante.client == null
                    || vivante.statut != Statut.CONNECTE) {
                continue;
            }
            for (ClientMcp.OutilInfo info : vivante.outils) {
                String nom = OutilMcpDistant.nomOutil(
                        vivante.config.nom, info.nom, nomsOutilsPris);
                nomsOutilsPris.add(nom);
                sortie.add(new OutilMcpDistant(vivante.config.nom,
                        info, nom, (nomServeur, outil, arguments) ->
                        appeler(nomServeur, outil, arguments)));
            }
        }
        return sortie;
    }

    /** La version d'outils : incrémentée à chaque changement de la surface (le cache du hôte l'observe). */
    public int versionOutils() {
        return versionOutils.get();
    }

    /** Les instructions d'usage des serveurs connectés (pour le prompt système). */
    public synchronized List<String> instructions() {
        List<String> sortie = new ArrayList<>();
        for (Vivante vivante : serveurs.values()) {
            String note = vivante.client == null ? null
                    : vivante.client.instructions;
            if (note != null) {
                sortie.add(vivante.config.nom + ": " + note);
            }
        }
        return sortie;
    }

    /** Les états publiés (l'UI lit, elle ne déduit rien). */
    public synchronized List<EtatServeur> etats() {
        List<EtatServeur> sortie = new ArrayList<>();
        for (Vivante vivante : serveurs.values()) {
            sortie.add(new EtatServeur(vivante.config.nom,
                    vivante.statut, vivante.motif,
                    jetons != null && jetons.lire(vivante.config
                            .identite()) != null,
                    vivante.client == null ? null
                            : vivante.client.nomServeur));
        }
        return sortie;
    }

    /** Une signature stockée pour l'identité du serveur (le début de connexion la pose). */
    public void poserJetons(String identite, JetonsMcp valeurs) {
        if (jetons != null) {
            jetons.poser(identite, valeurs);
        }
    }

    /** Le défi d'auth du dernier 401 d'un serveur nommé (le flux de signature en a besoin). */
    public String defiAuth(String nom) {
        Vivante vivante = serveurs.get(nom);
        return vivante == null ? null : vivante.defiAuth;
    }

    /** L'identité d'un serveur nommé (pour lier signature et approbation). */
    public String identite(String nom) {
        Vivante vivante = serveurs.get(nom);
        return vivante == null ? null : vivante.config.identite();
    }

    /**
     * Appelle un outil distant — LA règle : on ne retente que ce qui
     * est prouvé non exécuté ; tout le reste échoue honnêtement une
     * fois, sans jamais relancer un mutant.
     */
    ClientMcp.ResultatAppel appeler(String nomServeur, String outil,
                                    JsonValue arguments) {
        Vivante vivante = serveurs.get(nomServeur);
        if (vivante == null) {
            throw new ErreurMcp("The MCP server \"" + nomServeur
                    + "\" is no longer configured.");
        }
        for (int essai = 0; essai < 2; essai++) {
            ClientMcp client = vivante.client;
            if (client == null) {
                connecterMaintenant(vivante);
                client = vivante.client;
                if (client == null) {
                    throw new ErreurMcp("The MCP server \""
                            + nomServeur + "\" is not connected"
                            + (vivante.motif == null ? "."
                            : ": " + vivante.motif));
                }
            }
            try {
                return client.appelerOutil(outil, arguments);
            } catch (ErreurMcp.SessionPerimee e) {
                // PRUVÉ non exécuté (le serveur a oublié la session) :
                // une reconnexion, puis l'appel repart
                fermerVivante(vivante);
                if (essai == 0) {
                    continue;
                }
                throw new ErreurMcp("The MCP server \"" + nomServeur
                        + "\" keeps dropping its session.");
            } catch (ErreurMcp.AuthRequise e) {
                vivante.defiAuth = e.defi;
                fermerVivante(vivante);
                // les identifiants refusés AVANT examen : le refresh
                // se tente une fois, puis l'utilisateur signe
                if (essai == 0 && rafraichir(vivante)) {
                    continue;
                }
                vivante.statut = Statut.AUTH_REQUISE;
                vivante.motif = "Sign in to " + nomServeur
                        + " to use its tools.";
                versionOutils.incrementAndGet();
                throw new ErreurMcp("The MCP server \"" + nomServeur
                        + "\" needs the user to sign in. Tell the "
                        + "user to open Connections and sign in to "
                        + "it.");
            } catch (ErreurMcp.RefusServeur | ErreurMcp.DelaiDepasse e) {
                // l'outil a répondu (ou le serveur a refusé l'appel
                // lui-même) : PAS de retente
                throw e;
            } catch (ErreurMcp e) {
                // une rupture EN COURS d'appel : l'outil a pu
                // s'exécuter ou non — jamais relancé
                fermerVivante(vivante);
                throw new ErreurMcp("The connection to the MCP server "
                        + "\"" + nomServeur + "\" broke during the "
                        + "call (" + e.getMessage() + "). It may or "
                        + "may not have run.");
            }
        }
        throw new ErreurMcp("The MCP server \"" + nomServeur
                + "\" could not be reached.");
    }

    /** Le refresh d'une signature expirée, une fois. */
    private boolean rafraichir(Vivante vivante) {
        if (jetons == null) {
            return false;
        }
        JetonsMcp courants = jetons.lire(vivante.config.identite());
        if (courants == null || courants.refresh == null) {
            return false;
        }
        try {
            JetonsMcp renouves = AutorisationMcp.rafraichir(courants,
                    transport, jeton);
            jetons.poser(vivante.config.identite(), renouves);
            journaliser(vivante.config.nom + ": tokens refreshed");
            return true;
        } catch (Exception e) {
            journaliser(vivante.config.nom
                    + ": token refresh failed: " + e.getMessage());
            return false;
        }
    }

    private void connecterFond(Vivante vivante) {
        connexions.submit(() -> connecterMaintenant(vivante));
    }

    /** La connexion d'un serveur : le repli SSE est tenté une fois pour un refus 4xx du Streamable. */
    private void connecterMaintenant(Vivante vivante) {
        synchronized (vivante.verrou) {
            if (vivante.client != null) {
                return;
            }
            ConfigServeurMcp config = vivante.config;
            if (config.desactive) {
                vivante.statut = Statut.DESACTIVE;
                vivante.motif = null;
                versionOutils.incrementAndGet();
                return;
            }
            if (config.transport
                    == ConfigServeurMcp.Transport.STDIO
                    && !stdioAutorise) {
                vivante.statut = Statut.NON_SUPPORTE;
                vivante.motif = "Local (command) servers run on "
                        + "desktop only. Use a remote URL on this "
                        + "device.";
                versionOutils.incrementAndGet();
                return;
            }
            vivante.statut = Statut.CONNEXION;
            vivante.motif = null;
            Exception echecInitial = null;
            try {
                ouvrir(vivante, transportDe(config));
                return;
            } catch (ErreurMcp.AuthRequise e) {
                vivante.defiAuth = e.defi;
                if (rafraichir(vivante)) {
                    try {
                        ouvrir(vivante, transportDe(config));
                        return;
                    } catch (Exception e2) {
                        echecInitial = e2;
                    }
                } else {
                    vivante.statut = Statut.AUTH_REQUISE;
                    vivante.motif = e.getMessage();
                    versionOutils.incrementAndGet();
                    return;
                }
            } catch (Exception e) {
                echecInitial = e;
            }
            // le repli : un serveur qui ne parle que le vieux SSE
            // refuse le POST du Streamable
            if (config.transport == ConfigServeurMcp.Transport.HTTP
                    && echecInitial instanceof ErreurMcp.EchecHttp echec
                    && echec.statut >= 400 && echec.statut < 500) {
                try {
                    ouvrir(vivante, nouveauSse(config));
                    return;
                } catch (ErreurMcp.AuthRequise e) {
                    vivante.defiAuth = e.defi;
                    vivante.statut = Statut.AUTH_REQUISE;
                    vivante.motif = e.getMessage();
                    versionOutils.incrementAndGet();
                    return;
                } catch (Exception e) {
                    // l'échec ORIGINAL est rapporté : le serveur a
                    // plus de chances d'être un Streamable cassé
                }
            }
            vivante.statut = Statut.ECHEC;
            vivante.motif = echecInitial == null ? "unknown error"
                    : decrire(echecInitial);
            journaliser(config.nom + ": " + vivante.motif);
            versionOutils.incrementAndGet();
        }
    }

    /** La poignée de main et la liste des outils ; l'état passe à CONNECTE. */
    private void ouvrir(Vivante vivante, TransportMcp transportMcp) {
        ClientMcp client = new ClientMcp(transportMcp);
        try {
            client.connecter();
            List<ClientMcp.OutilInfo> listes = client.listerOutils();
            client.outilsChanges = () -> {
                try {
                    vivante.outils = client.listerOutils();
                    versionOutils.incrementAndGet();
                } catch (Exception e) {
                    // le relistage réessaiera au prochain changement
                }
            };
            client.deconnecte = cause -> {
                if (vivante.client == client) {
                    vivante.client = null;
                    vivante.statut = Statut.ECHEC;
                    vivante.motif = cause == null
                            ? "The server disconnected."
                            : decrire(cause);
                    versionOutils.incrementAndGet();
                }
            };
            vivante.client = client;
            vivante.outils = listes;
            vivante.defiAuth = null;
            vivante.statut = Statut.CONNECTE;
            vivante.motif = null;
            journaliser(vivante.config.nom + ": connected, "
                    + listes.size() + " tools");
            versionOutils.incrementAndGet();
        } catch (Exception e) {
            try {
                client.close();
            } catch (Exception e2) {
                // la fermeture ne jette jamais
            }
            throw e instanceof RuntimeException probleme ? probleme
                    : new ErreurMcp(String.valueOf(e.getMessage()), e);
        }
    }

    private TransportMcp transportDe(ConfigServeurMcp config) {
        if (fabriqueTransport != null) {
            return fabriqueTransport.transport(config);
        }
        if (config.transport == ConfigServeurMcp.Transport.HTTP) {
            return nouveauFlux(config);
        }
        return nouveauSse(config);
    }

    private TransportMcp nouveauFlux(ConfigServeurMcp config) {
        return new TransportMcpFlux(config.url,
                () -> entetesDe(config), transport, jeton);
    }

    private TransportMcp nouveauSse(ConfigServeurMcp config) {
        return new TransportMcpSse(config.url,
                () -> entetesDe(config), transport, jeton);
    }

    /** Les en-têtes configurés + le Bearer de la signature stockée (rafraîchie avant si périssable). */
    private Map<String, String> entetesDe(ConfigServeurMcp config) {
        Map<String, String> entetes = new LinkedHashMap<>(
                config.entetes);
        boolean aAuth = false;
        for (String cle : entetes.keySet()) {
            if ("authorization".equalsIgnoreCase(cle)) {
                aAuth = true;
                break;
            }
        }
        JetonsMcp stockees = jetons == null ? null
                : jetons.lire(config.identite());
        if (stockees != null && stockees.perimee(null)) {
            try {
                JetonsMcp renouves = AutorisationMcp.rafraichir(
                        stockees, transport, jeton);
                jetons.poser(config.identite(), renouves);
                stockees = renouves;
            } catch (Exception e) {
                // les jetons périssables partiront tels quels : le
                // 401 déclenchera le flux de signature
            }
        }
        if (stockees != null && !aAuth) {
            entetes.put("Authorization",
                    "Bearer " + stockees.acces);
        }
        return entetes;
    }

    private void fermerVivante(Vivante vivante) {
        ClientMcp client = vivante.client;
        vivante.client = null;
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                // la fermeture ne jette jamais
            }
        }
    }

    /** La description d'un échec, actionnable (le motif de l'ADR-É9). */
    static String decrire(Throwable e) {
        if (e instanceof java.net.UnknownHostException) {
            return "Could not find the server (" + e.getMessage()
                    + "). Check the URL and your connection.";
        }
        if (e instanceof java.net.ConnectException) {
            return "Could not connect to the server: "
                    + e.getMessage();
        }
        if (e instanceof java.net.SocketTimeoutException
                || e instanceof java.util.concurrent
                .TimeoutException) {
            return "The server did not answer in time.";
        }
        if (e instanceof ErreurMcp.RefusServeur) {
            return "The server refused: " + e.getMessage();
        }
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName()
                : message;
    }

    private void journaliser(String ligne) {
        if (journal != null) {
            journal.add(ligne);
        }
    }

    @Override
    public void close() {
        connexions.shutdownNow();
        synchronized (this) {
            for (Vivante vivante : serveurs.values()) {
                fermerVivante(vivante);
            }
            serveurs.clear();
            versionOutils.incrementAndGet();
        }
    }

    /** Les outils déclarés dans un texte .mcp.json — le raccourci de l'hôte. */
    public static List<ConfigServeurMcp> lireFichier(String texte) {
        return ConfigServeurMcp.analyserFichier(texte);
    }
}
