package jo.joide.app.android.ecrans.exercice;

import jo.joide.app.android.R;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import jo.joide.app.android.ui.Bouton;
import jo.joide.app.android.ui.Carte;
import jo.joide.app.android.ui.Champ;
import jo.joide.app.android.ui.Dialogue;
import jo.joide.app.android.ui.Dock;
import jo.joide.app.android.ui.EtatVide;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.Puce;
import jo.joide.app.android.ui.TiroirPousse;
import jo.joide.app.android.ui.choix.ChoixBouton;
import jo.joide.app.android.ui.choix.ChoixCarte;

/**
 * Écran d'exercice du kit Graphite (lot G, ADR-048) : chaque
 * composant du lot y est montré dans le mode courant du système —
 * jetons, typographie, boutons, champs, cartes, puces, tiroir
 * poussé, dock, dialogues, état vide. Aucune navigation n'est câblée
 * (le plan l'interdit au lot G) : l'entrée « Kit Graphite » de
 * l'écran P8 y mène ; l'écran disparaît quand le lot H remplace
 * l'ouverture. Vue mince : aucune logique ne vit ici, tout vient
 * des classes de choix.
 */
public final class EcranExercice extends Activity {

    private TiroirPousse tiroir;

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        Graphite.Palette palette = GraphiteVue.palette(this);
        GraphiteVue.fondEcran(this, palette);
        setContentView(construire(palette));
    }

    private FrameLayout construire(Graphite.Palette palette) {
        FrameLayout racine = new FrameLayout(this);
        ScrollView defile = new ScrollView(this);
        LinearLayout colonne = new LinearLayout(this);
        colonne.setOrientation(LinearLayout.VERTICAL);
        int marge = GraphiteVue.dp(this, Graphite.MARGE);
        colonne.setPadding(marge, marge, marge, marge * 2);
        defile.addView(colonne, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        racine.addView(defile, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        entete(colonne, palette);
        sectionJetons(colonne, palette);
        sectionTypographie(colonne);
        sectionBoutons(colonne, palette);
        sectionChamps(colonne, palette);
        sectionCartes(colonne);
        sectionPuces(colonne);
        sectionTiroir(colonne);
        sectionDock(colonne, palette);
        sectionDialogue(colonne, palette);
        sectionEtatVide(colonne);

        tiroir = new TiroirPousse(this);
        tiroir.setPanneau(panneauTiroir(palette));
        racine.addView(tiroir, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        return racine;
    }

    private void entete(LinearLayout colonne, Graphite.Palette palette) {
        TextView titre = new TextView(this);
        GraphiteVue.typographie(titre, Graphite.TITRE_ECRAN);
        titre.setTextColor(palette.texte);
        titre.setText(R.string.exercice_titre);
        colonne.addView(titre);

        TextView mode = new TextView(this);
        GraphiteVue.typographie(mode, Graphite.NOTE);
        mode.setTextColor(palette.texteSecondaire);
        mode.setText(Graphite.estSombre(palette)
                ? R.string.exercice_mode_sombre
                : R.string.exercice_mode_clair);
        colonne.addView(mode, margeBas(6));
    }

    private void sectionJetons(LinearLayout colonne,
                               Graphite.Palette palette) {
        enteteSection(colonne, R.string.exercice_jetons);
        ligneJeton(colonne, palette, R.string.jeton_fond, palette.fond);
        ligneJeton(colonne, palette, R.string.jeton_surface, palette.surface);
        ligneJeton(colonne, palette, R.string.jeton_surface_haute,
                palette.surfaceHaute);
        ligneJeton(colonne, palette, R.string.jeton_puits, palette.puits);
        ligneJeton(colonne, palette, R.string.jeton_bord, palette.bord);
        ligneJeton(colonne, palette, R.string.jeton_bord_fin, palette.bordFin);
        ligneJeton(colonne, palette, R.string.jeton_texte, palette.texte);
        ligneJeton(colonne, palette, R.string.jeton_texte_2,
                palette.texteSecondaire);
        ligneJeton(colonne, palette, R.string.jeton_texte_3,
                palette.texteEstompe);
        ligneJeton(colonne, palette, R.string.jeton_accent, palette.accent);
        ligneJeton(colonne, palette, R.string.jeton_reussite, palette.reussite);
        ligneJeton(colonne, palette, R.string.jeton_alerte, palette.alerte);
        ligneJeton(colonne, palette, R.string.jeton_erreur, palette.erreur);
        ligneJeton(colonne, palette, R.string.jeton_violet, palette.violet);
        ligneJeton(colonne, palette, R.string.jeton_sur_accent,
                palette.texteSurAccent);
        ligneJeton(colonne, palette, R.string.jeton_sur_reussite,
                palette.texteSurReussite);
        ligneJeton(colonne, palette, R.string.jeton_rideau, palette.rideau);

        enteteSection(colonne, R.string.exercice_syntaxe);
        LinearLayout ligne = new LinearLayout(this);
        ligne.setOrientation(LinearLayout.HORIZONTAL);
        ligne.setBackgroundColor(palette.puits);
        int marge = GraphiteVue.dp(this, 12);
        ligne.setPadding(marge, marge, marge, marge);
        java.util.List<TextView> mots = java.util.List.of(
                motSyntaxe(palette, R.string.syn_mot_cle, palette.syntaxe.motCle),
                motSyntaxe(palette, R.string.syn_type, palette.syntaxe.type),
                motSyntaxe(palette, R.string.syn_fonction, palette.syntaxe.fonction),
                motSyntaxe(palette, R.string.syn_chaine, palette.syntaxe.chaine),
                motSyntaxe(palette, R.string.syn_nombre, palette.syntaxe.nombre),
                motSyntaxe(palette, R.string.syn_commentaire,
                        palette.syntaxe.commentaire),
                motSyntaxe(palette, R.string.syn_defaut, palette.syntaxe.defaut));
        for (TextView mot : mots) {
            LinearLayout.LayoutParams placements = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            placements.rightMargin = GraphiteVue.dp(this, 10);
            ligne.addView(mot, placements);
        }
        colonne.addView(ligne, margeBas(10));

        ligneJeton(colonne, palette, R.string.jeton_tuile_android,
                ChoixCarte.tuile(palette, ChoixCarte.TypeTuile.ANDROID));
        ligneJeton(colonne, palette, R.string.jeton_tuile_console,
                ChoixCarte.tuile(palette, ChoixCarte.TypeTuile.CONSOLE));
        ligneJeton(colonne, palette, R.string.jeton_tuile_bibliotheque,
                ChoixCarte.tuile(palette, ChoixCarte.TypeTuile.BIBLIOTHEQUE));
        ligneJeton(colonne, palette, R.string.jeton_tuile_vide,
                ChoixCarte.tuile(palette, ChoixCarte.TypeTuile.VIDE));
    }

    private TextView motSyntaxe(Graphite.Palette palette, int idRes,
                                int couleur) {
        TextView mot = new TextView(this);
        GraphiteVue.typographie(mot, Graphite.CODE);
        mot.setTextColor(couleur);
        mot.setText(idRes);
        return mot;
    }

    private void sectionTypographie(LinearLayout colonne) {
        enteteSection(colonne, R.string.exercice_typographie);
        paireTypographie(colonne, Graphite.LEGENDE, R.string.typo_legende);
        paireTypographie(colonne, Graphite.NOTE, R.string.typo_note);
        paireTypographie(colonne, Graphite.CORPS, R.string.typo_corps);
        paireTypographie(colonne, Graphite.SOUS_TITRE, R.string.typo_sous_titre);
        paireTypographie(colonne, Graphite.TITRE, R.string.typo_titre);
        paireTypographie(colonne, Graphite.TITRE_ECRAN, R.string.typo_titre_ecran);
        paireTypographie(colonne, Graphite.CODE, R.string.typo_code);
        paireTypographie(colonne, Graphite.CODE_COMPACT,
                R.string.typo_code_compact);
    }

    private void paireTypographie(LinearLayout colonne,
                                  Graphite.StyleTexte style, int idLegende) {
        TextView echantillon = new TextView(this);
        GraphiteVue.typographie(echantillon, style);
        echantillon.setTextColor(Graphite.pour(
                GraphiteVue.modeNuit(this)).texte);
        echantillon.setText(R.string.sample_phrase);
        colonne.addView(echantillon, margeBas(2));

        TextView legende = new TextView(this);
        GraphiteVue.typographie(legende, Graphite.LEGENDE);
        legende.setTextColor(Graphite.pour(
                GraphiteVue.modeNuit(this)).texteEstompe);
        legende.setText(idLegende);
        colonne.addView(legende, margeBas(14));
    }

    private void sectionBoutons(LinearLayout colonne,
                                Graphite.Palette palette) {
        enteteSection(colonne, R.string.exercice_boutons);
        LinearLayout actifs = new LinearLayout(this);
        actifs.setOrientation(LinearLayout.HORIZONTAL);
        actifs.addView(bouton(ChoixBouton.Variante.PRIMAIRE,
                R.string.bouton_primaire_ex, true));
        actifs.addView(bouton(ChoixBouton.Variante.TONAL,
                R.string.bouton_tonal_ex, true));
        actifs.addView(bouton(ChoixBouton.Variante.TEXTE,
                R.string.bouton_texte_ex, true));
        colonne.addView(actifs, margeBas(10));

        LinearLayout inertes = new LinearLayout(this);
        inertes.setOrientation(LinearLayout.HORIZONTAL);
        inertes.addView(bouton(ChoixBouton.Variante.PRIMAIRE,
                R.string.bouton_primaire_ex, false));
        inertes.addView(bouton(ChoixBouton.Variante.TONAL,
                R.string.bouton_tonal_ex, false));
        inertes.addView(bouton(ChoixBouton.Variante.TEXTE,
                R.string.bouton_texte_ex, false));
        colonne.addView(inertes, margeBas(10));

        TextView legende = new TextView(this);
        GraphiteVue.typographie(legende, Graphite.LEGENDE);
        legende.setTextColor(palette.texteEstompe);
        legende.setText(R.string.bouton_desactive_ex);
        colonne.addView(legende);
    }

    private Bouton bouton(ChoixBouton.Variante variante, int idRes,
                          boolean actif) {
        Bouton bouton = new Bouton(this);
        bouton.setVariante(variante);
        bouton.setTexte(idRes);
        bouton.setEnabled(actif);
        LinearLayout.LayoutParams placements = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        placements.rightMargin = GraphiteVue.dp(this, 10);
        bouton.setLayoutParams(placements);
        return bouton;
    }

    private void sectionChamps(LinearLayout colonne,
                               Graphite.Palette palette) {
        enteteSection(colonne, R.string.exercice_champs);

        Champ nom = new Champ(this);
        nom.setEtiquette(R.string.champ_nom_etiq);
        nom.setIndice(R.string.champ_nom_hint);
        colonne.addView(nom, margeBas(14));

        Champ emplacement = new Champ(this);
        emplacement.setEtiquette(R.string.champ_chemin_etiq);
        emplacement.edition().setText(R.string.champ_chemin_valeur);
        emplacement.setEnabled(false);
        colonne.addView(emplacement, margeBas(14));

        Champ paquet = new Champ(this);
        paquet.setEtiquette(R.string.champ_monospace_etiq);
        paquet.setIndice(R.string.champ_monospace_hint);
        paquet.setMonospace(true);
        colonne.addView(paquet);
    }

    private void sectionCartes(LinearLayout colonne) {
        enteteSection(colonne, R.string.exercice_cartes);
        carte(colonne, ChoixCarte.TypeTuile.ANDROID,
                R.string.carte_meta_android);
        carte(colonne, ChoixCarte.TypeTuile.CONSOLE,
                R.string.carte_meta_console);
        carte(colonne, ChoixCarte.TypeTuile.BIBLIOTHEQUE,
                R.string.carte_meta_bibliotheque);
        carte(colonne, ChoixCarte.TypeTuile.VIDE, R.string.carte_meta_vide);

        Carte nue = new Carte(this);
        LinearLayout corpsNue = nue.corps();
        TextView nomNue = Carte.ligneNom(this);
        nomNue.setText(R.string.carte_nom_ex);
        corpsNue.addView(nomNue);
        TextView metaNue = Carte.ligneMeta(this);
        metaNue.setText(R.string.carte_meta_console);
        corpsNue.addView(metaNue);
        Puce accessoire = new Puce(this);
        accessoire.setCategorie(true);
        accessoire.setPointee(true);
        accessoire.setText(R.string.puce_ex_1);
        nue.addAccessoire(accessoire);
        colonne.addView(nue, margeBas(10));
    }

    private void carte(LinearLayout colonne, ChoixCarte.TypeTuile type,
                       int idMeta) {
        Carte carte = new Carte(this);
        carte.setTuile(type);
        LinearLayout corps = carte.corps();
        TextView legende = Carte.ligneLegende(this);
        legende.setText(getString(R.string.carte_legende_ex).toUpperCase());
        corps.addView(legende);
        TextView nom = Carte.ligneNom(this);
        nom.setText(R.string.carte_nom_ex);
        corps.addView(nom);
        TextView meta = Carte.ligneMeta(this);
        meta.setText(idMeta);
        corps.addView(meta);
        colonne.addView(carte, margeBas(10));
    }

    private void sectionPuces(LinearLayout colonne) {
        enteteSection(colonne, R.string.exercice_puces);
        LinearLayout simples = new LinearLayout(this);
        simples.setOrientation(LinearLayout.HORIZONTAL);
        simples.addView(puce(false, false, R.string.puce_ex_1));
        simples.addView(puce(false, false, R.string.puce_ex_2));
        simples.addView(puce(false, false, R.string.puce_ex_3));
        colonne.addView(simples, margeBas(10));

        LinearLayout categories = new LinearLayout(this);
        categories.setOrientation(LinearLayout.HORIZONTAL);
        categories.addView(puce(true, true, R.string.puce_ex_1));
        categories.addView(puce(true, false, R.string.puce_ex_2));
        categories.addView(puce(true, false, R.string.puce_ex_3));
        categories.addView(puce(true, false, R.string.puce_ex_4));
        colonne.addView(categories, margeBas(10));

        Puce inertes = puce(true, false, R.string.puce_ex_4);
        inertes.setEnabled(false);
        LinearLayout rangeeInerte = new LinearLayout(this);
        rangeeInerte.setOrientation(LinearLayout.HORIZONTAL);
        rangeeInerte.addView(inertes);
        colonne.addView(rangeeInerte);
    }

    private Puce puce(boolean categorie, boolean pointee, int idRes) {
        Puce puce = new Puce(this);
        puce.setCategorie(categorie);
        puce.setPointee(pointee);
        puce.setText(idRes);
        LinearLayout.LayoutParams placements = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        placements.rightMargin = GraphiteVue.dp(this, 10);
        puce.setLayoutParams(placements);
        return puce;
    }

    private void sectionTiroir(LinearLayout colonne) {
        enteteSection(colonne, R.string.exercice_tiroir);
        Bouton ouvrir = new Bouton(this);
        ouvrir.setVariante(ChoixBouton.Variante.TONAL);
        ouvrir.setTexte(R.string.ouvrir_tiroir_ex);
        ouvrir.setOnClickListener(vue -> tiroir.ouvrir());
        colonne.addView(ouvrir);
    }

    private LinearLayout panneauTiroir(Graphite.Palette palette) {
        LinearLayout panneau = new LinearLayout(this);
        panneau.setOrientation(LinearLayout.VERTICAL);
        int marge = GraphiteVue.dp(this, 20);
        panneau.setPadding(marge, marge, marge, marge);
        TextView titre = new TextView(this);
        GraphiteVue.typographie(titre, Graphite.TITRE);
        titre.setTextColor(palette.texte);
        titre.setText(R.string.tiroir_titre_ex);
        panneau.addView(titre);
        TextView texte = new TextView(this);
        GraphiteVue.typographie(texte, Graphite.CORPS);
        texte.setTextColor(palette.texteSecondaire);
        texte.setText(R.string.tiroir_texte_ex);
        panneau.addView(texte, margeBas(6));
        return panneau;
    }

    private void sectionDock(LinearLayout colonne,
                             Graphite.Palette palette) {
        enteteSection(colonne, R.string.exercice_dock);
        Dock dock = new Dock(this);
        dock.setTitre(R.string.console_build);
        TextView ligne = new TextView(this);
        GraphiteVue.typographie(ligne, Graphite.CODE_COMPACT);
        ligne.setTextColor(palette.texteSecondaire);
        ligne.setText(R.string.dock_ex_ligne);
        int marge = GraphiteVue.dp(this, 18);
        ligne.setPadding(marge, 0, marge, GraphiteVue.dp(this, 12));
        dock.setContenu(ligne);
        colonne.addView(dock);
    }

    private void sectionDialogue(LinearLayout colonne,
                                 Graphite.Palette palette) {
        enteteSection(colonne, R.string.exercice_dialogue);
        Bouton demander = new Bouton(this);
        demander.setVariante(ChoixBouton.Variante.PRIMAIRE);
        demander.setTexte(R.string.dialogue_ex_titre);
        demander.setOnClickListener(vue -> Dialogue.confirmation(this,
                palette, R.string.dialogue_ex_titre,
                R.string.dialogue_ex_message, R.string.dialogue_ex_annuler,
                R.string.dialogue_ex_supprimer, null));
        colonne.addView(demander);
    }

    private void sectionEtatVide(LinearLayout colonne) {
        enteteSection(colonne, R.string.exercice_etat_vide);
        EtatVide vide = new EtatVide(this);
        vide.setGlypheDossier();
        vide.setTitre(R.string.vide_ex_titre);
        vide.setAide(R.string.vide_ex_aide);
        vide.addAction(boutonPlein(ChoixBouton.Variante.PRIMAIRE,
                R.string.vide_ex_creer));
        vide.addAction(boutonPlein(ChoixBouton.Variante.TONAL,
                R.string.vide_ex_importer));
        colonne.addView(vide);
    }

    private Bouton boutonPlein(ChoixBouton.Variante variante, int idRes) {
        Bouton bouton = new Bouton(this);
        bouton.setVariante(variante);
        bouton.setTexte(idRes);
        return bouton;
    }

    private void enteteSection(LinearLayout colonne, int idRes) {
        TextView titre = new TextView(this);
        GraphiteVue.typographie(titre, Graphite.NOTE);
        titre.setTypeface(GraphiteVue.police(this, 700, false));
        titre.setTextColor(Graphite.pour(
                GraphiteVue.modeNuit(this)).texteEstompe);
        titre.setText(getString(idRes).toUpperCase());
        LinearLayout.LayoutParams placements = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        placements.topMargin = GraphiteVue.dp(this, 28);
        placements.bottomMargin = GraphiteVue.dp(this, 10);
        colonne.addView(titre, placements);
    }

    private void ligneJeton(LinearLayout colonne, Graphite.Palette palette,
                            int idNom, int couleur) {
        LinearLayout ligne = new LinearLayout(this);
        ligne.setOrientation(LinearLayout.HORIZONTAL);
        ligne.setGravity(android.view.Gravity.CENTER_VERTICAL);

        VueEchantillon echantillon = new VueEchantillon(this, couleur);
        LinearLayout.LayoutParams placementsEchantillon =
                new LinearLayout.LayoutParams(
                        GraphiteVue.dp(this, 24), GraphiteVue.dp(this, 24));
        placementsEchantillon.rightMargin = GraphiteVue.dp(this, 12);
        ligne.addView(echantillon, placementsEchantillon);

        TextView nom = new TextView(this);
        GraphiteVue.typographie(nom, Graphite.NOTE);
        nom.setTextColor(palette.texte);
        nom.setText(idNom);
        ligne.addView(nom, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView hexa = new TextView(this);
        GraphiteVue.typographie(hexa, Graphite.CODE_COMPACT);
        hexa.setTextColor(palette.texteEstompe);
        hexa.setText(String.format(java.util.Locale.ROOT, "#%06X",
                couleur & 0xFFFFFF));
        ligne.addView(hexa, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        colonne.addView(ligne, margeBas(8));
    }

    private LinearLayout.LayoutParams margeBas(int dp) {
        LinearLayout.LayoutParams placements = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        placements.bottomMargin = GraphiteVue.dp(this, dp);
        return placements;
    }

    /** Pastille de nuancier : la couleur pleine, rayon 6 dp. */
    private static final class VueEchantillon extends View {
        private final int couleur;
        private final Paint pinceau = new Paint(Paint.ANTI_ALIAS_FLAG);

        VueEchantillon(Context contexte, int couleur) {
            super(contexte);
            this.couleur = couleur;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            pinceau.setStyle(Paint.Style.FILL);
            pinceau.setColor(couleur);
            float rayon = GraphiteVue.dp(getContext(), 6);
            canvas.drawRoundRect(0f, 0f, getWidth(), getHeight(),
                    rayon, rayon, pinceau);
        }
    }
}
