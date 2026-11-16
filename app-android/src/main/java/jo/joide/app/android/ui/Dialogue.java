package jo.joide.app.android.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.TextView;

/**
 * Dialogues du kit : l'AlertDialog du CADRE habillé Graphite —
 * fond surface, rayon 16 dp, bord fin, titre sous-titre demi-gras,
 * message au corps, boutons accent sans majuscules. Zéro AndroidX
 * (ADR-É2) : le dialogue est {@link android.app.AlertDialog} ; la
 * classe ne décide rien — les couleurs viennent de la palette.
 */
public final class Dialogue {

    private Dialogue() {
    }

    /**
     * Un dialogue de confirmation : titre, message, une action
     * secondaire (annuler) et une action primaire (valider) ;
     * {@code valideur} peut être nul (fermeture simple).
     */
    public static void confirmation(Context contexte,
                                    Graphite.Palette palette,
                                    int idTitre, int idMessage,
                                    int idAnnuler, int idValider,
                                    DialogInterface.OnClickListener valideur) {
        android.app.AlertDialog.Builder constructeur =
                new android.app.AlertDialog.Builder(contexte);
        constructeur.setTitle(idTitre)
                .setMessage(idMessage)
                .setNegativeButton(idAnnuler, null)
                .setPositiveButton(idValider, valideur);
        habiller(constructeur.create(), palette);
    }

    /** Un dialogue d'information : titre, message, une action fermante. */
    public static void information(Context contexte,
                                   Graphite.Palette palette,
                                   int idTitre, int idMessage, int idOk) {
        android.app.AlertDialog.Builder constructeur =
                new android.app.AlertDialog.Builder(contexte);
        constructeur.setTitle(idTitre)
                .setMessage(idMessage)
                .setPositiveButton(idOk, null);
        habiller(constructeur.create(), palette);
    }

    private static void habiller(android.app.AlertDialog dialogue,
                                 Graphite.Palette palette) {
        Context contexte = dialogue.getContext();
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(palette.surface);
        fond.setStroke(GraphiteVue.dp(contexte, 1), palette.bordFin);
        fond.setCornerRadius(GraphiteVue.dp(contexte,
                Graphite.RAYON_CARTE));
        dialogue.getWindow().setBackgroundDrawable(fond);
        // Les boutons et les textes n'existent qu'après show() :
        // l'auditeur de show les habille — jamais avant.
        dialogue.setOnShowListener(auditeur -> {
            styler(dialogue.findViewById(android.R.id.title),
                    Graphite.SOUS_TITRE);
            styler(dialogue.findViewById(android.R.id.message),
                    Graphite.CORPS);
            habillerBouton(dialogue.getButton(
                    android.app.AlertDialog.BUTTON_POSITIVE), palette);
            habillerBouton(dialogue.getButton(
                    android.app.AlertDialog.BUTTON_NEGATIVE), palette);
        });
        dialogue.show();
    }

    private static void styler(TextView vue, Graphite.StyleTexte style) {
        if (vue == null) {
            return;
        }
        GraphiteVue.typographie(vue, style);
        vue.setPadding(GraphiteVue.dp(vue.getContext(), 24),
                GraphiteVue.dp(vue.getContext(), 20),
                GraphiteVue.dp(vue.getContext(), 24), 0);
    }

    private static void habillerBouton(Button bouton, Graphite.Palette palette) {
        if (bouton == null) {
            return;
        }
        bouton.setAllCaps(false);
        bouton.setTypeface(GraphiteVue.police(bouton.getContext(), 600,
                false));
        bouton.setTextColor(palette.accent);
    }
}
