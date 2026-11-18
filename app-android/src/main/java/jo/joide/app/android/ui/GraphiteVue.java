package jo.joide.app.android.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.TextView;

import jo.joide.app.android.parametres.PreferencesAndroid;
import jo.joide.app.android.parametres.Reglages;

/**
 * Colle mince entre le cadre Android et les jetons Graphite :
 * conversions dp/sp, typographie posée sur un TextView, police
 * pondérée, mode nuit du système, couleurs de fenêtre. Aucune
 * décision de design ici — tout vit dans {@link Graphite} et les
 * classes de choix ; cette classe ne fait que traduire (ADR-048).
 *
 * <p>Depuis le lot M (ADR-É8/054), la palette tient compte des
 * réglages : le thème posé (sombre par défaut, système ou clair) et
 * l'accent du nuancier, lus dans les préférences privées à chaque
 * appel — un changement de réglage se voit à la recréation de
 * l'écran, jamais dans une vue déjà posée. L'échelle du texte
 * d'interface s'applique au passage de la typographie.</p>
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

    /**
     * La palette des réglages posés (lot M) : le thème décide du
     * mode — SOMBRE et CLAIR sont explicites, SYSTEME suit la nuit du
     * cadre — puis l'accent du nuancier remplace le jeton (le seul
     * réellement appliqué, ADR-É8).
     */
    public static Graphite.Palette palette(Context contexte) {
        Reglages reglages = Reglages.lire(
                PreferencesAndroid.source(contexte));
        Graphite.Mode mode = switch (reglages.theme) {
            case SOMBRE -> Graphite.Mode.SOMBRE;
            case CLAIR -> Graphite.Mode.CLAIR;
            case SYSTEME -> modeNuit(contexte);
        };
        Graphite.Palette base = Graphite.pour(mode);
        int index = Math.max(0,
                Math.min(Graphite.ACCENTS.length - 1, reglages.accent));
        return Graphite.avecAccent(base, Graphite.ACCENTS[index]);
    }

    /**
     * La RESSOURCE de thème des écrans (correction du crash du
     * 2026-11-18) : les ?attr/jo_* consommés par les layouts XML de
     * l'atelier v2 exigent Theme.JoIDE.Sombre/Clair POSÉ AVANT TOUTE
     * INFLATION — le manifeste reste framework, le kit programmatique
     * n'en dépend pas. Même résolution que {@link #palette(Context)}.
     */
    public static int resTheme(Context contexte) {
        Reglages reglages = Reglages.lire(
                PreferencesAndroid.source(contexte));
        boolean clair = switch (reglages.theme) {
            case SOMBRE -> false;
            case CLAIR -> true;
            case SYSTEME -> modeNuit(contexte) == Graphite.Mode.CLAIR;
        };
        return clair ? jo.joide.app.android.R.style.Theme_JoIDE_Clair
                : jo.joide.app.android.R.style.Theme_JoIDE_Sombre;
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

    /**
     * Pose un style de texte : taille sp, interligne, famille, graisse
     * — l'échelle du texte d'interface des réglages (lot M) s'applique
     * à la taille ET à l'interligne, le rapport du style reste.
     */
    public static void typographie(TextView vue, Graphite.StyleTexte style) {
        float echelle = echelleTexteUi(vue.getContext());
        vue.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                style.taille * echelle);
        vue.setLineSpacing(0, style.interligne / style.taille);
        vue.setTypeface(police(vue.getContext(), style.graisse,
                style.mono));
    }

    /**
     * @param contexte le contexte
     * @return le facteur d'échelle du texte d'interface (1 par défaut)
     */
    public static float echelleTexteUi(Context contexte) {
        return Reglages.lire(PreferencesAndroid.source(contexte))
                .echelleUi / 100f;
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
