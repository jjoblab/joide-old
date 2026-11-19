package jo.joide.app.android.ui.atelier;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import jo.joide.app.android.atelier.EtatFeuille;
import jo.joide.app.android.databinding.FeuilleSectionJournalBinding;
import jo.joide.app.android.databinding.FeuilleSectionLogcatBinding;
import jo.joide.app.android.databinding.FeuilleSectionProblemesBinding;
import jo.joide.app.android.databinding.VueConsoleZoneBinding;

/**
 * Lot C3 (2026-11-19, demande du terrain : « le bottomsheet n'affiche
 * toujours aucune vue, utiliser des fragments que ce soit les logcat,
 * journal, etc. ») — LA SECTION DE LA FEUILLE BASSE EN FRAGMENT : la
 * console de build, les problèmes LSP, le journal de l'IDE et le
 * logcat vivent chacun dans leur PROPRE fragment, monté par
 * TRANSACTION dans le conteneur {@code contenu_feuille} (un
 * {@code FragmentContainerView}, plein parent — plus jamais une vue
 * à {@code wrap_content} affamée dans un ScrollView : le transcript
 * de la console a ENFIN sa hauteur, les listes défilent seules).
 *
 * <p>Pourquoi ça tue la classe des bugs : plus de vue POSÉE puis
 * DÉPLACÉE de parent en parent (« already has a parent » du lot C2),
 * plus de ViewStub racine à z-plan bas, plus de listes DÉTACHÉES au
 * premier passage de la console — le conteneur ne change pas, la
 * transaction montre/cache. Le fragment reste un coquillage MINCE :
 * aucun état (la vérité vit dans {@code EtatConsole},
 * {@code EtatProblemes}, {@code EtatJournal}, {@code EtatLogcat}),
 * la vue se câble et se RE-POSE au montage par l'hôte (le
 * constructeur SANS argument survit à la restauration du
 * FragmentManager — recréation de thème/langue/police : l'argument
 * {@code Bundle} porte la section, l'étiquette la retrouve).</p>
 *
 * <p>Discipline ADR-085 respectée : le layout de chaque section vit
 * en XML (consommé par son {@code *Binding}), le fragment
 * n'instancie AUCUNE vue en code, les imports restent dans la liste
 * blanche ({@code androidx.fragment} — la famille des dialogues
 * demeure interdite par D3, inutilisée ici).</p>
 */
public final class FragmentSection extends Fragment {

    /** La clé d'argument qui porte la section (survit à la
     *  restauration — jamais un champ d'instance). */
    private static final String CLE_SECTION = "joide.feuille.section";

    /** Le préfixe d'étiquette du fragment dans le FragmentManager
     *  ({@code findFragmentByTag} — la restauration le retrouve). */
    public static final String PREFIXE_ETIQUETTE = "feuille_";

    /** L'hôte du fragment : l'activité de l'atelier (la coque délègue
     *  à {@code VueAtelier} — le câblage et la re-pose d'état). */
    public interface Hote {

        /** La vue de la section vient d'être créée : l'hôte câble ses
         *  auditeurs et RE-POSE l'état courant dessus (jamais une
         *  section montée muette — c'était le symptôme du terrain). */
        void surVueSection(EtatFeuille.Section section, View vue);

        /** La vue de la section meurt (ré-inflation de racine,
         *  fermeture) : l'hôte oublie la vue montée. */
        void surSectionDetruite(EtatFeuille.Section section);
    }

    /** Fabrique le fragment d'une section (l'argument porte la
     *  section : la restauration du FragmentManager le relit sans
     *  constructeur argumenté). */
    public static FragmentSection cree(EtatFeuille.Section section) {
        FragmentSection fragment = new FragmentSection();
        Bundle arguments = new Bundle();
        arguments.putString(CLE_SECTION, section.name());
        fragment.setArguments(arguments);
        return fragment;
    }

    /** L'étiquette du fragment d'une section dans le FragmentManager. */
    public static String etiquette(EtatFeuille.Section section) {
        return PREFIXE_ETIQUETTE + section.name();
    }

    /** La section portée par l'argument (CONSOLE par défaut : un
     *  fragment restauré sans argument reste honnête, jamais mort). */
    public EtatFeuille.Section section() {
        Bundle arguments = getArguments();
        String nom = arguments == null ? null
                : arguments.getString(CLE_SECTION);
        return nom == null ? EtatFeuille.Section.CONSOLE
                : EtatFeuille.Section.valueOf(nom);
    }

    @Override
    public View onCreateView(LayoutInflater gonfleur,
                             ViewGroup conteneur,
                             Bundle etat) {
        // la composition vit en XML : un layout par section, gonflé
        // par SON binding (le filet A0 « chaque layout a un
        // consommateur » le vérifie), attaché au conteneur PLEIN
        // PARENT — la vue reçoit la hauteur du fragment, pas un
        // wrap_content affamé
        return switch (section()) {
            case CONSOLE -> VueConsoleZoneBinding.inflate(
                    gonfleur, conteneur, false).getRoot();
            case PROBLEMES -> FeuilleSectionProblemesBinding.inflate(
                    gonfleur, conteneur, false).getRoot();
            case JOURNAL -> FeuilleSectionJournalBinding.inflate(
                    gonfleur, conteneur, false).getRoot();
            case LOGCAT -> FeuilleSectionLogcatBinding.inflate(
                    gonfleur, conteneur, false).getRoot();
        };
    }

    @Override
    public void onViewCreated(View vue, Bundle etat) {
        super.onViewCreated(vue, etat);
        ((Hote) requireActivity()).surVueSection(section(), vue);
    }

    @Override
    public void onDestroyView() {
        ((Hote) requireActivity()).surSectionDetruite(section());
        super.onDestroyView();
    }
}
