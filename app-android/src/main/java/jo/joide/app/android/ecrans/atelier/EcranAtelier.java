package jo.joide.app.android.ecrans.atelier;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import jo.joide.app.android.ui.ActiviteJoide;
import jo.joide.app.android.ProprietesConstruction;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import jo.joide.app.android.BuildService;
import jo.joide.app.android.FichiersProjet;
import jo.joide.app.android.R;
import jo.joide.app.android.SpecsBuild;
import jo.joide.app.android.StoreAndroid;
import jo.joide.app.android.parametres.PreferencesAndroid;
import jo.joide.app.android.parametres.Reglages;
import jo.joide.app.android.atelier.Arborescence;
import jo.joide.app.android.atelier.EcrituresFusionnees;
import jo.joide.app.android.atelier.EtatApercu;
import jo.joide.app.android.AgentService;
import jo.joide.app.android.atelier.EtatAtelier;
import jo.joide.agent.ReductionImages;
import jo.joide.app.android.atelier.agent.ConversationsAgent;
import jo.joide.app.android.atelier.agent.FournisseurCameraAgent;
import jo.joide.app.android.atelier.agent.PieceJointe;
import jo.joide.app.android.atelier.agent.ReducteurImagesAndroid;
import jo.joide.app.android.atelier.agent.EtatAgentChat;
import jo.joide.app.android.atelier.agent.VueAgent;
import jo.joide.app.android.atelier.NouveauNom;
import jo.joide.app.android.atelier.VueApercu;
import jo.joide.app.android.atelier.VueArbre;
import jo.joide.app.android.console.EtatConsole;
import jo.joide.app.android.console.VueConsole;
import jo.joide.app.android.ui.Bouton;
import jo.joide.app.android.ui.Dialogue;
import jo.joide.app.android.ui.EtatVide;
import jo.joide.app.android.ui.Fab;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.Icone;
import jo.joide.app.android.atelier.ModeleOnglets;
import jo.joide.app.android.atelier.EtatFeuille;
import jo.joide.app.android.atelier.EtatJournal;
import jo.joide.app.android.atelier.EtatLogcat;
import jo.joide.app.android.logcat.FluxLogcat;
import jo.joide.app.android.atelier.EtatProblemes;
import jo.joide.app.android.ui.atelier.BarreHaut;
import jo.joide.app.android.ui.atelier.BarreOnglets;
import jo.joide.app.android.ui.atelier.BarreSymboles;
import jo.joide.app.android.ui.atelier.FilAriane;
import jo.joide.app.android.ui.atelier.FeuilleBasse;
import jo.joide.app.android.ui.atelier.ListeJournal;
import jo.joide.app.android.ui.atelier.ListeLogcat;
import jo.joide.app.android.ui.atelier.ListeProblemes;
import jo.joide.app.android.ui.atelier.TiroirFichiers;
import jo.joide.app.android.ui.Pilule;
import jo.joide.app.android.ui.PopupAncre;
import jo.joide.app.android.ui.Puce;
import jo.joide.app.android.ui.TiroirPousse;
import jo.joide.app.android.ui.choix.ChoixBouton;
import jo.joide.build.daemon.client.DaemonClient;
import jo.joide.build.daemon.protocol.BuildSpec;
import jo.joide.build.daemon.runner.BuildRunner;
import jo.joide.deps.cache.DependencyCache;
import jo.joide.deps.repo.HttpTransport;
import jo.joide.deps.repo.Repository;
import jo.joide.deps.resolve.ResolverConfig;
import jo.joide.agent.Permissions;
import jo.joide.agent.espace.EspaceCheckpoint;
import jo.joide.ide.agent.EspaceTravailIde;
import jo.joide.ide.apercu.ApercuLayouts;
import jo.joide.ide.apercu.ConfigurationApercu;
import jo.joide.ide.apercu.ConfigurationsApercu;
import jo.joide.ide.editeur.SceneEditeur;
import jo.joide.ide.langages.EtatIndex;
import jo.joide.ide.langages.MoteursLangage;
import jo.joide.ide.sync.AndroidPlatform;
import jo.joide.ide.sync.ExternalResolution;
import jo.joide.ide.sync.LanguageModelSnapshot;
import jo.joide.ide.sync.LanguageModelSync;
import jo.joide.ide.sync.ModuleDirectories;
import jo.joide.ide.sync.ProjetLspAdapter;
import jo.joide.ide.sync.Resolutions;
import jo.joide.model.Workspace;
import jo.joide.model.WorkspaceLoad;
import jo.joide.model.WorkspaceLoader;

/**
 * L'atelier adaptatif (lot J, ADR-É5) : il remplace l'empilement
 * programmatique de P8 — barre supérieure exacte (nom du projet, puce
 * modules, pilule d'état de l'index, ⋮), onglets multi-fichiers à état
 * faible (ADR-051), éditeur celeris dans le puits, tiroir
 * arborescence avec sorties estompées, dock de console (lot K,
 * ADR-É6), FAB Construire qui devient pilule de build, aperçu côte à
 * côte au seuil 720 dp (lot L, ADR-É7). Le retour système ferme les
 * surcouches une à une — jamais un état zombie.
 *
 * <p>La doctrine P8 est reconduite : l'UI reste mince, la logique
 * testable vit dans {@link EtatAtelier}, {@link Arborescence},
 * {@link EtatConsole}, {@link EtatApercu}, {@link FichiersProjet},
 * {@link SpecsBuild} — l'écran monte, traduit et déroule. Les
 * montages des lots B-F (sync, moteurs, éditeur, aperçu, build) sont
 * repris tels quels : seul l'habillage change.</p>
 */
public final class EcranAtelier extends ActiviteJoide {

    /** Extra d'intention : la racine du projet (chemin absolu). */
    public static final String EXTRA_RACINE = "joide.racine";

    /** Extra d'intention : la bannière de compatibilité (optionnel). */
    public static final String EXTRA_BANNIERE = "joide.banniere";

    /**
     * Extra d'intention : le fichier ouvert au montage (optionnel,
     * lot I — l'atelier s'ouvre sur le fichier d'entrée du modèle,
     * jamais un atelier vide).
     */
    public static final String EXTRA_FICHIER = "joide.fichier";

    /**
     * L'attente de la publication du port (lot U/7 : 30 s — le
     * démarrage À FROID du processus :build paie le fork du zygote
     * et le chargement ART de tout le moteur de build ; sur un
     * appareil occupé, 10 s sortaient avant la publication et
     * chaque premier build répétait « injoignable », rapport du
     * 2026-11-17).
     */
    static final long ATTENTE_PORT_MS = 30_000L;

    /**
     * Le pas de re-demande du service pendant l'attente (lot U/7) :
     * un service tué entre-temps repart avec cette demande — jamais
     * une attente muette jusqu'au délai complet.
     */
    static final long REDEMANDE_SERVICE_MS = 5_000L;
    private static final int HAUTEUR_CONSOLE_DP = 300;

    final ExecutorService fond = Executors.newSingleThreadExecutor(
            runnable -> {
                Thread fil = new Thread(runnable, "joide-atelier");
                fil.setDaemon(true);
                return fil;
            });
    private final ExecutorService ecrivain =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread fil = new Thread(runnable, "joide-ecrivain");
                fil.setDaemon(true);
                return fil;
            });
    private final EcrituresFusionnees ecritures =
            new EcrituresFusionnees();
    final AtomicLong numerosBuild = new AtomicLong();
    final AtomicReference<DaemonClient> clientDemon =
            new AtomicReference<>();
    private final Set<String> dossiersOuverts = new LinkedHashSet<>();

    private Graphite.Palette palette;
    EtatAtelier etat;
    final EtatConsole etatConsole = new EtatConsole();
    private final EtatApercu etatApercu = new EtatApercu();

    private TextView nomProjet;
    private Puce puceModules;
    private Pilule piluleIndex;
    VueArbre vueArbre;
    private VueConsole vueConsole;
    private VueApercu vueApercu;
    private FeuilleBasse feuille;
    private Fab fab;
    private TiroirPousse tiroir;
    private FrameLayout puits;
    private EtatVide etatVidePuits;
    private LinearLayout voletDroit;
    private LinearLayout colonneArbre;
    private FrameLayout voletPleinEcran;
    private Icone boutonRailArbre;
    private Icone boutonRailConsole;
    private Icone boutonApercuOnglets;
    private TextView banniereCompat;
    private View vueEditeur;

    // ---- les états purs des sections de la feuille (lot L3, ADR-083)
    private final EtatProblemes etatProblemes = new EtatProblemes();
    private final EtatJournal etatJournal = new EtatJournal();
    private final EtatLogcat etatLogcat = new EtatLogcat();
    private ListeProblemes listeProblemes;
    private ListeJournal listeJournal;
    private ListeLogcat listeLogcat;
    private TiroirFichiers tiroirFichiers;
    private String filtreTiroir = "";

    // ---- les zones migrées en XML + ViewBinding (lot L2, ADR-083) :
    // barre supérieure, onglets, fil d'Ariane, barre de symboles —
    // les vues sont MINCES, l'état reste EtatAtelier ----
    private BarreHaut barreHaut;
    private BarreOnglets barreOnglets;
    private FilAriane filAriane;
    private BarreSymboles barreSymboles;

    /** IDE monté (null tant qu'aucun projet natif n'est chargé). */
    private MoteursLangage moteurs;
    LanguageModelSync sync;
    private SceneEditeur scene;
    private ApercuLayouts apercu;
    private ConfigurationApercu configApercuBase;
    Workspace modele;
    Path racineProjet;
    private String fichierCourant;
    private String fichierInitial;
    private String xmlCourant;
    private List<String> fichiersArbre = List.of();
    private boolean sortiesPresentes;
    volatile boolean buildEnCours;
    /** L6 : l'orchestration du build vit hors de l'écran. */
    private ControleurBuild controleurBuild;
    Reglages.Source sourceReglages;

    // ---- l'agent (lot P, ADR-É11) ----
    private VueAgent vueAgent;
    private TiroirPousse tiroirAgent;
    private Icone boutonAgent;
    private View pointAgent;
    private AgentService.AgentBinder agent;
    private EtatAgentChat etatAgent = new EtatAgentChat();
    private final List<PieceJointe> piecesAgent = new ArrayList<>();

    /** Le message parti avant la liaison du service (fix du
     *  2026-11-17) : un premier message envoyé pendant le bind
     *  ASYNCHRONE se perdait silencieusement — il part dès que le
     *  binder est là, même après un tour en cours. */
    private String messageEnAttenteAgent;

    /** Les pièces du message en attente (mêmes règles que le
     *  composeur : fondues au moment du départ). */
    private final List<PieceJointe> piecesEnAttenteAgent =
            new ArrayList<>();

    /** Le code de requête du choix d'image (SAF). */
    private static final int REQUETE_IMAGE = 47_101;

    /** Le code de requête de la photo (caméra). */
    private static final int REQUETE_PHOTO = 47_102;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // lot U : l'outillage de build vit ICI — le cache dans les
        // fichiers privés (jamais user.home, absent sur Android) et
        // nativeLibraryDir où ART seul exécute les binaires.
        // lot XX : ProprietesConstruction, UNE vérité pour les deux
        // processus (l'écran atelier, le service) — l'APK du fork
        // (registrar Compose dexé) et le jar du plugin extrait en plus
        ProprietesConstruction.poser(this);
        palette = GraphiteVue.palette(this);
        GraphiteVue.fondEcran(this, palette);
        sourceReglages = PreferencesAndroid.source(this);
        boolean grandEcran = EtatAtelier.estGrandEcran(
                getResources().getConfiguration().screenWidthDp);
        etat = new EtatAtelier(grandEcran);
        setContentView(construireUi());
        String racine = getIntent().getStringExtra(EXTRA_RACINE);
        String banniere = getIntent().getStringExtra(EXTRA_BANNIERE);
        if (racine == null || racine.isEmpty()) {
            montrerErreur(getString(R.string.projet_illisible_sans_detail));
            return;
        }
        if (banniere != null && !banniere.isEmpty()) {
            banniereCompat.setText(banniere);
            banniereCompat.setVisibility(View.VISIBLE);
            return;
        }
        etatVidePuits.setTitre(R.string.ide_chargement);
        etatVidePuits.setAide(R.string.atelier_etat_vide_aide);
        fichierInitial = getIntent().getStringExtra(EXTRA_FICHIER);
        fond.execute(() -> charger(Paths.get(racine)));
    }

    // ------------------------------------------------------------------
    // Construction de l'UI
    // ------------------------------------------------------------------

    private View construireUi() {
        ContexteUi ui = new ContexteUi();
        vueArbre = new VueArbre(this);
        vueArbre.setAuditeur(new VueArbre.AuditeurArbre() {
            @Override
            public void surDossier(String chemin, boolean ouvrir) {
                if (ouvrir) {
                    dossiersOuverts.add(chemin);
                } else {
                    dossiersOuverts.remove(chemin);
                }
                majArbre();
            }

            @Override
            public void surFichier(String chemin) {
                ouvrirFichier(chemin, null);
            }

            @Override
            public void surNouveau(boolean fichier) {
                proposerNouveau(fichier);
            }

            @Override
            public void surMenuContextuel(String chemin, boolean dossier) {
                montrerMenuContextuel(chemin, dossier);
            }
        });
        vueConsole = new VueConsole(this);
        vueConsole.setAuditeur(new VueConsole.AuditeurConsole() {
            @Override
            public void surArreter() {
                controleurBuild.arreterBuild();
            }

            @Override
            public void surVider() {
                confirmerVidage();
            }

            @Override
            public void surFiltre(jo.joide.app.android.console.EtatConsole.Filtre filtre) {
                etatConsole.setFiltre(filtre);
                majConsole();
            }

            @Override
            public void surPhase(jo.joide.app.android.ConsoleBuild.Phase phase) {
                etatConsole.setFiltrePhase(phase);
                majConsole();
            }

            @Override
            public void surOuvrir(String cible) {
                ouvrirCible(cible);
            }
        });
        vueApercu = new VueApercu(this);
        vueApercu.setAuditeur(new VueApercu.AuditeurApercu() {
            @Override
            public void surRafraichir() {
                if (apercu != null && FichiersProjet.estMiseEnPage(
                        fichierCourant)) {
                    apercu.rendreSansDelai(scene.texte());
                }
            }

            @Override
            public void surPleinEcran() {
                etat.basculerApercuPleinEcran();
                majVolets();
            }

            @Override
            public void surFermer() {
                etat.fermerVoletApercu();
                majVolets();
            }

            @Override
            public void surDensite(int densite) {
                etatApercu.setDensite(densite);
                reconfigurerApercu();
            }

            @Override
            public void surNuit(boolean nuit) {
                etatApercu.setNuit(nuit);
                reconfigurerApercu();
            }

            @Override
            public void surDimensions(int largeurDp, int hauteurDp) {
                etatApercu.setDimensions(largeurDp, hauteurDp);
                reconfigurerApercu();
            }
        });

        FrameLayout racineVue = new FrameLayout(this);

        LinearLayout colonne = new LinearLayout(this);
        colonne.setOrientation(LinearLayout.VERTICAL);
        colonne.setBackgroundColor(palette.fond);
        racineVue.addView(colonne, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        controleurBuild = new ControleurBuild(this);
        // ---- lot L2 (ADR-083) : la barre supérieure est un layout
        // XML + ViewBinding (atelier_barre_haut) — la vue est mince,
        // les auditeurs restent ici, l'état reste EtatAtelier ----
        barreHaut = new BarreHaut(this);
        nomProjet = barreHaut.binding.nomProjet;
        puceModules = barreHaut.binding.puceModules;
        piluleIndex = barreHaut.binding.piluleIndex;
        boutonAgent = barreHaut.binding.boutonAgent;
        pointAgent = barreHaut.binding.pointAgent;
        barreHaut.binding.btnTiroirFichiers.setOnClickListener(vue -> {
            etat.ouvrirTiroir();
            if (tiroir != null) {
                tiroir.ouvrir();
            }
        });
        piluleIndex.setOnClickListener(vue -> montrerDetailIndex());
        barreHaut.binding.cadreBoutonAgent.setOnClickListener(
                vue -> ouvrirAgent());
        barreHaut.binding.boutonOptions.setOnClickListener(
                vue -> ouvrirMenu());
        colonne.addView(barreHaut, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                GraphiteVue.dp(this, 52)));

        banniereCompat = new TextView(this);
        GraphiteVue.typographie(banniereCompat, Graphite.NOTE);
        banniereCompat.setTextColor(palette.alerte);
        banniereCompat.setBackgroundColor(Graphite.melange(
                palette.alerte, 0.12f, palette.fond));
        int marge = GraphiteVue.dp(this, 14);
        banniereCompat.setPadding(marge, marge / 2, marge, marge / 2);
        banniereCompat.setVisibility(View.GONE);
        colonne.addView(banniereCompat, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- lot L2 (ADR-083) : la bande d'onglets est un layout
        // XML + ViewBinding (atelier_onglets + item_onglet), dessiné
        // depuis le modèle pur ModeleOnglets ----
        barreOnglets = new BarreOnglets(this);
        barreOnglets.setAuditeur(new BarreOnglets.Auditeur() {
            @Override
            public void surActivation(String chemin) {
                etat.activer(chemin);
                montrerFichier(chemin, null);
            }

            @Override
            public void surFermeture(String chemin) {
                fermerOnglet(chemin);
            }

            @Override
            public void surApercu() {
                etat.basculerApercuPleinEcran();
                majVolets();
                if (apercu != null && scene != null
                        && FichiersProjet.estMiseEnPage(fichierCourant)) {
                    apercu.rendreSansDelai(scene.texte());
                }
            }
        });
        boutonApercuOnglets = barreOnglets.binding.boutonApercu;
        if (etat.grandEcran()) {
            boutonApercuOnglets.setVisibility(View.GONE);
        }
        colonne.addView(barreOnglets, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                GraphiteVue.dp(this, 40)));

        // ---- lot L2 (ADR-083) : le fil d'Ariane (28 dp, §3.3) ----
        filAriane = new FilAriane(this);
        colonne.addView(filAriane, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                GraphiteVue.dp(this, 28)));

        FrameLayout corps = new FrameLayout(this);
        if (etat.grandEcran()) {
            corps.addView(ui.rangeeGrandEcran());
        } else {
            corps.addView(ui.puitsCompact());
        }
        voletPleinEcran = new FrameLayout(this);
        voletPleinEcran.setBackgroundColor(palette.fond);
        voletPleinEcran.setVisibility(View.GONE);
        corps.addView(voletPleinEcran, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        colonne.addView(corps, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // ---- lot L2 (ADR-083) : la barre de symboles (42 dp, §3.5)
        // sous le corps — VISIBLE seulement quand le clavier est
        // affiché (détecteur d'insets du cadre) ----
        barreSymboles = new BarreSymboles(this);
        barreSymboles.setAuditeur(new BarreSymboles.Auditeur() {
            @Override
            public void inserer(String texte) {
                insererSymbole(texte);
            }

            @Override
            public void commenterLigne() {
                commenterLigneCourante();
            }

            @Override
            public void dupliquerLigne() {
                dupliquerLigneCourante();
            }
        });
        colonne.addView(barreSymboles, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                GraphiteVue.dp(this, 42)));
        // le détecteur d'insets du CADRE (pas d'AndroidX) : le clavier
        // affiché montre la barre, le cache sinon (§3.5)
        racineVue.setOnApplyWindowInsetsListener((vue, insets) -> {
            android.graphics.Insets ime = insets
                    .getInsets(android.view.WindowInsets.Type.ime());
            boolean clavier = ime.bottom > 0;
            if (barreSymboles != null) {
                barreSymboles.poserVisible(clavier);
            }
            if (fab != null) {
                fab.setVisibility(clavier ? View.GONE
                        : View.VISIBLE);
            }
            return insets;
        });

        // ---- lot L3 (ADR-083) : la feuille basse REMPLACE le Dock
        // au câblage (le retrait de la classe morte reste au lot L6).
        // Le rail, les zones vivantes et les détentes vivent dans la
        // vue ; le contenu CONSOLE reste la VueConsole existante.
        feuille = new FeuilleBasse(this);
        feuille.setAuditeur(new FeuilleBasse.Auditeur() {
            @Override
            public void surOuvrirProbleme(String chemin, int ligne) {
                ouvrirFichier(chemin, ligne);
            }

            @Override
            public void surPipeline() {
                etat.deplierConsole();
                majVolets();
            }

            @Override
            public void surLogcat(boolean actif) {
                // ADR-084 : le relais de journal (brique utilisateur,
                // permission signature) pousse dans FluxLogcat ; la
                // section lit le tampon quand elle est ouverte
                if (!actif) {
                    FluxLogcat.tampon().poserAuditeur(null);
                    return;
                }
                FluxLogcat.tampon().poserAuditeur(auditeurFluxLogcat);
                viderFluxDansEtatLogcat();
            }
        });
        feuille.poserContenu(EtatFeuille.Section.CONSOLE, vueConsole);
        listeProblemes = new ListeProblemes(this);
        listeProblemes.setAuditeur(new ListeProblemes.Auditeur() {
            @Override
            public void surOuvrir(String chemin, int ligne) {
                ouvrirFichier(chemin, ligne);
            }
        });
        feuille.poserContenu(EtatFeuille.Section.PROBLEMES,
                listeProblemes);
        listeJournal = new ListeJournal(this);
        feuille.poserContenu(EtatFeuille.Section.JOURNAL, listeJournal);
        listeLogcat = new ListeLogcat(this);
        feuille.poserContenu(EtatFeuille.Section.LOGCAT, listeLogcat);
        feuille.boutonProblemeSuivant().setOnClickListener(vue -> {
            // §3.6 : le ↓ saute à l'erreur suivante SANS déplier
            EtatProblemes.Probleme suivant = etatProblemes.suivant(
                    fichierCourant == null ? "" : fichierCourant,
                    ligneCourante());
            if (suivant != null) {
                ouvrirFichier(suivant.chemin(), suivant.ligne());
            }
        });
        feuille.cibleProblemes().setOnClickListener(vue -> {
            EtatProblemes.Probleme premier =
                    etatProblemes.premierDuFichier(
                            fichierCourant == null ? "" : fichierCourant);
            if (premier != null) {
                ouvrirFichier(premier.chemin(), premier.ligne());
            }
        });
        colonne.addView(feuille, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        if (!etat.grandEcran()) {
            // lot L4 (ADR-083) : le tiroir porte la vue liée
            // tiroir_fichiers — titre, actions réelles, filtre, arbre
            tiroir = new TiroirPousse(this);
            tiroir.setBordDroit(false);
            tiroirFichiers = new TiroirFichiers(this);
            tiroirFichiers.cadreArbre().addView(vueArbre,
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT));
            tiroirFichiers.setAuditeur(new TiroirFichiers.Auditeur() {
                @Override
                public void surNouveau(boolean fichier) {
                    proposerNouveau(fichier);
                }

                @Override
                public void surToutReduire() {
                    dossiersOuverts.clear();
                    majArbre();
                }

                @Override
                public void surLocaliser() {
                    localiserFichierActif();
                }

                @Override
                public void surFiltre(String texte) {
                    filtreTiroir = texte;
                    majArbre();
                }
            });
            tiroir.setPanneau(tiroirFichiers);
            racineVue.addView(tiroir, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }

        // le tiroir agent (lot P) : poussé depuis la droite, 360 dp,
        // au-dessus de tout — même form factor aux deux tailles (l'
        // amarrage grand écran partagé avec l'aperçu va au backlog)
        vueAgent = new VueAgent(this);
        vueAgent.poserAuditeur(new VueAgent.Auditeur() {
            @Override
            public void envoyer(String message) {
                envoyerAgent(message);
            }

            @Override
            public void arreter() {
                if (agent != null) {
                    agent.arreter();
                }
            }

            @Override
            public void repondrePermission(boolean accordee) {
                repondrePermissionAgent(accordee);
            }

            @Override
            public void repondreQuestion(int index, int choix) {
                repondreQuestionAgent(index, choix);
            }

            @Override
            public void poserMode(jo.joide.agent.Permissions.Mode mode) {
                etatAgent.poserMode(mode);
                if (agent != null) {
                    agent.poserMode(mode);
                }
                vueAgent.rafraichir();
            }

            @Override
            public void ouvrirModele() {
                ouvrirModeleAgent();
            }

            @Override
            public void ouvrirMenuAgent() {
                ouvrirMenuAgentOptions();
            }

            @Override
            public void ouvrirAjouts() {
                ouvrirAjoutsAgent();
            }

            @Override
            public void retirerPiece(int index) {
                if (index >= 0 && index < piecesAgent.size()) {
                    piecesAgent.remove(index);
                    vueAgent.poserPieces(PieceJointe.noms(piecesAgent));
                }
            }
        });
        tiroirAgent = new TiroirPousse(this);
        tiroirAgent.setBordDroit(true);
        tiroirAgent.setPanneau(vueAgent);
        racineVue.addView(tiroirAgent, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        fab = new Fab(this);
        fab.setAuditeur(new Fab.AuditeurFab() {
            @Override
            public void surLancer() {
                controleurBuild.construire();
            }

            @Override
            public void surOuvrirConsole() {
                etat.deplierConsole();
                majVolets();
            }

            @Override
            public void surArreter() {
                controleurBuild.arreterBuild();
            }
        });
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        // §3.7 : bas = 84 dp (feuille repliée 72 + 12)
        fabParams.bottomMargin = GraphiteVue.dp(this, 84);
        fabParams.rightMargin = GraphiteVue.dp(this, 20);
        racineVue.addView(fab, fabParams);
        return racineVue;
    }

    /** Les sous-constructions de l'atelier (méthodes à usage local). */
    private final class ContexteUi {

        private View puitsCompact() {
            puits = cadrePuits();
            return puits;
        }

        private View rangeeGrandEcran() {
            LinearLayout rang = new LinearLayout(EcranAtelier.this);
            rang.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout rail = new LinearLayout(EcranAtelier.this);
            rail.setOrientation(LinearLayout.VERTICAL);
            rail.setBackgroundColor(palette.surface);
            rail.setPadding(0, GraphiteVue.dp(EcranAtelier.this, 10),
                    0, 0);
            boutonRailArbre = new Icone(EcranAtelier.this);
            boutonRailArbre.setGlyphe(Icone.Glyphe.DOSSIER);
            boutonRailArbre.setCouleur(palette.accent);
            boutonRailArbre.setClickable(true);
            boutonRailArbre.setContentDescription(getString(
                    R.string.atelier_desc_fichiers));
            boutonRailArbre.setOnClickListener(vue -> {
                boolean visible = colonneArbre.getVisibility()
                        != View.VISIBLE;
                colonneArbre.setVisibility(
                        visible ? View.VISIBLE : View.GONE);
                boutonRailArbre.setCouleur(
                        visible ? palette.accent
                                : palette.texteSecondaire);
            });
            rail.addView(boutonRailArbre, clicParamsRail());
            boutonRailConsole = new Icone(EcranAtelier.this);
            boutonRailConsole.setGlyphe(Icone.Glyphe.TERMINAL);
            boutonRailConsole.setCouleur(palette.texteSecondaire);
            boutonRailConsole.setClickable(true);
            boutonRailConsole.setContentDescription(getString(
                    R.string.atelier_desc_console));
            boutonRailConsole.setOnClickListener(vue -> {
                etat.basculerConsole();
                majVolets();
            });
            rail.addView(boutonRailConsole, clicParamsRail());
            rang.addView(rail, new LinearLayout.LayoutParams(
                    GraphiteVue.dp(EcranAtelier.this, 48),
                    LinearLayout.LayoutParams.MATCH_PARENT));

            colonneArbre = new LinearLayout(EcranAtelier.this);
            colonneArbre.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bord = new GradientDrawable();
            bord.setColor(palette.surface);
            colonneArbre.setBackground(bord);
            colonneArbre.addView(vueArbre, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
            rang.addView(colonneArbre, new LinearLayout.LayoutParams(
                    GraphiteVue.dp(EcranAtelier.this, 240),
                    LinearLayout.LayoutParams.MATCH_PARENT));

            puits = cadrePuits();
            rang.addView(puits, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f));

            voletDroit = new LinearLayout(EcranAtelier.this);
            voletDroit.setOrientation(LinearLayout.VERTICAL);
            voletDroit.setVisibility(View.GONE);
            rang.addView(voletDroit, new LinearLayout.LayoutParams(
                    GraphiteVue.dp(EcranAtelier.this, 320),
                    LinearLayout.LayoutParams.MATCH_PARENT));
            return rang;
        }

        private FrameLayout cadrePuits() {
            FrameLayout cadre = new FrameLayout(EcranAtelier.this);
            cadre.setBackgroundColor(palette.puits);
            etatVidePuits = new EtatVide(EcranAtelier.this);
            cadre.addView(etatVidePuits, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            return cadre;
        }

        private LinearLayout.LayoutParams clicParams() {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    GraphiteVue.dp(EcranAtelier.this, 42),
                    GraphiteVue.dp(EcranAtelier.this, 42));
            params.leftMargin = params.rightMargin =
                    GraphiteVue.dp(EcranAtelier.this, 4);
            return params;
        }

        private LinearLayout.LayoutParams clicParamsRail() {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    GraphiteVue.dp(EcranAtelier.this, 48),
                    GraphiteVue.dp(EcranAtelier.this, 48));
            return params;
        }

        private LinearLayout.LayoutParams margeGauche() {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.leftMargin = GraphiteVue.dp(EcranAtelier.this, 8);
            return params;
        }
    }

    // ------------------------------------------------------------------
    // Chargement et montage (les lots B-F repris tels quels)
    // ------------------------------------------------------------------

    private void montrerErreur(String message) {
        etatVidePuits.setTitre(R.string.projet_illisible_sans_detail);
        etatVidePuits.setAide(message);
    }

    private void charger(Path racine) {
        WorkspaceLoad charge = WorkspaceLoader.load(racine);
        if (!charge.isUsable()) {
            String detail = charge.errors().isEmpty()
                    ? "?" : charge.errors().get(0).message();
            runOnUiThread(() -> montrerErreur(
                    getString(R.string.projet_illisible, detail)));
            return;
        }
        monterIde(racine, charge);
    }

    private void monterIde(Path racine, WorkspaceLoad charge) {
        Workspace workspace = charge.workspace().orElse(null);
        if (workspace == null) {
            runOnUiThread(() -> montrerErreur(
                    getString(R.string.projet_illisible_sans_detail)));
            return;
        }
        DependencyCache cache;
        try {
            cache = new DependencyCache(
                    Paths.get(getFilesDir().getAbsolutePath(), "m2"));
        } catch (IOException e) {
            runOnUiThread(() -> montrerErreur(
                    getString(R.string.import_echoue,
                            String.valueOf(e.getMessage()))));
            return;
        }
        ResolverConfig config = new ResolverConfig.Builder()
                .repository(new Repository.Remote("central",
                        "https://repo.maven.apache.org/maven2",
                        new HttpTransport()))
                // fix du 2026-11-17 : Google Maven manquait — toute
                // dépendance AndroidX (com.android.*, androidx.*)
                // restait « non résolue, absente du classpath de
                // build » et la compilation échouait ; CodeAssist
                // sert central + google par défaut
                .repository(new Repository.Remote("google",
                        "https://dl.google.com/android/maven2",
                        new HttpTransport()))
                .cache(cache)
                .build();
        LanguageModelSync syncLocal = new LanguageModelSync(racine,
                new ProjetLspAdapter(ModuleDirectories.nested(racine),
                        AndroidPlatform.absent()),
                Resolutions.byResolver(config));
        MoteursLangage moteursLocal = new MoteursLangage(syncLocal,
                Paths.get(getFilesDir().getAbsolutePath(), "cidx"));
        LanguageModelSnapshot instantane =
                moteursLocal.synchroniser(workspace);
        runOnUiThread(() -> monterVues(racine, workspace, syncLocal,
                moteursLocal, instantane));
    }

    private void monterVues(Path racine, Workspace workspace,
                            LanguageModelSync syncLocal,
                            MoteursLangage moteursLocal,
                            LanguageModelSnapshot instantane) {
        if (isFinishing() || isDestroyed()) {
            moteursLocal.close();
            return;
        }
        fermerIde();
        racineProjet = racine;
        modele = workspace;
        sync = syncLocal;
        moteurs = moteursLocal;
        etat.setProjetMonte(true);
        journaliser(EtatJournal.Source.INDEX,
                EtatJournal.Niveau.INFO,
                workspace.allModules().size() + " modules · "
                        + fichiersArbre.size() + " fichiers");
        nomProjet.setText(String.valueOf(racine.getFileName()));
        int modules = workspace.allModules().size();
        puceModules.setText(modules == 1
                ? getString(R.string.meta_module_1, modules)
                : getString(R.string.meta_modules, modules));

        apercu = new ApercuLayouts(this);
        configApercuBase = ConfigurationsApercu.depuisSnapshot(instantane);
        etatApercu.surAvertissementsConfig(
                configApercuBase.avertissements());
        reconfigurerApercu();
        apercu.poserEcouteur(new ApercuLayouts.EcouteurRendu() {
            @Override
            public void onSucces(View rendu, long dureeMs, int vues,
                                 int largeur, int hauteur) {
                poserRendu(rendu);
                etatApercu.surSucces(vues, dureeMs, largeur, hauteur);
                etatApercu.surMockViews(compterMockViews(rendu));
                etatApercu.surRessourcesManquantes(
                        apercu.ressourcesManquantes(xmlCourant));
                vueApercu.maj(etatApercu, Arborescence.nomAffiche(
                        fichierCourant));
            }

            @Override
            public void onErreur(String message, Throwable cause) {
                etatApercu.surErreur(message);
                vueApercu.maj(etatApercu, Arborescence.nomAffiche(
                        fichierCourant));
            }

            @Override
            public void onXmlInvalide(String message) {
                etatApercu.surXmlInvalide();
                vueApercu.maj(etatApercu, Arborescence.nomAffiche(
                        fichierCourant));
            }
        });

        scene = new SceneEditeur(this, moteurs);
        // lot L3 (ADR-083) : les diagnostics BRUTS alimentent la
        // section Problèmes (la source LSP de la feuille)
        scene.poserAuditeurDiagnosticsEcran(
                this::surDiagnosticsLangage);
        // lot M (ADR-É8) : les réglages d'éditeur s'appliquent à
        // l'OUVERTURE suivante — la session se monte avec eux
        Reglages reglagesEditeur = Reglages.lire(sourceReglages);
        scene.taillePolice((float) reglagesEditeur.tailleCode);
        scene.numerosLignes(reglagesEditeur.numerosLignes);
        scene.retourLigne(reglagesEditeur.retourLigne);
        scene.theme(Graphite.estSombre(palette));
        scene.poserAuditeurEdition(texte -> {
            if (fichierCourant == null) {
                return;
            }
            ecritures.poser(fichierCourant, texte);
            etat.marquerModifie(fichierCourant);
            majOnglets();
            if (FichiersProjet.estMiseEnPage(fichierCourant)) {
                xmlCourant = texte;
                apercu.rendre(texte);
            }
        });
        vueEditeur = new FrameLayout(this);
        ((FrameLayout) vueEditeur).addView(scene.vue(),
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        ((FrameLayout) vueEditeur).addView(scene.couchePopups(),
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        puits.addView(vueEditeur, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        etatVidePuits.setTitre(R.string.atelier_etat_vide_titre);
        etatVidePuits.setAide(R.string.atelier_etat_vide_aide);
        majPiluleIndex();
        rechargerArbre();
        if (fichierInitial != null && !fichierInitial.isEmpty()) {
            // lot I : l'atelier s'ouvre sur le fichier d'entrée du
            // modèle, jamais un atelier vide (ADR-É4)
            ouvrirFichier(fichierInitial, null);
            fichierInitial = null;
        }
    }

    private void ecrireEnAttente() {
        java.util.Map<String, String> aEcrire = ecritures.drainer();
        for (java.util.Map.Entry<String, String> entree
                : aEcrire.entrySet()) {
            try {
                Files.write(racineProjet.resolve(entree.getKey()),
                        entree.getValue().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                runOnUiThread(() -> Dialogue.information(
                        EcranAtelier.this, palette,
                        getString(R.string.import_echoue_titre),
                        getString(R.string.ecriture_echouee),
                        R.string.action_ok));
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Onglets et fichiers
    // ------------------------------------------------------------------

    private void ouvrirFichier(String relatif, Integer ligne) {
        if (racineProjet == null) {
            return;
        }
        EtatAtelier.Ouverture ouverture = etat.ouvrir(relatif);
        if (!ouverture.faite()) {
            Dialogue.information(this, palette,
                    getString(R.string.atelier_etat_vide_titre),
                    getString(R.string.atelier_onglets_plafond,
                            EtatAtelier.ONGLETS_MAX),
                    R.string.action_ok);
            return;
        }
        if (ouverture.evince() != null) {
            majOnglets();
        }
        montrerFichier(relatif, ligne);
    }

    private void montrerFichier(String relatif, Integer ligne) {
        if (racineProjet == null || scene == null) {
            return;
        }
        fond.execute(() -> {
            String contenu;
            try {
                contenu = FichiersProjet.lire(racineProjet, relatif);
            } catch (IOException e) {
                runOnUiThread(() -> Dialogue.information(
                        EcranAtelier.this, palette,
                        getString(R.string.import_echoue_titre),
                        getString(R.string.lecture_echouee, relatif),
                        R.string.action_ok));
                return;
            }
            runOnUiThread(() -> {
                fichierCourant = relatif;
                dossiersOuverts.addAll(Arborescence.parentsDe(relatif));
                String uri = racineProjet.resolve(relatif).toString();
                scene.ouvrir(uri, Arborescence.nomAffiche(relatif),
                        contenu);
                majPuits();
                boolean miseEnPage = FichiersProjet.estMiseEnPage(relatif);
                etat.surActifEstMiseEnPage(miseEnPage);
                if (miseEnPage) {
                    xmlCourant = contenu;
                    etatApercu.setTheme(configApercuBase.nomTheme());
                    reconfigurerApercu();
                    apercu.rendreSansDelai(contenu);
                }
                majOnglets();
                majVolets();
                majArbre();
                if (ligne != null) {
                    scene.allerALigne(ligne);
                }
            });
        });
    }

    private void fermerOnglet(String relatif) {
        if (!etat.fermer(relatif)) {
            return;
        }
        ecritures.drainer();
        fond.execute(this::ecrireEnAttente);
        String nouveau = etat.actif();
        if (nouveau != null) {
            montrerFichier(nouveau, null);
        } else {
            fichierCourant = null;
            xmlCourant = null;
            etat.fermerVoletApercu();
            majOnglets();
            majVolets();
            majArbre();
        }
    }

    /** Lot L2 (ADR-083) — commenter la ligne courante : « // » en
     *  tête de ligne, via l'InputConnection réelle de la vue éditeur
     *  (batch unique, la position du curseur reprise après). */
    private void commenterLigneCourante() {
        android.view.inputmethod.InputConnection connexion =
                connexionEditeur();
        if (connexion == null) {
            return;
        }
        CharSequence avantBrut = connexion
                .getTextBeforeCursor(8000, 0);
        String avant = avantBrut == null ? "" : avantBrut.toString();
        int debutLigne = avant.lastIndexOf("\n") + 1;
        connexion.beginBatchEdit();
        connexion.setSelection(debutLigne, debutLigne);
        connexion.commitText("// ", 1);
        connexion.endBatchEdit();
    }

    /** Dupliquer la ligne courante sous elle-même. */
    private void dupliquerLigneCourante() {
        android.view.inputmethod.InputConnection connexion =
                connexionEditeur();
        if (connexion == null) {
            return;
        }
        CharSequence avantBrut = connexion
                .getTextBeforeCursor(8000, 0);
        CharSequence apresBrut = connexion
                .getTextAfterCursor(8000, 0);
        if (avantBrut == null || apresBrut == null) {
            return;
        }
        String avant = avantBrut.toString();
        String apres = apresBrut.toString();
        int debutLigne = avant.lastIndexOf("\n") + 1;
        String ligne = avant.substring(debutLigne)
                + ligneApres(avant.length() - debutLigne, apres);
        connexion.beginBatchEdit();
        connexion.commitText("\n" + ligne, 1);
        connexion.endBatchEdit();
    }

    /** La suite de la ligne courante (jusqu'au premier retour). */
    private String ligneApres(int dejaLue, CharSequence apres) {
        String suite = apres.toString();
        int finLigne = suite.indexOf("\n");
        return finLigne < 0 ? suite : suite.substring(0, finLigne);
    }

    /** L'InputConnection RÉELLE de la vue éditeur (la fabrique de la
     *  vue est légitime : la connexion est liée à la session). */
    private android.view.inputmethod.InputConnection connexionEditeur() {
        if (scene == null) {
            return null;
        }
        android.view.View editeur = scene.vue();
        return editeur.onCreateInputConnection(
                new android.view.inputmethod.EditorInfo());
    }

    /** L'insertion d'une touche symbole (même voie). */
    private void insererSymbole(String texte) {
        android.view.inputmethod.InputConnection connexion =
                connexionEditeur();
        if (connexion == null) {
            return;
        }
        connexion.commitText(texte, 1);
    }

    /** Lot L2 (ADR-083) — le fil d'Ariane suit le fichier courant
     *  (§3.3 : chemin + symbole englobant du moteur de langage ; le
     *  symbole arrive au lot L3 avec la source LSP — absent, la
     *  classe termine le fil, franchement). */
    /** Lot L3 (ADR-083) — le journal de l'IDE reçoit l'événement et
     *  la zone vivante suit (source accent, MCP alerte, erreur rouge). */
    private void journaliser(EtatJournal.Source source,
                             EtatJournal.Niveau niveau,
                             String message) {
        etatJournal.ajouter(source, niveau, message);
        if (listeJournal != null) {
            listeJournal.maj(etatJournal);
        }
        EtatJournal.Evenement dernier = etatJournal.dernier();
        if (feuille != null && dernier != null) {
            int couleur = dernier.niveau() == EtatJournal.Niveau.ERREUR
                    ? palette.erreur : palette.accent;
            feuille.poserVerdictJournal("● " + dernier.source()
                    + " · " + message.toLowerCase(java.util.Locale
                    .FRENCH), dernier.heure() + " · "
                    + etatJournal.taille() + " événements");
        }
    }

    /** La ligne courante du curseur (le saut ↓ part d'ici) — 1 si
     *  la session n'est pas lisible. */
    private int ligneCourante() {
        if (scene == null) {
            return 1;
        }
        android.view.View editeur = scene.vue();
        android.view.inputmethod.EditorInfo infos =
                new android.view.inputmethod.EditorInfo();
        android.view.inputmethod.InputConnection connexion =
                editeur.onCreateInputConnection(infos);
        if (connexion == null) {
            return 1;
        }
        CharSequence avantBrut = connexion
                .getTextBeforeCursor(8000, 0);
        if (avantBrut == null) {
            return 1;
        }
        int lignes = 1;
        for (int i = 0; i < avantBrut.length(); i++) {
            if (avantBrut.charAt(i) == '\n') {
                lignes++;
            }
        }
        return lignes;
    }

    /** L'écran alimente EtatProblemes depuis les diagnostics de la
     *  scène (la source LSP de la section, §3.6) et redessine. */
    private void surDiagnosticsLangage(String uri,
            List<SceneEditeur.ProblemeEcran> bruts) {
        String chemin = uri;
        String prefixe = "file://";
        if (chemin.startsWith(prefixe)) {
            chemin = chemin.substring(prefixe.length());
        }
        List<EtatProblemes.Probleme> nouveaux = new ArrayList<>();
        for (SceneEditeur.ProblemeEcran brut : bruts) {
            nouveaux.add(new EtatProblemes.Probleme(chemin,
                    Arborescence.nomAffiche(chemin),
                    brut.ligne(), brut.message(), brut.erreur()));
        }
        etatProblemes.poserFichier(chemin,
                Arborescence.nomAffiche(chemin), nouveaux);
        if (listeProblemes != null) {
            listeProblemes.maj(etatProblemes);
        }
        feuille.poserBadgeProblemes(etatProblemes.erreurs());
        majVerdictProblemes();
    }

    /** La zone vivante des problèmes (§3.6 : ✕ n ▲ n ou « Aucun
     *  problème » / première erreur du fichier actif). */
    private void majVerdictProblemes() {
        if (feuille == null) {
            return;
        }
        int erreurs = etatProblemes.erreurs();
        int avertissements = etatProblemes.avertissements();
        String verdict;
        int couleur;
        if (erreurs == 0 && avertissements == 0) {
            verdict = getString(R.string.atelier_aucun_probleme);
            couleur = palette.reussite;
        } else {
            verdict = "✕ " + erreurs + "  ▲ " + avertissements;
            couleur = erreurs > 0 ? palette.erreur : palette.alerte;
        }
        EtatProblemes.Probleme premier = etatProblemes
                .premierDuFichier(fichierCourant == null ? ""
                        : fichierCourant);
        String detail = premier == null
                ? getString(R.string.atelier_aucun_probleme)
                : Arborescence.nomAffiche(premier.chemin()) + ":"
                + premier.ligne() + " · " + premier.message();
        feuille.poserVerdictProblemes(verdict, detail);
    }

    /**
     * ADR-084 — l'auditeur du flux du relais : les lignes déposées
     * depuis le fil Binder passent sur le fil UI (post) avant toute
     * manipulation de vue.
     */
    private final FluxLogcat.Auditeur auditeurFluxLogcat =
            new FluxLogcat.Auditeur() {
                @Override
                public void surLignes() {
                    runOnUiThread(EcranAtelier.this::lireFluxRelais);
                }
            };

    /** Lit le tampon du relais et alimente l'état de la section. */
    private void lireFluxRelais() {
        for (FluxLogcat.LigneRelais ligne
                : FluxLogcat.tampon().lire()) {
            etatLogcat.recevoir(ligne.niveau(), ligne.tag(),
                    ligne.message(), System.currentTimeMillis());
        }
        if (listeLogcat != null) {
            listeLogcat.maj(etatLogcat);
        }
        majVerdictLogcat();
    }

    /** Au branchement (ouverture de la section) : ce qui est déjà en
     *  tampon entre dans l'état, puis le verdict suit. */
    private void viderFluxDansEtatLogcat() {
        runOnUiThread(this::lireFluxRelais);
    }

    /** La zone vivante du logcat (§3.6) : flux vivant ou état vide
     *  honnête, dernière ligne colorée par niveau, histogramme. */
    private void majVerdictLogcat() {
        if (feuille == null) {
            return;
        }
        EtatLogcat.Ligne derniere = etatLogcat.derniere();
        if (derniere == null) {
            feuille.poserVerdictLogcat(
                    getString(R.string.atelier_logcat_inactif),
                    palette.texteSecondaire,
                    getString(R.string.atelier_logcat_relais),
                    palette.texteEstompe,
                    etatLogcat.histogramme(System.currentTimeMillis()));
            return;
        }
        int couleur = switch (derniere.niveau()) {
            case E -> palette.erreur;
            case W -> palette.alerte;
            case D -> palette.reussite;
            default -> palette.texte;
        };
        feuille.poserVerdictLogcat(
                getString(R.string.atelier_logcat_vivant, derniere.tag()),
                couleur,
                derniere.message(), couleur,
                etatLogcat.histogramme(System.currentTimeMillis()));
    }

    private void majFilAriane() {
        if (filAriane == null || fichierCourant == null) {
            return;
        }
        String relatif = fichierCourant;
        String prefixe = racineProjet == null ? ""
                : racineProjet.toString();
        if (relatif.startsWith(prefixe)) {
            relatif = relatif.substring(prefixe.length());
            while (relatif.startsWith("/")) {
                relatif = relatif.substring(1);
            }
        }
        filAriane.poser("", "", "", "");
        var segments = jo.joide.app.android.atelier
                .FormateurFilAriane.segmentsDepuisChemin(relatif, null);
        StringBuilder module = new StringBuilder();
        StringBuilder paquet = new StringBuilder();
        StringBuilder classe = new StringBuilder();
        if (!segments.isEmpty()) {
            module.append(segments.get(0).texte());
        }
        for (int i = 1; i < segments.size(); i++) {
            if (i == segments.size() - 1) {
                classe.append(segments.get(i).texte());
            } else {
                if (paquet.length() > 0) {
                    paquet.append('.');
                }
                paquet.append(segments.get(i).texte());
            }
        }
        filAriane.poser(module.toString(), paquet.toString(),
                classe.toString(), null);
    }

    private void majOnglets() {
        if (barreOnglets == null) {
            return;
        }
        // lot L2 (ADR-083) : le modèle PUR porte l'état des onglets
        // (pastilles de type, points erreur/modifié), la vue liée le
        // dessine — plus de widget propriétaire
        ModeleOnglets modele = new ModeleOnglets();
        for (String chemin : etat.onglets()) {
            modele.ouvrir(chemin, Arborescence.nomAffiche(chemin),
                    etat.estModifie(chemin), false);
        }
        modele.activer(etat.actif());
        barreOnglets.poser(modele);
        // le bouton d'aperçu : onglet actif = mise en page (ADR-É7)
        if (boutonApercuOnglets != null && !etat.grandEcran()) {
            boutonApercuOnglets.setVisibility(
                    FichiersProjet.estMiseEnPage(fichierCourant)
                            ? View.VISIBLE : View.GONE);
        }
        majFilAriane();
    }

    private void ouvrirCible(String cible) {
        int separation = cible.lastIndexOf(':');
        if (separation <= 0) {
            return;
        }
        String chemin = cible.substring(0, separation);
        int ligne;
        try {
            ligne = Integer.parseInt(cible.substring(separation + 1));
        } catch (NumberFormatException e) {
            ligne = 1;
        }
        String prefixe = racineProjet.toString();
        if (chemin.startsWith(prefixe)) {
            chemin = chemin.substring(prefixe.length());
            while (chemin.startsWith("/")) {
                chemin = chemin.substring(1);
            }
        }
        if (!FichiersProjet.ouvrable(Arborescence.nomAffiche(chemin))
                || !java.util.Arrays.asList(fichiersArbre.toArray())
                        .contains(chemin)) {
            Dialogue.information(this, palette,
                    getString(R.string.console_probleme_ouvrir),
                    getString(R.string.console_cible_introuvable, chemin),
                    R.string.action_ok);
            return;
        }
        ouvrirFichier(chemin, ligne);
    }

    // ------------------------------------------------------------------
    // Arborescence et nouveaux fichiers
    // ------------------------------------------------------------------

    void rechargerArbre() {
        if (racineProjet == null) {
            return;
        }
        fond.execute(() -> {
            try {
                List<String> fichiers = FichiersProjet.lister(racineProjet);
                boolean sorties = Files.isDirectory(
                        racineProjet.resolve(Arborescence.NOM_SORTIES));
                runOnUiThread(() -> {
                    fichiersArbre = fichiers;
                    sortiesPresentes = sorties;
                    majArbre();
                });
            } catch (IOException e) {
                // l'arbre reste l'état précédent : jamais un arbre vide
                // muet — le prochain accès le rechargera
            }
        });
    }

    void majArbre() {
        if (vueArbre == null || racineProjet == null) {
            return;
        }
        vueArbre.setSortiesEnCours(buildEnCours);
        List<String> retenus = Arborescence.cheminsFiltres(fichiersArbre,
                filtreTiroir);
        vueArbre.setArbre(Arborescence.composer(retenus,
                String.valueOf(racineProjet.getFileName()),
                filtreTiroir.isBlank() ? dossiersOuverts
                        : Arborescence.ancetresDe(retenus),
                etat.actif(), sortiesPresentes),
                String.valueOf(racineProjet.getFileName()));
    }

    /**
     * Lot L4 (ADR-083) — le menu contextuel de l'arbre (§3.8,
     * appui long) : des VERBES RÉELS — nouveau fichier/dossier ici,
     * renommer (déplacement), dupliquer (copie « (copie) »),
     * supprimer (confirmation, l'onglet ouvert se ferme). Franchise :
     * jamais un item mort.
     */
    private void montrerMenuContextuel(String chemin, boolean dossier) {
        java.util.List<String> verbes = new ArrayList<>();
        java.util.List<Runnable> actions = new ArrayList<>();
        verbes.add(getString(R.string.atelier_nouveau_fichier));
        actions.add(() -> proposerNouveau(true));
        verbes.add(getString(R.string.atelier_nouveau_dossier));
        actions.add(() -> proposerNouveau(false));
        if (!dossier) {
            verbes.add(getString(R.string.atelier_dupliquer));
            actions.add(() -> dupliquerEntree(chemin));
            verbes.add(getString(R.string.atelier_renommer));
            actions.add(() -> renommerEntree(chemin));
        }
        verbes.add(getString(R.string.atelier_supprimer));
        actions.add(() -> supprimerEntree(chemin, dossier));
        LinearLayout contenu = new LinearLayout(this);
        contenu.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < verbes.size(); i++) {
            String verbe = verbes.get(i);
            Runnable action = actions.get(i);
            View ligne = PopupAncre.ligneMenu(this, palette, verbe,
                    () -> {
                        fenetreMenuArbre.dismiss();
                        action.run();
                    });
            contenu.addView(ligne);
        }
        View vueArbreAncre = getWindow().getDecorView()
                .findViewById(android.R.id.content);
        fenetreMenuArbre = PopupAncre.montrer(vueArbre != null
                ? vueArbre : vueArbreAncre, palette, contenu,
                GraphiteVue.dp(this, 220));
    }

    /** Duplique un fichier du projet : la copie vit à côté. */
    private void dupliquerEntree(String chemin) {
        try {
            java.nio.file.Path source = java.nio.file.Path.of(chemin);
            String nom = source.getFileName().toString();
            int point = nom.lastIndexOf('.');
            String copie = point < 0 ? nom + " (copie)"
                    : nom.substring(0, point) + " (copie)"
                    + nom.substring(point);
            java.nio.file.Path cible = source.resolveSibling(copie);
            java.nio.file.Files.copy(source, cible,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            rechargerArbre();
        } catch (java.io.IOException e) {
            Dialogue.information(this, palette,
                    getString(R.string.atelier_dupliquer),
                    String.valueOf(e.getMessage()), R.string.action_ok);
        }
    }

    /** Renomme un fichier du projet (déplacement au même dossier) :
     *  la saisie suit le même dialogue que la création, la validation
     *  NouveauNom incluse. */
    private void renommerEntree(String chemin) {
        android.widget.EditText saisie = new android.widget.EditText(this);
        saisie.setHint(R.string.atelier_renommer);
        Dialogue.saisie(this, palette,
                getString(R.string.atelier_renommer),
                saisie, R.string.action_annuler, R.string.action_ok,
                (dialog, lequel) -> {
                    if (lequel != DialogInterface.BUTTON_POSITIVE) {
                        return;
                    }
                    String nom = saisie.getText().toString().trim();
                    NouveauNom.Raison raison = NouveauNom.valider(nom);
                    if (raison != null) {
                        Dialogue.information(this, palette,
                                getString(R.string.atelier_renommer),
                                texteRaisonNom(raison),
                                R.string.action_ok);
                        return;
                    }
                    try {
                        java.nio.file.Path source =
                                java.nio.file.Path.of(chemin);
                        java.nio.file.Files.move(source,
                                source.resolveSibling(nom),
                                java.nio.file.StandardCopyOption
                                        .ATOMIC_MOVE);
                        rechargerArbre();
                    } catch (java.io.IOException e) {
                        Dialogue.information(this, palette,
                                getString(R.string.atelier_renommer),
                                String.valueOf(e.getMessage()),
                                R.string.action_ok);
                    }
                });
    }

    /** Supprime (confirmation ; l'onglet ouvert se ferme, §3.8). */
    private void supprimerEntree(String chemin, boolean dossier) {
        Dialogue.confirmation(this, palette,
                getString(R.string.atelier_supprimer), chemin,
                R.string.action_annuler, R.string.action_ok,
                (dialog, lequel) -> {
                    try {
                        java.nio.file.Path cible =
                                java.nio.file.Path.of(chemin);
                        if (dossier) {
                            supprimerRecursivement(cible);
                        } else {
                            java.nio.file.Files.deleteIfExists(cible);
                        }
                        if (fichierCourant != null
                                && fichierCourant.startsWith(chemin)) {
                            fermerOnglet(fichierCourant);
                        }
                        rechargerArbre();
                    } catch (java.io.IOException e) {
                        Dialogue.information(this, palette,
                                getString(R.string.atelier_supprimer),
                                String.valueOf(e.getMessage()),
                                R.string.action_ok);
                    }
                });
    }

    /**
     * Lot L4 (ADR-083) — « localiser le fichier actif » (§3.8) : les
     * dossiers ancêtres s'ouvrent et l'arbre se redessine avec le
     * filtre levé (on cherche à VOIR le fichier, pas à filtrer).
     */
    private void localiserFichierActif() {
        if (fichierCourant == null) {
            return;
        }
        String relatif = fichierCourant;
        String prefixe = racineProjet == null ? ""
                : racineProjet.toString();
        if (relatif.startsWith(prefixe)) {
            relatif = relatif.substring(prefixe.length());
            while (relatif.startsWith("/")) {
                relatif = relatif.substring(1);
            }
        }
        filtreTiroir = "";
        if (tiroirFichiers != null) {
            tiroirFichiers.champFiltre().setText("");
        }
        dossiersOuverts.addAll(
                Arborescence.ancetresDe(List.of(relatif)));
        majArbre();
    }

    /** La suppression récursive d'un dossier du projet. */
    private static void supprimerRecursivement(java.nio.file.Path racine)
            throws java.io.IOException {
        try (var chemins = java.nio.file.Files.walk(racine)) {
            chemins.sorted(java.util.Comparator.reverseOrder())
                    .forEach(chemin -> {
                        try {
                            java.nio.file.Files.deleteIfExists(chemin);
                        } catch (java.io.IOException ignore) {
                            // au mieux : le suivant continue
                        }
                    });
        }
    }

    /** La fenêtre du menu contextuel de l'arbre (fermée sur action). */
    private android.widget.PopupWindow fenetreMenuArbre;

    private void proposerNouveau(boolean fichier) {
        String dossier = etat.actif() == null
                ? "" : Arborescence.dossierDe(etat.actif());
        String affichage = dossier.isEmpty()
                ? String.valueOf(racineProjet.getFileName()) : dossier;
        android.widget.EditText saisie = new android.widget.EditText(this);
        saisie.setHint(fichier
                ? R.string.atelier_nouveau_fichier
                : R.string.atelier_nouveau_dossier);
        Dialogue.saisie(this, palette,
                getString(R.string.atelier_nouveau_dans, affichage),
                saisie, R.string.action_annuler, R.string.action_ok,
                (dialog, lequel) -> {
                    if (lequel == DialogInterface.BUTTON_POSITIVE) {
                        creerEntree(saisie.getText().toString().trim(),
                                fichier, dossier);
                    }
                });
    }

    private void creerEntree(String nom, boolean fichier, String dossier) {
        NouveauNom.Raison raison = NouveauNom.valider(nom);
        if (raison != null) {
            Dialogue.information(this, palette,
                    getString(R.string.atelier_nouveau),
                    texteRaisonNom(raison), R.string.action_ok);
            return;
        }
        Path cible = racineProjet.resolve(
                dossier.isEmpty() ? nom : dossier + nom);
        if (Files.exists(cible)) {
            Dialogue.information(this, palette,
                    getString(R.string.atelier_nouveau),
                    getString(R.string.atelier_existe_deja, nom),
                    R.string.action_ok);
            return;
        }
        try {
            if (fichier) {
                Files.createDirectories(cible.getParent());
                Files.write(cible, new byte[0]);
            } else {
                Files.createDirectories(cible);
            }
        } catch (IOException e) {
            Dialogue.information(this, palette,
                    getString(R.string.atelier_nouveau),
                    getString(R.string.atelier_creation_echouee,
                            String.valueOf(e.getMessage())),
                    R.string.action_ok);
            return;
        }
        rechargerArbre();
        if (fichier) {
            String relatif = racineProjet.relativize(cible)
                    .toString().replace('\\', '/');
            ouvrirFichier(relatif, null);
        }
    }

    private String texteRaisonNom(NouveauNom.Raison raison) {
        return switch (raison) {
            case VIDE -> getString(R.string.atelier_nom_err_vide);
            case TROP_LONG -> getString(R.string.atelier_nom_err_long);
            case SEPARATEUR -> getString(
                    R.string.atelier_nom_err_separateur);
            case POINT -> getString(R.string.atelier_nom_err_point);
            case CONTROLE -> getString(R.string.atelier_nom_err_controle);
            case POINT_FINAL -> getString(
                    R.string.atelier_nom_err_point_final);
        };
    }

    // ------------------------------------------------------------------
    // Index de noms (pilule honnête)
    // ------------------------------------------------------------------

    private void majPiluleIndex() {
        if (piluleIndex == null || moteurs == null) {
            return;
        }
        EtatIndex index = moteurs.etatIndex();
        piluleIndex.setEtat(getString(R.string.atelier_index,
                        index.phase()), Pilule.Role.NEUTRE);
    }

    private void montrerDetailIndex() {
        if (moteurs == null) {
            return;
        }
        EtatIndex index = moteurs.etatIndex();
        majPiluleIndex();
        Dialogue.information(this, palette,
                getString(R.string.atelier_index, index.phase()),
                getString(R.string.atelier_index_detail, index.phase(),
                        index.total(), index.faits(),
                        index.avertissements().size(),
                        index.erreurs().size()),
                R.string.action_ok);
    }

    private void ouvrirMenu() {
        // lot M (ADR-É8) : la case « en processus » du lot F vit
        // désormais dans Paramètres — ici il reste fermer le projet
        // et le chemin vers les réglages
        String fermer = getString(R.string.atelier_fermer_projet);
        Dialogue.menu(this, palette,
                racineProjet == null ? ""
                        : String.valueOf(racineProjet.getFileName()),
                new CharSequence[]{
                        getString(R.string.agent_demander_ia),
                        getString(R.string.parametres_titre), fermer},
                (dialog, lequel) -> {
                    if (lequel == 0) {
                        demanderIa();
                    } else if (lequel == 1) {
                        startActivity(new Intent(this,
                                jo.joide.app.android.ecrans.parametres
                                        .EcranParametres.class));
                    } else if (lequel == 2) {
                        demanderFermeture();
                    }
                });
    }

    // ------------------------------------------------------------------
    // Retour système : les surcouches une à une (ADR-É5)
    // ------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // le tiroir agent est la surcouche la plus récente : le
        // retour la ferme d'abord (ADR-É11 point 1, ordre ADR-É5)
        if (tiroirAgent != null && tiroirAgent.ouvert()) {
            fermerAgent();
            return;
        }
        switch (etat.surRetour()) {
            case FERMER_CONSOLE -> {
                etat.replierConsole();
                majVolets();
            }
            case FERMER_TIROIR -> {
                etat.fermerTiroir();
                if (tiroir != null) {
                    tiroir.fermer();
                }
            }
            case FERMER_APERCU -> {
                etat.fermerApercuPleinEcran();
                majVolets();
            }
            case DEMANDER_FERMETURE -> demanderFermeture();
            case QUITTER -> finish();
            default -> finish();
        }
    }

    private void demanderFermeture() {
        Dialogue.confirmation(this, palette,
                getString(R.string.atelier_fermer_titre),
                getString(R.string.atelier_fermer_message,
                        etat.nombreModifies()),
                R.string.action_annuler,
                R.string.atelier_fermer_projet,
                (dialog, lequel) -> finish());
    }

    // ------------------------------------------------------------------
    // L'agent (lot P, ADR-É11) : le tiroir, le service, les deux
    // blocages honnêtes, l'entrée « Demander à l'IA »
    // ------------------------------------------------------------------

    /** Ouvre le tiroir agent et lie le service de premier plan. */
    private void ouvrirAgent() {
        if (tiroirAgent == null) {
            return;
        }
        tiroirAgent.ouvrir();
        if (agent != null) {
            brancherEspaceAgent();
            rejouerJournalAgent();
            return;
        }
        AgentService.demarrer(this);
        bindService(new Intent(this, AgentService.class),
                connexionAgent(), Context.BIND_AUTO_CREATE);
    }

    private void fermerAgent() {
        if (tiroirAgent != null) {
            tiroirAgent.fermer();
        }
    }

    private android.content.ServiceConnection connexionAgent() {
        return new android.content.ServiceConnection() {
            @Override
            public void onServiceConnected(
                    android.content.ComponentName nom,
                    android.os.IBinder service) {
                agent = (AgentService.AgentBinder) service;
                brancherEspaceAgent();
                agent.poserEcouteur(VueAgentEntree -> runOnUiThread(
                        () -> surEntreeAgent(VueAgentEntree)));
                rejouerJournalAgent();
                // le message parti pendant le bind s'en va MAINTENANT
                runOnUiThread(() -> depecherAttenteAgent());
            }

            @Override
            public void onServiceDisconnected(
                    android.content.ComponentName nom) {
                agent = null;
            }
        };
    }

    /** Construit l'espace de travail du projet pour l'agent. */
    private void brancherEspaceAgent() {
        if (agent == null || racineProjet == null || scene == null) {
            return;
        }
        EspaceTravailIde espace = new EspaceTravailIde(racineProjet,
                modele).poserMoteurs(moteurs);
        espace.poserOverlayEditeur(chemin -> {
            String actif = etat.actif();
            if (actif != null && actif.equals(chemin)) {
                return scene.texte();
            }
            return null;
        });
        agent.poserEspace(new EspaceCheckpoint(espace));
        Reglages reglages = Reglages.lire(sourceReglages);
        agent.poserModele(reglages.fournisseurIa, reglages.modeleIa);
        // le .mcp.json du projet, lu borné, part au service : les
        // serveurs déclarés sont parsés, pas activés (ADR-063)
        agent.poserMcpJson(lireMcpJson());
        // l'approbation PERSISTÉE par projet (lot T, ADR-065) : liée
        // à la signature du fichier qui l'a motivée — le même
        // .mcp.json retrouve ses serveurs, un fichier changé redemande
        // le geste explicite de l'utilisateur
        if ("1".equals(sourceReglages.lire(cleApprobationMcp()))) {
            agent.poserApprobationMcp(true);
        }
        etatAgent.poserModele(reglages.modeleIa);
        etatAgent.poserMode(EtatAgentChat.TourEtat.EN_COURS
                == etatAgent.etat()
                ? etatAgent.mode() : Permissions.Mode.DEMANDE_CHAQUE);
    }

    /** Rejoue le journal du service dans l'état pur (retour d'écran). */
    private void rejouerJournalAgent() {
        if (agent == null) {
            return;
        }
        etatAgent = new EtatAgentChat();
        for (AgentService.Entree entree : agent.journal()) {
            if (entree.estMessage()) {
                etatAgent.envoyer(entree.message, entree.pieces);
            } else if (entree.estReponse()) {
                etatAgent.restaurerReponse(entree.reponse);
            } else {
                etatAgent.surEvenement(entree.evenement);
            }
        }
        majQuestionsAgent();
        vueAgent.poserEtat(etatAgent);
    }

    /** L'entrée live du service : événement ou message. */
    private void surEntreeAgent(AgentService.Entree entree) {
        if (entree.estMessage()) {
            etatAgent.envoyer(entree.message, entree.pieces);
        } else if (entree.estReponse()) {
            etatAgent.restaurerReponse(entree.reponse);
        } else {
            etatAgent.surEvenement(entree.evenement);
        }
        majQuestionsAgent();
        vueAgent.poserEtat(etatAgent);
        // la fin d'un tour libère le message retenu pendant le bind
        // (session revenue sur un tour vivant)
        if (messageEnAttenteAgent != null
                && etatAgent.etat() != EtatAgentChat.TourEtat.EN_COURS) {
            depecherAttenteAgent();
        }
        if (pointAgent != null) {
            pointAgent.setVisibility(
                    etatAgent.etat()
                            == EtatAgentChat.TourEtat.EN_COURS
                            ? View.VISIBLE : View.GONE);
        }
        Permissions.DemandeEcriture permission =
                agent == null ? null : agent.permissionEnAttente();
        if (permission != null) {
            montrerPermissionAgent(permission);
        }
    }

    /** Les questions en attente deviennent une carte (ask_user). */
    private void majQuestionsAgent() {
        if (agent == null) {
            return;
        }
        java.util.List<jo.joide.agent.espace.QuestionUtilisateur>
                questions = agent.questionsEnAttente();
        if (questions != null && !questions.isEmpty()
                && etatAgent.reponses() == null) {
            etatAgent.poserQuestions(questions);
        }
    }

    /** Le dialogue de permission sur diff déplié (fermer = refuser). */
    private void montrerPermissionAgent(
            Permissions.DemandeEcriture demande) {
        if (etatAgent.permissionEnAttente() != null) {
            return;
        }
        etatAgent.poserPermission(demande);
        vueAgent.poserEtat(etatAgent);
        ScrollView defilement = new ScrollView(this);
        TextView diff = new TextView(this);
        diff.setTypeface(GraphiteVue.police(this, 400, true));
        diff.setTextSize(11.5f);
        diff.setTextColor(palette.texteSecondaire);
        diff.setText(demande.diffApercu);
        int marge = GraphiteVue.dp(this, 24);
        diff.setPadding(marge, GraphiteVue.dp(this, 6), marge, 0);
        defilement.addView(diff, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        Dialogue.autorisation(this, palette,
                getString(R.string.agent_permission_ecriture),
                demande.outil + " — " + demande.cible,
                defilement,
                R.string.agent_refuser, R.string.agent_appliquer,
                (dialog, lequel) -> repondrePermissionAgent(
                        lequel == android.content.DialogInterface
                                .BUTTON_POSITIVE),
                dialog -> repondrePermissionAgent(false));
    }

    private void envoyerAgent(String message) {
        if (agent == null) {
            ouvrirAgent();
        }
        if (agent == null) {
            // le bind est ASYNCHRONE : le message part dès la liaison
            // (onServiceConnected) — plus jamais perdu silencieusement
            // (fix du 2026-11-17 : le premier message d'une session
            // disparaissait quand le service n'était pas encore lié)
            messageEnAttenteAgent =
                    PieceJointe.fondre(message, piecesAgent);
            piecesEnAttenteAgent.clear();
            piecesEnAttenteAgent.addAll(piecesAgent);
            piecesAgent.clear();
            vueAgent.poserPieces(List.of());
            return;
        }
        if (agent.tourEnCours()) {
            // le service n'empile jamais deux tours — les pièces
            // restent posées, l'utilisateur renverra après l'arrêt
            return;
        }
        // les fichiers et l'arbre se fondent dans le message, les
        // images partent multimodales (lot S)
        String fondu = PieceJointe.fondre(message, piecesAgent);
        agent.envoyer(fondu, List.copyOf(piecesAgent));
        piecesAgent.clear();
        vueAgent.poserPieces(List.of());
    }

    /** Le départ du message retenu : la liaison du service est là
     *  (ou le tour en cours vient de finir). */
    private void depecherAttenteAgent() {
        if (agent == null || messageEnAttenteAgent == null
                || agent.tourEnCours()) {
            return;
        }
        String message = messageEnAttenteAgent;
        List<PieceJointe> pieces = List.copyOf(piecesEnAttenteAgent);
        messageEnAttenteAgent = null;
        piecesEnAttenteAgent.clear();
        agent.envoyer(message, pieces);
    }

    // ------------------------------------------------------------------
    // Les pièces jointes (lot S, ADR-064) : image, photo, fichier
    // courant, projet — le menu ne montre que ce que l'appareil
    // sait faire (caméra présente, fichier actif ouvert).
    // ------------------------------------------------------------------

    /** Le menu + ancré à son bouton (lot T) : chaque entrée est câblée, jamais morte. */
    private void ouvrirAjoutsAgent() {
        if (vueAgent == null) {
            return;
        }
        List<CharSequence> elements = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        elements.add(getString(R.string.agent_ajouter_image));
        actions.add(0);
        boolean camera = getPackageManager().hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_CAMERA_ANY);
        if (camera) {
            elements.add(getString(R.string.agent_ajouter_photo));
            actions.add(1);
        }
        if (etat.actif() != null) {
            elements.add(getString(R.string.agent_ajouter_fichier));
            actions.add(2);
        }
        elements.add(getString(R.string.agent_ajouter_projet));
        actions.add(3);
        LinearLayout colonne = new LinearLayout(this);
        colonne.setOrientation(LinearLayout.VERTICAL);
        android.widget.PopupWindow[] fenetre = new android.widget
                .PopupWindow[1];
        Runnable fermer = () -> {
            if (fenetre[0] != null) {
                fenetre[0].dismiss();
            }
        };
        for (int i = 0; i < elements.size(); i++) {
            CharSequence nom = elements.get(i);
            int action = actions.get(i);
            colonne.addView(PopupAncre.ligneMenu(this, palette, nom,
                    () -> {
                        fermer.run();
                        if (action == 0) {
                            choisirImageAgent();
                        } else if (action == 1) {
                            prendrePhotoAgent();
                        } else if (action == 2) {
                            joindreFichierCourant();
                        } else {
                            joindreProjet();
                        }
                    }));
        }
        fenetre[0] = PopupAncre.montrer(vueAgent.ancreAjouter(),
                palette, colonne, 240);
    }

    /** Le choix d'une image : SAF, lecture bornée, réduction. */
    private void choisirImageAgent() {
        Intent intention = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intention.addCategory(Intent.CATEGORY_OPENABLE);
        intention.setType("image/*");
        try {
            startActivityForResult(intention, REQUETE_IMAGE);
        } catch (android.content.ActivityNotFoundException e) {
            Dialogue.information(this, palette,
                    getString(R.string.agent_ajouter),
                    getString(R.string.agent_image_indisponible),
                    R.string.action_ok);
        }
    }

    /** La photo : cible du cache servie par le fournisseur local. */
    private void prendrePhotoAgent() {
        try {
            android.net.Uri cible = FournisseurCameraAgent.preparer(this);
            Intent intention = new Intent(
                    android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intention.putExtra(
                    android.provider.MediaStore.EXTRA_OUTPUT, cible);
            intention.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intention, REQUETE_PHOTO);
        } catch (IOException | RuntimeException e) {
            Dialogue.information(this, palette,
                    getString(R.string.agent_ajouter),
                    getString(R.string.agent_image_indisponible),
                    R.string.action_ok);
        }
    }

    @Override
    protected void onActivityResult(int requete, int resultat,
                                    Intent donnees) {
        super.onActivityResult(requete, resultat, donnees);
        if (resultat != RESULT_OK) {
            return;
        }
        if (requete == REQUETE_IMAGE && donnees != null
                && donnees.getData() != null) {
            attacherImage(donnees.getData(),
                    nomDadresse(donnees.getData()));
        } else if (requete == REQUETE_PHOTO) {
            Path photo = FournisseurCameraAgent.dernierePhoto();
            if (photo != null && Files.isRegularFile(photo)) {
                attacherImage(android.net.Uri.fromFile(photo.toFile()),
                        photo.getFileName().toString());
            }
        }
    }

    /** Le nom lisible d'une URI SAF (dernier segment, décodé). */
    private String nomDadresse(android.net.Uri adresse) {
        String chemin = adresse == null ? ""
                : String.valueOf(adresse.getLastPathSegment());
        int barre = chemin.lastIndexOf('/');
        return barre >= 0 ? chemin.substring(barre + 1) : chemin;
    }

    /**
     * Attache une image : lecture bornée (8 Mio), type de média,
     * réduction au budget — une image qui ne passe pas est REFUSÉE
     * nommément, jamais attachée en silence.
     */
    private void attacherImage(android.net.Uri adresse, String nom) {
        byte[] octets;
        try (java.io.InputStream flux =
                     getContentResolver().openInputStream(adresse)) {
            if (flux == null) {
                return;
            }
            // lecture bornée à la main (readNBytes exige l'API 33,
            // le min est 28) : un octet de plus révèle le dépassement
            java.io.ByteArrayOutputStream tampon =
                    new java.io.ByteArrayOutputStream();
            byte[] morceau = new byte[65_536];
            int lus;
            while (tampon.size() <= 8_000_000
                    && (lus = flux.read(morceau)) > 0) {
                tampon.write(morceau, 0, lus);
            }
            octets = tampon.toByteArray();
        } catch (IOException | RuntimeException e) {
            Dialogue.information(this, palette,
                    getString(R.string.agent_ajouter),
                    getString(R.string.agent_image_indisponible),
                    R.string.action_ok);
            return;
        }
        if (octets.length > 8_000_000) {
            Dialogue.information(this, palette,
                    getString(R.string.agent_ajouter),
                    getString(R.string.agent_image_trop_grande),
                    R.string.action_ok);
            return;
        }
        String mediaType = getContentResolver().getType(adresse);
        if (mediaType == null || !mediaType.startsWith("image/")) {
            mediaType = ReducteurImagesAndroid.mediaTypeDe(nom);
        }
        String base64 = java.util.Base64.getEncoder()
                .encodeToString(octets);
        ReductionImages.ResultatImage reduite =
                ReductionImages.reduire(mediaType, base64,
                        new ReducteurImagesAndroid(),
                        ReductionImages.BUDGET_OCTETS_DEFAUT);
        if (reduite.image == null) {
            Dialogue.information(this, palette,
                    getString(R.string.agent_ajouter),
                    getString(R.string.agent_image_trop_grande),
                    R.string.action_ok);
            return;
        }
        piecesAgent.add(PieceJointe.image(
                nom.isEmpty() ? "image" : nom,
                reduite.image.mediaType, reduite.image.donnees));
        vueAgent.poserPieces(PieceJointe.noms(piecesAgent));
    }

    /** Le fichier courant : cité en bloc borné (le modèle a read_file). */
    private void joindreFichierCourant() {
        String actif = etat.actif();
        if (actif == null || scene == null) {
            return;
        }
        piecesAgent.add(PieceJointe.fichier(actif,
                PieceJointe.bornerTexte(scene.texte())));
        vueAgent.poserPieces(PieceJointe.noms(piecesAgent));
    }

    /** La structure du projet : l'arbre borné en lignes. */
    private void joindreProjet() {
        String arbre = PieceJointe.arbre(fichiersArbre);
        if (arbre.isEmpty()) {
            return;
        }
        piecesAgent.add(PieceJointe.projet(arbre));
        vueAgent.poserPieces(PieceJointe.noms(piecesAgent));
    }

    // ------------------------------------------------------------------
    // Le menu de l'agent (lot S, ADR-064) : nouvelle conversation,
    // historique — le contenu du menu ne vit QUE ici, la vue
    // n'annonce que les actions câblées.
    // ------------------------------------------------------------------

    /** Le menu ⋯ ancré à son icône (lot T) : chaque entrée est câblée, jamais morte. */
    private void ouvrirMenuAgentOptions() {
        if (vueAgent == null) {
            return;
        }
        LinearLayout colonne = new LinearLayout(this);
        colonne.setOrientation(LinearLayout.VERTICAL);
        android.widget.PopupWindow[] fenetre = new android.widget
                .PopupWindow[1];
        Runnable fermer = () -> {
            if (fenetre[0] != null) {
                fenetre[0].dismiss();
            }
        };
        colonne.addView(PopupAncre.ligneMenu(this, palette,
                getString(R.string.agent_menu_nouvelle), () -> {
                    fermer.run();
                    nouvelleConversationAgent();
                }));
        colonne.addView(PopupAncre.ligneMenu(this, palette,
                getString(R.string.agent_menu_historique), () -> {
                    fermer.run();
                    ouvrirHistoriqueAgent();
                }));
        colonne.addView(PopupAncre.ligneMenu(this, palette,
                getString(R.string.agent_modele_titre), () -> {
                    fermer.run();
                    ouvrirModeleAgent();
                }));
        colonne.addView(PopupAncre.ligneMenu(this, palette,
                getString(R.string.agent_connexions_titre), () -> {
                    fermer.run();
                    ouvrirConnexionsAgent();
                }));
        colonne.addView(PopupAncre.ligneMenu(this, palette,
                getString(R.string.agent_usages_titre), () -> {
                    fermer.run();
                    ouvrirUsagesAgent();
                }));
        fenetre[0] = PopupAncre.montrer(vueAgent.ancreMenu(), palette,
                colonne, 220);
    }

    /**
     * Le {@code .mcp.json} du projet (lot S) : lu borné (64 Kio),
     * vide si absent ou illisible — jamais un crash d'ouverture.
     */
    private String lireMcpJson() {
        if (racineProjet == null) {
            return "";
        }
        try {
            Path cible = racineProjet.resolve(".mcp.json");
            if (!Files.isRegularFile(cible)) {
                return "";
            }
            byte[] octets = Files.readAllBytes(cible);
            if (octets.length > 65_536) {
                return "";
            }
            return new String(octets, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * La clé d'approbation MCP du projet (lot T) : liée à la
     * SIGNATURE du fichier (chemin + contenu passés au SHA-256) —
     * l'approbation vaut pour ce qui a été approuvé, un serveur
     * ajouté ou modifié redemande le geste explicite.
     */
    private String cleApprobationMcp() {
        String chemin = racineProjet == null ? ""
                : racineProjet.resolve(".mcp.json").toString();
        try {
            java.security.MessageDigest empreinte =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] octets = empreinte.digest((chemin + "\n"
                    + lireMcpJson()).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(
                    "agent.mcp.approuve.");
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", octets[i]));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 manque (impossible sur Android) : la clé retombe
            // sur le chemin seul — la persistance reste par projet
            return "agent.mcp.approuve."
                    + Integer.toHexString(chemin.hashCode());
        }
    }

    /**
     * L'écran Connexions (lot S, ADR-064 câblant l'ADR-063) : les
     * serveurs déclarés par le projet, leur état publié, le geste
     * d'approbation qui seul connecte — un dépôt cloné n'est pas un
     * choix de l'utilisateur.
     */
    private void ouvrirConnexionsAgent() {
        if (agent == null) {
            return;
        }
        List<jo.joide.agent.mcp.ConfigServeurMcp> configs =
                agent.configsMcp();
        boolean approuve = agent.approuveMcp();
        java.util.Map<String, jo.joide.agent.mcp.GestionnaireMcp
                .EtatServeur> etats = new java.util.HashMap<>();
        for (var etat : agent.etatsMcp()) {
            etats.put(etat.nom, etat);
        }

        LinearLayout corps = new LinearLayout(this);
        corps.setOrientation(LinearLayout.VERTICAL);
        int marge = GraphiteVue.dp(this, 4);
        corps.setPadding(marge, marge, marge, marge);

        if (configs.isEmpty()) {
            TextView vide = new TextView(this);
            GraphiteVue.typographie(vide, Graphite.CORPS);
            vide.setTextColor(palette.texteEstompe);
            vide.setText(R.string.agent_connexions_vide);
            vide.setPadding(0, GraphiteVue.dp(this, 10), 0,
                    GraphiteVue.dp(this, 10));
            corps.addView(vide);
        } else {
            TextView avertissement = new TextView(this);
            GraphiteVue.typographie(avertissement, Graphite.LEGENDE);
            avertissement.setTextColor(palette.texteSecondaire);
            avertissement.setText(approuve
                    ? R.string.agent_connexions_actives
                    : R.string.agent_connexions_avertissement);
            avertissement.setPadding(0, marge, 0,
                    GraphiteVue.dp(this, 8));
            corps.addView(avertissement);

            TextView action = new TextView(this);
            GraphiteVue.typographie(action, Graphite.CORPS);
            action.setTextColor(palette.accent);
            action.setText(approuve
                    ? R.string.agent_connexions_desactiver
                    : R.string.agent_connexions_activer);
            action.setPadding(0, 0, 0, GraphiteVue.dp(this, 10));
            action.setOnClickListener(vue -> {
                if (agent != null) {
                    agent.poserApprobationMcp(!approuve);
                }
                // la décision se souvient par projet (lot T), liée à
                // la signature du .mcp.json qui l'a motivée
                sourceReglages.ecrire(cleApprobationMcp(),
                        !approuve ? "1" : "0");
                ouvrirConnexionsAgent();
            });
            corps.addView(action);

            if (approuve) {
                TextView compte = new TextView(this);
                GraphiteVue.typographie(compte, Graphite.LEGENDE);
                compte.setTextColor(palette.texteEstompe);
                compte.setText(getString(R.string.agent_connexions_outils,
                        agent.outilsMcp()));
                compte.setPadding(0, 0, 0, GraphiteVue.dp(this, 6));
                corps.addView(compte);
            }

            for (jo.joide.agent.mcp.ConfigServeurMcp config : configs) {
                corps.addView(ligneServeurMcp(config, approuve,
                        etats.get(config.nom)));
            }
        }

        ScrollView defilement = new ScrollView(this);
        defilement.addView(corps, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        // le popup ancré (lot T) : l'écran Connexions vit près du menu
        // qui l'appelle, jamais un dialogue centré qui coupe le fil
        if (vueAgent != null) {
            PopupAncre.montrer(vueAgent.ancreMenu(), palette,
                    defilement, 320);
        }
    }

    /** Une ligne de serveur MCP : nom, état, motif, reconnexion. */
    private View ligneServeurMcp(
            jo.joide.agent.mcp.ConfigServeurMcp config, boolean approuve,
            jo.joide.agent.mcp.GestionnaireMcp.EtatServeur etat) {
        LinearLayout ligne = new LinearLayout(this);
        ligne.setOrientation(LinearLayout.VERTICAL);
        int padding = GraphiteVue.dp(this, 10);
        ligne.setPadding(padding, padding, padding, padding);
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setColor(palette.surface);
        fond.setCornerRadius(GraphiteVue.dp(this,
                Graphite.RAYON_COMPOSANT));
        fond.setStroke(GraphiteVue.dp(this, 1), palette.bordFin);
        ligne.setBackground(fond);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int marge = GraphiteVue.dp(this, 4);
        params.setMargins(0, marge, 0, marge);
        ligne.setLayoutParams(params);

        TextView nom = new TextView(this);
        GraphiteVue.typographie(nom, Graphite.CORPS);
        nom.setTextColor(palette.texte);
        nom.setText(config.nom);
        ligne.addView(nom);

        TextView statut = new TextView(this);
        GraphiteVue.typographie(statut, Graphite.LEGENDE);
        CharSequence texteStatut = !approuve
                ? getString(R.string.agent_connexions_non_active)
                : etat == null
                        ? getString(R.string.agent_connexions_statut_connexion)
                        : texteStatutMcp(etat.statut)
                                + (etat.motif == null ? ""
                                        : " — " + etat.motif);
        statut.setText(texteStatut);
        statut.setTextColor(approuve && etat != null
                && etat.statut == jo.joide.agent.mcp.GestionnaireMcp
                        .Statut.ECHEC
                ? palette.erreur : palette.texteEstompe);
        statut.setPadding(0, GraphiteVue.dp(this, 3), 0, 0);
        ligne.addView(statut);

        if (approuve && etat != null
                && (etat.statut == jo.joide.agent.mcp.GestionnaireMcp
                        .Statut.ECHEC
                || etat.statut == jo.joide.agent.mcp.GestionnaireMcp
                        .Statut.AUTH_REQUISE)) {
            // lot U : la signature OAuth — le parcours MCP se
            // complète : découverte, enregistrement du client,
            // navigateur, rappel joide://mcp/oauth, jetons, connexion
            TextView connecter = new TextView(this);
            GraphiteVue.typographie(connecter, Graphite.CORPS);
            connecter.setTextColor(palette.accent);
            connecter.setText(R.string.agent_connexions_se_connecter);
            connecter.setPadding(0, GraphiteVue.dp(this, 6), 0, 0);
            connecter.setOnClickListener(vue -> {
                if (agent == null) {
                    return;
                }
                agent.demanderOAuthMcp(config.nom,
                        url -> runOnUiThread(() -> {
                            try {
                                startActivity(new Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(url)));
                            } catch (Exception e) {
                                Dialogue.information(
                                        EcranAtelier.this, palette,
                                        getString(R.string
                                                .agent_connexions_titre),
                                        getString(R.string
                                                .agent_connexions_oauth_echec),
                                        R.string.action_ok);
                            }
                        }),
                        erreur -> runOnUiThread(() -> Dialogue
                                .information(EcranAtelier.this,
                                        palette,
                                        getString(R.string
                                                .agent_connexions_titre),
                                        erreur,
                                        R.string.action_ok)));
            });
            ligne.addView(connecter);

            TextView reconnecter = new TextView(this);
            GraphiteVue.typographie(reconnecter, Graphite.CORPS);
            reconnecter.setTextColor(palette.accent);
            reconnecter.setText(R.string.agent_connexions_reconnecter);
            reconnecter.setPadding(0, GraphiteVue.dp(this, 6), 0, 0);
            reconnecter.setOnClickListener(vue -> {
                if (agent != null) {
                    agent.reconnecterMcp(config.nom);
                }
                ouvrirConnexionsAgent();
            });
            ligne.addView(reconnecter);
        }
        return ligne;
    }

    /** Le libellé localisé d'un statut MCP. */
    private CharSequence texteStatutMcp(
            jo.joide.agent.mcp.GestionnaireMcp.Statut statut) {
        return getString(switch (statut) {
            case CONNECTE -> R.string.agent_connexions_statut_connecte;
            case CONNEXION -> R.string.agent_connexions_statut_connexion;
            case AUTH_REQUISE -> R.string.agent_connexions_statut_auth;
            case ECHEC -> R.string.agent_connexions_statut_echec;
            case NON_SUPPORTE ->
                    R.string.agent_connexions_statut_non_supporte;
            default -> R.string.agent_connexions_statut_desactive;
        });
    }

    /**
     * Le sélecteur de modèle de la manière CodeAssist (lot T,
     * ADR-065) : une popup ANCRÉE à la puce du tiroir — la rangée de
     * fournisseurs, la recherche qui filtre, les favoris (étoile)
     * et les récents devant les suggestions, la coche sur le modèle
     * courant. Un modèle se choisit D'UN TAP : le couple part au
     * service, la puce se rafraîchit, la conversation continue. Le
     * modèle personnalisé (et l'URL de base) gardent leur dialogue.
     */
    private void ouvrirModeleAgent() {
        if (vueAgent == null) {
            return;
        }
        Reglages reglages = Reglages.lire(sourceReglages);
        int marge = GraphiteVue.dp(this, 8);
        LinearLayout colonne = new LinearLayout(this);
        colonne.setOrientation(LinearLayout.VERTICAL);
        colonne.setPadding(marge, marge, marge, marge);
        android.widget.PopupWindow[] fenetre =
                new android.widget.PopupWindow[1];
        Runnable fermer = () -> {
            if (fenetre[0] != null) {
                fenetre[0].dismiss();
            }
        };
        Runnable[] rafraichir = new Runnable[1];
        int[] fournisseurIndex = {reglages.fournisseurIa.ordinal()};

        // ---- l'en-tête : titre et fermer ----
        LinearLayout tete = new LinearLayout(this);
        tete.setOrientation(LinearLayout.HORIZONTAL);
        tete.setGravity(Gravity.CENTER_VERTICAL);
        TextView titre = new TextView(this);
        GraphiteVue.typographie(titre, Graphite.SOUS_TITRE);
        titre.setTextColor(palette.texte);
        titre.setText(R.string.agent_modele_titre);
        tete.addView(titre, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Icone fermerIcone = new Icone(this);
        fermerIcone.setGlyphe(Icone.Glyphe.CROIX);
        fermerIcone.setCouleur(palette.texteSecondaire);
        fermerIcone.setClickable(true);
        fermerIcone.setContentDescription(
                getString(R.string.action_annuler));
        fermerIcone.setOnClickListener(vue -> fermer.run());
        tete.addView(fermerIcone, new LinearLayout.LayoutParams(
                GraphiteVue.dp(this, 30),
                GraphiteVue.dp(this, 30)));
        colonne.addView(tete);

        // ---- la rangée des fournisseurs : une puce pointée dit le
        // choix — le TAP PERSISTE le fournisseur (fix du 2026-11-17 :
        // avant, seule la puce bougeait, le fournisseur des réglages
        // restait ANTHROPIC et la requête partait chez Anthropic —
        // « no key for anthropic » quel que soit le fournisseur
        // choisi) ; l'état de clé se lit du stockage chiffré et se
        // voit sous la rangée ----
        Reglages.FournisseurIa[] fournisseurs =
                Reglages.FournisseurIa.values();
        // lot U : quatre fournisseurs dépassent la largeur posée — la
        // rangée DÉFILE horizontalement, jamais une puce coupée
        android.widget.HorizontalScrollView defileFournisseurs =
                new android.widget.HorizontalScrollView(this);
        defileFournisseurs.setHorizontalScrollBarEnabled(false);
        defileFournisseurs.setClipToPadding(false);
        LinearLayout rangeeFournisseurs = new LinearLayout(this);
        rangeeFournisseurs.setOrientation(LinearLayout.HORIZONTAL);

        // l'état de clé du fournisseur pointé (fix du 2026-11-17 :
        // les chaînes agent_cle_posee/absente existaient mais ne se
        // voyaient NULLE PART — l'utilisateur ne savait pas que SA
        // clé n'était pas celle du fournisseur choisi). Un tap ouvre
        // les réglages IA.
        TextView etatCles = new TextView(this);
        GraphiteVue.typographie(etatCles, Graphite.LEGENDE);
        etatCles.setPadding(GraphiteVue.dp(this, 14),
                GraphiteVue.dp(this, 4), GraphiteVue.dp(this, 14), 0);
        etatCles.setOnClickListener(vue -> {
            try {
                startActivity(new Intent(this,
                        jo.joide.app.android.ecrans.parametres
                                .EcranParametres.class));
            } catch (RuntimeException e) {
                // les réglages restent atteignables par le menu
            }
        });
        final Runnable[] majEtatCles = new Runnable[1];
        majEtatCles[0] = () -> {
            Reglages.FournisseurIa courant =
                    fournisseurs[fournisseurIndex[0]];
            boolean posee = false;
            try {
                posee = jo.joide.app.android.parametres.KeystoreCles
                        .sur(this).estPosee(
                                nomStockageCle(courant));
            } catch (RuntimeException e) {
                // Keystore cassé : l'état reste « absente », jamais
                // un crash du sélecteur
            }
            etatCles.setTextColor(posee ? palette.texteSecondaire
                    : palette.alerte);
            etatCles.setText(getString(
                    posee ? R.string.agent_cle_etat_posee
                            : R.string.agent_cle_etat_absente,
                    nomFournisseur(courant)));
        };

        Puce[] puces = new Puce[fournisseurs.length];
        for (int i = 0; i < fournisseurs.length; i++) {
            final int index = i;
            Puce puce = new Puce(this);
            puce.setText(nomFournisseur(fournisseurs[i]));
            puce.setPointee(i == fournisseurIndex[0]);
            puce.setOnClickListener(vue -> {
                fournisseurIndex[0] = index;
                for (int j = 0; j < puces.length; j++) {
                    puces[j].setPointee(j == index);
                }
                // fix 2026-11-17 : le fournisseur se PERSISTE au tap —
                // puce seule ne suffisait pas (voir choisirFournisseur)
                choisirFournisseur(fournisseurs[index]);
                majEtatCles[0].run();
                rafraichir[0].run();
            });
            puces[i] = puce;
            LinearLayout.LayoutParams paramsPuce =
                    new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            paramsPuce.rightMargin = GraphiteVue.dp(this, 6);
            paramsPuce.topMargin = GraphiteVue.dp(this, 8);
            rangeeFournisseurs.addView(puce, paramsPuce);
        }
        defileFournisseurs.addView(rangeeFournisseurs,
                new android.widget.HorizontalScrollView.LayoutParams(
                        android.widget.HorizontalScrollView.LayoutParams
                                .WRAP_CONTENT,
                        android.widget.HorizontalScrollView.LayoutParams
                                .WRAP_CONTENT));
        colonne.addView(defileFournisseurs,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        colonne.addView(etatCles,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        majEtatCles[0].run();

        // ---- la recherche : le filtre des listes longues ----
        android.widget.EditText recherche =
                new android.widget.EditText(this);
        recherche.setHint(R.string.agent_modeles_recherche);
        recherche.setSingleLine(true);
        recherche.setTextSize(14f);
        recherche.setTextColor(palette.texte);
        recherche.setHintTextColor(palette.texteEstompe);
        recherche.setTypeface(GraphiteVue.police(this, 400, false));
        android.graphics.drawable.GradientDrawable pilule =
                new android.graphics.drawable.GradientDrawable();
        pilule.setColor(palette.fond);
        pilule.setCornerRadius(GraphiteVue.dp(this, 18));
        pilule.setStroke(GraphiteVue.dp(this, 1), palette.bordFin);
        recherche.setBackground(pilule);
        recherche.setPadding(GraphiteVue.dp(this, 12),
                GraphiteVue.dp(this, 8), GraphiteVue.dp(this, 12),
                GraphiteVue.dp(this, 8));
        LinearLayout.LayoutParams paramsRecherche =
                new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        paramsRecherche.topMargin = GraphiteVue.dp(this, 8);
        colonne.addView(recherche, paramsRecherche);

        // ---- la liste : Favoris, Récents, Suggestions ----
        LinearLayout liste = new LinearLayout(this);
        liste.setOrientation(LinearLayout.VERTICAL);
        colonne.addView(liste);

        rafraichir[0] = () -> {
            liste.removeAllViews();
            remplirListeModeles(liste, fournisseurIndex[0],
                    recherche.getText().toString(), fermer,
                    rafraichir[0]);
        };
        recherche.addTextChangedListener(
                new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s,
                            int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s,
                            int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(
                            android.text.Editable s) {
                        rafraichir[0].run();
                    }
                });
        rafraichir[0].run();

        fenetre[0] = PopupAncre.montrer(vueAgent.ancreModele(),
                palette, colonne, 320);
    }

    /** Les sections du sélecteur : favoris, récents, suggestions. */
    private void remplirListeModeles(LinearLayout liste,
                                     int fournisseurIndex,
                                     String requete, Runnable fermer,
                                     Runnable rafraichir) {
        Reglages reglages = Reglages.lire(sourceReglages);
        String[] catalogue = catalogueModele(fournisseurIndex);
        List<String> favoris = lireListeModeles(
                cleFavorisModeles(fournisseurIndex));
        List<String> recents = lireListeModeles(
                cleRecentsModeles(fournisseurIndex));
        String filtre = requete == null ? ""
                : requete.trim().toLowerCase(java.util.Locale.ROOT);
        List<String> sectionFavoris = new ArrayList<>();
        List<String> sectionRecents = new ArrayList<>();
        List<String> sectionSuggestions = new ArrayList<>();
        for (String modele : catalogue) {
            if (!filtre.isEmpty() && !modele.toLowerCase(
                    java.util.Locale.ROOT).contains(filtre)) {
                continue;
            }
            if (favoris.contains(modele)) {
                sectionFavoris.add(modele);
            } else if (recents.contains(modele)) {
                sectionRecents.add(modele);
            } else {
                sectionSuggestions.add(modele);
            }
        }
        boolean vide = sectionFavoris.isEmpty()
                && sectionRecents.isEmpty()
                && sectionSuggestions.isEmpty();
        if (!sectionFavoris.isEmpty()) {
            liste.addView(enteteSection(getString(
                    R.string.agent_modeles_favoris)));
        }
        for (String modele : sectionFavoris) {
            liste.addView(ligneModele(modele,
                    modele.equals(reglages.modeleIa), true,
                    fournisseurIndex, fermer, rafraichir));
        }
        if (!sectionRecents.isEmpty()) {
            liste.addView(enteteSection(getString(
                    R.string.agent_modeles_recents)));
        }
        for (String modele : sectionRecents) {
            liste.addView(ligneModele(modele,
                    modele.equals(reglages.modeleIa), false,
                    fournisseurIndex, fermer, rafraichir));
        }
        if (!sectionSuggestions.isEmpty()) {
            liste.addView(enteteSection(getString(
                    R.string.agent_modeles_suggestions)));
        }
        for (String modele : sectionSuggestions) {
            liste.addView(ligneModele(modele,
                    modele.equals(reglages.modeleIa), false,
                    fournisseurIndex, fermer, rafraichir));
        }
        if (vide && filtre.isEmpty()) {
            TextView videVue = new TextView(this);
            GraphiteVue.typographie(videVue, Graphite.LEGENDE);
            videVue.setTextColor(palette.texteEstompe);
            videVue.setText(R.string.agent_modeles_recherche);
            videVue.setPadding(GraphiteVue.dp(this, 14),
                    GraphiteVue.dp(this, 12), 0, 0);
            liste.addView(videVue);
        }
        // le modèle hors catalogue reste atteignable : le geste
        // personnalisé garde son dialogue (nom, URL de base)
        TextView personnalise = new TextView(this);
        GraphiteVue.typographie(personnalise, Graphite.CORPS);
        personnalise.setTextColor(palette.accent);
        personnalise.setText(R.string.agent_modeles_personnalise);
        int vertical = GraphiteVue.dp(this, 11);
        personnalise.setPadding(GraphiteVue.dp(this, 14), vertical,
                GraphiteVue.dp(this, 14), vertical);
        personnalise.setOnClickListener(vue -> {
            fermer.run();
            dialogueModelePersonnalise(fournisseurIndex);
        });
        liste.addView(personnalise);
    }

    /** L'en-tête discret d'une section du sélecteur. */
    private TextView enteteSection(CharSequence titre) {
        TextView vue = new TextView(this);
        GraphiteVue.typographie(vue, Graphite.LEGENDE);
        vue.setTextColor(palette.texteEstompe);
        vue.setText(titre);
        vue.setPadding(GraphiteVue.dp(this, 14),
                GraphiteVue.dp(this, 10),
                GraphiteVue.dp(this, 14),
                GraphiteVue.dp(this, 2));
        return vue;
    }

    /** Une ligne de modèle : la coche du courant, l'étoile du favori. */
    private View ligneModele(String modele, boolean courant,
                             boolean favori, int fournisseurIndex,
                             Runnable fermer, Runnable rafraichir) {
        LinearLayout ligne = new LinearLayout(this);
        ligne.setOrientation(LinearLayout.HORIZONTAL);
        ligne.setGravity(Gravity.CENTER_VERTICAL);
        ligne.setBackground(PopupAncre.fondAppuye(palette));
        ligne.setOnClickListener(vue -> {
            fermer.run();
            choisirModele(fournisseurIndex, modele);
        });
        TextView nom = new TextView(this);
        GraphiteVue.typographie(nom, Graphite.CORPS);
        nom.setTextColor(palette.texte);
        nom.setText(modele);
        nom.setSingleLine(true);
        nom.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ligne.addView(nom, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (courant) {
            Icone coche = new Icone(this);
            coche.setGlyphe(Icone.Glyphe.COCHE);
            coche.setCouleur(palette.accent);
            coche.setTraitDp(1.8f);
            LinearLayout.LayoutParams paramsCoche =
                    new LinearLayout.LayoutParams(
                    GraphiteVue.dp(this, 16),
                    GraphiteVue.dp(this, 16));
            paramsCoche.rightMargin = GraphiteVue.dp(this, 8);
            ligne.addView(coche, paramsCoche);
        }
        Icone etoile = new Icone(this);
        etoile.setGlyphe(Icone.Glyphe.ETOILE);
        etoile.setCouleur(favori ? palette.alerte : palette.bord);
        etoile.setContentDescription(
                getString(R.string.agent_modeles_favori));
        etoile.setClickable(true);
        etoile.setOnClickListener(vue -> {
            basculeFavori(fournisseurIndex, modele, !favori);
            rafraichir.run();
        });
        LinearLayout.LayoutParams paramsEtoile =
                new LinearLayout.LayoutParams(
                GraphiteVue.dp(this, 30),
                GraphiteVue.dp(this, 30));
        paramsEtoile.leftMargin = GraphiteVue.dp(this, 2);
        ligne.addView(etoile, paramsEtoile);
        int vertical = GraphiteVue.dp(this, 9);
        ligne.setPadding(GraphiteVue.dp(this, 14), vertical,
                GraphiteVue.dp(this, 10), vertical);
        return ligne;
    }

    /** Le catalogue suggéré d'un fournisseur (l'honnêteté : des
     * suggestions nommées, jamais une liste prétendue vivante). */
    private static String[] catalogueModele(int fournisseurIndex) {
        return switch (Reglages.FournisseurIa
                .values()[fournisseurIndex]) {
            case ANTHROPIC -> new String[]{
                    "claude-opus-4-1", "claude-sonnet-4-5",
                    "claude-haiku-4-5", "claude-3-7-sonnet",
                    "claude-3-5-haiku"};
            case GEMINI -> new String[]{
                    "gemini-2.5-pro", "gemini-2.5-flash",
                    "gemini-2.5-flash-lite", "gemini-2.0-flash"};
            case OPENROUTER -> new String[]{
                    "anthropic/claude-sonnet-4.5",
                    "openai/gpt-5.1", "google/gemini-2.5-pro",
                    "deepseek/deepseek-chat-v3.1",
                    "z-ai/glm-4.6", "qwen/qwen3-max"};
            default -> new String[]{
                    "gpt-5.1", "gpt-5", "gpt-4.1",
                    "gpt-4.1-mini", "gpt-4o", "o4-mini"};
        };
    }

    /** Le choix d'un modèle : écrit, pousse au service, retient. */
    private void choisirModele(int fournisseurIndex, String modele) {
        Reglages.FournisseurIa choisi = Reglages.FournisseurIa
                .values()[fournisseurIndex];
        Reglages.ecrireFournisseurIa(sourceReglages, choisi);
        Reglages.ecrireModeleIa(sourceReglages, modele);
        // lot U/7 : le modèle se retient POUR CE fournisseur aussi —
        // le modèle partagé ne traverse jamais les fournisseurs
        // (manière CodeAssist)
        Reglages.ecrireModelePour(sourceReglages, choisi, modele);
        if (agent != null) {
            agent.poserModele(choisi, modele);
        }
        etatAgent.poserModele(modele);
        vueAgent.rafraichir();
        pousserRecent(fournisseurIndex, modele);
    }

    /**
     * Le choix d'un fournisseur SEUL (tap de puce, fix du
     * 2026-11-17) : le fournisseur se PERSISTE à l'instant du choix —
     * avant, seuls le choix d'un MODÈLE ou le dialogue personnalisé
     * écrivaient le fournisseur : une puce tapée puis le popup fermé
     * laissait le fournisseur des réglages (ANTHROPIC par défaut) et
     * la requête partait chez Anthropic avec une clé absente
     * (« no key for anthropic », quel que soit le fournisseur choisi).
     *
     * <p>Le modèle suit, à la manière de choisirModele : le modèle
     * posé POUR CE fournisseur d'abord, le modèle partagé s'il lui
     * APPARTIENT, sinon la première suggestion du catalogue — le
     * garde d'appartenance ({@link Reglages#modeleAppartient})
     * empêche « claude-sonnet-4-5 » de partir chez OpenRouter.</p>
     */
    private void choisirFournisseur(Reglages.FournisseurIa choisi) {
        Reglages.ecrireFournisseurIa(sourceReglages, choisi);
        String modele = Reglages.lireModelePour(sourceReglages, choisi);
        if (modele.isEmpty()) {
            String partage = Reglages.lire(sourceReglages).modeleIa;
            if (partage != null
                    && Reglages.modeleAppartient(partage, choisi)) {
                modele = partage;
            }
        }
        if (!Reglages.modeleAppartient(modele, choisi)) {
            String[] catalogue = catalogueModele(choisi.ordinal());
            modele = catalogue.length > 0 ? catalogue[0] : "";
        }
        if (modele.isEmpty()) {
            // aucun modèle plausible pour ce fournisseur : le
            // fournisseur est déjà persisté, le modèle reste celui
            // des réglages — le garde du service refusera proprement
            return;
        }
        Reglages.ecrireModeleIa(sourceReglages, modele);
        Reglages.ecrireModelePour(sourceReglages, choisi, modele);
        if (agent != null) {
            agent.poserModele(choisi, modele);
        }
        etatAgent.poserModele(modele);
        vueAgent.rafraichir();
    }

    /** L'identifiant de stockage chiffré d'un fournisseur (mêmes
     *  noms qu'AgentService et Paramètres : anthropic, openai,
     *  gemini, openrouter). */
    private static String nomStockageCle(
            Reglages.FournisseurIa fournisseur) {
        return switch (fournisseur) {
            case ANTHROPIC -> "anthropic";
            case GEMINI -> "gemini";
            case OPENROUTER -> "openrouter";
            default -> "openai";
        };
    }

    /** L'étoile : basculer un favori (devant les suggestions). */
    private void basculeFavori(int fournisseurIndex, String modele,
                               boolean favori) {
        String cle = cleFavorisModeles(fournisseurIndex);
        List<String> favoris = lireListeModeles(cle);
        favoris.remove(modele);
        if (favori) {
            favoris.add(0, modele);
        }
        ecrireListeModeles(cle, favoris);
    }

    /** Un modèle choisi rejoint les récents (devant, borné à cinq). */
    private void pousserRecent(int fournisseurIndex, String modele) {
        String cle = cleRecentsModeles(fournisseurIndex);
        List<String> recents = lireListeModeles(cle);
        recents.remove(modele);
        recents.add(0, modele);
        while (recents.size() > 5) {
            recents.remove(recents.size() - 1);
        }
        ecrireListeModeles(cle, recents);
    }

    /** La liste persistée d'une clé (séparateur |, jamais vide). */
    private List<String> lireListeModeles(String cle) {
        String brut = sourceReglages.lire(cle);
        List<String> valeurs = new ArrayList<>();
        if (brut != null && !brut.isEmpty()) {
            for (String morceau : brut.split("\\|")) {
                if (!morceau.isEmpty()) {
                    valeurs.add(morceau);
                }
            }
        }
        return valeurs;
    }

    /** La liste persistée s'écrit d'un trait (séparateur |). */
    private void ecrireListeModeles(String cle, List<String> valeurs) {
        sourceReglages.ecrire(cle, String.join("|", valeurs));
    }

    private static String cleFavorisModeles(int fournisseurIndex) {
        return "agent.modeles.favoris." + fournisseurIndex;
    }

    private static String cleRecentsModeles(int fournisseurIndex) {
        return "agent.modeles.recents." + fournisseurIndex;
    }

    /**
     * Le dialogue du modèle personnalisé (lot S conservé) : le nom
     * libre et l'URL OpenAI-compatible s'écrivent au tap sur OK —
     * le couple part au service sans quitter la conversation.
     */
    private void dialogueModelePersonnalise(int fournisseurIndex) {
        Reglages reglages = Reglages.lire(sourceReglages);
        Reglages.FournisseurIa[] fournisseurs =
                Reglages.FournisseurIa.values();
        LinearLayout corps = new LinearLayout(this);
        corps.setOrientation(LinearLayout.VERTICAL);
        int marge = GraphiteVue.dp(this, 4);
        corps.setPadding(marge, marge, marge, marge);

        TextView etiquetteFournisseur = new TextView(this);
        GraphiteVue.typographie(etiquetteFournisseur,
                Graphite.LEGENDE);
        etiquetteFournisseur.setTextColor(palette.texteEstompe);
        etiquetteFournisseur.setText(R.string.agent_fournisseur_section);
        etiquetteFournisseur.setPadding(0, marge, 0, marge);
        corps.addView(etiquetteFournisseur);
        TextView valeurFournisseur = new TextView(this);
        GraphiteVue.typographie(valeurFournisseur, Graphite.CORPS);
        valeurFournisseur.setTextColor(palette.texte);
        valeurFournisseur.setText(nomFournisseur(
                fournisseurs[Math.max(0, Math.min(fournisseurIndex,
                        fournisseurs.length - 1))]));
        corps.addView(valeurFournisseur);

        TextView etiquetteModele = new TextView(this);
        GraphiteVue.typographie(etiquetteModele, Graphite.LEGENDE);
        etiquetteModele.setTextColor(palette.texteEstompe);
        etiquetteModele.setText(R.string.parametres_modele_ia);
        etiquetteModele.setPadding(0, GraphiteVue.dp(this, 12), 0,
                marge);
        corps.addView(etiquetteModele);
        android.widget.EditText saisieModele =
                new android.widget.EditText(this);
        saisieModele.setText(reglages.modeleIa);
        saisieModele.setHint(R.string.parametres_modele_indice);
        saisieModele.setSingleLine(true);
        corps.addView(saisieModele);

        TextView etiquetteUrl = new TextView(this);
        GraphiteVue.typographie(etiquetteUrl, Graphite.LEGENDE);
        etiquetteUrl.setTextColor(palette.texteEstompe);
        etiquetteUrl.setText(R.string.parametres_url_ia);
        etiquetteUrl.setPadding(0, GraphiteVue.dp(this, 12), 0,
                marge);
        corps.addView(etiquetteUrl);
        android.widget.EditText saisieUrl =
                new android.widget.EditText(this);
        // lot U : l'URL d'OpenRouter est propre à la passerelle —
        // préremplie pour la voir, jamais devinée de l'utilisateur
        int indexCourant = Math.max(0, Math.min(fournisseurIndex,
                fournisseurs.length - 1));
        saisieUrl.setText(reglages.urlIa.isEmpty()
                && Reglages.FournisseurIa.OPENROUTER
                        == fournisseurs[indexCourant]
                ? AgentService.URL_OPENROUTER
                : reglages.urlIa);
        saisieUrl.setHint(R.string.parametres_url_indice);
        saisieUrl.setSingleLine(true);
        corps.addView(saisieUrl);

        TextView gererCles = new TextView(this);
        GraphiteVue.typographie(gererCles, Graphite.CORPS);
        gererCles.setTextColor(palette.accent);
        gererCles.setText(R.string.agent_cles_gerer);
        gererCles.setPadding(0, GraphiteVue.dp(this, 12), 0, 0);
        gererCles.setOnClickListener(vue -> {
            try {
                startActivity(new Intent(this,
                        jo.joide.app.android.ecrans.parametres
                                .EcranParametres.class));
            } catch (RuntimeException e) {
                // les réglages restent atteignables par le menu
            }
        });
        corps.addView(gererCles);

        Dialogue.saisie(this, palette,
                getString(R.string.agent_modele_titre), corps,
                R.string.action_annuler, R.string.action_ok,
                (dialog, lequel) -> {
                    if (lequel != DialogInterface.BUTTON_POSITIVE) {
                        return;
                    }
                    Reglages.FournisseurIa choisi =
                            fournisseurs[Math.max(0,
                                    Math.min(fournisseurIndex,
                                            fournisseurs.length - 1))];
                    String modele = saisieModele.getText()
                            .toString().trim();
                    if (modele.isEmpty()) {
                        modele = reglages.modeleIa;
                    }
                    // le choix PERSISTE D'ABORD : une URL refusée ne
                    // doit JAMAIS annuler le fournisseur et le modèle
                    // (rapport du 2026-11-17 — l'URL écartée laissait
                    // tout le dialogue sur Anthropic par défaut)
                    Reglages.ecrireFournisseurIa(sourceReglages, choisi);
                    Reglages.ecrireModeleIa(sourceReglages, modele);
                    // lot U/7 : le modèle se retient POUR CE
                    // fournisseur aussi (manière CodeAssist)
                    Reglages.ecrireModelePour(sourceReglages, choisi,
                            modele);
                    String url = saisieUrl.getText().toString().trim();
                    try {
                        // la correction du 2026-11-18 : l'API
                        // personnalisée se rattache au provider
                        // choisi (une URL OpenRouter ne part plus
                        // chez OpenAI)
                        Reglages.ecrireUrlPour(sourceReglages, choisi,
                                url);
                    } catch (IllegalArgumentException e) {
                        Dialogue.information(this, palette,
                                getString(R.string.agent_modele_titre),
                                getString(
                                        R.string.parametres_valeur_invalide),
                                R.string.action_ok);
                        // pas de return : le choix est déjà persisté
                    }
                    if (agent != null) {
                        agent.poserModele(choisi, modele);
                    }
                    etatAgent.poserModele(modele);
                    vueAgent.rafraichir();
                });
    }

    /** Le nom lisible d'un fournisseur. */
    private CharSequence nomFournisseur(
            Reglages.FournisseurIa fournisseur) {
        return getString(switch (fournisseur) {
            case ANTHROPIC -> R.string.agent_fournisseur_anthropic;
            case GEMINI -> R.string.agent_fournisseur_gemini;
            case OPENROUTER -> R.string.agent_fournisseur_openrouter;
            default -> R.string.agent_fournisseur_openai;
        });
    }

    /**
     * L'écran Usages (lot T, ADR-065 — la manière CodeAssist) : un
     * COMPTEUR par fenêtre plafonnée (requêtes, jetons), sa bande
     * colorée et son pourcentage, puis les trois pieds de la
     * conversation et l'état du banc de clés. Le popup ancré porte
     * son bouton d'actualisation — l'occupation se relit sans rouvrir.
     */
    private void ouvrirUsagesAgent() {
        if (agent == null || vueAgent == null) {
            return;
        }
        LinearLayout colonne = new LinearLayout(this);
        colonne.setOrientation(LinearLayout.VERTICAL);
        int marge = GraphiteVue.dp(this, 6);
        colonne.setPadding(marge, marge, marge, marge);

        // ---- l'en-tête : titre, actualiser, fermer (la manière
        // CodeAssist — le dialogue se suffit, jamais un seul OK) ----
        LinearLayout tete = new LinearLayout(this);
        tete.setOrientation(LinearLayout.HORIZONTAL);
        tete.setGravity(Gravity.CENTER_VERTICAL);
        TextView titre = new TextView(this);
        GraphiteVue.typographie(titre, Graphite.SOUS_TITRE);
        titre.setTextColor(palette.texte);
        titre.setText(R.string.agent_usages_titre);
        tete.addView(titre, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Icone rafraichir = new Icone(this);
        rafraichir.setGlyphe(Icone.Glyphe.RAFRAICHIR);
        rafraichir.setCouleur(palette.texteSecondaire);
        rafraichir.setClickable(true);
        rafraichir.setContentDescription(
                getString(R.string.agent_usages_rafraichir));
        LinearLayout corps = new LinearLayout(this);
        corps.setOrientation(LinearLayout.VERTICAL);
        rafraichir.setOnClickListener(vue -> {
            corps.removeAllViews();
            construireUsages(corps);
        });
        tete.addView(rafraichir, new LinearLayout.LayoutParams(
                GraphiteVue.dp(this, 30),
                GraphiteVue.dp(this, 30)));
        Icone fermer = new Icone(this);
        fermer.setGlyphe(Icone.Glyphe.CROIX);
        fermer.setCouleur(palette.texteSecondaire);
        fermer.setClickable(true);
        fermer.setContentDescription(getString(R.string.action_annuler));
        tete.addView(fermer, new LinearLayout.LayoutParams(
                GraphiteVue.dp(this, 30),
                GraphiteVue.dp(this, 30)));
        colonne.addView(tete);
        colonne.addView(corps);
        construireUsages(corps);

        android.widget.PopupWindow[] fenetre =
                new android.widget.PopupWindow[1];
        fermer.setOnClickListener(vue -> {
            if (fenetre[0] != null) {
                fenetre[0].dismiss();
            }
        });
        fenetre[0] = PopupAncre.montrer(vueAgent.ancreMenu(), palette,
                colonne, 300);
    }

    /** Le corps de l'écran Usages : compteurs, pieds, banc. */
    private void construireUsages(LinearLayout corps) {
        // ---- les compteurs du régulateur (l'occupation des fenêtres
        // du modèle courant — la bande prévient AVANT le 429) ----
        double[] occupations = agent.occupationsModele();
        double requetes = occupations[0];
        double jetons = occupations[1];
        boolean plafondConnu = requetes > 0 || jetons > 0;
        if (plafondConnu) {
            ligneCompteur(corps,
                    getString(R.string.agent_usages_minute_requetes),
                    requetes);
            ligneCompteur(corps,
                    getString(R.string.agent_usages_minute_jetons),
                    jetons);
        } else {
            TextView sansPlafond = new TextView(this);
            GraphiteVue.typographie(sansPlafond, Graphite.LEGENDE);
            sansPlafond.setTextColor(palette.texteEstompe);
            sansPlafond.setText(R.string.agent_usages_sans_plafond);
            sansPlafond.setPadding(0, GraphiteVue.dp(this, 6), 0,
                    GraphiteVue.dp(this, 4));
            corps.addView(sansPlafond);
        }

        // ---- les trois pieds de la conversation (ce qui est facturé
        // est facturé — la légende compacte du précédent CodeAssist)
        jo.joide.agent.UsageLlm usage = agent.usageConversation();
        TextView conversation = new TextView(this);
        GraphiteVue.typographie(conversation, Graphite.CORPS);
        conversation.setTextColor(palette.texte);
        conversation.setText(getString(
                R.string.agent_usages_conversation_compacte,
                EtatAgentChat.jetonsCompacts(usage.entrees),
                EtatAgentChat.jetonsCompacts(usage.sorties),
                EtatAgentChat.jetonsCompacts(usage.lecturesCache)));
        conversation.setPadding(0, GraphiteVue.dp(this, 10), 0,
                GraphiteVue.dp(this, 4));
        corps.addView(conversation);

        // ---- l'état du banc de clés ----
        int taille = agent.tailleBanc();
        TextView banc = new TextView(this);
        GraphiteVue.typographie(banc, Graphite.CORPS);
        banc.setTextColor(palette.texte);
        banc.setText(taille == 0
                ? getString(R.string.agent_usages_banc_vide)
                : getString(R.string.agent_usages_banc, taille));
        banc.setPadding(0, GraphiteVue.dp(this, 8), 0,
                GraphiteVue.dp(this, 2));
        corps.addView(banc);

        for (var assise : agent.bancParRaison().entrySet()) {
            TextView ligne = new TextView(this);
            GraphiteVue.typographie(ligne, Graphite.LEGENDE);
            ligne.setTextColor(palette.texteSecondaire);
            ligne.setText(getString(R.string.agent_usages_assis,
                    assise.getValue(),
                    libelleGenre(assise.getKey())));
            ligne.setPadding(0, GraphiteVue.dp(this, 3), 0, 0);
            corps.addView(ligne);
        }
    }

    /** Un compteur d'occupation : nom, pourcentage, sa bande colorée. */
    private void ligneCompteur(LinearLayout corps, String nom,
                               double fraction) {
        LinearLayout rangee = new LinearLayout(this);
        rangee.setOrientation(LinearLayout.HORIZONTAL);
        rangee.setGravity(Gravity.CENTER_VERTICAL);
        rangee.setPadding(0, GraphiteVue.dp(this, 6), 0, 0);
        TextView nomVue = new TextView(this);
        GraphiteVue.typographie(nomVue, Graphite.CORPS);
        nomVue.setTextColor(palette.texte);
        nomVue.setText(nom);
        rangee.addView(nomVue, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView pourcentage = new TextView(this);
        GraphiteVue.typographie(pourcentage, Graphite.LEGENDE);
        pourcentage.setTextColor(palette.texteEstompe);
        pourcentage.setText(getString(
                R.string.agent_usages_occupation,
                (int) Math.round(fraction * 100)));
        rangee.addView(pourcentage);
        corps.addView(rangee);
        corps.addView(barreUsage(fraction));
    }

    /**
     * La bande d'usage : une piste pilule, un remplissage qui suit la
     * fraction — la couleur dit la bande (accent, alerte, erreur),
     * l'alerte précède l'erreur (BandesUsage, ADR-062).
     */
    private View barreUsage(double fraction) {
        float rempli = (float) Math.max(0.02,
                Math.min(1, fraction));
        LinearLayout barre = new LinearLayout(this);
        barre.setOrientation(LinearLayout.HORIZONTAL);
        android.graphics.drawable.GradientDrawable piste =
                new android.graphics.drawable.GradientDrawable();
        piste.setColor(palette.surfaceHaute);
        piste.setCornerRadius(GraphiteVue.dp(this, 3));
        barre.setBackground(piste);
        barre.setClipToOutline(true);
        View remplissage = new View(this);
        jo.joide.agent.BandesUsage.Bande bande =
                jo.joide.agent.BandesUsage.bandeDe(fraction);
        int couleur = switch (bande) {
            case CRITIQUE -> palette.erreur;
            case ATTENTION -> palette.alerte;
            default -> palette.accent;
        };
        android.graphics.drawable.GradientDrawable jauge =
                new android.graphics.drawable.GradientDrawable();
        jauge.setColor(couleur);
        jauge.setCornerRadius(GraphiteVue.dp(this, 3));
        remplissage.setBackground(jauge);
        barre.addView(remplissage, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, rempli));
        View reste = new View(this);
        barre.addView(reste, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f - rempli));
        LinearLayout.LayoutParams params = new LinearLayout
                .LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                GraphiteVue.dp(this, 6));
        params.setMargins(0, GraphiteVue.dp(this, 4), 0, 0);
        barre.setLayoutParams(params);
        return barre;
    }

    /** Le libellé localisé d'un genre d'assise du banc. */
    private CharSequence libelleGenre(
            jo.joide.agent.ErreursLlm.Genre genre) {
        return getString(switch (genre) {
            case LIMITE_DEBIT -> R.string.agent_genre_limite_debit;
            case LIMITE_JOUR -> R.string.agent_genre_limite_jour;
            case QUOTA_EPUISE -> R.string.agent_genre_quota;
            case CLE_INVALIDE -> R.string.agent_genre_cle_invalide;
            case CLE_SANS_DROIT -> R.string.agent_genre_cle_sans_droit;
            default -> R.string.agent_genre_autre;
        });
    }

    /** Nouvelle conversation : le service garde la vérité, l'état suit. */
    private void nouvelleConversationAgent() {
        if (agent == null) {
            return;
        }
        if (!agent.nouvelleConversation()) {
            Dialogue.information(this, palette,
                    getString(R.string.agent_menu_titre),
                    getString(R.string.agent_tour_en_cours),
                    R.string.action_ok);
            return;
        }
        rejouerJournalAgent();
    }

    /**
     * L'historique des conversations : titre dérivé du premier
     * message, nombre d'échanges, la courante marquée, la
     * suppression demandée nommément.
     */
    private void ouvrirHistoriqueAgent() {
        if (agent == null) {
            return;
        }
        java.util.List<ConversationsAgent.Resume> resumes =
                agent.conversations();
        ScrollView defilement = new ScrollView(this);
        LinearLayout colonne = new LinearLayout(this);
        colonne.setOrientation(LinearLayout.VERTICAL);
        int marge = GraphiteVue.dp(this, 4);
        colonne.setPadding(marge, marge, marge, marge);
        defilement.addView(colonne, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        String active = agent.conversationActive();
        if (resumes.isEmpty()) {
            TextView vide = new TextView(this);
            GraphiteVue.typographie(vide, Graphite.CORPS);
            vide.setTextColor(palette.texteEstompe);
            vide.setText(getString(R.string.agent_historique_vide,
                    ConversationsAgent.CONVERSATIONS_MAX));
            vide.setPadding(0, GraphiteVue.dp(this, 12), 0,
                    GraphiteVue.dp(this, 12));
            colonne.addView(vide);
        }
        java.text.DateFormat formatDate =
                    java.text.DateFormat.getDateInstance(
                            java.text.DateFormat.SHORT);
        java.text.DateFormat formatHeure =
                    java.text.DateFormat.getTimeInstance(
                            java.text.DateFormat.SHORT);
        for (ConversationsAgent.Resume resume : resumes) {
            colonne.addView(ligneConversation(resume,
                    resume.id.equals(active), formatDate, formatHeure));
        }
        // le popup ancré (lot T) : l'historique vit près du menu qui
        // l'appelle, jamais un dialogue centré qui coupe le fil
        if (vueAgent != null) {
            PopupAncre.montrer(vueAgent.ancreMenu(), palette,
                    defilement, 320);
        }
    }

    /** Une ligne de l'historique : titre, méta, la courante marquée. */
    private View ligneConversation(ConversationsAgent.Resume resume,
                                   boolean active,
                                   java.text.DateFormat formatDate,
                                   java.text.DateFormat formatHeure) {
        Context contexte = this;
        LinearLayout ligne = new LinearLayout(contexte);
        ligne.setOrientation(LinearLayout.VERTICAL);
        int padding = GraphiteVue.dp(contexte, 10);
        ligne.setPadding(padding, padding, padding, padding);
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setColor(active ? palette.surfaceHaute : palette.surface);
        fond.setCornerRadius(GraphiteVue.dp(contexte,
                Graphite.RAYON_COMPOSANT));
        fond.setStroke(GraphiteVue.dp(contexte, 1), active
                ? palette.accent : palette.bordFin);
        ligne.setBackground(fond);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int marge = GraphiteVue.dp(contexte, 4);
        params.setMargins(0, marge, 0, marge);
        ligne.setLayoutParams(params);

        TextView titre = new TextView(contexte);
        GraphiteVue.typographie(titre, Graphite.CORPS);
        titre.setTextColor(palette.texte);
        titre.setMaxLines(2);
        titre.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titre.setText(resume.titre.isEmpty()
                ? getString(R.string.agent_historique_sans_titre)
                : resume.titre);
        ligne.addView(titre);

        TextView meta = new TextView(contexte);
        GraphiteVue.typographie(meta, Graphite.LEGENDE);
        meta.setTextColor(active ? palette.accent
                : palette.texteEstompe);
        meta.setText((active
                ? getString(R.string.agent_historique_active) + " · "
                : "")
                + getString(R.string.agent_historique_echanges,
                        resume.echanges)
                + " · " + formatDate.format(new java.util.Date(
                        resume.modifiee))
                + " " + formatHeure.format(new java.util.Date(
                        resume.modifiee)));
        meta.setPadding(0, GraphiteVue.dp(contexte, 3), 0, 0);
        ligne.addView(meta);

        if (!active) {
            TextView reprendre = new TextView(contexte);
            GraphiteVue.typographie(reprendre, Graphite.CORPS);
            reprendre.setTextColor(palette.accent);
            reprendre.setText(R.string.agent_menu_historique);
            reprendre.setPadding(0, GraphiteVue.dp(contexte, 6), 0, 0);
            ligne.setOnClickListener(vue ->
                    reprendreConversationAgent(resume.id));
            ligne.addView(reprendre);
            TextView supprimer = new TextView(contexte);
            GraphiteVue.typographie(supprimer, Graphite.CORPS);
            supprimer.setTextColor(palette.erreur);
            supprimer.setText(R.string.agent_supprimer_conversation);
            supprimer.setPadding(0, GraphiteVue.dp(contexte, 2), 0, 0);
            supprimer.setOnClickListener(vue ->
                    supprimerConversationAgent(resume.id));
            ligne.addView(supprimer);
        }
        return ligne;
    }

    /** Reprend une conversation : l'état du tiroir suit le service. */
    private void reprendreConversationAgent(String id) {
        if (agent == null) {
            return;
        }
        if (!agent.reprendreConversation(id)) {
            Dialogue.information(this, palette,
                    getString(R.string.agent_menu_titre),
                    getString(R.string.agent_tour_en_cours),
                    R.string.action_ok);
            return;
        }
        rejouerJournalAgent();
    }

    /** Supprime une conversation : la demande est nommée et confirmée. */
    private void supprimerConversationAgent(String id) {
        if (agent == null) {
            return;
        }
        Dialogue.confirmation(this, palette,
                getString(R.string.agent_supprimer_conversation),
                getString(R.string.agent_supprimer_detail),
                R.string.action_annuler, R.string.agent_menu_historique,
                (dialog, lequel) -> {
                    if (agent != null) {
                        agent.supprimerConversation(id);
                    }
                    ouvrirHistoriqueAgent();
                });
    }

    private void repondrePermissionAgent(boolean accordee) {
        etatAgent.repondrePermission(accordee);
        if (agent != null) {
            agent.repondrePermission(accordee);
        }
        vueAgent.rafraichir();
    }

    private void repondreQuestionAgent(int index, int choix) {
        etatAgent.repondre(index, choix, null);
        java.util.List<String> reponses = etatAgent.reponses();
        if (reponses != null && agent != null) {
            agent.repondreQuestions(reponses);
        }
        vueAgent.rafraichir();
    }

    /**
     * « Demander à l'IA » (ADR-É11 point 10) : le tiroir s'ouvre, le
     * composeur est prérempli avec le fichier ACTIF quoté (v1 : le
     * document entier borné ; la sélection précise attend l'API
     * celeris — écart consigné au backlog).
     */
    private void demanderIa() {
        ouvrirAgent();
        String actif = etat.actif();
        if (actif == null || scene == null) {
            return;
        }
        String texte = scene.texte();
        int plafond = 8_000;
        String borne = texte.length() > plafond
                ? texte.substring(0, plafond)
                        + "\n[… tronqué — "
                        + (texte.length() - plafond)
                        + " caractères de plus]"
                : texte;
        etatAgent.poserPreRemplissage(actif, borne);
        vueAgent.poserEtat(etatAgent);
    }

    /** Le point d'activité du glyphe agent. */
    private android.graphics.drawable.GradientDrawable pointActivite() {
        return cercle(palette.accent);
    }

    /** Un petit disque de couleur (les points de sélection). */
    private android.graphics.drawable.GradientDrawable cercle(
            int couleur) {
        android.graphics.drawable.GradientDrawable rond =
                new android.graphics.drawable.GradientDrawable();
        rond.setShape(
                android.graphics.drawable.GradientDrawable.OVAL);
        rond.setColor(couleur);
        return rond;
    }

    // ------------------------------------------------------------------
    // Build (lot F repris, pilule et arrêt du lot J/K)
    // ------------------------------------------------------------------


    private void confirmerVidage() {
        Dialogue.confirmation(this, palette,
                R.string.console_vider_titre,
                R.string.console_vider_message,
                R.string.action_annuler,
                R.string.console_vider,
                (dialog, lequel) -> {
                    etatConsole.vider();
                    majConsole();
                });
    }

    // ------------------------------------------------------------------
    // Volets, pilules, dock, aperçu
    // ------------------------------------------------------------------

    /**
     * L'état vide du puits EST l'état sans onglet actif — UNE seule
     * source de vérité (lot U) : l'ouverture, la fermeture du dernier
     * onglet, le chargement et l'erreur passent tous par ici ; jamais
     * un puits muet derrière un éditeur absent.
     */
    private void majPuits() {
        if (etatVidePuits == null) {
            return;
        }
        boolean sansOnglet = etat.actif() == null;
        etatVidePuits.setVisibility(
                sansOnglet ? View.VISIBLE : View.GONE);
        if (vueEditeur != null) {
            vueEditeur.setVisibility(
                    sansOnglet ? View.GONE : View.VISIBLE);
        }
    }


    void majVolets() {
        majPuits();
        if (feuille != null) {
            feuille.poserDeplie(etat.consoleDepliee());
        }
        if (fab != null) {
            // §3.7 : le FAB disparaît dès que la feuille dépasse la
            // barre (le clavier le cache aussi, via les insets)
            fab.setVisibility(etat.consoleDepliee()
                    ? View.GONE : View.VISIBLE);
        }
        if (boutonRailConsole != null) {
            boutonRailConsole.setCouleur(etat.consoleDepliee()
                    ? palette.accent : palette.texteSecondaire);
        }
        if (voletPleinEcran == null || vueApercu == null) {
            return;
        }
        boolean plein = etat.apercuPleinEcran();
        boolean miseEnPage = fichierCourant != null
                && FichiersProjet.estMiseEnPage(fichierCourant);
        boolean voletDroitVisible = etat.grandEcran()
                && etat.voletApercu() && miseEnPage && !plein;
        if (plein) {
            if (vueApercu.getParent() != voletPleinEcran) {
                detacher(vueApercu);
                voletPleinEcran.addView(vueApercu,
                        new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT));
            }
        } else if (voletDroitVisible && voletDroit != null) {
            if (vueApercu.getParent() != voletDroit) {
                detacher(vueApercu);
                voletDroit.addView(vueApercu,
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.MATCH_PARENT));
            }
        }
        voletPleinEcran.setVisibility(
                plein ? View.VISIBLE : View.GONE);
        if (voletDroit != null) {
            voletDroit.setVisibility(
                    voletDroitVisible ? View.VISIBLE : View.GONE);
        }
        vueApercu.maj(etatApercu, fichierCourant == null ? null
                : Arborescence.nomAffiche(fichierCourant));
    }

    private static void detacher(View vue) {
        if (vue.getParent() instanceof android.view.ViewGroup parent) {
            parent.removeView(vue);
        }
    }

    void majFab() {
        if (fab == null) {
            return;
        }
        fab.setContentDescription(getString(
                etat.buildEnCours()
                        ? R.string.atelier_desc_arreter_build
                        : R.string.atelier_desc_construire));
        if (etat.buildEnCours() && etat.build() != null) {
            fab.setEtat(true, getString(R.string.console_etat_encours,
                    etat.build().phase() == null
                            ? getString(R.string.console_phase_aucune)
                            : etat.build().phase()));
        } else {
            fab.setEtat(false, null);
        }
        buildEnCours = etat.buildEnCours();
    }

    void majVerdictConsole() {
        if (feuille == null) {
            return;
        }
        // lot L3 (ADR-083) : la zone vivante console — verdict coloré
        // selon l'état, détail = dernier bilan, pipeline de 3 segments
        String libelle = etatConsole.libelleCourt();
        int couleur;
        String verdict;
        switch (etatConsole.etat()) {
            case EN_COURS -> {
                couleur = palette.accent;
                verdict = "● " + libelle;
            }
            case REUSSI -> {
                couleur = palette.reussite;
                verdict = "✓ " + libelle;
            }
            case ECHOUE -> {
                couleur = palette.erreur;
                verdict = "✕ " + libelle;
            }
            case INTERROMPU -> {
                couleur = palette.alerte;
                verdict = "■ " + libelle;
            }
            default -> {
                couleur = palette.texteSecondaire;
                verdict = getString(R.string.console_etat_vide);
            }
        }
        boolean enCours = etatConsole.etat() == EtatConsole.Etat.EN_COURS;
        boolean resolutionOk = false;
        boolean compilationOk = false;
        boolean compilationErreur = false;
        boolean assemblageAtteint = false;
        boolean assemblageErreur = false;
        for (EtatConsole.PhaseEtat phase : etatConsole.phases()) {
            if (phase.phase() == jo.joide.app.android.ConsoleBuild.Phase.PREPARATION) {
                resolutionOk = phase.commencee() && !enCours;
            } else if (phase.phase()
                    == jo.joide.app.android.ConsoleBuild.Phase.COMPILATION) {
                compilationOk = phase.commencee() && !enCours;
                compilationErreur = etatConsole.etat()
                        == EtatConsole.Etat.ECHOUE;
            } else if (phase.phase()
                    == jo.joide.app.android.ConsoleBuild.Phase.ASSEMBLAGE) {
                assemblageAtteint = phase.commencee();
                assemblageErreur = etatConsole.etat()
                        == EtatConsole.Etat.ECHOUE && assemblageAtteint;
            }
        }
        feuille.poserVerdictConsole(verdict, couleur,
                String.valueOf(legendeDernierBilan()), enCours,
                resolutionOk, compilationOk, compilationErreur,
                assemblageAtteint, assemblageErreur);
    }

    private CharSequence legendeDernierBilan() {
        List<jo.joide.app.android.ConsoleBuild.Ligne> lignes =
                etatConsole.lignes();
        for (int i = lignes.size() - 1; i >= 0; i--) {
            String texte = lignes.get(i).texte();
            if (texte.startsWith("build #") && texte.contains(" — ")) {
                return texte.substring(texte.indexOf(" — ") + 3);
            }
        }
        return null;
    }

    void majConsole() {
        if (vueConsole == null) {
            return;
        }
        vueConsole.maj(etatConsole,
                racineProjet == null ? null
                        : String.valueOf(racineProjet.getFileName()));
        majVerdictConsole();
    }

    private void reconfigurerApercu() {
        if (apercu == null || configApercuBase == null) {
            return;
        }
        apercu.configurer(configApercuBase
                .avecDensite(etatApercu.densite())
                .avecDimensions(etatApercu.largeurPx(),
                        etatApercu.hauteurPx())
                .avecModeNuit(etatApercu.nuit()));
        vueApercu.maj(etatApercu, fichierCourant == null ? null
                : Arborescence.nomAffiche(fichierCourant));
    }

    /** Pose le rendu LayoutLib tel quel, mis à l'échelle du volet. */
    private void poserRendu(View rendu) {
        if (vueApercu == null) {
            return;
        }
        FrameLayout perchoir = vueApercu.conteneurRendu();
        perchoir.removeAllViews();
        int largeurMesuree = rendu.getMeasuredWidth();
        int hauteurMesuree = rendu.getMeasuredHeight();
        final int largeur = largeurMesuree <= 0
                ? etatApercu.largeurPx() : largeurMesuree;
        final int hauteur = hauteurMesuree <= 0
                ? etatApercu.hauteurPx() : hauteurMesuree;
        perchoir.addView(rendu, new FrameLayout.LayoutParams(largeur,
                hauteur, Gravity.CENTER));
        rendu.post(() -> {
            float echelle = Math.min(1f,
                    Math.min(perchoir.getWidth() / (float) largeur,
                            perchoir.getHeight() / (float) hauteur));
            rendu.setScaleX(echelle);
            rendu.setScaleY(echelle);
            rendu.setPivotX(0f);
            rendu.setPivotY(0f);
            perchoir.requestLayout();
        });
        perchoir.requestLayout();
    }

    /** Compte les MockView de l'arbre rendu (classes inconnues). */
    private static int compterMockViews(View racine) {
        int[] compte = new int[1];
        parcourir(racine, compte, new int[]{0});
        return compte[0];
    }

    private static void parcourir(View vue, int[] compte, int[] garde) {
        if (garde[0]++ > 2000) {
            return;
        }
        if (vue.getClass().getSimpleName().equals("MockView")) {
            compte[0]++;
        }
        if (vue instanceof android.view.ViewGroup groupe) {
            for (int i = 0; i < groupe.getChildCount(); i++) {
                parcourir(groupe.getChildAt(i), compte, garde);
            }
        }
    }

    // ------------------------------------------------------------------
    // Cycle de vie
    // ------------------------------------------------------------------

    private void fermerIde() {
        if (scene != null) {
            scene.close();
            scene = null;
        }
        if (apercu != null) {
            apercu.close();
            apercu = null;
        }
        if (moteurs != null) {
            moteurs.close();
            moteurs = null;
        }
        sync = null;
        modele = null;
        fichierCourant = null;
        if (puits != null && vueEditeur != null) {
            puits.removeView(vueEditeur);
        }
        vueEditeur = null;
    }

    @Override
    protected void onDestroy() {
        if (agent != null) {
            try {
                unbindService(connexionAgent());
            } catch (RuntimeException e) {
                // le service partait déjà
            }
            agent = null;
        }
        fermerIde();
        fond.shutdown();
        ecrivain.shutdown();
        super.onDestroy();
    }
}
