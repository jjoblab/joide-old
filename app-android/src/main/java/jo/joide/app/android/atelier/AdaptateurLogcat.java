package jo.joide.app.android.atelier;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import jo.joide.app.android.atelier.EtatLogcat.Ligne;
import jo.joide.app.android.databinding.ItemLigneLogcatBinding;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * La liste de la section Logcat (ADR-085, lot A5, décision 11) :
 * {@code RecyclerView + ListAdapter/DiffUtil}, view-holder = binding
 * d'item ({@code item_ligne_logcat} du lot L3 conservé) — la mise à
 * jour passe par {@code submitList} : plus de {@code removeAllViews()}
 * suivi d'une ré-inflation de TOUTES les lignes à chaque lot déposé
 * par le relais (constat I4 — le flux notifie en continu) ; le
 * DiffUtil ne gonfle que les lignes NOUVELLES, le plafond 400 est
 * conservé.
 *
 * <p>« D/Tag message » colorée par NIVEAU au bind (D réussite, W
 * alerte, E erreur, I texte — décision 8 : le niveau décide, jamais
 * une littérale).</p>
 */
public final class AdaptateurLogcat
        extends ListAdapter<Ligne, AdaptateurLogcat.Case> {

    /** Le DIFF : identité et contenu par valeur (record). */
    private static final DiffUtil.ItemCallback<Ligne> DIFF =
            new DiffUtil.ItemCallback<Ligne>() {
                @Override
                public boolean areItemsTheSame(Ligne ancienne,
                                               Ligne nouvelle) {
                    return ancienne.equals(nouvelle);
                }

                @Override
                public boolean areContentsTheSame(Ligne ancienne,
                                                  Ligne nouvelle) {
                    return ancienne.equals(nouvelle);
                }
            };

    public AdaptateurLogcat() {
        super(DIFF);
    }

    @Override
    public Case onCreateViewHolder(ViewGroup parent, int type) {
        return new Case(ItemLigneLogcatBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(Case possesseur, int position) {
        possesseur.lier(getItem(position));
    }

    /** Une ligne de logcat, gonflée depuis item_ligne_logcat. */
    final class Case extends RecyclerView.ViewHolder {

        private final ItemLigneLogcatBinding binding;

        Case(ItemLigneLogcatBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void lier(Ligne ligne) {
            Context contexte = binding.getRoot().getContext();
            Graphite.Palette palette = GraphiteVue.palette(contexte);
            int couleur = switch (ligne.niveau()) {
                case D -> palette.reussite;
                case W -> palette.alerte;
                case E -> palette.erreur;
                default -> palette.texte;
            };
            binding.ligneLogcat.setText(ligne.niveau() + "/"
                    + ligne.tag() + "  " + ligne.message());
            binding.ligneLogcat.setTextColor(couleur);
        }
    }
}
