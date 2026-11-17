package jo.joide.build.daemon.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.build.daemon.protocol.BuildSpec;

/**
 * Acceptation du pipeline Android à travers le démon : l'application
 * et sa bibliothèque passent le protocole IPC, le démon exécute le
 * pipeline complet — aapt2, ecj, dex par scopes en processus, fusion
 * et rétrécissement R8 dans la machine virtuelle fille d'un
 * gigaoctet (fork à gros tas), signature, bundle — et l'APK signé
 * est vérifié au niveau minimum de la variante. La revendication du
 * fork (1 024 Mo, mémoire de l'appareil) oblige le démon à un tas
 * qui la couvre ; la PREUVE de l'isolation est le pic de tas du
 * démon : il reste sous 700 Mo pendant que la fusion d'un gigaoctet
 * tourne dehors.
 */
class DaemonAndroidPipelineTest {

    @TempDir
    Path temp;

    private static Path keystore;

    @BeforeAll
    static void generateKeystore(@TempDir Path keyDir) throws IOException {
        keystore = keyDir.resolve("test.jks");
        try {
            Process keytool = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin",
                            "keytool").toString(),
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

    /** @return les problèmes d'un build échoué, en texte lisible */
    private static String problemsOf(DaemonClient.Event finished) {
        StringBuilder text = new StringBuilder("build échoué : ");
        for (jo.joide.deps.json.JsonValue problem
                : finished.body.member("problems").items()) {
            text.append('[').append(problem.asString()).append("] ");
        }
        text.append("— exécutées ")
                .append(finished.body.member("executed").asLong());
        return text.toString();
    }

    private static void write(Path project, String relative,
                              String content) throws IOException {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
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
                    android:layout_width="wrap_content"
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

    private static BuildSpec androidSpec(Path project, Path out, Path m2) {
        BuildSpec.ModuleSpec lib = new BuildSpec.ModuleSpec(
                "lib", false,
                List.of("lib/src/main/java"),
                List.of("lib/src/main/res"),
                null, "lib/src/main/AndroidManifest.xml",
                List.of(), List.of(), List.of());
        BuildSpec.ModuleSpec app = new BuildSpec.ModuleSpec(
                "app", true,
                List.of("app/src/main/java"),
                List.of("app/src/main/res"),
                null, "app/src/main/AndroidManifest.xml",
                List.of("lib"), List.of(), List.of());
        return new BuildSpec(BuildSpec.Kind.ANDROID,
                project.toString(), out.toString(), m2.toString(), "17",
                4, 1280, List.of(lib, app),
                List.of(Map.of(
                        "buildType", "debug",
                        "applicationId", "com.example.app",
                        "versionCode", 7,
                        "minSdk", 23,
                        "minifyEnabled", true,
                        "v1Signing", true,
                        "v2Signing", true,
                        "keystoreFile", keystore.toString(),
                        "keystoreAlias", "joide")));
    }

    @Test
    void androidBuildRunsThroughDaemonWithForkedR8() throws Exception {
        Path project = Files.createDirectories(temp.resolve("projet"));
        Path out = temp.resolve("out");
        Path m2 = Files.createDirectories(temp.resolve("m2"));
        writeProject(project);
        BuildSpec spec = androidSpec(project, out, m2);

        // le tas du démon couvre le budget d’admission (les
        // revendications du fork y sont portées : la mémoire de
        // l'appareil, pas seulement celle du démon) ; son usage réel
        // reste bien en dessous — c'est la preuve
        try (DaemonClient daemon = DaemonClient.spawn(
                Path.of(DaemonClient.currentClasspath()),
                "jo.joide.build.daemon.desktop.DaemonMain", 1500)) {
            daemon.await("hello", 60);
            // le premier build peut télécharger l'outillage épinglé
            // (aapt2, plateforme) : délai généreux
            daemon.build(1, spec);
            DaemonClient.Event finished;
            while (true) {
                DaemonClient.Event event = daemon.next(900);
                if ("build-finished".equals(event.type)) {
                    finished = event;
                    break;
                }
            }
            assertTrue(finished.body.member("success").asBoolean(),
                    () -> problemsOf(finished));
            assertTrue(finished.body.member("executed").asLong() >= 10,
                    "le pipeline complet doit travailler");
            // PREUVE du fork à gros tas : la fusion/R8 d'un gigaoctet
            // tourne dans la machine virtuelle fille — le pic de tas
            // du démon reste sous 700 Mo pendant qu'elle tourne
            long daemonPeak = finished.body.member("peakHeapBytes")
                    .asLong();
            assertTrue(daemonPeak <= 700L * 1024 * 1024,
                    "le démon doit rester maigre pendant le fork : "
                            + daemonPeak / (1024 * 1024) + " Mo");
            // le dexeur en processus est créé une fois et réutilisé :
            // compilateurs et dexeurs chauds à travers le protocole
            assertEquals(1L,
                    finished.body.member("hotDexers").asLong(),
                    "le dexeur de scopes doit être réutilisé");

            // APK signé, signature vérifiée v1+v2 au niveau 23
            Path apk = out.resolve(
                    "android/app/variants/debug/app-debug.apk");
            assertTrue(Files.isRegularFile(apk), "apk must exist");
            jo.joide.build.android.sign.ApkSignerService.Verification v =
                    java.util.ServiceLoader.load(
                            jo.joide.build.android.sign.ApkSignerService
                                    .class).findFirst().orElseThrow()
                            .verify(apk, 23);
            assertTrue(v.verified, "signature must verify");
            assertTrue(v.v1, "v1 scheme requested");
            assertTrue(v.v2, "v2 scheme requested");
            assertFalse(v.v3, "v3 not requested");

            // contenu de l'APK : manifeste, table alignée, dex
            try (ZipFile zip = new ZipFile(apk.toFile())) {
                assertTrue(zip.getEntry("AndroidManifest.xml") != null);
                ZipEntry arsc = zip.getEntry("resources.arsc");
                assertTrue(arsc != null);
                assertEquals(ZipEntry.STORED, arsc.getMethod(),
                        "resources.arsc must be stored (mmap)");
                assertTrue(zip.getEntry("classes.dex") != null);
            }

            // bundle validé par bundletool, depuis le processus du
            // test (le démon l'a assemblé)
            Path aab = out.resolve(
                    "android/app/variants/debug/app-debug.aab");
            assertTrue(Files.isRegularFile(aab), "bundle must exist");
            jo.joide.build.android.tools.BundletoolAssembler
                    .validate(aab);

            // second build sans changement : zéro travail, le
            // pipeline Android est chaud aussi
            daemon.build(2, spec);
            DaemonClient.Event hot;
            while (true) {
                DaemonClient.Event event = daemon.next(300);
                if ("build-finished".equals(event.type)) {
                    hot = event;
                    break;
                }
            }
            assertTrue(hot.body.member("success").asBoolean(),
                    () -> hot.toString());
            assertEquals(0L, hot.body.member("executed").asLong(),
                    "rien à faire au second passage");
            daemon.shutdown();
        }
    }
}
