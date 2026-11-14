package jo.joide.bench;

import java.util.List;
import java.util.Objects;

/**
 * Écriture des fichiers de configuration JoIDE du projet synthétique
 * (ADR-014) : joide.toml (workspace) et joide-module.toml (module).
 *
 * <p>Sous-ensemble TOML figé pour P0, versionné par champ
 * {@code schema} : {@code joide.workspace/1} et {@code joide.module/1}.
 * Personne ne relit ces fichiers en P0 ; core-model (P1) devra lire ce
 * sous-ensemble tel quel (risque R2 du plan, consigné).</p>
 */
public final class TomlWriter {

    private TomlWriter() {
    }

    /**
     * @param tier    tier du projet
     * @param modules noms des modules (triés)
     * @return le contenu de joide.toml
     */
    public static String workspace(TierSpec tier, List<String> modules) {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(modules, "modules");
        StringBuilder out = new StringBuilder(64 + modules.size() * 24);
        out.append("# Genere par tools-bench (ADR-014) — ne pas editer.\n");
        out.append("schema = \"joide.workspace/1\"\n");
        out.append("name = \"").append(tier.projectName()).append("\"\n");
        out.append("seed = ").append(tier.seed).append("\n\n");
        out.append("[android]\nmin-sdk = 28\n\n");
        out.append("[modules]\n");
        for (String module : modules) {
            out.append("include = \"").append(module).append("\"\n");
        }
        return out.toString();
    }

    /**
     * @param module       module planifié
     * @return le contenu de joide-module.toml
     */
    public static String module(ModuleGraphPlanner.PlannedModule module) {
        Objects.requireNonNull(module, "module");
        StringBuilder out = new StringBuilder(256);
        out.append("# Genere par tools-bench (ADR-014) — ne pas editer.\n");
        out.append("schema = \"joide.module/1\"\n");
        out.append("name = \"").append(module.name).append("\"\n");
        out.append("layer = ").append(module.layer).append("\n");
        out.append("hub = ").append(module.hub).append("\n\n");
        out.append("[sources]\n");
        out.append("java = \"src/main/java\"\n");
        out.append("kotlin = \"src/main/kotlin\"\n\n");
        out.append("[android]\n");
        out.append("manifest = \"src/main/AndroidManifest.xml\"\n");
        out.append("res = \"src/main/res\"\n");
        out.append("assets = \"src/main/assets\"\n");
        out.append("min-sdk = 28\n\n");
        out.append("[dependencies]\n");
        for (String dep : module.dependencies) {
            out.append("path = \"").append(dep).append("\"\n");
        }
        return out.toString();
    }
}
