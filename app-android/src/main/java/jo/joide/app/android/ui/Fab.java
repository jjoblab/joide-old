package jo.joide.app.android.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Le FAB Construire de l'atelier (lot J, ADR-É5) : rond « réussite »
 * au repos (triangle lecture dessiné au canvas, 56 dp) ; pendant le
 * build il devient PILULE d'état — « build #n — phase » — qui ouvre la
 * console, avec sa croix d'arrêt : le build se lance ET s'arrête
 * depuis la pilule (critère du lot J). La vue ne décide rien : l'état
 * arrive posé.
 */
public final class Fab extends FrameLayout {

    /** L'auditeur des trois actions du FAB. */
    public interface AuditeurFab {
        /** Le FAB au repos : lancer le build. */
        void surLancer();

        /** La pilule : ouvrir la console. */
        void surOuvrirConsole();

        /** La croix : arrêter le build en cours. */
        void surArreter();
    }

    private final Graphite.Palette palette;
    private final android.view.View rond;
    private final LinearLayout pilule;
    private final TextView libelle;
    private final android.view.View croix;
    private boolean enCours;
    private AuditeurFab auditeur;

    public Fab(Context contexte) {
        this(contexte, null);
    }

    public Fab(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);
        setMinimumWidth(GraphiteVue.dp(contexte, 56));
        setMinimumHeight(GraphiteVue.dp(contexte, 56));

        rond = new android.view.View(contexte) {
            @Override
            protected void onDraw(Canvas canvas) {
                float cote = Math.min(getWidth(), getHeight());
                Paint pinceau = new Paint(Paint.ANTI_ALIAS_FLAG);
                pinceau.setColor(palette.reussite);
                canvas.drawCircle(cote / 2f, cote / 2f, cote / 2f,
                        pinceau);
                pinceau.setColor(palette.texteSurReussite);
                pinceau.setStyle(Paint.Style.FILL);
                Path triangle = new Path();
                float unite = cote / 24f;
                triangle.moveTo(9f * unite, 6.5f * unite);
                triangle.lineTo(9f * unite, 17.5f * unite);
                triangle.lineTo(19f * unite, 12f * unite);
                triangle.close();
                canvas.drawPath(triangle, pinceau);
            }
        };
        rond.setClickable(true);
        rond.setOnClickListener(vue -> {
            if (auditeur != null && !enCours) {
                auditeur.surLancer();
            }
        });
        LayoutParams rondParams = new LayoutParams(
                GraphiteVue.dp(contexte, 56), GraphiteVue.dp(contexte, 56),
                Gravity.CENTER);
        addView(rond, rondParams);

        pilule = new LinearLayout(contexte);
        pilule.setOrientation(LinearLayout.HORIZONTAL);
        pilule.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(palette.reussite);
        fond.setCornerRadius(Graphite.RAYON_PUCE);
        pilule.setBackground(fond);
        pilule.setClickable(true);
        pilule.setPadding(GraphiteVue.dp(contexte, 16),
                GraphiteVue.dp(contexte, 7), GraphiteVue.dp(contexte, 4),
                GraphiteVue.dp(contexte, 7));
        pilule.setOnClickListener(vue -> {
            if (auditeur != null && enCours) {
                auditeur.surOuvrirConsole();
            }
        });

        libelle = new TextView(contexte);
        GraphiteVue.typographie(libelle, Graphite.NOTE);
        libelle.setTextColor(palette.texteSurReussite);
        pilule.addView(libelle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        croix = new VueCroix(contexte);
        croix.setClickable(true);
        croix.setOnClickListener(vue -> {
            if (auditeur != null && enCours) {
                auditeur.surArreter();
            }
        });
        pilule.addView(croix, new LinearLayout.LayoutParams(
                GraphiteVue.dp(contexte, 30), GraphiteVue.dp(contexte, 30)));
        addView(pilule, new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        maj();
    }

    /** L'auditeur des actions. */
    public void setAuditeur(AuditeurFab ecouteur) {
        this.auditeur = ecouteur;
    }

    /**
     * Pose l'état : au repos le FAB rond Construire ; en cours la
     * pilule « build #n — phase » (libellé composé par l'écran).
     */
    public void setEtat(boolean buildEnCours, CharSequence texte) {
        this.enCours = buildEnCours;
        libelle.setText(texte);
        maj();
    }

    private void maj() {
        rond.setVisibility(enCours ? GONE : VISIBLE);
        pilule.setVisibility(enCours ? VISIBLE : GONE);
        requestLayout();
        invalidate();
    }

    /** La croix d'arrêt, dessinée au canvas (texteSurReussite). */
    private final class VueCroix extends android.view.View {
        VueCroix(Context contexte) {
            super(contexte);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Paint pinceau = new Paint(Paint.ANTI_ALIAS_FLAG);
            pinceau.setStyle(Paint.Style.STROKE);
            pinceau.setStrokeWidth(GraphiteVue.dp(getContext(), 2));
            pinceau.setStrokeCap(Paint.Cap.ROUND);
            pinceau.setColor(palette.texteSurReussite);
            float cote = GraphiteVue.dp(getContext(), 5);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            Path croix = new Path();
            croix.moveTo(cx - cote, cy - cote);
            croix.lineTo(cx + cote, cy + cote);
            croix.moveTo(cx + cote, cy - cote);
            croix.lineTo(cx - cote, cy + cote);
            canvas.drawPath(croix, pinceau);
        }
    }
}
