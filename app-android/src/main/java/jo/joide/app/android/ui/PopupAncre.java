package jo.joide.app.android.ui;

import android.view.View;
import android.widget.PopupWindow;

/**
 * Le popup ANCRÉ (lot T, ADR-065) : une carte arrondie qui tombe —
 * ou monte — depuis la vue qui l'appelle, jamais un dialogue centré
 * qui coupe la conversation. Le précédent des menus d'en-tête de
 * CodeAssist : le menu vit près de son bouton, le doigt ne quitte
 * pas son contexte. La classe ne décide RIEN du contenu — l'atelier
 * compose les vues, elle ne porte que la mécanique d'ancrage :
 * tombée sous l'ancre quand la place existe, montée au-dessus
 * sinon, disparition au tap extérieur.
 */
public final class PopupAncre {

    private PopupAncre() {
    }

    /**
     * Montre {@code contenu} ancré à {@code ancre} : sous l'ancre si
     * la place suffit, au-dessus sinon (le composeur ne se fait pas
     * recouvrir). La carte porte le fond surface, le rayon carte et
     * le bord fin de la palette ; le tap extérieur la ferme.
     *
     * @param ancre     la vue à laquelle le popup s'accroche
     * @param palette   la palette Graphite
     * @param contenu   la vue du contenu (déjà défilante si longue)
     * @param largeurDp la largeur de la carte en dp (bornée à l'écran)
     * @return la fenêtre montrée (l'appelant la ferme sur action)
     */
    public static PopupWindow montrer(View ancre,
                                      Graphite.Palette palette,
                                      View contenu, int largeurDp) {
        android.content.Context contexte = ancre.getContext();
        android.widget.FrameLayout carte = new android.widget
                .FrameLayout(contexte);
        android.graphics.drawable.GradientDrawable fond =
                new android.graphics.drawable.GradientDrawable();
        fond.setColor(palette.surface);
        fond.setCornerRadius(GraphiteVue.dp(contexte,
                Graphite.RAYON_CARTE));
        fond.setStroke(GraphiteVue.dp(contexte, 1), palette.bordFin);
        carte.setBackground(fond);
        int padding = GraphiteVue.dp(contexte, 8);
        carte.setPadding(padding, padding, padding, padding);
        carte.addView(contenu, new android.widget.FrameLayout
                .LayoutParams(android.view.ViewGroup.LayoutParams
                .MATCH_PARENT, android.view.ViewGroup.LayoutParams
                .WRAP_CONTENT));

        int ecran = contexte.getResources().getDisplayMetrics()
                .widthPixels;
        int largeur = Math.min(GraphiteVue.dp(contexte, largeurDp),
                ecran - GraphiteVue.dp(contexte, 16));

        PopupWindow fenetre = new PopupWindow(carte, largeur,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        fenetre.setBackgroundDrawable(new android.graphics.drawable
                .ColorDrawable(android.graphics.Color.TRANSPARENT));
        fenetre.setOutsideTouchable(true);
        fenetre.setElevation(GraphiteVue.dp(contexte, 12));

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
                ? cote[1] - GraphiteVue.dp(contexte, 8)
                : hauteurFenetre - (cote[1] + ancre.getHeight())
                        - GraphiteVue.dp(contexte, 8);
        if (hauteur > place && place > GraphiteVue.dp(contexte, 120)) {
            // la carte tient dans la place qui reste, et elle seule —
            // le contenu défilant s'étend à la hauteur posée
            fenetre.setHeight(place);
            contenu.setLayoutParams(new android.widget.FrameLayout
                    .LayoutParams(android.view.ViewGroup.LayoutParams
                    .MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (auDessus) {
            fenetre.showAsDropDown(ancre, 0,
                    -ancre.getHeight() - hauteur
                            - GraphiteVue.dp(contexte, 4));
        } else {
            fenetre.showAsDropDown(ancre, 0,
                    GraphiteVue.dp(contexte, 4));
        }
        return fenetre;
    }

    /**
     * Une ligne de menu : le texte au corps, les touches respirées,
     * le ripple discret du thème — la matière des menus ancrés.
     *
     * @param contexte le contexte
     * @param palette  la palette
     * @param texte    le libellé de l'entrée
     * @param action   le geste (l'atelier décide)
     * @return la vue prête à empiler
     */
    public static View ligneMenu(android.content.Context contexte,
                                 Graphite.Palette palette,
                                 CharSequence texte,
                                 Runnable action) {
        android.widget.TextView vue = new android.widget.TextView(
                contexte);
        GraphiteVue.typographie(vue, Graphite.CORPS);
        vue.setTextColor(palette.texte);
        vue.setText(texte);
        vue.setSingleLine(true);
        vue.setEllipsize(android.text.TextUtils.TruncateAt.END);
        int vertical = GraphiteVue.dp(contexte, 11);
        vue.setPadding(GraphiteVue.dp(contexte, 14), vertical,
                GraphiteVue.dp(contexte, 14), vertical);
        vue.setBackground(fondAppuye(palette));
        vue.setOnClickListener(vue2 -> action.run());
        return vue;
    }

    /**
     * Le fond d'une ligne qui marque le toucher (appuyé : la surface
     * haute) sans AndroidX — un StateListDrawable maison.
     */
    public static android.graphics.drawable.Drawable fondAppuye(
            Graphite.Palette palette) {
        android.graphics.drawable.ColorDrawable normal =
                new android.graphics.drawable.ColorDrawable(
                        android.graphics.Color.TRANSPARENT);
        android.graphics.drawable.ColorDrawable appuye =
                new android.graphics.drawable.ColorDrawable(
                        palette.surfaceHaute);
        android.graphics.drawable.StateListDrawable etats =
                new android.graphics.drawable.StateListDrawable();
        etats.addState(new int[]{android.R.attr.state_pressed},
                appuye);
        etats.addState(new int[]{}, normal);
        return etats;
    }
}
