package jo.joide.build.android.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jo.joide.build.android.aapt2.Aapt2;
import jo.joide.platform.Problem;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.ResourceClaim;
import jo.joide.tasks.TaskContext;
import jo.joide.tasks.TaskDefinition;
import jo.joide.tasks.TaskExecution;
import jo.joide.tasks.TaskInputs;
import jo.joide.tasks.TaskOutputs;

/**
 * Tâche de lien aapt2 : fichiers plats compilés + manifeste vers
 * l'archive liée (manifeste binaire + table de ressources), les
 * sources R.java et la table de symboles R.txt. Le lien est
 * global — toute modification de ressource reprend le lien entier,
 * mais le lien ne recompile pas les fichiers inchangés (la tâche de
 * compilation reste proportionnelle à la modification).
 */
public final class Aapt2LinkTask implements TaskDefinition {

    private final String moduleName;
    private final RootedPath manifest;
    private final List<RootedPath> flatDirs;
    private final RootedPath linkedApk;
    private final RootedPath javaDir;
    private final RootedPath rTxt;
    private final RootedPath protoDir;
    private final Path aapt2Binary;
    private final Path platformJar;
    private final String packageName;
    private final RootedPath assetsDir;

    /**
     * @param moduleName  nom du module
     * @param manifest    manifeste source
     * @param flatDirs    fichiers plats (sortie de compilation) :
     *                    les propres du module puis ceux des
     *                    bibliothèques pour le lien d'application
     * @param linkedApk   archive liée en sortie
     * @param javaDir     répertoire des sources R.java
     * @param rTxt        table de symboles en sortie
     * @param protoDir    répertoire des sorties proto (bundle)
     * @param aapt2Binary binaire aapt2
     * @param platformJar android.jar de la plateforme
     * @param packageName identifiant d'application (renommage)
     * @param assetsDir   répertoire d'actifs, ou {@code null}
     */
    public Aapt2LinkTask(String moduleName, RootedPath manifest,
                         List<RootedPath> flatDirs, RootedPath linkedApk,
                         RootedPath javaDir, RootedPath rTxt,
                         RootedPath protoDir, Path aapt2Binary,
                         Path platformJar, String packageName,
                         RootedPath assetsDir) {
        this.moduleName = Objects.requireNonNull(moduleName);
        this.manifest = Objects.requireNonNull(manifest);
        this.flatDirs = List.copyOf(flatDirs);
        this.linkedApk = Objects.requireNonNull(linkedApk);
        this.javaDir = Objects.requireNonNull(javaDir);
        this.rTxt = Objects.requireNonNull(rTxt);
        this.protoDir = Objects.requireNonNull(protoDir);
        this.aapt2Binary = Objects.requireNonNull(aapt2Binary);
        this.platformJar = Objects.requireNonNull(platformJar);
        this.packageName = packageName;
        this.assetsDir = assetsDir;
    }

    @Override
    public String name() {
        return moduleName + ":linkAndroidResources";
    }

    @Override
    public String taskType() {
        return "aapt2";
    }

    @Override
    public TaskInputs inputs() {
        TaskInputs.Builder builder = TaskInputs.builder();
        builder.file(manifest);
        for (RootedPath flatDir : flatDirs) {
            builder.dir(flatDir);
        }
        if (assetsDir != null) {
            builder.dir(assetsDir);
        }
        builder.property("joide.aapt2.platform", platformJar.toString());
        if (packageName != null) {
            builder.property("joide.aapt2.package", packageName);
        }
        return builder.build();
    }

    @Override
    public TaskOutputs outputs() {
        return TaskOutputs.builder()
                .file(linkedApk)
                .dir(javaDir)
                .file(rTxt)
                .dir(protoDir)
                .file(protoZip())
                .build();
    }

    /** @return le chemin ancré de l'archive proto (bundle) */
    public RootedPath protoZip() {
        return protoDir.parent().resolve(protoDir.fileName() + ".zip");
    }

    @Override
    public String toolVersion() {
        return "aapt2";
    }

    @Override
    public String optionsFingerprint() {
        return "link-v1" + (packageName == null ? "" : ":" + packageName);
    }

    @Override
    public ResourceClaim resourceClaim() {
        return new ResourceClaim(1, 256, false);
    }

    @Override
    public TaskExecution execute(TaskContext context)
            throws InterruptedException {
        Path apkOut = context.resolve(linkedApk);
        Path genDir = context.resolve(javaDir);
        Path protoOut = context.resolve(protoDir);
        try {
            Files.createDirectories(apkOut.getParent());
            if (genDir.getParent() != null) {
                Files.createDirectories(genDir.getParent());
            }
            Files.createDirectories(genDir);
            Files.createDirectories(protoOut);
            deleteTree(context.resolve(javaDir));
            Files.createDirectories(context.resolve(javaDir));
        } catch (IOException e) {
            return TaskExecution.failed(List.of(Problem.error("aapt2.io",
                    "cannot prepare output: " + e.getMessage())));
        }
        List<Path> flats = new ArrayList<>();
        for (RootedPath flatDir : flatDirs) {
            Path flatRoot = context.resolve(flatDir);
            if (!Files.isDirectory(flatRoot)) {
                continue;
            }
            try (var stream = Files.list(flatRoot)) {
                stream.filter(p -> p.getFileName() != null
                                && p.getFileName().toString()
                                        .endsWith(".flat"))
                        .sorted()
                        .forEach(flats::add);
            } catch (IOException e) {
                return TaskExecution.failed(List.of(Problem.error("aapt2.io",
                        "cannot list flat files: " + e.getMessage())));
            }
        }
        Aapt2 aapt2 = new Aapt2(aapt2Binary, 300);
        try {
            // lien binaire (APK)
            Aapt2.LinkOptions.Builder apkLink = Aapt2.LinkOptions.builder()
                    .manifest(context.resolve(manifest))
                    .platformJar(platformJar)
                    .output(apkOut)
                    .javaOut(genDir)
                    .rTxtOut(context.resolve(rTxt))
                    .autoAddOverlay(true);
            if (packageName != null) {
                apkLink.packageName(packageName);
            }
            if (assetsDir != null) {
                apkLink.assetsDir(context.resolve(assetsDir));
            }
            for (Path flat : flats) {
                apkLink.compiled(flat);
            }
            aapt2.link(apkLink.build());
            // lien proto (bundle)
            Path protoZip = context.resolve(protoZip());
            Aapt2.LinkOptions.Builder protoLink = Aapt2.LinkOptions.builder()
                    .manifest(context.resolve(manifest))
                    .platformJar(platformJar)
                    .output(protoZip)
                    .protoFormat(true)
                    .autoAddOverlay(true);
            if (packageName != null) {
                protoLink.packageName(packageName);
            }
            if (assetsDir != null) {
                protoLink.assetsDir(context.resolve(assetsDir));
            }
            for (Path flat : flats) {
                protoLink.compiled(flat);
            }
            aapt2.link(protoLink.build());
            return TaskExecution.ok(List.of(Problem.info("aapt2.link",
                    flats.size() + " flat file(s) linked")));
        } catch (IOException e) {
            String message = e instanceof Aapt2.Aapt2Exception failure
                    ? failure.result.stderr : e.getMessage();
            return TaskExecution.failed(List.of(Problem.error("aapt2.link",
                    "link failed: " + message)));
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
