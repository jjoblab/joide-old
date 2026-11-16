package jo.joide.app.android.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * Colle mince entre le cadre Android et les jetons Graphite :
 * conversions dp/sp, typographie posée sur un TextView, police
 * pondérée, mode nuit du système, couleurs de fenêtre. Aucune
 * décision de design ici — tout vit dans {@link Graphite} et les
 * classes de choix ; cette classe ne fait que traduire (ADR-048).
 */
public final class GraphiteVue {

    private GraphiteVue() {
    }

    /**
     * Le mode d'affichage : {@code NIGHT_NO} donne le clair, la nuit
     * ou l'indéfini donnent le sombre (« sombre d'abord », ADR-É2) —
     * la bascule jour/nuit recrée l'activité et repasse ici.
     */
    public static Graphite.Mode modeNuit(Context contexte) {
        int masque = contexte.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return masque == Configuration.UI_MODE_NIGHT_NO
                ? Graphite.Mode.CLAIR : Graphite.Mode.SOMBRE;
    }

    /** La palette du mode courant du système. */
    public static Graphite.Palette palette(Context contexte) {
        return Graphite.pour(modeNuit(contexte));
    }

    /** Conversion dp vers pixels. */
    public static int dp(Context contexte, float valeur) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, valeur,
                contexte.getResources().getDisplayMetrics()));
    }

    /** Conversion sp vers pixels. */
    public static int sp(Context contexte, float valeur) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, valeur,
                contexte.getResources().getDisplayMetrics()));
    }

    /** Pose un style de texte : taille sp, interligne, famille, graisse. */
    public static void typographie(TextView vue, Graphite.StyleTexte style) {
        vue.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.taille);
        vue.setLineSpacing(0, style.interligne / style.taille);
        vue.setTypeface(police(vue.getContext(), style.graisse,
                style.mono));
    }

    /**
     * La police d'un style : sans système pondérée, ou monospace.
     * Les graisses passent par {@link Typeface.Builder} (API 28) ;
     * un échec de construction retombe sur le gras du cadre — jamais
     * de promesse de poids non tenue.
     */
    public static Typeface police(Context contexte, int graisse,
                                  boolean mono) {
        String famille = mono ? "monospace" : "sans-serif";
        if (graisse <= 400) {
            return Typeface.create(famille, Typeface.NORMAL);
        }
        Typeface ponderee = new Typeface.Builder(famille)
                .setWeight(graisse).build();
        if (ponderee != null) {
            return ponderee;
        }
        return Typeface.create(famille, Typeface.BOLD);
    }

    /**
     * Couleurs de fenêtre de l'activité : barre d'état, barre de
     * navigation et fond — depuis les constantes, jamais de ressources
     * (ADR-048 : les valeurs vivent une seule fois dans Graphite).
     *
     * <p>Les deux setters de barres sont dépréciés en API 35 (versant
     * edge-to-edge du système) mais restent le SEUL moyen de colorer
     * les barres de minSdk 28 à 34 ; sur 35+ ils sont ignorés et le
     * fond de fenêtre porte la couleur — suppression documentée, pas
     * un angle mort.</p>
     */
    @SuppressWarnings("deprecation")
    public static void fondEcran(Activity activite,
                                 Graphite.Palette palette) {
        android.view.Window fenetre = activite.getWindow();
        fenetre.setStatusBarColor(palette.fond);
        fenetre.setNavigationBarColor(palette.fond);
        fenetre.setBackgroundDrawable(
                new ColorDrawable(palette.fond));
    }
}
