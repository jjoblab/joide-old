package jo.joide.app.android.atelier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Les éléments de la section Problèmes (ADR-085, lot A5, décision 11) :
 * les diagnostics groupés par fichier (§3.6) deviennent une liste plate
 * d'éléments RECYCLABLES pour le {@code RecyclerView} +
 * {@code ListAdapter} — plus de {@code removeAllViews()} suivie d'une
 * ré-inflation complète à chaque diagnostic (constat I4). Chaque
 * élément est un enregistrement (égalité par valeur, le DiffUtil s'en
 * nourrit) ; le premier groupe est marqué : la maquette ne trace pas de
 * séparateur au-dessus du premier en-tête (le comportement du lot L3
 * conservé).
 */
public sealed interface ElementProblemes {

    /** L'en-tête d'un groupe : « Facture.java · 2 ». */
    record Entete(String fichier, String nomAffiche, int nombre,
                  boolean premier) implements ElementProblemes {
    }

    /** Une ligne de diagnostic (le tap ouvre le fichier à la ligne). */
    record Ligne(EtatProblemes.Probleme probleme)
            implements ElementProblemes {
    }

    /**
     * Compose les éléments de la section depuis l'état pur — le même
     * groupement que la vue d'hier (lot L3), extrait pur.
     *
     * @param etat l'état des diagnostics (groupé par fichier)
     * @return les éléments, prêts pour {@code submitList}
     */
    static List<ElementProblemes> composer(EtatProblemes etat) {
        List<ElementProblemes> elements = new ArrayList<>();
        Map<String, List<EtatProblemes.Probleme>> groupes =
                etat.parFichier();
        boolean premierGroupe = true;
        for (Map.Entry<String, List<EtatProblemes.Probleme>> entree
                : groupes.entrySet()) {
            String fichier = entree.getKey();
            List<EtatProblemes.Probleme> problemes = entree.getValue();
            elements.add(new Entete(fichier, nomAffiche(fichier),
                    problemes.size(), premierGroupe));
            premierGroupe = false;
            for (EtatProblemes.Probleme probleme : problemes) {
                elements.add(new Ligne(probleme));
            }
        }
        return elements;
    }

    /** Le nom affiché d'un chemin (le dernier morceau). */
    private static String nomAffiche(String fichier) {
        int barre = fichier == null ? -1 : fichier.lastIndexOf('/');
        return barre < 0 ? fichier : fichier.substring(barre + 1);
    }
}
