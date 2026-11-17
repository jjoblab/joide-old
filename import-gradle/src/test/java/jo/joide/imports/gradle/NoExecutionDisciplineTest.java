package jo.joide.imports.gradle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Discipline d'exécution de l'import : les sources principales du
 * module ne contiennent aucune API capable d'exécuter un script, un
 * processus ou du code chargé dynamiquement (critère §7 P7 : « aucun
 * script de build exécuté pendant l'import »).
 *
 * <p>La preuve comportementale (poison dans les scripts, marqueurs
 * absents, aucun exit) vit dans {@code GradleImporterTest} ; ce test
 * est la preuve structurelle : ce qui n'est pas dans le code ne peut
 * pas s'exécuter.</p>
 */
class NoExecutionDisciplineTest {

    private static final Path MAIN_SOURCES =
            Paths.get("src", "main", "java");

    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("ProcessBuilder"),
            Pattern.compile("Runtime\\s*\\.\\s*getRuntime"),
            Pattern.compile("System\\s*\\.\\s*exit"),
            Pattern.compile("javax\\.script"),
            Pattern.compile("GroovyShell"),
            Pattern.compile("groovy\\.lang"),
            Pattern.compile("Class\\s*\\.\\s*forName"),
            Pattern.compile("URLClassLoader"),
            Pattern.compile("ScriptEngine"),
            Pattern.compile("MethodHandles\\s*\\.\\s*lookup"),
            Pattern.compile("Compiler"),
            Pattern.compile("\\.exec\\s*\\("));

    @Test
    void mainSourcesContainNoExecutionApi() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path file : files.filter(
                    p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList())) {
                String source = Files.readString(file,
                        StandardCharsets.UTF_8);
                String stripped = stripCommentsAndStrings(source);
                for (Pattern pattern : FORBIDDEN) {
                    Matcher matcher = pattern.matcher(stripped);
                    if (matcher.find()) {
                        violations.add(file + " contient "
                                + pattern.pattern());
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                () -> "APIs d'exécution interdites dans les sources"
                        + " principales : " + violations);
    }

    @Test
    void noGradleRuntimeDependency() throws IOException {
        Path buildFile = Paths.get("build.gradle.kts");
        String content = Files.readString(buildFile,
                StandardCharsets.UTF_8);
        assertTrue(!content.contains("org.gradle") && !content
                        .contains("groovy"),
                "le module ne doit pas dépendre du runtime Gradle ni"
                        + " de Groovy : l'import est statique");
    }

    /** Retire commentaires et chaînes : seuls les identifiants comptent. */
    private static String stripCommentsAndStrings(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < source.length()
                    && source.charAt(i + 1) == '/') {
                while (i < source.length() && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < source.length()
                    && source.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < source.length()
                        && !(source.charAt(i) == '*'
                                && source.charAt(i + 1) == '/')) {
                    i++;
                }
                i += 2;
            } else if (c == '"') {
                i++;
                while (i < source.length() && source.charAt(i) != '"') {
                    if (source.charAt(i) == '\\') {
                        i++;
                    }
                    i++;
                }
                i++;
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
