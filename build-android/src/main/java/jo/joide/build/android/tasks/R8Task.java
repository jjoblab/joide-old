package jo.joide.build.android.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jo.joide.build.android.dex.Dexer;
import jo.joide.platform.Problem;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.ResourceClaim;
import jo.joide.tasks.TaskContext;
import jo.joide.tasks.TaskDefinition;
import jo.joide.tasks.TaskExecution;
import jo.joide.tasks.TaskInputs;
import jo.joide.tasks.TaskOutputs;

/**
 * Tâche de rétrécissement R8 : les classes du module et ses jars
 * externes passent par R8 avec les règles de conservation de
 * l'application (point d'entrée, classes référencées par réflexion
 * dans le manifeste). Le résultat remplace les dex avant le package.
 * La fusion de la variante release consomme ces dex rétrécis.
 */
public final class R8Task implements TaskDefinition {

    private final String moduleName;
    private final String variantName;
    private final List<RootedPath> program;
    private final List<RootedPath> classpath;
    private final List<String> keepRules;
    private final int minApi;
    private final RootedPath outputDir;
    private final RootedPath mappingFile;
    private final Path platformJar;
    private final Dexer dexer;

    /**
     * @param moduleName  nom du module
     * @param variantName nom de la variante
     * @param program     entrées à rétrécir (classes du module, jars)
     * @param classpath   bibliothèques
     * @param keepRules   règles de conservation
     * @param minApi      niveau d'API minimum
     * @param outputDir   répertoire des dex rétrécis
     * @param mappingFile mapping de renommage en sortie
     * @param platformJar android.jar de la plateforme
     * @param dexer       service de rétrécissement
     */
    public R8Task(String moduleName, String variantName,
                  List<RootedPath> program, List<RootedPath> classpath,
                  List<String> keepRules, int minApi, RootedPath outputDir,
                  RootedPath mappingFile, Path platformJar, Dexer dexer) {
        this.moduleName = Objects.requireNonNull(moduleName);
        this.variantName = Objects.requireNonNull(variantName);
        this.program = List.copyOf(program);
        this.classpath = List.copyOf(classpath);
        this.keepRules = List.copyOf(keepRules);
        this.minApi = minApi;
        this.outputDir = Objects.requireNonNull(outputDir);
        this.mappingFile = mappingFile;
        this.platformJar = Objects.requireNonNull(platformJar);
        this.dexer = Objects.requireNonNull(dexer);
    }

    @Override
    public String name() {
        return moduleName + ":minify[" + variantName + "]";
    }

    @Override
    public String taskType() {
        return "dex";
    }

    @Override
    public TaskInputs inputs() {
        TaskInputs.Builder builder = TaskInputs.builder();
        for (RootedPath entry : program) {
            if (entry.relative().endsWith(".jar")) {
                builder.file(entry);
            } else {
                builder.dir(entry);
            }
        }
        builder.property("joide.r8.minApi", Integer.toString(minApi));
        builder.property("joide.r8.rules", String.join(";", keepRules));
        return builder.build();
    }

    @Override
    public TaskOutputs outputs() {
        TaskOutputs.Builder builder = TaskOutputs.builder().dir(outputDir);
        if (mappingFile != null) {
            builder.file(mappingFile);
        }
        return builder.build();
    }

    @Override
    public String toolVersion() {
        return dexer.toolVersion();
    }

    @Override
    public String optionsFingerprint() {
        return "r8-v1:" + String.join(";", keepRules);
    }

    @Override
    public ResourceClaim resourceClaim() {
        return new ResourceClaim(2, 1024, false);
    }

    @Override
    public TaskExecution execute(TaskContext context)
            throws InterruptedException {
        Path out = context.resolve(outputDir);
        try {
            deleteTree(out);
            Files.createDirectories(out);
            List<Path> programPaths = new ArrayList<>();
            for (RootedPath entry : program) {
                programPaths.add(context.resolve(entry));
            }
            List<Path> libPaths = new ArrayList<>();
            libPaths.add(platformJar);
            for (RootedPath entry : classpath) {
                libPaths.add(context.resolve(entry));
            }
            dexer.shrink(new Dexer.ShrinkRequest(programPaths, libPaths,
                    minApi, keepRules, out,
                    mappingFile == null ? null
                            : context.resolve(mappingFile)));
            return TaskExecution.ok(List.of(Problem.info("r8.ok",
                    "shrink complete: " + out.getFileName())));
        } catch (IOException | RuntimeException e) {
            return TaskExecution.failed(List.of(Problem.error("r8.failed",
                    e.getMessage())));
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
