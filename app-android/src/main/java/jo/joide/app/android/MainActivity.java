package jo.joide.app.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import jo.joide.app.android.accueil.AgeRelatif;
import jo.joide.app.android.accueil.ClassifieurProjet;
import jo.joide.app.android.accueil.EtatAccueil;
import jo.joide.app.android.accueil.ProjetsRecents;
import jo.joide.app.android.accueil.Salutation;
import jo.joide.app.android.accueil.TypeProjet;
import jo.joide.app.android.ecrans.creation.EcranCreation;
import jo.joide.app.android.ecrans.atelier.EcranAtelier;
import jo.joide.app.android.ui.Bouton;
import jo.joide.app.android.ui.Carte;
import jo.joide.app.android.ui.Champ;
import jo.joide.app.android.ui.Dialogue;
import jo.joide.app.android.ui.EtatVide;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.Icone;
import jo.joide.app.android.ui.choix.ChoixBouton;
import jo.joide.model.Workspace;
import jo.joide.model.WorkspaceLoader;
import jo.joide.platform.Clock;

/**
 * L'accueil « Mes projets » (ADR-É3, lot H) : le hub de projets, écran
 * de lancement de l'application. Il ne monte aucun moteur (aucune
 * résolution, aucun index) — il est instantané et ne quitte jamais la
 * barre d'application. La création est au premier plan (bouton
 * primaire), l'import SAF est l'action secondaire visible, la carte
 * « Reprendre » rouvre le projet le plus récent, l'état vide est un
 * état de première classe.
 *
 * <p>Vue mince : le tri, la fusion de la reprise, le filtrage et le
 * compteur vivent dans {@link EtatAccueil} (purs, testés JVM) ; la
 * persistance dans {@link ProjetsRecents} (JSON atomique, ADR-049) ;
 * la classification dans {@link ClassifieurProjet}. Les actions ⋮
 * (renommer l'affichage, supprimer avec ses deux conséquences
 * distinctes) et la recherche filtrent l'état, jamais l'inverse.</p>
 */
public final class MainActivity extends Activity {

    private static final int PICK_IMPORT = 41;

    private final ExecutorService fond = Executors.newSingleThreadExecutor(
            runnable -> {
                Thread fil = new Thread(runnable, "joide-accueil");
                fil.setDaemon(true);
                return fil;
            });
    private final AtomicLong compteImport = new AtomicLong();

    private Graphite.Palette palette;
    private TextView legende;
    private Champ champRecherche;
    private TextView ligneProgression;
    private LinearLayout colonneContenu;

    private List<ProjetsRecents.Entree> entrees = List.of();
    private String filtre = "";
    private boolean importEnCours;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        palette = GraphiteVue.palette(this);
        GraphiteVue.fondEcran(this, palette);
        setContentView(construireUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // le retour d'un autre écran (création, atelier) peut avoir
        // changé la liste : elle se relit, la salutation se recale
        recharger();
    }

    private LinearLayout construireUi() {
        LinearLayout racine = new LinearLayout(this);
        racine.setOrientation(LinearLayout.VERTICAL);

        // Barre d'application (ADR-É3) : titre d'écran, salutation
        // datée, actions recherche et ⋮.
        LinearLayout barreApp = new LinearLayout(this);
        barreApp.setOrientation(LinearLayout.HORIZONTAL);
        barreApp.setPadding(GraphiteVue.dp(this, Graphite.MARGE),
                GraphiteVue.dp(this, 10), GraphiteVue.dp(this, 12),
                GraphiteVue.dp(this, 2));
        LinearLayout titres = new LinearLayout(this);
        titres.setOrientation(LinearLayout.VERTICAL);
        TextView titre = new TextView(this);
        GraphiteVue.typographie(titre, Graphite.TITRE_ECRAN);
        titre.setTextColor(palette.texte);
        titre.setText(R.string.accueil_titre);
        titres.addView(titre);
        legende = new TextView(this);
        GraphiteVue.typographie(legende, Graphite.NOTE);
        legende.setTextColor(palette.texteSecondaire);
        titres.addView(legende);
        barreApp.addView(titres, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        barreApp.addView(boutonIcone(Icone.Glyphe.RECHERCHE,
                v -> basculerRecherche()));
        barreApp.addView(boutonIcone(Icone.Glyphe.POINTS,
                v -> menuApplication()));
        racine.addView(barreApp);

        // Recherche (cachée jusqu'à l'action) et progression d'import.
        champRecherche = new Champ(this);
        champRecherche.sansEtiquette();
        champRecherche.setIndice(R.string.accueil_rechercher_indice);
        champRecherche.setVisibility(View.GONE);
        champRecherche.edition().addTextChangedListener(
                new TextWatcher() {
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
        racine.addView(champRecherche, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ligneProgression = new TextView(this);
        GraphiteVue.typographie(ligneProgression, Graphite.NOTE);
        ligneProgression.setTextColor(palette.texteSecondaire);
        ligneProgression.setPadding(GraphiteVue.dp(this, Graphite.MARGE),
                0, GraphiteVue.dp(this, Graphite.MARGE),
                GraphiteVue.dp(this, 4));
        ligneProgression.setVisibility(View.GONE);
        racine.addView(ligneProgression);

        ScrollView defile = new ScrollView(this);
        colonneContenu = new LinearLayout(this);
        colonneContenu.setOrientation(LinearLayout.VERTICAL);
        colonneContenu.setPadding(GraphiteVue.dp(this, Graphite.MARGE),
                GraphiteVue.dp(this, 12), GraphiteVue.dp(this, Graphite.MARGE),
                GraphiteVue.dp(this, 8));
        defile.addView(colonneContenu);
        racine.addView(defile, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return racine;
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
        colonneContenu.removeAllViews();
        if (vue.premiereFois()) {
            colonneContenu.addView(etatVide());
            return;
        }
        if (vue.reprise() != null) {
            colonneContenu.addView(carte(vue.reprise(), true),
                    marges(0, GraphiteVue.dp(this, 10)));
        }
        colonneContenu.addView(rangeeActions(),
                marges(0, GraphiteVue.dp(this, 10)));
        if (vue.sectionVisible()) {
            colonneContenu.addView(enteteSection(vue),
                    marges(GraphiteVue.dp(this, 6), 0));
            for (ProjetsRecents.Entree e : vue.section()) {
                colonneContenu.addView(carte(e, false),
                        marges(0, GraphiteVue.dp(this, Graphite.GOUTTIERE / 2)));
            }
            if (vue.aucunResultat()) {
                TextView aucun = new TextView(this);
                GraphiteVue.typographie(aucun, Graphite.NOTE);
                aucun.setTextColor(palette.texteSecondaire);
                aucun.setText(getString(R.string.accueil_aucun_resultat,
                        filtre));
                colonneContenu.addView(aucun,
                        marges(0, GraphiteVue.dp(this, 8)));
            }
        }
    }

    private void majLegende() {
        LocalDateTime maintenant = LocalDateTime.now();
        int mot = Salutation.de(maintenant.getHour()) == Salutation.Moment.BONJOUR
                ? R.string.accueil_salutation_bonjour
                : R.string.accueil_salutation_bonsoir;
        legende.setText(getString(mot) + " — "
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

    // ---- Composants ----

    private View boutonIcone(Icone.Glyphe glyphe,
                             View.OnClickListener auditeur) {
        FrameLayout bouton = new FrameLayout(this);
        TypedValue indication = new TypedValue();
        getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless,
                indication, true);
        bouton.setBackgroundResource(indication.resourceId);
        bouton.setClickable(true);
        bouton.setOnClickListener(auditeur);
        Icone icone = new Icone(this);
        icone.setGlyphe(glyphe);
        icone.setCouleur(palette.texteSecondaire);
        int cote = GraphiteVue.dp(this, 40);
        FrameLayout.LayoutParams placements = new FrameLayout.LayoutParams(
                GraphiteVue.dp(this, 22), GraphiteVue.dp(this, 22),
                android.view.Gravity.CENTER);
        bouton.addView(icone, placements);
        LinearLayout.LayoutParams parametres = new LinearLayout.LayoutParams(
                cote, cote);
        parametres.leftMargin = GraphiteVue.dp(this, 4);
        bouton.setLayoutParams(parametres);
        return bouton;
    }

    private View etatVide() {
        EtatVide vide = new EtatVide(this);
        vide.setGlypheDossier();
        vide.setTitre(R.string.accueil_vide_titre);
        vide.setAide(R.string.accueil_vide_aide);
        Bouton creer = new Bouton(this);
        creer.setVariante(ChoixBouton.Variante.PRIMAIRE);
        creer.setTexte(R.string.accueil_vide_creer);
        creer.setOnClickListener(v -> nouveauProjet());
        vide.addAction(creer);
        Bouton importer = new Bouton(this);
        importer.setVariante(ChoixBouton.Variante.TONAL);
        importer.setTexte(R.string.accueil_vide_importer);
        importer.setOnClickListener(v -> importerDossier());
        vide.addAction(importer);
        return vide;
    }

    private View rangeeActions() {
        LinearLayout rangee = new LinearLayout(this);
        rangee.setOrientation(LinearLayout.HORIZONTAL);
        Bouton nouveau = new Bouton(this);
        nouveau.setVariante(ChoixBouton.Variante.PRIMAIRE);
        nouveau.setTexte(R.string.accueil_nouveau_projet);
        nouveau.setOnClickListener(v -> nouveauProjet());
        rangee.addView(nouveau, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Bouton importer = new Bouton(this);
        importer.setVariante(ChoixBouton.Variante.TONAL);
        importer.setTexte(R.string.accueil_importer);
        importer.setOnClickListener(v -> importerDossier());
        LinearLayout.LayoutParams placements = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        placements.leftMargin = GraphiteVue.dp(this, 10);
        rangee.addView(importer, placements);
        return rangee;
    }

    private View enteteSection(EtatAccueil.Vue vue) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.HORIZONTAL);
        section.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView nom = new TextView(this);
        GraphiteVue.typographie(nom, Graphite.NOTE);
        nom.setTypeface(GraphiteVue.police(this, 600, false));
        nom.setTextColor(palette.texteSecondaire);
        nom.setText(R.string.accueil_section_projets);
        section.addView(nom);
        TextView compte = new TextView(this);
        GraphiteVue.typographie(compte, Graphite.LEGENDE);
        compte.setTextColor(palette.texteSecondaire);
        compte.setText(vue.texteCompteur());
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setColor(palette.surfaceHaute);
        fond.setCornerRadius(GraphiteVue.dp(this, Graphite.RAYON_PUCE));
        compte.setBackground(fond);
        compte.setPadding(GraphiteVue.dp(this, 8),
                GraphiteVue.dp(this, 1), GraphiteVue.dp(this, 8),
                GraphiteVue.dp(this, 1));
        LinearLayout.LayoutParams placements = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        placements.leftMargin = GraphiteVue.dp(this, 8);
        section.addView(compte, placements);
        return section;
    }

    private View carte(ProjetsRecents.Entree entree, boolean reprise) {
        Carte carte = new Carte(this);
        carte.setTonale(reprise);
        carte.setTuile(entree.type().tuile());
        LinearLayout corps = carte.corps();
        if (reprise) {
            TextView legendeCarte = Carte.ligneLegende(this);
            legendeCarte.setText(R.string.accueil_reprendre);
            corps.addView(legendeCarte);
        }
        TextView nom = Carte.ligneNom(this);
        nom.setText(entree.nomAffiche());
        corps.addView(nom);
        TextView meta = Carte.ligneMeta(this);
        meta.setText(metaLigne(entree));
        corps.addView(meta);
        if (reprise) {
            Icone chevron = new Icone(this);
            chevron.setGlyphe(Icone.Glyphe.CHEVRON);
            chevron.setCouleur(palette.texteEstompe);
            carte.addAccessoire(chevron);
        } else {
            FrameLayout points = (FrameLayout) boutonIcone(
                    Icone.Glyphe.POINTS, v -> menuProjet(entree));
            carte.addAccessoire(points);
        }
        carte.setOnClickListener(v -> ouvrir(entree));
        carte.setClickable(true);
        return carte;
    }

    private String metaLigne(ProjetsRecents.Entree entree) {
        if (entree.type() == TypeProjet.GRADLE) {
            return getString(R.string.type_gradle_meta);
        }
        AgeRelatif.Age age = AgeRelatif.de(
                Clock.system().wallTimeMillis(),
                entree.dernierOuverture());
        String modules = entree.nbModules() == 1
                ? getString(R.string.meta_module_1, entree.nbModules())
                : getString(R.string.meta_modules, entree.nbModules());
        return getString(R.string.meta_projet, typeTexte(entree.type()),
                modules, ageTexte(age));
    }

    private String typeTexte(TypeProjet type) {
        int id = switch (type) {
            case ANDROID -> R.string.type_android;
            case CONSOLE -> R.string.type_console;
            case BIBLIOTHEQUE -> R.string.type_bibliotheque;
            case VIDE -> R.string.type_vide;
            case GRADLE -> R.string.type_gradle_meta;
        };
        return getString(id);
    }

    private String ageTexte(AgeRelatif.Age age) {
        return switch (age.unite()) {
            case INSTANT -> getString(R.string.age_instant);
            case MINUTES -> getString(R.string.age_minutes, age.valeur());
            case HEURES -> getString(R.string.age_heures, age.valeur());
            case HIER -> getString(R.string.age_hier);
            case JOURS -> getString(R.string.age_jours, age.valeur());
            case SEMAINE_DERNIERE -> getString(R.string.age_semaine_derniere);
            case SEMAINES -> getString(R.string.age_semaines, age.valeur());
            case MOIS -> getString(R.string.age_mois, age.valeur());
            case ANNEE -> getString(R.string.age_annee);
        };
    }

    private static LinearLayout.LayoutParams marges(int dessus, int dessous) {
        LinearLayout.LayoutParams placements = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        placements.topMargin = dessus;
        placements.bottomMargin = dessous;
        return placements;
    }

    // ---- Actions ----

    private void nouveauProjet() {
        startActivity(new Intent(this, EcranCreation.class));
    }

    private void basculerRecherche() {
        if (champRecherche.getVisibility() == View.VISIBLE) {
            champRecherche.setVisibility(View.GONE);
            champRecherche.edition().setText("");
        } else {
            champRecherche.setVisibility(View.VISIBLE);
            champRecherche.edition().requestFocus();
        }
    }

    private void menuApplication() {
        // ADR-É3 : à propos, paramètres et journal ; le journal vit
        // désormais DANS les paramètres (lot M, ADR-É8) — aucune
        // entrée morte.
        Dialogue.menu(this, palette, getString(R.string.app_name),
                new CharSequence[]{
                        getString(R.string.parametres_titre),
                        getString(R.string.accueil_menu_a_propos)},
                (dialog, lequel) -> {
                    if (lequel == 0) {
                        startActivity(new Intent(this,
                                jo.joide.app.android.ecrans.parametres
                                        .EcranParametres.class));
                    } else {
                        aPropos();
                    }
                });
    }

    private void aPropos() {
        String version = "?";
        try {
            version = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            // impossible pour son propre paquet : la version reste « ? »
        }
        Dialogue.information(this, palette, getString(R.string.app_name),
                getString(R.string.accueil_a_propos_message, version),
                R.string.action_ok);
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
            Dialogue.information(this, palette,
                    R.string.accueil_dossier_perdu_titre,
                    R.string.accueil_dossier_perdu_message,
                    R.string.action_ok);
            return;
        }
        long maintenant = Clock.system().wallTimeMillis();
        entrees = ProjetsRecents.ouvrir(entrees,
                new ProjetsRecents.Entree(entree.id(),
                        entree.nomAffiche(), entree.type(),
                        entree.dossier(), maintenant, entree.nbModules()));
        persister();
        Intent intention = new Intent(this, EcranAtelier.class);
        intention.putExtra(EcranAtelier.EXTRA_RACINE, racine.toString());
        if (entree.type() == TypeProjet.GRADLE) {
            intention.putExtra(EcranAtelier.EXTRA_BANNIERE,
                    getString(R.string.ide_gradle_banniere));
        }
        startActivity(intention);
    }

    private void menuProjet(ProjetsRecents.Entree entree) {
        Dialogue.menu(this, palette, entree.nomAffiche(),
                new CharSequence[]{
                        getString(R.string.accueil_menu_renommer),
                        getString(R.string.accueil_menu_supprimer)},
                (dialog, lequel) -> {
                    if (lequel == 0) {
                        renommer(entree);
                    } else {
                        supprimer(entree);
                    }
                });
    }

    private void renommer(ProjetsRecents.Entree entree) {
        Champ champ = new Champ(this);
        champ.setEtiquette(R.string.accueil_nom_etiquette);
        champ.edition().setText(entree.nomAffiche());
        Dialogue.saisie(this, palette,
                getString(R.string.accueil_renommer_titre,
                        entree.nomAffiche()),
                champ, R.string.action_annuler, R.string.accueil_menu_renommer,
                (dialog, lequel) -> {
                    CharSequence valeur = champ.valeur();
                    entrees = ProjetsRecents.renommer(entrees, entree.id(),
                            valeur == null ? "" : valeur.toString());
                    persister();
                    rendre();
                });
    }

    private void supprimer(ProjetsRecents.Entree entree) {
        Dialogue.destructive(this, palette,
                getString(R.string.accueil_supprimer_titre,
                        entree.nomAffiche()),
                getString(R.string.accueil_supprimer_message),
                R.string.action_annuler,
                R.string.accueil_supprimer_retirer,
                R.string.accueil_supprimer_effacer,
                (dialog, lequel) -> retirerDeLaListe(entree),
                (dialog, lequel) -> effacerDuStockage(entree));
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
                runOnUiThread(() -> Dialogue.information(this, palette,
                        R.string.accueil_suppression_echouee_titre,
                        R.string.accueil_suppression_echouee,
                        R.string.action_ok));
            }
        });
    }

    // ---- Import SAF (action secondaire, ADR-É3) ----

    private void importerDossier() {
        if (importEnCours) {
            return;
        }
        Intent choisir = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(choisir, PICK_IMPORT);
    }

    @Override
    protected void onActivityResult(int code, int resultat,
                                    Intent donnees) {
        if (code != PICK_IMPORT || resultat != RESULT_OK
                || donnees == null || donnees.getData() == null) {
            return;
        }
        Uri arbre = donnees.getData();
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
            default -> Dialogue.information(this, palette,
                    R.string.projet_inconnu, R.string.projet_inconnu,
                    R.string.action_ok);
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
                    runOnUiThread(() -> montrerProgression(
                            getString(R.string.import_n_fichiers, total)));
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
                    runOnUiThread(() -> montrerProgression(
                            getString(R.string.import_n_fichiers, total)));
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
            importEnCours = false;
            ligneProgression.setVisibility(View.GONE);
            rendre();
            Intent intention = new Intent(this, EcranAtelier.class);
            intention.putExtra(EcranAtelier.EXTRA_RACINE, cible.toString());
            if (gradle) {
                intention.putExtra(EcranAtelier.EXTRA_BANNIERE,
                        getString(R.string.ide_gradle_banniere));
            }
            startActivity(intention);
        });
    }

    private void finirImportErreur(String message) {
        runOnUiThread(() -> {
            importEnCours = false;
            ligneProgression.setVisibility(View.GONE);
            Dialogue.information(this, palette,
                    getString(R.string.import_echoue_titre), message,
                    R.string.action_ok);
            rendre();
        });
    }

    private void montrerProgression(String texte) {
        ligneProgression.setText(texte);
        ligneProgression.setVisibility(View.VISIBLE);
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
    protected void onDestroy() {
        fond.shutdown();
        super.onDestroy();
    }
}
