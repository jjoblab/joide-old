package jo.joide.build.jvm.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jo.joide.platform.Problem;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.TaskContext;
import jo.joide.tasks.TaskDefinition;
import jo.joide.tasks.TaskExecution;
import jo.joide.tasks.TaskInputs;
import jo.joide.tasks.TaskOutputs;

/**
 * Tâche run JVM : exécute la classe principale du module dans une
 * machine virtuelle fille (bureau et tests seulement — sur appareil,
 * l'exécution passe par l'installation et le lancement de
 * l'application, phase P5). La sortie et le code de retour sont
 * capturés dans un fichier de journal déclaré en sortie.
 */
public final class JvmRunTask implements TaskDefinition {

    private final String moduleName;
    private final String mainClass;
    private final List<String> arguments;
    private final List<RootedPath> runtimeClasspath;
    private final RootedPath logFile;

    /**
     * @param moduleName      nom du module
     * @param mainClass       classe principale (forme pointée)
     * @param arguments       arguments du programme
     * @param runtimeClasspath répertoires et jars d'exécution
     * @param logFile         journal (sortie + erreur + code retour)
     */
    public JvmRunTask(String moduleName, String mainClass,
                      List<String> arguments,
                      List<RootedPath> runtimeClasspath,
                      RootedPath logFile) {
        this.moduleName = moduleName;
        this.mainClass = mainClass;
        this.arguments = List.copyOf(arguments);
        this.runtimeClasspath = List.copyOf(runtimeClasspath);
        this.logFile = logFile;
    }

    @Override
    public String name() {
        return moduleName + ":run";
    }

    @Override
    public String taskType() {
        return "run";
    }

    @Override
    public TaskInputs inputs() {
        TaskInputs.Builder builder = TaskInputs.builder();
        for (RootedPath entry : runtimeClasspath) {
            builder.dir(entry);
        }
        builder.property("joide.run.main", mainClass);
        return builder.build();
    }

    @Override
    public TaskOutputs outputs() {
        return TaskOutputs.builder().file(logFile).build();
    }

    @Override
    public String toolVersion() {
        return "joide-run-1";
    }

    @Override
    public String optionsFingerprint() {
        return mainClass + "|" + String.join(" ", arguments);
    }

    @Override
    public TaskExecution execute(TaskContext context) {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null) {
            return TaskExecution.failed(List.of(Problem.error(
                    "run-no-jdk",
                    "no java.home: run task requires a desktop JVM")));
        }
        List<String> command = new ArrayList<>();
        command.add(Path.of(javaHome, "bin", "java").toString());
        StringBuilder classpath = new StringBuilder();
        for (RootedPath entry : runtimeClasspath) {
            if (classpath.length() > 0) {
                classpath.append(java.io.File.pathSeparatorChar);
            }
            classpath.append(context.resolve(entry));
        }
        command.add("-cp");
        command.add(classpath.toString());
        command.add(mainClass);
        command.addAll(arguments);
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            StringBuilder log = new StringBuilder();
            byte[] buffer = new byte[8192];
            try (java.io.InputStream in = process.getInputStream()) {
                int read;
                while ((read = in.read(buffer)) > 0) {
                    log.append(new String(buffer, 0, read,
                            java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            int exit = process.waitFor();
            log.append("\n[exit ").append(exit).append(']');
            Path target = context.resolve(logFile);
            Files.createDirectories(target.getParent());
            jo.joide.platform.AtomicFiles.writeString(target, log);
            if (exit != 0) {
                return TaskExecution.failed(List.of(Problem.error(
                        "run-exit-code",
                        "module " + moduleName + " run exited with "
                                + exit)));
            }
            return TaskExecution.ok();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return TaskExecution.failed(List.of(Problem.error(
                    "run-error",
                    "module " + moduleName + " run failed: "
                            + e.getMessage())));
        }
    }
}
