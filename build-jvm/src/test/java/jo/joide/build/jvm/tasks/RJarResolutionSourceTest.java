package jo.joide.build.jvm.tasks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.build.jvm.compile.CompileRequest;
import jo.joide.build.jvm.compile.CompileResult;
import jo.joide.build.jvm.compile.CompilerDriver;
import jo.joide.platform.CancelToken;
import jo.joide.platform.Clock;
import jo.joide.platform.Problem;
import jo.joide.platform.RootName;
import jo.joide.platform.RootSet;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.InputDelta;
import jo.joide.tasks.TaskContext;

/**
 * Lot XX/h — la doctrine CodeAssist au niveau de la requête : le
 * R.jar est classpath de la compilation JAVA, entrée d'empreinte
 * partout, et JAMAIS dans le -classpath du compilateur KOTLIN —
 * kotlinc résout R depuis les sources R.java du gen, passées comme
 * racines de résolution. Le terrain du 2026-11-18 (« Classpath entry
 * points to a non-existent location: app/r.jar » sur l'appareil,
 * avec le jar présent côté moteur ET côté daemon) devient
 * structurellement impossible : aucun jar R n'atteint kotlinc.
 */
class RJarResolutionSourceTest {

    @TempDir
    Path temp;

    /** Pilote qui enregistre la requête au lieu de compiler. */
    private static final class Recodeur implements CompilerDriver {
        CompileRequest vue;

        @Override
        public CompileResult compile(CompileRequest request) {
            this.vue = request;
            return new CompileResult(true, List.of(), List.of());
        }

        @Override
        public String toolVersion() {
            return "recordeur";
        }
    }

    /** Contexte minimal : la résolution RootSet suffit à compileRequest. */
    private static final class ContexteTest implements TaskContext {
        final RootSet roots;

        ContexteTest(RootSet roots) {
            this.roots = roots;
        }

        @Override
        public CancelToken cancellation() {
            return CancelToken.NEVER;
        }

        @Override
        public Clock clock() {
            return Clock.system();
        }

        @Override
        public InputDelta inputDelta() {
            return InputDelta.empty();
        }

        @Override
        public Path resolve(RootedPath path) {
            return roots.resolve(path);
        }

        @Override
        public RootSet roots() {
            return roots;
        }

        @Override
        public void report(Problem problem) {
            // sans objet ici
        }
    }

    private static ClasspathEntry entreeRJar() {
        return new ClasspathEntry(
                ClasspathEntry.Kind.EXTERNAL_R, "app:R.jar",
                RootedPath.of(RootName.OUT, "android/app/r.jar"),
                RootedPath.of(RootName.OUT,
                        "android/app/r.abi.manifest"),
                null);
    }

    private static Map<RootName, Path> anchors(Path out) {
        Map<RootName, Path> anchors = new LinkedHashMap<>();
        anchors.put(RootName.PROJECT, out.resolve("project"));
        anchors.put(RootName.OUT, out);
        return anchors;
    }

    @Test
    void kotlinClasspathCarriesNoRJarButCarriesGenSources()
            throws IOException {
        Path out = Files.createDirectories(temp.resolve("out"));
        RootSet roots = RootSet.of(anchors(out));
        Recodeur recodeur = new Recodeur();
        IncrementalJvmCompile core = new IncrementalJvmCompile(
                "app", "kotlin", "kt", List.of(), List.of(entreeRJar()),
                recodeur,
                List.of(RootedPath.of(RootName.OUT, "android/app/gen")),
                RootedPath.of(RootName.OUT, "jvm/app/classes-kotlin"),
                RootedPath.of(RootName.OUT, "jvm/app/abi-kotlin"),
                null, "17", "UTF-8", 0.30, false, true);
        CompileRequest requeteKotlin = core.compileRequest(
                new ContexteTest(roots), List.of(), out.resolve("k"));

        Path jarResolu = roots.resolve(
                RootedPath.of(RootName.OUT, "android/app/r.jar"));
        assertFalse(requeteKotlin.classpath.contains(jarResolu),
                "le -classpath du compilateur KOTLIN ne porte jamais "
                        + "le R.jar : " + requeteKotlin.classpath);
        Path genResolu = roots.resolve(
                RootedPath.of(RootName.OUT, "android/app/gen"));
        assertTrue(requeteKotlin.javaSourceRootsForResolution
                        .contains(genResolu),
                "kotlinc reçoit le gen des R.java pour la résolution : "
                        + requeteKotlin.javaSourceRootsForResolution);
    }

    @Test
    void javaClasspathKeepsTheRJar() throws IOException {
        Path out = Files.createDirectories(temp.resolve("out"));
        RootSet roots = RootSet.of(anchors(out));
        Recodeur recodeur = new Recodeur();
        IncrementalJvmCompile core = new IncrementalJvmCompile(
                "app", "java", "java", List.of(), List.of(entreeRJar()),
                recodeur, List.of(),
                RootedPath.of(RootName.OUT, "jvm/app/classes-java"),
                RootedPath.of(RootName.OUT, "jvm/app/abi-java"),
                null, "17", "UTF-8", 0.30, false, false);
        CompileRequest requeteJava = core.compileRequest(
                new ContexteTest(roots), List.of(), out.resolve("j"));

        Path jarResolu = roots.resolve(
                RootedPath.of(RootName.OUT, "android/app/r.jar"));
        assertTrue(requeteJava.classpath.contains(jarResolu),
                "la compilation JAVA garde le R.jar au classpath : "
                        + requeteJava.classpath);
    }

    @Test
    void fingerprintStillWatchesTheRJarManifest() throws IOException {
        Path out = Files.createDirectories(temp.resolve("out"));
        RootSet roots = RootSet.of(anchors(out));
        RootedPath manifest = RootedPath.of(RootName.OUT,
                "android/app/r.abi.manifest");
        // une entrée d'empreinte pointant dans le vide lèverait au
        // premier build : le manifeste existe ici
        Files.createDirectories(out.resolve("android/app"));
        Files.writeString(out.resolve("android/app/r.abi.manifest"),
                "digest");
        JvmCompileTask kotlin = JvmCompileTask.create("app", "kotlin",
                List.of(), List.of(entreeRJar()), new Recodeur(),
                List.of(RootedPath.of(RootName.OUT, "android/app/gen")),
                RootedPath.of(RootName.OUT, "jvm/app/classes-kotlin"),
                RootedPath.of(RootName.OUT, "jvm/app/abi-kotlin"),
                null, "17", true);
        assertTrue(kotlin.inputs().filePaths().contains(manifest),
                "le manifeste ABI du R.jar reste une entrée d'empreinte "
                        + "de la compilation Kotlin : "
                        + kotlin.inputs().filePaths());
    }
}
