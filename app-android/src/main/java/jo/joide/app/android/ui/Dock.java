package jo.joide.app.android.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import jo.joide.app.android.ui.choix.ChoixMouvement;

/**
 * Dock du kit : panneau repliable ancré en bas de l'atelier —
 * poignée cliquable (48 dp, titre + chevron), contenu monté par
 * l'écran (la console de build au lot K). Fond surface, bord
 * supérieur fin, ombre douce ; repli/dépli en 180 ms
 * ({@link ChoixMouvement#composant()}).
 */
public final class Dock extends LinearLayout {

    private final Graphite.Palette palette;
    private final Paint pinceau = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Poignee poignee;
    private final FrameLayout contenu;
    private boolean deplie;

    public Dock(Context contexte) {
        this(contexte, null);
    }

    public Dock(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);
        setOrientation(VERTICAL);
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(palette.surface);
        setBackground(fond);
        setElevation(GraphiteVue.dp(contexte, 6));

        poignee = new Poignee(contexte);
        addView(poignee, new LayoutParams(LayoutParams.MATCH_PARENT,
                GraphiteVue.dp(contexte, 48)));

        contenu = new FrameLayout(contexte);
        contenu.setVisibility(GONE);
        addView(contenu, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
    }

    /** Le titre de la poignée, depuis les ressources. */
    public void setTitre(int idRes) {
        poignee.titre = getContext().getText(idRes);
        poignee.invalidate();
    }

    /** Le contenu du dock (remplace le précédent). */
    public void setContenu(View vue) {
        contenu.removeAllViews();
        contenu.addView(vue, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    /** Le dock est-il déplié ? */
    public boolean deplie() {
        return deplie;
    }

    /** Replie ou déplie le dock (le chevron suit). */
    public void setDeplie(boolean etat) {
        if (deplie == etat) {
            return;
        }
        deplie = etat;
        if (etat) {
            contenu.setVisibility(VISIBLE);
            contenu.setAlpha(0f);
            contenu.setTranslationY(
                    GraphiteVue.dp(getContext(), 16));
            contenu.animate().alpha(1f).translationY(0f)
                    .setDuration(ChoixMouvement.composant());
        } else {
            contenu.animate().alpha(0f)
                    .translationY(GraphiteVue.dp(getContext(), 16))
                    .setDuration(ChoixMouvement.composant())
                    .withEndAction(() -> {
                        if (!deplie) {
                            contenu.setVisibility(GONE);
                        }
                    });
        }
        poignee.invalidate();
    }

    @Override
    public void onDrawForeground(Canvas canvas) {
        super.onDrawForeground(canvas);
        pinceau.setStyle(Paint.Style.FILL);
        pinceau.setColor(palette.bordFin);
        canvas.drawRect(0f, 0f, getWidth(),
                GraphiteVue.dp(getContext(), 1), pinceau);
    }

    /** La poignée du dock : titre à gauche, chevron à droite. */
    private final class Poignee extends View {
        private CharSequence titre = "";
        private final Paint pinceau =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        Poignee(Context contexte) {
            super(contexte);
            setClickable(true);
            setOnClickListener(vue -> setDeplie(!deplie));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            pinceau.setTextAlign(Paint.Align.LEFT);
            pinceau.setTextSize(GraphiteVue.sp(getContext(),
                    Graphite.NOTE.taille));
            pinceau.setTypeface(GraphiteVue.police(getContext(), 600,
                    false));
            pinceau.setColor(palette.texteSecondaire);
            float retrait = GraphiteVue.dp(getContext(), 18);
            float y = getHeight() / 2f
                    - (pinceau.ascent() + pinceau.descent()) / 2f;
            canvas.drawText(titre, 0, titre.length(), retrait, y,
                    pinceau);

            pinceau.setStyle(Paint.Style.STROKE);
            pinceau.setStrokeWidth(GraphiteVue.dp(getContext(), 2));
            pinceau.setStrokeCap(Paint.Cap.ROUND);
            pinceau.setColor(palette.texteSecondaire);
            float cx = getWidth() - retrait;
            float cy = getHeight() / 2f;
            float cote = GraphiteVue.dp(getContext(), 5);
            float pente = GraphiteVue.dp(getContext(), 3);
            if (deplie) {
                pinceau.setPathEffect(null);
                android.graphics.Path chevron =
                        new android.graphics.Path();
                chevron.moveTo(cx - cote, cy + pente);
                chevron.lineTo(cx, cy - pente);
                chevron.lineTo(cx + cote, cy + pente);
                canvas.drawPath(chevron, pinceau);
            } else {
                android.graphics.Path chevron =
                        new android.graphics.Path();
                chevron.moveTo(cx - cote, cy - pente);
                chevron.lineTo(cx, cy + pente);
                chevron.lineTo(cx + cote, cy - pente);
                canvas.drawPath(chevron, pinceau);
            }
        }
    }
}
