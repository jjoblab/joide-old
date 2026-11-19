package jo.joide.app.android.ui.atelier;

import android.content.Context;
import android.util.AttributeSet;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import jo.joide.app.android.atelier.AdaptateurLogcat;
import jo.joide.app.android.atelier.EtatLogcat;

/**
 * Atelier v2, lot L3 (ADR-083) — la liste de la section Logcat
 * (§3.6) : lignes gonflées depuis {@code item_ligne_logcat} —
 * « D/Tag  message » colorée par NIVEAU (D réussite, I défaut, W
 * alerte, E erreur). La vue est MINCE : elle lit l'état pur
 * {@link EtatLogcat}.
 *
 * <p>Lot A5 (ADR-085, décision 11) : la liste est RECYCLÉE —
 * {@code RecyclerView + ListAdapter/DiffUtil} (l'adaptateur
 * {@link AdaptateurLogcat}), la mise à jour passe par
 * {@code submitList} : plus de {@code removeAllViews()} suivi d'une
 * ré-inflation de toutes les lignes à chaque lot déposé par le relais
 * (constat I4) ; le plafond 400 lignes est conservé, seules les
 * lignes NOUVELLES se gonflent.</p>
 */
public final class ListeLogcat extends RecyclerView {

    /** L'adaptateur recyclé des lignes. */
    private final AdaptateurLogcat adaptateur;

    public ListeLogcat(Context contexte) {
        this(contexte, null);
    }

    public ListeLogcat(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        adaptateur = new AdaptateurLogcat();
        setLayoutManager(new LinearLayoutManager(contexte));
        setAdapter(adaptateur);
    }

    /** Redessine depuis l'état pur (filtre appliqué) ; scrolle en bas
     *  (le flux vit au bas). */
    public void maj(EtatLogcat etat) {
        List<EtatLogcat.Ligne> visibles = etat.visibles();
        // le RAPPEL DE COMMIT porte le scroll : le diff court en tâche
        // de fond, un simple post() passerait AVANT le commit et
        // raterait la dernière ligne (reprise d'audit)
        adaptateur.submitList(visibles, () -> {
            int nombre = adaptateur.getItemCount();
            if (nombre > 0) {
                scrollToPosition(nombre - 1);
            }
        });
    }
}
