package jo.joide.app.android.ui.atelier;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import jo.joide.app.android.R;
import jo.joide.app.android.atelier.EtatJournal;
import jo.joide.app.android.databinding.ItemLigneJournalBinding;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * Atelier v2, lot L3 (ADR-083) — la liste de la section Journal
 * (§3.6) : lignes gonflées depuis {@code item_ligne_journal} —
 * HH:MM:SS + source (accent ; MCP en alerte, erreur en rouge — la
 * couleur se pose par code depuis les jetons) + message. La vue est
 * MINCE : elle lit l'état pur {@link EtatJournal}.
 */
public final class ListeJournal extends ScrollView {

    /** La colonne des lignes. */
    private final LinearLayout rangee;

    public ListeJournal(Context contexte) {
        this(contexte, null);
    }

    public ListeJournal(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        rangee = new LinearLayout(contexte);
        rangee.setOrientation(LinearLayout.VERTICAL);
        addView(rangee, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
    }

    /** Redessine depuis l'état pur (filtre appliqué). */
    public void maj(EtatJournal etat) {
        rangee.removeAllViews();
        Graphite.Palette palette = GraphiteVue.palette(getContext());
        LayoutInflater gonfleur = LayoutInflater.from(getContext());
        for (EtatJournal.Evenement evenement : etat.visibles()) {
            ItemLigneJournalBinding item =
                    ItemLigneJournalBinding.inflate(gonfleur, rangee,
                            false);
            item.heureJournal.setText(evenement.heure());
            item.sourceJournal.setText(
                    String.valueOf(evenement.source()));
            int couleurSource = switch (evenement.niveau()) {
                case ERREUR -> palette.erreur;
                case AVERTISSEMENT ->
                        evenement.source() == EtatJournal.Source.MCP
                                ? palette.alerte : palette.accent;
                default -> palette.accent;
            };
            item.sourceJournal.setTextColor(couleurSource);
            item.messageJournal.setText(evenement.message());
            rangee.addView(item.getRoot());
        }
    }
}
