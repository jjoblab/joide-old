package jo.joide.app.android.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * La barre d'onglets multi-fichiers de l'atelier (lot J, ADR-É5) :
 * fichiers ouverts dans l'ordre d'ouverture, indicateur accent 2 dp
 * sur l'onglet actif, point de modification par onglet, fermeture par
 * croix. L'état est faible (ADR-051) : le texte vit sur le disque, la
 * barre ne montre que noms, points et croix — elle ne décide rien.
 */
public final class Onglets extends FrameLayout {

    /** Un onglet affichable : le chemin porteur et son nom. */
    public record Onglet(String chemin, String nom, boolean modifie) {
    }

    /** L'auditeur des actions de la barre. */
    public interface AuditeurOnglets {
        /** Un onglet est activé (le fichier doit s'ouvrir). */
        void surActivation(String chemin);

        /** La croix d'un onglet (le fichier doit se fermer). */
        void surFermeture(String chemin);
    }

    private final Graphite.Palette palette;
    private final LinearLayout barre;
    private AuditeurOnglets auditeur;

    public Onglets(Context contexte) {
        this(contexte, null);
    }

    public Onglets(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);
        HorizontalScrollView defile = new HorizontalScrollView(contexte);
        defile.setHorizontalScrollBarEnabled(false);
        barre = new LinearLayout(contexte);
        barre.setOrientation(LinearLayout.HORIZONTAL);
        defile.addView(barre, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        addView(defile, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
    }

    /** L'auditeur des actions. */
    public void setAuditeur(AuditeurOnglets ecouteur) {
        this.auditeur = ecouteur;
    }

    /**
     * Pose les onglets : reconstruit la barre (les onglets sont peu
     * nombreux, le plafond vit dans l'état — ADR-051).
     */
    public void setOnglets(List<Onglet> onglets, String actif) {
        barre.removeAllViews();
        for (Onglet onglet : onglets) {
            barre.addView(construire(onglet,
                    onglet.chemin().equals(actif)));
        }
        if (onglets.isEmpty()) {
            return;
        }
        // la barre suit l'onglet actif (jamais perdu hors champ)
        barre.requestLayout();
        post(() -> {
            for (int i = 0; i < barre.getChildCount(); i++) {
                View enfant = barre.getChildAt(i);
                if (enfant.getTag() instanceof String chemin
                        && chemin.equals(actif)) {
                    enfant.requestRectangleOnScreen(
                            new android.graphics.Rect(0, 0,
                                    enfant.getWidth(),
                                    enfant.getHeight()));
                }
            }
        });
    }

    private View construire(Onglet onglet, boolean actif) {
        Context contexte = getContext();
        LinearLayout vertical = new LinearLayout(contexte);
        vertical.setOrientation(LinearLayout.VERTICAL);
        vertical.setTag(onglet.chemin());
        vertical.setClickable(true);
        vertical.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surActivation(onglet.chemin());
            }
        });

        LinearLayout rangee = new LinearLayout(contexte);
        rangee.setOrientation(LinearLayout.HORIZONTAL);
        rangee.setGravity(Gravity.CENTER_VERTICAL);
        int marge = GraphiteVue.dp(contexte, 14);
        rangee.setPadding(marge, 0, GraphiteVue.dp(contexte, 6), 0);

        TextView nom = new TextView(contexte);
        GraphiteVue.typographie(nom, Graphite.NOTE);
        nom.setSingleLine(true);
        nom.setMaxWidth(GraphiteVue.dp(contexte, 170));
        nom.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        nom.setTextColor(actif ? palette.texte
                : palette.texteSecondaire);
        nom.setTypeface(GraphiteVue.police(contexte, actif ? 600 : 400,
                false));
        nom.setText(onglet.nom());
        rangee.addView(nom, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        if (onglet.modifie()) {
            android.view.View point = new android.view.View(contexte);
            android.graphics.drawable.GradientDrawable rond =
                    new android.graphics.drawable.GradientDrawable();
            rond.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            rond.setColor(palette.accent);
            point.setBackground(rond);
            LinearLayout.LayoutParams pointParams =
                    new LinearLayout.LayoutParams(
                            GraphiteVue.dp(contexte, 6),
                            GraphiteVue.dp(contexte, 6));
            pointParams.leftMargin = GraphiteVue.dp(contexte, 6);
            rangee.addView(point, pointParams);
        }

        View croix = new Icone(contexte);
        ((Icone) croix).setGlyphe(Icone.Glyphe.CROIX);
        ((Icone) croix).setCouleur(palette.texteEstompe);
        croix.setClickable(true);
        croix.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surFermeture(onglet.chemin());
            }
        });
        LinearLayout.LayoutParams croixParams =
                new LinearLayout.LayoutParams(
                        GraphiteVue.dp(contexte, 30),
                        GraphiteVue.dp(contexte, 30));
        rangee.addView(croix, croixParams);

        vertical.addView(rangee, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, 1f));

        android.view.View indicateur = new android.view.View(contexte);
        indicateur.setBackgroundColor(actif ? palette.accent
                : android.graphics.Color.TRANSPARENT);
        vertical.addView(indicateur,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        GraphiteVue.dp(contexte, 2)));

        return vertical;
    }
}
