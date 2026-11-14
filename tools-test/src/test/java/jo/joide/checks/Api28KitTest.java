package jo.joide.checks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Échec volontaire du contrôle API 28 (critère d'acceptation P0, §7) :
 * un module de fixture utilise java.util.HexFormat — présent dans le
 * JDK 17 (donc compilable avec --release 17), ABSENT de l'API Android
 * 28 (constat ADR-013) — la tâche checkApi28 doit l'attraper ; un
 * module conforme doit passer.
 */
class Api28KitTest {

    @TempDir
    Path temp;

    @Test
    void hexFormatUsageIsRejectedAndCompliantModulePasses()
            throws IOException {
        write("settings.gradle.kts", """
                pluginManagement {
                    includeBuild("%BUILD_LOGIC%")
                }
                dependencyResolutionManagement {
                    repositories { mavenCentral() }
                    versionCatalogs {
                        create("libs") {
                            from(files("%ROOT%/gradle/libs.versions.toml"))
                        }
                    }
                }
                rootProject.name = "fixture-api28"
                include(":ok")
                include(":bad")
                """.replace("%BUILD_LOGIC%", buildLogicPath())
                .replace("%ROOT%", repoRoot()));
        write("gradle/module-graph.txt", """
                ok ->
                bad ->
                """);
        // Le contrôle lit les signatures réelles du dépôt.
        Files.copy(Path.of("..", "gradle", "api28-signatures.txt.gz"),
                temp.resolve("gradle/api28-signatures.txt.gz"),
                StandardCopyOption.REPLACE_EXISTING);
        write("ok/build.gradle.kts", """
                plugins { id("joide.java-library-conventions") }
                """);
        write("bad/build.gradle.kts", """
                plugins { id("joide.java-library-conventions") }
                """);
        write("ok/src/main/java/fixture/ok/Ok.java", """
            package fixture.ok;

            /** Compliant fixture. */
            public class Ok {
                /** Uses only API 28 members. */
                public String render(int value) {
                    return String.valueOf(value);
                }
            }
            """);
        write("bad/src/main/java/fixture/bad/Bad.java", """
            package fixture.bad;

            /** HexFormat is Java 17 on desktop, absent from API 28. */
            public class Bad {
                /** Compiles, must be rejected by checkApi28. */
                public String hex(int value) {
                    return java.util.HexFormat.of().toHexDigits(value);
                }
            }
            """);

        // Le module fautif doit échouer, avec la référence exacte.
        write("gradle.properties", "org.gradle.jvmargs=-Xmx512m\norg.gradle.parallel=false\n");
        BuildResult failure = GradleRunner.create()
                .withProjectDir(temp.toFile())
                .withArguments(":bad:checkApi28")
                .buildAndFail();
        String output = failure.getOutput();
        assertTrue(output.contains("API 28 check failed"),
                "api28 failure must be reported, output was:\n" + output);
        assertTrue(output.contains("java/util/HexFormat"),
                "HexFormat must be named, output was:\n" + output);

        // Le module conforme doit passer.
        write("gradle.properties", "org.gradle.jvmargs=-Xmx512m\norg.gradle.parallel=false\n");
        BuildResult success = GradleRunner.create()
                .withProjectDir(temp.toFile())
                .withArguments(":ok:checkApi28")
                .build();
        assertTrue(success.getOutput().contains("BUILD SUCCESSFUL"),
                "compliant module must pass, output was:\n"
                        + success.getOutput());
        assertFalse(success.getOutput().contains("API 28 check failed"));
    }

    /** Chemin absolu du build-logic du dépôt (inclusion réelle). */
    private static String buildLogicPath() {
        return Path.of("..", "build-logic").toAbsolutePath().normalize()
                .toString();
    }

    /** Chemin absolu de la racine du dépôt (catalogue de versions). */
    private static String repoRoot() {
        return Path.of("..").toAbsolutePath().normalize().toString();
    }

    private void write(String relative, String content) throws IOException {
        Path target = temp.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.write(target, List.of(content.split("\n", -1)));
    }
}
