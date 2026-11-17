package jo.joide.build.jvm.tasks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.platform.RootName;
import jo.joide.platform.RootSet;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.TaskGraph;

/**
 * Le plateau d'appareil (lot XVIII, le terrain du 2026-11-17 :
 * « No class roots are found in the JDK path: /apex/com.android.art »
 * sur la route console JVM) : sur ART, kotlinc SANS -no-jdk sonde
 * java.home (ART, aucune racine JDK) et échoue ; la manière
 * CodeAssist — -no-jdk et android.jar DU CLASSPATH. La fabrique pose
 * donc le plateau dans les DEUX classpaths (kotlinc ET ecj lisent
 * java.* du classpath) et ne met -no-jdk QUE sur la tâche Kotlin.
 */
class JvmTaskFactoryTest {

    @Test
    void lePlateauPorteNoJdkSurKotlinEtLesDeuxClasspaths(
            @TempDir Path dossier) throws IOException {
        Path projet = dossier.resolve("projet");
        Path sorties = dossier.resolve("sorties");
        Path m2 = dossier.resolve("m2");
        Files.createDirectories(
                projet.resolve("console/src/main/kotlin"));
        // un vrai (petit) jar : le manifeste ABI du plateau se
        // calcule au montage du graphe (AbiCache lit le zip) — une
        // entrée NON-class suffit (EntryAbi ne parse que les
        // .class ; un faux bytecode serait rejeté, à raison)
        Path plateau = dossier.resolve("android.jar");
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(plateau))) {
            jar.putNextEntry(new ZipEntry("notes/plateau.txt"));
            jar.write("plateau de test".getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        Map<RootName, Path> ancres = new LinkedHashMap<>();
        ancres.put(RootName.PROJECT, projet);
        ancres.put(RootName.OUT, sorties);
        ancres.put(RootName.M2, m2);
        ancres.put(RootName.SDK, dossier.resolve("sdk"));
        JvmTaskFactory factory = new JvmTaskFactory(sorties, m2, "17")
                .withSourceAnchors(RootSet.of(ancres))
                .withPlateauAppareil(
                        RootedPath.of(RootName.SDK,
                                "android-35/android.jar"), plateau);
        JvmTaskFactory.ModuleSpec console =
                new JvmTaskFactory.ModuleSpec("console");
        console.sourceRoots.add(RootedPath.of(RootName.PROJECT,
                "console/src/main/kotlin"));
        factory.forceKotlin("console", true);
        factory.module(console);
        TaskGraph graph = factory.buildGraph();

        JvmCompileTask compileKotlin = (JvmCompileTask) graph.task(
                "console:compileKotlin");
        assertTrue(compileKotlin.core().spec().contains(
                        "|cp=EXTERNAL:platform:android.jar@"),
                "le plateau entre au classpath Kotlin : "
                        + compileKotlin.core().spec());
        assertTrue(compileKotlin.core().noJdk(),
                "la compilation Kotlin sur appareil porte -no-jdk "
                        + "(java.* vient du plateau, java.home d'ART "
                        + "n'a AUCUNE racine)");

        JvmCompileTask compileJava = (JvmCompileTask) graph.task(
                "console:compileJava");
        assertTrue(compileJava.core().spec().contains(
                        "|cp=EXTERNAL:platform:android.jar@"),
                "le plateau entre AUSSI au classpath Java (ecj lit "
                        + "java.* du classpath) : "
                        + compileJava.core().spec());
        assertFalse(compileJava.core().noJdk(),
                "le drapeau -no-jdk ne concerne QUE Kotlin (ecj n'a "
                        + "pas cette option)");
    }

    @Test
    void sansPlateauLeComportementBureauResteIntact(
            @TempDir Path dossier) throws IOException {
        Path projet = dossier.resolve("projet");
        Path sorties = dossier.resolve("sorties");
        Path m2 = dossier.resolve("m2");
        Files.createDirectories(
                projet.resolve("console/src/main/kotlin"));
        Map<RootName, Path> ancres = new LinkedHashMap<>();
        ancres.put(RootName.PROJECT, projet);
        ancres.put(RootName.OUT, sorties);
        ancres.put(RootName.M2, m2);
        JvmTaskFactory factory = new JvmTaskFactory(sorties, m2, "17")
                .withSourceAnchors(RootSet.of(ancres));
        JvmTaskFactory.ModuleSpec console =
                new JvmTaskFactory.ModuleSpec("console");
        console.sourceRoots.add(RootedPath.of(RootName.PROJECT,
                "console/src/main/kotlin"));
        factory.forceKotlin("console", true);
        factory.module(console);
        TaskGraph graph = factory.buildGraph();

        JvmCompileTask compileKotlin = (JvmCompileTask) graph.task(
                "console:compileKotlin");
        assertFalse(compileKotlin.core().spec().contains(
                        "platform:android.jar"),
                "au bureau, PAS de plateau au classpath : "
                        + compileKotlin.core().spec());
        assertFalse(compileKotlin.core().noJdk(),
                "au bureau, le JDK réel fournit les racines — PAS de "
                        + "-no-jdk (le comportement d'avant le lot "
                        + "XVIII reste entier)");
    }
}
