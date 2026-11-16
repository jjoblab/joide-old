package jo.joide.app.android.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import jo.joide.app.android.ui.choix.ChoixCarte;

/**
 * Carte du kit : conteneur horizontal — fond surface, rayon 16 dp,
 * bord fin 1 dp, ombre douce (élévation), liseré supérieur en sombre
 * ({@link ChoixCarte#lisere}). Tuile tonale en tête (46 dp, rayon
 * 12 dp, glyphe dossier dessiné, ADR-048), colonne de corps au
 * poids 1, accessoire en bout. Les fabriques de lignes appliquent
 * la troncature du kit : une ligne, ellipse à la fin.
 */
public final class Carte extends LinearLayout {

    private final Graphite.Palette palette;
    private final Paint pinceau = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearLayout colonneCorps;

    public Carte(Context contexte) {
        this(contexte, null);
    }

    public Carte(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);
        setOrientation(HORIZONTAL);
        int marge = GraphiteVue.dp(contexte, 14);
        setPadding(marge, marge, marge, marge);
        poserFond(false);
        setElevation(GraphiteVue.dp(contexte, 3));
    }

    /**
     * La carte tonale (lot H) : bord accent 35 %, dégradé accent 8 %
     * vers la surface — la reprise de l'accueil et le modèle choisi
     * de l'assistant (maquette accueil-projets.html).
     *
     * @param tonale vrai pour l'accentuation, faux pour la carte
     *        standard du kit
     */
    public void setTonale(boolean tonale) {
        poserFond(tonale);
        invalidate();
    }

    private void poserFond(boolean tonale) {
        GradientDrawable fond = new GradientDrawable();
        if (tonale) {
            fond = new GradientDrawable(
                    android.graphics.drawable.GradientDrawable
                            .Orientation.TOP_BOTTOM,
                    new int[]{ChoixCarte.tonaleHaut(palette),
                            palette.surface});
            fond.setStroke(GraphiteVue.dp(getContext(), 1),
                    ChoixCarte.tonaleBord(palette));
        } else {
            fond.setColor(palette.surface);
            fond.setStroke(GraphiteVue.dp(getContext(), 1), palette.bordFin);
        }
        fond.setCornerRadius(GraphiteVue.dp(getContext(),
                Graphite.RAYON_CARTE));
        setBackground(fond);
    }

    @Override
    public void onDrawForeground(Canvas canvas) {
        super.onDrawForeground(canvas);
        int lisere = ChoixCarte.lisere(palette);
        if (lisere != 0) {
            pinceau.setStyle(Paint.Style.FILL);
            pinceau.setColor(lisere);
            float inset = GraphiteVue.dp(getContext(),
                    Graphite.RAYON_CARTE);
            canvas.drawRect(inset, 0f, getWidth() - inset,
                    GraphiteVue.dp(getContext(), 1), pinceau);
        }
    }

    /**
     * Pose la tuile tonale en tête de la carte (46 dp) pour ce type
     * de projet.
     */
    public void setTuile(ChoixCarte.TypeTuile type) {
        Tuile tuile = new Tuile(getContext(), palette, type);
        LayoutParams placements =
                new LayoutParams(GraphiteVue.dp(getContext(),
                        Graphite.TUILE),
                        GraphiteVue.dp(getContext(), Graphite.TUILE));
        placements.rightMargin = GraphiteVue.dp(getContext(), 14);
        addView(tuile, 0, placements);
    }

    /** La colonne de corps (entre la tuile et l'accessoire). */
    public LinearLayout corps() {
        if (colonneCorps == null) {
            colonneCorps = new LinearLayout(getContext());
            colonneCorps.setOrientation(VERTICAL);
            LayoutParams placements =
                    new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            placements.rightMargin = GraphiteVue.dp(getContext(), 14);
            addView(colonneCorps, tuileCompte(), placements);
        }
        return colonneCorps;
    }

    /**
     * Ajoute un accessoire en bout de carte (menu ⋮ au lot H,
     * interrupteur au lot M).
     */
    public void addAccessoire(View vue) {
        LayoutParams placements = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        placements.leftMargin = GraphiteVue.dp(getContext(), 14);
        addView(vue, placements);
    }

    /** Le nombre de tuiles déjà posées (0 ou 1). */
    private int tuileCompte() {
        int compteur = 0;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof Tuile) {
                compteur++;
            }
        }
        return compteur;
    }

    /** Ligne de nom : sous-titre demi-gras, une ligne, ellipse. */
    public static TextView ligneNom(Context contexte) {
        return ligne(contexte, Graphite.SOUS_TITRE, Graphite.pour(
                GraphiteVue.modeNuit(contexte)).texte);
    }

    /** Ligne de méta : note, une ligne, ellipse. */
    public static TextView ligneMeta(Context contexte) {
        return ligne(contexte, Graphite.NOTE, Graphite.pour(
                GraphiteVue.modeNuit(contexte)).texteSecondaire);
    }

    /** Ligne de légende : légende, texte estompé, une ligne, ellipse. */
    public static TextView ligneLegende(Context contexte) {
        return ligne(contexte, Graphite.LEGENDE, Graphite.pour(
                GraphiteVue.modeNuit(contexte)).texteEstompe);
    }

    private static TextView ligne(Context contexte,
                                  Graphite.StyleTexte style, int couleur) {
        TextView vue = new TextView(contexte);
        GraphiteVue.typographie(vue, style);
        vue.setTextColor(couleur);
        vue.setSingleLine(true);
        vue.setEllipsize(TextUtils.TruncateAt.END);
        return vue;
    }

    /**
     * Dessine le glyphe dossier du kit (chemin en unités 24,
     * arrondi ADR-048) — utilisé par la tuile et l'état vide.
     */
    static void dessinerDossier(Canvas canvas, Paint pinceau,
                                float unite, float dx, float dy) {
        pinceau.setStyle(Paint.Style.STROKE);
        Path chemin = new Path();
        chemin.moveTo(4f * unite, 6f * unite);
        chemin.arcTo(new RectF(4f * unite, 4f * unite,
                8f * unite, 8f * unite), 180f, 90f);
        chemin.lineTo(10f * unite, 4f * unite);
        chemin.lineTo(12f * unite, 6.5f * unite);
        chemin.lineTo(18f * unite, 6.5f * unite);
        chemin.arcTo(new RectF(16f * unite, 6.5f * unite,
                20f * unite, 10.5f * unite), 270f, 90f);
        chemin.lineTo(20f * unite, 18f * unite);
        chemin.arcTo(new RectF(16f * unite, 16f * unite,
                20f * unite, 20f * unite), 0f, 90f);
        chemin.lineTo(6f * unite, 20f * unite);
        chemin.arcTo(new RectF(4f * unite, 16f * unite,
                8f * unite, 20f * unite), 90f, 90f);
        chemin.close();
        canvas.save();
        canvas.translate(dx, dy);
        canvas.drawPath(chemin, pinceau);
        canvas.restore();
    }

    /** La tuile tonale : fond à 12 %, glyphe dossier au rôle. */
    private static final class Tuile extends View {
        private final int fond;
        private final int couleurIcone;
        private final Paint pinceau = new Paint(
                Paint.ANTI_ALIAS_FLAG);

        Tuile(Context contexte, Graphite.Palette palette,
              ChoixCarte.TypeTuile type) {
            super(contexte);
            fond = ChoixCarte.tuile(palette, type);
            couleurIcone = ChoixCarte.icone(palette, type);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            pinceau.setStyle(Paint.Style.FILL);
            pinceau.setColor(fond);
            float rayon = GraphiteVue.dp(getContext(),
                    Graphite.RAYON_COMPOSANT);
            canvas.drawRoundRect(0f, 0f, getWidth(), getHeight(),
                    rayon, rayon, pinceau);
            float unite = GraphiteVue.dp(getContext(), 1f);
            pinceau.setColor(couleurIcone);
            pinceau.setStrokeWidth(GraphiteVue.dp(getContext(), 1.7f));
            pinceau.setStrokeCap(Paint.Cap.ROUND);
            pinceau.setStrokeJoin(Paint.Join.ROUND);
            float cote = Graphite.TUILE * unite;
            dessinerDossier(canvas, pinceau, unite,
                    (cote - 24f * unite) / 2f,
                    (cote - 24f * unite) / 2f);
        }
    }
}
