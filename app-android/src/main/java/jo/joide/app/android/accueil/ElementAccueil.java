package jo.joide.app.android.accueil;

import jo.joide.app.android.accueil.ProjetsRecents.Entree;

/**
 * Les éléments de la liste d'accueil (ADR-085, lot A2, décision 11) : la
 * vue pure de {@link EtatAccueil} composée en éléments RECYCLABLES —
 * chaque élément est un enregistrement (égalité par valeur, le DiffUtil
 * s'en nourrit). Le singleton VIDE/ACTIONS évite les doublons.
 */
public sealed interface ElementAccueil {

    /** La première visite : l'état vide fonctionnel. */
    Vide VIDE = new Vide();

    /** La rangée d'actions (créer, importer). */
    Actions ACTIONS = new Actions();

    /** L'état vide de première visite. */
    record Vide() implements ElementAccueil {
    }

    /** La rangée d'actions permanentes. */
    record Actions() implements ElementAccueil {
    }

    /** La carte de reprise du projet le plus récent. */
    record Reprise(Entree entree) implements ElementAccueil {
    }

    /** L'en-tête de section « Projets » avec son compteur. */
    record Entete(String compteur) implements ElementAccueil {
    }

    /** Une carte de projet. */
    record Projet(Entree entree) implements ElementAccueil {
    }

    /** Le « aucun résultat » de la recherche courante. */
    record Aucun(String texte) implements ElementAccueil {
    }

    /** La vue composée en éléments recyclables (ordre de l'accueil). */
    static java.util.List<ElementAccueil> composer(EtatAccueil.Vue vue,
                                                   String aucunResultat) {
        java.util.List<ElementAccueil> elements = new java.util.ArrayList<>();
        if (vue.premiereFois()) {
            elements.add(VIDE);
            return elements;
        }
        if (vue.reprise() != null) {
            elements.add(new Reprise(vue.reprise()));
        }
        elements.add(ACTIONS);
        if (vue.sectionVisible()) {
            elements.add(new Entete(vue.texteCompteur()));
            for (Entree entree : vue.section()) {
                elements.add(new Projet(entree));
            }
            if (vue.aucunResultat()) {
                elements.add(new Aucun(aucunResultat));
            }
        }
        return elements;
    }
}
