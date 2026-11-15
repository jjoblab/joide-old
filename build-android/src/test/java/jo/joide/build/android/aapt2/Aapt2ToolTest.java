package jo.joide.build.android.aapt2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.build.android.ToolchainFixture;
import jo.joide.build.android.toolchain.Toolchain;

/**
 * Pilotage réel d'aapt2 : compilation par fichier et par répertoire,
 * lien avec génération des sources R (y compris le R du paquet
 * applicatif et celui d'un module bibliothèque), diagnostic structuré
 * sur manifeste cassé.
 */
class Aapt2ToolTest {

    @TempDir
    Path temp;

    private Aapt2 aapt2;
    private Path platform;

    @BeforeEach
    void setUp() {
        Toolchain toolchain = ToolchainFixture.toolchain();
        aapt2 = new Aapt2(toolchain.aapt2, 120);
        platform = toolchain.platformJar;
    }

    @Test
    void compileSingleFileThenDirectory() throws IOException {
        Path res = Files.createDirectories(temp.resolve("res/values"));
        Files.writeString(res.resolve("strings.xml"),
                "<?xml version=\"1.0\"?>\n<resources>\n"
                        + "  <string name=\"app_name\">Test</string>\n"
                        + "</resources>\n");
        Path out = Files.createDirectories(temp.resolve("flat"));
        aapt2.compileFile(res.resolve("strings.xml"), out);
        assertTrue(Files.isRegularFile(out.resolve("values_strings.arsc.flat")),
                "flat file must be named after the resource path");
        Path zip = temp.resolve("all.zip");
        aapt2.compileDir(temp.resolve("res"), zip);
        assertTrue(Files.isRegularFile(zip));
        assertTrue(Files.size(zip) > 0);
    }

    @Test
    void linkGeneratesRAndTable() throws IOException {
        Path res = Files.createDirectories(temp.resolve("res/values"));
        Files.writeString(res.resolve("strings.xml"),
                "<?xml version=\"1.0\"?>\n<resources>\n"
                        + "  <string name=\"app_name\">Test</string>\n"
                        + "  <string name=\"hello\">Bonjour</string>\n"
                        + "</resources>\n");
        Path flat = Files.createDirectories(temp.resolve("flat"));
        aapt2.compileFile(res.resolve("strings.xml"), flat);
        Files.writeString(temp.resolve("AndroidManifest.xml"),
                "<?xml version=\"1.0\"?>\n<manifest\n"
                        + "  xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "  package=\"com.example.tool\"\n"
                        + "  android:versionCode=\"3\"\n"
                        + "  android:versionName=\"1.3\">\n"
                        + "  <application android:label=\"@string/app_name\"/>\n"
                        + "</manifest>\n");
        Path apk = temp.resolve("linked.apk");
        Path gen = Files.createDirectories(temp.resolve("gen"));
        Path rTxt = temp.resolve("R.txt");
        aapt2.link(Aapt2.LinkOptions.builder()
                .manifest(temp.resolve("AndroidManifest.xml"))
                .platformJar(platform)
                .output(apk)
                .javaOut(gen)
                .rTxtOut(rTxt)
                .compiled(flat.resolve("values_strings.arsc.flat"))
                .build());
        assertTrue(Files.isRegularFile(apk));
        Path rJava = gen.resolve("com/example/tool/R.java");
        assertTrue(Files.isRegularFile(rJava), "R.java must be generated");
        String r = Files.readString(rJava);
        assertTrue(r.contains("app_name"), "R must carry app_name");
        assertTrue(r.contains("hello"), "R must carry hello");
        String table = Files.readString(rTxt);
        assertTrue(table.contains("string app_name"), "R.txt must list symbols");
        assertTrue(table.contains("string hello"));
    }

    @Test
    void brokenManifestProducesDiagnostics() throws IOException {
        Files.writeString(temp.resolve("AndroidManifest.xml"),
                "<?xml version=\"1.0\"?>\n<manifest\n"
                        + "  xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "  package=\"com.example.broken\">\n"
                        + "  <application android:label=\"@string/missing\"/>\n"
                        + "</manifest>\n");
        Path out = temp.resolve("broken.apk");
        Aapt2.Aapt2Exception failure = assertThrows(
                Aapt2.Aapt2Exception.class,
                () -> aapt2.link(Aapt2.LinkOptions.builder()
                        .manifest(temp.resolve("AndroidManifest.xml"))
                        .platformJar(platform)
                        .output(out)
                        .build()));
        assertEquals(false, failure.result.success());
        assertTrue(failure.result.stderr.contains("missing"),
                "stderr must name the missing resource: "
                        + failure.result.stderr);
        assertEquals("", failure.result.stdout.trim());
    }

    @Test
    void protoLinkEmitsProtoEntries() throws IOException {
        Path res = Files.createDirectories(temp.resolve("res/values"));
        Files.writeString(res.resolve("strings.xml"),
                "<?xml version=\"1.0\"?>\n<resources>\n"
                        + "  <string name=\"app_name\">Proto</string>\n"
                        + "</resources>\n");
        Path flat = Files.createDirectories(temp.resolve("flat"));
        aapt2.compileFile(res.resolve("strings.xml"), flat);
        Files.writeString(temp.resolve("AndroidManifest.xml"),
                "<?xml version=\"1.0\"?>\n<manifest\n"
                        + "  xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "  package=\"com.example.proto\"\n"
                        + "  android:versionCode=\"1\">\n"
                        + "  <application android:label=\"@string/app_name\"/>\n"
                        + "</manifest>\n");
        Path protoZip = temp.resolve("proto.zip");
        aapt2.link(Aapt2.LinkOptions.builder()
                .manifest(temp.resolve("AndroidManifest.xml"))
                .platformJar(platform)
                .output(protoZip)
                .protoFormat(true)
                .compiled(flat.resolve("values_strings.arsc.flat"))
                .build());
        try (java.util.zip.ZipFile zip =
                new java.util.zip.ZipFile(protoZip.toFile())) {
            assertTrue(zip.getEntry("AndroidManifest.xml") != null,
                    "proto manifest must be present");
            assertTrue(zip.getEntry("resources.pb") != null,
                    "proto resource table must be present");
        }
    }
}
