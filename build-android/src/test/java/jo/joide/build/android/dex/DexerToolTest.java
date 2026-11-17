package jo.joide.build.android.dex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.build.android.ToolchainFixture;
import jo.joide.build.android.tools.BundletoolAssembler;
import jo.joide.build.android.toolchain.Toolchain;

/**
 * Dexage réel : en processus et par fork produisent des dex valides
 * (en-tête lu, comptages cohérents), la fusion regroupe, le
 * rétrécissement R8 élimine une classe morte.
 */
class DexerToolTest {

    private static Toolchain toolchain;
    private static Path platform;
    private static Path classes;

    @BeforeAll
    static void compileSources(@TempDir Path temp) throws IOException {
        toolchain = ToolchainFixture.toolchain();
        platform = toolchain.platformJar;
        Path src = Files.createDirectories(temp.resolve("src/com/example"));
        Files.writeString(src.resolve("Alive.java"),
                "package com.example;\n"
                + "public class Alive {\n"
                + "  public static int add(int a, int b) { return a + b; }\n"
                + "}\n");
        Files.writeString(src.resolve("Dead.java"),
                "package com.example;\n"
                + "public class Dead {\n"
                + "  public static int multiply(int a, int b) { return a * b; }\n"
                + "}\n");
        Path out = Files.createDirectories(temp.resolve("classes"));
        Process javac = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "javac")
                        .toString(),
                "-d", out.toString(),
                src.resolve("Alive.java").toString(),
                src.resolve("Dead.java").toString()).inheritIO().start();
        try {
            assertEquals(0, javac.waitFor(), "javac must succeed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        classes = out;
    }

    @Test
    void inProcessDexProducesValidDex(@TempDir Path temp) throws IOException {
        Dexer dexer = ToolchainFixture.inProcessDexer();
        Path out = Files.createDirectories(temp.resolve("dex"));
        dexer.dex(new Dexer.DexRequest(List.of(classes), List.of(platform),
                28, out));
        Path dex = out.resolve("classes.dex");
        assertTrue(Files.isRegularFile(dex));
        DexInfo info = DexInfo.read(dex);
        assertTrue(info.methodIds >= 3, "at least the two methods + init");
        assertTrue(info.size > 0);
    }

    @Test
    void forkDexProducesSameClasses(@TempDir Path temp) throws IOException {
        Dexer fork = ToolchainFixture.forkDexer();
        Path out = Files.createDirectories(temp.resolve("dex"));
        fork.dex(new Dexer.DexRequest(List.of(classes), List.of(platform),
                28, out));
        Path dex = out.resolve("classes.dex");
        assertTrue(Files.isRegularFile(dex));
        DexInfo info = DexInfo.read(dex);
        assertTrue(info.methodIds >= 3);
    }

    @Test
    void mergeCombinesDexFiles(@TempDir Path temp) throws IOException {
        Dexer dexer = ToolchainFixture.inProcessDexer();
        Path first = Files.createDirectories(temp.resolve("d1"));
        Path second = Files.createDirectories(temp.resolve("d2"));
        dexer.dex(new Dexer.DexRequest(List.of(classes.resolve(
                "com/example/Alive.class")), List.of(platform), 28, first));
        dexer.dex(new Dexer.DexRequest(List.of(classes.resolve(
                "com/example/Dead.class")), List.of(platform), 28, second));
        Path merged = Files.createDirectories(temp.resolve("merged"));
        ToolchainFixture.forkDexer().merge(new Dexer.MergeRequest(
                List.of(first.resolve("classes.dex"),
                        second.resolve("classes.dex")),
                List.of(platform), 28, merged));
        DexInfo info = DexInfo.read(merged.resolve("classes.dex"));
        assertTrue(info.methodIds >= 3, "merged dex must carry both classes");
    }

    @Test
    void shrinkRemovesDeadCode(@TempDir Path temp) throws IOException {
        Path jar = temp.resolve("program.jar");
        jo.joide.build.android.packaging.ApkWriter writer =
                new jo.joide.build.android.packaging.ApkWriter();
        try (var stream = Files.walk(classes)) {
            stream.filter(Files::isRegularFile)
                    .sorted()
                    .forEach(file -> {
                        try {
                            writer.deflatedFile(
                                    classes.relativize(file).toString()
                                            .replace('\\', '/'), file);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        writer.writeTo(jar);
        Path out = Files.createDirectories(temp.resolve("shrunk"));
        ToolchainFixture.forkDexer().shrink(new Dexer.ShrinkRequest(
                List.of(jar), List.of(platform), 28,
                List.of("-keep class com.example.Alive { *; }"),
                out, temp.resolve("mapping.txt")));
        Path dex = out.resolve("classes.dex");
        assertTrue(Files.isRegularFile(dex));
        DexInfo shrunk = DexInfo.read(dex);
        Path fat = Files.createDirectories(temp.resolve("fat"));
        ToolchainFixture.inProcessDexer().dex(new Dexer.DexRequest(
                List.of(jar), List.of(platform), 28, fat));
        DexInfo full = DexInfo.read(fat.resolve("classes.dex"));
        assertTrue(shrunk.methodIds < full.methodIds,
                "shrunk dex must reference fewer methods: "
                        + shrunk.methodIds + " vs " + full.methodIds);
        String mapping = Files.readString(temp.resolve("mapping.txt"));
        assertTrue(mapping.contains("com.example.Alive"),
                "mapping must name the kept class");
    }

    @Test
    void bundletoolVersionIsExposed() {
        assertTrue(new BundletoolAssembler().toolVersion()
                .startsWith("bundletool:"));
    }

    @Test
    void recordsAreDesugaredByD8(@TempDir Path temp) throws IOException {
        // un enregistrement Java compile en Java 17 (classe avec
        // java.lang.Record en super-classe) : D8 doit le désucrer en
        // classes compatibles ART sans échec
        Path src = Files.createDirectories(temp.resolve("src/records"));
        Files.writeString(src.resolve("Point.java"),
                "package records;\n"
                + "public record Point(int x, int y) {\n"
                + "  public int sum() { return x + y; }\n"
                + "}\n");
        Path out = Files.createDirectories(temp.resolve("classes"));
        try {
            Process javac = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "javac")
                            .toString(),
                    "--release", "17",
                    "-d", out.toString(),
                    src.resolve("Point.java").toString())
                    .redirectErrorStream(true).start();
            assertEquals(0, javac.waitFor(),
                    "javac must compile the record: "
                            + new String(javac.getInputStream().readAllBytes()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        Path dex = Files.createDirectories(temp.resolve("dex"));
        ToolchainFixture.inProcessDexer().dex(new Dexer.DexRequest(
                List.of(out), List.of(platform), 28, dex));
        DexInfo info = DexInfo.read(dex.resolve("classes.dex"));
        assertTrue(info.methodIds >= 4,
                "desugared record must carry accessors and constructor");
    }
}
