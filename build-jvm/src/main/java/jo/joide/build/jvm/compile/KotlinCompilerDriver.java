package jo.joide.build.jvm.compile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        Map<String, CompileInventory.SizeAndTime> before =
                CompileInventory.of(request.outputDir);
        List<String> args;
        try {
            args = KotlinArgs.args(request);
        } catch (IOException e) {
            return new CompileResult(false, List.of(Problem.error(
                    "java-root-error",
                    "cannot walk java source root : " + e.getMessage())),
                    List.of());
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
        List<String> written = new ArrayList<>(CompileInventory.written(
                before, CompileInventory.of(request.outputDir)));
        return new CompileResult(success, diagnostics, written);
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

}
