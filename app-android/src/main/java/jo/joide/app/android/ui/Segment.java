package jo.joide.app.android.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import jo.joide.app.android.ui.choix.ChoixSegment;

/**
 * Segment du kit (lot I) : contrôle à options exclusives —
 * « 28 | 30 » pour le minSdk de l'assistant (maquette
 * création-projet.html). Conteneur surface haute, rayon 12, padding
 * 4 ; cellules de poids 1, rayon 9, centrées ; la cellule active
 * suit {@link ChoixSegment}. La vue ne décide rien : elle rend
 * l'état qu'on lui pose.
 */
public final class Segment extends LinearLayout {

    /** Écoute le changement d'option par l'utilisateur. */
    public interface AuditeurSegment {
        /** Appelé après chaque choix utilisateur. */
        void surChoix(Segment segment, int index);
    }

    private final Graphite.Palette palette;
    private final List<String> options = new ArrayList<>();
    private int selection = -1;
    private AuditeurSegment auditeur;

    public Segment(Context contexte) {
        this(contexte, null);
    }

    public Segment(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);
        setOrientation(HORIZONTAL);
        int interne = GraphiteVue.dp(contexte, 4);
        setPadding(interne, interne, interne, interne);
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(palette.surfaceHaute);
        fond.setStroke(GraphiteVue.dp(contexte, 1), palette.bordFin);
        fond.setCornerRadius(GraphiteVue.dp(contexte,
                Graphite.RAYON_COMPOSANT));
        setBackground(fond);
    }

    /**
     * Pose les options et la sélection (sans prévenir l'auditeur).
     *
     * @param libelles les libellés des cellules
     * @param selectionnee l'index sélectionné, ou -1
     */
    public void setOptions(List<String> libelles, int selectionnee) {
        options.clear();
        options.addAll(libelles);
        selection = selectionnee;
        removeAllViews();
        for (int i = 0; i < options.size(); i++) {
            addView(cellule(i), new LayoutParams(0,
                    LayoutParams.WRAP_CONTENT, 1f));
        }
        majCellules();
    }

    /** L'index sélectionné, ou -1. */
    public int selection() {
        return selection;
    }

    /** L'auditeur des choix utilisateur. */
    public void setAuditeur(AuditeurSegment ecouteur) {
        auditeur = ecouteur;
    }

    private TextView cellule(int index) {
        TextView cellule = new TextView(getContext());
        GraphiteVue.typographie(cellule, Graphite.NOTE);
        cellule.setGravity(Gravity.CENTER);
        int vertical = GraphiteVue.dp(getContext(), 9);
        cellule.setPadding(0, vertical, 0, vertical);
        cellule.setText(options.get(index));
        cellule.setOnClickListener(v -> {
            if (!isEnabled()) {
                return;
            }
            selection = index;
            majCellules();
            if (auditeur != null) {
                auditeur.surChoix(Segment.this, index);
            }
        });
        // le fond de la cellule se pose après l'ajout au parent
        cellule.setTag(index);
        return cellule;
    }

    private void majCellules() {
        for (int i = 0; i < getChildCount(); i++) {
            android.view.View enfant = getChildAt(i);
            if (!(enfant instanceof TextView)) {
                continue;
            }
            int index = i;
            Object marque = enfant.getTag();
            if (marque instanceof Integer) {
                index = (Integer) marque;
            }
            poserApparence((TextView) enfant, index == selection);
        }
    }

    private void poserApparence(TextView cellule, boolean selectionnee) {
        ChoixSegment.Apparence apparence = ChoixSegment.apparence(
                palette, selectionnee);
        GradientDrawable fond = new GradientDrawable();
        if (apparence.fond() != 0) {
            fond.setColor(apparence.fond());
        } else {
            fond.setColor(0x00000000);
        }
        fond.setCornerRadius(GraphiteVue.dp(getContext(), 9));
        cellule.setBackground(fond);
        cellule.setTextColor(apparence.texte());
        cellule.setTypeface(GraphiteVue.police(getContext(),
                apparence.gras() ? 600 : 400, false));
    }

    @Override
    public void setEnabled(boolean actif) {
        super.setEnabled(actif);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setEnabled(actif);
        }
        setAlpha(actif ? 1f : 0.4f);
    }
}
