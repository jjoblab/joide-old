package jo.joide.app.android.accueil;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import jo.joide.app.android.R;
import jo.joide.app.android.databinding.ItemActionsAccueilBinding;
import jo.joide.app.android.databinding.ItemAucunResultatBinding;
import jo.joide.app.android.databinding.ItemEtatVideAccueilBinding;
import jo.joide.app.android.databinding.ItemProjetCarteBinding;
import jo.joide.app.android.databinding.ItemProjetRepriseBinding;
import jo.joide.app.android.databinding.ItemSectionEnteteBinding;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.choix.ChoixCarte;

/**
 * L'adaptateur de la liste d'accueil (ADR-085, lot A2, décision 11) :
 * {@code RecyclerView + ListAdapter/DiffUtil}, view-holders = bindings
 * d'items ({@code item_*.xml}) — AUCUNE vue n'est instanciée en code, les
 * couleurs calculées (tuile, carte tonale) se posent au bind depuis la
 * palette Graphite (décision 8 : l'accent du nuancier décide).
 *
 * <p>L'auditeur est l'hôte étroit de l'adaptateur : l'écran reste mince,
 * la méta d'une carte se formule ici (texte de présentation), jamais
 * d'état mutable dans les vues.</p>
 */
public final class AdaptateurAccueil extends
        ListAdapter<ElementAccueil, AdaptateurAccueil.Case> {

    /** Les actions de l'écran hôte (l'interface étroite, décision 3). */
    public interface Auditeur {

        /** Ouvrir le projet d'une carte. */
        void ouvrir(ProjetsRecents.Entree entree);

        /** Ouvrir le menu ⋮ d'un projet, ANCRÉ au bouton ⋮ de sa
         *  carte (lot A7 : le popup s'accroche à la vue qui l'appelle). */
        void menuProjet(View ancre, ProjetsRecents.Entree entree);

        /** Créer un projet (premier plan de l'accueil). */
        void nouveauProjet();

        /** Importer un dossier de projet (SAF). */
        void importerDossier();
    }

    private static final int TYPE_VIDE = 0;
    private static final int TYPE_REPRISE = 1;
    private static final int TYPE_ACTIONS = 2;
    private static final int TYPE_ENTETE = 3;
    private static final int TYPE_PROJET = 4;
    private static final int TYPE_AUCUN = 5;

    private final Auditeur auditeur;

    /** Le DIFF des éléments : identité par type + id d'entrée. */
    private static final DiffUtil.ItemCallback<ElementAccueil> DIFF =
            new DiffUtil.ItemCallback<ElementAccueil>() {
                @Override
                public boolean areItemsTheSame(
                        ElementAccueil ancien,
                        ElementAccueil nouveau) {
                    if (ancien instanceof ElementAccueil.Projet a
                            && nouveau instanceof ElementAccueil.Projet b) {
                        return a.entree().id()
                                .equals(b.entree().id());
                    }
                    if (ancien instanceof ElementAccueil.Reprise a
                            && nouveau instanceof ElementAccueil.Reprise b) {
                        return a.entree().id()
                                .equals(b.entree().id());
                    }
                    return ancien.getClass() == nouveau.getClass();
                }

                @Override
                public boolean areContentsTheSame(
                        ElementAccueil ancien,
                        ElementAccueil nouveau) {
                    return ancien.equals(nouveau);
                }
            };

    public AdaptateurAccueil(Auditeur auditeur) {
        super(DIFF);
        this.auditeur = auditeur;
    }

    @Override
    public int getItemViewType(int position) {
        ElementAccueil element = getItem(position);
        if (element instanceof ElementAccueil.Vide) {
            return TYPE_VIDE;
        }
        if (element instanceof ElementAccueil.Reprise) {
            return TYPE_REPRISE;
        }
        if (element instanceof ElementAccueil.Actions) {
            return TYPE_ACTIONS;
        }
        if (element instanceof ElementAccueil.Entete) {
            return TYPE_ENTETE;
        }
        if (element instanceof ElementAccueil.Projet) {
            return TYPE_PROJET;
        }
        return TYPE_AUCUN;
    }

    @Override
    public Case onCreateViewHolder(ViewGroup parent, int type) {
        LayoutInflater gonfleur = LayoutInflater.from(parent.getContext());
        if (type == TYPE_VIDE) {
            ItemEtatVideAccueilBinding binding =
                    ItemEtatVideAccueilBinding.inflate(gonfleur,
                            parent, false);
            // l'état vide se prépare UNE fois (ses setters du kit ne
            // sont pas idempotents — glyphe inséré à chaque appel)
            binding.videAccueil.setGlypheDossier();
            binding.videAccueil.setTitre(R.string.accueil_vide_titre);
            binding.videAccueil.setAide(R.string.accueil_vide_aide);
            binding.actionVideCreer.setOnClickListener(
                    v -> auditeur.nouveauProjet());
            binding.actionVideImporter.setOnClickListener(
                    v -> auditeur.importerDossier());
            return new CaseVide(binding);
        }
        if (type == TYPE_REPRISE) {
            return new CaseReprise(ItemProjetRepriseBinding.inflate(
                    gonfleur, parent, false));
        }
        if (type == TYPE_ACTIONS) {
            ItemActionsAccueilBinding binding =
                    ItemActionsAccueilBinding.inflate(gonfleur,
                            parent, false);
            binding.actionNouveau.setOnClickListener(
                    v -> auditeur.nouveauProjet());
            binding.actionImporter.setOnClickListener(
                    v -> auditeur.importerDossier());
            return new CaseActions(binding);
        }
        if (type == TYPE_ENTETE) {
            return new CaseEntete(ItemSectionEnteteBinding.inflate(
                    gonfleur, parent, false));
        }
        if (type == TYPE_PROJET) {
            ItemProjetCarteBinding binding =
                    ItemProjetCarteBinding.inflate(gonfleur, parent,
                            false);
            binding.carteProjet.setOnClickListener(
                    v -> auditeur.ouvrir(projetDe(binding)));
            binding.actionMenuProjet.setOnClickListener(
                    v -> auditeur.menuProjet(v, projetDe(binding)));
            return new CaseProjet(binding);
        }
        return new CaseAucun(ItemAucunResultatBinding.inflate(gonfleur,
                parent, false));
    }

    @Override
    public void onBindViewHolder(Case holder, int position) {
        holder.lier(getItem(position));
    }

    /** La racine de la case (pour la mesure du recyclage). */
    abstract static class Case extends RecyclerView.ViewHolder {

        protected Case(View racine) {
            super(racine);
        }

        abstract void lier(ElementAccueil element);
    }

    private static final class CaseVide extends Case {

        private CaseVide(ItemEtatVideAccueilBinding binding) {
            super(binding.getRoot());
        }

        @Override
        void lier(ElementAccueil element) {
            // tout est posé à la création : rien à recycler
        }
    }

    private static final class CaseActions extends Case {

        private CaseActions(ItemActionsAccueilBinding binding) {
            super(binding.getRoot());
        }

        @Override
        void lier(ElementAccueil element) {
            // les écouteurs sont posés à la création : rien à recycler
        }
    }

    private static final class CaseEntete extends Case {

        private final ItemSectionEnteteBinding binding;

        private CaseEntete(ItemSectionEnteteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @Override
        void lier(ElementAccueil element) {
            ElementAccueil.Entete entete = (ElementAccueil.Entete) element;
            binding.compteSection.setText(entete.compteur());
        }
    }

    private static final class CaseAucun extends Case {

        private final ItemAucunResultatBinding binding;

        private CaseAucun(ItemAucunResultatBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @Override
        void lier(ElementAccueil element) {
            ElementAccueil.Aucun aucun = (ElementAccueil.Aucun) element;
            binding.aucunResultat.setText(aucun.texte());
        }
    }

    private static final class CaseProjet extends Case {

        private final ItemProjetCarteBinding binding;

        private CaseProjet(ItemProjetCarteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @Override
        void lier(ElementAccueil element) {
            ProjetsRecents.Entree entree =
                    ((ElementAccueil.Projet) element).entree();
            Context contexte = binding.getRoot().getContext();
            // le tag porte l'entrée aux écouteurs posés à la création
            binding.carteProjet.setTag(entree);
            binding.tuileProjet.poserType(entree.type().tuile());
            binding.nomProjet.setText(entree.nomAffiche());
            binding.metaProjet.setText(
                    metaLigne(contexte, entree));
        }
    }

    private static final class CaseReprise extends Case {

        private final ItemProjetRepriseBinding binding;

        private CaseReprise(ItemProjetRepriseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @Override
        void lier(ElementAccueil element) {
            ProjetsRecents.Entree entree =
                    ((ElementAccueil.Reprise) element).entree();
            Context contexte = binding.getRoot().getContext();
            binding.tuileReprise.poserType(entree.type().tuile());
            binding.nomReprise.setText(entree.nomAffiche());
            binding.metaReprise.setText(metaLigne(contexte, entree));
            // la carte tonale : les couleurs dépendent de l'accent du
            // nuancier — elles se posent au bind (décision 8)
            Graphite.Palette palette = GraphiteVue.palette(contexte);
            binding.carteReprise.setCardBackgroundColor(
                    ColorStateList.valueOf(
                            ChoixCarte.tonaleHaut(palette)));
            binding.carteReprise.setStrokeColor(
                    ColorStateList.valueOf(
                            ChoixCarte.tonaleBord(palette)));
        }
    }

    /** L'entrée portée par une case de projet (liaison de l'écouteur). */
    private static ProjetsRecents.Entree projetDe(
            ItemProjetCarteBinding binding) {
        return (ProjetsRecents.Entree) binding.carteProjet.getTag();
    }

    // ---- méta de carte (texte de présentation, sans état) ----

    private static String metaLigne(Context contexte,
                                    ProjetsRecents.Entree entree) {
        if (entree.type() == TypeProjet.GRADLE) {
            return contexte.getString(R.string.type_gradle_meta);
        }
        AgeRelatif.Age age = AgeRelatif.de(
                jo.joide.platform.Clock.system().wallTimeMillis(),
                entree.dernierOuverture());
        String modules = entree.nbModules() == 1
                ? contexte.getString(R.string.meta_module_1,
                        entree.nbModules())
                : contexte.getString(R.string.meta_modules,
                        entree.nbModules());
        return contexte.getString(R.string.meta_projet,
                typeTexte(contexte, entree.type()), modules,
                ageTexte(contexte, age));
    }

    private static String typeTexte(Context contexte, TypeProjet type) {
        int id = switch (type) {
            case ANDROID -> R.string.type_android;
            case CONSOLE -> R.string.type_console;
            case BIBLIOTHEQUE -> R.string.type_bibliotheque;
            case VIDE -> R.string.type_vide;
            case GRADLE -> R.string.type_gradle_meta;
        };
        return contexte.getString(id);
    }

    private static String ageTexte(Context contexte, AgeRelatif.Age age) {
        return switch (age.unite()) {
            case INSTANT -> contexte.getString(R.string.age_instant);
            case MINUTES -> contexte.getString(R.string.age_minutes,
                    age.valeur());
            case HEURES -> contexte.getString(R.string.age_heures,
                    age.valeur());
            case HIER -> contexte.getString(R.string.age_hier);
            case JOURS -> contexte.getString(R.string.age_jours,
                    age.valeur());
            case SEMAINE_DERNIERE ->
                    contexte.getString(R.string.age_semaine_derniere);
            case SEMAINES -> contexte.getString(R.string.age_semaines,
                    age.valeur());
            case MOIS -> contexte.getString(R.string.age_mois,
                    age.valeur());
            case ANNEE -> contexte.getString(R.string.age_annee);
        };
    }
}
