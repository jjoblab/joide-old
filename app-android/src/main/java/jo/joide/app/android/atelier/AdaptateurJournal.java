package jo.joide.app.android.atelier;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import jo.joide.app.android.atelier.EtatJournal.Evenement;
import jo.joide.app.android.databinding.ItemLigneJournalBinding;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * La liste de la section Journal (ADR-085, lot A5, décision 11) :
 * {@code RecyclerView + ListAdapter/DiffUtil}, view-holder = binding
 * d'item ({@code item_ligne_journal} du lot L3 conservé) — la mise à
 * jour passe par {@code submitList} : plus de {@code removeAllViews()}
 * suivi d'une ré-inflation de TOUTES les lignes à chaque événement de
 * l'IDE (constat I4) ; le plafond 400 est conservé.
 *
 * <p>HH:MM:SS + source (accent ; MCP en alerte, erreur en rouge — la
 * couleur se pose au bind depuis les jetons, décision 8) +
 * message.</p>
 */
public final class AdaptateurJournal
        extends ListAdapter<Evenement, AdaptateurJournal.Case> {

    /** Le DIFF : identité et contenu par valeur (record). */
    private static final DiffUtil.ItemCallback<Evenement> DIFF =
            new DiffUtil.ItemCallback<Evenement>() {
                @Override
                public boolean areItemsTheSame(Evenement ancien,
                                               Evenement nouveau) {
                    return ancien.equals(nouveau);
                }

                @Override
                public boolean areContentsTheSame(Evenement ancien,
                                                  Evenement nouveau) {
                    return ancien.equals(nouveau);
                }
            };

    public AdaptateurJournal() {
        super(DIFF);
    }

    @Override
    public Case onCreateViewHolder(ViewGroup parent, int type) {
        return new Case(ItemLigneJournalBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(Case possesseur, int position) {
        possesseur.lier(getItem(position));
    }

    /** Une ligne du journal, gonflée depuis item_ligne_journal. */
    final class Case extends RecyclerView.ViewHolder {

        private final ItemLigneJournalBinding binding;

        Case(ItemLigneJournalBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void lier(Evenement evenement) {
            Context contexte = binding.getRoot().getContext();
            Graphite.Palette palette = GraphiteVue.palette(contexte);
            binding.heureJournal.setText(evenement.heure());
            binding.sourceJournal.setText(
                    String.valueOf(evenement.source()));
            int couleurSource = switch (evenement.niveau()) {
                case ERREUR -> palette.erreur;
                case AVERTISSEMENT ->
                        evenement.source() == EtatJournal.Source.MCP
                                ? palette.alerte : palette.accent;
                default -> palette.accent;
            };
            binding.sourceJournal.setTextColor(couleurSource);
            binding.messageJournal.setText(evenement.message());
        }
    }
}
