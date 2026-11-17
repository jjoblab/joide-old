package jo.joide.imports.gradle;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import jo.joide.model.ContentRoot;
import jo.joide.model.DependencyRef;
import jo.joide.model.DependencyScope;
import jo.joide.model.Facet;
import jo.joide.model.Module;
import jo.joide.model.SourceSet;
import jo.joide.model.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Vérification de l'export dans un vrai Gradle (§7 P7 : « l'export se
 * construit dans un vrai Gradle »). Ces tests ne tournent que sur
 * demande — hors appareil, sur une machine de développement — parce
 * qu'ils lancent une distribution Gradle réelle sur les fichiers
 * exportés.
 *
 * <p>Activation : {@code -Djoide.export.realgradle=true} pour la
 * partie JVM (aucun réseau requis avec le cache local) ;
 * {@code -Djoide.export.realandroid=true} pour la partie Android
 * (réseau et SDK requis). Le script
 * {@code scripts/verify-export-gradle.sh} les lance tous les
 * deux.</p>
 */
class ExportRealGradleTest {

    @TempDir
    Path tempDir;

    private static Workspace jvmWorkspace() {
        Map<String, SourceSet> libSets = new LinkedHashMap<>();
        libSets.put("main", new SourceSet("main", List.of(
                new ContentRoot("src/main/java",
                        jo.joide.model.ContentRole.SOURCE))));
        Module lib = new Module(":lib", "lib",
                jo.joide.model.ModuleType.JVM_LIBRARY, libSets,
                List.of(DependencyRef.external(
                        "com.google.code.gson:gson:2.11.0",
                        DependencyScope.API, Set.of())),
                Map.of());
        Map<String, SourceSet> appSets = new LinkedHashMap<>();
        appSets.put("main", new SourceSet("main", List.of(
                new ContentRoot("src/main/java",
                        jo.joide.model.ContentRole.SOURCE))));
        Module app = new Module(":app", "app",
                jo.joide.model.ModuleType.JVM_LIBRARY, appSets,
                List.of(DependencyRef.toModule(":lib",
                        DependencyScope.IMPLEMENTATION, Set.of())),
                Map.of());
        return new Workspace("export-verifie", Map.of("main",
                new jo.joide.model.Project("main",
                        Map.of(":app", app, ":lib", lib))), Map.of());
    }

    @Test
    void jvmExportCompilesInRealGradle() throws Exception {
        assumeTrue(Boolean.getBoolean("joide.export.realgradle"),
                "active avec -Djoide.export.realgradle=true");
        Path dir = tempDir.resolve("export-jvm");
        GradleExporter.write(GradleExporter.export(jvmWorkspace()), dir);
        writeSource(dir.resolve("lib"),
                "src/main/java/verifie/lib/Analyse.java", """
                package verifie.lib;

                import com.google.gson.Gson;

                /** A class that uses the external dependency. */
                public final class Analyse {
                    /** @return le texte en JSON */
                    public static String enJson(String texte) {
                        return new Gson().toJson(texte);
                    }
                }
                """);
        writeSource(dir.resolve("app"),
                "src/main/java/verifie/app/Principal.java", """
                package verifie.app;

                import verifie.lib.Analyse;

                /** A class that depends on the lib module. */
                public final class Principal {
                    /** @return le message en JSON */
                    public static String message() {
                        return Analyse.enJson("bonjour");
                    }
                }
                """);
        String output = runGradle(dir, "clean", "compileJava", "jar");
        assertTrue(output.contains("BUILD SUCCESSFUL"),
                () -> "le vrai Gradle doit construire l'export JVM :\n"
                        + output);
        assertTrue(Files.isRegularFile(dir.resolve("app/build/libs/"
                + "app.jar")), "le jar du module app doit exister");
        assertTrue(Files.isRegularFile(dir.resolve("lib/build/libs/"
                + "lib.jar")), "le jar du module lib doit exister");
    }

    @Test
    void androidExportConfiguresInRealGradle() throws Exception {
        assumeTrue(Boolean.getBoolean("joide.export.realandroid"),
                "active avec -Djoide.export.realandroid=true");
        Path samples = Path.of("src", "test", "resources",
                "gradle-samples");
        GradleImporter.ImportResult imported =
                GradleImporter.importProject(samples.resolve(
                        "multi-catalog"));
        Path dir = tempDir.resolve("export-android");
        GradleExporter.write(GradleExporter.export(
                imported.workspace()), dir);
        // l'export ne porte que la structure : sources et manifeste
        // sont ceux du projet
        Path manifest = dir.resolve("app/src/main/AndroidManifest.xml");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" />
                """, StandardCharsets.UTF_8);
        writeSource(dir.resolve("app"),
                "src/main/java/com/example/multicatalog/app/Racine.java", """
                package com.example.multicatalog.app;

                /** Minimal activity for AGP configuration. */
                public final class Racine extends android.app.Activity {
                }
                """);
        String sdk = System.getProperty("joide.export.android.home",
                System.getenv("ANDROID_HOME"));
        if (sdk != null) {
            Files.writeString(dir.resolve("local.properties"),
                    "sdk.dir=" + sdk + "\n", StandardCharsets.UTF_8);
        }
        String output = runGradle(dir, "assembleDebug");
        assertTrue(output.contains("BUILD SUCCESSFUL"),
                () -> "le vrai Gradle doit construire l'export Android"
                        + " (AGP résolu, configuration complète, APK"
                        + " assemblé) :\n" + output);
        assertTrue(Files.isRegularFile(dir.resolve(
                "app/build/outputs/apk/debug/app-debug.apk")),
                "l'APK de debug doit être produit par le vrai Gradle");
    }

    private static void writeSource(Path moduleDir, String relative,
                                    String content) throws IOException {
        Path source = moduleDir.resolve(relative);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content, StandardCharsets.UTF_8);
    }

    /** Lance le wrapper Gradle du dépôt sur le projet exporté. */
    private static String runGradle(Path projectDir, String... tasks)
            throws IOException, InterruptedException {
        Path wrapper = Path.of("..", "gradlew").toAbsolutePath()
                .normalize();
        assertTrue(Files.isRegularFile(wrapper),
                "gradlew introuvable : " + wrapper);
        List<String> command = new ArrayList<>();
        command.add(wrapper.toString());
        command.add("-p");
        command.add(projectDir.toAbsolutePath().toString());
        command.add("--no-daemon");
        command.add("--console=plain");
        String initScript = System.getProperty(
                "joide.export.init.script");
        if (initScript != null && !initScript.isBlank()) {
            command.add("--init-script");
            command.add(Path.of(initScript).toAbsolutePath()
                    .normalize().toString());
        }
        command.addAll(List.of(tasks));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("JAVA_HOME",
                System.getProperty("java.home"));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        boolean finished = process.waitFor(20, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(
                    "le vrai Gradle n'a pas fini en vingt minutes :\n"
                            + output);
        }
        return output;
    }
}
