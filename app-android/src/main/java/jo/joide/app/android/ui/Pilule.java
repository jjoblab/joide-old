package jo.joide.app.android.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Pilule d'état du kit (lot J) : un point coloré et un libellé court —
 * l'état de l'index de la barre supérieure, l'état du build de la
 * poignée console. La couleur vient du rôle posé (accent, réussite,
 * alerte, erreur, neutre) ; la vue ne décide rien.
 */
public final class Pilule extends LinearLayout {

    /** Le rôle coloré du point. */
    public enum Role {
        /** Neutre : texte secondaire (inconnu, en attente). */
        NEUTRE,
        /** Accent : en cours. */
        ACCENT,
        /** Réussite. */
        REUSSITE,
        /** Alerte. */
        ALERTE,
        /** Erreur. */
        ERREUR
    }

    private final Graphite.Palette palette;
    private final android.view.View point;
    private final TextView libelle;

    public Pilule(Context contexte) {
        this(contexte, null);
    }

    public Pilule(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pad = GraphiteVue.dp(contexte, 8);
        setPadding(pad, GraphiteVue.dp(contexte, 3), pad,
                GraphiteVue.dp(contexte, 3));

        GradientDrawable fond = new GradientDrawable();
        fond.setColor(palette.surfaceHaute);
        fond.setCornerRadius(Graphite.RAYON_PUCE);
        setBackground(fond);

        point = new android.view.View(contexte);
        GradientDrawable rond = new GradientDrawable();
        rond.setShape(GradientDrawable.OVAL);
        rond.setColor(palette.texteSecondaire);
        point.setBackground(rond);
        addView(point, new LayoutParams(GraphiteVue.dp(contexte, 6),
                GraphiteVue.dp(contexte, 6)));

        libelle = new TextView(contexte);
        GraphiteVue.typographie(libelle, Graphite.LEGENDE);
        libelle.setPadding(GraphiteVue.dp(contexte, 6), 0, 0, 0);
        addView(libelle, new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        maj();
    }

    /** Pose le libellé et le rôle du point. */
    public void setEtat(CharSequence texte, Role role) {
        libelle.setText(texte);
        this.role = role == null ? Role.NEUTRE : role;
        maj();
    }

    private Role role = Role.NEUTRE;

    private void maj() {
        int couleur = switch (role) {
            case ACCENT -> palette.accent;
            case REUSSITE -> palette.reussite;
            case ALERTE -> palette.alerte;
            case ERREUR -> palette.erreur;
            case NEUTRE -> palette.texteSecondaire;
        };
        android.graphics.drawable.GradientDrawable rond =
                (android.graphics.drawable.GradientDrawable)
                        point.getBackground();
        rond.setColor(couleur);
        libelle.setTextColor(role == Role.NEUTRE
                ? palette.texteSecondaire : couleur);
    }
}
