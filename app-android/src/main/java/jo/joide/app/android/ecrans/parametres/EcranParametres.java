package jo.joide.app.android.ecrans.parametres;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import jo.joide.app.android.parametres.ClesChiffrees;
import jo.joide.app.android.parametres.KeystoreCles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jo.joide.app.android.BuildConfig;
import jo.joide.app.android.R;
import jo.joide.app.android.parametres.EtatParametres;
import jo.joide.app.android.parametres.JournalDiagnostic;
import jo.joide.app.android.parametres.MesuresStockage;
import jo.joide.app.android.parametres.PreferencesAndroid;
import jo.joide.app.android.parametres.Reglages;
import jo.joide.app.android.ui.Champ;
import jo.joide.app.android.ui.Dialogue;
import jo.joide.app.android.ui.EtatVide;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.Icone;
import jo.joide.app.android.ui.Interrupteur;
import jo.joide.app.android.ui.Nuancier;
import jo.joide.app.android.ui.Segment;
import jo.joide.app.android.ui.Stepper;

/**
 * L'écran de paramètres (lot M, ADR-É8/054) : plein écran, atteint
 * depuis l'accueil (⋮) et l'atelier (⋮). Recherche en tête, sections
 * en listes, valeurs MESURÉES au disque, actions destructrices à
 * conséquence nominée. Réglages réels seulement : chaque rangée a un
 * consommateur — le thème et l'accent passent par
 * {@code GraphiteVue.palette} (recréation de l'écran), les réglages
 * d'éditeur par la vue celeris à son ouverture suivante, le moteur et
 * les fils par le lanceur de build.
 *
 * <p>Vue mince : la structure, le filtrage et la franchise vivent
 * dans {@link EtatParametres}, les valeurs et bornes dans
 * {@link Reglages}, les mesures dans {@link MesuresStockage} —
 * l'écran monte, traduit et déroule.</p>
 */
public final class EcranParametres extends Activity {

    /** Le dépôt public du projet (rangée « Dépôt », ADR-É8). */
    private static final String DEPOT = "https://github.com/jjoblab/joide";

    private final ExecutorService fond = Executors.newSingleThreadExecutor(
            runnable -> {
                Thread fil = new Thread(runnable, "joide-parametres");
                fil.setDaemon(true);
                return fil;
            });

    private Graphite.Palette palette;
    private Reglages.Source source;
    private Reglages reglages;
    /** Le stockage chiffré des clés d'API (lot P). */
    private ClesChiffrees cles;

    private LinearLayout colonneSections;
    private EtatVide etatVide;
    private String requete = "";

    /** Les mesures disque, lues sur le fil de fond (null : pas lues). */
    private Map<String, Long> octets = new HashMap<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        palette = GraphiteVue.palette(this);
        GraphiteVue.fondEcran(this, palette);
        source = PreferencesAndroid.source(this);
        reglages = Reglages.lire(source);
        cles = KeystoreCles.sur(this);
        setContentView(construireUi());
        mesurerStockage();
    }

    @Override
    protected void onDestroy() {
        fond.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    private View construireUi() {
        LinearLayout racine = new LinearLayout(this);
        racine.setOrientation(LinearLayout.VERTICAL);
        racine.setBackgroundColor(palette.fond);

        LinearLayout barre = new LinearLayout(this);
        barre.setOrientation(LinearLayout.HORIZONTAL);
        barre.setGravity(Gravity.CENTER_VERTICAL);
        barre.setBackgroundColor(palette.surface);
        barre.setPadding(GraphiteVue.dp(this, 8),
                GraphiteVue.dp(this, 10), GraphiteVue.dp(this, 8),
                GraphiteVue.dp(this, 10));
        Icone retour = new Icone(this);
        retour.setGlyphe(Icone.Glyphe.RETOUR);
        retour.setCouleur(palette.texte);
        retour.setClickable(true);
        retour.setContentDescription(getString(
                R.string.parametres_desc_retour));
        retour.setOnClickListener(vue -> finish());
        barre.addView(retour, new LinearLayout.LayoutParams(
                GraphiteVue.dp(this, 40), GraphiteVue.dp(this, 40)));
        TextView titre = new TextView(this);
        titre.setText(R.string.parametres_titre);
        titre.setTextColor(palette.texte);
        titre.setTextSize(20f);
        titre.setTypeface(GraphiteVue.police(this, 700, false));
        barre.addView(titre, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        racine.addView(barre, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Champ recherche = new Champ(this);
        recherche.sansEtiquette();
        recherche.setIndice(R.string.parametres_recherche_indice);
        recherche.edition().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int debut,
                                        int nombre, int apres) {
                // seul le texte après changement compte
            }

            @Override
            public void onTextChanged(CharSequence s, int debut,
                                      int avant, int nombre) {
                // lu dans afterTextChanged
            }

            @Override
            public void afterTextChanged(Editable s) {
                requete = String.valueOf(s);
                reconstruireSections();
            }
        });
        LinearLayout.LayoutParams margeRecherche =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        margeRecherche.leftMargin = GraphiteVue.dp(this, Graphite.MARGE);
        margeRecherche.rightMargin =
                GraphiteVue.dp(this, Graphite.MARGE);
        margeRecherche.bottomMargin = GraphiteVue.dp(this, 14);
        racine.addView(recherche, margeRecherche);

        FrameLayout corps = new FrameLayout(this);
        ScrollView defilement = new ScrollView(this);
        colonneSections = new LinearLayout(this);
        colonneSections.setOrientation(LinearLayout.VERTICAL);
        int marge = GraphiteVue.dp(this, Graphite.MARGE);
        colonneSections.setPadding(marge, 0, marge,
                GraphiteVue.dp(this, 32));
        defilement.addView(colonneSections, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        corps.addView(defilement, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        etatVide = new EtatVide(this);
        etatVide.setVisibility(View.GONE);
        corps.addView(etatVide, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        racine.addView(corps, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return racine;
    }

    // ------------------------------------------------------------------
    // Sections et rangées
    // ------------------------------------------------------------------

    private void reconstruireSections() {
        colonneSections.removeAllViews();
        Map<String, String> libelles = libelles();
        List<EtatParametres.Section> vivantes = EtatParametres.filtrer(
                requete, libelles::get);
        boolean aucun = EtatParametres.aucunResultat(requete,
                libelles::get);
        etatVide.setVisibility(aucun ? View.VISIBLE : View.GONE);
        colonneSections.setVisibility(aucun ? View.GONE : View.VISIBLE);
        if (aucun) {
            etatVide.setTitre(getString(R.string.parametres_aucun_resultat,
                    requete));
            etatVide.setAide(R.string.parametres_aucun_resultat_aide);
            return;
        }
        for (EtatParametres.Section section : vivantes) {
            colonneSections.addView(titreSection(section.cle()),
                    margesSection());
            colonneSections.addView(groupe(section),
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private TextView titreSection(String cle) {
        TextView nom = new TextView(this);
        GraphiteVue.typographie(nom, Graphite.LEGENDE);
        nom.setTypeface(GraphiteVue.police(this, 700, false));
        nom.setAllCaps(true);
        nom.setLetterSpacing(0.07f);
        nom.setTextColor(palette.texteEstompe);
        nom.setText(nomSection(cle));
        return nom;
    }

    private LinearLayout.LayoutParams margesSection() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = GraphiteVue.dp(this, 12);
        params.bottomMargin = GraphiteVue.dp(this, 4);
        return params;
    }

    private LinearLayout groupe(EtatParametres.Section section) {
        LinearLayout groupe = new LinearLayout(this);
        groupe.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(palette.surface);
        fond.setStroke(GraphiteVue.dp(this, 1), palette.bordFin);
        fond.setCornerRadius(GraphiteVue.dp(this,
                Graphite.RAYON_CARTE));
        groupe.setBackground(fond);
        int interne = GraphiteVue.dp(this, 14);
        groupe.setPadding(interne, 0, interne, 0);
        boolean premiere = true;
        for (EtatParametres.Rangee rangee : section.rangees()) {
            if (!premiere) {
                separateur(groupe);
            }
            premiere = false;
            groupe.addView(rangee(rangee));
        }
        return groupe;
    }

    private void separateur(LinearLayout groupe) {
        View ligne = new View(this);
        ligne.setBackgroundColor(palette.bordFin);
        groupe.addView(ligne, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
    }

    private View rangee(EtatParametres.Rangee rangee) {
        switch (rangee.genre()) {
            case SEGMENT:
                return rangeeSegment(rangee);
            case STEPPER_ENTIER:
                return rangeeStepper(rangee, false);
            case STEPPER_DECIMAL:
                return rangeeStepper(rangee, true);
            case COMMUTATEUR:
                return rangeeCommutateur(rangee);
            case NUANCIER:
                return rangeeNuancier();
            case MESURE_ACTION:
                return rangeeMesure(rangee, true);
            case MESURE_SEULE:
                return rangeeMesure(rangee, false);
            case ACTION:
                return rangeeAction(rangee);
            case INFO:
                return rangeeInfo(rangee);
            default:
                return rangeeAction(rangee);
        }
    }

    private View rangeeSegment(EtatParametres.Rangee rangee) {
        LinearLayout colonne = new LinearLayout(this);
        colonne.setOrientation(LinearLayout.VERTICAL);
        LinearLayout haut = new LinearLayout(this);
        haut.setOrientation(LinearLayout.HORIZONTAL);
        haut.setGravity(Gravity.CENTER_VERTICAL);
        haut.setPadding(0, GraphiteVue.dp(this, 9), 0,
                GraphiteVue.dp(this, 4));
        haut.addView(libelle(nomRangee(rangee.cle())));
        if (EtatParametres.R_THEME.equals(rangee.cle())) {
            Segment segment = new Segment(this);
            segment.setOptions(List.of(
                    getString(R.string.parametres_theme_sombre),
                    getString(R.string.parametres_theme_systeme),
                    getString(R.string.parametres_theme_clair)),
                    reglages.theme.ordinal());
            segment.setAuditeur((s, index) -> {
                Reglages.Theme choix = Reglages.Theme.values()[index];
                Reglages.ecrireTheme(source, choix);
                reglages = Reglages.lire(source);
                recreate();
            });
            haut.addView(segment, pousseADroite());
        } else {
            Segment segment = new Segment(this);
            segment.setOptions(List.of(
                    getString(R.string.parametres_moteur_service),
                    getString(R.string.parametres_moteur_processus)),
                    reglages.moteur.ordinal());
            segment.setAuditeur((s, index) -> {
                Reglages.Moteur choix = Reglages.Moteur.values()[index];
                Reglages.ecrireMoteur(source, choix);
                reglages = Reglages.lire(source);
            });
            haut.addView(segment, pousseADroite());
        }
        colonne.addView(haut, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        colonne.addView(legende(texteLegende(rangee.cle())),
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        return colonne;
    }

    private View rangeeStepper(EtatParametres.Rangee rangee,
                               boolean decimal) {
        LinearLayout ligne = ligneSimple();
        ligne.addView(libelle(nomRangee(rangee.cle())));
        Stepper stepper = new Stepper(this);
        if (EtatParametres.R_ECHELLE_UI.equals(rangee.cle())) {
            stepper.poser(Reglages.ECHELLE_UI_MIN, Reglages.ECHELLE_UI_MAX,
                    Reglages.ECHELLE_UI_PAS, reglages.echelleUi, false);
            stepper.setAuditeur(valeur -> {
                Reglages.ecrireEchelleUi(source,
                        (int) Math.round(valeur));
                reglages = Reglages.lire(source);
                recreate();
            });
        } else if (EtatParametres.R_FILS.equals(rangee.cle())) {
            stepper.poser(Reglages.FILS_MIN, Reglages.FILS_MAX, 1,
                    reglages.filsParBuild, false);
            stepper.setAuditeur(valeur -> {
                Reglages.ecrireFils(source,
                        (int) Math.round(valeur));
                reglages = Reglages.lire(source);
            });
        } else {
            stepper.poser(Reglages.TAILLE_CODE_MIN,
                    Reglages.TAILLE_CODE_MAX, Reglages.TAILLE_CODE_PAS,
                    reglages.tailleCode, true);
            stepper.setAuditeur(valeur -> {
                Reglages.ecrireTailleCode(source, valeur);
                reglages = Reglages.lire(source);
            });
        }
        ligne.addView(stepper, pousseADroite());
        return ligne;
    }

    private View rangeeCommutateur(EtatParametres.Rangee rangee) {
        LinearLayout ligne = ligneSimple();
        ligne.addView(libelle(nomRangee(rangee.cle())));
        Interrupteur commutateur = new Interrupteur(this);
        boolean etat = EtatParametres.R_NUMEROS.equals(rangee.cle())
                ? reglages.numerosLignes : reglages.retourLigne;
        commutateur.poser(etat);
        commutateur.setAuditeur(actif -> {
            if (EtatParametres.R_NUMEROS.equals(rangee.cle())) {
                Reglages.ecrireNumerosLignes(source, actif);
            } else {
                Reglages.ecrireRetourLigne(source, actif);
            }
            reglages = Reglages.lire(source);
        });
        ligne.addView(commutateur, pousseADroite());
        return ligne;
    }

    private View rangeeNuancier() {
        LinearLayout ligne = ligneSimple();
        ligne.addView(libelle(nomRangee(EtatParametres.R_ACCENT)));
        Nuancier nuancier = new Nuancier(this);
        nuancier.poser(Graphite.ACCENTS, reglages.accent, 26);
        nuancier.setAuditeur(index -> {
            Reglages.ecrireAccent(source, index);
            reglages = Reglages.lire(source);
            recreate();
        });
        ligne.addView(nuancier, pousseADroite());
        return ligne;
    }

    private View rangeeMesure(EtatParametres.Rangee rangee,
                              boolean avecAction) {
        LinearLayout ligne = ligneSimple();
        ligne.addView(libelle(nomRangee(rangee.cle())));
        LinearLayout droite = new LinearLayout(this);
        droite.setOrientation(LinearLayout.HORIZONTAL);
        droite.setGravity(Gravity.CENTER_VERTICAL);
        TextView valeur = new TextView(this);
        GraphiteVue.typographie(valeur, Graphite.NOTE);
        valeur.setTextColor(palette.texteSecondaire);
        Long octetsMesures = octets.get(rangee.cle());
        valeur.setText(getString(R.string.parametres_mo,
                MesuresStockage.formaterMo(
                        octetsMesures == null ? 0L : octetsMesures,
                        Locale.getDefault())));
        droite.addView(valeur);
        if (avecAction) {
            TextView action = actionTexte(
                    getString(R.string.parametres_vider),
                    EtatParametres.R_M2.equals(rangee.cle())
                            ? palette.erreur : palette.accent);
            action.setOnClickListener(vue ->
                    confirmerVidage(rangee.cle()));
            LinearLayout.LayoutParams margeAction =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            margeAction.leftMargin = GraphiteVue.dp(this, 12);
            droite.addView(action, margeAction);
        }
        ligne.addView(droite, pousseADroite());
        return ligne;
    }

    private View rangeeAction(EtatParametres.Rangee rangee) {
        LinearLayout ligne = ligneSimple();
        ligne.addView(libelle(nomRangee(rangee.cle())));
        TextView action = actionTexte(
                texteAction(rangee.cle()), palette.accent);
        action.setOnClickListener(vue -> activer(rangee.cle()));
        ligne.addView(action, pousseADroite());
        return ligne;
    }

    private View rangeeInfo(EtatParametres.Rangee rangee) {
        LinearLayout ligne = ligneSimple();
        ligne.addView(libelle(nomRangee(rangee.cle())));
        TextView valeur = new TextView(this);
        GraphiteVue.typographie(valeur, Graphite.NOTE);
        valeur.setTextColor(palette.texteSecondaire);
        if (EtatParametres.R_VERSION.equals(rangee.cle())) {
            valeur.setText(getString(R.string.parametres_version_valeur,
                    BuildConfig.VERSION_NAME, BuildConfig.JOIDE_SHA));
        } else {
            valeur.setText(DEPOT.replace("https://", ""));
        }
        ligne.addView(valeur, pousseADroite());
        return ligne;
    }

    private LinearLayout ligneSimple() {
        LinearLayout ligne = new LinearLayout(this);
        ligne.setOrientation(LinearLayout.HORIZONTAL);
        ligne.setGravity(Gravity.CENTER_VERTICAL);
        ligne.setPadding(0, GraphiteVue.dp(this, 9), 0,
                GraphiteVue.dp(this, 9));
        return ligne;
    }

    private TextView libelle(CharSequence texte) {
        TextView vue = new TextView(this);
        GraphiteVue.typographie(vue, Graphite.CORPS);
        vue.setTextColor(palette.texte);
        vue.setText(texte);
        return vue;
    }

    /** Le libellé d'une rangée depuis les ressources. */
    private TextView libelle(int idRes) {
        return libelle(getString(idRes));
    }

    private TextView legende(CharSequence texte) {
        TextView vue = new TextView(this);
        GraphiteVue.typographie(vue, Graphite.LEGENDE);
        vue.setTextColor(palette.texteEstompe);
        vue.setPadding(0, 0, 0, GraphiteVue.dp(this, 9));
        vue.setText(texte);
        return vue;
    }

    private TextView actionTexte(CharSequence texte, int couleur) {
        TextView vue = new TextView(this);
        GraphiteVue.typographie(vue, Graphite.NOTE);
        vue.setTypeface(GraphiteVue.police(this, 700, false));
        vue.setTextColor(couleur);
        vue.setClickable(true);
        vue.setText(texte);
        return vue;
    }

    private LinearLayout.LayoutParams pousseADroite() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    // ------------------------------------------------------------------
    // Libellés et textes
    // ------------------------------------------------------------------

    private Map<String, String> libelles() {
        Map<String, String> table = new HashMap<>();
        for (EtatParametres.Section section
                : EtatParametres.sections()) {
            table.put(section.cle(), getString(nomSection(section.cle())));
            for (EtatParametres.Rangee rangee : section.rangees()) {
                table.put(rangee.cle(),
                        getString(nomRangee(rangee.cle())));
            }
        }
        return table;
    }

    private int nomSection(String cle) {
        switch (cle) {
            case EtatParametres.SECT_APPARENCE:
                return R.string.parametres_section_apparence;
            case EtatParametres.SECT_EDITEUR:
                return R.string.parametres_section_editeur;
            case EtatParametres.SECT_BUILD:
                return R.string.parametres_section_build;
            case EtatParametres.SECT_IA:
                return R.string.parametres_section_ia;
            case EtatParametres.SECT_STOCKAGE:
                return R.string.parametres_section_stockage;
            default:
                return R.string.parametres_section_a_propos;
        }
    }

    private int nomRangee(String cle) {
        switch (cle) {
            case EtatParametres.R_THEME:
                return R.string.parametres_theme;
            case EtatParametres.R_ACCENT:
                return R.string.parametres_accent;
            case EtatParametres.R_ECHELLE_UI:
                return R.string.parametres_taille_ui;
            case EtatParametres.R_TAILLE_CODE:
                return R.string.parametres_taille_code;
            case EtatParametres.R_NUMEROS:
                return R.string.parametres_numeros;
            case EtatParametres.R_RETOUR_LIGNE:
                return R.string.parametres_retour_ligne;
            case EtatParametres.R_MOTEUR:
                return R.string.parametres_moteur;
            case EtatParametres.R_FILS:
                return R.string.parametres_fils;
            case EtatParametres.R_PROJETS:
                return R.string.parametres_projets;
            case EtatParametres.R_CIDX:
                return R.string.parametres_cache_cidx;
            case EtatParametres.R_M2:
                return R.string.parametres_m2;
            case EtatParametres.R_VERSION:
                return R.string.parametres_version;
            case EtatParametres.R_LICENCES:
                return R.string.parametres_licences;
            case EtatParametres.R_JOURNAL:
                return R.string.parametres_journal;
            case EtatParametres.R_CLE_ANTHROPIC:
                return R.string.parametres_cle_anthropic;
            case EtatParametres.R_CLE_OPENAI:
                return R.string.parametres_cle_openai;
            case EtatParametres.R_CLE_GEMINI:
                return R.string.parametres_cle_gemini;
            case EtatParametres.R_CLE_OPENROUTER:
                return R.string.parametres_cle_openrouter;
            case EtatParametres.R_MODELE_IA:
                return R.string.parametres_modele_ia;
            case EtatParametres.R_URL_IA:
                return R.string.parametres_url_ia;
            default:
                return R.string.parametres_depot;
        }
    }

    private CharSequence texteLegende(String cle) {
        switch (cle) {
            case EtatParametres.R_THEME:
                return getString(R.string.parametres_theme_legende);
            case EtatParametres.R_MOTEUR:
                return getString(R.string.parametres_moteur_legende);
            case EtatParametres.R_FILS:
                return getString(R.string.parametres_fils_legende);
            default:
                return getString(
                        R.string.parametres_echelle_legende);
        }
    }

    private CharSequence texteAction(String cle) {
        if (EtatParametres.R_DEPOT.equals(cle)) {
            return DEPOT.replace("https://", "");
        }
        if (EtatParametres.R_CLE_ANTHROPIC.equals(cle)) {
            return getString(cles.estPosee(FOURNISSEUR_ANTHROPIC)
                    ? R.string.parametres_cle_posee
                    : R.string.parametres_cle_absente);
        }
        if (EtatParametres.R_CLE_OPENAI.equals(cle)) {
            return getString(cles.estPosee(FOURNISSEUR_OPENAI)
                    ? R.string.parametres_cle_posee
                    : R.string.parametres_cle_absente);
        }
        if (EtatParametres.R_CLE_GEMINI.equals(cle)) {
            return getString(cles.estPosee(FOURNISSEUR_GEMINI)
                    ? R.string.parametres_cle_posee
                    : R.string.parametres_cle_absente);
        }
        if (EtatParametres.R_CLE_OPENROUTER.equals(cle)) {
            return getString(cles.estPosee(FOURNISSEUR_OPENROUTER)
                    ? R.string.parametres_cle_posee
                    : R.string.parametres_cle_absente);
        }
        if (EtatParametres.R_MODELE_IA.equals(cle)) {
            return reglages.modeleIa;
        }
        if (EtatParametres.R_URL_IA.equals(cle)) {
            return getString(reglages.urlIa.isEmpty()
                    ? R.string.parametres_url_absente
                    : R.string.parametres_url_definie);
        }
        return getString(R.string.parametres_consulter);
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void activer(String cle) {
        if (EtatParametres.R_LICENCES.equals(cle)) {
            montrerLicences();
        } else if (EtatParametres.R_JOURNAL.equals(cle)) {
            montrerJournal();
        } else if (EtatParametres.R_DEPOT.equals(cle)) {
            ouvrirDepot();
        } else if (EtatParametres.R_CLE_ANTHROPIC.equals(cle)) {
            gererCle(FOURNISSEUR_ANTHROPIC);
        } else if (EtatParametres.R_CLE_OPENAI.equals(cle)) {
            gererCle(FOURNISSEUR_OPENAI);
        } else if (EtatParametres.R_CLE_GEMINI.equals(cle)) {
            gererCle(FOURNISSEUR_GEMINI);
        } else if (EtatParametres.R_CLE_OPENROUTER.equals(cle)) {
            gererCle(FOURNISSEUR_OPENROUTER);
        } else if (EtatParametres.R_MODELE_IA.equals(cle)) {
            gererModeleIa();
        } else if (EtatParametres.R_URL_IA.equals(cle)) {
            gererUrlIa();
        }
    }

    /** Les identifiants de fournisseur du stockage chiffré. */
    private static final String FOURNISSEUR_ANTHROPIC = "anthropic";
    private static final String FOURNISSEUR_OPENAI = "openai";
    private static final String FOURNISSEUR_GEMINI = "gemini";
    private static final String FOURNISSEUR_OPENROUTER = "openrouter";

    /**
     * La clé d'un fournisseur (lot P, ADR-É11 point 9) : menu
     * honnête — poser/remplacer, effacer si posée, annuler. La clé
     * ne s'affiche JAMAIS en clair, seulement son état.
     */
    private void gererCle(String fournisseur) {
        boolean posee = cles.estPosee(fournisseur);
        CharSequence titre = getString(
                FOURNISSEUR_ANTHROPIC.equals(fournisseur)
                        ? R.string.parametres_cle_anthropic
                        : FOURNISSEUR_GEMINI.equals(fournisseur)
                                ? R.string.parametres_cle_gemini
                                : FOURNISSEUR_OPENROUTER.equals(fournisseur)
                                        ? R.string.parametres_cle_openrouter
                                        : R.string.parametres_cle_openai);
        if (posee) {
            Dialogue.menu(this, palette, titre,
                    new CharSequence[]{
                            getString(R.string.parametres_cle_remplacer),
                            getString(R.string.parametres_cle_effacer)},
                    (dialog, lequel) -> {
                        if (lequel == 0) {
                            saisirCle(fournisseur);
                        } else if (lequel == 1) {
                            effacerCle(fournisseur, titre);
                        }
                    });
        } else {
            saisirCle(fournisseur);
        }
    }

    private void saisirCle(String fournisseur) {
        android.widget.EditText saisie =
                new android.widget.EditText(this);
        saisie.setHint(R.string.parametres_cle_indice);
        saisie.setTypeface(GraphiteVue.police(this, 400, true));
        Dialogue.saisie(this, palette,
                getString(
                        FOURNISSEUR_ANTHROPIC.equals(fournisseur)
                                ? R.string.parametres_cle_anthropic
                                : FOURNISSEUR_OPENROUTER.equals(fournisseur)
                                        ? R.string.parametres_cle_openrouter
                                        : R.string.parametres_cle_openai),
                saisie, R.string.action_annuler, R.string.action_ok,
                (dialog, lequel) -> {
                    if (lequel == DialogInterface.BUTTON_POSITIVE) {
                        String valeur = saisie.getText().toString()
                                .trim();
                        if (valeur.isEmpty()) {
                            Dialogue.information(this, palette,
                                    getString(R.string.parametres_section_ia),
                                    getString(R.string.parametres_cle_vide),
                                    R.string.action_ok);
                            return;
                        }
                        try {
                            cles.poser(fournisseur, valeur);
                        } catch (IOException e) {
                            Dialogue.information(this, palette,
                                    getString(R.string.parametres_section_ia),
                                    getString(R.string.parametres_cle_echec),
                                    R.string.action_ok);
                            return;
                        }
                        reconstruireSections();
                    }
                });
    }

    private void effacerCle(String fournisseur, CharSequence titre) {
        Dialogue.confirmation(this, palette, titre,
                getString(R.string.parametres_cle_effacer_confirme),
                R.string.action_annuler,
                R.string.parametres_cle_effacer,
                (dialog, lequel) -> {
                    try {
                        cles.effacer(fournisseur);
                    } catch (IOException e) {
                        // le fichier part au mieux : l'état se relit
                    }
                    reconstruireSections();
                });
    }

    /** Le modèle par défaut : saisie bornée (jamais vide). */
    private void gererModeleIa() {
        android.widget.EditText saisie =
                new android.widget.EditText(this);
        saisie.setText(reglages.modeleIa);
        saisie.setHint(R.string.parametres_modele_indice);
        Dialogue.saisie(this, palette,
                getString(R.string.parametres_modele_ia), saisie,
                R.string.action_annuler, R.string.action_ok,
                (dialog, lequel) -> {
                    if (lequel == DialogInterface.BUTTON_POSITIVE) {
                        try {
                            Reglages.ecrireModeleIa(source,
                                    saisie.getText().toString());
                            // lot U/7 : le modèle se retient aussi POUR
                            // le fournisseur courant — le modèle partagé
                            // ne traverse jamais les fournisseurs
                            // (manière CodeAssist)
                            Reglages.ecrireModelePour(source,
                                    reglages.fournisseurIa,
                                    saisie.getText().toString());
                        } catch (IllegalArgumentException e) {
                            Dialogue.information(this, palette,
                                    getString(R.string.parametres_modele_ia),
                                    getString(R.string.parametres_valeur_invalide),
                                    R.string.action_ok);
                            return;
                        }
                        reglages = Reglages.lire(source);
                        reconstruireSections();
                    }
                });
    }

    /** L'URL de base OpenAI-compatible : vide ou http(s), bornée. */
    private void gererUrlIa() {
        android.widget.EditText saisie =
                new android.widget.EditText(this);
        saisie.setText(reglages.urlIa);
        saisie.setHint(R.string.parametres_url_indice);
        Dialogue.saisie(this, palette,
                getString(R.string.parametres_url_ia), saisie,
                R.string.action_annuler, R.string.action_ok,
                (dialog, lequel) -> {
                    if (lequel == DialogInterface.BUTTON_POSITIVE) {
                        try {
                            Reglages.ecrireUrlIa(source,
                                    saisie.getText().toString());
                        } catch (IllegalArgumentException e) {
                            Dialogue.information(this, palette,
                                    getString(R.string.parametres_url_ia),
                                    getString(R.string.parametres_valeur_invalide),
                                    R.string.action_ok);
                            return;
                        }
                        reglages = Reglages.lire(source);
                        reconstruireSections();
                    }
                });
    }

    private void ouvrirDepot() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(DEPOT)));
        } catch (android.content.ActivityNotFoundException e) {
            Dialogue.information(this, palette,
                    getString(R.string.parametres_depot),
                    getString(R.string.parametres_depot_echoue, DEPOT),
                    R.string.action_ok);
        }
    }

    private void montrerLicences() {
        fond.execute(() -> {
            String licences = lireAsset("licences/LICENSE") + "\n\n"
                    + lireAsset("licences/NOTICE");
            runOnUiThread(() -> montrerTexte(
                    getString(R.string.parametres_licences), licences));
        });
    }

    private void montrerJournal() {
        fond.execute(() -> {
            try {
                String brut = JournalDiagnostic.lire(
                        () -> Runtime.getRuntime().exec(
                                new String[]{"logcat", "-d", "-t",
                                        "1000"})
                                .getInputStream());
                List<String> lignes = JournalDiagnostic.borner(brut);
                runOnUiThread(() -> {
                    if (lignes.isEmpty()) {
                        Dialogue.information(this, palette,
                                getString(R.string.parametres_journal),
                                getString(
                                        R.string.parametres_journal_vide),
                                R.string.action_ok);
                    } else {
                        montrerTexte(
                                getString(R.string.parametres_journal),
                                String.join("\n", lignes));
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() -> Dialogue.information(this, palette,
                        getString(R.string.parametres_journal),
                        getString(R.string.parametres_journal_echoue,
                                String.valueOf(e.getMessage())),
                        R.string.action_ok));
            }
        });
    }

    /** Un écran texte : dialogue à vue défilante, police mono — jamais un texte coupé. */
    private void montrerTexte(CharSequence titre, String texte) {
        ScrollView defilement = new ScrollView(this);
        TextView contenu = new TextView(this);
        GraphiteVue.typographie(contenu, Graphite.CODE_COMPACT);
        contenu.setTextColor(palette.texteSecondaire);
        int marge = GraphiteVue.dp(this, 20);
        contenu.setPadding(marge, marge / 2, marge, marge);
        contenu.setText(texte);
        defilement.addView(contenu, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        Dialogue.saisieTexte(this, palette, titre, defilement,
                R.string.action_ok, null);
    }

    private String lireAsset(String chemin) {
        try (InputStream flux = getAssets().open(chemin)) {
            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            byte[] tampon = new byte[8192];
            int lu;
            while ((lu = flux.read(tampon)) >= 0) {
                sortie.write(tampon, 0, lu);
            }
            return new String(sortie.toByteArray(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "asset unreadable: " + chemin;
        }
    }

    // ------------------------------------------------------------------
    // Stockage : mesure et vidage
    // ------------------------------------------------------------------

    private void mesurerStockage() {
        fond.execute(() -> {
            Map<String, Long> mesures = new HashMap<>();
            mesures.put(EtatParametres.R_PROJETS, mesurer(
                    dossierProjets()));
            mesures.put(EtatParametres.R_CIDX, mesurer(
                    dossierCidx()));
            mesures.put(EtatParametres.R_M2, mesurer(dossierM2()));
            runOnUiThread(() -> {
                octets = mesures;
                reconstruireSections();
            });
        });
    }

    private static long mesurer(Path dossier) {
        try {
            return MesuresStockage.octets(dossier);
        } catch (IOException e) {
            return 0L;
        }
    }

    private Path dossierProjets() {
        return Paths.get(getFilesDir().getAbsolutePath(), "projets");
    }

    private Path dossierCidx() {
        return Paths.get(getFilesDir().getAbsolutePath(), "cidx");
    }

    private Path dossierM2() {
        return Paths.get(getFilesDir().getAbsolutePath(), "m2");
    }

    private void confirmerVidage(String cle) {
        boolean cidx = EtatParametres.R_CIDX.equals(cle);
        Dialogue.confirmation(this, palette,
                getString(cidx
                        ? R.string.parametres_vider_cidx_titre
                        : R.string.parametres_vider_m2_titre),
                getString(cidx
                        ? R.string.parametres_vider_cidx_message
                        : R.string.parametres_vider_m2_message),
                R.string.action_annuler,
                R.string.parametres_vider,
                (dialog, lequel) -> fond.execute(() -> {
                    Path cible = cidx ? dossierCidx() : dossierM2();
                    try {
                        MesuresStockage.vider(cible);
                    } catch (IOException e) {
                        runOnUiThread(() -> Dialogue.information(
                                this, palette,
                                getString(R.string.import_echoue_titre),
                                getString(R.string.parametres_vider_echoue,
                                        String.valueOf(e.getMessage())),
                                R.string.action_ok));
                    }
                    runOnUiThread(this::mesurerStockage);
                }));
    }
}
