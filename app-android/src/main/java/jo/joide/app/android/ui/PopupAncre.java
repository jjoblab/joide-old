package jo.joide.app.android.ui;

import android.view.View;
import android.widget.PopupWindow;

import jo.joide.app.android.R;

/**
 * Le popup ANCRÉ (lot T, ADR-065 ; généralisé au lot A7, ADR-085
 * décision 12) : une carte qui tombe — ou monte — depuis la vue qui
 * l'appelle, jamais un dialogue centré qui coupe la conversation. Le
 * précédent des menus d'en-tête de l'IDE de référence : le menu vit près de
 * son bouton, le doigt ne quitte pas son contexte.
 *
 * <p>Lot A7 — la GÉNÉRALISATION : la carte (fond surface, liseré
 * bord-fin, rayon carte, retrait 8) vit désormais dans le CORPS
 * {@code popup_*.xml} gonflé par binding — la classe ne fabrique plus
 * aucune vue, elle ne porte que la mécanique d'ancrage : tombée sous
 * l'ancre quand la place existe, montée au-dessus sinon, hauteur
 * bornée à la place qui reste (le corps défile dedans),
 * disparition au tap extérieur, retour système fermé
 * ({@code focusable} — la fenêtre prend le focus et le retour la
 * ferme AVANT toute autre surcouche de l'écran).</p>
 */
public final class PopupAncre {

    private PopupAncre() {
    }

    /**
     * Montre {@code carte} ancrée à {@code ancre} : sous l'ancre si
     * la place suffit, au-dessus sinon (le composeur ne se fait pas
     * recouvrir). Le tap extérieur ferme ; aucun rappel de
     * fermeture.
     *
     * @param ancre     la vue à laquelle le popup s'accroche
     * @param carte     le corps du popup (popup_*.xml gonflé, fond
     *                  porté par le layout)
     * @param largeurDp la largeur de la carte en dp (bornée à l'écran)
     * @return la fenêtre montrée (l'appelant la ferme sur action)
     */
    public static PopupWindow montrer(View ancre, View carte,
                                      int largeurDp) {
        return montrer(ancre, carte, largeurDp, null);
    }

    /**
     * Montre {@code carte} ancrée à {@code ancre}, avec un rappel de
     * fermeture (le porte-popups s'y retire ; tout fermeture — tap
     * extérieur, retour, action — l'appelle, une seule fois).
     *
     * @param ancre         la vue à laquelle le popup s'accroche
     * @param carte         le corps du popup (popup_*.xml gonflé)
     * @param largeurDp     la largeur de la carte en dp (bornée à
     *                      l'écran)
     * @param aLaFermeture  le rappel de fermeture (peut être nul)
     * @return la fenêtre montrée (l'appelant la ferme sur action)
     */
    public static PopupWindow montrer(View ancre, View carte,
                                      int largeurDp,
                                      Runnable aLaFermeture) {
        android.content.Context contexte = ancre.getContext();
        // les jetons du lot (parité ThemesM3Test) se lisent des
        // ressources — plus de dimension codée en dur ici
        int retrait = contexte.getResources().getDimensionPixelOffset(
                R.dimen.jo_pad_popup);
        int chute = contexte.getResources().getDimensionPixelOffset(
                R.dimen.jo_ecart_popup_ancre);
        int ecran = contexte.getResources().getDisplayMetrics()
                .widthPixels;
        int largeur = Math.min(GraphiteVue.dp(contexte, largeurDp),
                ecran - GraphiteVue.dp(contexte, 16));

        // focusable : le RETOUR système ferme le popup en premier —
        // la fenêtre prend le focus, l'écran ne voit pas la touche
        PopupWindow fenetre = new PopupWindow(carte, largeur,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        // le fond TRANSPARENT (pas nul) porte le tap extérieur : la
        // fermeture au dehors reste la règle des menus
        fenetre.setBackgroundDrawable(new android.graphics.drawable
                .ColorDrawable(android.graphics.Color.TRANSPARENT));
        fenetre.setOutsideTouchable(true);
        fenetre.setElevation(GraphiteVue.dp(contexte, 12));
        if (aLaFermeture != null) {
            fenetre.setOnDismissListener(() -> aLaFermeture.run());
        }

        // mesurée une fois : la hauteur réelle borne la pose
        carte.measure(View.MeasureSpec.makeMeasureSpec(largeur,
                View.MeasureSpec.EXACTLY), View.MeasureSpec
                .makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int hauteur = carte.getMeasuredHeight();

        // la fenêtre décide en SES coordonnées : l'ancre vivant-elle
        // dans la moitié basse ? la carte monte au-dessus d'elle —
        // jamais un popup qui recouvre le composeur
        int[] cote = new int[2];
        ancre.getLocationInWindow(cote);
        View racine = ancre.getRootView();
        int hauteurFenetre = racine.getHeight() > 0
                ? racine.getHeight()
                : contexte.getResources().getDisplayMetrics()
                        .heightPixels;
        boolean auDessus = cote[1] > hauteurFenetre / 2;
        int place = auDessus
                ? cote[1] - retrait
                : hauteurFenetre - (cote[1] + ancre.getHeight())
                        - retrait;
        if (hauteur > place && place > GraphiteVue.dp(contexte, 120)) {
            // la carte tient dans la place qui reste, et elle seule —
            // le corps défilant (racine ScrollView du popup_*.xml)
            // s'étend à la hauteur posée
            fenetre.setHeight(place);
        }
        if (auDessus) {
            fenetre.showAsDropDown(ancre, 0,
                    -ancre.getHeight() - hauteur - chute);
        } else {
            fenetre.showAsDropDown(ancre, 0, chute);
        }
        return fenetre;
    }
}
