package jo.joide.app.android.console;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import jo.joide.app.android.ConsoleBuild;
import jo.joide.app.android.R;
import jo.joide.app.android.databinding.ItemLigneConsoleBinding;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * Le TRANSCRIPT de la console (ADR-085, lot A5, décision 11) :
 * {@code RecyclerView + ListAdapter/DiffUtil}, view-holder = binding
 * d'item ({@code item_ligne_console}) — la grille deux colonnes du
 * lot K, chaque ligne reste du texte sélectionnable/copiable. La mise
 * à jour passe par {@code submitList} : plus de {@code removeAllViews()}
 * suivi d'une ré-inflation de TOUTES les lignes à chaque événement
 * (constat I4 — le flux du démon P6 notifie à chaque tâche) ; le
 * DiffUtil ne re-lient que les lignes CHANGÉES et n'inflige des
 * gonflages qu'aux lignes NOUVELLES, le plafond 400 est conservé.
 *
 * <p>Couleur par nature au bind (accent/fonction/texte2/erreur/alerte
 * — décision 8 : la nature de la ligne décide, jamais une littérale) ;
 * une ligne de problème localisée porte le fond erreur 10 % et
 * s'ouvre entière.</p>
 */
final class AdaptateurConsole extends
        ListAdapter<ConsoleBuild.Ligne, AdaptateurConsole.Case> {

    /** Ouvre la cible d'un problème (« fichier:ligne »). */
    interface Auditeur {
        void surOuvrir(String cible);
    }

    /** Le DIFF : identité et contenu par valeur (record, lot F). */
    private static final DiffUtil.ItemCallback<ConsoleBuild.Ligne> DIFF =
            new DiffUtil.ItemCallback<ConsoleBuild.Ligne>() {
                @Override
                public boolean areItemsTheSame(ConsoleBuild.Ligne ancienne,
                                               ConsoleBuild.Ligne nouvelle) {
                    return ancienne.equals(nouvelle);
                }

                @Override
                public boolean areContentsTheSame(
                        ConsoleBuild.Ligne ancienne,
                        ConsoleBuild.Ligne nouvelle) {
                    return ancienne.equals(nouvelle);
                }
            };

    private final Auditeur auditeur;

    AdaptateurConsole(Auditeur auditeur) {
        super(DIFF);
        this.auditeur = auditeur;
    }

    @Override
    public Case onCreateViewHolder(ViewGroup parent, int type) {
        return new Case(ItemLigneConsoleBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(Case possesseur, int position) {
        possesseur.lier(getItem(position));
    }

    /** Une ligne du transcript, gonflée depuis item_ligne_console. */
    final class Case extends RecyclerView.ViewHolder {

        private final ItemLigneConsoleBinding binding;
        private final int padProbleme;

        Case(ItemLigneConsoleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.padProbleme = binding.getRoot().getContext()
                    .getResources().getDimensionPixelSize(
                            R.dimen.jo_pad_ligne_probleme);
        }

        void lier(ConsoleBuild.Ligne ligne) {
            Context contexte = binding.getRoot().getContext();
            Graphite.Palette palette = GraphiteVue.palette(contexte);
            binding.natureLigne.setText(
                    LibellesConsole.natureCourte(contexte, ligne.nature()));
            binding.natureLigne.setTextColor(
                    couleurNature(palette, ligne.nature()));
            binding.texteLigne.setText(ligne.texte());
            binding.texteLigne.setTextColor(
                    couleurTexte(palette, ligne.nature()));
            if (ligne.cible() != null) {
                // problème ouvrable : la ligne entière l'ouvre (le fond
                // erreur 10 % porte le rayon 8 du lot K)
                binding.getRoot().setBackgroundResource(
                        R.drawable.ligne_probleme_console);
                binding.getRoot().setPadding(0, padProbleme, 0,
                        padProbleme);
                binding.getRoot().setOnClickListener(vue -> {
                    if (auditeur != null) {
                        auditeur.surOuvrir(ligne.cible());
                    }
                });
            } else {
                binding.getRoot().setBackgroundResource(0);
                binding.getRoot().setPadding(0, 0, 0, 0);
                binding.getRoot().setOnClickListener(null);
            }
        }

        private int couleurNature(Graphite.Palette palette,
                                  ConsoleBuild.Nature nature) {
            return switch (nature) {
                case SYSTEME -> palette.accent;
                case PHASE -> palette.syntaxe.fonction;
                case TACHE -> palette.texteSecondaire;
                case ERREUR -> palette.erreur;
                case AVERTISSEMENT -> palette.alerte;
            };
        }

        private int couleurTexte(Graphite.Palette palette,
                                 ConsoleBuild.Nature nature) {
            return switch (nature) {
                case SYSTEME -> palette.texte;
                case PHASE -> palette.syntaxe.fonction;
                case TACHE -> palette.texte;
                case ERREUR -> palette.erreur;
                case AVERTISSEMENT -> palette.alerte;
            };
        }
    }
}
