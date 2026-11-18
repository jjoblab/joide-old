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

    Graphite.Palette palette;
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
    MoteursLangage moteurs;
    LanguageModelSync sync;
    SceneEditeur scene;
    private ApercuLayouts apercu;
    private ConfigurationApercu configApercuBase;
    Workspace modele;
    Path racineProjet;
    private String fichierCourant;
    private String fichierInitial;
    private String xmlCourant;
    List<String> fichiersArbre = List.of();
    private boolean sortiesPresentes;
    volatile boolean buildEnCours;
    /** L6 : l'orchestration du build vit hors de l'écran. */
    private ControleurBuild controleurBuild;
    /** L6 : l'agent vit hors de l'écran — service, tour, pièces,
     *  MCP, sélecteur, usages, historique, permissions. */
    private ControleurAgent controleurAgent;
    Reglages.Source sourceReglages;

    // ---- l'agent (lot P, ADR-É11) ----
    VueAgent vueAgent;
    TiroirPousse tiroirAgent;
    Icone boutonAgent;
    View pointAgent;
    AgentService.AgentBinder agent;
    EtatAgentChat etatAgent = new EtatAgentChat();
    final List<PieceJointe> piecesAgent = new ArrayList<>();

    /** Le message parti avant la liaison du service (fix du
     *  2026-11-17) : un premier message envoyé pendant le bind
     *  ASYNCHRONE se perdait silencieusement — il part dès que le
     *  binder est là, même après un tour en cours. */
    String messageEnAttenteAgent;

    /** Les pièces du message en attente (mêmes règles que le
     *  composeur : fondues au moment du départ). */
    final List<PieceJointe> piecesEnAttenteAgent =
            new ArrayList<>();

    /** Le code de requête du choix d'image (SAF). */
    static final int REQUETE_IMAGE = 47_101;

    /** Le code de requête de la photo (caméra). */
    static final int REQUETE_PHOTO = 47_102;

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
        controleurAgent = new ControleurAgent(this);
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
                vue -> controleurAgent.ouvrirAgent());
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
                controleurAgent.envoyerAgent(message);
            }

            @Override
            public void arreter() {
                controleurAgent.arreterTour();
            }

            @Override
            public void repondrePermission(boolean accordee) {
                controleurAgent.repondrePermissionAgent(accordee);
            }

            @Override
            public void repondreQuestion(int index, int choix) {
                controleurAgent.repondreQuestionAgent(index, choix);
            }

            @Override
            public void poserMode(jo.joide.agent.Permissions.Mode mode) {
                controleurAgent.poserMode(mode);
            }

            @Override
            public void ouvrirModele() {
                controleurAgent.ouvrirModeleAgent();
            }

            @Override
            public void ouvrirMenuAgent() {
                controleurAgent.ouvrirMenuAgentOptions();
            }

            @Override
            public void ouvrirAjouts() {
                controleurAgent.ouvrirAjoutsAgent();
            }

            @Override
            public void retirerPiece(int index) {
                controleurAgent.retirerPiece(index);
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

    @Override
    protected void onActivityResult(int requete, int resultat,
                                    Intent donnees) {
        super.onActivityResult(requete, resultat, donnees);
        controleurAgent.surResultatActivite(requete, resultat, donnees);
    }

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
                        controleurAgent.demanderIa();
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
            controleurAgent.fermerAgent();
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
        controleurAgent.debrancherService();
        fermerIde();
        fond.shutdown();
        ecrivain.shutdown();
        super.onDestroy();
    }
}
