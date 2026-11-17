package jo.joide.app.android.accueil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * L'état d'écran pur de l'accueil « Mes projets » (ADR-É3) : tri par
 * dernière ouverture décroissante, carte « Reprendre » fusionnée avec
 * la première carte (jamais de doublon), section « Projets (n) » au
 * compteur exact, état vide de première classe, filtre de recherche.
 *
 * <p>Classe PURE : la vue ne fait que dérouler {@link Vue} ; tous les
 * comportements (tri, fusion, filtrage, compteur) sont testés JVM.
 * La traduction en chaînes localisées (âges, types, pluriel des
 * modules) reste à la vue — l'état livre les pièces structurées.</p>
 */
public final class EtatAccueil {

    private EtatAccueil() {
    }

    /**
     * La vue déroulée de l'accueil.
     *
     * <ul>
     *   <li>{@code premiereFois} : aucun projet connu — l'état vide
     *       de première classe (grand encadré, deux actions).</li>
     *   <li>{@code reprise} : le projet le plus récemment ouvert,
     *       fusionné avec la première carte (absent de la section),
     *       ou {@code null} sans projet ou pendant une recherche.</li>
     *   <li>{@code section} : les cartes de la liste, reprise
     *       exclue, filtrées par la recherche.</li>
     *   <li>{@code total} : le nombre exact de projets connus — le
     *       compteur de section, jamais arrondi.</li>
     *   <li>{@code aucunResultat} : une recherche active ne matche
     *       rien (ligne « aucun résultat », pas l'état vide).</li>
     * </ul>
     */
    public record Vue(boolean premiereFois,
                      ProjetsRecents.Entree reprise,
                      List<ProjetsRecents.Entree> section,
                      int total,
                      String filtre,
                      boolean aucunResultat) {

        /** Le compteur de section : le nombre de cartes affichées. */
        public int compteSection() {
            return section.size();
        }

        /** Le libellé du compteur : « 3 », ou « 2/4 » pendant une recherche. */
        public String texteCompteur() {
            if (filtre == null || filtre.isEmpty()) {
                return String.valueOf(section.size());
            }
            return section.size() + "/" + total;
        }

        /** La section est-elle visible (au moins une carte) ? */
        public boolean sectionVisible() {
            return !section.isEmpty() || aucunResultat;
        }
    }

    /**
     * Compose l'état de l'accueil.
     *
     * @param entrees les projets connus (ordre de persistance indifférent)
     * @param filtre  le texte de recherche (null ou vide : aucun)
     * @return l'état déroulé, jamais null
     */
    public static Vue composer(List<ProjetsRecents.Entree> entrees,
                               String filtre) {
        Objects.requireNonNull(entrees, "entrees");
        List<ProjetsRecents.Entree> triees =
                new ArrayList<>(entrees);
        triees.sort(ProjetsRecents.OrdreRecent.INSTANCE);
        int total = triees.size();
        String texte = filtre == null ? "" : filtre.trim().toLowerCase(
                Locale.ROOT);
        if (total == 0) {
            return new Vue(true, null, List.of(), 0, texte, false);
        }
        if (texte.isEmpty()) {
            ProjetsRecents.Entree reprise = triees.get(0);
            List<ProjetsRecents.Entree> section =
                    triees.subList(1, triees.size());
            return new Vue(false, reprise, List.copyOf(section), total,
                    texte, false);
        }
        List<ProjetsRecents.Entree> filtres = new ArrayList<>();
        for (ProjetsRecents.Entree e : triees) {
            if (e.nomAffiche().toLowerCase(Locale.ROOT).contains(texte)) {
                filtres.add(e);
            }
        }
        return new Vue(false, null, List.copyOf(filtres), total, texte,
                filtres.isEmpty());
    }

    /**
     * Le nom par défaut unique d'un nouveau projet (ADR-É4, étape 2) :
     * « mon-application », puis « mon-application-2 », « …-3 »…
     *
     * @param entrees les projets connus
     * @param base le nom de base assaini
     * @return un nom affiché absent de la liste
     */
    public static String nomDefautUnique(
            List<ProjetsRecents.Entree> entrees, String base) {
        String candidat = base;
        int suffixe = 2;
        while (nomPris(entrees, candidat)) {
            candidat = base + "-" + suffixe;
            suffixe++;
        }
        return candidat;
    }

    private static boolean nomPris(
            List<ProjetsRecents.Entree> entrees, String candidat) {
        for (ProjetsRecents.Entree entree : entrees) {
            if (entree.nomAffiche().equals(candidat)) {
                return true;
            }
        }
        return false;
    }
}
