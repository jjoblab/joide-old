package jo.joide.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrôle de la politique de langue :
 * le script doit échouer sur la fixture de violations — identifiant non
 * ASCII, message d'exception français, classe publique sans Javadoc,
 * typographie, parité values/values-fr (lot G, ADR-048) — et passer
 * sur la fixture conforme (chaînes des deux langues, mêmes clés).
 */
class LanguagePolicyScriptTest {

    @TempDir
    Path temp;

    /**
     * Copie une fixture dans un répertoire temporaire : le script ne
     * doit jamais modifier les fichiers du dépôt en mode contrôle.
     */
    private Path copyFixture(String name) throws IOException {
        Path source = Path.of("src", "test", "resources", "fixtures", name);
        Path target = temp.resolve(name);
        try (var walk = Files.walk(source)) {
            for (Path file : walk.filter(Files::isRegularFile)
                    .toList()) {
                Path dest = target.resolve(source.relativize(file).toString());
                Files.createDirectories(dest.getParent());
                Files.copy(file, dest);
            }
        }
        return target;
    }

    @Test
    void scriptFailsOnViolationFixture() throws IOException, InterruptedException {
        Result result = runScript(copyFixture("language-violation"));
        assertEquals(1, result.exitCode,
                "the script must fail on the violation fixture");
        assertTrue(result.output.contains("non ASCII"),
                "non-ascii identifier must be reported, output was: "
                        + result.output);
        assertTrue(result.output.contains("exception"),
                "french exception message must be reported");
        assertTrue(result.output.contains("sans Javadoc"),
                "missing javadoc must be reported");
        assertTrue(result.output.contains("insecable"),
                "typography must be reported");
        assertTrue(result.output.contains("parite"),
                "values/values-fr parity must be reported, output was: "
                        + result.output);
        assertTrue(result.output.contains("oui"),
                "the key missing from values-fr must be named, output was: "
                        + result.output);
        assertTrue(result.output.contains("extra"),
                "the orphan key of values-fr must be named, output was: "
                        + result.output);
    }

    @Test
    void scriptPassesOnCleanFixture() throws IOException, InterruptedException {
        Result result = runScript(copyFixture("language-clean"));
        assertEquals(0, result.exitCode,
                "the script must pass on the clean fixture, output was: "
                        + result.output);
        assertFalse(result.output.contains("probleme(s) : autre"));
    }

    private Result runScript(Path target)
            throws IOException, InterruptedException {
        Path script = Path.of("..", "scripts", "check-language-policy")
                .toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(script), "script not found: " + script);
        List<String> command = new ArrayList<>();
        command.add("python3");
        command.add(script.toString());
        command.add(target.toAbsolutePath().toString());
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        byte[] raw = process.getInputStream().readAllBytes();
        int code = process.waitFor();
        return new Result(code, new String(raw, java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Sortie de script : code de retour et texte combiné. */
    private static final class Result {
        final int exitCode;
        final String output;

        Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
