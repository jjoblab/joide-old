package jo.joide.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Écriture canonique d'un module dans son fichier joide-module.toml :
 * la sérialisation inverse de la lecture, déterministe.
 */
public final class ModuleWriter {

    private ModuleWriter() {
    }

    /**
     * @param module module à écrire
     * @return le contenu canonique du fichier de module
     */
    public static String write(Module module) {
        StringBuilder out = new StringBuilder(512);
        out.append("schema = \"").append(WorkspaceLoader.MODULE_SCHEMA).append("\"\n");
        out.append("name = \"").append(module.name()).append("\"\n");
        out.append("type = \"").append(module.type().id()).append("\"\n");
        out.append('\n');
        Map<String, List<ContentRoot>> bySourceSet = new LinkedHashMap<>();
        for (Map.Entry<String, SourceSet> entry : module.sourceSets().entrySet()) {
            bySourceSet.put(entry.getKey(), entry.getValue().roots());
        }
        for (Map.Entry<String, List<ContentRoot>> entry : bySourceSet.entrySet()) {
            out.append("[[sources]]\n");
            out.append("name = \"").append(entry.getKey()).append("\"\n");
            // regrouper les racines par role pour un rendu compact
            Map<String, List<String>> byRole = new TreeMap<>();
            for (ContentRoot root : entry.getValue()) {
                byRole.computeIfAbsent(root.role().id(), k -> new ArrayList<>())
                        .add(root.path());
            }
            for (Map.Entry<String, List<String>> role : byRole.entrySet()) {
                out.append(role.getKey()).append(" = [");
                for (int i = 0; i < role.getValue().size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    out.append('"').append(role.getValue().get(i)).append('"');
                }
                out.append("]\n");
            }
            out.append('\n');
        }
        if (!module.facets().isEmpty()) {
            for (Map.Entry<String, Facet> entry : module.facets().entrySet()) {
                out.append("[facets.").append(entry.getKey()).append("]\n");
                for (Map.Entry<String, String> config
                        : new TreeMap<>(entry.getValue().entries()).entrySet()) {
                    out.append(config.getKey()).append(" = \"")
                            .append(config.getValue()).append("\"\n");
                }
                out.append('\n');
            }
        }
        if (!module.dependencies().isEmpty()) {
            for (DependencyRef dependency : module.dependencies()) {
                out.append("[[dependencies]]\n");
                if (dependency.isModule()) {
                    out.append("path = \"").append(dependency.modulePath()).append("\"\n");
                } else {
                    out.append("coordinates = \"")
                            .append(dependency.coordinates()).append("\"\n");
                }
                out.append("scope = \"")
                        .append(dependency.scope().name().toLowerCase(
                                java.util.Locale.ROOT))
                        .append("\"\n");
                if (!dependency.variants().isEmpty()) {
                    out.append("variants = [");
                    List<String> sorted = new ArrayList<>(dependency.variants());
                    java.util.Collections.sort(sorted);
                    for (int i = 0; i < sorted.size(); i++) {
                        if (i > 0) {
                            out.append(", ");
                        }
                        out.append('"').append(sorted.get(i)).append('"');
                    }
                    out.append("]\n");
                }
                out.append('\n');
            }
        }
        return out.toString();
    }

    /**
     * @param workspace espace de travail à écrire
     * @return le contenu canonique du fichier joide.toml
     */
    public static String writeWorkspace(Workspace workspace) {
        StringBuilder out = new StringBuilder(128);
        out.append("schema = \"").append(WorkspaceLoader.WORKSPACE_SCHEMA).append("\"\n");
        out.append("name = \"").append(workspace.name()).append("\"\n");
        out.append('\n');
        String minSdk = workspace.setting("android.min-sdk").orElse(null);
        if (minSdk != null) {
            out.append("[android]\nmin-sdk = ").append(minSdk).append("\n\n");
        }
        out.append("[modules]\ninclude = [");
        for (String path : workspace.allModules().keySet()) {
            out.append("\"").append(path.substring(1)).append("\", ");
        }
        if (!workspace.allModules().isEmpty()) {
            out.setLength(out.length() - 2);
        }
        out.append("]\n");
        return out.toString();
    }
}
