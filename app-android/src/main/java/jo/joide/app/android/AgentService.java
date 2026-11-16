package jo.joide.app.android;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import jo.joide.agent.BoucleAgent;
import jo.joide.app.android.atelier.agent.ConversationsAgent;
import jo.joide.app.android.atelier.agent.PieceJointe;
import jo.joide.agent.ErreursLlm;
import jo.joide.agent.espace.EspaceTravailInjecteur;
import jo.joide.agent.EvenementAgent;
import jo.joide.agent.ExecuteurOutils;
import jo.joide.agent.BancCles;
import jo.joide.agent.FournisseurAnthropic;
import jo.joide.agent.FournisseurBascule;
import jo.joide.agent.FournisseurGemini;
import jo.joide.agent.FournisseurLlm;
import jo.joide.agent.FournisseurOpenAiCompatible;
import jo.joide.agent.HistoriqueAgent;
import jo.joide.agent.Permissions;
import jo.joide.agent.SessionsAgent;
import jo.joide.agent.TransportOkHttp;
import jo.joide.agent.UsageLlm;
import jo.joide.agent.espace.EspaceCheckpoint;
import jo.joide.agent.espace.EspaceTravailAgent;
import jo.joide.agent.espace.QuestionUtilisateur;
import jo.joide.agent.mcp.ConfigServeurMcp;
import jo.joide.agent.mcp.GestionnaireMcp;
import jo.joide.agent.mcp.JetonsMcp;
import jo.joide.agent.mcp.StoreJetonsMcp;
import jo.joide.agent.outils.OutilsV1;
import jo.joide.agent.outils.PlanAgent;
import jo.joide.platform.CancelToken;
import jo.joide.platform.WorkerPools;

/**
 * Le service de premier plan de l'agent (lot P, ADR-É11 point 8) :
 * le tour vit ICI, pas dans l'écran — il survit au changement
 * d'écran, à la mise en arrière-plan, au doigt qui glisse. Le
 * service est le modèle d'état : il tient le JOURNAL rejouable des
 * événements, l'historique de la conversation (persisté par
 * {@link SessionsAgent} à chaque fin de tour), et les DEUX blocages
 * honnêtes — la permission (fermer = refuser) et les questions
 * ask_user — résolus par l'UI quand elle est là, par le DÉFAUT SÛR
 * (refus / passer) quand elle ne l'est pas ou que le délai (120 s)
 * expire.
 *
 * <p>Notification silencieuse « l'agent travaille » avec bouton
 * Arrêter (le {@code CancelToken} du tour) — pattern ADR-043 du
 * démon. Arrêt propre mi-outils : l'annulation coupe la boucle, la
 * réparation d'historique (lot N) apparie les appels interrompus, le
 * tour finit ANNULE, la conversation reprend au message suivant.</p>
 */
public final class AgentService extends Service {

    /** Nom du canal de notification. */
    static final String CANAL = "joide-agent";

    /** L'action Arrêter de la notification. */
    private static final String ACTION_ARRETER = "joide.agent.ARRETER";

    /**
     * L'URL de base d'OpenRouter (lot U) : la passerelle multi-modèles
     * parle OpenAI-compatible (Chat Completions) — son URL de base est
     * propre à elle, l'URL libre du fournisseur OPENAI ne s'applique
     * pas.
     */
    private static final String URL_OPENROUTER =
            "https://openrouter.ai/api/v1";

    /** Le délai des blocages sans UI (permission, questions). */
    private static final long DELAI_SANS_UI_MS = 120_000;

    /**
     * Une entrée du journal rejouable : un événement, un message
     * utilisateur ou une réponse restaurée (lot S — la reprise de
     * conversation rejoue le transcript persisté SANS inventer les
     * événements de flux d'un tour que personne n'a revu).
     */
    public static final class Entree {
        /** L'événement (null si message ou réponse). */
        public final EvenementAgent evenement;
        /** Le message utilisateur (null sinon). */
        public final String message;
        /** La réponse restaurée (null sinon). */
        public final String reponse;
        /** Les noms des pièces jointes du message (affichage seul). */
        public final List<String> pieces;

        Entree(EvenementAgent evenement, String message) {
            this(evenement, message, null, List.of());
        }

        Entree(EvenementAgent evenement, String message,
               String reponse, List<String> pieces) {
            this.evenement = evenement;
            this.message = message;
            this.reponse = reponse;
            this.pieces = List.copyOf(pieces);
        }

        /** @return l'entrée message utilisateur */
        public static Entree message(String texte) {
            return new Entree(null, texte, null, List.of());
        }

        /**
         * @param texte le message
         * @param noms  les noms des pièces jointes (affichage)
         * @return l'entrée message avec pièces
         */
        public static Entree message(String texte,
                                     List<String> noms) {
            return new Entree(null, texte, null, noms);
        }

        /** @return l'entrée réponse restaurée */
        public static Entree reponse(String texte) {
            return new Entree(null, null, texte, List.of());
        }

        /** @return vrai si c'est un message utilisateur */
        public boolean estMessage() {
            return message != null;
        }

        /** @return vrai si c'est une réponse restaurée */
        public boolean estReponse() {
            return reponse != null;
        }
    }

    /** Le binder local : l'atelier y branche l'espace et l'écouteur. */
    public final class AgentBinder extends Binder {

        /**
         * Pose l'espace de travail du projet (l'atelier l'a construit
         * sur les briques, enveloppé de checkpoints — lot O).
         *
         * @param espace l'espace confiné
         */
        public void poserEspace(EspaceTravailAgent espace) {
            AgentService.this.espace = espace;
        }

        /** @param mode le mode de permission du tour suivant */
        public void poserMode(Permissions.Mode mode) {
            AgentService.this.mode = mode;
        }

        /**
         * Pose le modèle de la conversation.
         *
         * @param fournisseur le fournisseur choisi
         * @param modele      le nom de modèle
         */
        public void poserModele(jo.joide.app.android.parametres.Reglages
                                        .FournisseurIa fournisseur,
                                String modele) {
            AgentService.this.fournisseur = fournisseur;
            AgentService.this.modele = modele;
        }

        /** @return vrai si un tour est en cours */
        public boolean tourEnCours() {
            return tour != null && !tour.isDone();
        }

        /**
         * Envoie un message : ouvre un tour (premier appel : la
         * conversation repart de la session persistée si elle existe).
         *
         * @param message ce que dit l'utilisateur
         */
        public void envoyer(String message) {
            AgentService.this.envoyer(message, List.of());
        }

        /**
         * Envoie un message avec ses pièces jointes (lot S) : les
         * images partent en parties IMAGE du message neutre, les
         * fichiers et l'arbre sont déjà fondus par l'atelier dans le
         * message.
         *
         * @param message ce que dit l'utilisateur (contexte fondu)
         * @param pieces  les pièces jointes du tour
         */
        public void envoyer(String message,
                            List<jo.joide.app.android.atelier.agent
                                    .PieceJointe> pieces) {
            AgentService.this.envoyer(message, pieces);
        }

        /** Arrête le tour en cours (bouton Arrêter, notification). */
        public void arreter() {
            AgentService.this.arreter();
        }

        /** @return le journal complet (rejouable à l'identique) */
        public List<Entree> journal() {
            synchronized (AgentService.this) {
                return new ArrayList<>(journal);
            }
        }

        // ---- les conversations (lot S, ADR-064) ----

        /**
         * Ouvre une conversation neuve : la courante est persistée
         * (si elle a vécu), l'état repart du vide.
         *
         * @return vrai si la conversation a changé (faux : un tour
         *         est en cours — le service n'abandonne jamais un
         *         tour vivant)
         */
        public boolean nouvelleConversation() {
            return AgentService.this.nouvelleConversation();
        }

        /** @return les résumés, du plus récent au plus ancien */
        public List<jo.joide.app.android.atelier.agent
                .ConversationsAgent.Resume> conversations() {
            return conversations.lister();
        }

        /** @return l'identité de la conversation courante */
        public String conversationActive() {
            synchronized (AgentService.this) {
                return idConversation;
            }
        }

        /**
         * Reprend une conversation persistée.
         *
         * @param id l'identité (nom de fichier sans suffixe)
         * @return vrai si reprise (faux : tour en cours, ou
         *         conversation introuvable — l'état ne change pas)
         */
        public boolean reprendreConversation(String id) {
            return AgentService.this.reprendreConversation(id);
        }

        /**
         * Supprime une conversation persistée.
         *
         * @param id l'identité
         * @return vrai si le fichier est parti
         */
        public boolean supprimerConversation(String id) {
            return conversations.supprimer(id);
        }

        /**
         * @return l'usage cumulé de la conversation courante
         *         (l'écran Usages le montre, lot S)
         */
        public UsageLlm usageConversation() {
            synchronized (AgentService.this) {
                return usageConversation;
            }
        }

        /**
         * @return le banc de clés par raison d'assise (l'écran Usages
         *         le montre), ou une carte vide si le banc n'est pas
         *         né (aucun tour n'a encore tourné)
         */
        public java.util.Map<ErreursLlm.Genre, Integer> bancParRaison() {
            BancCles banc = bancCles;
            return banc == null ? java.util.Map.of()
                    : banc.bancParRaison();
        }

        /**
         * @return le nombre de clés du banc (0 si le banc n'est pas né)
         */
        public int tailleBanc() {
            BancCles banc = bancCles;
            return banc == null ? 0 : banc.taille();
        }

        /**
         * L'occupation des fenêtres du modèle courant (lot T, ADR-065
         * fermant l'écart consigné par l'ADR-064) : {requêtes,
         * jetons}, 0..1, 0 sans plafond connu — l'écran Usages en
         * tire ses bandes colorées, l'alerte précède le 429.
         *
         * @return {occupationRpm, occupationTpm}
         */
        public double[] occupationsModele() {
            FournisseurBascule bascule;
            String modeleCourant;
            synchronized (AgentService.this) {
                bascule = fournisseurBascule;
                modeleCourant = modele;
            }
            if (bascule == null) {
                return new double[]{0, 0};
            }
            return bascule.occupationModele(modeleCourant);
        }

        // ---- les connexions MCP (lot S, ADR-064 câblant l'ADR-063) ----

        /**
         * Pose le contenu du {@code .mcp.json} du projet (lu borné
         * par l'atelier) : les serveurs declarés sont PARSÉS, pas
         * activés — l'approbation reste un geste de l'utilisateur
         * (ADR-063 : un dépôt cloné n'est pas un choix).
         *
         * @param contenu le texte du fichier, vide si absent
         */
        public void poserMcpJson(String contenu) {
            List<ConfigServeurMcp> configs =
                    GestionnaireMcp.lireFichier(contenu);
            synchronized (AgentService.this) {
                configsMcp = configs;
            }
            if (approuveMcp) {
                gestionnaireMcp.maj(configs);
            }
        }

        /** @return les serveurs declarés par le .mcp.json du projet */
        public List<ConfigServeurMcp> configsMcp() {
            synchronized (AgentService.this) {
                return configsMcp;
            }
        }

        /** @return vrai si l'utilisateur a activé les serveurs du projet */
        public boolean approuveMcp() {
            return approuveMcp;
        }

        /**
         * L'approbation des serveurs du projet (lot S) : le geste
         * explicite qui connecte — ou déconnecte. v1 : par session,
         * la persistance par projet attend le store chiffré des
         * signatures (backlog lot R).
         *
         * @param accordee vrai pour connecter les serveurs declarés
         */
        public void poserApprobationMcp(boolean accordee) {
            approuveMcp = accordee;
            List<ConfigServeurMcp> configs;
            synchronized (AgentService.this) {
                configs = configsMcp;
            }
            gestionnaireMcp.maj(accordee ? configs : List.of());
        }

        /** @return les états publiés des serveurs connectés */
        public List<GestionnaireMcp.EtatServeur> etatsMcp() {
            return gestionnaireMcp.etats();
        }

        /** Reconnecte un serveur nommé (après une signature, un échec). */
        public void reconnecterMcp(String nom) {
            gestionnaireMcp.reconnecter(nom);
        }

        /** @return le nombre d'outils MCP en ligne */
        public int outilsMcp() {
            return gestionnaireMcp.outils().size();
        }

        /**
         * Pose l'écouteur live (appelé sur le fil UI) ; les entrées
         * déjà passées ne sont PAS rejouées ici — l'appelant rejoue
         * {@link #journal()} d'abord.
         *
         * @param ecouteur le consommateur d'entrées
         */
        public void poserEcouteur(Consumer<Entree> ecouteur) {
            AgentService.this.ecouteur = ecouteur;
        }

        /** @return la demande de permission en attente, ou null */
        public Permissions.DemandeEcriture permissionEnAttente() {
            synchronized (AgentService.this) {
                return permission;
            }
        }

        /**
         * @param accordee la décision de l'utilisateur (fermer le
         *                 dialogue revient à refuser — l'UI le dit)
         */
        public void repondrePermission(boolean accordee) {
            CompletableFuture<Boolean> attente = attentePermission;
            if (attente != null) {
                attente.complete(accordee);
            }
        }

        /** @return les questions en attente, ou null */
        public List<QuestionUtilisateur> questionsEnAttente() {
            synchronized (AgentService.this) {
                return questions;
            }
        }

        /**
         * @param lignes les réponses, une par ligne « question : réponse »
         */
        public void repondreQuestions(List<String> lignes) {
            CompletableFuture<List<String>> attente = attenteQuestions;
            if (attente != null) {
                attente.complete(lignes);
            }
        }
    }

    private final AgentBinder binder = new AgentBinder();
    private final Handler filUi = new Handler(Looper.getMainLooper());

    private EspaceTravailAgent espace;
    private Permissions.Mode mode = Permissions.Mode.DEMANDE_CHAQUE;
    private jo.joide.app.android.parametres.Reglages.FournisseurIa
            fournisseur = jo.joide.app.android.parametres.Reglages
            .FournisseurIa.ANTHROPIC;
    private String modele = "";
    private final List<Entree> journal = new ArrayList<>();
    private HistoriqueAgent historique;
    private List<String> transcript = new ArrayList<>();
    private UsageLlm usageConversation = UsageLlm.zero();
    private ExecutorService executeur;
    private java.util.concurrent.Future<?> tour;
    private CancelToken jeton;
    private CancelToken.Cancellation annulation;
    private Consumer<Entree> ecouteur;
    private Permissions.DemandeEcriture permission;
    private List<QuestionUtilisateur> questions;
    private CompletableFuture<Boolean> attentePermission;
    private CompletableFuture<List<String>> attenteQuestions;
    private PlanAgent plan;
    private int requetesDuTour;
    private List<jo.joide.agent.PartieContenu> imagesDuTour = List.of();
    private ConversationsAgent conversations;
    private String idConversation = "";
    private GestionnaireMcp gestionnaireMcp;
    private List<ConfigServeurMcp> configsMcp = List.of();
    private volatile boolean approuveMcp;

    /** Le store des signatures MCP en mémoire (v1 : par session). */
    private final StoreJetonsMcp jetonsMcpMemoire =
            new StoreJetonsMcp() {
                private final java.util.Map<String, JetonsMcp> jetons =
                        new java.util.concurrent.ConcurrentHashMap<>();

                @Override
                public JetonsMcp lire(String identite) {
                    return jetons.get(identite);
                }

                @Override
                public void poser(String identite, JetonsMcp poses) {
                    jetons.put(identite, poses);
                }

                @Override
                public void retirer(String identite) {
                    jetons.remove(identite);
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager gestionnaire =
                getSystemService(NotificationManager.class);
        if (gestionnaire != null
                && gestionnaire.getNotificationChannel(CANAL) == null) {
            gestionnaire.createNotificationChannel(
                    new NotificationChannel(CANAL,
                            getString(R.string.canal_agent),
                            NotificationManager.IMPORTANCE_LOW));
        }
        executeur = WorkerPools.newFixed("joide-agent", 1);
        plan = new PlanAgent();
        conversations = new ConversationsAgent(
                Paths.get(getFilesDir().getAbsolutePath(),
                        "agent", "conversations"),
                java.time.Clock.systemUTC());
        // la migration du lot P : l'unique session devient la plus
        // ancienne conversation — rien n'est perdu, rien n'est dupliqué
        conversations.migrerSessionUnique(Paths.get(
                getFilesDir().getAbsolutePath(),
                "agent", "session.json"));
        idConversation = conversations.activeOuNeuve();
        relireSession();
        // le gestionnaire MCP (lot R) : stdio non autorisé sur
        // Android (NON_SUPPORTE honnête), les jetons en mémoire v1
        gestionnaireMcp = new GestionnaireMcp(jetonsMcpMemoire,
                new TransportOkHttp(), CancelToken.NEVER, false,
                null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_ARRETER.equals(intent.getAction())) {
            arreter();
            stopSelf();
            return START_NOT_STICKY;
        }
        // premier démarrage (ou relance) : l'avant-plan tant qu'un
        // tour vit ; l'envoi passe par le binder
        demarrerAvantPlan(startId);
        return START_NOT_STICKY;
    }

    private void demarrerAvantPlan(int id) {
        PendingIntent arreter = PendingIntent.getService(this, 1,
                new Intent(this, AgentService.class)
                        .setAction(ACTION_ARRETER),
                PendingIntent.FLAG_IMMUTABLE
                        | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification =
                new Notification.Builder(this, CANAL)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setContentTitle(getString(
                                R.string.agent_service_actif))
                        .setOngoing(true)
                        .setActions(new Notification.Action.Builder(
                                null, getString(R.string.agent_arreter),
                                arreter).build())
                        .build();
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            startForeground(id, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(id, notification);
        }
    }

    // ------------------------------------------------------------------
    // Le tour
    // ------------------------------------------------------------------

    private void envoyer(String message) {
        envoyer(message, List.of());
    }

    /**
     * Envoie un message avec ses pièces jointes (lot S) : les images
     * réduites partent en parties IMAGE du message neutre, le texte
     * des fichiers/projet est déjà fondu dans le message par
     * l'atelier.
     *
     * @param message ce que dit l'utilisateur (contexte fondu)
     * @param pieces  les pièces jointes du tour
     */
    private void envoyer(String message,
                         List<jo.joide.app.android.atelier.agent
                                 .PieceJointe> pieces) {
        if (message == null || (message.isBlank()
                && PieceJointe
                        .images(pieces).isEmpty())) {
            return;
        }
        if (tour != null && !tour.isDone()) {
            // jamais deux tours : le composeur est désactivé pendant
            // le tour (l'UI y veille), le service n'empile pas
            return;
        }
        if (espace == null) {
            consigner(new Entree(EvenementAgent.erreur(
                    new ErreursLlm.ErreurLlm(ErreursLlm.Genre
                            .RESEAU, "no workspace", -1)), null));
            consigner(new Entree(EvenementAgent.fin(
                    EvenementAgent.RaisonFin.ERREUR), null));
            return;
        }
        imagesDuTour = PieceJointe
                .images(pieces);
        consigner(Entree.message(message,
                PieceJointe
                        .noms(pieces)));
        demarrerAvantPlan(1);
        annulation = CancelToken.create();
        jeton = annulation.token();
        tour = executeur.submit(this::deroulerTour);
    }

    private void arreter() {
        CancelToken.Cancellation annulationCourante = annulation;
        if (annulationCourante != null) {
            annulationCourante.cancel();
        }
        // les blocages sans UI se débloquent sur le DÉFAUT SÛR
        CompletableFuture<Boolean> permissionAttente = attentePermission;
        if (permissionAttente != null) {
            permissionAttente.complete(false);
        }
        CompletableFuture<List<String>> questionsAttente = attenteQuestions;
        if (questionsAttente != null) {
            questionsAttente.complete(List.of());
        }
    }

    private void deroulerTour() {
        String message = dernierMessage();
        if (message == null || historique == null) {
            historique = HistoriqueAgent.vide();
        }
        jo.joide.app.android.parametres.Reglages reglages =
                jo.joide.app.android.parametres.Reglages.lire(
                        jo.joide.app.android.parametres.PreferencesAndroid
                                .source(this));
        String nomFournisseur =
                jo.joide.app.android.parametres.Reglages.FournisseurIa
                        .ANTHROPIC == fournisseur ? "anthropic"
                        : jo.joide.app.android.parametres.Reglages
                        .FournisseurIa.GEMINI == fournisseur
                        ? "gemini" : jo.joide.app.android.parametres
                        .Reglages.FournisseurIa.OPENROUTER == fournisseur
                        ? "openrouter" : "openai";
        String cle = lireCle(nomFournisseur);
        if (cle == null) {
            consigner(new Entree(EvenementAgent.erreur(
                    new ErreursLlm.ErreurLlm(
                            ErreursLlm.Genre.CLE_MANQUANTE,
                            "no key for " + nomFournisseur, -1)), null));
            consigner(new Entree(EvenementAgent.fin(
                    EvenementAgent.RaisonFin.ERREUR), null));
            tourFini();
            return;
        }
        String modeleChoisi = modele == null || modele.isEmpty()
                ? reglages.modeleIa : modele;
        // le lot Q : la clé du stockage chiffré porte PLUSIEURS clés
        // séparées par saut de ligne ou virgule — le banc et la
        // bascule survivent à la conversation (les limites apprises
        // aussi), ils ne se reconstruisent que si les clés changent
        FournisseurLlm fournisseurLlm = bascule(nomFournisseur, cle);
        EspaceTravailAgent espacePonte = espace;
        EspaceCheckpoint checkpoints = espacePonte instanceof
                EspaceCheckpoint verrou ? verrou : null;
        if (checkpoints != null) {
            checkpoints.commencerTour();
        }
        EspaceTravailAgent espaceQuestions =
                new EspaceTravailInjecteur(espacePonte) {
                    @Override
                    public String demanderUtilisateur(
                            List<QuestionUtilisateur> posees) {
                        return poserQuestions(posees);
                    }
                };
        requetesDuTour = 0;
        FournisseurLlm compteur = new FournisseurLlm() {
            @Override
            public String nom() {
                return fournisseurLlm.nom();
            }

            @Override
            public jo.joide.agent.MessageAgent envoyer(
                    jo.joide.agent.TransportHttp transport,
                    jo.joide.agent.RequeteLlm requete, CancelToken jeton,
                    Consumer<jo.joide.agent.EvenementFlux> ecouteur) {
                requetesDuTour++;
                return fournisseurLlm.envoyer(transport, requete,
                        jeton, ecouteur);
            }
        };
        ExecuteurOutils executeurOutils = new ExecuteurOutils(
                portOutils(espaceQuestions, espacePonte),
                chemin -> {
                    try {
                        return espacePonte.lireFichier(chemin);
                    } catch (IOException e) {
                        return null;
                    }
                });
        BoucleAgent boucle = new BoucleAgent(compteur, modeleChoisi,
                new TransportOkHttp(), executeurOutils, null, null);
        try {
            BoucleAgent.ResultatTour resultat = boucle.tour(message,
                    historique, mode,
                    this::demanderPermission,
                    contexteVolatile(), jeton,
                    evenement -> consigner(
                            new Entree(evenement, null)),
                    imagesDuTour);
            usageConversation = usageConversation.plus(resultat.usage);
            transcript.add(message);
            transcript.add(resultat.texteFinal == null
                    ? "" : resultat.texteFinal);
            persiste();
        } catch (RuntimeException e) {
            ErreursLlm.ErreurLlm erreur = e instanceof
                    ErreursLlm.ErreurLlm classifie ? classifie
                    : new ErreursLlm.ErreurLlm(
                            ErreursLlm.Genre.REPONSE_ILLISIBLE,
                            String.valueOf(e.getMessage()), -1);
            consigner(new Entree(EvenementAgent.erreur(erreur), null));
            consigner(new Entree(EvenementAgent.fin(
                    EvenementAgent.RaisonFin.ERREUR), null));
        } finally {
            executeurOutils.close();
            tourFini();
        }
    }

    private void tourFini() {
        filUi.post(() -> {
            NotificationManager gestionnaire =
                    getSystemService(NotificationManager.class);
            if (gestionnaire != null) {
                gestionnaire.cancel(1);
            }
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        });
    }

    /** Le contexte volatile du tour (note de fin, ADR-055). */
    private String contexteVolatile() {
        StringBuilder contexte = new StringBuilder("Project context.");
        if (!transcript.isEmpty()) {
            contexte.append(" This conversation already has ")
                    .append(transcript.size() / 2)
                    .append(" completed turn(s).");
        }
        contexte.append(" Permission mode: ").append(mode);
        return contexte.toString();
    }

    // ------------------------------------------------------------------
    // Les deux blocages honnêtes : permission et questions
    // ------------------------------------------------------------------

    private Permissions.Decision demanderPermission(
            Permissions.DemandeEcriture demande) {
        synchronized (this) {
            permission = demande;
            attentePermission = new CompletableFuture<>();
        }
        publierAuxEcouteurs(new Entree(
                EvenementAgent.outilDebut("", demande.outil), null));
        try {
            boolean accordee = attentePermission.get(
                    DELAI_SANS_UI_MS, java.util.concurrent.TimeUnit
                            .MILLISECONDS);
            return accordee ? Permissions.Decision.ACCORDEE
                    : Permissions.Decision.REFUSEE;
        } catch (Exception e) {
            // délai expiré ou interruption : le DÉFAUT SÛR
            return Permissions.Decision.REFUSEE;
        } finally {
            synchronized (this) {
                permission = null;
                attentePermission = null;
            }
        }
    }

    private String poserQuestions(List<QuestionUtilisateur> posees) {
        synchronized (this) {
            questions = posees;
            attenteQuestions = new CompletableFuture<>();
        }
        try {
            List<String> reponses = attenteQuestions.get(
                    DELAI_SANS_UI_MS, java.util.concurrent.TimeUnit
                            .MILLISECONDS);
            return String.join("\n", reponses);
        } catch (Exception e) {
            // sans réponse : chaque question devient « passer »
            List<String> passees = new ArrayList<>();
            for (QuestionUtilisateur question : posees) {
                passees.add(question.question + " : (skipped)");
            }
            return String.join("\n", passees);
        } finally {
            synchronized (this) {
                questions = null;
                attenteQuestions = null;
            }
        }
    }

    // ------------------------------------------------------------------
    // Les conversations persistées (lot S, ADR-064) : une
    // conversation = un fichier du répertoire, l'identité est le nom
    // de fichier. Le transcript alterne message/réponse — la
    // restauration rend à chacun son RÔLE (le lot P rejouait tout en
    // messages : une réponse redevenait une carte utilisateur).
    // ------------------------------------------------------------------

    private void relireSession() {
        SessionsAgent.Session session =
                conversations.lire(idConversation);
        if (session == null) {
            historique = HistoriqueAgent.vide();
            return;
        }
        historique = new HistoriqueAgent(session.historique,
                session.compactes);
        transcript = new ArrayList<>(session.transcript);
        usageConversation = session.usage;
        rejouerTranscript();
    }

    /** Le transcript persisté redevient un journal rejouable. */
    private void rejouerTranscript() {
        for (int i = 0; i < transcript.size(); i++) {
            journal.add(i % 2 == 0
                    ? Entree.message(transcript.get(i))
                    : Entree.reponse(transcript.get(i)));
        }
    }

    private void persiste() {
        conversations.ecrire(idConversation,
                new SessionsAgent.Session(
                        fournisseur.name(), modele, transcript,
                        historique.messages(),
                        historique.compactes(), usageConversation));
    }

    /**
     * Ouvre une conversation neuve : la courante est persistée, le
     * journal, l'historique et l'usage repartent du vide.
     *
     * @return vrai si la conversation a changé (faux : un tour vit)
     */
    private boolean nouvelleConversation() {
        if (tour != null && !tour.isDone()) {
            return false;
        }
        if (!transcript.isEmpty()) {
            persiste();
        }
        synchronized (this) {
            journal.clear();
            idConversation = conversations.nouvelleIdentite();
        }
        historique = HistoriqueAgent.vide();
        transcript = new ArrayList<>();
        usageConversation = UsageLlm.zero();
        return true;
    }

    /**
     * Reprend une conversation persistée : son journal redevient
     * l'état du service, l'usage et l'historique suivent.
     *
     * @param id l'identité
     * @return vrai si reprise (faux : tour vivant ou introuvable)
     */
    private boolean reprendreConversation(String id) {
        if (tour != null && !tour.isDone() || id == null) {
            return false;
        }
        SessionsAgent.Session session = conversations.lire(id);
        if (session == null) {
            return false;
        }
        if (!transcript.isEmpty()) {
            persiste();
        }
        synchronized (this) {
            journal.clear();
            idConversation = id;
            historique = new HistoriqueAgent(session.historique,
                    session.compactes);
            transcript = new ArrayList<>(session.transcript);
            usageConversation = session.usage;
            rejouerTranscript();
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Internes
    // ------------------------------------------------------------------

    private String lireCle(String nomFournisseur) {
        try {
            return jo.joide.app.android.parametres.KeystoreCles
                    .sur(this).lire(nomFournisseur);
        } catch (IOException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Le banc de clés et la bascule (lot Q, ADR-062) : une clé ou
    // plusieurs — l'ordre utilisateur, les bancs différenciés, la
    // régulation préventive qui apprend. Détenu PAR SERVICE : les
    // limites apprises survivent à la conversation.
    // ------------------------------------------------------------------

    private BancCles bancCles;
    private FournisseurBascule fournisseurBascule;
    private String bancSignature;

    /**
     * La bascule du fournisseur pour ce tour : reconstruite seulement
     * quand le fournisseur ou l'ensemble des clés a changé.
     *
     * @param nomFournisseur l'identifiant du stockage chiffré
     * @param cle            la valeur stockée (plusieurs clés
     *                       séparées par saut de ligne ou virgule)
     * @return le fournisseur enveloppé
     */
    private FournisseurLlm bascule(String nomFournisseur,
                                   String cle) {
        List<String> cles = separerCles(cle);
        String signature = nomFournisseur + "|" + cles;
        if (fournisseurBascule == null
                || !signature.equals(bancSignature)) {
            bancCles = new BancCles(cles, null);
            fournisseurBascule = new FournisseurBascule(bancCles,
                    cleDeLaFabrique -> fournisseurParCle(
                            nomFournisseur, cleDeLaFabrique), null);
            bancSignature = signature;
        }
        return fournisseurBascule;
    }

    /** Les clés séparées, dans l'ordre de l'utilisateur. */
    private static List<String> separerCles(String cle) {
        List<String> cles = new ArrayList<>();
        for (String morceau : cle.split("[\n,]")) {
            String n = morceau.trim();
            if (!n.isEmpty()) {
                cles.add(n);
            }
        }
        return cles;
    }

    /** Le fournisseur d'une clé du banc. */
    private FournisseurLlm fournisseurParCle(String nomFournisseur,
                                             String cleDuBanc) {
        if ("anthropic".equals(nomFournisseur)) {
            return new FournisseurAnthropic("", cleDuBanc);
        }
        if ("gemini".equals(nomFournisseur)) {
            return new FournisseurGemini(cleDuBanc);
        }
        if ("openrouter".equals(nomFournisseur)) {
            // lot U : la passerelle porte SON URL de base — l'URL
            // libre du fournisseur OpenAI ne s'applique pas ici
            return new FournisseurOpenAiCompatible(
                    URL_OPENROUTER, cleDuBanc);
        }
        jo.joide.app.android.parametres.Reglages reglages =
                jo.joide.app.android.parametres.Reglages.lire(
                        jo.joide.app.android.parametres
                                .PreferencesAndroid.source(this));
        return new FournisseurOpenAiCompatible(reglages.urlIa,
                cleDuBanc);
    }

    private String dernierMessage() {
        synchronized (this) {
            for (int i = journal.size() - 1; i >= 0; i--) {
                Entree entree = journal.get(i);
                if (entree.estMessage()) {
                    return entree.message;
                }
            }
        }
        return null;
    }

    private ExecuteurOutils.PortOutils portOutils(
            EspaceTravailAgent espaceQuestions,
            EspaceTravailAgent espacePonte) {
        List<jo.joide.agent.OutilAgent> outils = OutilsV1.tous(
                espaceQuestions, transportWeb(), plan);
        // les outils MCP distants rejoignent le port quand les
        // serveurs du projet sont approuvés (lot S)
        outils.addAll(gestionnaireMcp.outils());
        return new ExecuteurOutils.PortOutils() {
            @Override
            public jo.joide.agent.OutilAgent outil(String nom) {
                for (jo.joide.agent.OutilAgent outil : outils) {
                    if (outil.description().nom.equals(nom)) {
                        return outil;
                    }
                }
                return null;
            }

            @Override
            public List<jo.joide.agent.OutilAgent> outils() {
                return outils;
            }
        };
    }

    private jo.joide.agent.TransportHttp transportWeb() {
        return new TransportOkHttp();
    }

    private void consigner(Entree entree) {
        synchronized (this) {
            journal.add(entree);
        }
        publierAuxEcouteurs(entree);
    }

    private void publierAuxEcouteurs(Entree entree) {
        Consumer<Entree> ecouteurCourant = ecouteur;
        if (ecouteurCourant != null) {
            filUi.post(() -> {
                Consumer<Entree> ecouteurMaintenant = ecouteur;
                if (ecouteurMaintenant != null) {
                    ecouteurMaintenant.accept(entree);
                }
            });
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        arreter();
        if (gestionnaireMcp != null) {
            gestionnaireMcp.close();
        }
        if (executeur != null) {
            executeur.shutdownNow();
        }
        super.onDestroy();
    }

    /**
     * Démarre le service (l'atelier l'appelle à l'ouverture du
     * tiroir — l'avant-plan ne part qu'au premier tour).
     *
     * @param contexte le contexte de l'application
     */
    public static void demarrer(Context contexte) {
        contexte.startService(new Intent(contexte, AgentService.class));
    }
}
