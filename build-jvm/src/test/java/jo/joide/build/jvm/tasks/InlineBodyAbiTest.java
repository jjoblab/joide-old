package jo.joide.build.jvm.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import jo.joide.build.jvm.abi.ClassAbi;
import jo.joide.build.jvm.classfile.ClassFileReader;
import jo.joide.build.jvm.classfile.ClassModel;
import jo.joide.build.jvm.compile.CompileRequest;
import jo.joide.build.jvm.compile.CompileResult;
import jo.joide.build.jvm.compile.EcjCompilerDriver;
import jo.joide.build.jvm.compile.KotlinCompilerDriver;

/**
 * Régression P3 (§6.4, cas particuliers Kotlin) : le corps d'une
 * fonction {@code inline} fait partie de l'ABI — il est copié dans
 * les appelants sans leur laisser de référence. Mesure fondatrice :
 * les métadonnées Kotlin 2.1.0 ne portent PAS les corps (condensé
 * brut identique entre deux corps différents) ; l'ABI des classes à
 * membres inline inclut donc le condensé du fichier de classe entier.
 *
 * <p>La réciproque est gardée aussi : un corps de méthode Java ne
 * change PAS l'ABI (compile avoidance des corps, §7 P3).</p>
 */
class InlineBodyAbiTest {

    /** Localise kotlin-stdlib sur le classpath d'exécution des tests. */
    private static Path stdlib() {
        String classpath = System.getProperty("java.class.path");
        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            if (entry.contains("kotlin-stdlib")) {
                return Path.of(entry);
            }
        }
        return null;
    }

    private static Path writeSource(Path dir, String name, String body)
            throws IOException {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, body);
        return file;
    }

    private static final String FACADE_TEMPLATE = """
            package com.example.base

            const val MAGIC = 7

            inline fun tag(value: String): String = %s

            class Holder(val seed: Int) {
                fun describe(): String = "seed=$seed"
            }
            """;

    /**
     * Compile la façade et relit la classe produite ; le compilateur
     * Kotlin écrit la façade de fichier BaseKtKt.class.
     */
    private static ClassModel compileFacade(Path work, String decorate)
            throws IOException {
        Path dir = Files.createTempDirectory(work, "facade");
        Path kt = writeSource(dir, "com/example/base/BaseKt.kt",
                FACADE_TEMPLATE.formatted(decorate));
        KotlinCompilerDriver driver = new KotlinCompilerDriver();
        CompileResult result = driver.compile(CompileRequest.builder()
                .source(kt).classpath(stdlib())
                .outputDir(dir.resolve("out")).build());
        assertTrue(result.success, () -> result.diagnostics.toString());
        return ClassFileReader.read(Files.readAllBytes(
                dir.resolve("out/com/example/base/BaseKtKt.class")));
    }

    @Test
    void inlineBodyChangeChangesAbi() throws IOException {
        assertNotNull(stdlib(), "kotlin-stdlib must be on the classpath");
        Path work = Files.createTempDirectory("inline-abi");
        ClassModel angles = compileFacade(work, "\"<\" + value + \">\"");
        ClassModel brackets = compileFacade(work, "\"[\" + value + \"]\"");
        ClassAbi anglesAbi = ClassAbi.of(angles);
        ClassAbi bracketsAbi = ClassAbi.of(brackets);
        // le corps inline est dans l'ABI : deux corps différents, deux
        // hachages différents (échouait avant le correctif : hachages
        // identiques, manifestes égaux, appelants avals jamais recompilés)
        assertNotEquals(anglesAbi.hash(), bracketsAbi.hash(),
                "inline body must be part of the facade ABI");
        assertTrue(anglesAbi.hasInlineMembers());
        assertTrue(ClassAbi.isInlineFlagged(anglesAbi.manifestValue()));
        assertTrue(ClassAbi.isInlineFlagged(bracketsAbi.manifestValue()));
    }

    @Test
    void sameInlineBodyCompilesToSameAbi() throws IOException {
        assertNotNull(stdlib(), "kotlin-stdlib must be on the classpath");
        Path work = Files.createTempDirectory("inline-abi");
        ClassModel first = compileFacade(work, "\"{\" + value + \"}\"");
        ClassModel second = compileFacade(work, "\"{\" + value + \"}\"");
        // compilateur déterministe : même source, même ABI — sinon la
        // moindre recompilation invaliderait l'aval (sur-invalidation)
        assertEquals(ClassAbi.of(first).hash(), ClassAbi.of(second).hash());
    }

    @Test
    void javaMethodBodyChangeKeepsAbi() throws IOException {
        Path dir = Files.createTempDirectory("java-abi");
        String template = """
                package com.example.base;

                public class Base {
                    public int calc(int value) {
                        return value * %d;
                    }
                }
                """;
        ClassModel three = compileJava(dir, template.formatted(3));
        ClassModel five = compileJava(dir, template.formatted(5));
        // corps de méthode : hors ABI — modifier un corps en amont ne
        // recompile pas l'aval (critère d'acceptation P3)
        assertEquals(ClassAbi.of(three).hash(), ClassAbi.of(five).hash());
        assertTrue(!ClassAbi.of(three).hasInlineMembers());
    }

    private static ClassModel compileJava(Path work, String source)
            throws IOException {
        Path dir = Files.createTempDirectory(work, "java");
        Path java = writeSource(dir, "com/example/base/Base.java", source);
        EcjCompilerDriver driver = new EcjCompilerDriver();
        CompileResult result = driver.compile(CompileRequest.builder()
                .source(java).classpath(stdlib())
                .outputDir(dir.resolve("out")).build());
        assertTrue(result.success, () -> result.diagnostics.toString());
        return ClassFileReader.read(Files.readAllBytes(
                dir.resolve("out/com/example/base/Base.class")));
    }
}
