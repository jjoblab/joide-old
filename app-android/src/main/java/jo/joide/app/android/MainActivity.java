package jo.joide.app.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import jo.joide.app.android.ecrans.exercice.EcranExercice;
import jo.joide.app.android.ui.Bouton;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.choix.ChoixBouton;
import jo.joide.build.daemon.client.DaemonClient;
import jo.joide.build.daemon.protocol.BuildSpec;
import jo.joide.build.daemon.runner.BuildRunner;
import jo.joide.deps.cache.DependencyCache;
import jo.joide.deps.repo.HttpTransport;
import jo.joide.deps.repo.Repository;
import jo.joide.deps.resolve.ResolverConfig;
import jo.joide.ide.apercu.ApercuLayouts;
import jo.joide.ide.apercu.ConfigurationsApercu;
import jo.joide.ide.editeur.SceneEditeur;
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
 * Écran principal de l'IDE : ouverture d'un projet (sélecteur système,
 * import par copie), montage des moteurs de langage (lot C) derrière
 * l'éditeur celeris (lot D) et l'aperçu de layouts (lot E), console de
 * build branchée au service de premier plan (ADR-043). L'état vide est
 * un état fonctionnel complet : rien n'est ouvert, rien n'est affirmé.
 *
 * <p>Les projets natifs JoIDE (joide.toml) sont montés intégralement ;
 * les projets Gradle restent en mode compatibilité (bannière, import
 * statique hors application — l'échappatoire est l'export P7) ;
 * l'inconnu est dit inconnu. L'UI reste mince : la logique testable
 * vit dans {@link ProjectDetector}, {@link FichiersProjet},
 * {@link SpecsBuild}, {@link ConsoleBuild} et {@link ImporteurProjet}.</p>
 */
public final class MainActivity extends Activity {

    private static final int PICK_ROOT = 41;
    /** Délai d'attente du port du service de build (ms). */
    private static final long ATTENTE_PORT_MS = 10_000L;

    private final ExecutorService fond = Executors.newSingleThreadExecutor(
            runnable -> {
                Thread fil = new Thread(runnable, "joide-ide");
                fil.setDaemon(true);
                return fil;
            });
    private final ExecutorService ecrivain =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread fil = new Thread(runnable, "joide-ecrivain");
                fil.setDaemon(true);
                return fil;
            });
    private final AtomicReference<String> texteEnAttente =
            new AtomicReference<>();
    private final AtomicLong numerosBuild = new AtomicLong();
    private final AtomicLong compteImport = new AtomicLong();

    private TextView statut;
    private LinearLayout zoneEditeur;
    private FrameLayout conteneurEditeur;
    private FrameLayout conteneurApercu;
    private LinearLayout zoneConsole;
    private TextView console;
    private Button boutonFichiers;
    private Button boutonApercu;
    private Button boutonConstruire;
    private CheckBox enProcessus;

    /** IDE monté (null tant qu'aucun projet natif n'est ouvert). */
    private MoteursLangage moteurs;
    private LanguageModelSync sync;
    private SceneEditeur scene;
    private ApercuLayouts apercu;
    private Workspace modele;
    private Path racineProjet;
    private String fichierCourant;
    private volatile boolean buildEnCours;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        GraphiteVue.fondEcran(this, GraphiteVue.palette(this));
        setContentView(construireUi());
    }

    private LinearLayout construireUi() {
        LinearLayout racine = new LinearLayout(this);
        racine.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        racine.setPadding(pad, pad, pad, pad);

        statut = new TextView(this);
        statut.setText(R.string.aucun_projet);
        statut.setTextSize(16f);
        statut.setPadding(0, 0, 0, dp(8));
        racine.addView(statut);

        LinearLayout barre = new LinearLayout(this);
        barre.setOrientation(LinearLayout.HORIZONTAL);
        Button ouvrir = new Button(this);
        ouvrir.setText(R.string.ouvrir_projet);
        ouvrir.setOnClickListener(v -> choisirProjet());
        barre.addView(ouvrir);
        boutonFichiers = new Button(this);
        boutonFichiers.setText(R.string.fichiers);
        boutonFichiers.setEnabled(false);
        boutonFichiers.setOnClickListener(v -> choisirFichier());
        barre.addView(boutonFichiers);
        boutonApercu = new Button(this);
        boutonApercu.setText(R.string.apercu);
        boutonApercu.setVisibility(View.GONE);
        boutonApercu.setOnClickListener(v -> basculerApercu());
        barre.addView(boutonApercu);
        boutonConstruire = new Button(this);
        boutonConstruire.setText(R.string.construire);
        boutonConstruire.setEnabled(false);
        boutonConstruire.setOnClickListener(v -> construire());
        barre.addView(boutonConstruire);
        racine.addView(barre);

        conteneurEditeur = new FrameLayout(this);
        conteneurApercu = new FrameLayout(this);
        conteneurApercu.setVisibility(View.GONE);
        LinearLayout cote = new LinearLayout(this);
        cote.setOrientation(LinearLayout.HORIZONTAL);
        cote.addView(conteneurEditeur,
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        cote.addView(conteneurApercu,
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        zoneEditeur = new LinearLayout(this);
        zoneEditeur.setOrientation(LinearLayout.VERTICAL);
        zoneEditeur.addView(cote,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        racine.addView(zoneEditeur,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        zoneConsole = new LinearLayout(this);
        zoneConsole.setOrientation(LinearLayout.VERTICAL);
        zoneConsole.setVisibility(View.GONE);
        LinearLayout enteteConsole = new LinearLayout(this);
        enteteConsole.setOrientation(LinearLayout.HORIZONTAL);
        TextView titreConsole = new TextView(this);
        titreConsole.setText(R.string.console_build);
        titreConsole.setTextSize(14f);
        enteteConsole.addView(titreConsole,
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        enProcessus = new CheckBox(this);
        enProcessus.setText(R.string.build_en_processus);
        enteteConsole.addView(enProcessus);
        Button fermer = new Button(this);
        fermer.setText(R.string.fermer_console);
        fermer.setOnClickListener(v -> montrerConsole(false));
        enteteConsole.addView(fermer);
        zoneConsole.addView(enteteConsole);
        ScrollView defile = new ScrollView(this);
        console = new TextView(this);
        console.setTypeface(android.graphics.Typeface.MONOSPACE);
        console.setTextSize(12f);
        defile.addView(console);
        zoneConsole.addView(defile,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        racine.addView(zoneConsole,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Lot G (ADR-048) : entrée discrète vers l'écran d'exercice du
        // kit — TEMPORAIRE, retirée quand le lot H remplace l'ouverture
        // par l'accueil « Mes projets ». Aucune navigation n'est câblée
        // au lot G au-delà de ce bouton.
        Bouton kit = new Bouton(this);
        kit.setVariante(ChoixBouton.Variante.TEXTE);
        kit.setTexte(R.string.kit_graphique);
        kit.setOnClickListener(v -> startActivity(
                new Intent(this, EcranExercice.class)));
        racine.addView(kit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return racine;
    }

    private void choisirProjet() {
        Intent choisir = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(choisir, PICK_ROOT);
    }

    @Override
    protected void onActivityResult(int code, int resultat,
                                    Intent donnees) {
        if (code != PICK_ROOT || resultat != RESULT_OK
                || donnees == null || donnees.getData() == null) {
            return;
        }
        Uri arbre = donnees.getData();
        List<String> noms = RootNames.list(this, arbre);
        ProjectDetector.Detection detection =
                ProjectDetector.detect(noms);
        fermerIde();
        switch (detection.kind()) {
            case NATIVE -> {
                statut.setText(R.string.import_en_cours);
                fond.execute(() -> importerNatif(arbre));
            }
            case GRADLE -> statut.setText(R.string.projet_gradle);
            default -> statut.setText(R.string.projet_inconnu);
        }
    }

    private void importerNatif(Uri arbre) {
        try {
            Path projets = ImporteurProjet.dossierProjets(getFilesDir());
            Path cible = projets.resolve(nomProjet(arbre));
            supprimerRecursif(cible);
            compteImport.set(0);
            ImporteurProjet.importer(this, arbre, cible, fichier -> {
                long total = compteImport.incrementAndGet();
                if (total % 25 == 0) {
                    runOnUiThread(() -> statut.setText(
                            getString(R.string.import_n_fichiers, total)));
                }
            });
            WorkspaceLoad charge = WorkspaceLoader.load(cible);
            if (!charge.isUsable()) {
                String detail = charge.errors().isEmpty()
                        ? "?" : charge.errors().get(0).message();
                runOnUiThread(() -> statut.setText(
                        getString(R.string.projet_illisible, detail)));
                return;
            }
            monterIde(cible, charge);
        } catch (IOException | RuntimeException e) {
            runOnUiThread(() -> statut.setText(
                    getString(R.string.import_echoue,
                            String.valueOf(e.getMessage()))));
        }
    }

    /**
     * Montage de l'IDE : la résolution et la synchronisation (réseau,
     * index CIDX) partent sur le fil de fond, le montage des vues
     * revient au fil principal — les vues ne naissent jamais ailleurs.
     */
    private void monterIde(Path racine, WorkspaceLoad charge) {
        Workspace workspace = charge.workspace().orElse(null);
        if (workspace == null) {
            runOnUiThread(() -> statut.setText(
                    R.string.projet_illisible_sans_detail));
            return;
        }
        DependencyCache cache;
        try {
            cache = new DependencyCache(
                    Paths.get(getFilesDir().getAbsolutePath(), "m2"));
        } catch (IOException e) {
            runOnUiThread(() -> statut.setText(
                    getString(R.string.import_echoue,
                            String.valueOf(e.getMessage()))));
            return;
        }
        ResolverConfig config = new ResolverConfig.Builder()
                .repository(new Repository.Remote("central",
                        "https://repo.maven.apache.org/maven2",
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
        // les deux se ferment si le montage UI échoue (activité morte)
        runOnUiThread(() -> monterVues(racine, workspace, syncLocal,
                moteursLocal, instantane, charge));
    }

    private void monterVues(Path racine, Workspace workspace,
                            LanguageModelSync syncLocal,
                            MoteursLangage moteursLocal,
                            LanguageModelSnapshot instantane,
                            WorkspaceLoad charge) {
        if (isFinishing() || isDestroyed()) {
            moteursLocal.close();
            return;
        }
        fermerIde();
        racineProjet = racine;
        modele = workspace;
        sync = syncLocal;
        moteurs = moteursLocal;
        apercu = new ApercuLayouts(this);
        apercu.configurer(ConfigurationsApercu.depuisSnapshot(instantane));
        apercu.poserEcouteur(new ApercuLayouts.EcouteurRendu() {
            @Override
            public void onSucces(View rendu, long dureeMs, int vues,
                                 int largeur, int hauteur) {
                conteneurApercu.removeAllViews();
                conteneurApercu.addView(rendu,
                        new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT));
                statut.setText(getString(R.string.rendu_ok, vues,
                        largeur, hauteur));
            }

            @Override
            public void onErreur(String message, Throwable cause) {
                statut.setText(getString(R.string.rendu_echoue, message));
            }
        });
        scene = new SceneEditeur(this, moteurs);
        scene.poserAuditeurEdition(texte -> {
            texteEnAttente.set(texte);
            ecrivain.execute(this::ecrireEnAttente);
            if (FichiersProjet.estMiseEnPage(fichierCourant)) {
                apercu.rendre(texte);
            }
        });
        conteneurEditeur.removeAllViews();
        conteneurEditeur.addView(scene.vue(),
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        conteneurEditeur.addView(scene.couchePopups(),
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        boutonFichiers.setEnabled(true);
        boutonConstruire.setEnabled(true);
        int problemes = charge.problems().size()
                + instantane.problems().size();
        statut.setText(getString(R.string.projet_pret,
                workspace.allModules().size(), problemes));
    }

    private void ecrireEnAttente() {
        String texte = texteEnAttente.getAndSet(null);
        if (texte == null || fichierCourant == null) {
            return;
        }
        try {
            Files.write(racineProjet.resolve(fichierCourant),
                    texte.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            runOnUiThread(() -> statut.setText(
                    R.string.ecriture_echouee));
        }
    }

    private void choisirFichier() {
        if (racineProjet == null) {
            return;
        }
        fond.execute(() -> {
            List<String> fichiers;
            try {
                fichiers = FichiersProjet.lister(racineProjet);
            } catch (IOException e) {
                return;
            }
            runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.fichiers)
                    .setItems(fichiers.toArray(new String[0]),
                            (dialog, lequel) ->
                                    ouvrirFichier(fichiers.get(lequel)))
                    .show());
        });
    }

    private void ouvrirFichier(String relatif) {
        if (racineProjet == null || scene == null) {
            return;
        }
        fond.execute(() -> {
            String contenu;
            try {
                contenu = FichiersProjet.lire(racineProjet, relatif);
            } catch (IOException e) {
                runOnUiThread(() -> statut.setText(
                        getString(R.string.lecture_echouee, relatif)));
                return;
            }
            fichierCourant = relatif;
            String uri = racineProjet.resolve(relatif).toString();
            runOnUiThread(() -> {
                scene.ouvrir(uri, nomAffiche(relatif), contenu);
                boolean miseEnPage = FichiersProjet.estMiseEnPage(relatif);
                boutonApercu.setVisibility(
                        miseEnPage ? View.VISIBLE : View.GONE);
                if (miseEnPage) {
                    conteneurApercu.setVisibility(View.VISIBLE);
                    boutonApercu.setText(R.string.masquer);
                    apercu.rendreSansDelai(contenu);
                } else {
                    conteneurApercu.setVisibility(View.GONE);
                }
                statut.setText(relatif);
            });
        });
    }

    private void basculerApercu() {
        if (conteneurApercu.getVisibility() == View.VISIBLE) {
            conteneurApercu.setVisibility(View.GONE);
            boutonApercu.setText(R.string.apercu);
        } else {
            conteneurApercu.setVisibility(View.VISIBLE);
            boutonApercu.setText(R.string.masquer);
            if (scene != null && scene.texte() != null
                    && FichiersProjet.estMiseEnPage(fichierCourant)) {
                apercu.rendreSansDelai(scene.texte());
            }
        }
    }

    private void construire() {
        if (racineProjet == null || modele == null || buildEnCours) {
            return;
        }
        ExternalResolution externals = sync.derniereResolution()
                .orElse(ExternalResolution.empty());
        SpecsBuild.Constitution constitution = SpecsBuild.jvm(modele,
                externals, racineProjet,
                racineProjet.resolve("build-joide"),
                Paths.get(getFilesDir().getAbsolutePath(), "m2"));
        montrerConsole(true);
        for (String ecart : constitution.ecarts) {
            ligneConsole(ecart);
        }
        if (!constitution.constructible()) {
            ligneConsole(getString(R.string.rien_a_construire));
            return;
        }
        buildEnCours = true;
        boutonConstruire.setEnabled(false);
        if (enProcessus.isChecked()) {
            ligneConsole(getString(R.string.build_en_processus_annonce));
            fond.execute(() -> construireEnProcessus(constitution.spec));
        } else {
            fond.execute(() -> construireParService(constitution.spec));
        }
    }

    private void construireParService(BuildSpec spec) {
        startForegroundService(new Intent(this, BuildService.class));
        int port = -1;
        long limite = System.currentTimeMillis() + ATTENTE_PORT_MS;
        while (port < 0 && System.currentTimeMillis() < limite) {
            port = BuildService.lirePort(getFilesDir());
            if (port < 0) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    runOnUiThread(this::finirBuild);
                    return;
                }
            }
        }
        if (port < 0) {
            runOnUiThread(() -> {
                ligneConsole(getString(R.string.service_injoignable));
                finirBuild();
            });
            return;
        }
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setTcpNoDelay(true);
            try (DaemonClient client = DaemonClient.over(
                    socket.getInputStream(),
                    socket.getOutputStream())) {
                DaemonClient.Event salutation = client.await("hello", 30);
                ligneConsole(ConsoleBuild.ligne(salutation.type,
                        salutation.id, salutation.body));
                construireAvecClient(client, spec);
            }
        } catch (DaemonClient.DaemonDiedException e) {
            // §6.7 : la mort du démon est un événement, l'UI survit —
            // le build suivant relance le service
            ligneConsole(getString(R.string.build_interrompu));
        } catch (IOException e) {
            ligneConsole(getString(R.string.service_injoignable));
        } finally {
            runOnUiThread(this::finirBuild);
        }
    }

    private void construireAvecClient(DaemonClient client, BuildSpec spec)
            throws IOException {
        long numero = numerosBuild.incrementAndGet();
        client.build(numero, spec);
        while (true) {
            DaemonClient.Event event = client.next(900);
            ligneConsole(ConsoleBuild.ligne(event.type, event.id,
                    event.body));
            if ("build-finished".equals(event.type)) {
                return;
            }
        }
    }

    private void construireEnProcessus(BuildSpec spec) {
        try (BuildRunner runner = new BuildRunner(
                outDir -> StoreAndroid.open(outDir),
                SpecsBuild.FILS_PAR_DEFAUT)) {
            long numero = numerosBuild.incrementAndGet();
            ligneConsole("build #" + numero + " démarré");
            BuildRunner.Outcome resultat = runner.run(spec, null,
                    (nom, statut) -> ligneConsole("  " + nom + " : "
                            + statut), jo.joide.platform.CancelToken.NEVER);
            ligneConsole("build #" + numero
                    + (resultat.success ? " réussi" : " échoué")
                    + " — " + resultat.executed + " exécutée(s), "
                    + resultat.fromCache + " du cache, "
                    + resultat.upToDate + " à jour");
            for (String probleme : resultat.problems) {
                ligneConsole("  ! " + probleme);
            }
        } catch (Exception e) {
            ligneConsole("build échoué : " + e.getMessage());
        } finally {
            runOnUiThread(this::finirBuild);
        }
    }

    private void finirBuild() {
        buildEnCours = false;
        boutonConstruire.setEnabled(true);
    }

    private void ligneConsole(String ligne) {
        runOnUiThread(() -> {
            StringBuilder contenu = new StringBuilder(
                    console.getText());
            if (contenu.length() > 0) {
                contenu.append('\n');
            }
            contenu.append(ligne);
            String[] lignes = contenu.toString().split("\n", -1);
            int debut = Math.max(0,
                    lignes.length - ConsoleBuild.PLAFOND_LIGNES);
            StringBuilder garde = new StringBuilder();
            for (int i = debut; i < lignes.length; i++) {
                if (i > debut) {
                    garde.append('\n');
                }
                garde.append(lignes[i]);
            }
            console.setText(garde);
        });
    }

    private void montrerConsole(boolean visible) {
        zoneConsole.setVisibility(visible ? View.VISIBLE : View.GONE);
        zoneEditeur.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

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
        if (conteneurEditeur != null) {
            conteneurEditeur.removeAllViews();
        }
        if (conteneurApercu != null) {
            conteneurApercu.removeAllViews();
        }
        if (boutonFichiers != null) {
            boutonFichiers.setEnabled(false);
        }
        if (boutonConstruire != null) {
            boutonConstruire.setEnabled(false);
        }
        if (boutonApercu != null) {
            boutonApercu.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        fermerIde();
        fond.shutdown();
        ecrivain.shutdown();
        super.onDestroy();
    }

    private static String nomProjet(Uri arbre) {
        String chemin = arbre.getLastPathSegment();
        return chemin == null ? "projet"
                : chemin.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String nomAffiche(String relatif) {
        int barre = relatif.lastIndexOf('/');
        return barre < 0 ? relatif : relatif.substring(barre + 1);
    }

    private static void supprimerRecursif(Path cible) throws IOException {
        if (!Files.exists(cible)) {
            return;
        }
        try (var parcours = Files.walk(cible)) {
            parcours.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            // la suppression au mieux : l'import repart
                            // de zéro ce qu'il peut
                        }
                    });
        }
    }

    private int dp(int valeur) {
        return (int) (valeur * getResources().getDisplayMetrics().density);
    }
}
