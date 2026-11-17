package jo.joide.app.android.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.widget.TextView;

import jo.joide.app.android.ui.choix.ChoixPuce;

/**
 * Puce du kit : pilule (rayon 999 dp), note 13/18, padding 7/14.
 * La puce de catégorie est sélectionnable — la sélection
 * ({@link ChoixPuce}) teinte le fond accent 16 %, bord accent 45 %,
 * texte accent demi-gras. La puce simple est un libellé. Les
 * couleurs viennent de ChoixPuce ; la vue ne décide rien.
 */
public final class Puce extends TextView {

    /** Écoute le basculement d'une puce de catégorie par l'utilisateur. */
    public interface AuditeurPuce {
        /** Appelé après chaque bascule utilisateur. */
        void surBascule(Puce puce, boolean pointee);
    }

    private final Graphite.Palette palette;
    private boolean pointee;
    private boolean categorie;
    private AuditeurPuce auditeur;

    public Puce(Context contexte) {
        this(contexte, null);
    }

    public Puce(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);
        GraphiteVue.typographie(this, Graphite.NOTE);
        setPadding(GraphiteVue.dp(contexte, 14),
                GraphiteVue.dp(contexte, 7),
                GraphiteVue.dp(contexte, 14),
                GraphiteVue.dp(contexte, 7));
        setOnClickListener(vue -> {
            if (categorie && isEnabled()) {
                pointee = !pointee;
                maj();
                if (auditeur != null) {
                    auditeur.surBascule(this, pointee);
                }
            }
        });
        maj();
    }

    /** Fait de cette puce une catégorie sélectionnable. */
    public void setCategorie(boolean estCategorie) {
        categorie = estCategorie;
        setClickable(estCategorie);
        maj();
    }

    /** L'état de sélection (catégories). */
    public boolean pointee() {
        return pointee;
    }

    /** Positionne la sélection sans avertir l'auditeur. */
    public void setPointee(boolean selection) {
        pointee = selection;
        maj();
    }

    /** L'auditeur des bascules utilisateur. */
    public void setAuditeur(AuditeurPuce ecouteur) {
        auditeur = ecouteur;
    }

    @Override
    public void setEnabled(boolean actif) {
        super.setEnabled(actif);
        maj();
    }

    private void maj() {
        ChoixPuce.Apparence apparence = ChoixPuce.apparence(palette,
                pointee, !isEnabled());
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(apparence.fond);
        fond.setStroke(GraphiteVue.dp(getContext(), 1), apparence.bord);
        fond.setCornerRadius(GraphiteVue.dp(getContext(), 250));
        setBackground(fond);
        setTextColor(apparence.texte);
        setTypeface(GraphiteVue.police(getContext(),
                apparence.gras ? 600 : 400, false));
        setAlpha(apparence.opacite);
    }
}
