package jo.joide.build.android.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.build.android.ToolchainFixture;
import jo.joide.build.android.variant.AndroidVariant;
import jo.joide.tasks.BuildReport;
import jo.joide.tasks.TaskGraph;
import jo.joide.vfs.DirtySet;

/**
 * Cache de dex partageable entre projets : deux projets différents
 * partageant le même dépôt de bibliothèques, le même cache de sorties
 * et le même store (comme le démon) ne dexent une bibliothèque
 * externe qu'une fois — le second projet restaure le dex du cache
 * adressé par contenu.
 */
class SharedDexCacheTest {

    private static Path keystore;
    private static Path externalJar;

    @BeforeAll
    static void setUp(@TempDir Path temp) throws Exception {
        keystore = temp.resolve("test.jks");
        ToolchainFixture.toolchain();
        Process keytool = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "keytool")
                        .toString(),
                "-genkeypair",
                "-keystore", keystore.toString(),
                "-storepass", "joidepass",
                "-keypass", "joidepass",
                "-alias", "joide",
                "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "10000",
                "-dname", "CN=JoIDE Test,O=JoIDE,C=FR")
                .inheritIO().start();
        assertEquals(0, keytool.waitFor(), "keytool must succeed");
        // bibliothèque externe factice : une classe compilée en jar
        Path src = Files.createDirectories(temp.resolve("extsrc/util"));
        Files.writeString(src.resolve("Util.java"), """
                package util;

                public class Util {
                    public static int answer() { return 42; }
                }
                """);
        Path classes = Files.createDirectories(temp.resolve("extclasses"));
        Process javac = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "javac")
                        .toString(),
                "-d", classes.toString(),
                src.resolve("Util.java").toString()).inheritIO().start();
        assertEquals(0, javac.waitFor(), "javac must succeed");
        externalJar = temp.resolve("util-1.0.jar");
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(externalJar))) {
            jar.putNextEntry(new ZipEntry("util/Util.class"));
            jar.write(Files.readAllBytes(classes.resolve("util/Util.class")));
            jar.closeEntry();
        }
    }

    @Test
    void secondProjectRestoresExternalDexFromSharedCache(
            @TempDir Path temp) throws Exception {
        Path m2 = Files.createDirectories(temp.resolve("m2"));
        Path sharedJar = m2.resolve(externalJar.getFileName().toString());
        Files.copy(externalJar, sharedJar);

        // premier projet : la bibliothèque est dexée (store propre)
        Path projectA = temp.resolve("a");
        Path outA = temp.resolve("out-a");
        writeApp(projectA, "com.example.a", "Projet A");
        try (AndroidHarness harness = new AndroidHarness(projectA, outA,
                List.of(appSpec("a", sharedJar)), variants(), keystore)) {
            BuildReport cold = harness.build(harness.graph(),
                    DirtySet.empty());
            assertTrue(cold.successful(),
                    () -> AndroidHarness.allTasks(cold).toString());
            assertTrue(AndroidHarness.executedTasks(cold)
                            .contains("app:dexScope[ext:util-1.0.jar]"),
                    "first project must dex the external jar");
            assertTrue(AndroidHarness.cachedTasks(cold).isEmpty(),
                    "cold build restores nothing");
        }

        // second projet : mêmes bibliothèque, cache et store — le dex
        // de la bibliothèque est restauré, jamais recalculé
        Path projectB = temp.resolve("b");
        Path outB = temp.resolve("out-b");
        writeApp(projectB, "com.example.b", "Projet B");
        try (AndroidHarness harness = new AndroidHarness(projectB, outB,
                List.of(appSpec("b", sharedJar)), variants(), keystore)) {
            BuildReport warm = harness.build(harness.graph(),
                    DirtySet.empty());
            assertTrue(warm.successful(),
                    () -> AndroidHarness.allTasks(warm).toString());
            assertTrue(AndroidHarness.cachedTasks(warm)
                            .contains("app:dexScope[ext:util-1.0.jar]"),
                    "second project must restore the external dex: "
                            + AndroidHarness.allTasks(warm));
            List<String> reexecuted = AndroidHarness.executedTasks(warm);
            assertTrue(reexecuted.stream().noneMatch(
                            name -> name.contains("dexScope[ext:")),
                    "no external dex may be recomputed: " + reexecuted);
            // le dex restauré est bien présent sur le disque
            Path restored = outB.resolve("android/app/variants/debug/"
                    + "dex-ext-util-1.0/classes.dex");
            assertTrue(Files.isRegularFile(restored),
                    "restored dex must exist on disk");
            assertTrue(Files.size(restored) > 0);
        }
    }

    private static List<AndroidVariant> variants() {
        return List.of(AndroidVariant.builder("com.example.app", "debug")
                .minSdk(28)
                .v2Signing(true)
                .keystoreFile(keystore.toString())
                .keystoreAlias("joide")
                .build());
    }

    private static AndroidModuleSpec appSpec(String flavor, Path jar) {
        AndroidModuleSpec spec = new AndroidModuleSpec("app", true);
        spec.manifest = Path.of("app/src/main/AndroidManifest.xml");
        spec.resourceDirs.add(Path.of("app/src/main/res"));
        spec.sourceRoots.add(Path.of("app/src/main/java"));
        spec.externalJars.add(jar);
        return spec;
    }

    private static void writeApp(Path project, String packageName,
                                 String appName) throws IOException {
        AndroidPipelineTest.write(project, "app/src/main/AndroidManifest.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="%s"
                    android:versionCode="1">
                  <application android:label="@string/app_name" />
                </manifest>
                """.formatted(packageName));
        AndroidPipelineTest.write(project, "app/src/main/res/values/strings.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                  <string name="app_name">%s</string>
                </resources>
                """.formatted(appName));
        AndroidPipelineTest.write(project,
                "app/src/main/java/" + packageName.replace('.', '/')
                        + "/App.java",
                """
                package %s;

                public class App {
                    public static int answer() {
                        return util.Util.answer();
                    }
                }
                """.formatted(packageName));
    }
}
