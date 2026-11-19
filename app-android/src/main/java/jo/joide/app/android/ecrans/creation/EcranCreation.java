package jo.joide.app.android.ecrans.creation;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.activity.OnBackPressedCallback;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import jo.joide.app.android.ImporteurProjet;
import jo.joide.app.android.R;
import jo.joide.app.android.accueil.ProjetsRecents;
import jo.joide.app.android.creation.EcrireProjet;
import jo.joide.app.android.creation.EtatCreation;
import jo.joide.app.android.creation.GenerateurProjet;
import jo.joide.app.android.creation.ModeleProjet;
import jo.joide.app.android.databinding.ActivityCreationBinding;
import jo.joide.app.android.databinding.CreationEtapeIdentiteBinding;
import jo.joide.app.android.databinding.CreationEtapeModeleBinding;
import jo.joide.app.android.databinding.CreationEtapeRecapitulatifBinding;
import jo.joide.app.android.databinding.ItemBanniereBinding;
import jo.joide.app.android.databinding.ItemLigneArboBinding;
import jo.joide.app.android.databinding.ItemLigneRecapBinding;
import jo.joide.app.android.databinding.ItemModeleProjetBinding;
import jo.joide.app.android.databinding.ItemPuceModuleBinding;
import jo.joide.app.android.ecrans.Ecrans;
import jo.joide.app.android.ui.ActiviteJoide;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.choix.ChoixCarte;
import jo.joide.platform.Clock;

/**
 * L'assistant de création de projet (ADR-É4, lot I ; racine XML au lot
 * A4 de l'ADR-085) : trois étapes — Modèle, Identité, Récapitulatif —
 * la progression toujours visible. Le retour système remonte d'une
 * étape ; depuis l'étape 1 il quitte l'assistant : rien n'est créé
 * avant « Créer le projet ». La création écrit dans le stockage privé
 * de JoIDE, tout ou rien ({@link EcrireProjet}), inscrit le projet dans
 * la liste d'accueil et ouvre l'atelier sur le fichier d'entrée du
 * modèle.
 *
 * <p>Coque mince (décision 2) : la hiérarchie vit dans
 * {@code activity_creation.xml} — un layout D'ÉTAPE par
 * {@link EtatCreation.Etape} inclus dans un {@code ViewFlipper} (le
 * corps de l'assistant : l'ordinal de l'étape est l'indice affiché, un
 * seul enfant visible par construction) ; les cartes de modèle, les
 * puces de module, les lignes de fiche et d'arborescence se GONFLENT
 * par binding ({@code item_*.xml}, listes très courtes et bornées —
 * décision 11) ; la machine à états, les validations et le
 * récapitulatif exact vivent dans {@link EtatCreation} (pur, testé
 * JVM), la génération dans {@link GenerateurProjet}, les modèles sont
 * des fichiers TOML de {@code assets/modeles/} (ADR-050). Le kit
 * cède la place à Material : {@code MaterialCardView} (modèles),
 * {@code TextInputLayout} (identité), {@code Chip} (catégories et
 * modules — remplace le kit Puce, teintes de {@code ChoixPuce} en
 * {@code res/color}), {@code MaterialButtonToggleGroup} (minSdk, motif
 * A3) ; la tuile et les icônes restent des vues du kit dessinées au
 * Canvas (sans équivalent). La génération court sur l'exécuteur de
 * l'écran avec garde de cycle de vie (I8) : {@code isDestroyed()} et
 * binding MUET après {@code onDestroy()}.</p>
 *
 * <p>Lot A8 (ADR-085 décision 10) : l'état d'UI de l'assistant survit
 * aux recréations (rotation, thème, langue) — l'export de
 * l'{@link EtatCreation} se sauve en {@code onSaveInstanceState} et
 * s'importe au {@code onCreate} AVANT le rendu (A4 : « A8 fera la
 * sauvegarde ») ; les saisies se re-posent depuis l'état (une seule
 * vérité : {@code saveEnabled=false} reste — la restauration du
 * framework écrirait un deuxième avis), le DÉFILEMENT se restaure
 * par le cadre (les racines d'étape sont des {@code NestedScrollView},
 * id portés par les {@code <include>}).</p>
 */
public final class EcranCreation extends ActiviteJoide {

    /**
     * Le fil de fond de l'écran (I8) : l'écriture du projet (tout ou
     * rien) n'a rien à faire sur le fil UI — les résultats y reviennent
     * par {@code runOnUiThread} avec la garde de cycle de vie.
     */
    private final ExecutorService fond = Executors.newSingleThreadExecutor(
            runnable -> {
                Thread fil = new Thread(runnable, "joide-creation");
                fil.setDaemon(true);
                return fil;
            });

    /**
     * Le retour système (décision 13 — OnBackPressedDispatcher) remonte
     * d'une étape ; depuis l'étape 1, la garde se désarme et le cadre
     * ferme l'assistant : rien n'est créé avant le bouton.
     */
    private final OnBackPressedCallback retourSysteme =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (etat.surPremiereEtape()) {
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                        return;
                    }
                    etat.precedente();
                    rendre();
                }
            };

    private ActivityCreationBinding ecran;
    private CreationEtapeModeleBinding etapeModele;
    private CreationEtapeIdentiteBinding etapeIdentite;
    private CreationEtapeRecapitulatifBinding etapeRecap;

    private Graphite.Palette palette;
    private EtatCreation etat;

    /** Les clés Bundle de l'état d'UI de l'assistant (lot A8,
     *  décision 10 — la coque traduit, l'état pur ne touche pas
     *  Bundle). */
    private static final String CLE_ETAPE = "joide.creation.etape";
    private static final String CLE_FILTRE = "joide.creation.filtre";
    private static final String CLE_CATEGORIE = "joide.creation.categorie";
    private static final String CLE_MODELE = "joide.creation.modele";
    private static final String CLE_NOM = "joide.creation.nom";
    private static final String CLE_PAQUET = "joide.creation.paquet";
    private static final String CLE_PAQUET_TOUCHE =
            "joide.creation.paquetTouche";
    private static final String CLE_MINSDK = "joide.creation.minSdk";
    private static final String CLE_MODULES = "joide.creation.modules";

    /**
     * Vrai pendant les re-poses programmatiques : les écouteurs de
     * saisie et de segment se taisent (seul l'utilisateur les déclenche).
     */
    private boolean poseEnCours;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        palette = GraphiteVue.palette(this);
        GraphiteVue.fondEcran(this, palette);
        etat = new EtatCreation(chargerModeles(),
                ProjetsRecents.lire(fichier()).entrees());
        // lot A8 (décision 10) : l'état d'assistant restauré AVANT le
        // montage et le rendu — l'assistant reprend EXACTEMENT où il
        // était (étape, recherche, catégorie, modèle, identité,
        // modules) ; les saisies se re-posent depuis l'état
        if (state != null && state.containsKey(CLE_ETAPE)) {
            etat.importer(depuis(state));
        }
        ecran = monter(ActivityCreationBinding::inflate);
        etapeModele = ecran.etapeModele;
        etapeIdentite = ecran.etapeIdentite;
        etapeRecap = ecran.etapeRecapitulatif;
        brancher();
        if (state != null && state.containsKey(CLE_ETAPE)) {
            // la recherche restaurée se re-pose en silence (le texte
            // frappé vit dans l'état — le rendu s'en sert, jamais le
            // framework)
            poseEnCours = true;
            etapeModele.editionRecherche.setText(etat.filtre());
            poseEnCours = false;
        }
        rendre();
        getOnBackPressedDispatcher().addCallback(this, retourSysteme);
    }

    /**
     * L'état d'UI de l'assistant (décision 10, lot A8 — A4 : « A8 fera
     * la sauvegarde ») : export de l'{@link EtatCreation}, traduit en
     * Bundle par la coque. Les recréations (rotation, thème, langue)
     * ne réinitialisent plus l'assistant.
     */
    @Override
    protected void onSaveInstanceState(Bundle sortie) {
        super.onSaveInstanceState(sortie);
        if (etat == null) {
            return;
        }
        EtatCreation.Export export = etat.exporter();
        sortie.putInt(CLE_ETAPE, export.etape().ordinal());
        sortie.putString(CLE_FILTRE, export.filtre());
        sortie.putString(CLE_CATEGORIE, export.categorie() == null ? ""
                : export.categorie().name());
        sortie.putString(CLE_MODELE, export.modeleChoisi());
        sortie.putString(CLE_NOM, export.nom());
        sortie.putString(CLE_PAQUET, export.paquet());
        sortie.putBoolean(CLE_PAQUET_TOUCHE, export.paquetTouche());
        sortie.putInt(CLE_MINSDK, export.minSdk());
        sortie.putStringArrayList(CLE_MODULES,
                new ArrayList<>(export.modules()));
    }

    /** Traduit un Bundle sauvegardé en export d'état pur : les
     *  ordinaux hors bornes et les noms inconnus se normalisent
     *  (garde d'audit A8 — un Bundle écrit par une AUTRE version de
     *  l'application, réordonnée ou renommée, ne fait jamais planter
     *  le réveil : l'état dégradé vaut mieux que l'écran mort). */
    private static EtatCreation.Export depuis(Bundle state) {
        EtatCreation.Etape[] etapes = EtatCreation.Etape.values();
        int ordinal = state.getInt(CLE_ETAPE);
        EtatCreation.Etape etape = ordinal >= 0 && ordinal < etapes.length
                ? etapes[ordinal] : EtatCreation.Etape.MODELE;
        String categorie = state.getString(CLE_CATEGORIE, "");
        ModeleProjet.Categorie categorieLue = null;
        if (!categorie.isEmpty()) {
            try {
                categorieLue = ModeleProjet.Categorie.valueOf(categorie);
            } catch (IllegalArgumentException e) {
                // catégorie inconnue de CETTE version : toutes (null)
            }
        }
        return new EtatCreation.Export(
                etape,
                state.getString(CLE_FILTRE, ""),
                categorieLue,
                state.getString(CLE_MODELE),
                state.getString(CLE_NOM, ""),
                state.getString(CLE_PAQUET, ""),
                state.getBoolean(CLE_PAQUET_TOUCHE),
                state.getInt(CLE_MINSDK),
                state.getStringArrayList(CLE_MODULES));
    }

    @Override
    protected void onDestroy() {
        // I8 : l'exécuteur tombe, le binding devient MUET — un retour
        // de fil de fond après la fermeture ne touche plus aucune vue
        fond.shutdownNow();
        ecran = null;
        etapeModele = null;
        etapeIdentite = null;
        etapeRecap = null;
        super.onDestroy();
    }

    private Path fichier() {
        return getFilesDir().toPath().resolve(ProjetsRecents.FICHIER);
    }

    /**
     * Les modèles exploitables des assets (ADR-050) : triés par nom, un
     * modèle inutilisable se voit au journal, jamais une galerie qui le
     * montre à moitié.
     */
    private List<ModeleProjet.Modele> chargerModeles() {
        List<ModeleProjet.Modele> modeles = new ArrayList<>();
        String[] noms;
        try {
            noms = getAssets().list("modeles");
        } catch (IOException e) {
            noms = new String[0];
        }
        if (noms == null) {
            return modeles;
        }
        Arrays.sort(noms);
        for (String nom : noms) {
            if (!nom.endsWith(".toml")) {
                continue;
            }
            String texte;
            try (java.io.InputStream entree =
                    getAssets().open("modeles/" + nom)) {
                // InputStream.readAllBytes exige le niveau 33 : la
                // lecture passe par un tampon (le précédent P8)
                java.io.ByteArrayOutputStream sortie =
                        new java.io.ByteArrayOutputStream();
                byte[] tampon = new byte[8192];
                int lu;
                while ((lu = entree.read(tampon)) >= 0) {
                    sortie.write(tampon, 0, lu);
                }
                texte = sortie.toString(StandardCharsets.UTF_8.name());
            } catch (IOException e) {
                android.util.Log.w("joide.creation",
                        "modele illisible : " + nom + " : " + e.getMessage());
                continue;
            }
            ModeleProjet.Analyse analyse = ModeleProjet.analyser(texte);
            if (analyse.exploitable()) {
                modeles.add(analyse.modele());
            } else {
                android.util.Log.w("joide.creation",
                        "modele rejete : " + nom + " : "
                                + analyse.problemes());
            }
        }
        return modeles;
    }

    // ------------------------------------------------------------------
    // Câblage (une seule fois) : écouteurs et textes immuables
    // ------------------------------------------------------------------

    private void brancher() {
        ecran.actionRetour.setOnClickListener(vue -> retour());
        ecran.boutonRetour.setOnClickListener(vue -> retour());
        ecran.boutonPrincipal.setOnClickListener(vue -> {
            if (etat.etape() == EtatCreation.Etape.RECAPITULATIF) {
                creer();
            } else {
                etat.suivante();
                rendre();
            }
        });

        // la recherche ne re-filtre que la galerie : le champ, immobile
        // dans le layout, ne perd ni focus ni texte (le code d'hier le
        // reconstruisait vide à chaque changement de catégorie)
        etapeModele.editionRecherche.addTextChangedListener(
                apresSaisie(texte -> {
                    etat.filtrer(texte);
                    majModeles();
                }));
        poserCategorie(etapeModele.puceToutes, null);
        poserCategorie(etapeModele.puceApplications,
                ModeleProjet.Categorie.APPLICATIONS);
        poserCategorie(etapeModele.puceBibliotheques,
                ModeleProjet.Categorie.BIBLIOTHEQUES);
        poserCategorie(etapeModele.puceVide,
                ModeleProjet.Categorie.VIDE);

        etapeIdentite.editionNom.addTextChangedListener(apresSaisie(
                texte -> {
                    if (poseEnCours) {
                        return;
                    }
                    etat.changerNom(texte);
                    suivrePaquet();
                    majValiditeIdentite();
                }));
        etapeIdentite.editionPaquet.addTextChangedListener(apresSaisie(
                texte -> {
                    if (poseEnCours) {
                        return;
                    }
                    etat.changerPaquet(texte);
                    majValiditeIdentite();
                }));

        monterSegmentMinsdk();
        // l'information de stockage ne bouge jamais : posée au montage
        poseBanniere(etapeIdentite.banniereStockage, false,
                getString(R.string.creation_note_stockage));
    }

    /**
     * Une puce de catégorie : cochée → la catégorie, décochée → toutes
     * (la galerie ne reste jamais vide par accident). Le rendu
     * re-coche « Tous » depuis l'état.
     */
    private void poserCategorie(Chip puce,
                               ModeleProjet.Categorie categorie) {
        puce.setOnClickListener(vue -> {
            etat.changerCategorie(puce.isChecked() ? categorie : null);
            rendre();
        });
    }

    /** Les textes et l'écoute du segment minSdk (options immuables). */
    private void monterSegmentMinsdk() {
        List<Integer> niveaux = GenerateurProjet.MIN_SDK;
        etapeIdentite.optionMinsdk0.setText(
                String.valueOf(niveaux.get(0)));
        if (niveaux.size() > 1) {
            etapeIdentite.optionMinsdk1.setText(
                    String.valueOf(niveaux.get(1)));
        } else {
            etapeIdentite.optionMinsdk1.setVisibility(View.GONE);
        }
        etapeIdentite.segmentMinsdk.addOnButtonCheckedListener(
                (group, coche, estCoche) -> {
                    if (!estCoche || poseEnCours) {
                        return;
                    }
                    int index = coche == etapeIdentite.optionMinsdk0.getId()
                            ? 0 : 1;
                    etat.changerMinSdk(niveaux.get(index));
                    poserGrasSegment();
                });
    }

    // ------------------------------------------------------------------
    // Rendu (les vues persistent, l'état décide)
    // ------------------------------------------------------------------

    private void rendre() {
        ecran.compteEtape.setText(String.format(Locale.ROOT, "%d/3",
                etat.etape().numero()));
        poserBarreEtapes();
        masquerErreur();
        // l'ordinal de l'étape EST l'indice du flipper : un seul corps
        // d'étape visible, par construction
        ecran.etapesCreation.setDisplayedChild(etat.etape().ordinal());
        poserBoutons();
        switch (etat.etape()) {
            case MODELE -> majModeles();
            case IDENTITE -> poserIdentite();
            case RECAPITULATIF -> poserRecap();
        }
    }

    /**
     * La barre de progression : l'état fait/active/à venir des numéros
     * et libellés passe par les états activé/sélectionné (les
     * sélecteurs res/color portent les teintes), les jalons par
     * l'état activé.
     */
    private void poserBarreEtapes() {
        TextView[] numeros = {ecran.numEtape1, ecran.numEtape2,
                ecran.numEtape3};
        TextView[] libelles = {ecran.libelleEtape1, ecran.libelleEtape2,
                ecran.libelleEtape3};
        View[] jalons = {ecran.jalon1, ecran.jalon2};
        int courant = etat.etape().ordinal();
        for (int i = 0; i < numeros.length; i++) {
            boolean faite = i < courant;
            boolean active = i == courant;
            numeros[i].setActivated(faite);
            numeros[i].setSelected(active);
            numeros[i].setText(faite
                    ? getText(R.string.etapes_faite)
                    : String.valueOf(i + 1));
            libelles[i].setActivated(faite);
            libelles[i].setSelected(active);
            libelles[i].setTypeface(GraphiteVue.police(this,
                    active ? 600 : 400, false));
        }
        for (int i = 0; i < jalons.length; i++) {
            jalons[i].setActivated(i < courant);
        }
    }

    private void poserBoutons() {
        boolean premiere = etat.surPremiereEtape();
        ecran.boutonRetour.setVisibility(premiere
                ? View.GONE : View.VISIBLE);
        ecran.boutonPrincipal.setText(
                etat.etape() == EtatCreation.Etape.RECAPITULATIF
                        ? R.string.creation_bouton_creer
                        : R.string.creation_bouton_continuer);
        ecran.boutonPrincipal.setEnabled(etat.continuerPossible());
    }

    private void retour() {
        if (etat.surPremiereEtape()) {
            finish();
            return;
        }
        etat.precedente();
        rendre();
    }

    // ---- Étape 1 : la galerie de modèles ----

    /**
     * La galerie : les puces de catégorie se re-cochent depuis l'état,
     * puis les cartes visibles ({@code item_modele_projet}) se gonflent
     * — liste très courte et bornée par les assets, la vue reste mince.
     */
    private void majModeles() {
        etapeModele.puceToutes.setChecked(
                etat.categorie() == null);
        etapeModele.puceApplications.setChecked(
                etat.categorie() == ModeleProjet.Categorie.APPLICATIONS);
        etapeModele.puceBibliotheques.setChecked(
                etat.categorie() == ModeleProjet.Categorie.BIBLIOTHEQUES);
        etapeModele.puceVide.setChecked(
                etat.categorie() == ModeleProjet.Categorie.VIDE);
        etapeModele.colonneModeles.removeAllViews();
        List<ModeleProjet.Modele> visibles = etat.modelesVisibles();
        for (ModeleProjet.Modele modele : visibles) {
            ItemModeleProjetBinding carte =
                    ItemModeleProjetBinding.inflate(getLayoutInflater(),
                            etapeModele.colonneModeles, false);
            lierModele(carte, modele);
            etapeModele.colonneModeles.addView(carte.getRoot());
        }
        etapeModele.aucunModele.setVisibility(
                visibles.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void lierModele(ItemModeleProjetBinding carte,
                            ModeleProjet.Modele modele) {
        boolean choisi = modele.id().equals(etat.modeleChoisi());
        carte.tuileModele.poserType(modele.tuile());
        carte.nomModele.setText(modele.nom());
        carte.descModele.setText(modele.description());
        if (modele.franchise() != null) {
            carte.franchiseModele.setTextColor(palette.alerte);
            carte.franchiseModele.setText(modele.franchise());
        } else {
            carte.franchiseModele.setTextColor(palette.texteEstompe);
            carte.franchiseModele.setText(getString(
                    R.string.creation_note_prefixe,
                    modele.note() == null ? "" : modele.note()));
        }
        // la carte du modèle choisi : tonale (bord accent 35 %, aplat
        // accent 8 % — le dégradé du kit devient un aplat, écart A2),
        // le coche du code d'hier reprend sa taille de maquette
        carte.carteModele.setCardBackgroundColor(ColorStateList.valueOf(
                choisi ? ChoixCarte.tonaleHaut(palette)
                        : palette.surface));
        carte.carteModele.setStrokeColor(ColorStateList.valueOf(
                choisi ? ChoixCarte.tonaleBord(palette)
                        : palette.bordFin));
        carte.cocheModele.setVisibility(
                choisi ? View.VISIBLE : View.INVISIBLE);
        carte.carteModele.setOnClickListener(vue -> {
            etat.selectionnerModele(modele.id());
            rendre();
        });
    }

    // ---- Étape 2 : l'identité (formulaire piloté par le modèle) ----

    private void poserIdentite() {
        ModeleProjet.Modele modele = etat.modele();
        poseEnCours = true;
        etapeIdentite.editionNom.setText(etat.nom());
        etapeIdentite.editionPaquet.setText(etat.paquet());
        poseEnCours = false;

        boolean modules = modele != null && !modele.modules().isEmpty();
        etapeIdentite.blocModules.setVisibility(
                modules ? View.VISIBLE : View.GONE);
        if (modules) {
            poserPucesModules(modele);
        }
        boolean paquet = etat.champsPaquet();
        etapeIdentite.blocPaquet.setVisibility(
                paquet ? View.VISIBLE : View.GONE);
        etapeIdentite.blocMinsdk.setVisibility(
                paquet ? View.VISIBLE : View.GONE);
        if (paquet) {
            poserSegmentMinsdk();
        }
        majValiditeIdentite();
    }

    /**
     * Les puces de module ({@code item_puce_module}) : cochée = module
     * conservé ; un retrait refusé re-coche la puce depuis l'état et
     * nomme le responsable — jamais un refus silencieux.
     */
    private void poserPucesModules(ModeleProjet.Modele modele) {
        ChipGroup groupe = etapeIdentite.pucesModules;
        groupe.removeAllViews();
        for (ModeleProjet.ModuleModele module : modele.modules()) {
            ItemPuceModuleBinding puce = ItemPuceModuleBinding.inflate(
                    getLayoutInflater(), groupe, false);
            puce.puceModule.setText(":" + module.id());
            puce.puceModule.setChecked(
                    etat.modulesChoisis().contains(module.id()));
            puce.puceModule.setOnClickListener(vue -> {
                EtatCreation.Bascule bascule =
                        etat.basculerModule(module.id());
                if (!bascule.faite() && bascule.requisPar() != null) {
                    montrerErreur(getString(
                            R.string.creation_err_module_requis,
                            ":" + bascule.requisPar()));
                } else {
                    masquerErreur();
                }
                // la puce se re-pose : l'état décide (refus compris),
                // la vue déroule
                puce.puceModule.setChecked(
                        etat.modulesChoisis().contains(module.id()));
                majValiditeIdentite();
            });
            groupe.addView(puce.getRoot());
        }
    }

    /** Le segment minSdk se re-coche depuis l'état (muette). */
    private void poserSegmentMinsdk() {
        List<Integer> niveaux = GenerateurProjet.MIN_SDK;
        int index = niveaux.indexOf(etat.minSdk());
        poseEnCours = true;
        etapeIdentite.segmentMinsdk.check(index <= 0
                ? etapeIdentite.optionMinsdk0.getId()
                : etapeIdentite.optionMinsdk1.getId());
        poseEnCours = false;
        poserGrasSegment();
    }

    /** La graisse 700 de la case cochée (concession A3 : pas en style). */
    private void poserGrasSegment() {
        int coche = etapeIdentite.segmentMinsdk.getCheckedButtonId();
        grasDe(etapeIdentite.optionMinsdk0, coche);
        if (etapeIdentite.optionMinsdk1.getVisibility() == View.VISIBLE) {
            grasDe(etapeIdentite.optionMinsdk1, coche);
        }
    }

    private void grasDe(MaterialButton bouton, int coche) {
        bouton.setTypeface(GraphiteVue.police(this,
                bouton.getId() == coche ? 700 : 400, false));
    }

    /**
     * Le paquet suit le nom tant que l'utilisateur ne l'a pas touché :
     * le champ se re-pose en silence (le code d'hier dérivait le paquet
     * sans jamais le montrer).
     */
    private void suivrePaquet() {
        if (etat.suitPaquet() && etat.champsPaquet()) {
            poseEnCours = true;
            etapeIdentite.editionPaquet.setText(etat.paquet());
            poseEnCours = false;
        }
    }

    private TextWatcher apresSaisie(
            java.util.function.Consumer<String> traitement) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b,
                                          int c) {
                // rien : le changement se traite après
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b,
                                      int c) {
                // rien : le changement se traite après
            }

            @Override
            public void afterTextChanged(Editable s) {
                traitement.accept(s.toString());
            }
        };
    }

    /**
     * La validation au fil de la saisie : les erreurs en ligne et le
     * bouton Continuer reflètent l'état, jamais d'alerte tardive.
     */
    private void majValiditeIdentite() {
        if (etat.raisonNom() == EtatCreation.RaisonNom.VIDE) {
            poser(etapeIdentite.erreurNom, R.string.creation_err_nom_vide);
        } else if (etat.raisonNom() == EtatCreation.RaisonNom.LONG) {
            poser(etapeIdentite.erreurNom, R.string.creation_err_nom_long);
        } else {
            etapeIdentite.erreurNom.setVisibility(View.GONE);
        }
        if (etat.modulesValides()) {
            etapeIdentite.erreurModules.setVisibility(View.GONE);
        } else {
            poser(etapeIdentite.erreurModules,
                    R.string.creation_err_modules);
        }
        if (etat.paquetValide()) {
            etapeIdentite.erreurPaquet.setVisibility(View.GONE);
        } else {
            poser(etapeIdentite.erreurPaquet,
                    R.string.creation_err_paquet);
        }
        poserBoutons();
    }

    private void poser(TextView erreur, int idTexte) {
        erreur.setText(idTexte);
        erreur.setVisibility(View.VISIBLE);
    }

    // ---- Étape 3 : le récapitulatif ----

    /**
     * Le récapitulatif exact : les lignes de la fiche
     * ({@code item_ligne_recap}) et l'arborescence générée
     * ({@code item_ligne_arbo}) se gonflent depuis l'état — l'arbre est
     * la liste des fichiers que la génération produira, jamais une
     * promesse séparée de l'écriture.
     */
    private void poserRecap() {
        ModeleProjet.Modele modele = etat.modele();

        etapeRecap.colonneFiche.removeAllViews();
        List<EtatCreation.LigneRecap> lignes = etat.lignesRecap();
        for (int i = 0; i < lignes.size(); i++) {
            EtatCreation.LigneRecap ligne = lignes.get(i);
            ItemLigneRecapBinding rangee = ItemLigneRecapBinding.inflate(
                    getLayoutInflater(), etapeRecap.colonneFiche, false);
            rangee.separateurLigne.setVisibility(
                    i == 0 ? View.GONE : View.VISIBLE);
            rangee.cleLigne.setText(cleRecap(ligne.cle()));
            rangee.valeurLigne.setText(ligne.valeur());
            etapeRecap.colonneFiche.addView(rangee.getRoot());
        }

        etapeRecap.colonneArbo.removeAllViews();
        for (EtatCreation.LigneArbo ligne : etat.arborescence()) {
            ItemLigneArboBinding texte = ItemLigneArboBinding.inflate(
                    getLayoutInflater(), etapeRecap.colonneArbo, false);
            texte.texteLigne.setText(ligne.profondeur() > 0
                    ? "  ".repeat(ligne.profondeur()) + ligne.texte()
                    : ligne.texte());
            texte.texteLigne.setTextColor(couleurArbo(ligne));
            etapeRecap.colonneArbo.addView(texte.getRoot());
        }

        // la franchise du modèle se RÉPÈTE sur le récapitulatif (la
        // carte l'a dite, ADR-É4) ; sans franchise, pas de bannière
        boolean franchise = modele != null && modele.franchise() != null;
        etapeRecap.banniereFranchise.getRoot().setVisibility(
                franchise ? View.VISIBLE : View.GONE);
        if (franchise) {
            poseBanniere(etapeRecap.banniereFranchise, true,
                    modele.franchise());
        }
    }

    /** La couleur d'une ligne d'arborescence (les trois d'hier). */
    private int couleurArbo(EtatCreation.LigneArbo ligne) {
        if (ligne.annotation()) {
            return palette.syntaxe.commentaire;
        }
        return ligne.profondeur() == 0 ? palette.texte
                : palette.texteSecondaire;
    }

    private int cleRecap(String cle) {
        return switch (cle) {
            case "modele" -> R.string.creation_recap_modele;
            case "nom" -> R.string.creation_recap_nom;
            case "modules" -> R.string.creation_recap_modules;
            case "paquet" -> R.string.creation_recap_paquet;
            case "minSdk" -> R.string.creation_recap_minsdk;
            default -> R.string.creation_recap_nom;
        };
    }

    /**
     * La bannière du bas d'étape : alerte (franchise) ou information —
     * les mélanges (rôle à 10 % sur le fond, liseré 35 %, rayon
     * composant) se posent depuis la palette, couleurs calculées
     * (décision 8).
     */
    private void poseBanniere(ItemBanniereBinding banniere,
                              boolean alerte, CharSequence texte) {
        int role = alerte ? palette.alerte : palette.accent;
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(Graphite.melange(role, 0.10f, palette.fond));
        fond.setStroke(GraphiteVue.dp(this, 1),
                Graphite.melange(role, 0.35f, palette.fond));
        fond.setCornerRadius(
                GraphiteVue.dp(this, Graphite.RAYON_COMPOSANT));
        banniere.getRoot().setBackground(fond);
        banniere.texteBanniere.setTextColor(role);
        banniere.texteBanniere.setText(texte);
    }

    // ---- Création (atomique, tout ou rien) ----

    private void creer() {
        GenerateurProjet.Generation generation = etat.generation();
        if (generation == null || !generation.exploitable()) {
            montrerErreur(getString(R.string.creation_echouee,
                    generation == null ? "?"
                            : String.join(", ", generation.problemes())));
            return;
        }
        List<ProjetsRecents.Entree> existants =
                ProjetsRecents.lire(fichier()).entrees();
        String id = ProjetsRecents.idUnique(existants,
                GenerateurProjet.idDossier(etat.nom().trim()));
        Path projets = ImporteurProjet.dossierProjets(getFilesDir());
        // l'écriture court sur l'exécuteur de l'écran (I8), le retour
        // revient au fil UI sous garde de cycle de vie
        fond.execute(() -> {
            EcrireProjet.Resultat resultat = EcrireProjet.ecrire(
                    projets, id, generation.fichiers(),
                    generation.dossiers());
            runOnUiThread(() -> {
                if (isDestroyed() || ecran == null) {
                    // l'écran a fermé pendant l'écriture : le projet
                    // est écrit, l'accueil le relira — jamais une vue
                    // morte touchée
                    return;
                }
                apresCreation(resultat, generation, id);
            });
        });
    }

    private void apresCreation(EcrireProjet.Resultat resultat,
                               GenerateurProjet.Generation generation,
                               String id) {
        if (!resultat.ecrit()) {
            // l'échec reste sur l'étape 3, nominale — jamais un
            // projet à moitié créé passé sous silence
            montrerErreur(getString(R.string.creation_echouee,
                    resultat.erreur()));
            return;
        }
        List<ProjetsRecents.Entree> existants =
                ProjetsRecents.lire(fichier()).entrees();
        ProjetsRecents.Entree entree = new ProjetsRecents.Entree(id,
                etat.nom().trim(), generation.type(), id,
                Clock.system().wallTimeMillis(), generation.nbModules());
        try {
            ProjetsRecents.ecrire(fichier(),
                    ProjetsRecents.ouvrir(existants, entree));
        } catch (IOException e) {
            android.util.Log.w("joide.creation",
                    "projets recents non ecrits : " + e.getMessage());
        }
        // navigation typée (décision 13) : l'atelier s'ouvre sur le
        // fichier d'entrée du modèle — jamais un atelier vide
        startActivity(Ecrans.atelier(this, resultat.chemin().toString(),
                null, generation.fichierEntree()));
        finish();
    }

    // ---- Erreurs en ligne ----

    private void montrerErreur(String texte) {
        ecran.ligneErreur.setText(texte);
        ecran.ligneErreur.setVisibility(View.VISIBLE);
    }

    private void masquerErreur() {
        ecran.ligneErreur.setVisibility(View.GONE);
    }
}
