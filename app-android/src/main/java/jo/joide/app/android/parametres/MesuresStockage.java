package jo.joide.app.android.parametres;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Les mesures réelles du stockage (lot M, ADR-É8) : chaque valeur
 * affichée vient du disque, jamais d'une estimation — « Projets
 * importés », « Cache CIDX », « Dépendances (m2) ». Classe PURE : le
 * parcours et le formatage sont testés JVM ; l'écran mesure sur son
 * fil de fond et pose les valeurs.
 *
 * <p>Le parcours est récursif et complet (fichiers réguliers
 * seulement) ; un répertoire absent vaut 0 — c'est un fait, pas une
 * erreur. Le vidage supprime le CONTENU du répertoire, jamais le
 * répertoire lui-même (les moteurs le recréent).</p>
 */
public final class MesuresStockage {

    /** Plafond d'entrées parcourues : l'appareil n'est pas un serveur de fichiers. */
    public static final int PLAFOND_ENTREES = 100_000;

    private int visites;

    private MesuresStockage() {
    }

    /**
     * Mesure la taille d'un répertoire (fichiers réguliers, récursif).
     *
     * @param dossier le répertoire (un fichier simple vaut sa taille)
     * @return la taille en octets, 0 si absent
     * @throws IOException si le parcours échoue
     */
    public static long octets(Path dossier) throws IOException {
        if (dossier == null || !Files.exists(dossier)) {
            return 0L;
        }
        MesuresStockage mesures = new MesuresStockage();
        return mesures.somme(dossier);
    }

    private long somme(Path chemin) throws IOException {
        if (++visites > PLAFOND_ENTREES) {
            return 0L;
        }
        if (Files.isRegularFile(chemin)) {
            try {
                return Files.size(chemin);
            } catch (IOException e) {
                return 0L;
            }
        }
        if (!Files.isDirectory(chemin)) {
            return 0L;
        }
        long total = 0L;
        List<Path> enfants;
        try (var fluide = Files.list(chemin)) {
            enfants = fluide.sorted()
                    .collect(java.util.stream.Collectors.toList());
        }
        for (Path enfant : enfants) {
            total += somme(enfant);
        }
        return total;
    }

    /**
     * Vide le contenu d'un répertoire (le répertoire reste).
     *
     * @param dossier le répertoire à vider
     * @throws IOException si la suppression échoue — l'appelant
     *                     affiche l'échec, jamais un succès muet
     */
    public static void vider(Path dossier) throws IOException {
        if (dossier == null || !Files.isDirectory(dossier)) {
            return;
        }
        List<Path> enfants;
        try (var fluide = Files.list(dossier)) {
            enfants = fluide.sorted(Comparator.reverseOrder())
                    .collect(java.util.stream.Collectors.toList());
        }
        for (Path enfant : enfants) {
            supprimer(enfant);
        }
    }

    private static void supprimer(Path chemin) throws IOException {
        if (Files.isDirectory(chemin)) {
            List<Path> enfants;
            try (var fluide = Files.list(chemin)) {
                enfants = fluide.sorted(Comparator.reverseOrder())
                        .collect(java.util.stream.Collectors.toList());
            }
            for (Path enfant : enfants) {
                supprimer(enfant);
            }
        }
        Files.deleteIfExists(chemin);
    }

    /**
     * Formate une taille en mégaoctets : une décimale sous 100 Mo
     * (« 42,0 »), entier au-delà (« 240 ») — la valeur affichée reste
     * celle du disque, arrondie à l'affichage seulement.
     *
     * @param octets la taille en octets
     * @param locale la locale d'affichage (séparateur décimal)
     * @return le nombre formaté, sans unité (l'unité vit dans la chaîne)
     */
    public static String formaterMo(long octets, Locale locale) {
        double mo = octets / (1024d * 1024d);
        if (mo < 100d) {
            return String.format(locale, "%.1f", mo);
        }
        return String.format(locale, "%.0f", mo);
    }
}
