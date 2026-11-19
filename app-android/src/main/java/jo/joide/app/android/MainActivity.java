package jo.joide.app.android;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import jo.joide.app.android.ui.ActiviteJoide;
import jo.joide.app.android.accueil.AdaptateurAccueil;
import jo.joide.app.android.accueil.ClassifieurProjet;
import jo.joide.app.android.accueil.EtatAccueil;
import jo.joide.app.android.accueil.ElementAccueil;
import jo.joide.app.android.accueil.ProjetsRecents;
import jo.joide.app.android.accueil.TypeProjet;
import jo.joide.app.android.databinding.ActivityAccueilBinding;
import jo.joide.app.android.ecrans.Ecrans;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.PortePopups;
import jo.joide.model.Workspace;
import jo.joide.model.WorkspaceLoader;
import jo.joide.platform.Clock;

/**
 * L'accueil « Mes projets » (ADR-É3, lot H ; racine XML + liste recyclée
 * au lot A2 de l'ADR-085) : le hub de projets, écran de lancement — il ne
 * monte aucun moteur, reste instantané et ne quitte jamais la barre
 * d'application.
 *
 * <p>Coque mince (décision 2) : la hiérarchie vit dans
 * {@code activity_accueil.xml} (barre d'application, champ de recherche
 * Material, progression, RecyclerView + LE VOILE des popups centrés), la
 * liste dans {@link AdaptateurAccueil} (ListAdapter/DiffUtil, view-holders
 * {@code *Binding}) ; le tri, la fusion de la reprise, le filtrage et le
 * compteur vivent dans {@link EtatAccueil} (purs, testés JVM), la
 * persistance dans {@link ProjetsRecents}, la classification dans
 * {@link ClassifieurProjet}. Lot A7 (ADR-085 décision 12, D3) : les
 * dialogues d'hier deviennent des POPUPS — les menus (⋮ de l'écran, ⋮
 * d'une carte de projet) ANCRÉS à leur bouton, l'information, la saisie
 * de renommage et la confirmation destructive centrées sur le voile,
 * portées par le PORTE-POPUPS de l'écran (fermées en onPause/onDestroy ;
 * le retour système ferme d'abord un popup ouvert).</p>
 */
public final class MainActivity extends ActiviteJoide {

    /**
     * L'import SAF par CONTRAT typé (ADR-085, décision 13 — lot A1) :
     * le code de requête PICK_IMPORT=41 a disparu, l'arbre choisi
     * arrive directement au greffon — plus d'onActivityResult.
     */
    private final ActivityResultLauncher<Uri> importLauncher =
            registerForActivityResult(Ecrans.IMPORTER_DOSSIER,
                    this::arbreChoisi);

    private final ExecutorService fond = Executors.newSingleThreadExecutor(
            runnable -> {
                Thread fil = new Thread(runnable, "joide-accueil");
                fil.setDaemon(true);
                return fil;
            });
    private final AtomicLong compteImport = new AtomicLong();

    private ActivityAccueilBinding ecran;
    private AdaptateurAccueil adaptateur;
    private Graphite.Palette palette;

    /** Le porte-popups de l'écran (menus ancrés, cartes centrées sur
     *  le voile du layout racine — lot A7, ADR-085 décision 12). */
    private PortePopups popups;

    private List<ProjetsRecents.Entree> entrees = List.of();
    private String filtre = "";
    private boolean importEnCours;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        palette = GraphiteVue.palette(this);
        GraphiteVue.fondEcran(this, palette);
        ecran = monter(ActivityAccueilBinding::inflate);
        popups = new PortePopups(ecran.voilePopup);
        adaptateur = new AdaptateurAccueil(new Hote());
        ecran.listeAccueil.setLayoutManager(new LinearLayoutManager(this));
        ecran.listeAccueil.setAdapter(adaptateur);
        ecran.actionRecherche.setOnClickListener(v -> basculerRecherche());
        ecran.actionMenu.setOnClickListener(v -> menuApplication());
        ecran.editionRecherche.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a,
                                          int b, int c) {
                // rien : le changement se traite après
            }

            @Override
            public void onTextChanged(CharSequence s, int a,
                                      int b, int c) {
                // rien : le changement se traite après
            }

            @Override
            public void afterTextChanged(Editable s) {
                filtre = s.toString();
                rendre();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // le retour d'un autre écran (création, atelier) peut avoir
        // changé la liste : elle se relit, la salutation se recale
        recharger();
        // lot C1 (garde-crash) : un plantage JAMAIS VU se montre au
        // retour — le démon :build meurt en silence pendant un build
        // (l'écran de crash ne vole pas l'atelier vivant), son rapport
        // attend ici ; l'ouverture ne se répète pas (marqueur posé
        // par l'écran consulté)
        if (jo.joide.app.android.crash.GardienCrash.rapportsNouveaux()) {
            startActivity(new android.content.Intent(this,
                    jo.joide.app.android.crash.EcranCrash.class));
        }
    }

    // ---- État ----

    private void recharger() {
        ProjetsRecents.Chargement charge = ProjetsRecents.lire(fichier());
        entrees = charge.entrees();
        rendre();
    }

    private Path fichier() {
        return getFilesDir().toPath().resolve(ProjetsRecents.FICHIER);
    }

    private void persister() {
        try {
            ProjetsRecents.ecrire(fichier(), entrees);
        } catch (IOException e) {
            // la persistance échouée se voit au prochain démarrage :
            // le message va au journal, l'écran continue sur l'état
            // courant — jamais d'écran bloqué pour un fichier de liste
            android.util.Log.w("joide.accueil",
                    "projets recents non ecrits : " + e.getMessage());
        }
    }

    private void rendre() {
        majLegende();
        EtatAccueil.Vue vue = EtatAccueil.composer(entrees, filtre);
        adaptateur.submitList(ElementAccueil.composer(vue,
                getString(R.string.accueil_aucun_resultat, filtre)));
    }

    private void majLegende() {
        LocalDateTime maintenant = LocalDateTime.now();
        int mot = jo.joide.app.android.accueil.Salutation.de(
                maintenant.getHour()) == jo.joide.app.android.accueil
                .Salutation.Moment.BONJOUR
                ? R.string.accueil_salutation_bonjour
                : R.string.accueil_salutation_bonsoir;
        ecran.legendeAccueil.setText(getString(mot) + " — "
                + dateLegende(maintenant.toLocalDate()));
    }

    /** La date localisée de la légende (« lundi 16 novembre »). */
    private static String dateLegende(LocalDate jour) {
        Locale locale = Locale.getDefault();
        String motif = Locale.FRENCH.getLanguage()
                .equals(locale.getLanguage())
                ? "EEEE d MMMM" : "EEEE, MMMM d";
        return DateTimeFormatter.ofPattern(motif, locale).format(jour);
    }

    // ---- Actions (les popups du lot A7, D3 : plus AUCUN dialogue) ----

    private void nouveauProjet() {
        startActivity(Ecrans.creation(this));
    }

    private void basculerRecherche() {
        if (ecran.champRecherche.getVisibility() == View.VISIBLE) {
            ecran.champRecherche.setVisibility(View.GONE);
            ecran.editionRecherche.setText("");
        } else {
            ecran.champRecherche.setVisibility(View.VISIBLE);
            ecran.editionRecherche.requestFocus();
        }
    }

    private void menuApplication() {
        // ADR-É3 : à propos, paramètres et journal ; le journal vit
        // désormais DANS les paramètres (lot M, ADR-É8) — aucune
        // entrée morte. Lot A7 : le menu ANCRÉ au bouton ⋮ — le tap
        // FERME le menu PUIS agit (jamais une action morte derrière
        // un menu resté ouvert)
        List<PortePopups.EntreeMenu> entrees = new ArrayList<>();
        entrees.add(new PortePopups.EntreeMenu(
                getString(R.string.parametres_titre),
                () -> startActivity(Ecrans.parametres(this))));
        entrees.add(new PortePopups.EntreeMenu(
                getString(R.string.accueil_menu_a_propos),
                this::aPropos));
        popups.menu(ecran.actionMenu, getString(R.string.app_name),
                240, entrees);
    }

    private void aPropos() {
        String version = "?";
        try {
            version = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            // impossible pour son propre paquet : la version reste « ? »
        }
        popups.information(getString(R.string.app_name),
                getString(R.string.accueil_a_propos_message, version),
                getString(R.string.action_ok));
    }

    private void ouvrir(ProjetsRecents.Entree entree) {
        Path racine = ImporteurProjet.dossierProjets(getFilesDir())
                .resolve(entree.dossier());
        if (!Files.isDirectory(racine)) {
            // l'entrée pointe un dossier absent : le retrait se dit,
            // il ne se fait jamais en silence
            entrees = ProjetsRecents.retirer(entrees, entree.id());
            persister();
            rendre();
            popups.information(
                    getString(R.string.accueil_dossier_perdu_titre),
                    getString(R.string.accueil_dossier_perdu_message),
                    getString(R.string.action_ok));
            return;
        }
        long maintenant = Clock.system().wallTimeMillis();
        entrees = ProjetsRecents.ouvrir(entrees,
                new ProjetsRecents.Entree(entree.id(),
                        entree.nomAffiche(), entree.type(),
                        entree.dossier(), maintenant, entree.nbModules()));
        persister();
        String banniere = entree.type() == TypeProjet.GRADLE
                ? getString(R.string.ide_gradle_banniere) : null;
        startActivity(Ecrans.atelier(this, racine.toString(), banniere,
                null));
    }

    /** Le menu ⋮ d'un projet, ANCRÉ au bouton ⋮ de sa carte (lot A7 :
     *  le popup s'accroche à la vue qui l'appelle — renommer,
     *  supprimer). */
    private void menuProjet(View ancre, ProjetsRecents.Entree entree) {
        List<PortePopups.EntreeMenu> entrees = new ArrayList<>();
        entrees.add(new PortePopups.EntreeMenu(
                getString(R.string.accueil_menu_renommer),
                () -> renommer(entree)));
        entrees.add(new PortePopups.EntreeMenu(
                getString(R.string.accueil_menu_supprimer),
                () -> supprimer(entree)));
        popups.menu(ancre, entree.nomAffiche(), 220, entrees);
    }

    /** Le renommage d'un projet : la SAISIE centrée sur voile (lot
     *  A7, clavier requis — le champ vient du popup, prérempli du nom
     *  affiché). */
    private void renommer(ProjetsRecents.Entree entree) {
        android.widget.EditText champ = popups.saisie(
                getString(R.string.accueil_renommer_titre,
                        entree.nomAffiche()),
                getString(R.string.accueil_nom_etiquette),
                getString(R.string.action_annuler),
                getString(R.string.accueil_menu_renommer),
                saisie -> {
                    CharSequence valeur = saisie.getText();
                    entrees = ProjetsRecents.renommer(entrees, entree.id(),
                            valeur == null ? "" : valeur.toString());
                    persister();
                    rendre();
                });
        champ.setText(entree.nomAffiche());
    }

    /** La suppression d'un projet : la DESTRUCTIVE centrée sur voile
     *  (lot A7) — retirer de la liste (les fichiers restent) et
     *  effacer du stockage (les fichiers disparaissent) sont DEUX
     *  actions (ADR-É3). */
    private void supprimer(ProjetsRecents.Entree entree) {
        popups.destructive(
                getString(R.string.accueil_supprimer_titre,
                        entree.nomAffiche()),
                getString(R.string.accueil_supprimer_message),
                getString(R.string.action_annuler),
                getString(R.string.accueil_supprimer_retirer),
                getString(R.string.accueil_supprimer_effacer),
                () -> retirerDeLaListe(entree),
                () -> effacerDuStockage(entree));
    }

    private void retirerDeLaListe(ProjetsRecents.Entree entree) {
        entrees = ProjetsRecents.retirer(entrees, entree.id());
        persister();
        rendre();
    }

    private void effacerDuStockage(ProjetsRecents.Entree entree) {
        retirerDeLaListe(entree);
        Path cible = ImporteurProjet.dossierProjets(getFilesDir())
                .resolve(entree.dossier());
        fond.execute(() -> {
            try {
                supprimerRecursif(cible);
            } catch (IOException e) {
                runOnUiThread(() -> {
                    // I8/lot A8 : la garde de cycle de vie — l'écran
                    // fermé ne montre plus rien (le projet est effacé,
                    // l'accueil le relira)
                    if (isDestroyed()) {
                        return;
                    }
                    popups.information(
                            getString(R.string.accueil_suppression_echouee_titre),
                            getString(R.string.accueil_suppression_echouee),
                            getString(R.string.action_ok));
                });
            }
        });
    }

    // ---- Import SAF (action secondaire, ADR-É3) ----

    private void importerDossier() {
        if (importEnCours) {
            return;
        }
        importLauncher.launch(null);
    }

    /**
     * L'arbre SAF choisi arrive TYPÉ — le contrat ne remonte qu'un
     * choix réussi, le reste (annulation, code de requête, resultCode)
     * ne regarde plus cet écran.
     */
    private void arbreChoisi(Uri arbre) {
        if (arbre == null) {
            return;
        }
        List<String> noms = RootNames.list(this, arbre);
        ProjectDetector.Detection detection = ProjectDetector.detect(noms);
        switch (detection.kind()) {
            case NATIVE -> {
                importEnCours = true;
                montrerProgression(getString(R.string.import_en_cours));
                fond.execute(() -> importerNatif(arbre));
            }
            case GRADLE -> {
                importEnCours = true;
                montrerProgression(getString(R.string.import_en_cours));
                fond.execute(() -> importerGradle(arbre));
            }
            default -> popups.information(getString(R.string.projet_inconnu),
                    getString(R.string.projet_inconnu),
                    getString(R.string.action_ok));
        }
    }

    private void importerNatif(Uri arbre) {
        try {
            String nomBrut = nomAfficheArbre(arbre);
            Path projets = ImporteurProjet.dossierProjets(getFilesDir());
            String id = ProjetsRecents.idUnique(entrees,
                    ProjetsRecents.assainirId(nomBrut));
            Path cible = projets.resolve(id);
            supprimerRecursif(cible);
            compteImport.set(0);
            ImporteurProjet.importer(this, arbre, cible, fichier -> {
                long total = compteImport.incrementAndGet();
                if (total % 25 == 0) {
                    runOnUiThread(() -> {
                        if (isDestroyed()) {
                            return; // I8/lot A8 : garde de cycle de vie
                        }
                        montrerProgression(
                                getString(R.string.import_n_fichiers, total));
                    });
                }
            });
            Workspace espace = WorkspaceLoader.load(cible)
                    .workspace().orElse(null);
            if (espace == null) {
                finirImportErreur(getString(R.string.projet_illisible_sans_detail));
                return;
            }
            TypeProjet type = ClassifieurProjet.classifier(
                    ProjectDetector.Kind.NATIVE, espace);
            int modules = ClassifieurProjet.compterModules(espace);
            enregistrerOuvert(id, nomBrut, type, modules);
            ouvrirIdeImporte(cible, false);
        } catch (IOException | RuntimeException e) {
            finirImportErreur(getString(R.string.import_echoue,
                    String.valueOf(e.getMessage())));
        }
    }

    private void importerGradle(Uri arbre) {
        try {
            String nomBrut = nomAfficheArbre(arbre);
            Path projets = ImporteurProjet.dossierProjets(getFilesDir());
            String id = ProjetsRecents.idUnique(entrees,
                    ProjetsRecents.assainirId(nomBrut));
            Path cible = projets.resolve(id);
            supprimerRecursif(cible);
            compteImport.set(0);
            ImporteurProjet.importer(this, arbre, cible, fichier -> {
                long total = compteImport.incrementAndGet();
                if (total % 25 == 0) {
                    runOnUiThread(() -> {
                        if (isDestroyed()) {
                            return; // I8/lot A8 : garde de cycle de vie
                        }
                        montrerProgression(
                                getString(R.string.import_n_fichiers, total));
                    });
                }
            });
            enregistrerOuvert(id, nomBrut, TypeProjet.GRADLE, 0);
            ouvrirIdeImporte(cible, true);
        } catch (IOException | RuntimeException e) {
            finirImportErreur(getString(R.string.import_echoue,
                    String.valueOf(e.getMessage())));
        }
    }

    private void enregistrerOuvert(String id, String nomAffiche,
                                   TypeProjet type, int modules) {
        ProjetsRecents.Entree nouvelle = new ProjetsRecents.Entree(id,
                nomAffiche, type, id,
                Clock.system().wallTimeMillis(), modules);
        entrees = ProjetsRecents.ouvrir(entrees, nouvelle);
        persister();
    }

    private void ouvrirIdeImporte(Path cible, boolean gradle) {
        runOnUiThread(() -> {
            if (isDestroyed()) {
                // I8/lot A8 : l'écran fermé ne rouvre plus l'atelier —
                // l'accueil le proposera à la prochaine visite
                return;
            }
            importEnCours = false;
            ecran.ligneProgression.setVisibility(View.GONE);
            rendre();
            String banniere = gradle
                    ? getString(R.string.ide_gradle_banniere) : null;
            startActivity(Ecrans.atelier(this, cible.toString(), banniere,
                    null));
        });
    }

    private void finirImportErreur(String message) {
        runOnUiThread(() -> {
            if (isDestroyed()) {
                // I8/lot A8 : garde de cycle de vie — l'écran fermé ne
                // montre plus rien (l'import a échoué, l'accueil le
                // dira si l'utilisateur revient)
                return;
            }
            importEnCours = false;
            ecran.ligneProgression.setVisibility(View.GONE);
            popups.information(getString(R.string.import_echoue_titre),
                    message, getString(R.string.action_ok));
            rendre();
        });
    }

    private void montrerProgression(String texte) {
        ecran.ligneProgression.setText(texte);
        ecran.ligneProgression.setVisibility(View.VISIBLE);
    }

    private static String nomAfficheArbre(Uri arbre) {
        String chemin = arbre.getLastPathSegment();
        if (chemin == null) {
            return "projet";
        }
        int deuxPoints = chemin.lastIndexOf(':');
        if (deuxPoints >= 0 && deuxPoints + 1 < chemin.length()) {
            chemin = chemin.substring(deuxPoints + 1);
        }
        int barre = chemin.lastIndexOf('/');
        if (barre >= 0 && barre + 1 < chemin.length()) {
            chemin = chemin.substring(barre + 1);
        }
        return chemin.isEmpty() ? "projet" : chemin;
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

    @Override
    protected void onPause() {
        // décision 12 : un popup ouvert se ferme EXPLICITEMENT —
        // jamais une fuite au détachement, jamais restauré
        popups.fermer();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        popups.fermer();
        fond.shutdown();
        super.onDestroy();
    }

    /** L'hôte étroit de l'adaptateur (décision 3 : jamais l'écran entier). */
    private final class Hote implements AdaptateurAccueil.Auditeur {

        @Override
        public void ouvrir(ProjetsRecents.Entree entree) {
            MainActivity.this.ouvrir(entree);
        }

        @Override
        public void menuProjet(View ancre, ProjetsRecents.Entree entree) {
            MainActivity.this.menuProjet(ancre, entree);
        }

        @Override
        public void nouveauProjet() {
            MainActivity.this.nouveauProjet();
        }

        @Override
        public void importerDossier() {
            MainActivity.this.importerDossier();
        }
    }
}
