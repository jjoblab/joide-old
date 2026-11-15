package jo.joide.build.daemon.bench;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Génération déterministe d'un projet Java multi-modules en couches
 * avec modules centraux : chaque module de la couche i dépend de
 * modules de la couche i-1 (les hubs d'abord), chaque classe
 * référence des classes de ses dépendances — la compilation fait un
 * vrai travail de résolution, les manifestes ABI de vraies entrées.
 *
 * <p>Déterminisme : les dépendances et le contenu de chaque fichier
 * proviennent d'un générateur pseudo-aléatoire semé par (graine du
 * tier, index du module) — le projet régénéré est octet pour octet
 * identique.</p>
 */
public final class BenchProjectGenerator {

    /** Module généré : nom et dépendances (noms). */
    public static final class GeneratedModule {
        /** Nom unique du module. */
        public final String name;
        /** Dépendances de modules (noms), ordre de classpath. */
        public final List<String> dependencies;

        GeneratedModule(String name, List<String> dependencies) {
            this.name = name;
            this.dependencies = List.copyOf(dependencies);
        }
    }

    private final DaemonTier tier;

    /** @param tier gabarit à générer */
    public BenchProjectGenerator(DaemonTier tier) {
        this.tier = tier;
    }

    /**
     * Génère le projet sur disque.
     *
     * @param project répertoire racine du projet
     * @return les modules générés, dans l'ordre de déclaration
     * @throws IOException si l'écriture échoue
     */
    public List<GeneratedModule> generate(Path project) throws IOException {
        Files.createDirectories(project);
        int javaFiles = tier.sourceFiles();
        int otherFiles = tier.totalFiles - javaFiles;
        int perModule = javaFiles / tier.modules;
        int remainder = javaFiles % tier.modules;
        // couches : répartition presque uniforme des modules
        int[] layerOf = new int[tier.modules];
        List<List<Integer>> layers = new ArrayList<>();
        for (int layer = 0; layer < tier.layers; layer++) {
            layers.add(new ArrayList<>());
        }
        for (int module = 0; module < tier.modules; module++) {
            int layer = Math.min(tier.layers - 1,
                    module * tier.layers / tier.modules);
            layerOf[module] = layer;
            layers.get(layer).add(module);
        }
        // hubs : les premiers modules de la première couche
        List<Integer> hubs = new ArrayList<>();
        for (int module : layers.get(0)) {
            if (hubs.size() < tier.hubs) {
                hubs.add(module);
            }
        }
        List<GeneratedModule> generated = new ArrayList<>();
        int filesWritten = 0;
        for (int module = 0; module < tier.modules; module++) {
            String name = "m" + String.format("%03d", module);
            int files = perModule + (module < remainder ? 1 : 0);
            Random random = new Random(tier.seed * 31L + module);
            List<String> dependencies = new ArrayList<>();
            int layer = layerOf[module];
            if (layer > 0) {
                List<Integer> above = layers.get(layer - 1);
                int count = 1 + random.nextInt(
                        Math.min(3, above.size()));
                for (int pick = 0; pick < count; pick++) {
                    int chosen = pick < count - 1 && !hubs.isEmpty()
                            && random.nextInt(4) > 0
                            ? hubs.get(random.nextInt(hubs.size()))
                            : above.get(random.nextInt(above.size()));
                    String dependency = "m" + String.format("%03d",
                            chosen);
                    if (!dependencies.contains(dependency)
                            && !dependency.equals(name)) {
                        dependencies.add(dependency);
                    }
                }
            }
            Path sources = project.resolve(name
                    + "/src/main/java/demo/" + name);
            Files.createDirectories(sources);
            for (int file = 0; file < files; file++) {
                String code = sourceOf(name, file, dependencies,
                        random);
                Files.writeString(sources.resolve("C" + file + ".java"),
                        code, StandardCharsets.UTF_8);
                filesWritten++;
            }
            // fichiers de structure : le tier compte tous les
            // fichiers, pas seulement les sources
            int structure = otherFiles * (module + 1) / tier.modules
                    - otherFiles * module / tier.modules;
            Path moduleDir = project.resolve(name);
            Files.createDirectories(moduleDir);
            for (int extra = 0; extra < structure; extra++) {
                Files.writeString(
                        moduleDir.resolve("structure-" + extra + ".txt"),
                        "structure " + module + " " + extra + "\n",
                        StandardCharsets.UTF_8);
                filesWritten++;
            }
            generated.add(new GeneratedModule(name, dependencies));
        }
        if (filesWritten != tier.totalFiles) {
            throw new IllegalStateException(
                    "generation count mismatch: " + filesWritten
                            + " != " + tier.totalFiles);
        }
        return generated;
    }

    private static String sourceOf(String module, int file,
                                   List<String> dependencies,
                                   Random random) {
        StringBuilder code = new StringBuilder(256);
        code.append("package demo.").append(module).append(";\n\n");
        code.append("public class C").append(file).append(" {\n");
        code.append("    private static final int BASE = ")
                .append(file * 3 + 1).append(";\n\n");
        int references = Math.min(3, dependencies.size() * 2 + 1);
        for (int method = 0; method < 4; method++) {
            code.append("    public static int v").append(method)
                    .append("() {\n");
            code.append("        int total = BASE * ")
                    .append(method + 1).append(";\n");
            for (int reference = 0; reference < references;
                    reference++) {
                if (!dependencies.isEmpty()) {
                    String dependency = dependencies.get(
                            random.nextInt(dependencies.size()));
                    code.append("        total += demo.")
                            .append(dependency).append(".C0.v0() % ")
                            .append(reference + 2).append(";\n");
                } else {
                    code.append("        total += ")
                            .append(reference * 7 + method + 1)
                            .append(";\n");
                }
            }
            code.append("        return total;\n");
            code.append("    }\n\n");
        }
        code.append("    public static int id() {\n");
        code.append("        return v0() + v1() + v2() + v3();\n");
        code.append("    }\n");
        code.append("}\n");
        return code.toString();
    }
}
