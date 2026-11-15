package jo.joide.build.jvm.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import jo.joide.platform.Problem;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.TaskContext;
import jo.joide.tasks.TaskDefinition;
import jo.joide.tasks.TaskExecution;
import jo.joide.tasks.TaskInputs;
import jo.joide.tasks.TaskOutputs;

/**
 * Tâche processResources : copie ordonnée des racines de ressources
 * vers le répertoire de sortie du module (première racine gagne en
 * cas de doublon, ordre de déclaration documenté).
 */
public final class ProcessResourcesTask implements TaskDefinition {

    private final String moduleName;
    private final List<RootedPath> resourceRoots;
    private final RootedPath outputDir;

    /**
     * @param moduleName    nom du module
     * @param resourceRoots racines de ressources, en ordre de priorité
     * @param outputDir     répertoire de sortie
     */
    public ProcessResourcesTask(String moduleName,
                                List<RootedPath> resourceRoots,
                                RootedPath outputDir) {
        this.moduleName = moduleName;
        this.resourceRoots = List.copyOf(resourceRoots);
        this.outputDir = outputDir;
    }

    @Override
    public String name() {
        return moduleName + ":processResources";
    }

    @Override
    public String taskType() {
        return "resources";
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
        return TaskOutputs.builder().dir(outputDir).build();
    }

    @Override
    public String toolVersion() {
        return "joide-resources-1";
    }

    @Override
    public String optionsFingerprint() {
        return "plain-copy-first-wins";
    }

    @Override
    public TaskExecution execute(TaskContext context) {
        try {
            Path out = context.resolve(outputDir);
            Files.createDirectories(out);
            for (RootedPath root : resourceRoots) {
                Path dir = context.resolve(root);
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (var stream = Files.walk(dir)) {
                    List<Path> files = stream
                            .filter(Files::isRegularFile).sorted().toList();
                    for (Path file : files) {
                        Path relative = dir.relativize(file);
                        Path target = out.resolve(relative.toString());
                        if (Files.exists(target)) {
                            continue; // première racine gagne
                        }
                        Files.createDirectories(target.getParent());
                        Files.copy(file, target,
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            return TaskExecution.ok();
        } catch (IOException e) {
            return TaskExecution.failed(List.of(Problem.error(
                    "resources-error",
                    "module " + moduleName + " resources failed: "
                            + e.getMessage())));
        }
    }
}
