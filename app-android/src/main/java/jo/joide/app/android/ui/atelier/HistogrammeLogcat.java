package jo.joide.app.android.ui.atelier;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import jo.joide.app.android.atelier.EtatLogcat;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * Lot A6 (ADR-085 décision 1) — l'histogramme du débit logcat (§3.6
 * de l'atelier v2) : les 9 barres de 3 dp (écartées de 2, calées en
 * bas, rayon 2, la barre d'une fenêtre en erreur en couleur erreur,
 * les autres estompées) se DESSINENT au Canvas — la feuille posait
 * hier une à une des vues par code (la dernière vue instanciée de
 * {@code FeuilleBasse}, sortie des exceptions du filet du lot A5 :
 * seules les vues dessinées restent du code). La vue ne décide rien :
 * l'état arrive posé par la feuille depuis
 * {@link EtatLogcat.Histogramme}.
 */
public final class HistogrammeLogcat extends View {

    /** La largeur d'une barre (dp). */
    private static final int LARGEUR_BARRE_DP = 3;

    /** L'écart entre deux barres (dp). */
    private static final int ECART_BARRE_DP = 2;

    /** Le rayon d'une barre (dp). */
    private static final int RAYON_BARRE_DP = 2;

    private final Graphite.Palette palette;
    private final Paint pinceau = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barre = new RectF();
    private EtatLogcat.Histogramme histogramme;

    public HistogrammeLogcat(Context contexte) {
        this(contexte, null);
    }

    public HistogrammeLogcat(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);
    }

    /** Pose l'état du débit (les 9 barres) et redessine. */
    public void poser(EtatLogcat.Histogramme etat) {
        this.histogramme = etat;
        invalidate();
    }

    @Override
    protected void onMeasure(int largeurSpec, int hauteurSpec) {
        // la largeur intrinsèque : chaque barre porte son écart (2) et
        // sa largeur (3), la première son écart d'entrée — l'identité
        // de la rangée d'hier (wrap_content mesuré par le LinearLayout,
        // barres à 2 dp du bord gauche)
        int largeurPx = GraphiteVue.dp(getContext(),
                (LARGEUR_BARRE_DP + ECART_BARRE_DP)
                        * EtatLogcat.BARRES_HISTOGRAMME + ECART_BARRE_DP);
        setMeasuredDimension(
                resolveSize(largeurPx, largeurSpec),
                getDefaultSize(getSuggestedMinimumHeight(), hauteurSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (histogramme == null) {
            return;
        }
        int hauteur = getHeight();
        int largeurBarre = GraphiteVue.dp(getContext(), LARGEUR_BARRE_DP);
        int ecart = GraphiteVue.dp(getContext(), ECART_BARRE_DP);
        float rayon = GraphiteVue.dp(getContext(), RAYON_BARRE_DP);
        for (int i = 0; i < EtatLogcat.BARRES_HISTOGRAMME; i++) {
            int haute = GraphiteVue.dp(getContext(),
                    Math.max(1, histogramme.hauteurs()[i]));
            pinceau.setColor(histogramme.erreurs()[i] ? palette.erreur
                    : palette.texteEstompe);
            float gauche = ecart + i * (largeurBarre + ecart);
            barre.set(gauche, hauteur - haute, gauche + largeurBarre,
                    hauteur);
            canvas.drawRoundRect(barre, rayon, rayon, pinceau);
        }
    }
}
