package jo.joide.app.android.console;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import jo.joide.app.android.ConsoleBuild;
import jo.joide.app.android.R;
import jo.joide.app.android.console.EtatConsole.PhaseEtat;
import jo.joide.app.android.databinding.ItemPhaseBinding;

/**
 * La bande de PHASES de la console (ADR-085, lot A5, décision 11) :
 * une pilule par phase du pipeline ({@code item_phase}, Chip
 * {@code Widget.Jo.Puce}) — le tap FILTRE le transcript sur la phase.
 * {@code RecyclerView + ListAdapter/DiffUtil}, view-holder = binding
 * d'item : la mise à jour passe par {@code submitList}, plus de
 * {@code removeAllViews()} suivi d'une ré-inflation (constat I4 — la
 * vue ne re-lient que les phases qui changent, ≤ 3 lignes).
 *
 * <p>La durée se formule au bind (« 4,1 s » / « en cours… » / « — »),
 * l'identité du lot K : PAS d'état coché sur les phases.</p>
 */
final class AdaptateurPhases extends ListAdapter<PhaseEtat, AdaptateurPhases.Case> {

    /** Les actions de la bande (le tap filtre le transcript). */
    interface Auditeur {
        /** Le filtre par phase change (null = toutes les phases). */
        void surPhase(ConsoleBuild.Phase phase);
    }

    /** Le DIFF des phases : identité et contenu par valeur (record). */
    private static final DiffUtil.ItemCallback<PhaseEtat> DIFF =
            new DiffUtil.ItemCallback<PhaseEtat>() {
                @Override
                public boolean areItemsTheSame(PhaseEtat ancienne,
                                               PhaseEtat nouvelle) {
                    return ancienne.equals(nouvelle);
                }

                @Override
                public boolean areContentsTheSame(PhaseEtat ancienne,
                                                  PhaseEtat nouvelle) {
                    return ancienne.equals(nouvelle);
                }
            };

    private final Auditeur auditeur;

    AdaptateurPhases(Auditeur auditeur) {
        super(DIFF);
        this.auditeur = auditeur;
    }

    @Override
    public Case onCreateViewHolder(ViewGroup parent, int type) {
        return new Case(ItemPhaseBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(Case possesseur, int position) {
        possesseur.lier(getItem(position));
    }

    /** Une case : la pilule d'une phase, gonflée depuis item_phase. */
    final class Case extends RecyclerView.ViewHolder {

        private final ItemPhaseBinding binding;

        Case(ItemPhaseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void lier(PhaseEtat phase) {
            binding.phasePilule.setText(libelle(phase));
            binding.phasePilule.setOnClickListener(vue -> {
                if (auditeur != null) {
                    auditeur.surPhase(phase.phase());
                }
            });
        }

        private String libelle(PhaseEtat phase) {
            String duree = phase.terminee()
                    ? EtatConsole.secondes(phase.dureeMs())
                    : (phase.commencee()
                            ? binding.getRoot().getContext().getString(
                                    R.string.console_phase_encours)
                            : "—");
            return LibellesConsole.libellePhase(
                    binding.getRoot().getContext(), phase.phase())
                    + " · " + duree;
        }
    }
}
