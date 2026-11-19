package jo.joide.app.android.ui.atelier;

import android.content.Context;
import android.util.AttributeSet;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import jo.joide.app.android.atelier.AdaptateurProblemes;
import jo.joide.app.android.atelier.ElementProblemes;
import jo.joide.app.android.atelier.EtatProblemes;

/**
 * Atelier v2, lot L3 (ADR-083) — la liste de la section Problèmes
 * (§3.6) : lignes gonflées depuis {@code item_probleme}, groupées
 * par fichier (en-tête 12 semi-gras texte2 « Facture.java · 2 ») ;
 * tap → ouvre le fichier à la ligne et replie la feuille. La vue est
 * MINCE : elle lit l'état pur {@link EtatProblemes}.
 *
 * <p>Lot A5 (ADR-085, décision 11) : la liste est RECYCLÉE —
 * {@code RecyclerView + ListAdapter/DiffUtil} (l'adaptateur
 * {@link AdaptateurProblemes}, la composition des groupes est pure
 * dans {@link ElementProblemes}), la mise à jour passe par
 * {@code submitList} : plus de {@code removeAllViews()} suivi d'une
 * ré-inflation de toutes les lignes à chaque diagnostic LSP (constat
 * I4) — seules les lignes CHANGÉES du fichier re-diagnostiqué se
 * re-lient.</p>
 */
public final class ListeProblemes extends RecyclerView {

    /** Un auditeur (l'ouverture d'un problème). */
    public interface Auditeur {
        /** Ouvre le fichier à la ligne (et replie la feuille). */
        void surOuvrir(String chemin, int ligne);
    }

    /** L'adaptateur recyclé des groupes et lignes. */
    private final AdaptateurProblemes adaptateur;

    public ListeProblemes(Context contexte) {
        this(contexte, null);
    }

    public ListeProblemes(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        adaptateur = new AdaptateurProblemes((chemin, ligne) -> {
            if (auditeur != null) {
                auditeur.surOuvrir(chemin, ligne);
            }
        });
        setLayoutManager(new LinearLayoutManager(contexte));
        setAdapter(adaptateur);
    }

    private Auditeur auditeur;

    /** Pose l'auditeur. */
    public void setAuditeur(Auditeur ecouteur) {
        auditeur = ecouteur;
    }

    /** Redessine depuis l'état pur (groupes par fichier, §3.6). */
    public void maj(EtatProblemes etat) {
        adaptateur.submitList(ElementProblemes.composer(etat));
    }
}
