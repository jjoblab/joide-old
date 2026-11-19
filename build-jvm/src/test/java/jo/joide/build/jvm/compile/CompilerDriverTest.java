package jo.joide.build.jvm.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.platform.Problem;
import jo.joide.platform.Severity;

/**
 * Pilotes réels : ecj compile du Java (avec diagnostic structuré en
 * erreur), le compilateur Kotlin embarqué compile du Kotlin contre
 * une dépendance Java préalablement compilée.
 */
class CompilerDriverTest {

    @TempDir
    static Path work;

    private static Path stdlibJar;

    @BeforeAll
    static void locateStdlib() {
        String property = System.getProperty("java.class.path");
        for (String entry : property.split(java.io.File.pathSeparator)) {
            if (entry.contains("kotlin-stdlib")) {
                stdlibJar = Path.of(entry);
                return;
            }
        }
        stdlibJar = null;
    }

    @Test
    void ecjCompilesJavaWithStructuredOutput() throws Exception {
        Path src = work.resolve("ecj-src/Foo.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, """
                package com.example;

                public class Foo {
                    public static final int LIMIT = 42;

                    public int triple(int value) {
                        return value * 3;
                    }
                }
                """);
        EcjCompilerDriver driver = new EcjCompilerDriver();
        assertTrue(driver.toolVersion().startsWith("ecj-"),
                "version: " + driver.toolVersion());
        assertTrue(driver.toolVersion().length() > "ecj-".length() + 1);

        CompileResult result = driver.compile(CompileRequest.builder()
                .source(src)
                .outputDir(work.resolve("ecj-out"))
                .build());
        assertTrue(result.success, "diagnostics: " + result.diagnostics);
        assertEquals(List.of("com/example/Foo.class"),
                result.writtenClasses);
        byte[] bytes = Files.readAllBytes(
                work.resolve("ecj-out/com/example/Foo.class"));
        assertTrue(bytes.length > 100);
    }

    @Test
    void ecjReportsStructuredErrorWithLineColumnAndCode() throws Exception {
        Path src = work.resolve("ecj-bad/Bad.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, """
                package com.example;

                public class Bad {
                    void broken() {
                        return "not an int";
                    }
                }
                """);
        EcjCompilerDriver driver = new EcjCompilerDriver();
        CompileResult result = driver.compile(CompileRequest.builder()
                .source(src)
                .outputDir(work.resolve("ecj-bad-out"))
                .build());
        assertFalse(result.success);
        Problem error = result.diagnostics.stream()
                .filter(p -> p.severity() == Severity.ERROR)
                .findFirst().orElse(null);
        assertNotNull(error, "diagnostics: " + result.diagnostics);
        assertTrue(error.code().startsWith("ecj."),
                "code: " + error.code());
        assertTrue(error.location().endsWith(":5:9"),
                "location: " + error.location());
    }

    @Test
    void ecjResolvesClasspath() throws Exception {
        Path lib = work.resolve("ecj-lib/Lib.java");
        Files.createDirectories(lib.getParent());
        Files.writeString(lib, """
                package com.example.lib;

                public class Lib {
                    public String name() {
                        return "lib";
                    }
                }
                """);
        EcjCompilerDriver driver = new EcjCompilerDriver();
        assertTrue(driver.compile(CompileRequest.builder()
                .source(lib)
                .outputDir(work.resolve("ecj-lib-out"))
                .build()).success);

        Path app = work.resolve("ecj-app/App.java");
        Files.createDirectories(app.getParent());
        Files.writeString(app, """
                package com.example.app;

                import com.example.lib.Lib;

                public class App {
                    public String use() {
                        return new Lib().name();
                    }
                }
                """);
        CompileResult result = driver.compile(CompileRequest.builder()
                .source(app)
                .classpath(work.resolve("ecj-lib-out"))
                .outputDir(work.resolve("ecj-app-out"))
                .build());
        assertTrue(result.success, "diagnostics: " + result.diagnostics);
        assertEquals(List.of("com/example/app/App.class"),
                result.writtenClasses);
    }

    @Test
    void kotlinCompilesAgainstJavaDependency() throws Exception {
        assertNotNull(stdlibJar, "kotlin-stdlib must be on the test classpath");
        Path lib = work.resolve("kt-lib/Lib.java");
        Files.createDirectories(lib.getParent());
        Files.writeString(lib, """
                package com.example.lib;

                public class Lib {
                    public String process(String input) {
                        return "[" + input + "]";
                    }
                }
                """);
        EcjCompilerDriver ecj = new EcjCompilerDriver();
        assertTrue(ecj.compile(CompileRequest.builder()
                .source(lib)
                .outputDir(work.resolve("kt-lib-out"))
                .build()).success);

        Path kt = work.resolve("kt-src/Tool.kt");
        Files.createDirectories(kt.getParent());
        Files.writeString(kt, """
                package com.example.tool

                import com.example.lib.Lib

                const val MAGIC = 7

                class Tool(private val api: Lib) {
                    fun run(input: String): String = api.process(input) + MAGIC
                }

                inline fun tag(value: String): String = "<$value>"
                """);
        KotlinCompilerDriver kotlin = new KotlinCompilerDriver();
        assertTrue(kotlin.toolVersion().startsWith("kotlinc-"),
                "version: " + kotlin.toolVersion());
        CompileResult result = kotlin.compile(CompileRequest.builder()
                .source(kt)
                .classpath(work.resolve("kt-lib-out"))
                .classpath(stdlibJar)
                .outputDir(work.resolve("kt-out"))
                .build());
        assertTrue(result.success, "diagnostics: " + result.diagnostics);
        // Tool.class + la façade du fichier (fonctions de premier niveau)
        assertTrue(result.writtenClasses.contains("com/example/tool/Tool.class"),
                "written: " + result.writtenClasses);
        assertTrue(result.writtenClasses.size() >= 2,
                "facade expected: " + result.writtenClasses);
    }

    @Test
    void kotlinReportsStructuredError() throws Exception {
        assertNotNull(stdlibJar, "kotlin-stdlib must be on the test classpath");
        Path kt = work.resolve("kt-bad/Broken.kt");
        Files.createDirectories(kt.getParent());
        Files.writeString(kt, """
                package com.example.broken

                fun broken(): Int {
                    return "une chaine"
                }
                """);
        KotlinCompilerDriver kotlin = new KotlinCompilerDriver();
        CompileResult result = kotlin.compile(CompileRequest.builder()
                .source(kt)
                .classpath(stdlibJar)
                .outputDir(work.resolve("kt-bad-out"))
                .build());
        assertFalse(result.success);
        Problem error = result.diagnostics.stream()
                .filter(p -> p.severity() == Severity.ERROR)
                .findFirst().orElse(null);
        assertNotNull(error, "diagnostics: " + result.diagnostics);
        assertTrue(error.location().contains("Broken.kt:4:"),
                "location: " + error.location());
    }

    @Test
    void kotlinReadsJavaSourcesForResolution() throws Exception {
        assertNotNull(stdlibJar, "kotlin-stdlib must be on the test classpath");
        Path javaRoot = work.resolve("kt-joint/java");
        Files.createDirectories(javaRoot);
        Files.writeString(javaRoot.resolve("Joint.java"), """
                package com.example.joint;

                public class Joint {
                    public int answer() {
                        return 41;
                    }
                }
                """);
        Path kt = work.resolve("kt-joint/kt/Use.kt");
        Files.createDirectories(kt.getParent());
        Files.writeString(kt, """
                package com.example.joint

                class Use(private val joint: Joint) {
                    fun doubled(): Int = joint.answer() + 1
                }
                """);
        KotlinCompilerDriver kotlin = new KotlinCompilerDriver();
        CompileResult result = kotlin.compile(CompileRequest.builder()
                .source(kt)
                .javaSourceRoot(javaRoot)
                .classpath(stdlibJar)
                .outputDir(work.resolve("kt-joint-out"))
                .build());
        assertTrue(result.success, "diagnostics: " + result.diagnostics);
        assertTrue(result.writtenClasses.contains(
                "com/example/joint/Use.class"),
                "written: " + result.writtenClasses);
        // le Java n'est jamais compilé par le pilote Kotlin
        assertFalse(result.writtenClasses.contains(
                "com/example/joint/Joint.class"),
                "java must not be codegen'd: " + result.writtenClasses);
    }

    @Test
    void ecjSkipsJrtWhenAndroidJarIsThePlatform() throws Exception {
        // Terrain du 2026-11-17 (app:generateRJar sur ART) : le
        // sondage jrt de java.home levait « IllegalStateException:
        // Failed to init packages for /apex/com.android.art ». Un jar
        // NOMMÉ android.jar au classpath doit court-circuiter ce
        // sondage — le discriminant : un faux javax.tools.ToolProvider
        // portant une méthode ABSENTE du vrai (si le jrt du bureau
        // était ajouté, il gagnerait la résolution et la sonde
        // échouerait).
        EcjCompilerDriver driver = new EcjCompilerDriver();
        // la doctrine ImageFree : java.lang.Object lui-même se lie aux
        // OCTETS du jar de plateforme — le jar factice l'embarque (lu
        // depuis le jrt du JDK de test) sous la fabrique fausse
        byte[] objectBytes;
        try (java.nio.file.FileSystem jrt =
                     java.nio.file.FileSystems.newFileSystem(
                             java.net.URI.create("jrt:/"),
                             java.util.Map.of())) {
            objectBytes = Files.readAllBytes(jrt.getPath(
                    "/modules/java.base/java/lang/Object.class"));
        }
        // phase 1 : un mini-plateforme (Object seul) — il court-circuite
        // le jrt du bureau et permet de compiler le FAUX ToolProvider
        // sans conflit de module (javax.tools existe dans java.compiler)
        Path platform = work.resolve("plateforme");
        Files.createDirectories(platform);
        Path miniJar = platform.resolve("android.jar");
        try (java.util.zip.ZipOutputStream zip =
                     new java.util.zip.ZipOutputStream(
                             Files.newOutputStream(miniJar))) {
            zip.putNextEntry(new java.util.zip.ZipEntry(
                    "java/lang/Object.class"));
            zip.write(objectBytes);
            zip.closeEntry();
        }
        Path fakeSrc = work.resolve("sonde-faux/ToolProvider.java");
        Files.createDirectories(fakeSrc.getParent());
        Files.writeString(fakeSrc, """
                package javax.tools;

                public class ToolProvider {
                    public static int marquePlateforme() {
                        return 7;
                    }
                }
                """);
        Path fakeOut = work.resolve("sonde-faux-out");
        CompileResult fake = driver.compile(CompileRequest.builder()
                .source(fakeSrc)
                .classpath(miniJar)
                .outputDir(fakeOut)
                .build());
        assertTrue(fake.success, "diagnostics: " + fake.diagnostics);

        // phase 2 : le plateforme complet (Object + la fabrique fausse)
        // — UN seul flux zip, les deux entrées
        Path androidJar = platform.resolve("android.jar");
        try (java.util.zip.ZipOutputStream zip =
                     new java.util.zip.ZipOutputStream(
                             Files.newOutputStream(androidJar))) {
            zip.putNextEntry(new java.util.zip.ZipEntry(
                    "java/lang/Object.class"));
            zip.write(objectBytes);
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry(
                    "javax/tools/ToolProvider.class"));
            zip.write(Files.readAllBytes(
                    fakeOut.resolve("javax/tools/ToolProvider.class")));
            zip.closeEntry();
        }
        Path probe = work.resolve("sonde-src/Sonde.java");
        Files.createDirectories(probe.getParent());
        Files.writeString(probe, """
                package com.example.sonde;

                public class Sonde {
                    public int sonde() {
                        return javax.tools.ToolProvider.marquePlateforme();
                    }
                }
                """);
        assertTrue(driver.compile(CompileRequest.builder()
                        .source(probe)
                        .classpath(androidJar)
                        .outputDir(work.resolve("sonde-out"))
                        .build()).success,
                "le jrt du bureau ne doit pas gagner la résolution");
        // contre-preuve : renommé, le jar n'est plus une plateforme —
        // le jrt du bureau revient et le vrai ToolProvider (sans la
        // méthode) fait échouer la sonde
        Path autreJar = platform.resolve("autre.jar");
        Files.copy(androidJar, autreJar);
        assertFalse(driver.compile(CompileRequest.builder()
                        .source(probe)
                        .classpath(autreJar)
                        .outputDir(work.resolve("sonde-out-2"))
                        .build()).success,
                "le jrt doit revenir hors android.jar");
    }

    @Test
    void ecjCompilesIndyAgainstPlatformStubs() throws Exception {
        // La doctrine éprouvée : android.jar OMET les fabriques
        // java.lang.invoke ; à compliance ≥ 9, la concaténation et les
        // lambdas émettent un invokedynamic qui ne se résout que si le
        // jar system-modules du plateau (sibling d'android.jar, le
        // rôle d'AGP core-lambda-stubs) est au classpath — jamais dexé,
        // D8 désugare l'indy.
        String sdk = System.getenv("ANDROID_HOME");
        Assumptions.assumeTrue(sdk != null,
                "ANDROID_HOME absent — SDK non installé");
        Path androidJar = Path.of(sdk, "platforms", "android-36",
                "android.jar");
        Assumptions.assumeTrue(Files.isRegularFile(androidJar),
                "android.jar absent du SDK local");
        Path systemModules = androidJar.getParent()
                .resolve("core-for-system-modules.jar");
        Assumptions.assumeTrue(Files.isRegularFile(systemModules),
                "core-for-system-modules.jar absent du plateau");

        Path src = work.resolve("indy-src/Concat.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, """
                package com.example.indy;

                import java.util.function.IntSupplier;

                public class Concat {
                    public String salut(String qui) {
                        IntSupplier deux = () -> 2;
                        return "salut " + qui + deux.getAsInt();
                    }
                }
                """);
        EcjCompilerDriver driver = new EcjCompilerDriver();
        Path out = work.resolve("indy-out");
        CompileResult withStubs = driver.compile(CompileRequest.builder()
                .source(src)
                .classpath(androidJar)
                .classpath(systemModules)
                .outputDir(out)
                .build());
        assertTrue(withStubs.success, "diagnostics: " + withStubs.diagnostics);
        byte[] bytes = Files.readAllBytes(
                out.resolve("com/example/indy/Concat.class"));
        // l'indy de concaténation est bien émis contre les stubs
        assertTrue(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
                        .contains("makeConcatWithConstants"),
                "indy string concat attendu");
        // sans les stubs, la bootstrap est irrésolvable : échec propre
        Path outBare = work.resolve("indy-bare-out");
        CompileResult bare = driver.compile(CompileRequest.builder()
                .source(src)
                .classpath(androidJar)
                .outputDir(outBare)
                .build());
        assertFalse(bare.success,
                "les fabriques doivent manquer sans le jar system-modules");
    }
}
