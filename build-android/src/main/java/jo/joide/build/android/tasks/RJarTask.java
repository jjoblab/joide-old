package jo.joide.build.android.tasks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jo.joide.build.jvm.abi.AbiManifest;
import jo.joide.build.jvm.abi.EntryAbi;
import jo.joide.platform.Problem;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.ResourceClaim;
import jo.joide.tasks.TaskContext;
import jo.joide.tasks.TaskDefinition;
import jo.joide.tasks.TaskExecution;
import jo.joide.tasks.TaskInputs;
import jo.joide.tasks.TaskOutputs;

/**
 * Tâche de fabrication du jar des classes R en BYTECODE direct (la
 * manière AGP/CodeAssist) : les sources R.java générées par le lien
 * aapt2 sont PARSED et leurs constantes émises en class files faits
 * main ({@link RBytecode}) — AUCUN compiler Java n'intervient. Le jar
 * entre au classpath de compilation du module et à son dex.
 *
 * <p>L'histoire : la tâche compilait le R.java par ecj (des constantes
 * entières, aucun compilateur n'était nécessaire) et le terrain 2026-
 * 11-18 a tranché — sur ART, chaque diagnostic d'ecj réveille son
 * bundle de messages, ABSENT du dex (« ExceptionInInitializerError:
 * Missing message ») ; et à l'échelle CodeAssist, un R.java de
 * millions de lignes OOM le compiler à tas plafonné. Le R.jar d'AGP
 * est un artefact de bytecode, jamais un artefact de compilation.</p>
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
     * @param classesDir      répertoire des classes émises
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
        return "rjar-bytecode-v2";
    }

    @Override
    public String optionsFingerprint() {
        return "rjar-bytecode-v2";
    }

    @Override
    public ResourceClaim resourceClaim() {
        return new ResourceClaim(1, 64, false);
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
            int classesEmissions = 0;
            for (Path javaFile : javaFiles) {
                RBytecode.ModeleR modele = RBytecode.parse(Files
                        .readString(javaFile, StandardCharsets.UTF_8));
                classesEmissions += RBytecode.emettre(modele, classes);
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
                    classesEmissions + " classe(s) émise(s) depuis "
                            + javaFiles.size() + " R.java")));
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
