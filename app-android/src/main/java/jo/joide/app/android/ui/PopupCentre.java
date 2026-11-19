package jo.joide.app.android.ui;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.PopupWindow;

/**
 * Le popup CENTRÉ AVEC VOILE (lot A7, ADR-085 décision 12) : ce qui
 * EXIGE une réponse — confirmation destructive, saisie, diff de
 * permission, usages, historique, connexions MCP, écran texte. La
 * carte vit dans {@code popup_*.xml} gonflé par binding ; la classe
 * ne fabrique aucune vue, elle porte la mécanique du gabarit :
 *
 * <ul>
 * <li><b>le voile</b> : une vue-voile du LAYOUT RACINE de l'écran
 * ({@code ?attr/jo_rideau}, cliquable) — la seule voie praticable :
 * {@code PopupWindow} n'expose PAS sa fenêtre (aucun
 * {@code getWindow()}, aucun drapeau public), le dim du cadre ne se
 * pose que sur une fenêtre de DIALOGUE, interdite par D3 ; la vue-voile
 * assombrit l'écran, la carte vit dans sa PROPRE fenêtre AU-DESSUS
 * (choix uniformisé ici, justifié à l'ADR-085) ;</li>
 * <li><b>le retour système</b> : {@code focusable} — la fenêtre prend
 * le focus et le retour la ferme EN PREMIER, avant feuille, tiroirs
 * et sortie (l'ordre est testé en JVM dans {@code EtatPopups}) ;</li>
 * <li><b>le clavier</b> : les popups à saisie demandent
 * {@code INPUT_METHOD_NEEDED} + {@code SOFT_INPUT_ADJUST_RESIZE} et
 * prennent le focus sur le champ (décision 12) ;</li>
 * <li><b>la hauteur</b> : bornée à l'écran — le corps (racine
 * ScrollView du popup_*.xml) défile dans la fenêtre bornée ;</li>
 * <li><b>l'accessibilité</b> : {@code setAccessibilityPaneTitle} dit
 * le pane à TalkBack (API 26, minSdk 28 : sans garde).</li>
 * </ul>
 *
 * <p>La fermeture explicite (onPause / onDestroy /
 * onConfigurationChanged) est portée par le PORTE-POPUPS de l'écran
 * ({@link PortePopups}) — jamais ici : un popup n'est jamais
 * restauré, l'état de saisie vit dans l'{@code Etat*}.</p>
 */
public final class PopupCentre {

    /** La largeur des cartes centrées (bornée à l'écran moins 32). */
    private static final int LARGEUR = 360;

    /** La marge verticale laissée autour d'une carte trop haute. */
    private static final int MARGE_HAUTEUR = 48;

    private PopupCentre() {
    }

    /**
     * Montre {@code carte} centrée sur le voile de l'écran.
     *
     * @param voile       la vue-voile du layout racine de l'écran
     *                    (montrée ici, cachée à la fermeture)
     * @param carte       le corps du popup (popup_*.xml gonflé, la
     *                    carte porte le fond)
     * @param titrePane   le titre du pane (accessibilité,
     *                    {@code setAccessibilityPaneTitle})
     * @param saisie      le popup porte-t-il une saisie (clavier
     *                    requis, redimensionné, focus au champ)
     * @param aLaFermeture le rappel de fermeture (peut être nul) —
     *                     TOUTE fermeture l'appelle, une seule fois
     * @return la fenêtre montrée (l'appelant la ferme sur action)
     */
    // SOFT_INPUT_ADJUST_RESIZE : déprécié à l'API 35 (les insets ont
    // pris le relais sur les NOUVELLES plateformes) — la décision 12
    // de l'ADR-085 le retient : sous targetSdk 28, c'est la voie qui
    // rapporte le clavier de façon fiable sur API 28/29
    @SuppressWarnings("deprecation")
    public static PopupWindow montrer(View voile, View carte,
                                      CharSequence titrePane,
                                      boolean saisie,
                                      Runnable aLaFermeture) {
        return montrer(voile, carte, titrePane, saisie, LARGEUR,
                aLaFermeture);
    }

    /**
     * La variante À LARGEUR LIBRE (lot C5) : les grands dialogues de
     * la famille IA se posent plus larges que la carte commune (le
     * sélecteur de modèles, l'écran des usages) — la borne écran
     * moins 32 dp s'applique toujours.
     *
     * @param voile        la vue-voile du layout racine de l'écran
     * @param carte        le corps du popup
     * @param titrePane    le titre du pane (accessibilité)
     * @param saisie       le popup porte-t-il une saisie
     * @param largeurDp    la largeur demandée en dp
     * @param aLaFermeture le rappel de fermeture (peut être nul)
     * @return la fenêtre montrée
     */
    @SuppressWarnings("deprecation")
    public static PopupWindow montrer(View voile, View carte,
                                      CharSequence titrePane,
                                      boolean saisie, int largeurDp,
                                      Runnable aLaFermeture) {
        android.content.Context contexte = voile.getContext();
        int ecran = contexte.getResources().getDisplayMetrics()
                .widthPixels;
        int hauteurEcran = contexte.getResources().getDisplayMetrics()
                .heightPixels;
        int largeur = Math.min(GraphiteVue.dp(contexte, largeurDp),
                ecran - GraphiteVue.dp(contexte, 32));

        // focusable : le retour système ferme le popup en premier
        PopupWindow fenetre = new PopupWindow(carte, largeur,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        // le fond est porté par la CARTE du layout — le cadre du
        // popup reste transparent ; PAS de fermeture au tap extérieur
        // (une confirmation ne se laisse pas fermer du bout du doigt)
        fenetre.setBackgroundDrawable(new android.graphics.drawable
                .ColorDrawable(android.graphics.Color.TRANSPARENT));
        fenetre.setOutsideTouchable(false);
        if (saisie) {
            // décision 12 : le clavier suit la saisie du popup
            fenetre.setInputMethodMode(
                    PopupWindow.INPUT_METHOD_NEEDED);
            fenetre.setSoftInputMode(
                    android.view.WindowManager.LayoutParams
                            .SOFT_INPUT_ADJUST_RESIZE);
        }
        fenetre.setElevation(GraphiteVue.dp(contexte, 12));
        fenetre.setOnDismissListener(() -> {
            voile.setVisibility(View.GONE);
            if (aLaFermeture != null) {
                aLaFermeture.run();
            }
        });

        // la hauteur se borne à l'écran : la racine ScrollView du
        // corps défile dans la fenêtre bornée
        carte.measure(View.MeasureSpec.makeMeasureSpec(largeur,
                View.MeasureSpec.EXACTLY), View.MeasureSpec
                .makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int borne = hauteurEcran - GraphiteVue.dp(contexte,
                MARGE_HAUTEUR);
        if (carte.getMeasuredHeight() > borne) {
            fenetre.setHeight(borne);
        }

        voile.setVisibility(View.VISIBLE);
        fenetre.showAtLocation(voile, Gravity.CENTER, 0, 0);

        // le pane dit son titre à TalkBack (API 26 ≤ minSdk 28)
        if (titrePane != null) {
            carte.setAccessibilityPaneTitle(titrePane);
        }
        if (saisie) {
            View champ = premierChamp(carte);
            if (champ != null) {
                champ.requestFocus();
            }
        }
        return fenetre;
    }

    /**
     * Le premier champ de saisie du corps (le focus initial des
     * popups à saisie, décision 12) — parcours en profondeur, borné.
     */
    private static View premierChamp(View racine) {
        return premierChamp(racine, new int[]{0});
    }

    private static View premierChamp(View vue, int[] garde) {
        if (garde[0]++ > 64) {
            return null;
        }
        if (vue instanceof EditText) {
            return vue;
        }
        if (vue instanceof ViewGroup groupe) {
            for (int i = 0; i < groupe.getChildCount(); i++) {
                View trouve = premierChamp(groupe.getChildAt(i), garde);
                if (trouve != null) {
                    return trouve;
                }
            }
        }
        return null;
    }
}
