package jo.joide.app.android.ui.atelier;

import android.content.Context;
import android.util.AttributeSet;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import jo.joide.app.android.atelier.AdaptateurJournal;
import jo.joide.app.android.atelier.EtatJournal;

/**
 * Atelier v2, lot L3 (ADR-083) — la liste de la section Journal
 * (§3.6) : lignes gonflées depuis {@code item_ligne_journal} —
 * HH:MM:SS + source (accent ; MCP en alerte, erreur en rouge — la
 * couleur se pose par code depuis les jetons) + message. La vue est
 * MINCE : elle lit l'état pur {@link EtatJournal}.
 *
 * <p>Lot A5 (ADR-085, décision 11) : la liste est RECYCLÉE —
 * {@code RecyclerView + ListAdapter/DiffUtil} (l'adaptateur
 * {@link AdaptateurJournal}), la mise à jour passe par
 * {@code submitList} : plus de {@code removeAllViews()} suivi d'une
 * ré-inflation de toutes les lignes à chaque événement de l'IDE
 * (constat I4) ; le plafond 400 événements est conservé, seules les
 * lignes NOUVELLES se gonflent.</p>
 */
public final class ListeJournal extends RecyclerView {

    /** L'adaptateur recyclé des lignes. */
    private final AdaptateurJournal adaptateur;

    public ListeJournal(Context contexte) {
        this(contexte, null);
    }

    public ListeJournal(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        adaptateur = new AdaptateurJournal();
        setLayoutManager(new LinearLayoutManager(contexte));
        setAdapter(adaptateur);
    }

    /** Redessine depuis l'état pur (filtre appliqué). */
    public void maj(EtatJournal etat) {
        adaptateur.submitList(etat.visibles());
    }
}
