package jo.joide.app.android.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Interrupteur du kit (lot M, maquette parametres.html) : piste
 * pilule 46×27, pouce 19 qui glisse en 180 ms (ADR-É2 : mouvement
 * standard). Actif : piste teintée réussite et pouce réussite ;
 * inactif : surface haute et pouce estompé. La vue rend l'état qu'on
 * lui pose — la décision vit dans l'état d'écran pur.
 */
public final class Interrupteur extends View {

    /** Écoute les bascules par l'utilisateur. */
    public interface AuditeurInterrupteur {
        /**
         * @param actif l'état après bascule
         */
        void surBascule(boolean actif);
    }

    private final Graphite.Palette palette;
    private final Paint pisteInactive = new Paint(
            Paint.ANTI_ALIAS_FLAG);
    private final Paint pisteActive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pouceInactif = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pouceActif = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lisereInactif = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lisereActif = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean actif;
    private float progression;
    private AuditeurInterrupteur auditeur;

    public Interrupteur(Context contexte) {
        this(contexte, null);
    }

    public Interrupteur(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);
        pisteInactive.setColor(palette.surfaceHaute);
        pisteActive.setColor(Graphite.melange(palette.reussite, 0.25f,
                palette.fond));
        pouceInactif.setColor(palette.texteEstompe);
        pouceActif.setColor(palette.reussite);
        lisereInactif.setStyle(Paint.Style.STROKE);
        lisereInactif.setStrokeWidth(GraphiteVue.dp(contexte, 1));
        lisereInactif.setColor(palette.bord);
        lisereActif.setStyle(Paint.Style.STROKE);
        lisereActif.setStrokeWidth(GraphiteVue.dp(contexte, 1));
        lisereActif.setColor(Graphite.melange(palette.reussite, 0.6f,
                palette.fond));
        setClickable(true);
        // le focus par défaut du cadre dessinerait son propre fond
        setBackground(new GradientDrawable());
        setOnClickListener(v -> basculer());
    }

    /**
     * Pose l'état sans prévenir l'auditeur.
     *
     * @param actif l'état posé
     */
    public void poser(boolean actif) {
        this.actif = actif;
        this.progression = actif ? 1f : 0f;
        invalidate();
    }

    /** L'état courant. */
    public boolean actif() {
        return actif;
    }

    /** L'auditeur des bascules utilisateur. */
    public void setAuditeur(AuditeurInterrupteur ecouteur) {
        auditeur = ecouteur;
    }

    private void basculer() {
        poserAnime(!actif);
        if (auditeur != null) {
            auditeur.surBascule(actif);
        }
    }

    private void poserAnime(boolean cible) {
        actif = cible;
        ValueAnimator glisse = ValueAnimator.ofFloat(progression,
                cible ? 1f : 0f);
        glisse.setDuration(Graphite.MOUVEMENT_STANDARD);
        glisse.setInterpolator(new DecelerateInterpolator());
        glisse.addUpdateListener(animation -> {
            progression = (Float) animation.getAnimatedValue();
            invalidate();
        });
        glisse.start();
    }

    @Override
    protected void onMeasure(int largeurSpec, int hauteurSpec) {
        int largeur = GraphiteVue.dp(getContext(), 46);
        int hauteur = GraphiteVue.dp(getContext(), 27);
        setMeasuredDimension(largeur + getPaddingLeft()
                        + getPaddingRight(),
                hauteur + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onDraw(Canvas toile) {
        super.onDraw(toile);
        float gauche = getPaddingLeft();
        float haut = getPaddingTop();
        float largeur = GraphiteVue.dp(getContext(), 46);
        float hauteur = GraphiteVue.dp(getContext(), 27);
        float rayon = hauteur / 2f;

        Paint piste = progression < 0.5f ? pisteInactive : pisteActive;
        toile.drawRoundRect(gauche, haut, gauche + largeur,
                haut + hauteur, rayon, rayon, piste);
        // liseré discret, comme le composant de la maquette
        toile.drawRoundRect(gauche, haut, gauche + largeur,
                haut + hauteur, rayon, rayon,
                progression < 0.5f ? lisereInactif : lisereActif);

        float marge = GraphiteVue.dp(getContext(), 3);
        float cote = GraphiteVue.dp(getContext(), 19);
        float portee = largeur - marge * 2f - cote;
        float cx = gauche + marge + portee * progression + cote / 2f;
        float cy = haut + hauteur / 2f;
        Paint pouce = progression < 0.5f ? pouceInactif : pouceActif;
        toile.drawCircle(cx, cy, cote / 2f, pouce);
    }
}
