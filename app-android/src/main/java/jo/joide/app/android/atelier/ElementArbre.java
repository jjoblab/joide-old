package jo.joide.app.android.atelier;

import java.util.ArrayList;
import java.util.List;

import jo.joide.app.android.atelier.Arborescence.Noeud;

/**
 * Les éléments du tiroir arborescence (ADR-085, lot A5, décision 11) :
 * l'arbre posé par {@link VueArbre#setArbre} devient une liste plate
 * d'éléments RECYCLABLES pour le {@code RecyclerView} +
 * {@code ListAdapter} — plus de {@code removeAllViews()} suivie d'une
 * ré-inflation complète à chaque dépliage (constat I4). Chaque élément
 * est un enregistrement (égalité par valeur, le DiffUtil s'en nourrit).
 *
 * <p>Franchise (leçon 5, ADR-É5) : la note de franchise suit chaque
 * nœud des sorties ; jamais un nœud {@code build-joide} présenté
 * comme source. Le plafond {@link VueArbre#PLAFOND_NOEUDS} se coupe
 * avec sa raison — jamais un affichage rendu muet.</p>
 */
public sealed interface ElementArbre {

    /**
     * Une ligne de nœud : le retrait, les pastilles et l'atténuation se
     * posent au bind ; {@code sortiesEnCours} fait partie du CONTENU —
     * le DiffUtil rebascule l'atténuation quand l'état du build change,
     * les autres lignes ne se re-lient pas.
     */
    record Ligne(Noeud noeud, boolean sortiesEnCours)
            implements ElementArbre {
    }

    /** La note de franchise sous le nœud des sorties. */
    record NoteSorties(boolean enCours) implements ElementArbre {
    }

    /** La note de troncature : le plafond se dit, jamais muet. */
    record Tronque(int plafond) implements ElementArbre {
    }

    /**
     * Compose les éléments visibles du tiroir — la même composition que
     * la vue d'hier (lot L4), extraite PURE : la racine (profondeur 0)
     * ne s'affiche pas (l'en-tête porte le nom du projet), la note suit
     * chaque nœud des sorties, la troncature ferme la liste.
     *
     * @param noeuds         les nœuds composés par
     *                       {@link Arborescence#composer} (racine comprise)
     * @param sortiesEnCours vrai pendant qu'elles se construisent
     * @param plafond        le nombre MAXIMAL de lignes affichées
     *                       ({@link VueArbre#PLAFOND_NOEUDS})
     * @return les éléments, prêts pour {@code submitList}
     */
    static List<ElementArbre> composer(List<Noeud> noeuds,
                                       boolean sortiesEnCours,
                                       int plafond) {
        List<ElementArbre> elements = new ArrayList<>();
        int montres = 0;
        boolean tronque = false;
        for (Noeud noeud : noeuds) {
            if (noeud.profondeur() == 0) {
                // la racine ne s'affiche pas : l'en-tête porte le nom
                continue;
            }
            if (montres >= plafond) {
                tronque = true;
                break;
            }
            elements.add(new Ligne(noeud, sortiesEnCours));
            if (noeud.sorties()) {
                elements.add(new NoteSorties(sortiesEnCours));
            }
            montres++;
        }
        if (tronque) {
            elements.add(new Tronque(plafond));
        }
        return elements;
    }
}
