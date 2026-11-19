package jo.joide.app.android.atelier;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import jo.joide.app.android.R;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * Le damier neutre derrière le rendu de l'aperçu (lot L, ADR-É7 ; lot
 * A5 : extrait de VueApercu pour se DÉCLARER EN XML — le contrat
 * (Context, AttributeSet) d'ADR-083 §2) : le résultat de la brique
 * LayoutLib n'est jamais retouché ni embellie, le damier ne fait que
 * l'encadrer. Dessiné au {@link Canvas} (vues dessinées : le code
 * reste la voie, ADR-085) — cases 8 dp, deux gris (bord fin sur fond).
 */
public final class DamierApercu extends FrameLayout {

    private final Graphite.Palette palette;
    private final Paint pinceau = new Paint();

    public DamierApercu(Context contexte) {
        this(contexte, null);
    }

    public DamierApercu(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);
        setBackgroundColor(palette.fond);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cote = getResources().getDimensionPixelSize(
                R.dimen.jo_cote_case_damier);
        pinceau.setColor(palette.bordFin);
        for (float y = 0; y < getHeight(); y += cote) {
            for (float x = 0; x < getWidth(); x += cote) {
                if (((x / cote) + (y / cote)) % 2 == 0) {
                    canvas.drawRect(x, y, x + cote, y + cote, pinceau);
                }
            }
        }
    }
}
