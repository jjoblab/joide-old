package jo.joide.ide.sync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Localisation des répertoires des modules du workspace sur le disque.
 *
 * <p>Le modèle (core-model) porte des racines de contenu relatives au
 * répertoire du module ; la localisation du répertoire lui-même est un
 * fait de disposition, pas de modèle. La disposition native JoIDE
 * ({@code joide.toml}) et l'import Gradle (P7) partagent la même règle :
 * le répertoire d'un module {@code :a:b} est {@code racine/a/b}.</p>
 */
public final class ModuleDirectories {

    private final Path root;
    private final Map<String, Path> overrides;

    private ModuleDirectories(Path root, Map<String, Path> overrides) {
        this.root = root;
        this.overrides = overrides;
    }

    /**
     * @param root racine du workspace
     * @return la localisation par défaut : {@code racine/<segments>}
     */
    public static ModuleDirectories nested(Path root) {
        return new ModuleDirectories(root, Map.of());
    }

    /**
     * @param root     racine du workspace
     * @param overrides répertoires explicites par chemin de module
     *                  (prioritaires sur la règle imbriquée)
     * @return la localisation avec exceptions explicites
     */
    public static ModuleDirectories nestedWithOverrides(Path root,
            Map<String, Path> overrides) {
        return new ModuleDirectories(root, new LinkedHashMap<>(overrides));
    }

    /**
     * @param cheminModule chemin du module (« :a:b »)
     * @return le répertoire du module, ou {@code null} si inconnu
     */
    public Path directory(String cheminModule) {
        Path override = overrides.get(cheminModule);
        if (override != null) {
            return override;
        }
        Path dir = root;
        for (String segment : cheminModule.substring(1).split(":")) {
            dir = dir.resolve(segment);
        }
        return dir;
    }

    /**
     * @param cheminModule chemin du module
     * @return vrai si le répertoire du module existe sur le disque
     */
    public boolean exists(String cheminModule) {
        Path dir = directory(cheminModule);
        return dir != null && Files.isDirectory(dir);
    }
}
