package jo.joide.bench;

import java.util.List;
import java.util.Objects;

/**
 * Écriture des fichiers de configuration JoIDE du projet synthétique :
 * joide.toml (workspace) et joide-module.toml (module).
 *
 * <p>Le schéma écrit est le sous-ensemble canonique versionné par
 * champ {@code schema} : {@code joide.workspace/1} et
 * {@code joide.module/1}. Le lecteur de projets lit ce sous-ensemble
 * tel quel.</p>
 */
public final class TomlWriter {

    private TomlWriter() {
    }

    /**
     * @param projectName nom du projet synthétique
     * @param modules     noms des modules (triés)
     * @return le contenu de joide.toml
     */
    public static String workspace(String projectName, List<String> modules) {
        Objects.requireNonNull(projectName, "projectName");
        Objects.requireNonNull(modules, "modules");
        StringBuilder out = new StringBuilder(64 + modules.size() * 24);
        out.append("# Genere par tools-bench — ne pas editer.\n");
        out.append("schema = \"joide.workspace/1\"\n");
        out.append("name = \"").append(projectName).append("\"\n\n");
        out.append("[android]\nmin-sdk = 28\n\n");
        out.append("[modules]\ninclude = [");
        for (int i = 0; i < modules.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append('"').append(modules.get(i)).append('"');
        }
        out.append("]\n");
        return out.toString();
    }

    /**
     * @param name         nom court du module
     * @param dependencies noms des modules dont il dépend
     * @return le contenu de joide-module.toml
     */
    public static String module(String name, List<String> dependencies) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(dependencies, "dependencies");
        StringBuilder out = new StringBuilder(256);
        out.append("# Genere par tools-bench — ne pas editer.\n");
        out.append("schema = \"joide.module/1\"\n");
        out.append("name = \"").append(name).append("\"\n");
        out.append("type = \"android-library\"\n\n");
        out.append("[[sources]]\n");
        out.append("name = \"main\"\n");
        out.append("source = [\"src/main/java\", \"src/main/kotlin\"]\n");
        out.append("android-res = [\"src/main/res\"]\n");
        out.append("assets = [\"src/main/assets\"]\n\n");
        out.append("[facets.android]\n");
        out.append("manifest = \"src/main/AndroidManifest.xml\"\n\n");
        for (String dep : dependencies) {
            out.append("[[dependencies]]\n");
            out.append("path = \":").append(dep).append("\"\n");
            out.append("scope = \"api\"\n\n");
        }
        return out.toString();
    }
}
