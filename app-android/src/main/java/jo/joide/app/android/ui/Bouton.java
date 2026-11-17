package jo.joide.app.android.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import jo.joide.app.android.ui.choix.ChoixBouton;
import jo.joide.app.android.ui.choix.ChoixTexte;

/**
 * Bouton du kit Graphite : primaire (fond accent, texte sur
 * accent), tonal (surface haute), texte (transparent, accent) —
 * dessiné au canvas, texte centré au corps 15 demi-gras, rayon 12
 * dp, hauteur 52 dp. Les couleurs et états viennent de
 * {@link ChoixBouton} ; la vue ne décide rien. Quand le texte
 * déborde, la troncature suit {@link ChoixTexte} (coupe par mot).
 */
public final class Bouton extends View {

    private static final float HAUTEUR_DP = 52f;
    private static final float RETRAIT_H_DP = 24f;

    private final Graphite.Palette palette;
    private final Paint pinceau = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ChoixBouton.Variante variante = ChoixBouton.Variante.TONAL;
    private CharSequence texte = "";
    private boolean appuye;

    public Bouton(Context contexte) {
        this(contexte, null);
    }

    public Bouton(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);
        pinceau.setTextAlign(Paint.Align.CENTER);
        setClickable(true);
    }

    /** La variante du bouton (primaire, tonal ou texte). */
    public void setVariante(ChoixBouton.Variante nouvelle) {
        variante = nouvelle;
        maj();
    }

    /** Le libellé du bouton. */
    public void setTexte(CharSequence libelle) {
        texte = libelle == null ? "" : libelle;
        maj();
    }

    /** Le libellé du bouton, depuis les ressources. */
    public void setTexte(int idRes) {
        setTexte(getResources().getText(idRes));
    }

    /** Le libellé courant. */
    public CharSequence texte() {
        return texte;
    }

    @Override
    public void setEnabled(boolean actif) {
        super.setEnabled(actif);
        maj();
    }

    private void maj() {
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int specLargeur, int specHauteur) {
        armerPinceau();
        float largeurTexte = pinceau.measureText(texte.toString());
        int retrait = GraphiteVue.dp(getContext(), RETRAIT_H_DP);
        int voulu = Math.round(largeurTexte) + retrait * 2;
        setMeasuredDimension(
                resoudre(specLargeur, Math.max(voulu, retrait * 2)),
                resoudre(specHauteur,
                        GraphiteVue.dp(getContext(), HAUTEUR_DP)));
    }

    private static int resoudre(int spec, int voulu) {
        switch (android.view.View.MeasureSpec.getMode(spec)) {
            case android.view.View.MeasureSpec.EXACTLY:
                return android.view.View.MeasureSpec.getSize(spec);
            case android.view.View.MeasureSpec.AT_MOST:
                return Math.min(voulu,
                        android.view.View.MeasureSpec.getSize(spec));
            default:
                return voulu;
        }
    }

    private void armerPinceau() {
        pinceau.setTextSize(GraphiteVue.sp(getContext(),
                Graphite.CORPS.taille));
        pinceau.setTypeface(GraphiteVue.police(getContext(), 600, false));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        ChoixBouton.Apparence apparence = ChoixBouton.apparence(
                palette, variante, !isEnabled(), appuye);
        armerPinceau();
        int alpha = Math.round(apparence.opacite * 255f);
        int largeur = getWidth();
        int hauteur = getHeight();
        if (apparence.fond != 0) {
            pinceau.setStyle(Paint.Style.FILL);
            pinceau.setColor(apparence.fond);
            pinceau.setAlpha(alpha);
            float rayon = GraphiteVue.dp(getContext(),
                    Graphite.RAYON_COMPOSANT);
            canvas.drawRoundRect(0f, 0f, largeur, hauteur,
                    rayon, rayon, pinceau);
        }
        pinceau.setColor(apparence.texte);
        pinceau.setAlpha(alpha);
        CharSequence affiche = texte;
        int retrait = GraphiteVue.dp(getContext(), RETRAIT_H_DP);
        float disponible = Math.max(1f, largeur - retrait * 2f);
        if (affiche.length() > 0
                && pinceau.measureText(affiche.toString()) > disponible) {
            float parCaractere = pinceau.measureText(affiche.toString())
                    / affiche.length();
            int budget = Math.max(1, (int) (disponible / parCaractere));
            affiche = ChoixTexte.coupeParMot(affiche.toString(), budget);
        }
        float y = hauteur / 2f
                - (pinceau.ascent() + pinceau.descent()) / 2f;
        canvas.drawText(affiche, 0, affiche.length(),
                largeur / 2f, y, pinceau);
    }

    @Override
    public boolean onTouchEvent(MotionEvent evenement) {
        if (!isEnabled()) {
            return false;
        }
        boolean dedans;
        switch (evenement.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                appuye = true;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                dedans = evenement.getX() >= 0f
                        && evenement.getX() <= getWidth()
                        && evenement.getY() >= 0f
                        && evenement.getY() <= getHeight();
                if (dedans != appuye) {
                    appuye = dedans;
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                dedans = evenement.getX() >= 0f
                        && evenement.getX() <= getWidth()
                        && evenement.getY() >= 0f
                        && evenement.getY() <= getHeight();
                appuye = false;
                invalidate();
                if (dedans) {
                    performClick();
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                appuye = false;
                invalidate();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
