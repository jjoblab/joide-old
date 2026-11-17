package jo.joide.build.jvm.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.platform.RootName;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.TaskGraph;
import jo.joide.tasks.TaskStatus;

/**
 * Test différentiel obligatoire (§7 P3) : sur 200 séquences
 * d'éditions aléatoires à graine fixe — corps de méthode, signature
 * publique ajoutée, constante, corps de fonction inline, ajout et
 * suppression de classe, renommage de paquet d'un fichier feuille —
 * le build incrémental et le build propre produisent les mêmes
 * classes et les mêmes jars, octet pour octet.
 */
class DifferentialTest {

    /** Graine maîtresse, affichée en cas d'échec. */
    private static final long SEED = 20260311L;

    /** Nombre de séquences (le critère demande au moins 200). */
    private static final int SEQUENCES = 200;

    @TempDir
    Path tempDir;

    private static Path stdlibJar;

    @BeforeAll
    static void locateStdlib() {
        String classpath = System.getProperty("java.class.path");
        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            if (entry.contains("kotlin-stdlib")) {
                stdlibJar = Path.of(entry);
                return;
            }
        }
        stdlibJar = null;
    }

    /** Journal d'une séquence : fichiers ajoutés/modifiés/supprimés. */
    private static final class Edits {
        final Set<String> added = new TreeSet<>();
        final Set<String> modified = new TreeSet<>();
        final Set<String> removed = new TreeSet<>();

        void touch(String path) {
            if (!removed.remove(path)) {
                if (added.contains(path)) {
                    // toujours nouveau : reste dans « ajouté »
                } else {
                    modified.add(path);
                }
            }
        }

        void add(String path) {
            if (removed.remove(path)) {
                modified.add(path);
            } else {
                added.add(path);
            }
        }

        void remove(String path) {
            if (added.remove(path)) {
                return; // jamais compilé : pas sale
            }
            modified.remove(path);
            removed.add(path);
        }
    }

    private static List<JvmTaskFactory.ModuleSpec> modules() {
        JvmTaskFactory.ModuleSpec base = new JvmTaskFactory.ModuleSpec(
                "base");
        base.sourceRoots.add(RootedPath.of(RootName.PROJECT,
                "base/src/main/java"));
        base.sourceRoots.add(RootedPath.of(RootName.PROJECT,
                "base/src/main/kotlin"));
        JvmTaskFactory.ModuleSpec mid = new JvmTaskFactory.ModuleSpec("mid");
        mid.sourceRoots.add(RootedPath.of(RootName.PROJECT,
                "mid/src/main/java"));
        mid.sourceRoots.add(RootedPath.of(RootName.PROJECT,
                "mid/src/main/kotlin"));
        mid.moduleDependencies.add("base");
        JvmTaskFactory.ModuleSpec app = new JvmTaskFactory.ModuleSpec("app");
        app.sourceRoots.add(RootedPath.of(RootName.PROJECT,
                "app/src/main/java"));
        app.sourceRoots.add(RootedPath.of(RootName.PROJECT,
                "app/src/main/kotlin"));
        app.moduleDependencies.add("mid");
        app.moduleDependencies.add("base");
        List<JvmTaskFactory.ModuleSpec> modules = new ArrayList<>();
        modules.add(base);
        modules.add(mid);
        modules.add(app);
        return modules;
    }

    private static void writeInitialProject(Path project) throws IOException {
        write(project, "base/src/main/java/com/example/base/Base.java", """
                package com.example.base;

                public class Base {
                    public static final int LIMIT = 10;

                    public int calc(int value) {
                        return value * 2 + LIMIT;
                    }

                    public String label(String input) {
                        return "[" + input + "]";
                    }
                }
                """);
        write(project, "base/src/main/kotlin/com/example/base/BaseKt.kt", """
                package com.example.base

                const val MAGIC = 7

                inline fun tag(value: String): String = "<$value>"

                class Holder(val seed: Int) {
                    fun describe(): String = "seed=$seed"
                }
                """);
        write(project, "base/src/main/java/com/example/base/Leaf.java", """
                package com.example.base;

                public class Leaf {
                    public int unused() {
                        return 1;
                    }
                }
                """);
        write(project, "mid/src/main/java/com/example/mid/Mid.java", """
                package com.example.mid;

                import com.example.base.Base;

                public class Mid {
                    public int twice(int value) {
                        return new Base().calc(value) * 2;
                    }
                }
                """);
        write(project, "mid/src/main/kotlin/com/example/mid/MidKt.kt", """
                package com.example.mid

                import com.example.base.Holder
                import com.example.base.MAGIC
                import com.example.base.tag

                class MidTool(private val holder: Holder) {
                    fun render(input: String): String =
                        tag(holder.describe()) + MAGIC
                }
                """);
        write(project, "app/src/main/java/com/example/app/App.java", """
                package com.example.app;

                import com.example.base.Base;
                import com.example.mid.Mid;

                public class App {
                    public String run(String name) {
                        int value = new Mid().twice(name.length());
                        return new Base().label(name) + value;
                    }
                }
                """);
        write(project, "app/src/main/kotlin/com/example/app/AppKt.kt", """
                package com.example.app

                import com.example.mid.MidTool
                import com.example.base.Holder

                class AppTool {
                    fun show(): String = MidTool(Holder(3)).render("app")
                }
                """);
    }

    private static void write(Path project, String relative, String content)
            throws IOException {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /** Toutes les éditions maintiennent le projet compilable. */
    private static void applyEdit(Path project, Random rng, Edits edits)
            throws IOException {
        int kind = rng.nextInt(8);
        switch (kind) {
            case 0, 1 -> {
                // corps de méthode (sans changement d'ABI)
                int factor = 2 + rng.nextInt(6);
                write(project,
                        "base/src/main/java/com/example/base/Base.java", """
                                package com.example.base;

                                public class Base {
                                    public static final int LIMIT = 10;

                                    public int calc(int value) {
                                        return value * %d + LIMIT;
                                    }

                                    public String label(String input) {
                                        return "[" + input + "]";
                                    }
                                }
                                """.formatted(factor));
                edits.touch("base/src/main/java/com/example/base/Base.java");
            }
            case 2 -> {
                // constante inlinable
                int limit = 1 + rng.nextInt(50);
                write(project,
                        "base/src/main/java/com/example/base/Base.java", """
                                package com.example.base;

                                public class Base {
                                    public static final int LIMIT = %d;

                                    public int calc(int value) {
                                        return value * 2 + LIMIT;
                                    }

                                    public String label(String input) {
                                        return "[" + input + "]";
                                    }
                                }
                                """.formatted(limit));
                edits.touch("base/src/main/java/com/example/base/Base.java");
            }
            case 3 -> {
                // signature publique AJOUTÉE (ABI changée, code valide)
                write(project,
                        "base/src/main/java/com/example/base/Base.java", """
                                package com.example.base;

                                public class Base {
                                    public static final int LIMIT = 10;

                                    public int calc(int value) {
                                        return value * 2 + LIMIT;
                                    }

                                    public int calc(int value, int bonus) {
                                        return calc(value) + bonus;
                                    }

                                    public String label(String input) {
                                        return "[" + input + "]";
                                    }
                                }
                                """);
                edits.touch("base/src/main/java/com/example/base/Base.java");
            }
            case 4 -> {
                // corps de fonction inline (ABI Kotlin)
                String decorate = switch (rng.nextInt(3)) {
                    case 0 -> "\"<\" + value + \">\"";
                    case 1 -> "\"[\" + value + \"]\"";
                    default -> "\"{\" + value + \"}\"";
                };
                write(project,
                        "base/src/main/kotlin/com/example/base/BaseKt.kt", """
                                package com.example.base

                                const val MAGIC = 7

                                inline fun tag(value: String): String = %s

                                class Holder(val seed: Int) {
                                    fun describe(): String = "seed=$seed"
                                }
                                """.formatted(decorate));
                edits.touch("base/src/main/kotlin/com/example/base/BaseKt.kt");
            }
            case 5 -> {
                // ajout d'une classe publique non référencée
                int id = rng.nextInt(1000);
                String module = rng.nextBoolean() ? "base" : "mid";
                String path = module + "/src/main/java/com/example/"
                        + module + "/Extra" + id + ".java";
                write(project, path, """
                        package com.example.%s;

                        public class Extra%d {
                            public long probe() {
                                return %dL;
                            }
                        }
                        """.formatted(module, id, 42L + id));
                edits.add(path);
            }
            case 6 -> {
                // suppression d'une classe ajoutée précédemment
                Set<String> candidates = new TreeSet<>();
                try (var stream = Files.walk(project)) {
                    stream.filter(p -> p.getFileName().toString()
                            .startsWith("Extra")).forEach(
                                    p -> candidates.add(project.relativize(p)
                                            .toString().replace('\\', '/')));
                }
                if (!candidates.isEmpty()) {
                    String victim = new ArrayList<>(candidates)
                            .get(rng.nextInt(candidates.size()));
                    Files.deleteIfExists(project.resolve(victim));
                    edits.remove(victim);
                } else {
                    applyEdit(project, rng, edits);
                }
            }
            default -> {
                // renommage de paquet du fichier feuille (aucun référent) :
                // le Leaf courant (où qu'il soit) disparaît, un seul nouveau
                // apparaît — sinon les fichiers s'accumulent et l'édition
                // ne teste plus un renommage
                Set<String> current = new TreeSet<>();
                try (var stream = Files.walk(project)) {
                    stream.filter(p -> p.getFileName().toString()
                            .equals("Leaf.java")).forEach(
                                    p -> current.add(project.relativize(p)
                                            .toString().replace('\\', '/')));
                }
                for (String old : current) {
                    Files.deleteIfExists(project.resolve(old));
                    edits.remove(old);
                }
                String pkg = "com.example.base.p" + rng.nextInt(100);
                String to = "base/src/main/java/"
                        + pkg.replace('.', '/') + "/Leaf.java";
                write(project, to, """
                        package %s;

                        public class Leaf {
                            public int unused() {
                                return 1;
                            }
                        }
                        """.formatted(pkg));
                edits.add(to);
            }
        }
    }

    @Test
    void incrementalMatchesCleanBuildAcrossRandomEditSequences()
            throws Exception {
        assertNotNull(stdlibJar, "kotlin-stdlib must be on the classpath");
        Path project = Files.createDirectories(tempDir.resolve("project"));
        writeInitialProject(project);
        List<JvmTaskFactory.ModuleSpec> specs = modules();

        // build propre initial (référence de l'état incrémental)
        Path incOut = Files.createDirectories(tempDir.resolve("inc-out"));
        try (PipelineHarness inc = new PipelineHarness(project, incOut,
                specs, stdlibJar)) {
            TaskGraph graph = inc.graph();
            var first = inc.build(graph, PipelineHarness.dirty(
                    PipelineHarness.allProjectFiles(project), Set.of(),
                    Set.of()));
            assertTrue(first.successful(), () -> first.failures().toString());

            Random rng = new Random(SEED);
            for (int sequence = 1; sequence <= SEQUENCES; sequence++) {
                Edits edits = new Edits();
                int editCount = 1 + rng.nextInt(3);
                for (int e = 0; e < editCount; e++) {
                    applyEdit(project, rng, edits);
                }
                int seq = sequence;
                var incremental = inc.build(graph, PipelineHarness.dirty(
                        edits.added, edits.modified, edits.removed));
                assertTrue(incremental.successful(),
                        () -> "seed=" + SEED + " sequence=" + seq
                                + " incremental failed: "
                                + incremental.failures());

                // build propre de référence dans un répertoire neuf
                Path cleanOut = Files.createDirectories(
                        tempDir.resolve("clean-" + sequence));
                try (PipelineHarness clean = new PipelineHarness(project,
                        cleanOut, specs, stdlibJar)) {
                    var reference = clean.build(clean.graph(),
                            PipelineHarness.dirty(
                                    PipelineHarness.allProjectFiles(project),
                                    Set.of(), Set.of()));
                    assertTrue(reference.successful(),
                            () -> "seed=" + SEED + " sequence=" + seq
                                    + " clean failed: "
                                    + reference.failures());
                    assertOutputsIdentical(incOut, cleanOut, seq);
                }
            }
        }
    }

    /** Compare classes et jars des deux builds, octet pour octet. */
    private void assertOutputsIdentical(Path incremental, Path clean,
                                        int sequence) throws IOException {
        Map<String, byte[]> incFiles = snapshot(incremental.resolve("jvm"));
        Map<String, byte[]> cleanFiles = snapshot(clean.resolve("jvm"));
        Set<String> incOnly = new TreeSet<>(incFiles.keySet());
        incOnly.removeAll(cleanFiles.keySet());
        Set<String> cleanOnly = new TreeSet<>(cleanFiles.keySet());
        cleanOnly.removeAll(incFiles.keySet());
        // les manifestes ABI sont adressés par condensé : leurs noms
        // de fichiers diffèrent légitimement ; on compare les
        // répertoires par contenu après normalisation des noms
        assertTrue(incOnly.stream().allMatch(p -> p.endsWith(".manifest")
                        || p.endsWith("/current") || p.contains("abi-cache")
                        || p.endsWith("compile-state.bin")),
                () -> "seed=" + SEED + " sequence=" + sequence
                        + " files only in incremental: " + incOnly);
        assertTrue(cleanOnly.stream().allMatch(p -> p.endsWith(".manifest")
                        || p.endsWith("/current") || p.contains("abi-cache")
                        || p.endsWith("compile-state.bin")),
                () -> "seed=" + SEED + " sequence=" + sequence
                        + " files only in clean: " + cleanOnly);
        for (Map.Entry<String, byte[]> entry : incFiles.entrySet()) {
            byte[] other = cleanFiles.get(entry.getKey());
            if (other == null) {
                continue; // déjà couvert par incOnly
            }
            String key = entry.getKey();
            if (key.endsWith(".manifest") || key.endsWith("/current")
                    || key.contains("abi-cache")
                    || key.endsWith("compile-state.bin")) {
                continue; // métadonnées d'adressage : comparées via les
                           // classes et jars qu'elles décrivent
            }
            if (!java.util.Arrays.equals(entry.getValue(), other)) {
                byte[] a = entry.getValue();
                byte[] b = other;
                int i = 0;
                while (i < Math.min(a.length, b.length) && a[i] == b[i]) {
                    i++;
                }
                System.out.println("BYTEOFF " + key + " at " + i
                        + " of " + a.length);
                System.out.println("  a: " + hex(a, i));
                System.out.println("  b: " + hex(b, i));
            }
            assertTrue(java.util.Arrays.equals(entry.getValue(), other),
                    () -> "seed=" + SEED + " sequence=" + sequence
                            + " differs: " + key + " ("
                            + entry.getValue().length + " vs "
                            + (other == null ? -1 : other.length) + ")");
        }
    }

    private static String hex(byte[] bytes, int at) {
        StringBuilder out = new StringBuilder();
        for (int i = Math.max(0, at - 8); i < Math.min(bytes.length, at + 16); i++) {
            out.append(String.format("%02x ", bytes[i]));
        }
        return out.toString();
    }

    private static Map<String, byte[]> snapshot(Path dir) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        if (!Files.isDirectory(dir)) {
            return files;
        }
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                try {
                    files.put(dir.relativize(p).toString().replace('\\', '/'),
                            Files.readAllBytes(p));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
        return files;
    }

}
