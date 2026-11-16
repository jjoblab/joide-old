package jo.joide.app.android.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * État vide du kit : zone en pointillés (rayon 28 dp, bord 1,5 dp,
 * tirets), glyphe dossier estompé, titre sous-titre, aide en note
 * sur texte secondaire (260 dp au plus), actions empilées dessous.
 * « L'état vide est un état fonctionnel complet » (P8) : rien n'est
 * affirmé, tout est proposé — aucune promesse, aucun vide muet.
 */
public final class EtatVide extends LinearLayout {

    private final Graphite.Palette palette;
    private final Paint pinceau = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearLayout rangeeActions;

    public EtatVide(Context contexte) {
        this(contexte, null);
    }

    public EtatVide(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);
        setOrientation(VERTICAL);
        setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        setPadding(GraphiteVue.dp(contexte, 24),
                GraphiteVue.dp(contexte, 40),
                GraphiteVue.dp(contexte, 24),
                GraphiteVue.dp(contexte, 40));
    }

    /** Pose le glyphe dossier estompé (56 dp, trait 1,4 dp). */
    public void setGlypheDossier() {
        VueDossier glyphe = new VueDossier(getContext());
        LayoutParams placements = new LayoutParams(
                GraphiteVue.dp(getContext(), 56),
                GraphiteVue.dp(getContext(), 56));
        placements.bottomMargin = GraphiteVue.dp(getContext(), 10);
        addView(glyphe, 0, placements);
    }

    /** Le titre de l'état vide, depuis les ressources. */
    public void setTitre(int idRes) {
        TextView titre = new TextView(getContext());
        GraphiteVue.typographie(titre, Graphite.SOUS_TITRE);
        titre.setTextColor(palette.texte);
        titre.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        addView(titre, apresGlyphe());
        titre.setText(idRes);
    }

    /** L'aide de l'état vide, depuis les ressources (260 dp au plus). */
    public void setAide(int idRes) {
        TextView aide = new TextView(getContext());
        GraphiteVue.typographie(aide, Graphite.NOTE);
        aide.setTextColor(palette.texteSecondaire);
        aide.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        aide.setMaxWidth(GraphiteVue.dp(getContext(), 260));
        addView(aide, apresGlyphe());
        aide.setText(idRes);
    }

    /**
     * Ajoute une action empilée (largeur pleine, la maquette
     * accueil : rangée d'actions en colonne).
     */
    public void addAction(Bouton action) {
        if (rangeeActions == null) {
            rangeeActions = new LinearLayout(getContext());
            rangeeActions.setOrientation(VERTICAL);
            LayoutParams placements = new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            placements.topMargin = GraphiteVue.dp(getContext(), 10);
            addView(rangeeActions, placements);
        }
        rangeeActions.addView(action,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    @Override
    public void onDrawForeground(Canvas canvas) {
        super.onDrawForeground(canvas);
        pinceau.setStyle(Paint.Style.STROKE);
        pinceau.setColor(palette.bord);
        float trait = GraphiteVue.dp(getContext(), 1.5f);
        pinceau.setStrokeWidth(trait);
        pinceau.setPathEffect(new DashPathEffect(
                new float[]{GraphiteVue.dp(getContext(), 6),
                        GraphiteVue.dp(getContext(), 6)}, 0f));
        float demi = trait / 2f;
        canvas.drawRoundRect(demi, demi, getWidth() - demi,
                getHeight() - demi,
                GraphiteVue.dp(getContext(), 28),
                GraphiteVue.dp(getContext(), 28), pinceau);
        pinceau.setPathEffect(null);
    }

    private LinearLayout.LayoutParams apresGlyphe() {
        LinearLayout.LayoutParams placements = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        placements.topMargin = GraphiteVue.dp(getContext(), 8);
        return placements;
    }

    /** Le glyphe dossier de l'état vide. */
    private final class VueDossier extends android.view.View {
        VueDossier(Context contexte) {
            super(contexte);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            pinceau.setStyle(Paint.Style.STROKE);
            pinceau.setColor(palette.texteEstompe);
            pinceau.setStrokeWidth(GraphiteVue.dp(getContext(), 1.4f));
            pinceau.setStrokeCap(Paint.Cap.ROUND);
            pinceau.setStrokeJoin(Paint.Join.ROUND);
            float unite = GraphiteVue.dp(getContext(), 56f) / 24f;
            Carte.dessinerDossier(canvas, pinceau, unite, 0f, 0f);
        }
    }
}
