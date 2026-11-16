package jo.joide.app.android.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

/**
 * Nuancier du kit (lot M, maquette parametres.html) : pastilles
 * rondes 26 dp, la choisie porte un lisere du texte et une aura —
 * « un seul jeton réellement appliqué » (ADR-É8). La vue ne connaît
 * ni les couleurs ni le choix : tout est posé, rien n'est déduit.
 */
public final class Nuancier extends LinearLayout {

    /** Écoute les choix par l'utilisateur. */
    public interface AuditeurNuancier {
        /**
         * @param index la pastille choisie
         */
        void surChoix(int index);
    }

    private AuditeurNuancier auditeur;
    private int[] couleurs = new int[0];
    private int choisi = -1;

    public Nuancier(Context contexte) {
        this(contexte, null);
    }

    public Nuancier(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
    }

    /**
     * Pose les pastilles et la choisie (sans prévenir l'auditeur).
     *
     * @param couleurs   les couleurs des pastilles
     * @param choisie    l'index choisi, ou -1
     * @param tailleDp   le côté d'une pastille
     */
    public void poser(int[] couleurs, int choisie, int tailleDp) {
        removeAllViews();
        this.couleurs = couleurs.clone();
        choisi = choisie;
        for (int i = 0; i < couleurs.length; i++) {
            addView(pastille(couleurs[i], i == choisie, tailleDp),
                    new LayoutParams(
                            GraphiteVue.dp(getContext(), tailleDp),
                            GraphiteVue.dp(getContext(), tailleDp)));
            if (i + 1 < couleurs.length) {
                View espace = new View(getContext());
                addView(espace, new LayoutParams(
                        GraphiteVue.dp(getContext(), 10), 1));
            }
        }
    }

    /** L'index choisi, ou -1. */
    public int choisi() {
        return choisi;
    }

    /** L'auditeur des choix utilisateur. */
    public void setAuditeur(AuditeurNuancier ecouteur) {
        auditeur = ecouteur;
    }

    private View pastille(int couleur, boolean estChoisie, int tailleDp) {
        final int index = getChildCount() / 2;
        View pastille = new View(getContext());
        pastille.setClickable(true);
        GradientDrawable rond = new GradientDrawable();
        rond.setShape(GradientDrawable.OVAL);
        rond.setColor(couleur);
        int lisere = GraphiteVue.dp(getContext(), 2);
        if (estChoisie) {
            rond.setStroke(lisere,
                    GraphiteVue.palette(getContext()).texte);
        }
        pastille.setBackground(rond);
        pastille.setOnClickListener(v -> {
            choisir(index);
            if (auditeur != null) {
                auditeur.surChoix(index);
            }
        });
        return pastille;
    }

    private void choisir(int index) {
        choisi = index;
        for (int i = 0; i < getChildCount(); i++) {
            android.view.View enfant = getChildAt(i);
            if (!(enfant.isClickable())) {
                continue;
            }
            // les pastilles sont aux positions paires (les espaces
            // entre elles ne sont pas cliquables)
            int position = i / 2;
            GradientDrawable rond = new GradientDrawable();
            rond.setShape(GradientDrawable.OVAL);
            rond.setColor(couleurs[position]);
            if (position == choisi) {
                rond.setStroke(GraphiteVue.dp(getContext(), 2),
                        GraphiteVue.palette(getContext()).texte);
            }
            enfant.setBackground(rond);
        }
    }
}
