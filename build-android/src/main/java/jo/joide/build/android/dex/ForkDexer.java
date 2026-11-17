package jo.joide.build.android.dex;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Dexeur par fork : chaque invocation lance une machine virtuelle
 * fille ({@code java -cp <r8.jar>} com.android.tools.r8.D8 ou R8) avec
 * son plafond de tas propre. Le fork isole la mémoire des grosses
 * fusions et du rétrécissement — la machine virtuelle du build ne voit
 * jamais leur pic ; en contrepartie, chaque invocation paie un
 * démarrage de machine virtuelle. Les pilotes en processus (source
 * set tools) couvrent le cas inverse : petites entrées, compilateurs
 * déjà chauds.
 */
public final class ForkDexer implements Dexer {

    private final Path r8Jar;
    private final String maxHeap;
    private final long timeoutSeconds;

    /**
     * @param r8Jar          jar de D8/R8
     * @param maxHeap        plafond de tas de la machine virtuelle
     *                       fille (par ex. « 2g »)
     * @param timeoutSeconds plafond d'exécution par invocation
     */
    public ForkDexer(Path r8Jar, String maxHeap, long timeoutSeconds) {
        this.r8Jar = Objects.requireNonNull(r8Jar, "r8Jar");
        if (!Files.isRegularFile(r8Jar)) {
            throw new IllegalArgumentException("r8 jar not found: " + r8Jar);
        }
        this.maxHeap = Objects.requireNonNull(maxHeap, "maxHeap");
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public void dex(DexRequest request) throws IOException {
        List<String> args = new ArrayList<>();
        args.add(javaExecutable());
        args.add("-Xmx" + maxHeap);
        args.add("-cp");
        args.add(r8Jar.toString());
        args.add("com.android.tools.r8.D8");
        args.add("--release");
        args.add("--min-api");
        args.add(Integer.toString(request.minApi));
        for (Path lib : request.classpath) {
            args.add("--lib");
            args.add(lib.toString());
        }
        args.add("--output");
        args.add(request.outputDir.toString());
        for (Path entry : request.program) {
            if (java.nio.file.Files.isDirectory(entry)) {
                try (var walk = java.nio.file.Files.walk(entry)) {
                    walk.filter(java.nio.file.Files::isRegularFile)
                            .filter(f -> f.toString().endsWith(".class"))
                            .sorted()
                            .forEach(f -> args.add(f.toString()));
                }
            } else {
                args.add(entry.toString());
            }
        }
        run(args, request.outputDir);
    }

    @Override
    public void merge(MergeRequest request) throws IOException {
        List<String> args = new ArrayList<>();
        args.add(javaExecutable());
        args.add("-Xmx" + maxHeap);
        args.add("-cp");
        args.add(r8Jar.toString());
        args.add("com.android.tools.r8.D8");
        args.add("--release");
        args.add("--min-api");
        args.add(Integer.toString(request.minApi));
        for (Path lib : request.classpath) {
            args.add("--classpath");
            args.add(lib.toString());
        }
        for (Path dex : request.dexFiles) {
            args.add(dex.toString());
        }
        args.add("--output");
        args.add(request.outputDir.toString());
        run(args, request.outputDir);
    }

    @Override
    public void shrink(ShrinkRequest request) throws IOException {
        Path rules = Files.createTempFile("joide-r8-", ".pro");
        StringBuilder text = new StringBuilder();
        for (String rule : request.keepRules) {
            text.append(rule).append('\n');
        }
        Files.writeString(rules, text.toString(), StandardCharsets.UTF_8);
        List<String> args = new ArrayList<>();
        args.add(javaExecutable());
        args.add("-Xmx" + maxHeap);
        args.add("-cp");
        args.add(r8Jar.toString());
        args.add("com.android.tools.r8.R8");
        args.add("--release");
        args.add("--min-api");
        args.add(Integer.toString(request.minApi));
        for (Path lib : request.classpath) {
            args.add("--lib");
            args.add(lib.toString());
        }
        for (Path program : request.program) {
            args.add(program.toString());
        }
        args.add("--pg-conf");
        args.add(rules.toString());
        if (request.mappingOut != null) {
            args.add("--pg-map-output");
            args.add(request.mappingOut.toString());
        }
        args.add("--output");
        args.add(request.outputDir.toString());
        try {
            run(args, request.outputDir);
        } finally {
            Files.deleteIfExists(rules);
        }
    }

    @Override
    public String toolVersion() {
        return "fork-r8@" + r8Jar.getFileName();
    }

    private static String javaExecutable() {
        String home = System.getProperty("java.home", "");
        return Path.of(home, "bin",
                System.getProperty("os.name", "").toLowerCase()
                        .contains("win") ? "java.exe" : "java").toString();
    }

    private void run(List<String> command, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        String all;
        try (BufferedReader reader = new BufferedReader(
                new java.io.InputStreamReader(process.getInputStream(),
                        StandardCharsets.UTF_8))) {
            int code;
            while (true) {
                String line = reader.readLine();
                if (line != null) {
                    output.append(line).append('\n');
                    continue;
                }
                if (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                    continue;
                }
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
                code = process.exitValue();
                break;
            }
            all = output.toString();
            if (code != 0) {
                throw new IOException("r8 fork failed (exit " + code
                        + "):\n" + all);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("r8 fork interrupted", e);
        }
    }
}
