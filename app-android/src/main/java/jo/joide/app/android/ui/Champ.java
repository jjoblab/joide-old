package jo.joide.app.android.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import jo.joide.app.android.ui.choix.ChoixChamp;

/**
 * Champ du kit : étiquette au-dessus (légende demi-gras — la
 * maquette création-projet.html porte une graisse 600, écart pixel
 * consigné ADR-048), boîte dessinée (surface, rayon 12 dp, bord
 * selon {@link ChoixChamp}) contenant un EditText du cadre, corps
 * 15/22. Le champ peut être monospace (noms de paquet : code
 * 13,5/22, la maquette). Les couleurs viennent de ChoixChamp ;
 * la vue ne décide rien.
 */
public final class Champ extends LinearLayout {

    private final Graphite.Palette palette;
    private final TextView etiquette;
    private final EditText edition;
    private boolean monospace;

    public Champ(Context contexte) {
        this(contexte, null);
    }

    public Champ(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        palette = GraphiteVue.palette(contexte);
        setOrientation(VERTICAL);

        etiquette = new TextView(contexte);
        GraphiteVue.typographie(etiquette, Graphite.LEGENDE);
        etiquette.setTypeface(GraphiteVue.police(contexte, 600, false));
        LinearLayout.LayoutParams margeEtiquette =
                new LayoutParams(LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT);
        margeEtiquette.bottomMargin = GraphiteVue.dp(contexte, 6);
        addView(etiquette, margeEtiquette);

        edition = new EditText(contexte);
        GraphiteVue.typographie(edition, Graphite.CORPS);
        int retrait = GraphiteVue.dp(contexte, 14);
        edition.setPadding(retrait, 0, retrait, 0);
        edition.setBackground(null);
        edition.setMinLines(1);
        edition.setMaxLines(1);
        addView(edition, new LayoutParams(LayoutParams.MATCH_PARENT,
                GraphiteVue.dp(contexte, 48)));

        edition.setOnFocusChangeListener((vue, focus) -> maj());
        maj();
    }

    /** L'étiquette du champ, depuis les ressources. */
    public void setEtiquette(int idRes) {
        etiquette.setText(idRes);
    }

    /**
     * Champ sans étiquette (recherche de l'accueil, saisie de
     * renommage) : la ligne de légende disparaît, la boîte reste.
     */
    public void sansEtiquette() {
        etiquette.setVisibility(GONE);
    }

    /** L'indice (hint) du champ, depuis les ressources. */
    public void setIndice(int idRes) {
        edition.setHint(idRes);
    }

    /** L'EditText du cadre — pour brancher l'écoute de saisie. */
    public Editable saisie() {
        return edition.getEditableText();
    }

    /** Le contenu saisi. */
    public CharSequence valeur() {
        return edition.getText();
    }

    /** L'EditText du cadre — pour préremplir ou lire la saisie. */
    public EditText edition() {
        return edition;
    }

    /** Basculer le champ en monospace (noms de paquet). */
    public void setMonospace(boolean mono) {
        monospace = mono;
        GraphiteVue.typographie(edition,
                mono ? Graphite.CODE : Graphite.CORPS);
        if (!mono) {
            edition.setTypeface(GraphiteVue.police(getContext(), 400,
                    false));
        }
        maj();
    }

    @Override
    public void setEnabled(boolean actif) {
        super.setEnabled(actif);
        maj();
    }

    private void maj() {
        ChoixChamp.Apparence apparence = ChoixChamp.apparence(palette,
                edition.hasFocus(), !isEnabled());
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(palette.surface);
        fond.setStroke(GraphiteVue.dp(getContext(),
                Math.round(apparence.epaisseurBordDp)),
                apparence.couleurBord);
        fond.setCornerRadius(GraphiteVue.dp(getContext(),
                Graphite.RAYON_COMPOSANT));
        edition.setBackground(fond);
        edition.setTextColor(apparence.couleurTexte);
        edition.setHintTextColor(palette.texteEstompe);
        etiquette.setTextColor(apparence.couleurEtiquette);
        edition.setEnabled(isEnabled());
    }
}
