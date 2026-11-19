package jo.joide.build.android.tasks;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.build.android.toolchain.KotlinForkedDriver;
import jo.joide.build.jvm.compile.CompileRequest;
import jo.joide.build.jvm.compile.CompileResult;
import jo.joide.build.jvm.worker.KotlinWorkerMain;

/**
 * Lot XX/f — le R.jar lu PAR LA FOURCHE, puis RÉÉCRIT et relu : le
 * flux exact du terrain (chaque build réexécute generateRJar, le
 * replace-existing passe sous le même chemin, compileKotlin du build
 * suivant consomme le jar neuf).
 *
 * <p>Le terrain du 2026-11-18 : « Classpath entry points to a
 * non-existent location: app/r.jar » + « Unresolved reference 'R' »
 * dès le PREMIER build appareil atteignant compileKotlin — l'ouvrière
 * était fraîche, le problème vivait dans les OCTETS du jar : JarWriter
 * sortait un zip d'APK (ApkWriter, entrées STORED alignées pour
 * l'mmap de resources.arsc) ; sur ART, le ZipFile du compilateur
 * (libziparchive) est plus strict que le bureau, et kotlinc nomme un
 * jar inouvrable « non-existent location ». Le fix : JarWriter écrit
 * le jar STANDARD du JDK (JarOutputStream, manifeste) — c'est ce que
 * ce test prouve, chaîne réelle RBytecode → JarWriter comprise.</p>
 */
class RJarForkedReadTest {

    @TempDir
    static Path dossier;

    private static String ancienneVm;
    private static String ancienCp;

    @BeforeAll
    static void brancher() throws Exception {
        ancienneVm = System.setProperty("joide.kotlinVm",
                Path.of(System.getProperty("java.home"), "bin", "java")
                        .toString());
        ancienCp = System.setProperty("joide.kotlinWorkerCp",
                System.getProperty("java.class.path")
                        + File.pathSeparator
                        + jarDe(KotlinWorkerMain.class));
    }

    @AfterAll
    static void debrancher() {
        if (ancienneVm != null) {
            System.setProperty("joide.kotlinVm", ancienneVm);
        } else {
            System.clearProperty("joide.kotlinVm");
        }
        if (ancienCp != null) {
            System.setProperty("joide.kotlinWorkerCp", ancienCp);
        } else {
            System.clearProperty("joide.kotlinWorkerCp");
        }
    }

    /** Un R.java aapt2 (texte) : les mêmes lignes que l'outil pose. */
    private static String rJava(String... symboles) {
        StringBuilder corps = new StringBuilder();
        for (int i = 0; i < symboles.length; i++) {
            corps.append("        public static final int ")
                    .append(symboles[i]).append("=0x7f01000")
                    .append(i + 1).append(";\n");
        }
        return "/* AUTO-GENERATED FILE. DO NOT MODIFY. */\n"
                + "package com.example.app;\n"
                + "public final class R {\n"
                + "    public static final class string {\n"
                + corps
                + "    }\n"
                + "}\n";
    }

    /** La chaîne RÉELLE de generateRJar : R.java texte → parse →
     *  bytecode émis → jar standard du JDK écrit. */
    private static Path fabriquerRJar(String rJava, Path classes,
                                      Path rJar) throws Exception {
        Files.createDirectories(classes);
        int emis = RBytecode.emettre(RBytecode.parse(rJava), classes);
        assertTrue(emis >= 2,
                "l'émetteur doit poser R et R$string : " + emis);
        JarWriter.write(classes, rJar);
        return rJar;
    }

    /**
     * Le jar de la chaîne réelle s'ouvre chez kotlinc PAR LA FOURCHE
     * (R se résout), puis — réécrit avec un NOUVEAU symbole — se relit
     * par la MÊME ouvrière : le flux du terrain, octets neufs compris.
     */
    @Test
    void leJarRSeLitParLaFourcheEtSeRelitApresReecriture()
            throws Exception {
        Path sortie = dossier.resolve("sortie-r");
        Files.createDirectories(sortie);
        Path classes = dossier.resolve("r-classes");
        Path rJar = dossier.resolve("r.jar");
        fabriquerRJar(rJava("greeting"), classes, rJar);

        // l'audit structurel : un JarFile l'ouvre et le manifeste vit
        // (la manière éprouvée — un jar porte META-INF/MANIFEST.MF)
        try (JarFile jarFile = new JarFile(rJar.toFile())) {
            assertNotNull(jarFile.getManifest(),
                    "le jar standard porte son manifeste");
            assertNotNull(jarFile.getJarEntry("com/example/app/R.class"),
                    "la classe R vit dans le jar");
        }

        KotlinForkedDriver pilote = new KotlinForkedDriver();
        Path source = dossier.resolve("Activite.kt");
        Files.writeString(source, "package com.example.app\n\n"
                + "class Activite {\n"
                + "    fun libelle(): String = \"a:\""
                + " + R.string.greeting\n"
                + "}\n");
        CompileResult premier = pilote.compile(new CompileRequest(
                List.of(source),
                List.of(jarDe(kotlin.KotlinVersion.class), rJar),
                List.of(), sortie, "17", "UTF-8", false));
        assertTrue(premier.success,
                () -> "R doit se résoudre PAR LA FOURCHE : "
                        + premier.diagnostics);
        assertTrue(Files.isRegularFile(
                        sortie.resolve("com/example/app/Activite.class")),
                "la classe Kotlin écrite manque : "
                        + premier.writtenClasses);

        // le jar est RÉÉCRIT sous le MÊME chemin (un NOUVEAU symbole :
        // les octets changent, comme un generateRJar d'un build
        // suivant) et la MÊME ouvrière, sans reset, relit le neuf
        Path classes2 = dossier.resolve("r-classes-2");
        Path rJar2 = rJar;
        fabriquerRJar(rJava("greeting", "app_name"), classes2, rJar2);
        Path source2 = dossier.resolve("Activite2.kt");
        Files.writeString(source2, "package com.example.app\n\n"
                + "class Activite2 {\n"
                + "    fun libelle(): String = \"b:\""
                + " + R.string.app_name\n"
                + "}\n");
        CompileResult second = pilote.compile(new CompileRequest(
                List.of(source2),
                List.of(jarDe(kotlin.KotlinVersion.class), rJar),
                List.of(), sortie, "17", "UTF-8", false));
        assertTrue(second.success,
                () -> "le jar réécrit (symbole neuf) doit se relire "
                        + "par la même ouvrière : " + second.diagnostics);
        assertTrue(Files.isRegularFile(
                        sortie.resolve("com/example/app/Activite2.class")),
                "la seconde classe écrite manque : "
                        + second.writtenClasses);
    }

    /** Les entrées de classpath absentes se NOMMENT avant la fourche
     *  (le diagnostic du lot XX/f) : plus jamais un vague kotlinc. */
    @Test
    void lesEntreesAbsentesSeNomment(@TempDir Path local)
            throws Exception {
        Path sortie = local.resolve("sortie-absente");
        Files.createDirectories(sortie);
        Path source = local.resolve("Salut.kt");
        Files.writeString(source, "package demo\n\n"
                + "fun salut(): Int = 40 + 2\n");
        Path fantome = local.resolve("jar-fantome.jar");
        KotlinForkedDriver pilote = new KotlinForkedDriver();
        CompileResult resultat = pilote.compile(new CompileRequest(
                List.of(source),
                List.of(jarDe(kotlin.KotlinVersion.class), fantome),
                List.of(), sortie, "17", "UTF-8", false));
        assertTrue(!resultat.success,
                "une entrée absente doit échouer nommé");
        assertTrue(resultat.diagnostics.stream().anyMatch(p ->
                        "classpath.missing".equals(p.code())
                                && p.message().contains(
                                        "jar-fantome.jar")),
                "l'entrée absente doit se nommer : "
                        + resultat.diagnostics);
    }

    private static Path jarDe(Class<?> classe) throws Exception {
        return Path.of(classe.getProtectionDomain().getCodeSource()
                .getLocation().toURI());
    }
}
