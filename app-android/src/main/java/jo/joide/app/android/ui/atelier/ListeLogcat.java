package jo.joide.app.android.ui.atelier;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import jo.joide.app.android.R;
import jo.joide.app.android.atelier.EtatLogcat;
import jo.joide.app.android.databinding.ItemLigneLogcatBinding;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * Atelier v2, lot L3 (ADR-083) — la liste de la section Logcat
 * (§3.6) : lignes gonflées depuis {@code item_ligne_logcat} —
 * « D/Tag  message » colorée par NIVEAU (D réussite, I défaut, W
 * alerte, E erreur). La vue est MINCE : elle lit l'état pur
 * {@link EtatLogcat}.
 */
public final class ListeLogcat extends ScrollView {

    /** La colonne des lignes. */
    private final LinearLayout rangee;

    public ListeLogcat(Context contexte) {
        this(contexte, null);
    }

    public ListeLogcat(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        rangee = new LinearLayout(contexte);
        rangee.setOrientation(LinearLayout.VERTICAL);
        addView(rangee, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
    }

    /** Redessine depuis l'état pur (filtre appliqué) ; scrolle en bas
     *  (le flux vit au bas). */
    public void maj(EtatLogcat etat) {
        rangee.removeAllViews();
        Graphite.Palette palette = GraphiteVue.palette(getContext());
        LayoutInflater gonfleur = LayoutInflater.from(getContext());
        for (EtatLogcat.Ligne ligne : etat.visibles()) {
            ItemLigneLogcatBinding item =
                    ItemLigneLogcatBinding.inflate(gonfleur, rangee,
                            false);
            int couleur = switch (ligne.niveau()) {
                case D -> palette.reussite;
                case W -> palette.alerte;
                case E -> palette.erreur;
                default -> palette.texte;
            };
            item.ligneLogcat.setText(ligne.niveau() + "/"
                    + ligne.tag() + "  " + ligne.message());
            item.ligneLogcat.setTextColor(couleur);
            rangee.addView(item.getRoot());
        }
        post(() -> fullScroll(ScrollView.FOCUS_DOWN));
    }
}
