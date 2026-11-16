package jo.joide.app.android.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import jo.joide.app.android.ui.choix.ChoixMouvement;

/**
 * Tiroir poussé du kit : panneau latéral (360 dp au plus, ADR-É2),
 * rideau par-dessus le contenu, fermeture sur le rideau. Le panneau
 * se calcule à 360 dp : le parent le rogne s'il est plus étroit —
 * jamais au-delà d'É2. Mouvement de composant : 180 ms
 * ({@link ChoixMouvement#composant()}). Aucune navigation d'écran
 * n'est câblée au lot G (ADR-048) ; le bord (gauche/droite) est
 * porté par la vue.
 *
 * <p>PIEGE-31, non négociable : le rideau fermé ne porte JAMAIS la
 * cliquabilité — {@code setOnClickListener} rend la vue cliquable
 * par effet de bord (contrat Android), et un rideau invisible de
 * MATCH_PARENT cliquable AVALE toutes les touches de l'écran qu'il
 * recouvre : l'hôte posé dessous devient sourd (l'écran atelier,
 * 2026-11-17). La cliquabilité s'allume à l'OUVERTURE et s'éteint à
 * la fermeture ET au layout fermé — jamais un rideau mort-vivant.
 */
public final class TiroirPousse extends FrameLayout {

    private final Graphite.Palette palette;
    private final View rideau;
    private final FrameLayout panneau;
    private boolean ouvert;
    private boolean bordDroit;
    private boolean enMouvement;

    public TiroirPousse(Context contexte) {
        this(contexte, null);
    }

    public TiroirPousse(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);

        rideau = new View(contexte);
        rideau.setBackgroundColor(palette.rideau);
        rideau.setAlpha(0f);
        rideau.setOnClickListener(vue -> fermer());
        // PIEGE-31 : setOnClickListener vient de rendre le rideau
        // cliquable par effet de bord — fermé à la construction, il ne
        // doit JAMAIS le rester : un rideau invisible cliquable avale
        // toutes les touches de l'écran recouvert (l'atelier devient
        // sourd, seul le FAB posé après répond).
        rideau.setClickable(false);
        addView(rideau, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        panneau = new FrameLayout(contexte);
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(palette.surface);
        fond.setStroke(GraphiteVue.dp(contexte, 1), palette.bordFin);
        panneau.setBackground(fond);
        panneau.setElevation(GraphiteVue.dp(contexte, 8));
        addView(panneau, new LayoutParams(
                GraphiteVue.dp(contexte, 360), LayoutParams.MATCH_PARENT,
                bordDroit
                        ? android.view.Gravity.END
                        : android.view.Gravity.START));
    }

    /** Le côté du panneau : droite (vrai) ou gauche (faux, défaut). */
    public void setBordDroit(boolean droite) {
        bordDroit = droite;
        FrameLayout.LayoutParams placements =
                (FrameLayout.LayoutParams) panneau.getLayoutParams();
        placements.gravity = droite
                ? android.view.Gravity.END
                : android.view.Gravity.START;
        panneau.setLayoutParams(placements);
        if (!ouvert) {
            panneau.setTranslationX(fermeeX());
        }
    }

    /** Le contenu du panneau (remplace le précédent). */
    public void setPanneau(View vue) {
        panneau.removeAllViews();
        panneau.addView(vue, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    /** Le tiroir est-il ouvert ? */
    public boolean ouvert() {
        return ouvert;
    }

    /** Ouvre le tiroir (sans effet s'il l'est déjà). */
    public void ouvrir() {
        if (ouvert || enMouvement) {
            return;
        }
        ouvert = true;
        enMouvement = true;
        rideau.setClickable(true);
        int duree = ChoixMouvement.composant();
        DecelerateInterpolator ralenti =
                new DecelerateInterpolator(1.2f);
        panneau.animate().translationX(0f).setDuration(duree)
                .setInterpolator(ralenti)
                .withEndAction(() -> enMouvement = false);
        rideau.animate().alpha(1f).setDuration(duree)
                .setInterpolator(ralenti);
    }

    /** Ferme le tiroir (sans effet s'il est déjà fermé). */
    public void fermer() {
        if (!ouvert || enMouvement) {
            return;
        }
        ouvert = false;
        enMouvement = true;
        // PIEGE-31 : le rideau rendu muet DÈS le début de la fermeture
        // — jamais un voile qui descend en avalant les gestes.
        rideau.setClickable(false);
        int duree = ChoixMouvement.composant();
        DecelerateInterpolator ralenti =
                new DecelerateInterpolator(1.2f);
        panneau.animate().translationX(fermeeX()).setDuration(duree)
                .setInterpolator(ralenti)
                .withEndAction(() -> enMouvement = false);
        rideau.animate().alpha(0f).setDuration(duree)
                .setInterpolator(ralenti)
                .withEndAction(() -> {
                    if (!ouvert) {
                        rideau.setAlpha(0f);
                    }
                });
    }

    @Override
    protected void onLayout(boolean change, int g, int h,
                            int d, int b) {
        super.onLayout(change, g, h, d, b);
        if (!ouvert && !enMouvement) {
            panneau.setTranslationX(fermeeX());
            rideau.setAlpha(0f);
            // PIEGE-31 : la défense au layout — le rideau fermé se
            // déclare muet même si un appelant l'a rendu cliquable.
            rideau.setClickable(false);
        }
    }

    private float fermeeX() {
        float largeur = panneau.getWidth();
        return bordDroit ? largeur : -largeur;
    }
}
