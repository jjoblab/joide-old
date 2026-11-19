package jo.joide.build.android.tasks;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * L'émetteur bytecode du R.jar (la manière éprouvée
 * RBytecodeGenerator) : un R.java d'aapt2 est parse-é et ses
 * constantes émises en class files FAITS MAIN — le test charge les
 * octets émis par un ClassLoader et vérifie les valeurs PAR
 * RÉFLEXION (la preuve la plus forte : le class file est VALABLE, pas
 * seulement présent). Le flux complet (parse → émettre → jar →
 * manifeste ABI) est prouvé par les tâches du pipeline.
 */
class RBytecodeTest {

    @TempDir
    Path work;

    /** Un R.java de la FORME aapt2 : ids hex, tableaux multi-lignes. */
    private static final String R_AAPT2 = """
            /* AUTO-GENERATED FILE.  DO NOT MODIFY. */
            package com.exemple.app;

            public final class R {
                public static final class attr {
                    public static final int dividerVisible=0x7f010000;
                }
                public static final class id {
                    public static final int boutonValider=0x7f020001;
                    public static final int texteAccueil=0x7f020002;
                }
                public static final class styleable {
                    public static final int[] BarreTitre={
                        0x7f010001,
                        0x7f010002,
                        0x7f020001
                    };
                    public static final int BarreTitre_sousTitre=1;
                    public static final int BarreTitre_titre=0;
                }
            }
            """;

    @Test
    void parsesAapt2ShapeAndEmitsLoadableClasses() throws Exception {
        RBytecode.ModeleR modele = RBytecode.parse(R_AAPT2);
        assertEquals("com.exemple.app", modele.paquet);
        assertEquals(3, modele.types.size());
        RBytecode.TypeR styleable = modele.types.get(2);
        assertEquals("styleable", styleable.nom);
        assertEquals(1, styleable.tableaux.size());
        assertArrayEquals(new int[] {0x7f010001, 0x7f010002, 0x7f020001},
                styleable.tableaux.get(0).valeurs());

        int classes = RBytecode.emettre(modele, work);
        assertEquals(4, classes); // R externe + attr + id + styleable

        try (URLClassLoader loader = new URLClassLoader(
                new URL[] {work.toUri().toURL()}, null)) {
            Class<?> r = loader.loadClass("com.exemple.app.R");
            // l'attribut InnerClasses fait vivre les nichées
            assertEquals(3, r.getDeclaredClasses().length);
            Class<?> id = loader.loadClass("com.exemple.app.R$id");
            java.lang.reflect.Field bouton = id
                    .getDeclaredField("boutonValider");
            assertTrue(java.lang.reflect.Modifier.isFinal(
                    bouton.getModifiers()));
            assertEquals(0x7f020001, bouton.getInt(null));
            Class<?> styleableClasse = loader
                    .loadClass("com.exemple.app.R$styleable");
            Object tableau = styleableClasse
                    .getDeclaredField("BarreTitre").get(null);
            assertArrayEquals(new int[] {0x7f010001, 0x7f010002,
                    0x7f020001}, (int[]) tableau);
            assertEquals(1, styleableClasse
                    .getDeclaredField("BarreTitre_sousTitre")
                    .getInt(null));
        }
    }

    @Test
    void emittedBytesAreDeterministic() throws Exception {
        Path a = work.resolve("a");
        Path b = work.resolve("b");
        RBytecode.emettre(RBytecode.parse(R_AAPT2), a);
        RBytecode.emettre(RBytecode.parse(R_AAPT2), b);
        List<Path> filesA = Files.walk(a).filter(
                p -> p.toString().endsWith(".class")).sorted().toList();
        List<Path> filesB = Files.walk(b).filter(
                p -> p.toString().endsWith(".class")).sorted().toList();
        assertEquals(filesA.size(), filesB.size());
        for (int i = 0; i < filesA.size(); i++) {
            assertEquals(-1L, Files.mismatch(filesA.get(i),
                    filesB.get(i)),
                    filesA.get(i).getFileName().toString());
        }
    }

    @Test
    void abiReaderParsesEmittedClasses() throws Exception {
        // le manifeste ABI aval lit nos class files faits main : le
        // lecteur du dépôt doit les prendre sans broncher
        RBytecode.emettre(RBytecode.parse(R_AAPT2), work);
        jo.joide.build.jvm.abi.AbiManifest manifest =
                jo.joide.build.jvm.abi.EntryAbi.compute(work, 200_000);
        assertNotNull(manifest);
        assertTrue(manifest.text().contains("com/exemple/app/R$id"),
                "abi: " + manifest.text());
    }

    @Test
    void unparseableLinesAreSkippedNotFatal() throws Exception {
        String fragile = """
                package com.exemple.fr;
                // une ligne d'entête hors forme
                public final class R {
                    public static final class id {
                        public static final int correct=0x7f020001;
                        public static final int casse=;
                    }
                }
                """;
        RBytecode.ModeleR modele = RBytecode.parse(fragile);
        assertEquals("com.exemple.fr", modele.paquet);
        assertEquals(1, modele.types.get(0).ints.size());
    }
}
