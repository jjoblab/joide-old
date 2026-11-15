package jo.joide.checks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Échec volontaire de la règle de dépendances : un mini-projet
 * Gradle généré — branché sur le VRAI
 * build-logic du dépôt — déclare une dépendance remontante interdite
 * par son propre module-graph.txt ; la configuration du build doit
 * échouer avec le message de la règle.
 */
class DependencyRuleKitTest {

    @TempDir
    Path temp;

    @Test
    void upwardDependencyFailsConfiguration() throws IOException {
        // Fixture : deux modules « a » (bas) et « b » (haut) ; le graphe
        // n'autorise que b -> a. Le module « a » déclare a -> b :
        // remontante, rejetée.
        write("settings.gradle.kts", """
                pluginManagement {
                    includeBuild("%BUILD_LOGIC%")
                    // La substitution de greffon résout les transitifs de
                    // build-logic (AGP) ici — google() requis (P8).
                    repositories { google(); mavenCentral() }
                }
                dependencyResolutionManagement {
                    // build-logic dépend d'AGP (P8) : la substitution composite
                    // résout ses dépendances ici — google() requis.
                    repositories { mavenCentral(); google() }
                    versionCatalogs {
                        create("libs") {
                            from(files("%ROOT%/gradle/libs.versions.toml"))
                        }
                    }
                }
                rootProject.name = "fixture"
                include(":a")
                include(":b")
                """.replace("%BUILD_LOGIC%", buildLogicPath())
                .replace("%ROOT%", repoRoot()));
        write("gradle/module-graph.txt", """
                # graphe fixture : b peut dependre de a, pas l'inverse
                a ->
                b -> a
                """);
        write("a/build.gradle.kts", """
                plugins { id("joide.java-library-conventions") }
                dependencies { implementation(project(":b")) }
                """);
        write("b/build.gradle.kts", """
                plugins { id("joide.java-library-conventions") }
                """);
        write("a/src/main/java/fixture/a/A.java", """
            package fixture.a;

            /** Fixture class. */
            public class A {
                /** No-op. */
                public int value() { return 1; }
            }
            """);
        write("b/src/main/java/fixture/b/B.java", """
            package fixture.b;

            /** Fixture class. */
            public class B {
                /** No-op. */
                public int value() { return 2; }
            }
            """);

        write("gradle.properties", "org.gradle.jvmargs=-Xmx512m\norg.gradle.parallel=false\n");
        BuildResult result = GradleRunner.create()
                .withProjectDir(temp.toFile())
                .withArguments("help")
                .buildAndFail();
        String output = result.getOutput();
        assertTrue(output.contains("dependency rule violation"),
                "rule violation must be reported, output was:\n" + output);
        assertTrue(output.contains("a -> :b"),
                "the forbidden edge must be named, output was:\n" + output);
        assertFalse(output.contains("BUILD SUCCESSFUL"));
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
