package jo.joide.app.android.ecrans.parametres;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.android.material.button.MaterialButton;

import jo.joide.app.android.BuildConfig;
import jo.joide.app.android.R;
import jo.joide.app.android.databinding.ActivityParametresBinding;
import jo.joide.app.android.databinding.ItemParamActionBinding;
import jo.joide.app.android.databinding.ItemParamCommutateurBinding;
import jo.joide.app.android.databinding.ItemParamGroupeBinding;
import jo.joide.app.android.databinding.ItemParamInfoBinding;
import jo.joide.app.android.databinding.ItemParamMesureBinding;
import jo.joide.app.android.databinding.ItemParamNuancierBinding;
import jo.joide.app.android.databinding.ItemParamSegmentBinding;
import jo.joide.app.android.databinding.ItemParamStepperBinding;
import jo.joide.app.android.parametres.ClesChiffrees;
import jo.joide.app.android.parametres.EtatParametres;
import jo.joide.app.android.parametres.JournalDiagnostic;
import jo.joide.app.android.parametres.KeystoreCles;
import jo.joide.app.android.parametres.MesuresStockage;
import jo.joide.app.android.parametres.PreferencesAndroid;
import jo.joide.app.android.parametres.Reglages;
import jo.joide.app.android.ui.ActiviteJoide;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.Nuancier;
import jo.joide.app.android.ui.PortePopups;
import jo.joide.app.android.ui.Stepper;

/**
 * L'écran de paramètres (lot M, ADR-É8/054 ; racine XML au lot A3 de
 * l'ADR-085) : plein écran, atteint depuis l'accueil (⋮) et l'atelier
 * (⋮). Recherche en tête, sections en cartes groupées, valeurs
 * MESURÉES au disque, actions destructrices à conséquence nominée.
 * Réglages réels seulement : chaque rangée a un consommateur — le
 * thème, l'accent et l'échelle du texte recréent l'écran, les réglages
 * d'éditeur se consomment à l'ouverture suivante de la vue celeris, le
 * moteur et les fils par le lanceur de build.
 *
 * <p>Coque mince (décision 2) : la hiérarchie vit dans
 * {@code activity_parametres.xml} (un layout d'item par
 * {@link EtatParametres.Genre} : groupe, segment, stepper,
 * commutateur, nuancier, mesure, action, info — liste courte et
 * STATIQUE en NestedScrollView + {@code <include>}, décision 11 : la
 * recherche ne fait que cacher des rangées) ; la structure, le
 * filtrage et la franchise vivent dans {@link EtatParametres}, les
 * valeurs et bornes dans {@link Reglages}, les mesures dans
 * {@link MesuresStockage}. {@code MaterialButtonToggleGroup} remplace
 * le kit Segment, {@code MaterialSwitch} le kit Interrupteur (D2) ;
 * {@link Stepper} et {@link Nuancier} restent (sans équivalent
 * Material). Lot A7 (ADR-085 décision 12, D3) : les dialogues d'hier
 * (clés IA, modèle, URL, licences, journal, vidage) deviennent des
 * POPUPS — le menu de clé ANCRÉ à sa rangée, la saisie, la
 * confirmation et l'information centrées sur le voile du layout racine,
 * la page de texte (licences, NOTICE) centrée — portées par le
 * PORTE-POPUPS de l'écran (fermées en onPause/onDestroy, plus AUCUN
 * dialogue).</p>
 */
public final class EcranParametres extends ActiviteJoide {

    /** Le dépôt public du projet (rangée « Dépôt », ADR-É8). */
    private static final String DEPOT = "https://github.com/jjoblab/joide";

    /** La clé de sauvegarde de la position de défilement (recreate()). */
    private static final String CLE_DEFIL = "joide.parametres.defilement";

    /** Les identifiants de fournisseur du stockage chiffré. */
    private static final String FOURNISSEUR_ANTHROPIC = "anthropic";
    private static final String FOURNISSEUR_OPENAI = "openai";
    private static final String FOURNISSEUR_GEMINI = "gemini";
    private static final String FOURNISSEUR_OPENROUTER = "openrouter";

    private final ExecutorService fond = Executors.newSingleThreadExecutor(
            runnable -> {
                Thread fil = new Thread(runnable, "joide-parametres");
                fil.setDaemon(true);
                return fil;
            });

    /** La table clé d'état vers vues d'une rangée (structure statique). */
    private final Map<String, VuesRangee> rangees = new HashMap<>();

    /** La table clé d'état vers vues d'une section (structure statique). */
    private final Map<String, VuesSection> sections = new HashMap<>();

    private ActivityParametresBinding ecran;
    private Graphite.Palette palette;
    private Reglages.Source source;
    private Reglages reglages;
    /** Le stockage chiffré des clés d'API (lot P). */
    private ClesChiffrees cles;

    /** Le porte-popups de l'écran (menus ancrés, cartes centrées sur
     *  le voile du layout racine — lot A7, ADR-085 décision 12). */
    private PortePopups popups;

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
        ecran = monter(ActivityParametresBinding::inflate);
        popups = new PortePopups(ecran.voilePopup);
        monterStructure();
        monterControles();
        rendre();
        mesurerStockage();
    }

    @Override
    protected void onRestoreInstanceState(Bundle etat) {
        super.onRestoreInstanceState(etat);
        // le texte restauré du champ de recherche ne déclenche PAS
        // afterTextChanged : le filtre se relit ici, la recherche
        // survit donc aux recreate() du thème et de l'accent
        requete = String.valueOf(ecran.editionRecherche.getText());
        rendre();
        if (etat.containsKey(CLE_DEFIL)) {
            int position = etat.getInt(CLE_DEFIL);
            // la pose attend le premier passage de mise en page
            ecran.defilementParametres.post(() ->
                    ecran.defilementParametres.scrollTo(0, position));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle etat) {
        super.onSaveInstanceState(etat);
        // le NestedScrollView ne sauvegarde pas sa position : les
        // recreate() du thème, de l'accent et de l'échelle la préservent
        etat.putInt(CLE_DEFIL, ecran.defilementParametres.getScrollY());
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
        fond.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // Structure statique : clé d'état vers vues (le XML fait la mise
    // en page, l'écran traduit les clés ; les valeurs vivent au rendu)
    // ------------------------------------------------------------------

    /** Les vues d'une rangée : racine (visibilité) et séparateur de carte. */
    private record VuesRangee(View racine, View separateur) {
    }

    /** Les vues d'une section : en-tête nommé et carte conteneur. */
    private record VuesSection(View entete, LinearLayout carte) {
    }

    private void monterStructure() {
        section(EtatParametres.SECT_APPARENCE, ecran.sectApparence,
                ecran.groupeApparence);
        section(EtatParametres.SECT_EDITEUR, ecran.sectEditeur,
                ecran.groupeEditeur);
        section(EtatParametres.SECT_BUILD, ecran.sectBuild,
                ecran.groupeBuild);
        section(EtatParametres.SECT_IA, ecran.sectIa, ecran.groupeIa);
        section(EtatParametres.SECT_STOCKAGE, ecran.sectStockage,
                ecran.groupeStockage);
        section(EtatParametres.SECT_A_PROPOS, ecran.sectAPropos,
                ecran.groupeAPropos);

        ecran.sectApparence.titreGroupe.setText(
                R.string.parametres_section_apparence);
        ecran.sectEditeur.titreGroupe.setText(
                R.string.parametres_section_editeur);
        ecran.sectBuild.titreGroupe.setText(
                R.string.parametres_section_build);
        ecran.sectIa.titreGroupe.setText(R.string.parametres_section_ia);
        ecran.sectStockage.titreGroupe.setText(
                R.string.parametres_section_stockage);
        ecran.sectAPropos.titreGroupe.setText(
                R.string.parametres_section_a_propos);

        // la table rangée → vues : structure statique de l'état (les
        // valeurs vivantes se posent au rendu, jamais ici)
        rangee(EtatParametres.R_THEME, ecran.rangTheme);
        rangee(EtatParametres.R_ACCENT, ecran.rangAccent);
        rangee(EtatParametres.R_ECHELLE_UI, ecran.rangEchelleUi);
        rangee(EtatParametres.R_TAILLE_CODE, ecran.rangTailleCode);
        rangee(EtatParametres.R_NUMEROS, ecran.rangNumeros);
        rangee(EtatParametres.R_RETOUR_LIGNE, ecran.rangRetourLigne);
        rangee(EtatParametres.R_MOTEUR, ecran.rangMoteur);
        rangee(EtatParametres.R_FILS, ecran.rangFils);
        rangee(EtatParametres.R_CLE_ANTHROPIC, ecran.rangCleAnthropic);
        rangee(EtatParametres.R_CLE_OPENAI, ecran.rangCleOpenai);
        rangee(EtatParametres.R_CLE_GEMINI, ecran.rangCleGemini);
        rangee(EtatParametres.R_CLE_OPENROUTER, ecran.rangCleOpenrouter);
        rangee(EtatParametres.R_MODELE_IA, ecran.rangModeleIa);
        rangee(EtatParametres.R_URL_IA, ecran.rangUrlIa);
        rangee(EtatParametres.R_PROJETS, ecran.rangProjets);
        rangee(EtatParametres.R_CIDX, ecran.rangCidx);
        rangee(EtatParametres.R_M2, ecran.rangM2);
        rangee(EtatParametres.R_VERSION, ecran.rangVersion);
        rangee(EtatParametres.R_LICENCES, ecran.rangLicences);
        rangee(EtatParametres.R_JOURNAL, ecran.rangJournal);
        rangee(EtatParametres.R_DEPOT, ecran.rangDepot);
    }

    private void section(String cle, ItemParamGroupeBinding entete,
                         LinearLayout carte) {
        sections.put(cle, new VuesSection(entete.getRoot(), carte));
    }

    // Les surcharges de rangee : une par genre d'item (le séparateur
    // de carte vit DANS chaque layout d'item — la structure reste en
    // XML, l'écran n'en connaît que la table)

    private void rangee(String cle, ItemParamSegmentBinding b) {
        vues(cle, b.getRoot(), b.separateurParam);
    }

    private void rangee(String cle, ItemParamStepperBinding b) {
        vues(cle, b.getRoot(), b.separateurParam);
    }

    private void rangee(String cle, ItemParamCommutateurBinding b) {
        vues(cle, b.getRoot(), b.separateurParam);
    }

    private void rangee(String cle, ItemParamNuancierBinding b) {
        vues(cle, b.getRoot(), b.separateurParam);
    }

    private void rangee(String cle, ItemParamMesureBinding b) {
        vues(cle, b.getRoot(), b.separateurParam);
    }

    private void rangee(String cle, ItemParamActionBinding b) {
        vues(cle, b.getRoot(), b.separateurParam);
    }

    private void rangee(String cle, ItemParamInfoBinding b) {
        vues(cle, b.getRoot(), b.separateurParam);
    }

    private void vues(String cle, View racine, View separateur) {
        rangees.put(cle, new VuesRangee(racine, separateur));
    }

    // ------------------------------------------------------------------
    // Montage des contrôles : textes statiques, états, auditeurs
    // ------------------------------------------------------------------

    private void monterControles() {
        ecran.actionRetour.setOnClickListener(vue -> finish());

        ecran.editionRecherche.addTextChangedListener(new TextWatcher() {
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
                rendre();
            }
        });

        monterTheme();
        monterAccent();
        monterEchelleUi();
        monterEditeur();
        monterBuild();
        monterIa();
        monterStockage();
        monterAPropos();
    }

    /** Le segment de thème : trois options, sombre par défaut — le choix recrée l'écran (ADR-048). */
    private void monterTheme() {
        ItemParamSegmentBinding theme = ecran.rangTheme;
        monterSegment(theme, R.string.parametres_theme,
                new int[]{R.string.parametres_theme_sombre,
                        R.string.parametres_theme_systeme,
                        R.string.parametres_theme_clair},
                R.string.parametres_theme_legende);
        theme.segmentParam.check(idOption(theme, reglages.theme.ordinal()));
        poserGrasBascule(theme);
        theme.segmentParam.addOnButtonCheckedListener(
                (group, coche, estCoche) -> {
                    if (!estCoche) {
                        return;
                    }
                    poserGrasBascule(theme);
                    Reglages.ecrireTheme(source,
                            Reglages.Theme.values()[
                                    indexOption(theme, coche)]);
                    reglages = Reglages.lire(source);
                    recreate();
                });
    }

    /** Le nuancier d'accent : cinq pastilles, un seul jeton appliqué. */
    private void monterAccent() {
        ItemParamNuancierBinding accent = ecran.rangAccent;
        accent.libelleParam.setText(R.string.parametres_accent);
        Nuancier nuancier = accent.nuancierParam;
        nuancier.poser(Graphite.ACCENTS, reglages.accent, 26);
        nuancier.setAuditeur(index -> {
            Reglages.ecrireAccent(source, index);
            reglages = Reglages.lire(source);
            recreate();
        });
    }

    /** Le stepper d'échelle du texte UI : 85–130 % par pas de 5 — la pose recrée l'écran. */
    private void monterEchelleUi() {
        ItemParamStepperBinding echelle = ecran.rangEchelleUi;
        monterStepper(echelle, R.string.parametres_taille_ui);
        Stepper stepper = echelle.stepperParam;
        stepper.poser(Reglages.ECHELLE_UI_MIN, Reglages.ECHELLE_UI_MAX,
                Reglages.ECHELLE_UI_PAS, reglages.echelleUi, false);
        stepper.setAuditeur(valeur -> {
            Reglages.ecrireEchelleUi(source,
                    (int) Math.round(valeur));
            reglages = Reglages.lire(source);
            recreate();
        });
    }

    /** L'éditeur : taille du code (stepper décimal), numéros et retour à la ligne (commutateurs). */
    private void monterEditeur() {
        ItemParamStepperBinding taille = ecran.rangTailleCode;
        monterStepper(taille, R.string.parametres_taille_code);
        Stepper stepper = taille.stepperParam;
        stepper.poser(Reglages.TAILLE_CODE_MIN, Reglages.TAILLE_CODE_MAX,
                Reglages.TAILLE_CODE_PAS, reglages.tailleCode, true);
        stepper.setAuditeur(valeur -> {
            Reglages.ecrireTailleCode(source, valeur);
            reglages = Reglages.lire(source);
        });

        ItemParamCommutateurBinding numeros = ecran.rangNumeros;
        monterCommutateur(numeros, R.string.parametres_numeros,
                reglages.numerosLignes);
        numeros.commutateurParam.setOnCheckedChangeListener(
                (bouton, actif) -> {
                    Reglages.ecrireNumerosLignes(source, actif);
                    reglages = Reglages.lire(source);
                });

        ItemParamCommutateurBinding retourLigne = ecran.rangRetourLigne;
        monterCommutateur(retourLigne, R.string.parametres_retour_ligne,
                reglages.retourLigne);
        retourLigne.commutateurParam.setOnCheckedChangeListener(
                (bouton, actif) -> {
                    Reglages.ecrireRetourLigne(source, actif);
                    reglages = Reglages.lire(source);
                });
    }

    /** Le build : moteur par défaut (segment, sans recréation), fils (stepper entier). */
    private void monterBuild() {
        ItemParamSegmentBinding moteur = ecran.rangMoteur;
        monterSegment(moteur, R.string.parametres_moteur,
                new int[]{R.string.parametres_moteur_service,
                        R.string.parametres_moteur_processus},
                R.string.parametres_moteur_legende);
        moteur.segmentParam.check(idOption(moteur,
                reglages.moteur.ordinal()));
        poserGrasBascule(moteur);
        moteur.segmentParam.addOnButtonCheckedListener(
                (group, coche, estCoche) -> {
                    if (!estCoche) {
                        return;
                    }
                    poserGrasBascule(moteur);
                    Reglages.ecrireMoteur(source,
                            Reglages.Moteur.values()[
                                    indexOption(moteur, coche)]);
                    reglages = Reglages.lire(source);
                });

        ItemParamStepperBinding fils = ecran.rangFils;
        monterStepper(fils, R.string.parametres_fils);
        Stepper stepper = fils.stepperParam;
        stepper.poser(Reglages.FILS_MIN, Reglages.FILS_MAX, 1,
                reglages.filsParBuild, false);
        stepper.setAuditeur(valeur -> {
            Reglages.ecrireFils(source, (int) Math.round(valeur));
            reglages = Reglages.lire(source);
        });
    }

    /** L'agent IA : les clés des fournisseurs, le modèle, l'URL (actions à état). */
    private void monterIa() {
        monterAction(ecran.rangCleAnthropic,
                R.string.parametres_cle_anthropic);
        ecran.rangCleAnthropic.actionParam.setOnClickListener(
                vue -> gererCle(vue, FOURNISSEUR_ANTHROPIC));
        monterAction(ecran.rangCleOpenai, R.string.parametres_cle_openai);
        ecran.rangCleOpenai.actionParam.setOnClickListener(
                vue -> gererCle(vue, FOURNISSEUR_OPENAI));
        monterAction(ecran.rangCleGemini, R.string.parametres_cle_gemini);
        ecran.rangCleGemini.actionParam.setOnClickListener(
                vue -> gererCle(vue, FOURNISSEUR_GEMINI));
        monterAction(ecran.rangCleOpenrouter,
                R.string.parametres_cle_openrouter);
        ecran.rangCleOpenrouter.actionParam.setOnClickListener(
                vue -> gererCle(vue, FOURNISSEUR_OPENROUTER));
        monterAction(ecran.rangModeleIa, R.string.parametres_modele_ia);
        ecran.rangModeleIa.actionParam.setOnClickListener(
                vue -> gererModeleIa());
        monterAction(ecran.rangUrlIa, R.string.parametres_url_ia);
        ecran.rangUrlIa.actionParam.setOnClickListener(
                vue -> gererUrlIa());
    }

    /** Le stockage : mesures au disque, CIDX et m2 avec « Vider » (m2 en rouge, maquette). */
    private void monterStockage() {
        monterMesure(ecran.rangProjets, R.string.parametres_projets);
        monterMesure(ecran.rangCidx, R.string.parametres_cache_cidx);
        ecran.rangCidx.actionParam.setOnClickListener(
                vue -> confirmerVidage(EtatParametres.R_CIDX));
        monterMesure(ecran.rangM2, R.string.parametres_m2);
        // la maquette : le vidage m2 est ROUGE, le cidx accent — la
        // couleur se pose par jeton (l'état de la rangée tranche)
        ecran.rangM2.actionParam.setTextColor(
                getColor(R.color.jo_erreur));
        ecran.rangM2.actionParam.setOnClickListener(
                vue -> confirmerVidage(EtatParametres.R_M2));
    }

    /** L'à propos : version (info), licences, journal, dépôt (actions). */
    private void monterAPropos() {
        ItemParamInfoBinding version = ecran.rangVersion;
        version.libelleParam.setText(R.string.parametres_version);
        version.valeurParam.setText(getString(
                R.string.parametres_version_valeur,
                BuildConfig.VERSION_NAME, BuildConfig.JOIDE_SHA));

        monterAction(ecran.rangLicences, R.string.parametres_licences);
        ecran.rangLicences.actionParam.setOnClickListener(
                vue -> montrerLicences());
        monterAction(ecran.rangJournal, R.string.parametres_journal);
        ecran.rangJournal.actionParam.setOnClickListener(
                vue -> montrerJournal());
        monterAction(ecran.rangDepot, R.string.parametres_depot);
        ecran.rangDepot.actionParam.setOnClickListener(
                vue -> ouvrirDepot());
    }

    /** Le libellé et les options d'une rangée SEGMENT (texte, légende ; le coche suit). */
    private void monterSegment(ItemParamSegmentBinding b, int idLibelle,
                               int[] idOptions, int idLegende) {
        b.libelleParam.setText(idLibelle);
        b.optionSegment0.setText(idOptions[0]);
        b.optionSegment1.setText(idOptions[1]);
        if (idOptions.length > 2) {
            b.optionSegment2.setText(idOptions[2]);
        } else {
            // le moteur n'a que deux options (la maquette)
            b.optionSegment2.setVisibility(View.GONE);
        }
        b.legendeParam.setText(idLegende);
    }

    private void monterStepper(ItemParamStepperBinding b, int idLibelle) {
        b.libelleParam.setText(idLibelle);
    }

    /** Le libellé et l'état d'un commutateur — l'état AVANT l'auditeur, jamais écrit par le montage. */
    private void monterCommutateur(ItemParamCommutateurBinding b,
                                    int idLibelle, boolean etat) {
        b.libelleParam.setText(idLibelle);
        b.commutateurParam.setChecked(etat);
    }

    private void monterAction(ItemParamActionBinding b, int idLibelle) {
        b.libelleParam.setText(idLibelle);
    }

    private void monterMesure(ItemParamMesureBinding b, int idLibelle) {
        b.libelleParam.setText(idLibelle);
    }

    /**
     * La graisse de la case cochée (700, maquette) : un
     * ColorStateList porte la couleur, pas la graisse — l'écran la
     * pose, comme le kit Segment le faisait en interne.
     */
    private void poserGrasBascule(ItemParamSegmentBinding b) {
        int coche = b.segmentParam.getCheckedButtonId();
        grasDe(b.optionSegment0, coche);
        grasDe(b.optionSegment1, coche);
        if (b.optionSegment2.getVisibility() == View.VISIBLE) {
            grasDe(b.optionSegment2, coche);
        }
    }

    private void grasDe(MaterialButton bouton, int coche) {
        bouton.setTypeface(GraphiteVue.police(this,
                bouton.getId() == coche ? 700 : 400, false));
    }

    private static int idOption(ItemParamSegmentBinding b, int index) {
        return switch (index) {
            case 0 -> b.optionSegment0.getId();
            case 1 -> b.optionSegment1.getId();
            default -> b.optionSegment2.getId();
        };
    }

    private static int indexOption(ItemParamSegmentBinding b, int coche) {
        if (coche == b.optionSegment0.getId()) {
            return 0;
        }
        if (coche == b.optionSegment1.getId()) {
            return 1;
        }
        return 2;
    }

    // ------------------------------------------------------------------
    // Rendu : visibilité par la recherche, valeurs vivantes
    // ------------------------------------------------------------------

    private void rendre() {
        Map<String, String> libelles = libelles();
        List<EtatParametres.Section> vivantes = EtatParametres.filtrer(
                requete, libelles::get);
        boolean aucun = EtatParametres.aucunResultat(requete,
                libelles::get);
        ecran.etatVideParametres.setVisibility(
                aucun ? View.VISIBLE : View.GONE);
        ecran.defilementParametres.setVisibility(
                aucun ? View.GONE : View.VISIBLE);
        if (aucun) {
            ecran.etatVideParametres.setTitre(getString(
                    R.string.parametres_aucun_resultat, requete));
            ecran.etatVideParametres.setAide(
                    R.string.parametres_aucun_resultat_aide);
            return;
        }
        Map<String, EtatParametres.Section> parCle = new HashMap<>();
        for (EtatParametres.Section section : vivantes) {
            parCle.put(section.cle(), section);
        }
        for (EtatParametres.Section section
                : EtatParametres.sections()) {
            poserSection(section, parCle.get(section.cle()));
        }
        poserValeurs();
    }

    /** Pose la visibilité d'une section (en-tête + carte) et de ses rangées. */
    private void poserSection(EtatParametres.Section structure,
                              EtatParametres.Section vivante) {
        VuesSection vue = sections.get(structure.cle());
        boolean sectionVivante = vivante != null;
        vue.entete().setVisibility(
                sectionVivante ? View.VISIBLE : View.GONE);
        vue.carte().setVisibility(
                sectionVivante ? View.VISIBLE : View.GONE);
        if (!sectionVivante) {
            return;
        }
        Set<String> clesVives = new HashSet<>();
        for (EtatParametres.Rangee rangee : vivante.rangees()) {
            clesVives.add(rangee.cle());
        }
        // le séparateur s'arrête avant la DERNIÈRE rangée vivante
        String derniere = EtatParametres.derniereRangee(vivante);
        for (EtatParametres.Rangee rangee : structure.rangees()) {
            VuesRangee vueRangee = rangees.get(rangee.cle());
            boolean visible = clesVives.contains(rangee.cle());
            vueRangee.racine().setVisibility(
                    visible ? View.VISIBLE : View.GONE);
            vueRangee.separateur().setVisibility(
                    visible && !rangee.cle().equals(derniere)
                            ? View.VISIBLE : View.GONE);
        }
    }

    /** Les valeurs vivantes : mesures disque, états des clés, modèle, URL. */
    private void poserValeurs() {
        poserMesure(ecran.rangProjets, EtatParametres.R_PROJETS, false);
        poserMesure(ecran.rangCidx, EtatParametres.R_CIDX, true);
        poserMesure(ecran.rangM2, EtatParametres.R_M2, true);

        ecran.rangCleAnthropic.actionParam.setText(
                texteAction(EtatParametres.R_CLE_ANTHROPIC));
        ecran.rangCleOpenai.actionParam.setText(
                texteAction(EtatParametres.R_CLE_OPENAI));
        ecran.rangCleGemini.actionParam.setText(
                texteAction(EtatParametres.R_CLE_GEMINI));
        ecran.rangCleOpenrouter.actionParam.setText(
                texteAction(EtatParametres.R_CLE_OPENROUTER));
        ecran.rangModeleIa.actionParam.setText(
                texteAction(EtatParametres.R_MODELE_IA));
        ecran.rangUrlIa.actionParam.setText(
                texteAction(EtatParametres.R_URL_IA));
        ecran.rangLicences.actionParam.setText(
                texteAction(EtatParametres.R_LICENCES));
        ecran.rangJournal.actionParam.setText(
                texteAction(EtatParametres.R_JOURNAL));
        ecran.rangDepot.actionParam.setText(
                texteAction(EtatParametres.R_DEPOT));
    }

    private void poserMesure(ItemParamMesureBinding b, String cle,
                              boolean avecAction) {
        Long octetsMesures = octets.get(cle);
        b.valeurParam.setText(getString(R.string.parametres_mo,
                MesuresStockage.formaterMo(
                        octetsMesures == null ? 0L : octetsMesures,
                        Locale.getDefault())));
        b.actionParam.setVisibility(
                avecAction ? View.VISIBLE : View.GONE);
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
    // Actions (les dialogues existants attendent A7, D3)
    // ------------------------------------------------------------------

    /**
     * La clé d'un fournisseur (lot P, ADR-É11 point 9) : menu
     * honnête — poser/remplacer, effacer si posée, annuler. La clé
     * ne s'affiche JAMAIS en clair, seulement son état.
     */
    private void gererCle(View ancre, String fournisseur) {
        boolean posee = cles.estPosee(fournisseur);
        CharSequence titre = getString(titreCle(fournisseur));
        if (posee) {
            // le menu de clé, ANCRÉ à la rangée (lot A7) : poser/
            // remplacer, effacer si posée, annuler — le tap FERME le
            // menu PUIS agit
            List<PortePopups.EntreeMenu> entrees = new ArrayList<>();
            entrees.add(new PortePopups.EntreeMenu(
                    getString(R.string.parametres_cle_remplacer),
                    () -> saisirCle(fournisseur)));
            entrees.add(new PortePopups.EntreeMenu(
                    getString(R.string.parametres_cle_effacer),
                    () -> effacerCle(fournisseur, titre)));
            popups.menu(ancre, titre, 240, entrees);
        } else {
            saisirCle(fournisseur);
        }
    }

    /** La saisie d'une clé : la SAISIE centrée sur voile (lot A7,
     *  clavier requis) — le champ du popup (champ_saisie_param),
     *  monospace, jamais une clé affichée en clair. */
    private void saisirCle(String fournisseur) {
        EditText champ = popups.saisie(getString(titreCle(fournisseur)),
                null, getString(R.string.action_annuler),
                getString(R.string.action_ok),
                saisie -> poserCle(fournisseur, saisie.getText()
                        .toString().trim()));
        champ.setHint(R.string.parametres_cle_indice);
        champ.setTypeface(GraphiteVue.police(this, 400, true));
    }

    /** La clé validée : posée au coffre, RELUE (le 2026-11-18 : «
     *  posée » doit VRAIMENT vouloir dire posée — un coffre qui
     *  refuse la relecture est nommé, jamais avalé). */
    private void poserCle(String fournisseur, String valeur) {
        if (valeur.isEmpty()) {
            popups.information(getString(R.string.parametres_section_ia),
                    getString(R.string.parametres_cle_vide),
                    getString(R.string.action_ok));
            return;
        }
        try {
            cles.poser(fournisseur, valeur);
            if (!valeur.equals(cles.lire(fournisseur))) {
                popups.information(
                        getString(R.string.parametres_section_ia),
                        getString(R.string.parametres_cle_echec),
                        getString(R.string.action_ok));
                return;
            }
        } catch (IOException e) {
            popups.information(getString(R.string.parametres_section_ia),
                    getString(R.string.parametres_cle_echec),
                    getString(R.string.action_ok));
            return;
        }
        rendre();
    }

    /** L'effacement d'une clé : la CONFIRMATION centrée sur voile
     *  (lot A7) — le valideur part après la fermeture. */
    private void effacerCle(String fournisseur, CharSequence titre) {
        popups.confirmation(titre,
                getString(R.string.parametres_cle_effacer_confirme),
                getString(R.string.action_annuler),
                getString(R.string.parametres_cle_effacer),
                () -> {
                    try {
                        cles.effacer(fournisseur);
                    } catch (IOException e) {
                        // le fichier part au mieux : l'état se relit
                    }
                    rendre();
                });
    }

    /** Le titre de la rangée d'un fournisseur. */
    private int titreCle(String fournisseur) {
        if (FOURNISSEUR_ANTHROPIC.equals(fournisseur)) {
            return R.string.parametres_cle_anthropic;
        }
        if (FOURNISSEUR_GEMINI.equals(fournisseur)) {
            return R.string.parametres_cle_gemini;
        }
        if (FOURNISSEUR_OPENROUTER.equals(fournisseur)) {
            return R.string.parametres_cle_openrouter;
        }
        return R.string.parametres_cle_openai;
    }

    /** Le modèle par défaut : la SAISIE centrée sur voile (lot A7),
     *  préremplie du modèle courant — bornée (jamais vide). */
    private void gererModeleIa() {
        EditText champ = popups.saisie(
                getString(R.string.parametres_modele_ia), null,
                getString(R.string.action_annuler),
                getString(R.string.action_ok),
                saisie -> {
                    String valeur = saisie.getText().toString();
                    try {
                        Reglages.ecrireModeleIa(source, valeur);
                        // lot U/7 : le modèle se retient aussi POUR
                        // le fournisseur courant — le modèle partagé
                        // ne traverse jamais les fournisseurs
                        // (manière CodeAssist)
                        Reglages.ecrireModelePour(source,
                                reglages.fournisseurIa, valeur);
                    } catch (IllegalArgumentException e) {
                        popups.information(
                                getString(R.string.parametres_modele_ia),
                                getString(R.string.parametres_valeur_invalide),
                                getString(R.string.action_ok));
                        return;
                    }
                    reglages = Reglages.lire(source);
                    rendre();
                });
        champ.setText(reglages.modeleIa);
        champ.setHint(R.string.parametres_modele_indice);
    }

    /** L'URL de base OpenAI-compatible : la SAISIE centrée sur voile
     *  (lot A7), préremplie — vide ou http(s), bornée. */
    private void gererUrlIa() {
        EditText champ = popups.saisie(
                getString(R.string.parametres_url_ia), null,
                getString(R.string.action_annuler),
                getString(R.string.action_ok),
                saisie -> {
                    try {
                        Reglages.ecrireUrlIa(source,
                                saisie.getText().toString());
                    } catch (IllegalArgumentException e) {
                        popups.information(
                                getString(R.string.parametres_url_ia),
                                getString(R.string.parametres_valeur_invalide),
                                getString(R.string.action_ok));
                        return;
                    }
                    reglages = Reglages.lire(source);
                    rendre();
                });
        champ.setText(reglages.urlIa);
        champ.setHint(R.string.parametres_url_indice);
    }

    private void ouvrirDepot() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(DEPOT)));
        } catch (android.content.ActivityNotFoundException e) {
            popups.information(
                    getString(R.string.parametres_depot),
                    getString(R.string.parametres_depot_echoue, DEPOT),
                    getString(R.string.action_ok));
        }
    }

    private void montrerLicences() {
        fond.execute(() -> {
            String licences = lireAsset("licences/LICENSE") + "\n\n"
                    + lireAsset("licences/NOTICE");
            runOnUiThread(() -> {
                if (isDestroyed()) {
                    return;
                }
                montrerTexte(
                        getString(R.string.parametres_licences), licences);
            });
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
                    if (isDestroyed()) {
                        return;
                    }
                    if (lignes.isEmpty()) {
                        popups.information(
                                getString(R.string.parametres_journal),
                                getString(
                                        R.string.parametres_journal_vide),
                                getString(R.string.action_ok));
                    } else {
                        montrerTexte(
                                getString(R.string.parametres_journal),
                                String.join("\n", lignes));
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    popups.information(
                            getString(R.string.parametres_journal),
                            getString(R.string.parametres_journal_echoue,
                                    String.valueOf(e.getMessage())),
                            getString(R.string.action_ok));
                });
            }
        });
    }

    /** Un écran texte : la PAGE centrée sur voile (lot A7, ADR-É8 —
     *  police mono, jamais un texte coupé) : licences, NOTICE, journal
     *  de diagnostic. */
    private void montrerTexte(CharSequence titre, String texte) {
        popups.page(titre, texte, getString(R.string.action_ok));
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
                if (isDestroyed()) {
                    return;
                }
                octets = mesures;
                rendre();
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

    /** Le vidage de CIDX ou de m2 : la CONFIRMATION centrée sur voile
     *  (lot A7) — le valideur part après la fermeture. */
    private void confirmerVidage(String cle) {
        boolean cidx = EtatParametres.R_CIDX.equals(cle);
        popups.confirmation(
                getString(cidx
                        ? R.string.parametres_vider_cidx_titre
                        : R.string.parametres_vider_m2_titre),
                getString(cidx
                        ? R.string.parametres_vider_cidx_message
                        : R.string.parametres_vider_m2_message),
                getString(R.string.action_annuler),
                getString(R.string.parametres_vider),
                () -> fond.execute(() -> {
                    Path cible = cidx ? dossierCidx() : dossierM2();
                    try {
                        MesuresStockage.vider(cible);
                    } catch (IOException e) {
                        runOnUiThread(() -> {
                            if (isDestroyed()) {
                                return;
                            }
                            popups.information(
                                    getString(R.string.import_echoue_titre),
                                    getString(R.string.parametres_vider_echoue,
                                            String.valueOf(e.getMessage())),
                                    getString(R.string.action_ok));
                        });
                    }
                    runOnUiThread(this::mesurerStockage);
                }));
    }
}
