package jo.joide.app.android.atelier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * L'arborescence du tiroir (lot J, ADR-É5) : l'arbre affiché depuis
 * la liste plate des fichiers du projet ({@code FichiersProjet.lister}
 * — les exclusions du lot F s'appliquent), l'entrée active surlignée,
 * les sorties de build estompées et jamais présentées comme sources.
 * Pur, testé JVM.
 *
 * <p>Ordre du tiroir : dossiers avant fichiers à niveau égal (ordre
 * alphabétique), puis {@code build-joide} en toute fin de racine — le
 * nœud des sorties est présent mais non dépliable : la franchise
 * CodeAssist leçon 5 appliquée à l'arbre, « jamais un nœud build-joide
 * présenté comme source ».</p>
 */
public final class Arborescence {

    /** Le nœud du dossier des sorties de build (estompé, non dépliable). */
    public static final String NOM_SORTIES = "build-joide";

    /** Une ligne du tiroir. */
    public record Noeud(String nom, String chemin, int profondeur,
                        boolean dossier, boolean ouvert, boolean actif,
                        boolean sorties) {
    }

    /**
     * Lot L4 (ADR-083) — le filtre du tiroir (§3.8, « champ de
     * filtre ») : les chemins RETENUS quand un filtre texte est
     * actif — un fichier reste si son NOM contient le filtre
     * (insensible à la casse), et les dossiers ancêtres de tout
     * fichier retenu restent présents. La vue re-compose l'arbre
     * depuis cette liste avec les dossiers ouverts (le résultat du
     * filtre se montre déplié). Filtre vide → la liste complète.
     */
    public static List<String> cheminsFiltres(List<String> chemins,
                                              String filtre) {
        if (filtre == null || filtre.isBlank()) {
            return chemins;
        }
        String bas = filtre.toLowerCase();
        List<String> retenus = new ArrayList<>();
        for (String chemin : chemins) {
            String nom = chemin.substring(
                    chemin.lastIndexOf('/') + 1).toLowerCase();
            if (nom.contains(bas)) {
                retenus.add(chemin);
            }
        }
        return retenus;
    }

    /** Les dossiers ancêtres d'une liste de chemins (le filtre les
     * montre dépliés). */
    public static Set<String> ancetresDe(List<String> chemins) {
        Set<String> ancetres = new java.util.TreeSet<>();
        for (String chemin : chemins) {
            String dossier = "";
            String[] parties = chemin.split("/");
            for (int i = 0; i < parties.length - 1; i++) {
                if (dossier.isEmpty()) {
                    dossier = parties[i] + "/";
                } else {
                    dossier = dossier + parties[i] + "/";
                }
                ancetres.add(dossier);
            }
        }
        return ancetres;
    }

    private Arborescence() {
    }

    /**
     * Compose les lignes visibles de l'arbre.
     *
     * @param chemins          les chemins relatifs des fichiers (liste
     *                         plate de {@code FichiersProjet.lister})
     * @param nomRacine        le nom affiché de la racine (le projet)
     * @param dossiersOuverts  les chemins des dossiers dépliés
     * @param actif            le chemin du fichier actif, ou null
     * @param sortiesPresentes vrai si le dossier des sorties existe
     * @return les nœuds visibles, racine comprise
     */
    public static List<Noeud> composer(List<String> chemins,
                                       String nomRacine,
                                       Set<String> dossiersOuverts,
                                       String actif,
                                       boolean sortiesPresentes) {
        Map<String, Enfant> racine = new TreeMap<>();
        for (String chemin : chemins) {
            if (chemin == null || chemin.isEmpty()
                    || chemin.startsWith("/")) {
                continue;
            }
            inserer(racine, chemin.split("/"), 0, "");
        }
        List<Noeud> lignes = new ArrayList<>();
        lignes.add(new Noeud(nomRacine == null || nomRacine.isEmpty()
                ? "projet" : nomRacine, "", 0, true, true, false,
                false));
        deplier(racine, lignes, dossiersOuverts, actif, 1);
        if (sortiesPresentes) {
            lignes.add(new Noeud(NOM_SORTIES, NOM_SORTIES + "/", 1,
                    true, false, false, true));
        }
        return lignes;
    }

    private static void inserer(Map<String, Enfant> niveau,
                                String[] morceaux, int rang,
                                String prefixe) {
        String nom = morceaux[rang];
        boolean feuille = rang == morceaux.length - 1;
        String chemin = prefixe.isEmpty() ? nom
                : prefixe + "/" + nom;
        Enfant enfant = niveau.get(nom);
        if (enfant == null) {
            enfant = new Enfant(nom, chemin, !feuille);
            niveau.put(nom, enfant);
        } else if (!feuille) {
            // un nom traversé par un chemin plus long est un dossier
            enfant.dossier = true;
        }
        if (!feuille) {
            inserer(enfant.enfants, morceaux, rang + 1, chemin);
        }
    }

    private static void deplier(Map<String, Enfant> niveau,
                                List<Noeud> lignes,
                                Set<String> dossiersOuverts,
                                String actif, int profondeur) {
        List<Enfant> dossiers = new ArrayList<>();
        List<Enfant> fichiers = new ArrayList<>();
        for (Enfant enfant : niveau.values()) {
            if (enfant.dossier) {
                dossiers.add(enfant);
            } else {
                fichiers.add(enfant);
            }
        }
        for (Enfant dossier : dossiers) {
            boolean ouvert = dossiersOuverts != null
                    && dossiersOuverts.contains(dossier.chemin + "/");
            lignes.add(new Noeud(dossier.nom, dossier.chemin + "/",
                    profondeur, true, ouvert, false, false));
            if (ouvert) {
                deplier(dossier.enfants, lignes, dossiersOuverts, actif,
                        profondeur + 1);
            }
        }
        for (Enfant fichier : fichiers) {
            lignes.add(new Noeud(fichier.nom, fichier.chemin, profondeur,
                    false, false, fichier.chemin.equals(actif), false));
        }
    }

    /**
     * Les dossiers à déplier pour RÉVÉLER un chemin (les parents du
     * fichier, le dossier lui-même si c'en est un) — l'arbre
     * s'ouvre sur le fichier d'entrée du modèle (ADR-É4).
     */
    public static Set<String> parentsDe(String relatif) {
        Set<String> parents = new LinkedHashSet<>();
        if (relatif == null || relatif.isEmpty()) {
            return parents;
        }
        int barre = relatif.indexOf('/');
        while (barre >= 0) {
            parents.add(relatif.substring(0, barre + 1));
            barre = relatif.indexOf('/', barre + 1);
        }
        return parents;
    }

    /** Le nom affiché d'un chemin (le dernier morceau). */
    public static String nomAffiche(String relatif) {
        if (relatif == null || relatif.isEmpty()) {
            return relatif;
        }
        int barre = relatif.lastIndexOf('/');
        return barre < 0 ? relatif : relatif.substring(barre + 1);
    }

    /** Le dossier parent d'un chemin (avec barre finale), ou la racine. */
    public static String dossierDe(String relatif) {
        if (relatif == null || relatif.isEmpty()) {
            return "";
        }
        int barre = relatif.lastIndexOf('/');
        return barre < 0 ? "" : relatif.substring(0, barre + 1);
    }

    private static final class Enfant {
        final String nom;
        final String chemin;
        final Map<String, Enfant> enfants = new LinkedHashMap<>();
        boolean dossier;

        Enfant(String nom, String chemin, boolean dossier) {
            this.nom = nom;
            this.chemin = chemin;
            this.dossier = dossier;
        }
    }
}
