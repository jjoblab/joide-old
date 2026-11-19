package jo.joide.app.android.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import jo.joide.app.android.R;
import jo.joide.app.android.ui.choix.ChoixCarte;

/**
 * La tuile tonale des cartes de projet (46 dp, rayon 12, glyphe dossier)
 * extraite du kit {@code Carte} pour se DÉCLARER EN XML (ADR-083 §2 :
 * constructeur {@code (Context, AttributeSet)} ; ADR-085 lot A2). Le fond
 * et le glyphe se calculent depuis la palette Graphite — le rôle dépend
 * du type de projet (ChoixCarte en reste la source) et l'accent peut être
 * celui du nuancier : ces couleurs ne vivent PAS en XML (décision 8 de
 * l'ADR-085 : couleurs calculées dynamiquement).
 */
public final class TuileProjet extends View {

    private ChoixCarte.TypeTuile type = ChoixCarte.TypeTuile.VIDE;
    private final Graphite.Palette palette;
    private final Paint pinceau = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TuileProjet(Context contexte) {
        this(contexte, null);
    }

    public TuileProjet(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);
        // lot A2 (ADR-085) : le type se pose DEPUIS LE XML, motif du kit
        // (styleable + obtainStyledAttributes, jamais de valeur codée)
        if (attributs != null) {
            android.content.res.TypedArray pose = contexte
                    .obtainStyledAttributes(attributs,
                            R.styleable.JoTuileProjet);
            int typePose = pose.getInt(
                    R.styleable.JoTuileProjet_jo_tuile_type, -1);
            if (typePose >= 0
                    && typePose < ChoixCarte.TypeTuile.values().length) {
                type = ChoixCarte.TypeTuile.values()[typePose];
            }
            pose.recycle();
        }
    }

    /** Le type porté par la tuile (fond et glyphe suivent ChoixCarte). */
    public void poserType(ChoixCarte.TypeTuile nouveau) {
        type = nouveau;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        pinceau.setStyle(Paint.Style.FILL);
        pinceau.setColor(ChoixCarte.tuile(palette, type));
        float rayon = GraphiteVue.dp(getContext(),
                Graphite.RAYON_COMPOSANT);
        canvas.drawRoundRect(0f, 0f, getWidth(), getHeight(),
                rayon, rayon, pinceau);
        float unite = GraphiteVue.dp(getContext(), 1f);
        pinceau.setColor(ChoixCarte.icone(palette, type));
        pinceau.setStyle(Paint.Style.STROKE);
        pinceau.setStrokeWidth(GraphiteVue.dp(getContext(), 1.7f));
        pinceau.setStrokeCap(Paint.Cap.ROUND);
        pinceau.setStrokeJoin(Paint.Join.ROUND);
        float cote = Graphite.TUILE * unite;
        Carte.dessinerDossier(canvas, pinceau, unite,
                (cote - 24f * unite) / 2f,
                (cote - 24f * unite) / 2f);
    }
}
