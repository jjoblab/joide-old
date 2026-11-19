package jo.joide.app.android.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

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
    private TextView vueTitre;
    private TextView vueAide;
    /** Les actions déclarées en XML, adoptées à l'attachement (lot A2,
     * ADR-085) : la rangée reste TOUJOURS en fin de zone, l'ordre du kit
     * (glyphe, titre, aide, actions) est préservé. */
    private final List<View> actionsXml = new ArrayList<>();

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
        setTitre(getContext().getText(idRes));
    }

    /**
     * Le titre de l'état vide, texte libre (lot J : l'atelier y raconte
     * le chargement puis l'erreur) — posé idempotent, jamais empilé.
     */
    public void setTitre(CharSequence titre) {
        if (vueTitre == null) {
            vueTitre = new TextView(getContext());
            GraphiteVue.typographie(vueTitre, Graphite.SOUS_TITRE);
            vueTitre.setTextColor(palette.texte);
            vueTitre.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            addView(vueTitre, apresGlyphe());
        }
        vueTitre.setText(titre);
    }

    /** L'aide de l'état vide, depuis les ressources (260 dp au plus). */
    public void setAide(int idRes) {
        setAide(getContext().getText(idRes));
    }

    /**
     * L'aide de l'état vide, texte libre (lot J : le détail de
     * l'erreur) — posée idempotente, jamais empilée.
     */
    public void setAide(CharSequence aide) {
        if (vueAide == null) {
            vueAide = new TextView(getContext());
            GraphiteVue.typographie(vueAide, Graphite.NOTE);
            vueAide.setTextColor(palette.texteSecondaire);
            vueAide.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            vueAide.setMaxWidth(GraphiteVue.dp(getContext(), 260));
            addView(vueAide, apresGlyphe());
        }
        vueAide.setText(aide);
    }

    /**
     * Ajoute une action empilée (largeur pleine, la maquette
     * accueil : rangée d'actions en colonne).
     */
    public void addAction(Bouton action) {
        addAction((View) action);
    }

    /**
     * Ajoute une action empilée — la version View (lot A2, ADR-085) :
     * les boutons Material déclarés dans {@code item_etat_vide_accueil.xml}
     * entrent par ici, le kit ne décide rien de leur rendu.
     */
    public void addAction(View action) {
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

    /**
     * Les actions déclarées EN XML partent en attente : elles seront
     * rangées dans la rangée d'actions à l'attachement — APRÈS le
     * glyphe, le titre et l'aide posés par les setters du kit.
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        for (int i = getChildCount() - 1; i >= 0; i--) {
            actionsXml.add(0, getChildAt(i));
        }
        removeAllViews();
    }

    /** L'attachement range les actions déclarées en XML (fin de zone). */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        for (View action : actionsXml) {
            addAction(action);
        }
        actionsXml.clear();
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
