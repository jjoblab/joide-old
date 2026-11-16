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

    /**
     * Un dialogue de confirmation à message libre (lot J) : le message
     * porte un détail nommé — le compte de fichiers modifiés de la
     * session — jamais un texte générique qui cache.
     */
    public static void confirmation(Context contexte,
                                    Graphite.Palette palette,
                                    CharSequence titre,
                                    CharSequence message,
                                    int idAnnuler, int idValider,
                                    DialogInterface.OnClickListener valideur) {
        android.app.AlertDialog.Builder constructeur =
                new android.app.AlertDialog.Builder(contexte);
        constructeur.setTitle(titre)
                .setMessage(message)
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

    /**
     * Un dialogue d'information à message libre (lot H) : le message
     * porte un détail nommé — version de l'application, cause d'un
     * échec d'import — jamais un texte générique qui cache.
     */
    public static void information(Context contexte,
                                   Graphite.Palette palette,
                                   CharSequence titre, CharSequence message,
                                   int idOk) {
        android.app.AlertDialog.Builder constructeur =
                new android.app.AlertDialog.Builder(contexte);
        constructeur.setTitle(titre)
                .setMessage(message)
                .setPositiveButton(idOk, null);
        habiller(constructeur.create(), palette);
    }

    /**
     * Un menu (lot H) : titre libre (le nom du projet), éléments
     * nommés, choix unique — les actions ⋮ des cartes et de la barre
     * d'application.
     */
    public static void menu(Context contexte,
                            Graphite.Palette palette,
                            CharSequence titre, CharSequence[] elements,
                            DialogInterface.OnClickListener auditeur) {
        android.app.AlertDialog.Builder constructeur =
                new android.app.AlertDialog.Builder(contexte);
        constructeur.setTitle(titre).setItems(elements, auditeur);
        habiller(constructeur.create(), palette);
    }

    /**
     * Une saisie (lot H) : titre libre (le nom du projet), un corps
     * de vue (le champ du kit), annuler / valider.
     */
    public static void saisie(Context contexte,
                              Graphite.Palette palette,
                              CharSequence titre, android.view.View corps,
                              int idAnnuler, int idValider,
                              DialogInterface.OnClickListener valideur) {
        android.app.AlertDialog.Builder constructeur =
                new android.app.AlertDialog.Builder(contexte);
        constructeur.setTitle(titre).setView(corps)
                .setNegativeButton(idAnnuler, null)
                .setPositiveButton(idValider, valideur);
        habiller(constructeur.create(), palette);
    }

    /**
     * Un écran texte en dialogue (lot M, ADR-É8 « licences et NOTICE,
     * écran texte ») : titre libre, vue défilante du texte, une seule
     * action fermante — le texte long reste lisible, jamais coupé.
     */
    public static void saisieTexte(Context contexte,
                                   Graphite.Palette palette,
                                   CharSequence titre,
                                   android.view.View corps, int idFermer,
                                   DialogInterface.OnClickListener valideur) {
        android.app.AlertDialog.Builder constructeur =
                new android.app.AlertDialog.Builder(contexte);
        constructeur.setTitle(titre).setView(corps)
                .setPositiveButton(idFermer, valideur);
        habiller(constructeur.create(), palette);
    }

    /**
     * Une confirmation destructive à deux actions distinctes (lot H,
     * ADR-É3) : « Supprimer "x" de la liste ? » — retirer de la
     * liste (les fichiers restent) et effacer du stockage (les
     * fichiers disparaissent) sont DEUX actions, deux libellés, deux
     * auditeurs ; l'annulation reste toujours disponible.
     */
    public static void destructive(Context contexte,
                                   Graphite.Palette palette,
                                   CharSequence titre, CharSequence message,
                                   int idAnnuler, int idRetirer,
                                   int idEffacer,
                                   DialogInterface.OnClickListener retirer,
                                   DialogInterface.OnClickListener effacer) {
        android.app.AlertDialog.Builder constructeur =
                new android.app.AlertDialog.Builder(contexte);
        constructeur.setTitle(titre).setMessage(message)
                .setNegativeButton(idAnnuler, null)
                .setNeutralButton(idRetirer, retirer)
                .setPositiveButton(idEffacer, effacer);
        habiller(constructeur.create(), palette);
    }

    /**
     * Le dialogue d'autorisation de l'agent (lot P, ADR-É11 point 3) :
     * un corps LIBRE (le diff déplié dans sa vue défilante),
     * Refuser / Appliquer — et TOUTE annulation (croix, tap
     * extérieur, Retour) appelle {@code annuleur} : fermer revient
     * à REFUSER, le défaut sûr.
     */
    public static void autorisation(Context contexte,
                                    Graphite.Palette palette,
                                    CharSequence titre,
                                    CharSequence sousTitre,
                                    android.view.View corps,
                                    int idRefuser, int idAppliquer,
                                    DialogInterface.OnClickListener valideur,
                                    DialogInterface.OnCancelListener annuleur) {
        android.app.AlertDialog.Builder constructeur =
                new android.app.AlertDialog.Builder(contexte);
        TextView sousTitreVue = new TextView(contexte);
        sousTitreVue.setText(sousTitre);
        android.widget.LinearLayout colonne =
                new android.widget.LinearLayout(contexte);
        colonne.setOrientation(android.widget.LinearLayout.VERTICAL);
        int marge = (int) (24 * contexte.getResources()
                .getDisplayMetrics().density);
        sousTitreVue.setPadding(marge, 0, marge, 0);
        colonne.addView(sousTitreVue, new android.widget.LinearLayout
                .LayoutParams(android.widget.LinearLayout.LayoutParams
                .MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        colonne.addView(corps, new android.widget.LinearLayout
                .LayoutParams(android.widget.LinearLayout.LayoutParams
                .MATCH_PARENT, 0, 1f));
        constructeur.setTitle(titre).setView(colonne)
                .setNegativeButton(idRefuser, valideur)
                .setPositiveButton(idAppliquer, valideur);
        android.app.AlertDialog dialogue = constructeur.create();
        dialogue.setOnCancelListener(annuleur);
        habiller(dialogue, palette);
        dialogue.show();
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
