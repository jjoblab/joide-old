package jo.joide.app.android.ecrans.creation;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import jo.joide.app.android.ui.ActiviteJoide;
import jo.joide.app.android.ImporteurProjet;
import jo.joide.app.android.R;
import jo.joide.app.android.accueil.ProjetsRecents;
import jo.joide.app.android.creation.EcrireProjet;
import jo.joide.app.android.creation.EtatCreation;
import jo.joide.app.android.creation.GenerateurProjet;
import jo.joide.app.android.creation.ModeleProjet;
import jo.joide.app.android.ecrans.atelier.EcranAtelier;
import jo.joide.app.android.ui.Bouton;
import jo.joide.app.android.ui.Carte;
import jo.joide.app.android.ui.Champ;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.Icone;
import jo.joide.app.android.ui.Puce;
import jo.joide.app.android.ui.Segment;
import jo.joide.app.android.ui.choix.ChoixBouton;
import jo.joide.platform.Clock;

/**
 * L'assistant de création de projet (ADR-É4, lot I) : trois étapes —
 * Modèle, Identité, Récapitulatif — la progression toujours visible.
 * Le retour système remonte d'une étape ; depuis l'étape 1 il quitte
 * l'assistant : rien n'est créé avant « Créer le projet ». La
 * création écrit dans le stockage privé de JoIDE, tout ou rien
 * ({@link EcrireProjet}), inscrit le projet dans la liste d'accueil
 * et ouvre l'atelier sur le fichier d'entrée du modèle.
 *
 * <p>Vue mince : la machine à états, les validations et le
 * récapitulatif exact vivent dans {@link EtatCreation} (pur, testé
 * JVM) ; la génération dans {@link GenerateurProjet} ; les modèles
 * sont des fichiers TOML de {@code assets/modeles/} (ADR-050) — un
 * modèle v3 s'ajoute sans toucher cet écran.</p>
 */
public final class EcranCreation extends ActiviteJoide {

    private Graphite.Palette palette;
    private EtatCreation etat;
    private LinearLayout corpsEtape;
    private LinearLayout piedEtape;
    private LinearLayout barreEtapes;
    private TextView compteEtape;
    private TextView ligneErreur;
    private LinearLayout zoneModeles;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        palette = GraphiteVue.palette(this);
        GraphiteVue.fondEcran(this, palette);
        etat = new EtatCreation(chargerModeles(),
                ProjetsRecents.lire(fichier()).entrees());
        setContentView(construire());
        rendre();
    }

    private Path fichier() {
        return getFilesDir().toPath().resolve(ProjetsRecents.FICHIER);
    }

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
                // un modèle inutilisable se voit au journal, jamais
                // une galerie qui le montre à moitié
                android.util.Log.w("joide.creation",
                        "modele rejete : " + nom + " : "
                                + analyse.problemes());
            }
        }
        return modeles;
    }

    private LinearLayout construire() {
        LinearLayout racine = new LinearLayout(this);
        racine.setOrientation(LinearLayout.VERTICAL);

        LinearLayout sousBarre = new LinearLayout(this);
        sousBarre.setOrientation(LinearLayout.HORIZONTAL);
        sousBarre.setGravity(Gravity.CENTER_VERTICAL);
        sousBarre.setPadding(GraphiteVue.dp(this, 12),
                GraphiteVue.dp(this, 10), GraphiteVue.dp(this, 16),
                GraphiteVue.dp(this, 6));
        FrameLayout retour = new FrameLayout(this);
        retour.setOnClickListener(v -> retour());
        Icone iconeRetour = new Icone(this);
        iconeRetour.setGlyphe(Icone.Glyphe.RETOUR);
        iconeRetour.setCouleur(palette.texteSecondaire);
        retour.addView(iconeRetour, new FrameLayout.LayoutParams(
                GraphiteVue.dp(this, 20), GraphiteVue.dp(this, 20),
                Gravity.CENTER));
        sousBarre.addView(retour, new LinearLayout.LayoutParams(
                GraphiteVue.dp(this, 36), GraphiteVue.dp(this, 36)));
        TextView titre = new TextView(this);
        GraphiteVue.typographie(titre, Graphite.TITRE);
        titre.setTextColor(palette.texte);
        titre.setText(R.string.accueil_nouveau_projet);
        sousBarre.addView(titre, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        compteEtape = new TextView(this);
        GraphiteVue.typographie(compteEtape, Graphite.LEGENDE);
        compteEtape.setTextColor(palette.texteEstompe);
        GradientDrawable fondCompte = new GradientDrawable();
        fondCompte.setColor(palette.surfaceHaute);
        fondCompte.setCornerRadius(GraphiteVue.dp(this,
                Graphite.RAYON_PUCE));
        compteEtape.setBackground(fondCompte);
        compteEtape.setPadding(GraphiteVue.dp(this, 10),
                GraphiteVue.dp(this, 2), GraphiteVue.dp(this, 10),
                GraphiteVue.dp(this, 2));
        sousBarre.addView(compteEtape);
        racine.addView(sousBarre);

        barreEtapes = new LinearLayout(this);
        barreEtapes.setOrientation(LinearLayout.HORIZONTAL);
        barreEtapes.setGravity(Gravity.CENTER_VERTICAL);
        barreEtapes.setPadding(GraphiteVue.dp(this, 20), 0,
                GraphiteVue.dp(this, 20), GraphiteVue.dp(this, 10));
        racine.addView(barreEtapes);

        corpsEtape = new LinearLayout(this);
        corpsEtape.setOrientation(LinearLayout.VERTICAL);
        ScrollView defile = new ScrollView(this);
        defile.addView(corpsEtape, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        racine.addView(defile, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        ligneErreur = new TextView(this);
        GraphiteVue.typographie(ligneErreur, Graphite.NOTE);
        ligneErreur.setTextColor(palette.erreur);
        ligneErreur.setPadding(GraphiteVue.dp(this, Graphite.MARGE), 0,
                GraphiteVue.dp(this, Graphite.MARGE), 0);
        ligneErreur.setVisibility(View.GONE);
        racine.addView(ligneErreur);

        piedEtape = new LinearLayout(this);
        piedEtape.setOrientation(LinearLayout.HORIZONTAL);
        piedEtape.setPadding(GraphiteVue.dp(this, Graphite.MARGE),
                GraphiteVue.dp(this, 8), GraphiteVue.dp(this, Graphite.MARGE),
                GraphiteVue.dp(this, 14));
        racine.addView(piedEtape);
        return racine;
    }

    // ---- Rendu par étape ----

    private void rendre() {
        compteEtape.setText(String.format(Locale.ROOT, "%d/3",
                etat.etape().numero()));
        majBarreEtapes();
        masquerErreur();
        erreurNom = null;
        erreurModules = null;
        erreurPaquet = null;
        corpsEtape.removeAllViews();
        piedEtape.removeAllViews();
        switch (etat.etape()) {
            case MODELE -> corpsModele();
            case IDENTITE -> corpsIdentite();
            case RECAPITULATIF -> corpsRecap();
            default -> {
            }
        }
        poserBoutons();
    }

    private void majBarreEtapes() {
        barreEtapes.removeAllViews();
        EtatCreation.Etape[] etapes = EtatCreation.Etape.values();
        for (int i = 0; i < etapes.length; i++) {
            if (i > 0) {
                View jalon = new View(this);
                jalon.setBackgroundColor(
                        i <= etat.etape().ordinal()
                                ? palette.reussite : palette.bord);
                LinearLayout.LayoutParams placements = new LinearLayout
                        .LayoutParams(0, GraphiteVue.dp(this, 2), 1f);
                placements.leftMargin = GraphiteVue.dp(this, 8);
                placements.rightMargin = GraphiteVue.dp(this, 8);
                barreEtapes.addView(jalon, placements);
            }
            barreEtapes.addView(etapeBarre(etapes[i],
                    i == etat.etape().ordinal(),
                    i < etat.etape().ordinal()));
        }
    }

    private LinearLayout etapeBarre(EtatCreation.Etape etape,
                                    boolean active, boolean faite) {
        LinearLayout bloc = new LinearLayout(this);
        bloc.setOrientation(LinearLayout.HORIZONTAL);
        bloc.setGravity(Gravity.CENTER_VERTICAL);
        TextView numero = new TextView(this);
        numero.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        numero.setTypeface(GraphiteVue.police(this, 700, false));
        numero.setGravity(Gravity.CENTER);
        GradientDrawable fond = new GradientDrawable();
        fond.setShape(GradientDrawable.OVAL);
        int cote = GraphiteVue.dp(this, 20);
        if (faite) {
            fond.setColor(palette.reussite);
            fond.setStroke(GraphiteVue.dp(this, 1), palette.reussite);
            numero.setTextColor(palette.texteSurReussite);
            numero.setText(R.string.etapes_faite);
        } else if (active) {
            fond.setColor(palette.accent);
            fond.setStroke(GraphiteVue.dp(this, 1), palette.accent);
            numero.setTextColor(palette.texteSurAccent);
            numero.setText(String.valueOf(etape.numero()));
        } else {
            fond.setColor(0x00000000);
            fond.setStroke(GraphiteVue.dp(this, 1), palette.bord);
            numero.setTextColor(palette.texteEstompe);
            numero.setText(String.valueOf(etape.numero()));
        }
        numero.setBackground(fond);
        bloc.addView(numero, new LinearLayout.LayoutParams(cote, cote));
        TextView libelle = new TextView(this);
        GraphiteVue.typographie(libelle, Graphite.LEGENDE);
        libelle.setTextColor(faite ? palette.texteSecondaire
                : active ? palette.texte : palette.texteEstompe);
        libelle.setTypeface(GraphiteVue.police(this, active ? 600 : 400,
                false));
        libelle.setText(libelleEtape(etape));
        LinearLayout.LayoutParams placements = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        placements.leftMargin = GraphiteVue.dp(this, 7);
        bloc.addView(libelle, placements);
        return bloc;
    }

    private int libelleEtape(EtatCreation.Etape etape) {
        return switch (etape) {
            case MODELE -> R.string.creation_etape_modele;
            case IDENTITE -> R.string.creation_etape_identite;
            case RECAPITULATIF -> R.string.creation_etape_recapitulatif;
        };
    }

    // ---- Étape 1 : la galerie de modèles ----

    private void corpsModele() {
        Champ champRecherche = new Champ(this);
        champRecherche.sansEtiquette();
        champRecherche.setIndice(R.string.creation_recherche_indice);
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
                        etat.filtrer(s.toString());
                        majListeModeles();
                    }
                });
        corpsEtape.addView(champRecherche, marges(0, GraphiteVue.dp(this, 8)));

        LinearLayout puces = new LinearLayout(this);
        puces.setOrientation(LinearLayout.HORIZONTAL);
        ajouterPuceCategorie(puces, null,
                getString(R.string.creation_categorie_toutes));
        ajouterPuceCategorie(puces, ModeleProjet.Categorie.APPLICATIONS,
                getString(R.string.creation_categorie_applications));
        ajouterPuceCategorie(puces, ModeleProjet.Categorie.BIBLIOTHEQUES,
                getString(R.string.creation_categorie_bibliotheques));
        ajouterPuceCategorie(puces, ModeleProjet.Categorie.VIDE,
                getString(R.string.creation_categorie_vide));
        corpsEtape.addView(puces, marges(0, GraphiteVue.dp(this, 10)));

        zoneModeles = new LinearLayout(this);
        zoneModeles.setOrientation(LinearLayout.VERTICAL);
        corpsEtape.addView(zoneModeles);
        majListeModeles();
    }

    private void ajouterPuceCategorie(LinearLayout parent,
                                      ModeleProjet.Categorie categorie,
                                      String libelle) {
        Puce puce = new Puce(this);
        puce.setCategorie(true);
        puce.setText(libelle);
        puce.setPointee(etat.categorie() == categorie);
        puce.setAuditeur((p, pointee) -> {
            if (pointee) {
                etat.changerCategorie(categorie);
            } else {
                // une catégorie décochée revient à toutes : la galerie
                // ne reste jamais vide par accident
                etat.changerCategorie(null);
            }
            rendre();
        });
        LinearLayout.LayoutParams placements = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        placements.rightMargin = GraphiteVue.dp(this, 6);
        parent.addView(puce, placements);
    }

    private void majListeModeles() {
        if (zoneModeles == null) {
            return;
        }
        zoneModeles.removeAllViews();
        List<ModeleProjet.Modele> visibles = etat.modelesVisibles();
        for (ModeleProjet.Modele modele : visibles) {
            zoneModeles.addView(carteModele(modele), marges(0, GraphiteVue.dp(this, 8)));
        }
        if (visibles.isEmpty()) {
            TextView aucun = new TextView(this);
            GraphiteVue.typographie(aucun, Graphite.NOTE);
            aucun.setTextColor(palette.texteSecondaire);
            aucun.setText(R.string.creation_aucun_modele);
            zoneModeles.addView(aucun);
        }
    }

    private View carteModele(ModeleProjet.Modele modele) {
        boolean choisi = modele.id().equals(etat.modeleChoisi());
        Carte carte = new Carte(this);
        carte.setTonale(choisi);
        carte.setTuile(modele.tuile());
        LinearLayout corps = carte.corps();
        TextView nom = Carte.ligneNom(this);
        nom.setText(modele.nom());
        corps.addView(nom);
        TextView description = Carte.ligneMeta(this);
        description.setText(modele.description());
        corps.addView(description);
        TextView franchise = Carte.ligneLegende(this);
        if (modele.franchise() != null) {
            franchise.setTextColor(palette.alerte);
            franchise.setText(modele.franchise());
        } else {
            franchise.setText(getString(R.string.creation_note_prefixe,
                    modele.note() == null ? "" : modele.note()));
        }
        corps.addView(franchise);
        Icone coche = new Icone(this);
        coche.setGlyphe(choisi ? Icone.Glyphe.COCHE : Icone.Glyphe.CROIX);
        coche.setCouleur(choisi ? palette.accent : 0x00000000);
        coche.setVisibility(choisi ? View.VISIBLE : View.INVISIBLE);
        carte.addAccessoire(coche);
        carte.setOnClickListener(v -> {
            etat.selectionnerModele(modele.id());
            rendre();
        });
        carte.setClickable(true);
        return carte;
    }

    // ---- Étape 2 : l'identité (formulaire piloté par le modèle) ----

    /** L'erreur en ligne du nom (vit avec la saisie, jamais tardive). */
    private TextView erreurNom;

    /** L'erreur en ligne des modules. */
    private TextView erreurModules;

    /** L'erreur en ligne du paquet. */
    private TextView erreurPaquet;

    private void corpsIdentite() {
        ModeleProjet.Modele modele = etat.modele();

        Champ champNom = new Champ(this);
        champNom.setEtiquette(R.string.creation_champ_nom);
        champNom.edition().setText(etat.nom());
        champNom.edition().addTextChangedListener(apresSaisie(
                texte -> {
                    etat.changerNom(texte);
                    majValiditeIdentite();
                }));
        corpsEtape.addView(champNom, marges(0, GraphiteVue.dp(this, 6)));
        erreurNom = ligneErreurChamp();
        corpsEtape.addView(erreurNom);

        if (modele != null && !modele.modules().isEmpty()) {
            LinearLayout blocModules = new LinearLayout(this);
            blocModules.setOrientation(LinearLayout.VERTICAL);
            TextView etiquette = new TextView(this);
            GraphiteVue.typographie(etiquette, Graphite.LEGENDE);
            etiquette.setTypeface(GraphiteVue.police(this, 600, false));
            etiquette.setTextColor(palette.texteSecondaire);
            etiquette.setText(getString(R.string.creation_champ_modules));
            blocModules.addView(etiquette);
            TextView aide = new TextView(this);
            GraphiteVue.typographie(aide, Graphite.LEGENDE);
            aide.setTextColor(palette.texteEstompe);
            aide.setText(R.string.creation_champ_modules_note);
            blocModules.addView(aide);
            LinearLayout puces = new LinearLayout(this);
            puces.setOrientation(LinearLayout.HORIZONTAL);
            for (ModeleProjet.ModuleModele module : modele.modules()) {
                ajouterPuceModule(puces, module);
            }
            blocModules.addView(puces, marges(GraphiteVue.dp(this, 8), 0));
            corpsEtape.addView(blocModules, marges(0, GraphiteVue.dp(this, 6)));
            erreurModules = ligneErreurChamp();
            corpsEtape.addView(erreurModules);
        }

        if (etat.champsPaquet()) {
            Champ champPaquet = new Champ(this);
            champPaquet.setEtiquette(R.string.creation_champ_paquet);
            champPaquet.setMonospace(true);
            champPaquet.edition().setText(etat.paquet());
            champPaquet.edition().addTextChangedListener(apresSaisie(
                    texte -> {
                        etat.changerPaquet(texte);
                        majValiditeIdentite();
                    }));
            corpsEtape.addView(champPaquet, marges(0, GraphiteVue.dp(this, 6)));
            erreurPaquet = ligneErreurChamp();
            corpsEtape.addView(erreurPaquet);

            TextView etiquetteMinSdk = new TextView(this);
            GraphiteVue.typographie(etiquetteMinSdk, Graphite.LEGENDE);
            etiquetteMinSdk.setTypeface(GraphiteVue.police(this, 600,
                    false));
            etiquetteMinSdk.setTextColor(palette.texteSecondaire);
            etiquetteMinSdk.setText(R.string.creation_champ_minsdk);
            corpsEtape.addView(etiquetteMinSdk, marges(0, GraphiteVue.dp(this, 6)));
            Segment segment = new Segment(this);
            List<String> niveaux = new ArrayList<>();
            for (Integer niveau : GenerateurProjet.MIN_SDK) {
                niveaux.add(String.valueOf(niveau));
            }
            segment.setOptions(niveaux,
                    GenerateurProjet.MIN_SDK.indexOf(etat.minSdk()));
            segment.setAuditeur((s, index) ->
                    etat.changerMinSdk(GenerateurProjet.MIN_SDK
                            .get(index)));
            corpsEtape.addView(segment);
            TextView note = new TextView(this);
            GraphiteVue.typographie(note, Graphite.LEGENDE);
            note.setTextColor(palette.texteEstompe);
            note.setText(R.string.creation_note_minsdk);
            corpsEtape.addView(note, marges(GraphiteVue.dp(this, 6),
                    GraphiteVue.dp(this, 10)));
        }

        corpsEtape.addView(banniere(false,
                R.string.creation_note_stockage));
        majValiditeIdentite();
    }

    private void ajouterPuceModule(LinearLayout parent,
                                   ModeleProjet.ModuleModele module) {
        Puce puce = new Puce(this);
        puce.setCategorie(true);
        puce.setText(":" + module.id());
        puce.setPointee(etat.modulesChoisis().contains(module.id()));
        puce.setAuditeur((p, pointee) -> {
            EtatCreation.Bascule bascule =
                    etat.basculerModule(module.id());
            if (!bascule.faite() && bascule.requisPar() != null) {
                montrerErreur(getString(R.string.creation_err_module_requis,
                        ":" + bascule.requisPar()));
            } else {
                masquerErreur();
            }
            // les puces se re-posent (l'état décide, la vue déroule)
            puce.setPointee(etat.modulesChoisis().contains(module.id()));
            majValiditeIdentite();
        });
        LinearLayout.LayoutParams placements = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        placements.rightMargin = GraphiteVue.dp(this, 6);
        parent.addView(puce, placements);
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
     * bouton Continuer reflètent l'état, jamais une alerte tardive.
     */
    private void majValiditeIdentite() {
        if (erreurNom != null) {
            if (etat.raisonNom() == EtatCreation.RaisonNom.VIDE) {
                poser(erreurNom, R.string.creation_err_nom_vide);
            } else if (etat.raisonNom() == EtatCreation.RaisonNom.LONG) {
                poser(erreurNom, R.string.creation_err_nom_long);
            } else {
                erreurNom.setVisibility(View.GONE);
            }
        }
        if (erreurModules != null) {
            if (etat.modulesValides()) {
                erreurModules.setVisibility(View.GONE);
            } else {
                poser(erreurModules, R.string.creation_err_modules);
            }
        }
        if (erreurPaquet != null) {
            if (etat.paquetValide()) {
                erreurPaquet.setVisibility(View.GONE);
            } else {
                poser(erreurPaquet, R.string.creation_err_paquet);
            }
        }
        poserBoutons();
    }

    private void poser(TextView erreur, int idTexte) {
        erreur.setText(idTexte);
        erreur.setVisibility(View.VISIBLE);
    }

    /** Une ligne d'erreur de champ, cachée jusqu'à sa raison. */
    private TextView ligneErreurChamp() {
        TextView erreur = new TextView(this);
        GraphiteVue.typographie(erreur, Graphite.LEGENDE);
        erreur.setTextColor(palette.erreur);
        erreur.setVisibility(View.GONE);
        return erreur;
    }

    // ---- Étape 3 : le récapitulatif ----

    private void corpsRecap() {
        ModeleProjet.Modele modele = etat.modele();
        LinearLayout fiche = new LinearLayout(this);
        fiche.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(palette.surface);
        fond.setStroke(GraphiteVue.dp(this, 1), palette.bordFin);
        fond.setCornerRadius(GraphiteVue.dp(this, Graphite.RAYON_CARTE));
        fiche.setBackground(fond);
        int interne = GraphiteVue.dp(this, 14);
        fiche.setPadding(interne, GraphiteVue.dp(this, 4), interne,
                GraphiteVue.dp(this, 4));
        List<EtatCreation.LigneRecap> lignes = etat.lignesRecap();
        for (int i = 0; i < lignes.size(); i++) {
            if (i > 0) {
                View separateur = new View(this);
                separateur.setBackgroundColor(palette.bordFin);
                fiche.addView(separateur, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        GraphiteVue.dp(this, 1)));
            }
            EtatCreation.LigneRecap ligne = lignes.get(i);
            LinearLayout rangee = new LinearLayout(this);
            rangee.setOrientation(LinearLayout.HORIZONTAL);
            rangee.setPadding(0, GraphiteVue.dp(this, 9), 0,
                    GraphiteVue.dp(this, 9));
            TextView cle = new TextView(this);
            GraphiteVue.typographie(cle, Graphite.NOTE);
            cle.setTextColor(palette.texteSecondaire);
            cle.setText(cleRecap(ligne.cle()));
            rangee.addView(cle, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView valeur = new TextView(this);
            GraphiteVue.typographie(valeur, Graphite.NOTE);
            valeur.setTypeface(GraphiteVue.police(this, 600, false));
            valeur.setTextColor(palette.texte);
            valeur.setText(ligne.valeur());
            rangee.addView(valeur);
            fiche.addView(rangee);
        }
        corpsEtape.addView(fiche, marges(0, GraphiteVue.dp(this, 12)));

        TextView etiquetteArbo = new TextView(this);
        GraphiteVue.typographie(etiquetteArbo, Graphite.LEGENDE);
        etiquetteArbo.setTypeface(GraphiteVue.police(this, 600, false));
        etiquetteArbo.setTextColor(palette.texteSecondaire);
        etiquetteArbo.setText(R.string.creation_recap_arbo);
        corpsEtape.addView(etiquetteArbo, marges(0, GraphiteVue.dp(this, 6)));
        LinearLayout arbo = new LinearLayout(this);
        arbo.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable fondArbo = new GradientDrawable();
        fondArbo.setColor(palette.puits);
        fondArbo.setStroke(GraphiteVue.dp(this, 1), palette.bordFin);
        fondArbo.setCornerRadius(GraphiteVue.dp(this, 14));
        arbo.setBackground(fondArbo);
        arbo.setPadding(GraphiteVue.dp(this, 12),
                GraphiteVue.dp(this, 12), GraphiteVue.dp(this, 12),
                GraphiteVue.dp(this, 12));
        for (EtatCreation.LigneArbo ligne : etat.arborescence()) {
            TextView texte = new TextView(this);
            GraphiteVue.typographie(texte, Graphite.CODE_COMPACT);
            texte.setText(ligne.profondeur() > 0
                    ? "  ".repeat(ligne.profondeur()) + ligne.texte()
                    : ligne.texte());
            texte.setTextColor(ligne.annotation()
                    ? palette.syntaxe.commentaire : ligne.profondeur() == 0
                            ? palette.texte : palette.texteSecondaire);
            arbo.addView(texte);
        }
        corpsEtape.addView(arbo, marges(0, GraphiteVue.dp(this, 12)));

        if (modele != null && modele.franchise() != null) {
            LinearLayout banniere = banniere(true, 0);
            TextView texte = (TextView) banniere.getChildAt(0);
            texte.setText(modele.franchise());
            corpsEtape.addView(banniere);
        }
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

    /** La bannière du bas d'étape : alerte (franchise) ou info. */
    private LinearLayout banniere(boolean alerte, int idTexte) {
        LinearLayout banniere = new LinearLayout(this);
        banniere.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable fond = new GradientDrawable();
        if (alerte) {
            fond.setColor(Graphite.melange(palette.alerte, 0.10f,
                    palette.fond));
            fond.setStroke(GraphiteVue.dp(this, 1),
                    Graphite.melange(palette.alerte, 0.35f, palette.fond));
        } else {
            fond.setColor(Graphite.melange(palette.accent, 0.10f,
                    palette.fond));
            fond.setStroke(GraphiteVue.dp(this, 1),
                    Graphite.melange(palette.accent, 0.35f, palette.fond));
        }
        fond.setCornerRadius(GraphiteVue.dp(this,
                Graphite.RAYON_COMPOSANT));
        banniere.setBackground(fond);
        banniere.setPadding(GraphiteVue.dp(this, 12),
                GraphiteVue.dp(this, 10), GraphiteVue.dp(this, 12),
                GraphiteVue.dp(this, 10));
        TextView texte = new TextView(this);
        GraphiteVue.typographie(texte, Graphite.NOTE);
        texte.setTextColor(alerte ? palette.alerte : palette.accent);
        if (idTexte != 0) {
            texte.setText(idTexte);
        }
        banniere.addView(texte, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return banniere;
    }

    // ---- Boutons et navigation ----

    private void poserBoutons() {
        if (piedEtape == null) {
            return;
        }
        piedEtape.removeAllViews();
        if (etat.etape() != EtatCreation.Etape.MODELE) {
            Bouton retour = new Bouton(this);
            retour.setVariante(ChoixBouton.Variante.TEXTE);
            retour.setTexte(R.string.creation_bouton_retour);
            retour.setOnClickListener(v -> retour());
            LinearLayout.LayoutParams placements =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            placements.rightMargin = GraphiteVue.dp(this, 10);
            piedEtape.addView(retour, placements);
        }
        Bouton principal = new Bouton(this);
        principal.setVariante(ChoixBouton.Variante.PRIMAIRE);
        principal.setTexte(etat.etape() == EtatCreation.Etape.RECAPITULATIF
                ? R.string.creation_bouton_creer
                : R.string.creation_bouton_continuer);
        principal.setEnabled(etat.continuerPossible());
        principal.setOnClickListener(v -> {
            if (etat.etape() == EtatCreation.Etape.RECAPITULATIF) {
                creer();
            } else {
                etat.suivante();
                rendre();
            }
        });
        piedEtape.addView(principal, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }

    private void retour() {
        if (etat.surPremiereEtape()) {
            finish();
            return;
        }
        etat.precedente();
        rendre();
    }

    /**
     * Le retour système remonte d'une étape ; depuis l'étape 1 il
     * quitte l'assistant. Le cadre déprécie onBackPressed au profit
     * d'OnBackInvokedCallback (API 33+) : le produit vise le niveau
     * 28 (D4), le retour passe encore par ici — suppression
     * documentée, pas un angle mort.
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // le retour système remonte d'une étape ; depuis l'étape 1 il
        // quitte l'assistant — rien n'est créé avant le bouton
        if (etat.surPremiereEtape()) {
            super.onBackPressed();
            return;
        }
        etat.precedente();
        rendre();
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
        new Thread(() -> {
            EcrireProjet.Resultat resultat = EcrireProjet.ecrire(
                    projets, id, generation.fichiers(),
                    generation.dossiers());
            runOnUiThread(() -> apresCreation(resultat, generation, id));
        }, "joide-creation").start();
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
        Intent intention = new Intent(this, EcranAtelier.class);
        intention.putExtra(EcranAtelier.EXTRA_RACINE,
                resultat.chemin().toString());
        if (generation.fichierEntree() != null) {
            intention.putExtra(EcranAtelier.EXTRA_FICHIER,
                    generation.fichierEntree());
        }
        startActivity(intention);
        finish();
    }

    // ---- Erreurs en ligne ----

    private void montrerErreur(String texte) {
        ligneErreur.setText(texte);
        ligneErreur.setVisibility(View.VISIBLE);
    }

    private void masquerErreur() {
        ligneErreur.setVisibility(View.GONE);
    }

    private LinearLayout.LayoutParams marges(int dessus, int dessous) {
        LinearLayout.LayoutParams placements = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        placements.topMargin = dessus;
        placements.bottomMargin = dessous;
        return placements;
    }
}
