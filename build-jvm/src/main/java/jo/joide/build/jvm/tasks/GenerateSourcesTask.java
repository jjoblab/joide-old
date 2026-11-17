package jo.joide.build.jvm.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import jo.joide.platform.Problem;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.TaskContext;
import jo.joide.tasks.TaskDefinition;
import jo.joide.tasks.TaskExecution;
import jo.joide.tasks.TaskInputs;
import jo.joide.tasks.TaskOutputs;

/**
 * Tâche generateSources : exécute les générateurs injectés (SPI
 * {@link SourceGenerator}) dans un répertoire de sortie dédié, vide
 * au départ — la sortie est une fonction des générateurs, jamais de
 * l'état précédent.
 */
public final class GenerateSourcesTask implements TaskDefinition {

    private final String moduleName;
    private final List<SourceGenerator> generators;
    private final RootedPath outputDir;

    /**
     * @param moduleName nom du module
     * @param generators générateurs injectés, ordre stable
     * @param outputDir  répertoire de sortie
     */
    public GenerateSourcesTask(String moduleName,
                               List<SourceGenerator> generators,
                               RootedPath outputDir) {
        this.moduleName = moduleName;
        this.generators = List.copyOf(generators);
        this.outputDir = outputDir;
    }

    @Override
    public String name() {
        return moduleName + ":generateSources";
    }

    @Override
    public String taskType() {
        return "generate";
    }

    @Override
    public TaskInputs inputs() {
        TaskInputs.Builder builder = TaskInputs.builder();
        StringBuilder identity = new StringBuilder();
        for (SourceGenerator generator : generators) {
            identity.append(generator.name()).append(':')
                    .append(generator.version()).append('|');
        }
        builder.property("joide.generators", identity.toString());
        return builder.build();
    }

    @Override
    public TaskOutputs outputs() {
        return TaskOutputs.builder().dir(outputDir).build();
    }

    @Override
    public String toolVersion() {
        return "joide-generate-1";
    }

    @Override
    public String optionsFingerprint() {
        StringBuilder identity = new StringBuilder();
        for (SourceGenerator generator : generators) {
            identity.append(generator.name()).append(':')
                    .append(generator.version()).append('|');
        }
        return identity.toString();
    }

    @Override
    public TaskExecution execute(TaskContext context) {
        try {
            Path out = context.resolve(outputDir);
            deleteTree(out);
            Files.createDirectories(out);
            List<Problem> warnings = new ArrayList<>();
            for (SourceGenerator generator : generators) {
                List<String> written = generator.generate(
                        new SourceGenerator.Context() {
                            @Override
                            public Path outputDir() {
                                return out;
                            }
                        });
                if (written == null) {
                    warnings.add(Problem.warn("generator-no-listing",
                            "generator " + generator.name()
                                    + " returned no file listing"));
                }
            }
            return TaskExecution.ok(warnings);
        } catch (Exception e) {
            return TaskExecution.failed(List.of(Problem.error(
                    "generator-error",
                    "module " + moduleName + " generation failed: "
                            + e.getMessage())));
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            List<Path> paths = new ArrayList<>();
            stream.forEach(paths::add);
            paths.sort(java.util.Collections.reverseOrder());
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    /** Inventaire trié (tests). */
    static TreeMap<String, Path> inventory(Path dir) throws IOException {
        TreeMap<String, Path> files = new TreeMap<>();
        if (!Files.isDirectory(dir)) {
            return files;
        }
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile).forEach(
                    p -> files.put(dir.relativize(p).toString()
                            .replace('\\', '/'), p));
        }
        return files;
    }
}
