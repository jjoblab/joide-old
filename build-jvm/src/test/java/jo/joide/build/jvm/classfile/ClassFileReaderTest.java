package jo.joide.build.jvm.classfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Lecture de fichiers de classe réels : une classe JoIDE (javac 17),
 * une classe Kotlin (métadonnées visibles à l'exécution) et les
 * rejets attendus (mauvaise magie, troncature).
 */
class ClassFileReaderTest {

    private static ClassModel readClasspath(String slashedName)
            throws Exception {
        try (InputStream in = ClassFileReaderTest.class.getResourceAsStream(
                "/" + slashedName + ".class")) {
            assertNotNull(in, "class not found on test classpath: " + slashedName);
            byte[] bytes = in.readAllBytes();
            return ClassFileReader.read(bytes);
        }
    }

    @Test
    void readsPlatformClassWithMembersAndReferences() throws Exception {
        ClassModel model = readClasspath("jo/joide/platform/Hashing");
        assertEquals("jo/joide/platform/Hashing", model.binaryName);
        assertEquals("java/lang/Object", model.superName);
        assertEquals("Hashing.java", model.sourceFile);
        assertTrue(model.interfaces.isEmpty(),
                "Hashing implements no interface");
        assertTrue(model.referencedClasses.contains("java/lang/Object"));
        assertTrue(model.referencedClasses.contains("java/nio/ByteBuffer"));
        assertFalse(model.fields.isEmpty(), "Hashing has constant fields");
        // Les méthodes privées sont lisibles (et filtrées par l'ABI,
        // pas par le lecteur)
        assertTrue(model.methods.size() >= 5);
        List<String> names = new ArrayList<>();
        model.methods.forEach(m -> names.add(m.name));
        assertTrue(names.contains("sha256"), "methods: " + names);
        assertTrue(names.contains("toHex"), "methods: " + names);
    }

    @Test
    void readsKotlinClassWithRuntimeMetadata() throws Exception {
        ClassModel model = readClasspath("kotlin/collections/CollectionsKt");
        assertEquals("kotlin/collections/CollectionsKt", model.binaryName);
        // Façade multi-fichiers : la super-classe est la classe
        // sous-jacente de la même bibliothèque, pas Object
        assertEquals("kotlin/collections/CollectionsKt___CollectionsKt",
                model.superName);
        assertTrue(model.methods.size() >= 1,
                "facade carries at least its constructor");
        boolean hasMetadata = model.annotationAttributes.stream().anyMatch(
                a -> ClassModel.RUNTIME_VISIBLE.equals(a.name));
        assertTrue(hasMetadata, "kotlin class must carry runtime metadata");
        // La chaîne d'héritage de la façade est bien dans les références
        assertTrue(model.referencedClasses.contains(
                "kotlin/collections/CollectionsKt___CollectionsKt"));
    }

    @Test
    void rejectsBadMagic() {
        assertThrows(ClassFileException.class,
                () -> ClassFileReader.read(new byte[] {1, 2, 3, 4, 0, 0, 0, 0}));
    }

    @Test
    void rejectsTruncatedFile() {
        byte[] truncated = new byte[] {(byte) 0xCA, (byte) 0xFE,
                (byte) 0xBA, (byte) 0xBE, 0, 0};
        assertThrows(ClassFileException.class,
                () -> ClassFileReader.read(truncated));
    }
}
