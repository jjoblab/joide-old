package jo.joide.build.android.tasks;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.build.android.variant.AndroidVariant;
import jo.joide.tasks.TaskGraph;

/**
 * Les arêtes du graphe Android autour du R.jar (lot XVIII, le
 * terrain du 2026-11-17 : « cannot read input file:
 * $OUT/android/:app/r.abi.manifest » sur {@code app:compileKotlin}) —
 * l'entrée R.jar (manifeste ABI déclaré en FileInput) sert aux DEUX
 * tâches de compilation ; l'arête vers {@code generateRJar} du
 * module propre n'existait que pour {@code compileJava} : le
 * scheduler parallèle démarrait {@code compileKotlin} avant la
 * production du manifeste, et le moteur échouait AVANT l'exécution.
 */
class AndroidTaskFactoryGraphTest {

    @Test
    void compileKotlinAttendGenerateRJarDuModulePropre(
            @TempDir Path temp) throws Exception {
        Path project = temp.resolve("project");
        Path out = temp.resolve("out");
        Files.createDirectories(
                project.resolve("app/src/main/res/values"));
        Files.writeString(project.resolve(
                        "app/src/main/AndroidManifest.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.app">
                </manifest>
                """);
        Files.writeString(project.resolve(
                "app/src/main/res/values/strings.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="app_name">Demo</string>
                </resources>
                """);
        Files.createDirectories(project.resolve("app/src/main/kotlin"));
        Files.writeString(project.resolve(
                        "app/src/main/kotlin/App.kt"),
                "package com.example.app\n\nclass App\n");
        AndroidModuleSpec app = new AndroidModuleSpec("app", true);
        app.manifest = Path.of("app/src/main/AndroidManifest.xml");
        app.resourceDirs.add(Path.of("app/src/main/res"));
        app.sourceRoots.add(Path.of("app/src/main/kotlin"));
        List<AndroidVariant> variants = List.of(AndroidVariant
                .builder("com.example.app", "debug").build());
        try (AndroidHarness harness = new AndroidHarness(project, out,
                List.of(app), variants, null)) {
            TaskGraph graph = harness.graph();
            var dependents = graph.dependentsOf("app:generateRJar");
            assertTrue(dependents.contains("app:compileKotlin"),
                    "compileKotlin attend generateRJar (l'arête du lot "
                            + "XVIII, jumelle de celle de compileJava) : "
                            + dependents);
            assertTrue(dependents.contains("app:compileJava"),
                    "compileJava attend generateRJar (l'arête "
                            + "historique) : " + dependents);
        }
    }
}
