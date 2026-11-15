package jo.joide.build.android.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jo.joide.build.jvm.abi.AbiManifest;
import jo.joide.build.jvm.abi.EntryAbi;
import jo.joide.build.jvm.compile.CompileRequest;
import jo.joide.build.jvm.compile.CompileResult;
import jo.joide.build.jvm.compile.EcjCompilerDriver;
import jo.joide.platform.Problem;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.ResourceClaim;
import jo.joide.tasks.TaskContext;
import jo.joide.tasks.TaskDefinition;
import jo.joide.tasks.TaskExecution;
import jo.joide.tasks.TaskInputs;
import jo.joide.tasks.TaskOutputs;

/**
 * Tâche de fabrication du jar des classes R : les sources R.java
 * générées par le lien aapt2 sont compilées par ecj (aucune
 * dépendance externe — des constantes entières) puis archivées en jar
 * déterministe. Le jar entre au classpath de compilation du module et
 * à son dex.
 */
public final class RJarTask implements TaskDefinition {

    private final String moduleName;
    private final RootedPath javaDir;
    private final RootedPath classesDir;
    private final RootedPath rJar;
    private final RootedPath abiManifestFile;

    /**
     * @param moduleName      nom du module
     * @param javaDir         répertoire des sources R.java (sortie du
     *                        lien)
     * @param classesDir      répertoire de compilation
     * @param rJar            jar produit
     * @param abiManifestFile manifeste ABI stable du jar (entrée du
     *                        classpath de compilation aval)
     */
    public RJarTask(String moduleName, RootedPath javaDir,
                    RootedPath classesDir, RootedPath rJar,
                    RootedPath abiManifestFile) {
        this.moduleName = Objects.requireNonNull(moduleName);
        this.javaDir = Objects.requireNonNull(javaDir);
        this.classesDir = Objects.requireNonNull(classesDir);
        this.rJar = Objects.requireNonNull(rJar);
        this.abiManifestFile = Objects.requireNonNull(abiManifestFile);
    }

    @Override
    public String name() {
        return moduleName + ":generateRJar";
    }

    @Override
    public String taskType() {
        return "compile";
    }

    @Override
    public TaskInputs inputs() {
        return TaskInputs.builder().dir(javaDir).build();
    }

    @Override
    public TaskOutputs outputs() {
        // le manifeste ABI est une sortie comme les autres : une
        // restauration du cache doit le remettre sur le disque (la
        // compilation aval le lit comme entrée)
        return TaskOutputs.builder().dir(classesDir).file(rJar)
                .file(abiManifestFile).build();
    }

    @Override
    public String toolVersion() {
        return new EcjCompilerDriver().toolVersion();
    }

    @Override
    public String optionsFingerprint() {
        return "rjar-v1";
    }

    @Override
    public ResourceClaim resourceClaim() {
        return new ResourceClaim(1, 128, false);
    }

    @Override
    public TaskExecution execute(TaskContext context)
            throws InterruptedException {
        Path sources = context.resolve(javaDir);
        Path classes = context.resolve(classesDir);
        Path jar = context.resolve(rJar);
        try {
            deleteTree(classes);
            Files.createDirectories(classes);
            if (jar.getParent() != null) {
                Files.createDirectories(jar.getParent());
            }
            List<Path> javaFiles = new ArrayList<>();
            collectJava(sources, javaFiles);
            if (!javaFiles.isEmpty()) {
                EcjCompilerDriver driver = new EcjCompilerDriver();
                CompileRequest.Builder request = CompileRequest.builder()
                        .outputDir(classes)
                        .sourceLevel("17");
                for (Path javaFile : javaFiles) {
                    request.source(javaFile);
                }
                CompileResult result = driver.compile(request.build());
                if (!result.success) {
                    return TaskExecution.failed(result.diagnostics);
                }
            }
            JarWriter.write(classes, jar);
            // manifeste ABI stable : la compilation aval lit ce
            // fichier comme entrée d'empreinte (le jar n'existait pas
            // à la construction du graphe). Le calcul est pur — pas
            // d'index partagé, donc pas de course entre modules.
            AbiManifest manifest = EntryAbi.compute(jar, 200_000);
            Path manifestOut = context.resolve(abiManifestFile);
            if (manifestOut.getParent() != null) {
                Files.createDirectories(manifestOut.getParent());
            }
            Files.writeString(manifestOut, manifest.text());
            return TaskExecution.ok(List.of(Problem.info("r.jar",
                    javaFiles.size() + " source(s) compiled")));
        } catch (IOException e) {
            return TaskExecution.failed(List.of(Problem.error("rjar.io",
                    e.getMessage())));
        }
    }

    private static void collectJava(Path dir, List<Path> sink)
            throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.filter(p -> p.getFileName() != null
                    && p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .forEach(sink::add);
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
