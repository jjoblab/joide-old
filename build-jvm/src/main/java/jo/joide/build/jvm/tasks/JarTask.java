package jo.joide.build.jvm.tasks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jo.joide.platform.Problem;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.TaskContext;
import jo.joide.tasks.TaskDefinition;
import jo.joide.tasks.TaskExecution;
import jo.joide.tasks.TaskInputs;
import jo.joide.tasks.TaskOutputs;

/**
 * Tâche jar déterministe : classes et ressources d'un module,
 * entrées triées, horodatage fixe (1980-01-01, plancher du format
 * zip), manifeste minimal constant — deux builds égaux donnent le
 * même jar octet pour octet.
 */
public final class JarTask implements TaskDefinition {

    /** Horodatage fixe des entrées (époche zip). */
    private static final long FIXED_TIME = 315532800000L; // 1980-01-01

    private final String moduleName;
    private final List<RootedPath> classesDirs;
    private final List<RootedPath> resourceDirs;
    private final RootedPath outputFile;

    /**
     * @param moduleName  nom du module
     * @param classesDirs répertoires de classes (Java puis Kotlin)
     * @param resourceDirs répertoires de ressources
     * @param outputFile  jar produit
     */
    public JarTask(String moduleName, List<RootedPath> classesDirs,
                   List<RootedPath> resourceDirs, RootedPath outputFile) {
        this.moduleName = moduleName;
        this.classesDirs = List.copyOf(classesDirs);
        this.resourceDirs = List.copyOf(resourceDirs);
        this.outputFile = outputFile;
    }

    @Override
    public String name() {
        return moduleName + ":jar";
    }

    @Override
    public String taskType() {
        return "jar";
    }

    @Override
    public TaskInputs inputs() {
        TaskInputs.Builder builder = TaskInputs.builder();
        for (RootedPath dir : classesDirs) {
            builder.dir(dir);
        }
        for (RootedPath dir : resourceDirs) {
            builder.dir(dir);
        }
        return builder.build();
    }

    @Override
    public TaskOutputs outputs() {
        return TaskOutputs.builder().file(outputFile).build();
    }

    @Override
    public String toolVersion() {
        return "joide-jar-1";
    }

    @Override
    public String optionsFingerprint() {
        return "fixed-time-deflate";
    }

    @Override
    public TaskExecution execute(TaskContext context) {
        try {
            Map<String, Path> entries = new TreeMap<>();
            for (RootedPath dir : classesDirs) {
                collect(context.resolve(dir), context.resolve(dir),
                        entries);
            }
            for (RootedPath dir : resourceDirs) {
                collect(context.resolve(dir), context.resolve(dir),
                        entries);
            }
            Path target = context.resolve(outputFile);
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(
                    target.getFileName() + ".tmp");
            try (ZipOutputStream zip = new ZipOutputStream(
                    Files.newOutputStream(tmp))) {
                zip.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION);
                ZipEntry manifest = new ZipEntry("META-INF/MANIFEST.MF");
                manifest.setTime(FIXED_TIME);
                zip.putNextEntry(manifest);
                zip.write("Manifest-Version: 1.0\r\nCreated-By: joide\r\n"
                        .getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                for (Map.Entry<String, Path> entry : entries.entrySet()) {
                    ZipEntry zipEntry = new ZipEntry(entry.getKey());
                    zipEntry.setTime(FIXED_TIME);
                    zip.putNextEntry(zipEntry);
                    zip.write(Files.readAllBytes(entry.getValue()));
                    zip.closeEntry();
                }
            }
            try {
                Files.move(tmp, target,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, target,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return TaskExecution.ok();
        } catch (IOException e) {
            return TaskExecution.failed(List.of(Problem.error(
                    "jar-error",
                    "module " + moduleName + " jar failed: "
                            + e.getMessage())));
        }
    }

    private static void collect(Path root, Path dir,
                                Map<String, Path> entries) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            List<Path> children = new ArrayList<>(stream.toList());
            children.sort(null);
            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    collect(root, child, entries);
                } else {
                    entries.put(root.relativize(child).toString()
                            .replace('\\', '/'), child);
                }
            }
        }
    }
}
