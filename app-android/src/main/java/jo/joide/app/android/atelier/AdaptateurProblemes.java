package jo.joide.app.android.atelier;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import jo.joide.app.android.R;
import jo.joide.app.android.databinding.ItemEnteteProblemesBinding;
import jo.joide.app.android.databinding.ItemProblemeBinding;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * La liste de la section Problèmes (ADR-085, lot A5, décision 11) :
 * {@code RecyclerView + ListAdapter/DiffUtil}, view-holders = bindings
 * d'items ({@code item_probleme} du lot L3 conservé, en-têtes de
 * groupe en {@code item_entete_problemes}) — la mise à jour passe par
 * {@code submitList} : plus de {@code removeAllViews()} suivi d'une
 * ré-inflation de TOUTES les lignes à chaque diagnostic LSP (constat
 * I4) ; le DiffUtil ne re-lient que les lignes CHANGÉES du fichier
 * re-diagnostiqué, les autres groupes ne bougent pas.
 *
 * <p>Lignes groupées par fichier (en-tête 12 semi-gras texte2
 * « Facture.java · 2 », séparateur bord-fin entre groupes) ; tap →
 * ouvre le fichier à la ligne et replie la feuille. Le glyphe ✕/▲ se
 * teinte au bind depuis les jetons (décision 8 : l'erreur décide).</p>
 */
public final class AdaptateurProblemes
        extends ListAdapter<ElementProblemes, AdaptateurProblemes.Case> {

    /** Ouvre le fichier du problème à la ligne (et replie la feuille). */
    public interface Auditeur {
        void surOuvrir(String chemin, int ligne);
    }

    private static final int TYPE_ENTETE = 0;
    private static final int TYPE_LIGNE = 1;

    /**
     * Le DIFF : en-tête par fichier, ligne par diagnostic (record) —
     * l'identité du groupement d'hier.
     */
    private static final DiffUtil.ItemCallback<ElementProblemes> DIFF =
            new DiffUtil.ItemCallback<ElementProblemes>() {
                @Override
                public boolean areItemsTheSame(ElementProblemes ancien,
                                               ElementProblemes nouveau) {
                    if (ancien instanceof ElementProblemes.Entete a
                            && nouveau instanceof ElementProblemes.Entete b) {
                        return a.fichier().equals(b.fichier());
                    }
                    return ancien.equals(nouveau);
                }

                @Override
                public boolean areContentsTheSame(
                        ElementProblemes ancien,
                        ElementProblemes nouveau) {
                    return ancien.equals(nouveau);
                }
            };

    private final Auditeur auditeur;

    public AdaptateurProblemes(Auditeur auditeur) {
        super(DIFF);
        this.auditeur = auditeur;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position) instanceof ElementProblemes.Entete
                ? TYPE_ENTETE : TYPE_LIGNE;
    }

    @Override
    public Case onCreateViewHolder(ViewGroup parent, int type) {
        LayoutInflater gonfleur =
                LayoutInflater.from(parent.getContext());
        if (type == TYPE_ENTETE) {
            return new CaseEntete(ItemEnteteProblemesBinding.inflate(
                    gonfleur, parent, false));
        }
        return new CaseLigne(ItemProblemeBinding.inflate(
                gonfleur, parent, false));
    }

    @Override
    public void onBindViewHolder(Case possesseur, int position) {
        possesseur.lier(getItem(position));
    }

    /** Une case de la section : un en-tête de groupe ou une ligne. */
    abstract static class Case extends RecyclerView.ViewHolder {

        protected Case(View racine) {
            super(racine);
        }

        abstract void lier(ElementProblemes element);
    }

    /** L'en-tête d'un groupe (« Facture.java · 2 »). */
    private static final class CaseEntete extends Case {

        private final ItemEnteteProblemesBinding binding;

        CaseEntete(ItemEnteteProblemesBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @Override
        void lier(ElementProblemes element) {
            ElementProblemes.Entete entete =
                    (ElementProblemes.Entete) element;
            binding.enteteGroupe.setText(entete.nomAffiche() + " · "
                    + entete.nombre());
            // le séparateur au-dessus : masqué pour le premier groupe
            // (la maquette ne trace rien au-dessus du premier)
            binding.separateurGroupe.setVisibility(
                    entete.premier() ? View.GONE : View.VISIBLE);
        }
    }

    /** Une ligne de diagnostic (tap → ouvre le fichier à la ligne). */
    private final class CaseLigne extends Case {

        private final ItemProblemeBinding binding;

        CaseLigne(ItemProblemeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @Override
        void lier(ElementProblemes element) {
            EtatProblemes.Probleme probleme =
                    ((ElementProblemes.Ligne) element).probleme();
            Context contexte = binding.getRoot().getContext();
            Graphite.Palette palette = GraphiteVue.palette(contexte);
            binding.glypheProbleme.setText(
                    probleme.erreur() ? "✕" : "▲");
            binding.glypheProbleme.setTextColor(
                    probleme.erreur() ? palette.erreur : palette.alerte);
            binding.messageProbleme.setText(probleme.message());
            binding.ligneProbleme.setText(contexte.getString(
                    R.string.atelier_probleme_ligne,
                    probleme.ligne()));
            binding.getRoot().setOnClickListener(vue -> {
                if (auditeur != null) {
                    auditeur.surOuvrir(probleme.chemin(),
                            probleme.ligne());
                }
            });
        }
    }
}
