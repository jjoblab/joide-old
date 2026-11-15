package jo.joide.build.jvm.compile;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
import org.jetbrains.kotlin.config.Services;

import jo.joide.platform.Problem;
import jo.joide.platform.Severity;

/**
 * Pilote du compilateur Kotlin embarqué (kotlin-compiler-embeddable,
 * Apache-2.0) : compilation en processus, diagnostics structurés par
 * l'API du collecteur de messages (sévérité, chemin, ligne, colonne).
 *
 * <p>Les racines Java du module sont passées parmi les sources : le
 * compilateur les lit pour la résolution sans les compiler. Le
 * classpath fourni doit déjà porter la bibliothèque standard
 * (-no-stdlib : le compilateur embarqué n'en ajoute pas de
 * supplémentaire). Les classes écrites sont détectées par différence
 * d'inventaire du répertoire de sortie.</p>
 */
public final class KotlinCompilerDriver implements CompilerDriver {

    private final String toolVersion;

    /** Construit le pilote et fige la version de l'outil. */
    public KotlinCompilerDriver() {
        this.toolVersion = "kotlinc-" + detectVersion();
    }

    private static String detectVersion() {
        try {
            return String.valueOf(kotlin.KotlinVersion.CURRENT);
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    @Override
    public String toolVersion() {
        return toolVersion;
    }

    @Override
    public CompileResult compile(CompileRequest request) {
        Map<String, SizeAndTime> before = inventory(request.outputDir);
        List<String> args = new ArrayList<>();
        args.add("-classpath");
        args.add(joinPaths(request.classpath));
        args.add("-d");
        args.add(request.outputDir.toString());
        args.add("-jvm-target");
        args.add(request.sourceLevel);
        // la bibliothèque standard vient du classpath fourni
        args.add("-no-stdlib");
        args.add("-no-reflect");
        for (Path source : request.sources) {
            args.add(source.toString());
        }
        // racines Java pour la résolution jointe : le compilateur ne
        // parcourt pas les dossiers à la recherche de sources Java,
        // chaque fichier est passé individuellement
        for (Path root : request.javaSourceRootsForResolution) {
            if (Files.isRegularFile(root)) {
                args.add(root.toString());
            } else if (Files.isDirectory(root)) {
                try (var stream = Files.walk(root)) {
                    stream.filter(p -> p.getFileName().toString()
                            .endsWith(".java"))
                            .sorted()
                            .forEach(p -> args.add(p.toString()));
                } catch (IOException e) {
                    return new CompileResult(false, List.of(Problem.error(
                            "java-root-error",
                            "cannot walk java source root " + root
                                    + ": " + e.getMessage())),
                            List.of());
                }
            }
        }
        List<Problem> diagnostics = new ArrayList<>();
        boolean[] hasError = new boolean[1];
        MessageCollector collector = new MessageCollector() {
            @Override
            public void clear() {
                // rien : les messages sont consommés au fil de l'eau
            }

            @Override
            public void report(CompilerMessageSeverity severity,
                               String message,
                               CompilerMessageSourceLocation location) {
                if (CompilerMessageSeverity.ERROR.equals(severity)
                        || CompilerMessageSeverity.EXCEPTION.equals(severity)) {
                    hasError[0] = true;
                    diagnostics.add(toProblem(Severity.ERROR,
                            "kotlin.error", message, location));
                } else if (CompilerMessageSeverity.STRONG_WARNING.equals(
                        severity) || CompilerMessageSeverity.WARNING.equals(
                        severity)) {
                    diagnostics.add(toProblem(Severity.WARN,
                            "kotlin.warning", message, location));
                }
            }

            @Override
            public boolean hasErrors() {
                return hasError[0];
            }
        };
        K2JVMCompiler compiler = new K2JVMCompiler();
        K2JVMCompilerArguments parsed =
                compiler.createArguments();
        compiler.parseArguments(args.toArray(new String[0]), parsed);
        ExitCode code = compiler.exec(collector, Services.EMPTY, parsed);
        boolean success = ExitCode.OK.equals(code);
        TreeSet<String> written = new TreeSet<>();
        for (Map.Entry<String, SizeAndTime> entry
                : inventory(request.outputDir).entrySet()) {
            SizeAndTime previous = before.get(entry.getKey());
            if (previous == null || !previous.equals(entry.getValue())) {
                written.add(entry.getKey());
            }
        }
        return new CompileResult(success, diagnostics,
                new ArrayList<>(written));
    }

    private static Problem toProblem(Severity severity, String code,
                                     String message,
                                     CompilerMessageSourceLocation location) {
        Problem diagnostic = severity == Severity.ERROR
                ? Problem.error(code, message)
                : Problem.warn(code, message);
        if (location != null) {
            int line = location.getLine();
            int column = location.getColumn();
            if (line > 0) {
                return diagnostic.at(location.getPath()
                        + ":" + line + ":" + Math.max(column, 1));
            }
        }
        return diagnostic;
    }

    private static String joinPaths(List<Path> paths) {
        StringBuilder out = new StringBuilder();
        for (Path path : paths) {
            if (out.length() > 0) {
                out.append(java.io.File.pathSeparatorChar);
            }
            out.append(path);
        }
        return out.toString();
    }

    /** Inventaire du répertoire de sortie : chemin relatif → taille. */
    private static Map<String, SizeAndTime> inventory(Path dir) {
        Map<String, SizeAndTime> files = new TreeMap<>();
        if (!Files.isDirectory(dir)) {
            return files;
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file,
                                                 BasicFileAttributes attrs) {
                    if (file.getFileName().toString().endsWith(".class")) {
                        files.put(dir.relativize(file).toString()
                                        .replace('\\', '/'),
                                new SizeAndTime(attrs.size(),
                                        attrs.lastModifiedTime().toMillis()));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // inventaire vide : chaque classe paraîtra nouvelle, ce qui
            // est conservateur et sans effet sur la correction
        }
        return files;
    }

    private static final class SizeAndTime {
        final long size;
        final long lastModified;

        SizeAndTime(long size, long lastModified) {
            this.size = size;
            this.lastModified = lastModified;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SizeAndTime)) {
                return false;
            }
            SizeAndTime that = (SizeAndTime) other;
            return size == that.size && lastModified == that.lastModified;
        }

        @Override
        public int hashCode() {
            return (int) (size * 31 + lastModified);
        }
    }
}
