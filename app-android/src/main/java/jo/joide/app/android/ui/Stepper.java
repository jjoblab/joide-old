package jo.joide.app.android.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Stepper du kit (lot M, maquette parametres.html) : pastille
 * « − valeur + » — surface haute, rayon 999, valeur monospace.
 * La vue ne décide rien : bornes et pas sont posés par l'écran, le
 * bouton hors borne est désactivé (jamais un clic silencieusement
 * ignoré), l'auditeur apprend chaque changement utilisateur.
 */
public final class Stepper extends LinearLayout {

    /** Écoute les changements par l'utilisateur. */
    public interface AuditeurStepper {
        /**
         * @param valeur la valeur après le pas (toujours dans les bornes)
         */
        void surChangement(double valeur);
    }

    private final Graphite.Palette palette;
    private final TextView boutonMoins;
    private final TextView valeur;
    private final TextView boutonPlus;
    private AuditeurStepper auditeur;

    private double min;
    private double max;
    private double pas = 1d;
    private double courant;
    private boolean decimal;

    public Stepper(Context contexte) {
        this(contexte, null);
    }

    public Stepper(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int interne = GraphiteVue.dp(contexte, 3);
        setPadding(interne, interne, interne, interne);
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(palette.surfaceHaute);
        fond.setStroke(GraphiteVue.dp(contexte, 1), palette.bordFin);
        fond.setCornerRadius(GraphiteVue.dp(contexte,
                Graphite.RAYON_PUCE));
        setBackground(fond);

        boutonMoins = pastille("−");
        boutonMoins.setOnClickListener(v -> avancer(-pas));
        addView(boutonMoins, new LayoutParams(
                GraphiteVue.dp(contexte, 30),
                GraphiteVue.dp(contexte, 30)));

        valeur = new TextView(contexte);
        GraphiteVue.typographie(valeur, Graphite.CODE_COMPACT);
        valeur.setTextColor(palette.texte);
        valeur.setGravity(Gravity.CENTER);
        valeur.setMinWidth(GraphiteVue.dp(contexte, 52));
        addView(valeur, new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));

        boutonPlus = pastille("+");
        boutonPlus.setOnClickListener(v -> avancer(pas));
        addView(boutonPlus, new LayoutParams(
                GraphiteVue.dp(contexte, 30),
                GraphiteVue.dp(contexte, 30)));
    }

    /**
     * Pose les bornes, le pas et la valeur (sans prévenir l'auditeur).
     *
     * @param min     plancher
     * @param max     plafond
     * @param pas     incrément (strictement positif)
     * @param courant la valeur posée (bornée)
     * @param decimal vrai : une décimale affichée (« 13,5 »)
     */
    public void poser(double min, double max, double pas,
                      double courant, boolean decimal) {
        if (pas <= 0d || max < min) {
            throw new IllegalArgumentException("invalid stepper bounds");
        }
        this.min = min;
        this.max = max;
        this.pas = pas;
        this.decimal = decimal;
        this.courant = Math.max(min, Math.min(max, courant));
        maj();
    }

    /** La valeur courante. */
    public double valeur() {
        return courant;
    }

    /** L'auditeur des changements utilisateur. */
    public void setAuditeur(AuditeurStepper ecouteur) {
        auditeur = ecouteur;
    }

    private void avancer(double delta) {
        double cible = Math.max(min, Math.min(max, courant + delta));
        if (cible == courant) {
            return;
        }
        courant = cible;
        maj();
        if (auditeur != null) {
            auditeur.surChangement(courant);
        }
    }

    private void maj() {
        valeur.setText(decimal
                ? String.format(Locale.ROOT, "%.1f", courant)
                : String.format(Locale.ROOT, "%.0f", courant));
        boutonMoins.setEnabled(courant > min);
        boutonMoins.setAlpha(courant > min ? 1f : 0.35f);
        boutonPlus.setEnabled(courant < max);
        boutonPlus.setAlpha(courant < max ? 1f : 0.35f);
    }

    private TextView pastille(String glyphe) {
        TextView pastille = new TextView(getContext());
        GraphiteVue.typographie(pastille, Graphite.NOTE);
        pastille.setText(glyphe);
        pastille.setTextColor(palette.texteSecondaire);
        pastille.setGravity(Gravity.CENTER);
        GradientDrawable rond = new GradientDrawable();
        rond.setShape(GradientDrawable.OVAL);
        pastille.setBackground(rond);
        return pastille;
    }
}
