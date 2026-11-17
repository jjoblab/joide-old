package jo.joide.build.jvm.tasks;

import java.util.ArrayList;
import java.util.List;

import jo.joide.build.jvm.compile.CompilerDriver;
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
 * Tâche de compilation d'un langage de l'un des modules du pipeline
 * JVM : Java (ecj) ou Kotlin (compilateur embarqué), incrémentale
 * par ABI, sorties = répertoire de classes + manifestes ABI.
 */
public final class JvmCompileTask implements TaskDefinition {

    private final IncrementalJvmCompile core;
    private final String taskName;
    private final String taskType;

    private JvmCompileTask(IncrementalJvmCompile core, String taskName,
                           String taskType) {
        this.core = core;
        this.taskName = taskName;
        this.taskType = taskType;
    }

    /**
     * @param moduleName    nom du module
     * @param language      « java » ou « kotlin »
     * @param sourceRoots   racines de sources (les deux langages y sont
     *                      scannés par extension)
     * @param classpath     entrées de classpath (modules + externes)
     * @param driver        pilote du compilateur
     * @param javaRoots     racines Java pour la résolution Kotlin
     * @param outputDir     répertoire de sortie des classes
     * @param abiDir        répertoire des manifestes ABI
     * @param javaStateFile état Java du même module (compilation jointe)
     * @param sourceLevel   niveau de source/cible
     * @return la tâche construite
     */
    public static JvmCompileTask create(String moduleName, String language,
                                        List<RootedPath> sourceRoots,
                                        List<ClasspathEntry> classpath,
                                        CompilerDriver driver,
                                        List<RootedPath> javaRoots,
                                        RootedPath outputDir,
                                        RootedPath abiDir,
                                        RootedPath javaStateFile,
                                        String sourceLevel) {
        boolean kotlin = "kotlin".equals(language);
        IncrementalJvmCompile core = new IncrementalJvmCompile(
                moduleName, language, kotlin ? "kt" : "java",
                sourceRoots, classpath, driver,
                kotlin ? javaRoots : List.of(),
                outputDir, abiDir, javaStateFile, sourceLevel, "UTF-8",
                0.30, false);
        return new JvmCompileTask(core,
                moduleName + ":compile" + (kotlin ? "Kotlin" : "Java"),
                "compile");
    }

    /** @return le cœur incrémental (tests) */
    IncrementalJvmCompile core() {
        return core;
    }

    @Override
    public String name() {
        return taskName;
    }

    @Override
    public String taskType() {
        return taskType;
    }

    @Override
    public TaskInputs inputs() {
        TaskInputs.Builder builder = TaskInputs.builder();
        for (RootedPath root : core.sourceRoots()) {
            builder.dir(root);
        }
        for (ClasspathEntry entry : core.classpathEntries()) {
            builder.file(entry.manifestInput);
        }
        builder.property("joide.jvm.spec", core.spec());
        return builder.build();
    }

    @Override
    public TaskOutputs outputs() {
        return TaskOutputs.builder()
                .dir(core.classesOutput())
                .dir(core.abiOutput())
                .build();
    }

    @Override
    public String toolVersion() {
        return core.toolVersion();
    }

    @Override
    public String optionsFingerprint() {
        return core.spec();
    }

    @Override
    public ResourceClaim resourceClaim() {
        return new ResourceClaim(2, 320, false);
    }

    @Override
    public TaskExecution execute(TaskContext context) {
        InputDelta delta = context.inputDelta();
        if (delta.size() > 4000) {
            // garde-fou : un raz-de-marée de changements vaut un
            // repli complet explicite plutôt qu'un lot énorme
            return core.executeForcedFull(context);
        }
        return core.execute(context);
    }

    /** Diagnostics vides : la classe existe pour la lisibilité. */
    static List<Problem> noWarnings() {
        return new ArrayList<>();
    }
}
