package jo.joide.bench;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * Génération complète d'un projet synthétique (ADR-014).
 *
 * <p>Déterminisme : le budget de fichiers par module est réparti de
 * façon exacte (totaux du tier garantis), chaque fichier reçoit un
 * générateur pseudo-aléatoire semé par (graine du tier, chemin relatif)
 * — le contenu d'un fichier ne dépend jamais de l'ordre d'itération.
 * Le nombre total de fichiers écrits est exactement
 * {@link TierSpec#totalFiles} : 85 % de sources, 15 % de ressources et
 * fichiers de structure (joide.toml, joide-module.toml, manifestes,
 * ADR-014).</p>
 */
public final class ProjectGenerator {

    /** Statistiques de génération (partie déterministe des métriques). */
    public static final class Stats {
        /** Nombre de fichiers écrits. */
        public final int files;
        /** Nombre de répertoires créés. */
        public final int dirs;
        /** Nombre d'octets écrits. */
        public final long bytes;
        /** Nombre de fichiers Java. */
        public final int javaFiles;
        /** Nombre de fichiers Kotlin. */
        public final int kotlinFiles;
        /** Nombre de fichiers de ressources/structure. */
        public final int otherFiles;

        Stats(int files, int dirs, long bytes, int javaFiles,
              int kotlinFiles, int otherFiles) {
            this.files = files;
            this.dirs = dirs;
            this.bytes = bytes;
            this.javaFiles = javaFiles;
            this.kotlinFiles = kotlinFiles;
            this.otherFiles = otherFiles;
        }
    }

    /** Compteur d'écritures (un seul point de passage). */
    private static final class Sink {
        final Path root;
        int files;
        int dirs;
        long bytes;

        Sink(Path root) throws IOException {
            this.root = root;
            Files.createDirectories(root);
        }

        Path dir(String relative) throws IOException {
            Path path = root.resolve(relative);
            Files.createDirectories(path);
            return path;
        }

        void text(String relative, String content) throws IOException {
            Path path = root.resolve(relative);
            Files.createDirectories(path.getParent());
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            Files.write(path, data);
            files++;
            bytes += data.length;
        }

        void bytes(String relative, byte[] data) throws IOException {
            Path path = root.resolve(relative);
            Files.createDirectories(path.getParent());
            Files.write(path, data);
            files++;
            bytes += data.length;
        }
    }

    /**
     * Génère le projet d'un tier dans un répertoire (qui doit être vide
     * ou absent).
     *
     * @param tier   tier à générer
     * @param outDir répertoire racine du projet
     * @return les statistiques de génération
     * @throws IOException si une écriture échoue
     */
    public static Stats generate(TierSpec tier, Path outDir)
            throws IOException {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(outDir, "outDir");
        List<ModuleGraphPlanner.PlannedModule> modules =
                ModuleGraphPlanner.plan(tier);
        // Budgets exacts par module.
        int[] sourceBudget = spread(tier.sourceFiles(), modules.size());
        int overhead = 1 + modules.size() * 2;
        int resTotal = tier.totalFiles - tier.sourceFiles() - overhead;
        int[] resBudget = spread(resTotal, modules.size());
        // Registre après budgétisation : les exports couvrent les
        // fichiers Java de chaque module (un fichier = un type public).
        int[] javaBudget = new int[modules.size()];
        for (int i = 0; i < modules.size(); i++) {
            javaBudget[i] = (int) ((long) sourceBudget[i] * 60 / 100);
        }
        TypeRegistry registry = new TypeRegistry(modules, javaBudget);

        Sink sink = new Sink(outDir);
        List<String> moduleNames = new ArrayList<>();
        for (ModuleGraphPlanner.PlannedModule module : modules) {
            moduleNames.add(module.name);
        }
        sink.text("joide.toml", TomlWriter.workspace(tier, moduleNames));

        int javaTotal = 0;
        int kotlinTotal = 0;
        for (int m = 0; m < modules.size(); m++) {
            ModuleGraphPlanner.PlannedModule module = modules.get(m);
            String base = module.name;
            String pkgDir = "com/example/" + base;
            sink.text(base + "/joide-module.toml", TomlWriter.module(module));
            sink.text(base + "/src/main/AndroidManifest.xml",
                    new ResourceWriter(base).manifest());
            // Sources Java : un fichier par type exporté du module.
            List<TypeRegistry.ExportedType> exports = registry.exportsOf(base);
            JavaWriter java = new JavaWriter(registry, base,
                    module.dependencies);
            for (int i = 0; i < javaBudget[m]; i++) {
                TypeRegistry.ExportedType type = exports.get(i);
                String relative = base + "/src/main/java/" + pkgDir
                        + "/" + type.simpleName + ".java";
                Random rng = rngFor(tier, relative);
                int targetLines = 30 + rng.nextInt(371);
                sink.text(relative, java.write(type, targetLines, rng));
                javaTotal++;
            }
            // Sources Kotlin : index unique par module.
            int kotlinCount = sourceBudget[m] - javaBudget[m];
            KotlinWriter kotlin = new KotlinWriter(registry, base,
                    module.dependencies);
            for (int i = 0; i < kotlinCount; i++) {
                String relative = base + "/src/main/kotlin/" + pkgDir
                        + "/File" + i + ".kt";
                Random rng = rngFor(tier, relative);
                sink.text(relative, kotlin.write(i, rng));
                kotlinTotal++;
            }
            writeResources(tier, sink, base, resBudget[m]);
        }
        int otherTotal = sink.files - javaTotal - kotlinTotal;
        return new Stats(sink.files, countDirs(sink.root), sink.bytes,
                javaTotal, kotlinTotal, otherTotal);
    }

    private static void writeResources(TierSpec tier, Sink sink, String base,
                                       int budget) throws IOException {
        ResourceWriter resources = new ResourceWriter(base);
        // Deux fichiers de valeurs toujours présents.
        int remaining = budget - 2;
        int layouts = remaining * 6 / 10;
        int drawables = remaining * 2 / 10;
        int assets = remaining - layouts - drawables;
        List<Integer> layoutIndexes = new ArrayList<>();
        Random layoutRng = rngFor(tier, base + "/layouts");
        for (int i = 0; i < layouts; i++) {
            layoutIndexes.add(i);
            String relative = base + "/src/main/res/layout/view_" + i + ".xml";
            sink.text(relative, resources.layout(i, rngFor(tier, relative)));
        }
        for (int i = 0; i < drawables; i++) {
            String relative = base + "/src/main/res/drawable/shape_" + i
                    + ".xml";
            sink.text(relative, resources.drawable(i));
        }
        for (int i = 0; i < assets; i++) {
            String relative = base + "/src/main/assets/data_" + i + ".bin";
            Random rng = rngFor(tier, relative);
            sink.bytes(relative, resources.binaryAsset(
                    1024 + rng.nextInt(3072), rng));
        }
        String colorsRelative = base + "/src/main/res/values/colors.xml";
        sink.text(colorsRelative, resources.colors(
                4 + layoutRng.nextInt(5), rngFor(tier, colorsRelative)));
        sink.text(base + "/src/main/res/values/strings.xml",
                resources.strings(layoutIndexes));
    }

    /**
     * Répartition exacte d'un total : les premiers modules absorbent le
     * reste de la division (ordre stable).
     */
    private static int[] spread(int total, int count) {
        int[] result = new int[count];
        int base = total / count;
        int remainder = total % count;
        for (int i = 0; i < count; i++) {
            result[i] = base + (i < remainder ? 1 : 0);
        }
        return result;
    }

    /** Générateur semé par (graine du tier, chemin relatif). */
    private static Random rngFor(TierSpec tier, String relativePath) {
        return new Random(
                tier.seed ^ ModuleGraphPlanner.fnv64(relativePath));
    }

    private static int countDirs(Path root) throws IOException {
        int count = 0;
        List<Path> stack = new ArrayList<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            Path dir = stack.remove(stack.size() - 1);
            try (java.nio.file.DirectoryStream<Path> stream =
                         Files.newDirectoryStream(dir)) {
                for (Path child : stream) {
                    if (Files.isDirectory(child)) {
                        count++;
                        stack.add(child);
                    }
                }
            }
        }
        return count;
    }
}
