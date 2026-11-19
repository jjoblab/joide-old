package jo.joide.app.android.atelier;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import jo.joide.app.android.R;
import jo.joide.app.android.atelier.Arborescence.Noeud;
import jo.joide.app.android.databinding.ItemNoeudBinding;
import jo.joide.app.android.databinding.ItemNoteSortiesBinding;
import jo.joide.app.android.databinding.ItemNoteTronqueeBinding;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;

/**
 * L'arbre du tiroir (ADR-085, lot A5, décision 11) :
 * {@code RecyclerView + ListAdapter/DiffUtil}, view-holders = bindings
 * d'items ({@code item_noeud} du lot L4 conservé, notes de franchise
 * et de troncature en éléments recyclés) — la mise à jour passe par
 * {@code submitList} : plus de {@code removeAllViews()} suivie d'une
 * ré-inflation de TOUTES les lignes à chaque dépliage (constat I4) ;
 * le DiffUtil ne re-lient que les lignes CHANGÉES (dépliage, entrée
 * active, sorties en cours), le plafond 400 est conservé.
 *
 * <p>Le retrait par profondeur (6 + 16×niveau, borné 70), les
 * pastilles de type et la ligne active se posent au bind — le rendu du
 * lot L4, transporté. Correction du recyclage : l'atténuation des
 * sorties et le fond actif se RE-POSENT à chaque bind (le lot L4
 * gonflait des vues neuves à chaque setArbre, un alpha ou un fond
 * posés ne s'y annulaient jamais — ils s'y annulent ici).</p>
 */
final class AdaptateurArbre extends
        ListAdapter<ElementArbre, AdaptateurArbre.Case> {

    /** Les actions du tiroir (activation, pliage, menu contextuel). */
    interface Auditeur {
        /** L'appui long demande le menu contextuel du nœud (§3.8). */
        void surMenuContextuel(String chemin, boolean dossier);

        /** Un dossier est plié/déplié. */
        void surDossier(String chemin, boolean ouvrir);

        /** Un fichier est ouvert. */
        void surFichier(String chemin);
    }

    private static final int TYPE_NOEUD = 0;
    private static final int TYPE_NOTE_SORTIES = 1;
    private static final int TYPE_NOTE_TRONQUEE = 2;

    /** Le DIFF : identité par chemin pour les lignes, notes uniques. */
    private static final DiffUtil.ItemCallback<ElementArbre> DIFF =
            new DiffUtil.ItemCallback<ElementArbre>() {
                @Override
                public boolean areItemsTheSame(ElementArbre ancien,
                                                ElementArbre nouveau) {
                    if (ancien instanceof ElementArbre.Ligne a
                            && nouveau instanceof ElementArbre.Ligne b) {
                        return a.noeud().chemin()
                                .equals(b.noeud().chemin());
                    }
                    return ancien.getClass() == nouveau.getClass();
                }

                @Override
                public boolean areContentsTheSame(ElementArbre ancien,
                                                   ElementArbre nouveau) {
                    return ancien.equals(nouveau);
                }
            };

    private final Auditeur auditeur;

    AdaptateurArbre(Auditeur auditeur) {
        super(DIFF);
        this.auditeur = auditeur;
    }

    @Override
    public int getItemViewType(int position) {
        ElementArbre element = getItem(position);
        if (element instanceof ElementArbre.Ligne) {
            return TYPE_NOEUD;
        }
        if (element instanceof ElementArbre.NoteSorties) {
            return TYPE_NOTE_SORTIES;
        }
        return TYPE_NOTE_TRONQUEE;
    }

    @Override
    public Case onCreateViewHolder(ViewGroup parent, int type) {
        LayoutInflater gonfleur =
                LayoutInflater.from(parent.getContext());
        if (type == TYPE_NOTE_SORTIES) {
            return new CaseNoteSorties(ItemNoteSortiesBinding.inflate(
                    gonfleur, parent, false));
        }
        if (type == TYPE_NOTE_TRONQUEE) {
            return new CaseNoteTronquee(ItemNoteTronqueeBinding.inflate(
                    gonfleur, parent, false));
        }
        return new CaseNoeud(ItemNoeudBinding.inflate(
                gonfleur, parent, false));
    }

    @Override
    public void onBindViewHolder(Case possesseur, int position) {
        possesseur.lier(getItem(position));
    }

    /** Une case de l'arbre (ligne de nœud, note de franchise, troncature). */
    abstract static class Case extends RecyclerView.ViewHolder {

        protected Case(View racine) {
            super(racine);
        }

        abstract void lier(ElementArbre element);
    }

    /** Une ligne de nœud (le rendu du lot L4 transporté au bind). */
    private final class CaseNoeud extends Case {

        private final ItemNoeudBinding binding;
        private final Graphite.Palette palette;
        private final int rayonPastille;
        private final int rayonActif;

        CaseNoeud(ItemNoeudBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            Context contexte = binding.getRoot().getContext();
            this.palette = GraphiteVue.palette(contexte);
            this.rayonPastille = contexte.getResources()
                    .getDimensionPixelSize(R.dimen.jo_rayon_pastille_noeud);
            this.rayonActif = contexte.getResources()
                    .getDimensionPixelSize(R.dimen.jo_rayon_ligne_active);
        }

        @Override
        void lier(ElementArbre element) {
            Noeud noeud = ((ElementArbre.Ligne) element).noeud();
            boolean sortiesEnCours =
                    ((ElementArbre.Ligne) element).sortiesEnCours();
            Context contexte = binding.getRoot().getContext();

            // le retrait par profondeur (§3.8 : 6 + 16 par niveau, 70 max)
            int retrait = GraphiteVue.dp(contexte,
                    Math.min(6 + noeud.profondeur() * 16, 70));
            binding.rangeeNoeud.setPadding(retrait, 0,
                    GraphiteVue.dp(contexte, 8), 0);
            boolean actif = noeud.actif();
            binding.getRoot().setOnClickListener(vue -> {
                if (auditeur == null) {
                    return;
                }
                if (noeud.sorties()) {
                    // les sorties ne s'ouvrent pas : jamais des sources
                    return;
                }
                if (noeud.dossier()) {
                    auditeur.surDossier(noeud.chemin(), !noeud.ouvert());
                } else {
                    auditeur.surFichier(noeud.chemin());
                }
            });
            binding.getRoot().setOnLongClickListener(vue -> {
                if (auditeur == null || noeud.sorties()) {
                    return false;
                }
                auditeur.surMenuContextuel(noeud.chemin(),
                        noeud.dossier());
                return true;
            });

            // le chevron : dossiers OUVRABLES seulement (les sorties et
            // les fichiers portent la pastille de type)
            if (noeud.dossier() && !noeud.sorties()) {
                binding.chevronNoeud.setVisibility(View.VISIBLE);
                binding.chevronNoeud.setCouleur(noeud.ouvert()
                        ? palette.texteSecondaire : palette.texteEstompe);
                binding.chevronNoeud.setRotation(
                        noeud.ouvert() ? 90f : 0f);
            } else {
                binding.chevronNoeud.setVisibility(View.GONE);
            }

            // la pastille de type (§3.8) : J ambre / ‹› réussite /
            // ⚙ accent / dossier glyphe texte2 — tout vient des jetons
            String badge;
            int fondPastille;
            int textePastille;
            if (noeud.dossier()) {
                badge = "▤";
                fondPastille = 0;
                textePastille = palette.texteSecondaire;
            } else if (noeud.sorties()) {
                badge = "⚙";
                fondPastille = 0;
                textePastille = palette.texteEstompe;
            } else {
                String bas = noeud.nom().toLowerCase();
                if (bas.endsWith(".java")) {
                    badge = "J";
                    fondPastille = resoudre(contexte,
                            R.attr.jo_pastille_ambre);
                    textePastille = palette.texte;
                } else if (bas.endsWith(".kt")
                        || bas.endsWith(".kts")) {
                    badge = "K";
                    fondPastille = resoudre(contexte,
                            R.attr.jo_pastille_violet);
                    textePastille = palette.texte;
                } else if (bas.endsWith(".xml")) {
                    badge = "‹›";
                    fondPastille = resoudre(contexte,
                            R.attr.jo_pastille_reussite);
                    textePastille = palette.texte;
                } else {
                    badge = "⚙";
                    fondPastille = resoudre(contexte,
                            R.attr.jo_pastille_accent);
                    textePastille = palette.texte;
                }
            }
            binding.pastilleNoeud.setText(badge);
            binding.pastilleNoeud.setTextColor(textePastille);
            GradientDrawable fondBadge = new GradientDrawable();
            fondBadge.setCornerRadius(rayonPastille);
            if (fondPastille != 0) {
                fondBadge.setColor(fondPastille);
            }
            binding.pastilleNoeud.setBackground(fondBadge);

            // le nom : actif accent semi-gras, sorties estompées mono
            binding.nomNoeud.setText(noeud.nom());
            if (actif) {
                binding.nomNoeud.setTextColor(palette.accent);
                binding.nomNoeud.setTypeface(GraphiteVue.police(
                        contexte, 600, false));
            } else if (noeud.sorties()) {
                binding.nomNoeud.setTextColor(palette.texteEstompe);
                binding.nomNoeud.setTypeface(Typeface.create("monospace",
                        Typeface.NORMAL));
            } else {
                binding.nomNoeud.setTextColor(noeud.dossier()
                        ? palette.texte : palette.texteSecondaire);
                binding.nomNoeud.setTypeface(Typeface.DEFAULT);
            }

            // la puce « build #n… » sur les sorties en cours (§3.8) —
            // l'atténuation se RE-POSE (recyclage : jamais un alpha
            // qui traîne d'un bind précédent)
            if (noeud.sorties() && sortiesEnCours) {
                binding.puceSorties.setText(contexte.getString(
                        R.string.atelier_sorties_en_cours));
                binding.puceSorties.setVisibility(View.VISIBLE);
                binding.rangeeNoeud.setAlpha(0.5f);
            } else {
                binding.puceSorties.setVisibility(View.GONE);
                binding.rangeeNoeud.setAlpha(1f);
            }

            // la barre active : 3 dp accent (inset 9, §3.8) ; le fond
            // se RE-POSE (recyclage : jamais un fond qui traîne)
            binding.barreNoeud.setVisibility(
                    actif ? View.VISIBLE : View.GONE);
            if (actif) {
                GradientDrawable fondActif = new GradientDrawable();
                fondActif.setColor(Graphite.melange(palette.accent,
                        0.14f, palette.surface));
                fondActif.setCornerRadius(rayonActif);
                binding.getRoot().setBackground(fondActif);
            } else {
                binding.getRoot().setBackground(null);
            }
        }
    }

    /** La note de franchise sous le nœud des sorties. */
    private static final class CaseNoteSorties extends Case {

        private final ItemNoteSortiesBinding binding;

        CaseNoteSorties(ItemNoteSortiesBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @Override
        void lier(ElementArbre element) {
            binding.noteSorties.setAlpha(
                    ((ElementArbre.NoteSorties) element).enCours()
                            ? 0.7f : 1f);
        }
    }

    /** La note de troncature : le plafond se dit, jamais muet. */
    private static final class CaseNoteTronquee extends Case {

        private final ItemNoteTronqueeBinding binding;

        CaseNoteTronquee(ItemNoteTronqueeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @Override
        void lier(ElementArbre element) {
            int plafond = ((ElementArbre.Tronque) element).plafond();
            binding.noteTronquee.setText(binding.getRoot().getContext()
                    .getString(R.string.atelier_arbre_tronque, plafond));
        }
    }

    /**
     * Résout un attribut de thème en couleur (jamais une valeur codée
     * en dur, franchise §0.4) — la résolution du lot L4 transportée.
     */
    private static int resoudre(Context contexte, int attribut) {
        TypedValue valeur = new TypedValue();
        contexte.getTheme().resolveAttribute(attribut, valeur, true);
        if (valeur.type == TypedValue.TYPE_ATTRIBUTE) {
            TypedArray suivant = contexte.obtainStyledAttributes(
                    new int[]{valeur.data});
            int couleur = suivant.getColor(0, 0);
            suivant.recycle();
            return couleur;
        }
        return valeur.data;
    }
}
