package jo.joide.app.android;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Fichiers ouvrables du projet : parcours du répertoire importé et
 * filtre par extension — les fichiers que l'éditeur sait servir
 * (surlignage du registre celeris, moteurs code-lsp pour
 * Java/Kotlin/XML). Pur et testable sur la JVM.
 */
public final class FichiersProjet {

    /** Extensions ouvertes dans l'éditeur (registre de la brique). */
    private static final java.util.Set<String> OUVRABLES =
            java.util.Set.of("java", "kt", "kts", "gradle", "groovy",
                    "xml", "json", "md", "toml", "properties", "txt",
                    "cfg", "pro");

    /** Répertoires jamais parcourus (sorties, caches, VCS). */
    private static final java.util.Set<String> EXCLUS =
            java.util.Set.of("build", "build-joide", ".git", ".gradle",
                    ".joide", "node_modules");

    /** Plafond de fichiers listés : l'appareil n'est pas un serveur
     *  de fichiers. */
    public static final int PLAFOND_FICHIERS = 2000;

    private FichiersProjet() {
    }

    /**
     * @param nom nom de fichier
     * @return vrai si l'éditeur ouvre ce nom
     */
    public static boolean ouvrable(String nom) {
        int point = nom.lastIndexOf('.');
        if (point < 0 || point == nom.length() - 1) {
            return false;
        }
        return OUVRABLES.contains(
                nom.substring(point + 1).toLowerCase(Locale.ROOT));
    }

    /**
     * Liste les fichiers ouvrables du projet, triés par chemin.
     *
     * @param racine racine du projet importé
     * @return les chemins RELATIFS à la racine, plafonnés
     * @throws IOException si le parcours échoue
     */
    public static List<String> lister(Path racine) throws IOException {
        List<String> trouves = new ArrayList<>();
        parcourir(racine, racine, trouves);
        trouves.sort(Comparator.naturalOrder());
        return trouves;
    }

    private static void parcourir(Path racine, Path dossier,
                                  List<String> trouves)
            throws IOException {
        if (trouves.size() >= PLAFOND_FICHIERS) {
            return;
        }
        List<Path> entrees;
        try (var fluide = Files.list(dossier)) {
            entrees = fluide.sorted()
                    .collect(java.util.stream.Collectors.toList());
        }
        for (Path entree : entrees) {
            if (trouves.size() >= PLAFOND_FICHIERS) {
                return;
            }
            String nom = entree.getFileName().toString();
            if (Files.isDirectory(entree)) {
                if (!EXCLUS.contains(nom)) {
                    parcourir(racine, entree, trouves);
                }
            } else if (ouvrable(nom)) {
                trouves.add(racine.relativize(entree).toString()
                        .replace(java.io.File.separatorChar, '/'));
            }
        }
    }

    /**
     * Lit un fichier texte du projet.
     *
     * @param racine    racine du projet importé
     * @param relatif   chemin relatif (séparateurs « / »)
     * @return le contenu texte
     * @throws IOException si la lecture échoue
     */
    public static String lire(Path racine, String relatif)
            throws IOException {
        byte[] octets = Files.readAllBytes(racine.resolve(relatif));
        return new String(octets, StandardCharsets.UTF_8);
    }

    /**
     * @param relatif chemin relatif du fichier
     * @return vrai si c'est une mise en page Android candidate à
     *         l'aperçu (un dossier res/layout*, à n'importe quelle
     *         profondeur)
     */
    public static boolean estMiseEnPage(String relatif) {
        if (!relatif.endsWith(".xml")) {
            return false;
        }
        String[] segments = relatif.split("/");
        for (int i = 0; i + 1 < segments.length; i++) {
            if ("res".equals(segments[i])
                    && segments[i + 1].startsWith("layout")) {
                return true;
            }
        }
        return false;
    }
}
