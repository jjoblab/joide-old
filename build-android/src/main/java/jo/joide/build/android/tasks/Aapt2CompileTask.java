package jo.joide.build.android.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

import jo.joide.build.android.aapt2.Aapt2;
import jo.joide.platform.Problem;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.InputDelta;
import jo.joide.tasks.ResourceClaim;
import jo.joide.tasks.TaskContext;
import jo.joide.tasks.TaskDefinition;
import jo.joide.tasks.TaskExecution;
import jo.joide.tasks.TaskInputs;
import jo.joide.tasks.TaskOutputs;

/**
 * Tâche de compilation aapt2 : chaque fichier de ressource modifié
 * est recompilé seul en fichier plat — le travail est proportionnel à
 * la modification, jamais à la taille du projet. Les suppressions
 * retirent le fichier plat correspondant ; les renommements sont un
 * retrait suivi d'une addition. La première exécution compile tout.
 */
public final class Aapt2CompileTask implements TaskDefinition {

    private final String moduleName;
    private final List<RootedPath> resourceRoots;
    private final RootedPath flatDir;
    private final Path aapt2Binary;

    /**
     * @param moduleName    nom du module
     * @param resourceRoots racines de ressources
     * @param flatDir       répertoire des fichiers plats
     * @param aapt2Binary   binaire aapt2 (chemin absolu)
     */
    public Aapt2CompileTask(String moduleName, List<RootedPath> resourceRoots,
                            RootedPath flatDir, Path aapt2Binary) {
        this.moduleName = Objects.requireNonNull(moduleName);
        this.resourceRoots = List.copyOf(resourceRoots);
        this.flatDir = Objects.requireNonNull(flatDir);
        this.aapt2Binary = Objects.requireNonNull(aapt2Binary);
    }

    @Override
    public String name() {
        return moduleName + ":processAndroidResources";
    }

    @Override
    public String taskType() {
        return "aapt2";
    }

    @Override
    public TaskInputs inputs() {
        TaskInputs.Builder builder = TaskInputs.builder();
        for (RootedPath root : resourceRoots) {
            builder.dir(root);
        }
        return builder.build();
    }

    @Override
    public TaskOutputs outputs() {
        return TaskOutputs.builder().dir(flatDir).build();
    }

    @Override
    public String toolVersion() {
        return "aapt2";
    }

    @Override
    public String optionsFingerprint() {
        return "flat-v1";
    }

    @Override
    public ResourceClaim resourceClaim() {
        return new ResourceClaim(1, 128, false);
    }

    @Override
    public TaskExecution execute(TaskContext context)
            throws InterruptedException {
        Path outDir = context.resolve(flatDir);
        try {
            Files.createDirectories(outDir);
        } catch (IOException e) {
            return TaskExecution.failed(List.of(Problem.error("aapt2.io",
                    "cannot create output dir: " + e.getMessage())));
        }
        InputDelta delta = context.inputDelta();
        List<Path> changed = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        boolean cold = !hasFlat(outDir);
        if (!cold && delta != null) {
            collectDelta(context, delta, changed, removed);
        }
        if (cold) {
            // première exécution : tout compiler
            for (RootedPath root : resourceRoots) {
                Path dir = context.resolve(root);
                collectAll(dir, dir, changed);
            }
        }
        Aapt2 aapt2 = new Aapt2(aapt2Binary, 120);
        int compiled = 0;
        for (Path resource : changed) {
            if (context.cancellation().isCancelled()) {
                return TaskExecution.failed(List.of(Problem.error(
                        "aapt2.cancelled", "cancelled")));
            }
            try {
                aapt2.compileFile(resource, outDir);
                compiled++;
            } catch (IOException e) {
                return TaskExecution.failed(List.of(Problem.error(
                        "aapt2.failed", aapt2Message(e))));
            }
        }
        for (String name : removed) {
            try {
                Files.deleteIfExists(outDir.resolve(name));
            } catch (IOException e) {
                return TaskExecution.failed(List.of(Problem.error(
                        "aapt2.io", "cannot remove stale flat: "
                                + e.getMessage())));
            }
        }
        int total = countFlat(outDir);
        return TaskExecution.ok(List.of(Problem.info("aapt2.compile",
                compiled + " file(s) compiled, " + total
                        + " flat total")));
    }

    private void collectDelta(TaskContext context, InputDelta delta,
                              List<Path> changed, List<String> removed) {
        for (RootedPath path : delta.added()) {
            if (insideRoots(path) != null && isResourceFile(insideRoots(path))) {
                changed.add(context.resolve(path));
            }
        }
        for (RootedPath path : delta.modified()) {
            if (insideRoots(path) != null && isResourceFile(insideRoots(path))) {
                changed.add(context.resolve(path));
            }
        }
        for (RootedPath path : delta.removed()) {
            String inside = insideRoots(path);
            if (inside != null && isResourceFile(inside)) {
                removed.add(flatName(inside));
            }
        }
    }

    private String insideRoots(RootedPath path) {
        for (RootedPath root : resourceRoots) {
            if (!path.root().equals(root.root())) {
                continue;
            }
            String relative = path.relative();
            String rootRelative = root.relative();
            boolean inside = rootRelative.isEmpty()
                    || relative.equals(rootRelative)
                    || relative.startsWith(rootRelative + "/");
            if (inside) {
                if (rootRelative.isEmpty()) {
                    return relative;
                }
                return relative.equals(rootRelative) ? null
                        : relative.substring(rootRelative.length() + 1);
            }
        }
        return null;
    }

    private static void collectAll(Path root, Path dir, List<Path> sink) {
        try (var stream = Files.list(dir)) {
            for (Path child : (Iterable<Path>) stream::iterator) {
                if (Files.isDirectory(child)) {
                    collectAll(root, child, sink);
                } else {
                    String relative = root.relativize(child).toString()
                            .replace('\\', '/');
                    if (isResourceFile(relative)) {
                        sink.add(child);
                    }
                }
            }
        } catch (IOException e) {
            // l'arbre est rescanné par le moteur : silence si disparu
        }
    }

    private static boolean isResourceFile(String relative) {
        return relative.endsWith(".xml") || relative.endsWith(".png")
                || relative.endsWith(".jpg") || relative.endsWith(".gif")
                || relative.endsWith(".webp") || relative.endsWith(".9.png");
    }

    /**
     * Nom de fichier plat qu'aapt2 produit pour un fichier de
     * ressource donné.
     *
     * @param inside chemin relatif à la racine des ressources
     * @return le nom du fichier plat
     */
    static String flatName(String inside) {
        String flat = inside.replace('/', '_');
        if (flat.endsWith(".xml")) {
            return flat.substring(0, flat.length() - 4) + ".flat";
        }
        return flat + ".flat";
    }

    private static boolean hasFlat(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (var stream = Files.list(dir)) {
            return stream.anyMatch(p -> p.getFileName() != null
                    && p.getFileName().toString().endsWith(".flat"));
        } catch (IOException e) {
            return false;
        }
    }

    private static int countFlat(Path dir) {
        try (var stream = Files.list(dir)) {
            return (int) stream.filter(p -> p.getFileName() != null
                    && p.getFileName().toString().endsWith(".flat")).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private static String aapt2Message(IOException e) {
        if (e instanceof Aapt2.Aapt2Exception failure) {
            return "aapt2 failed:\n" + failure.result.stderr;
        }
        return "aapt2 failed: " + e.getMessage();
    }
}
