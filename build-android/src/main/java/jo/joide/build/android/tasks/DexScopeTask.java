package jo.joide.build.android.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Tâche de dexage d'un scope : le code d'un module du projet, un jar
 * de bibliothèque ou le classes.jar d'un AAR. Un scope par unité —
 * jamais un monolithe — parce que l'empreinte de la tâche est
 * fonction du contenu de ses entrées : deux projets partageant le
 * même cache de sorties et la même bibliothèque (même ancre, même
 * contenu) ne dexent qu'une fois. Le cache de dex est partageable
 * entre projets par construction.
 */
public final class DexScopeTask implements TaskDefinition {

    private final String taskName;
    private final String scopeLabel;
    private final List<RootedPath> program;
    private final List<RootedPath> classpath;
    private final int minApi;
    private final RootedPath outputDir;
    private final Dexer dexer;

    /**
     * @param moduleName nom du module porteur
     * @param scopeLabel libellé du scope (module, artefact)
     * @param program    entrées à dexer (répertoires de classes ou jars)
     * @param classpath  classpath de résolution
     * @param minApi     niveau d'API minimum
     * @param outputDir  répertoire de sortie du dex
     * @param dexer      service de dexage
     */
    public DexScopeTask(String moduleName, String scopeLabel,
                        List<RootedPath> program, List<RootedPath> classpath,
                        int minApi, RootedPath outputDir, Dexer dexer) {
        this.taskName = moduleName + ":dexScope[" + scopeLabel + "]";
        this.scopeLabel = scopeLabel;
        this.program = List.copyOf(program);
        this.classpath = List.copyOf(classpath);
        this.minApi = minApi;
        this.outputDir = Objects.requireNonNull(outputDir);
        this.dexer = Objects.requireNonNull(dexer);
    }

    @Override
    public String name() {
        return taskName;
    }

    @Override
    public String taskType() {
        return "dex";
    }

    @Override
    public TaskInputs inputs() {
        TaskInputs.Builder builder = TaskInputs.builder();
        for (int i = 0; i < program.size(); i++) {
            RootedPath entry = program.get(i);
            if (isJar(entry)) {
                builder.file(entry);
            } else {
                builder.dir(entry);
            }
        }
        builder.property("joide.dex.minApi", Integer.toString(minApi));
        return builder.build();
    }

    private static boolean isJar(RootedPath path) {
        return path.relative().endsWith(".jar");
    }

    @Override
    public TaskOutputs outputs() {
        return TaskOutputs.builder().dir(outputDir).build();
    }

    @Override
    public String toolVersion() {
        return dexer.toolVersion();
    }

    @Override
    public String optionsFingerprint() {
        return "dex-scope-v1";
    }

    @Override
    public ResourceClaim resourceClaim() {
        return new ResourceClaim(1, 256, false);
    }

    @Override
    public TaskExecution execute(TaskContext context)
            throws InterruptedException {
        Path out = context.resolve(outputDir);
        try {
            deleteTree(out);
            Files.createDirectories(out);
            List<Path> programPaths = new java.util.ArrayList<>();
            for (RootedPath entry : program) {
                programPaths.add(context.resolve(entry));
            }
            List<Path> classpathPaths = new java.util.ArrayList<>();
            for (RootedPath entry : classpath) {
                classpathPaths.add(context.resolve(entry));
            }
            dexer.dex(new Dexer.DexRequest(programPaths, classpathPaths,
                    minApi, out));
            return TaskExecution.ok(List.of(Problem.info("dex.scope",
                    "scope " + scopeLabel + " dexed")));
        } catch (IOException e) {
            return TaskExecution.failed(List.of(Problem.error("dex.failed",
                    "scope " + scopeLabel + " : " + e.getMessage())));
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
