package jo.joide.build.android.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import jo.joide.build.android.packaging.ApkWriter;
import jo.joide.platform.Problem;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.ResourceClaim;
import jo.joide.tasks.TaskContext;
import jo.joide.tasks.TaskDefinition;
import jo.joide.tasks.TaskExecution;
import jo.joide.tasks.TaskInputs;
import jo.joide.tasks.TaskOutputs;

/**
 * Tâche de package : assemble l'APK non signé depuis l'archive liée
 * aapt2 (manifeste binaire, table de ressources, ressources), les dex
 * fusionnés, les actifs et les bibliothèques natives des AAR. La
 * table de ressources et les dex sont écrits non compressés et
 * alignés (lecture directe en mémoire) ; les autres entrées sont
 * compressées. L'écriture est déterministe : mêmes entrées, mêmes
 * octets.
 */
public final class PackageTask implements TaskDefinition {

    private final String moduleName;
    private final String variantName;
    private final RootedPath linkedApk;
    private final RootedPath dexDir;
    private final RootedPath nativeDir;
    private final RootedPath outputApk;

    /**
     * @param moduleName  nom du module
     * @param variantName nom de la variante
     * @param linkedApk   archive liée aapt2
     * @param dexDir      répertoire des classes.dex fusionnés
     * @param nativeDir   répertoire des bibliothèques natives
     *                    (chemins jniabi/*.so), ou {@code null}
     * @param outputApk   APK non signé en sortie
     */
    public PackageTask(String moduleName, String variantName,
                       RootedPath linkedApk, RootedPath dexDir,
                       RootedPath nativeDir, RootedPath outputApk) {
        this.moduleName = Objects.requireNonNull(moduleName);
        this.variantName = Objects.requireNonNull(variantName);
        this.linkedApk = Objects.requireNonNull(linkedApk);
        this.dexDir = Objects.requireNonNull(dexDir);
        this.nativeDir = nativeDir;
        this.outputApk = Objects.requireNonNull(outputApk);
    }

    @Override
    public String name() {
        return moduleName + ":package[" + variantName + "]";
    }

    @Override
    public String taskType() {
        return "package";
    }

    @Override
    public TaskInputs inputs() {
        TaskInputs.Builder builder = TaskInputs.builder();
        builder.file(linkedApk);
        builder.dir(dexDir);
        if (nativeDir != null) {
            builder.dir(nativeDir);
        }
        return builder.build();
    }

    @Override
    public TaskOutputs outputs() {
        return TaskOutputs.builder().file(outputApk).build();
    }

    @Override
    public String toolVersion() {
        return "joide-package-1";
    }

    @Override
    public String optionsFingerprint() {
        return "package-v1:" + variantName;
    }

    @Override
    public ResourceClaim resourceClaim() {
        return new ResourceClaim(1, 256, false);
    }

    @Override
    public TaskExecution execute(TaskContext context)
            throws InterruptedException {
        Path apk = context.resolve(outputApk);
        try {
            ApkWriter writer = new ApkWriter();
            // entrées de l'archive liée : manifeste et ressources
            try (ZipFile linked = new ZipFile(
                    context.resolve(linkedApk).toFile())) {
                var entries = linked.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    byte[] content = linked.getInputStream(entry)
                            .readAllBytes();
                    String name = entry.getName();
                    if (name.equals("resources.arsc")) {
                        writer.stored(name, content,
                                ApkWriter.ALIGN_DEFAULT);
                    } else if (name.equals("AndroidManifest.xml")) {
                        writer.deflated(name, content);
                    } else {
                        writer.deflated(name, content);
                    }
                }
            }
            // dex : non compressés et alignés
            try (var stream = Files.list(context.resolve(dexDir))) {
                List<Path> dexes = new java.util.ArrayList<>();
                stream.filter(p -> p.getFileName() != null
                        && p.getFileName().toString().endsWith(".dex"))
                        .sorted()
                        .forEach(dexes::add);
                for (Path dex : dexes) {
                    writer.storedFile(dex.getFileName().toString(), dex,
                            ApkWriter.ALIGN_DEFAULT);
                }
            }
            // bibliothèques natives : non compressées, alignées page
            if (nativeDir != null) {
                Path root = context.resolve(nativeDir);
                if (Files.isDirectory(root)) {
                    try (var walk = Files.walk(root)) {
                        walk.filter(Files::isRegularFile)
                                .sorted()
                                .forEach(lib -> {
                                    String name = root.relativize(lib)
                                            .toString().replace('\\', '/');
                                    try {
                                        writer.storedFile("lib/" + name, lib,
                                                ApkWriter.ALIGN_NATIVE);
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                    }
                }
            }
            writer.writeTo(apk);
            return TaskExecution.ok(List.of(Problem.info("package.apk",
                    "apk written: " + context.resolve(outputApk)
                            .getFileName())));
        } catch (IOException | RuntimeException e) {
            return TaskExecution.failed(List.of(Problem.error("package.io",
                    e.getMessage())));
        }
    }
}
