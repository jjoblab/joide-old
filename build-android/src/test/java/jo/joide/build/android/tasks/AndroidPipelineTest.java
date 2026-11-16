package jo.joide.build.android.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.build.android.ToolchainFixture;
import jo.joide.build.android.tools.BundletoolAssembler;
import jo.joide.build.android.variant.AndroidVariant;
import jo.joide.tasks.BuildReport;
import jo.joide.tasks.TaskGraph;
import jo.joide.vfs.DirtySet;

/**
 * Acceptation du pipeline Android : application + bibliothèque avec
 * ressources et sources, build complet jusqu'à l'APK signé et au
 * bundle validé ; incrémentalité proportionnelle à la modification ;
 * incremental ≡ propre sur l'octet.
 */
class AndroidPipelineTest {

    private static Path keystore;

    @BeforeAll
    static void generateKeystore(@TempDir Path temp) throws IOException {
        keystore = temp.resolve("test.jks");
        ToolchainFixture.toolchain(); // hypothèse : outillage présent
        try {
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private static void writeProject(Path project) throws IOException {
        write(project, "app/src/main/AndroidManifest.xml", """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.app"
                    android:versionCode="7"
                    android:versionName="1.7">
                  <application android:label="@string/app_name" />
                </manifest>
                """);
        write(project, "app/src/main/res/values/strings.xml", """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                  <string name="app_name">JoIDE App</string>
                  <string name="greeting">Bonjour</string>
                </resources>
                """);
        write(project, "app/src/main/res/layout/main.xml", """
                <?xml version="1.0" encoding="utf-8"?>
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical">
                  <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/greeting" />
                </LinearLayout>
                """);
        write(project, "app/src/main/java/com/example/app/App.java", """
                package com.example.app;

                import com.example.lib.Lib;

                public class App {
                    public static String greet() {
                        return Lib.decorate(
                                String.valueOf(com.example.app.R.string.greeting));
                    }
                }
                """);
        write(project, "lib/src/main/AndroidManifest.xml", """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.lib">
                </manifest>
                """);
        write(project, "lib/src/main/res/values/strings.xml", """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                  <string name="lib_label">Bibliothèque</string>
                </resources>
                """);
        write(project, "lib/src/main/java/com/example/lib/Lib.java", """
                package com.example.lib;

                public class Lib {
                    public static String decorate(String value) {
                        return "[" + value + "]";
                    }

                    public static int labelId() {
                        return com.example.lib.R.string.lib_label;
                    }
                }
                """);
    }

    private static AndroidModuleSpec appSpec() {
        AndroidModuleSpec spec = new AndroidModuleSpec("app", true);
        spec.manifest = Path.of("app/src/main/AndroidManifest.xml");
        spec.resourceDirs.add(Path.of("app/src/main/res"));
        spec.sourceRoots.add(Path.of("app/src/main/java"));
        spec.moduleDependencies.add("lib");
        return spec;
    }

    private static AndroidModuleSpec libSpec() {
        AndroidModuleSpec spec = new AndroidModuleSpec("lib", false);
        spec.manifest = Path.of("lib/src/main/AndroidManifest.xml");
        spec.resourceDirs.add(Path.of("lib/src/main/res"));
        spec.sourceRoots.add(Path.of("lib/src/main/java"));
        return spec;
    }

    private static List<AndroidVariant> variants() {
        // minSdk 23 : la signature V1 y est vérifiée (les schémas V2+
        // ne couvrent que les plateformes 24 et au-delà)
        return List.of(AndroidVariant.builder("com.example.app", "debug")
                .versionCode(7)
                .versionName("1.7")
                .minSdk(23)
                .v1Signing(true)
                .v2Signing(true)
                .v3Signing(false)
                .keystoreFile(keystore.toString())
                .keystoreAlias("joide")
                .build());
    }

    @Test
    void fullBuildProducesSignedVerifiedApkAndValidBundle(
            @TempDir Path temp) throws Exception {
        Path project = temp.resolve("project");
        Path out = temp.resolve("out");
        writeProject(project);
        try (AndroidHarness harness = new AndroidHarness(project, out,
                List.of(libSpec(), appSpec()), variants(), keystore)) {
            TaskGraph graph = harness.graph();
            BuildReport report = harness.build(graph, DirtySet.empty());
            assertTrue(report.successful(), () -> AndroidHarness.allTasks(report)
                    .toString());
            Path signed = out.resolve("android/app/variants/debug/"
                    + "app-debug.apk");
            assertTrue(Files.isRegularFile(signed), "signed apk must exist");
            assertTrue(Files.size(signed) > 0);

            // signature vérifiée v1+v2 au niveau minimum de la
            // variante (23) : les deux schémas y sont contrôlés
            jo.joide.build.android.sign.ApkSignerService.Verification v =
                    ToolchainFixture.signer().verify(signed, 23);
            assertTrue(v.verified, "signature must verify");
            assertTrue(v.v1, "v1 scheme requested");
            assertTrue(v.v2, "v2 scheme requested");
            assertFalse(v.v3, "v3 not requested");

            // contenu de l'APK : manifeste, table alignée, dex
            try (ZipFile apk = new ZipFile(signed.toFile())) {
                assertTrue(apk.getEntry("AndroidManifest.xml") != null);
                ZipEntry arsc = apk.getEntry("resources.arsc");
                assertTrue(arsc != null);
                assertEquals(ZipEntry.STORED, arsc.getMethod(),
                        "resources.arsc must be stored (mmap)");
                assertTrue(apk.getEntry("classes.dex") != null);
            }

            // bundle validé par bundletool
            Path aab = out.resolve("android/app/variants/debug/"
                    + "app-debug.aab");
            assertTrue(Files.isRegularFile(aab), "bundle must exist");
            BundletoolAssembler.validate(aab);

            // R du module bibliothèque : la classe compilée référence
            // son propre R avec les symboles de la bibliothèque
            Path libR = out.resolve("android/lib/gen/com/example/lib/R.java");
            assertTrue(Files.isRegularFile(libR),
                    "library R must be generated");
            assertTrue(Files.readString(libR).contains("lib_label"));
        }
    }

    /**
     * Lot U/6 (rapport du 2026-11-17) : un frère JVM du graphe
     * Android n'a NI manifeste NI ressources — l'ancienne fabrique
     * NPEtait à la construction du graphe
     * ({@code Path.isAbsolute()} sur le manifeste null passé à
     * aapt2 link) ; le graphe se construit, le frère compile sans
     * R.jar et l'APK de l'application se construit.
     */
    @Test
    void jvmSiblingWithoutManifestBuilds(@TempDir Path temp)
            throws Exception {
        Path project = temp.resolve("project");
        Path out = temp.resolve("out");
        writeProject(project);
        // le frère JVM : sources pures, ni manifeste ni ressources
        write(project, "pure/src/main/java/com/example/pure/Pure.java",
                """
                package com.example.pure;

                public class Pure {
                    public static String plain(String value) {
                        return value + "!";
                    }
                }
                """);
        // l'application consomme le frère et son propre R
        write(project, "app/src/main/java/com/example/app/App.java", """
                package com.example.app;

                import com.example.pure.Pure;

                public class App {
                    public static String greet() {
                        return Pure.plain(String.valueOf(
                                com.example.app.R.string.greeting));
                    }
                }
                """);
        AndroidModuleSpec app = new AndroidModuleSpec("app", true);
        app.manifest = Path.of("app/src/main/AndroidManifest.xml");
        app.resourceDirs.add(Path.of("app/src/main/res"));
        app.sourceRoots.add(Path.of("app/src/main/java"));
        app.moduleDependencies.add("pure");
        AndroidModuleSpec pure = new AndroidModuleSpec("pure", false);
        pure.sourceRoots.add(Path.of("pure/src/main/java"));
        try (AndroidHarness harness = new AndroidHarness(project, out,
                List.of(pure, app), variants(), keystore)) {
            TaskGraph graph = harness.graph();
            BuildReport report = harness.build(graph, DirtySet.empty());
            assertTrue(report.successful(),
                    () -> AndroidHarness.allTasks(report).toString());
            assertTrue(Files.isRegularFile(out.resolve(
                    "android/app/variants/debug/app-debug.apk")),
                    "signed apk must exist");
            // le frère JVM : aucune tâche Android (ni aapt2, ni R.jar)
            for (String task : AndroidHarness.allTasks(report)) {
                assertFalse(task.startsWith("pure:processAndroidResources"),
                        "manifest-less sibling must not run aapt2: " + task);
                assertFalse(task.startsWith("pure:generateRJar"),
                        "manifest-less sibling must not generate R: " + task);
            }
        }
    }

    @Test
    void incrementalBuildIsProportionalToChange(@TempDir Path temp)
            throws Exception {
        Path project = temp.resolve("project");
        Path out = temp.resolve("out");
        writeProject(project);
        try (AndroidHarness harness = new AndroidHarness(project, out,
                List.of(libSpec(), appSpec()), variants(), keystore)) {
            TaskGraph graph = harness.graph();
            BuildReport cold = harness.build(graph, DirtySet.empty());
            assertTrue(cold.successful(), () -> AndroidHarness.allTasks(cold)
                    .toString());
            int coldCount = AndroidHarness.executedTasks(cold).size();
            assertTrue(coldCount >= 8, "cold build executes many tasks: "
                    + coldCount);

            // changement minime : une chaîne de l'application
            write(project, "app/src/main/res/values/strings.xml", """
                    <?xml version="1.0" encoding="utf-8"?>
                    <resources>
                      <string name="app_name">JoIDE App (modifié)</string>
                      <string name="greeting">Bonsoir</string>
                    </resources>
                    """);
            BuildReport warm = harness.build(graph, DirtySet.of(
                    Set.of(),
                    Set.of("app/src/main/res/values/strings.xml"),
                    Set.of()));
            assertTrue(warm.successful(), () -> AndroidHarness.allTasks(warm)
                    .toString());
            List<String> executed = AndroidHarness.executedTasks(warm);
            // la bibliothèque n'est pas retouchée
            for (String task : executed) {
                assertFalse(task.startsWith("lib:"),
                        "library must not be re-executed: " + task);
            }
            // le pipeline Android amont de l'application est repris
            assertTrue(executed.contains("app:processAndroidResources"),
                    () -> executed.toString());
            assertTrue(executed.contains("app:linkAndroidResources"),
                    () -> executed.toString());
            // la valeur d'une chaîne ne change pas les identifiants :
            // le R.jar est identique, sa tâche reste à jour
            assertFalse(executed.contains("app:generateRJar"),
                    "R.jar must stay up-to-date when ids do not change: "
                            + executed);
            // le dex de la bibliothèque n'est pas recalculé : les
            // jars externes et modules n'ont pas changé
            assertFalse(executed.contains("app:dexScope[lib]"),
                    "lib dex scope must not re-run: " + executed);
        }
    }

    @Test
    void incrementalMatchesCleanBuildByteForByte(@TempDir Path temp)
            throws Exception {
        Path projectA = temp.resolve("a");
        Path outA = temp.resolve("out-a");
        writeProject(projectA);
        // build propre
        try (AndroidHarness harness = new AndroidHarness(projectA, outA,
                List.of(libSpec(), appSpec()), variants(), keystore)) {
            BuildReport report = harness.build(harness.graph(),
                    DirtySet.empty());
            assertTrue(report.successful(), () -> AndroidHarness.allTasks(report)
                    .toString());
        }
        // modification : nouvelle chaîne dans l'application
        write(projectA, "app/src/main/res/values/strings.xml", """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                  <string name="app_name">JoIDE App (v2)</string>
                  <string name="greeting">Salut</string>
                </resources>
                """);
        write(projectA, "app/src/main/java/com/example/app/App.java", """
                package com.example.app;

                import com.example.lib.Lib;

                public class App {
                    public static String greet() {
                        return Lib.decorate("v2:"
                                + com.example.app.R.string.greeting);
                    }
                }
                """);
        byte[] incremental;
        try (AndroidHarness harness = new AndroidHarness(projectA, outA,
                List.of(libSpec(), appSpec()), variants(), keystore)) {
            BuildReport report = harness.build(harness.graph(),
                    DirtySet.of(Set.of(),
                            Set.of("app/src/main/res/values/strings.xml",
                                    "app/src/main/java/com/example/app/App.java"),
                            Set.of()));
            assertTrue(report.successful(), () -> AndroidHarness.allTasks(report)
                    .toString());
            Path unsigned = outA.resolve(
                    "android/app/variants/debug/unsigned.apk");
            incremental = Files.readAllBytes(unsigned);
        }
        // build propre du projet modifié, zéro état
        Path projectB = temp.resolve("b");
        Path outB = temp.resolve("out-b");
        writeProject(projectB);
        write(projectB, "app/src/main/res/values/strings.xml", """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                  <string name="app_name">JoIDE App (v2)</string>
                  <string name="greeting">Salut</string>
                </resources>
                """);
        write(projectB, "app/src/main/java/com/example/app/App.java", """
                package com.example.app;

                import com.example.lib.Lib;

                public class App {
                    public static String greet() {
                        return Lib.decorate("v2:"
                                + com.example.app.R.string.greeting);
                    }
                }
                """);
        byte[] clean;
        try (AndroidHarness harness = new AndroidHarness(projectB, outB,
                List.of(libSpec(), appSpec()), variants(), keystore)) {
            BuildReport report = harness.build(harness.graph(),
                    DirtySet.empty());
            assertTrue(report.successful(), () -> AndroidHarness.allTasks(report)
                    .toString());
            Path unsigned = outB.resolve(
                    "android/app/variants/debug/unsigned.apk");
            clean = Files.readAllBytes(unsigned);
        }
        if (!java.util.Arrays.equals(incremental, clean)) {
            StringBuilder diff = new StringBuilder(
                    "incremental " + incremental.length + " o, clean "
                            + clean.length + " o\n");
            try {
                var a = new java.util.zip.ZipFile(
                        java.nio.file.Files.write(temp.resolve("incr.apk"),
                                incremental).toFile());
                var b = new java.util.zip.ZipFile(
                        java.nio.file.Files.write(temp.resolve("clean.apk"),
                                clean).toFile());
                var namesA = new java.util.TreeSet<String>();
                var namesB = new java.util.TreeSet<String>();
                a.stream().forEach(e -> namesA.add(e.getName()));
                b.stream().forEach(e -> namesB.add(e.getName()));
                diff.append("entrées A ").append(namesA).append('\n');
                diff.append("entrées B ").append(namesB).append('\n');
                for (String name : namesA) {
                    if (!namesB.contains(name)) {
                        continue;
                    }
                    byte[] xa = a.getInputStream(a.getEntry(name))
                            .readAllBytes();
                    byte[] xb = b.getInputStream(b.getEntry(name))
                            .readAllBytes();
                    if (!java.util.Arrays.equals(xa, xb)) {
                        diff.append("différence sur ").append(name)
                                .append(" : ").append(xa.length).append(" o")
                                .append(" vs ").append(xb.length)
                                .append(" o\n");
                    }
                }
            } catch (Exception e) {
                diff.append("comparaison impossible : ").append(e);
            }
            assertTrue(false, diff.toString());
        }
    }

    static void write(Path project, String relative, String content)
            throws IOException {
        Path file = project.resolve(relative);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, content);
    }
}
