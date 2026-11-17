package jo.joide.build.jvm.abi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.build.jvm.classfile.ClassFileReader;
import jo.joide.build.jvm.classfile.ClassModel;

/**
 * ABI par classe : le corps d'une méthode ne compte pas, la signature
 * et les constantes comptent ; manifeste déterministe et cache.
 */
class AbiTest {

    private static ClassModel model(String slashedName) throws Exception {
        try (InputStream in = AbiTest.class.getResourceAsStream(
                "/" + slashedName + ".class")) {
            assertNotNull(in, "missing class: " + slashedName);
            return ClassFileReader.read(in.readAllBytes());
        }
    }

    @Test
    void sameClassGivesSameAbi() throws Exception {
        ClassAbi first = ClassAbi.of(model("jo/joide/platform/Hashing"));
        ClassAbi second = ClassAbi.of(model("jo/joide/platform/Hashing"));
        assertEquals(first.hash(), second.hash());
        assertEquals(32, first.hash().length());
    }

    @Test
    void kotlinFactsAreExtractedFromClasspath() throws Exception {
        ClassModel pair = model("kotlin/Pair");
        KotlinFacts facts = KotlinFacts.of(pair);
        assertNotNull(facts, "kotlin class must produce facts");
        assertTrue(facts.canonical().contains("kind=CLASS")
                || facts.canonical().contains("kind="),
                "facts: " + facts.canonical());
        // une façade multi-fichiers ne porte aucun membre : faits vides,
        // jamais null (la façade reste une classe Kotlin)
        KotlinFacts facade = KotlinFacts.of(
                model("kotlin/collections/CollectionsKt"));
        assertNotNull(facade, "facade is still a kotlin class");
    }

    @Test
    void javaClassHasNoKotlinFacts() throws Exception {
        assertNull(KotlinFacts.of(model("jo/joide/platform/Hashing")));
    }

    @Test
    void manifestIsDeterministicAndDigestStable() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("com/b", "hashB");
        entries.put("com/a", "hashA");
        AbiManifest first = AbiManifest.of(entries);
        AbiManifest second = AbiManifest.of(Map.of("com/a", "hashA",
                "com/b", "hashB"));
        assertEquals(first.digest(), second.digest(),
                "insertion order must not matter");
        assertTrue(first.text().startsWith(AbiManifest.HEADER));
        assertEquals("hashA", first.entries().get("com/a"));
    }

    @Test
    void manifestRoundTripPreservesEntries() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("com/example/Foo", "a".repeat(32));
        entries.put("com/example/Bar", "b".repeat(32));
        AbiManifest manifest = AbiManifest.of(entries);
        AbiManifest parsed = AbiManifest.parse(manifest.text());
        assertEquals(manifest.digest(), parsed.digest());
        assertEquals(manifest.entries(), parsed.entries());
    }

    @Test
    void manifestWriteCreatesImmutableFile(@TempDir Path dir) throws Exception {
        AbiManifest manifest = AbiManifest.of(Map.of("a/A", "1".repeat(32)));
        Path written = manifest.writeTo(dir);
        assertEquals(dir.resolve(manifest.digest() + ".manifest"), written);
        assertEquals(manifest.text(),
                Files.readString(written).replace("\r", ""));
        // réécriture : pas d'erreur, même contenu
        assertEquals(written, manifest.writeTo(dir));
    }

    @Test
    void distinctAbisDiffer() throws Exception {
        ClassAbi hashing = ClassAbi.of(model("jo/joide/platform/Hashing"));
        ClassAbi rooted = ClassAbi.of(model("jo/joide/platform/RootedPath"));
        assertNotEquals(hashing.hash(), rooted.hash());
    }

    @Test
    void entryAbiIsDeterministicForJar() throws Exception {
        String classpath = System.getProperty("java.class.path");
        Path stdlib = null;
        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            if (entry.contains("kotlin-stdlib")) {
                stdlib = Path.of(entry);
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(stdlib,
                "kotlin-stdlib must be on the test classpath");
        AbiManifest first = EntryAbi.compute(stdlib, 200_000);
        AbiManifest second = EntryAbi.compute(stdlib, 200_000);
        String diverging = "none";
        String detail = "";
        for (String key : first.entries().keySet()) {
            if (!java.util.Objects.equals(first.entries().get(key),
                    second.entries().get(key))) {
                diverging = key;
                break;
            }
        }
        assertEquals(first.digest(), second.digest(),
                "same jar must give the same manifest");
        assertEquals(first.text(), second.text());
        if (!first.digest().equals(second.digest())) {
            System.out.println("sizes " + first.size() + " vs "
                    + second.size());
            var keys1 = new java.util.TreeSet<>(first.entries().keySet());
            var keys2 = new java.util.TreeSet<>(second.entries().keySet());
            var only1 = new java.util.TreeSet<>(keys1);
            only1.removeAll(keys2);
            var only2 = new java.util.TreeSet<>(keys2);
            only2.removeAll(keys1);
            System.out.println("only1=" + only1);
            System.out.println("only2=" + only2);
            for (String key : keys1) {
                if (keys2.contains(key) && !java.util.Objects.equals(
                        first.entries().get(key),
                        second.entries().get(key))) {
                    System.out.println("DIVERGE " + key);
                }
            }
        }
    }

    @Test
    void entryAbiComputesManifestForDirectory(@TempDir Path dir)
            throws Exception {
        Path classes = dir.resolve("classes");
        Path pkg = classes.resolve("com/example");
        Files.createDirectories(pkg);
        String resource = "/jo/joide/platform/Hashing.class";
        try (InputStream in = AbiTest.class.getResourceAsStream(resource)) {
            Files.write(pkg.resolve("Hashing.class"), in.readAllBytes());
        }
        AbiManifest manifest = EntryAbi.compute(classes, 100);
        assertEquals(1, manifest.size());
        assertTrue(manifest.entries().containsKey("jo/joide/platform/Hashing"));
    }
}
