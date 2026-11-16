package jo.joide.app.android;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import jo.joide.agent.ErreursLlm;
import jo.joide.agent.espace.EspaceTravailInjecteur;
import jo.joide.agent.EvenementAgent;
import jo.joide.agent.ExecuteurOutils;
import jo.joide.agent.FournisseurAnthropic;
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

    /** Le délai des blocages sans UI (permission, questions). */
    private static final long DELAI_SANS_UI_MS = 120_000;

    /** Une entrée du journal rejouable : un événement OU un message utilisateur. */
    public static final class Entree {
        /** L'événement (null si message utilisateur). */
        public final EvenementAgent evenement;
        /** Le message utilisateur (null si événement). */
        public final String message;

        Entree(EvenementAgent evenement, String message) {
            this.evenement = evenement;
            this.message = message;
        }

        /** @return vrai si c'est un message utilisateur */
        public boolean estMessage() {
            return message != null;
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
            AgentService.this.envoyer(message);
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
        relireSession();
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
        if (message == null || message.isBlank()) {
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
        consigner(new Entree(null, message));
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
                        .ANTHROPIC == fournisseur
                        ? "anthropic" : "openai";
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
        FournisseurLlm fournisseurLlm;
        if (fournisseur == jo.joide.app.android.parametres.Reglages
                .FournisseurIa.ANTHROPIC) {
            fournisseurLlm = new FournisseurAnthropic("", cle);
        } else {
            fournisseurLlm = new FournisseurOpenAiCompatible(
                    reglages.urlIa, cle);
        }
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
                            new Entree(evenement, null)));
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
    // Session persistée
    // ------------------------------------------------------------------

    private Path fichierSession() {
        return Paths.get(getFilesDir().getAbsolutePath(),
                "agent", "session.json");
    }

    private void relireSession() {
        Path cible = fichierSession();
        if (!Files.isRegularFile(cible)) {
            historique = HistoriqueAgent.vide();
            return;
        }
        try {
            SessionsAgent.Session session = SessionsAgent.lire(cible);
            historique = new HistoriqueAgent(session.historique,
                    session.compactes);
            transcript = new ArrayList<>(session.transcript);
            usageConversation = session.usage;
            for (int i = 0; i < transcript.size(); i++) {
                journal.add(new Entree(null, transcript.get(i)));
            }
        } catch (IOException | RuntimeException e) {
            // session illisible : conversation neuve, jamais un crash
            historique = HistoriqueAgent.vide();
        }
    }

    private void persiste() {
        try {
            SessionsAgent.ecrire(fichierSession(),
                    new SessionsAgent.Session(
                            fournisseur.name(), modele, transcript,
                            historique.messages(),
                            historique.compactes(), usageConversation));
        } catch (IOException e) {
            // la session part au mieux : le tour vit en mémoire
        }
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
