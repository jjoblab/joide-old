package jo.joide.deps.aar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Extraction AAR : contenu, idempotence, zip-slip refusé.
 */
class AarExtractorTest {

    @TempDir
    Path tmp;

    private Path zipOf(Map<String, byte[]> entries) throws IOException {
        Path file = tmp.resolve("test-" + System.nanoTime() + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(
                Files.newOutputStream(file))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));
                out.write(e.getValue());
                out.closeEntry();
            }
        }
        return file;
    }

    @Test
    void extractsTheExpectedLayout() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("AndroidManifest.xml", "<manifest/>".getBytes());
        entries.put("classes.jar", "jar binaire factice".getBytes());
        entries.put("libs/foo.jar", "foo".getBytes());
        entries.put("libs/bar.jar", "bar".getBytes());
        entries.put("res/values/strings.xml", "<resources/>".getBytes());
        entries.put("assets/data.txt", "données".getBytes(StandardCharsets.UTF_8));
        Path aar = zipOf(entries);
        String sha = jo.joide.deps.Checksums.sha256OfText("identité");

        AarContents first = new AarExtractor().extract(aar, sha,
                tmp.resolve("out/one"));
        assertTrue(Files.isRegularFile(first.classesJar));
        assertTrue(Files.isRegularFile(first.manifest));
        assertEquals(2, first.libs.size());
        // Triées : bar avant foo.
        assertEquals(tmp.resolve("out/one").resolve("libs/bar.jar"),
                first.libs.get(0));
        assertEquals(tmp.resolve("out/one").resolve("libs/foo.jar"),
                first.libs.get(1));
        assertEquals(2, first.resources.size());
        // Idempotence : même marqueur, même contenu.
        AarContents second = new AarExtractor().extract(aar, sha,
                tmp.resolve("out/one"));
        assertEquals(first.classesJar, second.classesJar);
        // Une autre empreinte extrait ailleurs.
        new AarExtractor().extract(aar, "autre-empreinte-fictive-longue-assez",
                tmp.resolve("out/two"));
        assertTrue(Files.isRegularFile(tmp.resolve("out/two/classes.jar")));
    }

    @Test
    void zipSlipAttemptsAreRejected() throws IOException {
        Path honest = zipOf(Map.of("classes.jar", "ok".getBytes()));
        String sha = "0123456789abcdef0123456789abcdef";
        Path target = tmp.resolve("out/slip");
        // Chaque forme d'attaque est refusée individuellement.
        for (String evil : new String[]{
                "../escape.txt", "/absolu.txt", "a/../../traverse.txt",
                "C:\\windows\\evil.txt", "a/./cur.txt", "a//double.txt",
                "//réseau.txt"}) {
            Path zip = zipOf(Map.of(evil, "mal".getBytes()));
            AarExtractor.Rejected rejected = assertThrows(
                    AarExtractor.Rejected.class,
                    () -> new AarExtractor().extract(zip, sha, target),
                    "devrait refuser : " + evil);
            assertNotNull(rejected.getMessage());
            // Rien n'a été écrit hors de la zone d'attente.
            assertFalse(Files.exists(target.resolve(evil)));
        }
        // Le honnête reste extractible après tout ça.
        AarContents contents = new AarExtractor().extract(honest, sha, target);
        assertTrue(Files.isRegularFile(contents.classesJar));
    }

    @Test
    void slipPolicyIsPureAndTestable() {
        assertNullSafe(AarExtractor.slipReason("classes.jar"));
        assertNullSafe(AarExtractor.slipReason("res/values/strings.xml"));
        assertNullSafe(AarExtractor.slipReason("dossier/"));
        assertEquals("traversée de répertoire",
                AarExtractor.slipReason("../x.txt"));
        assertEquals("chemin absolu", AarExtractor.slipReason("/x.txt"));
        assertEquals("barre oblique inverse", AarExtractor.slipReason("a\\b.txt"));
        assertEquals("lecteur windows", AarExtractor.slipReason("C:/x.txt"));
        assertEquals("répertoire courant", AarExtractor.slipReason("a/./b"));
        assertEquals("segment vide", AarExtractor.slipReason("a//b"));
        assertEquals("chemin réseau", AarExtractor.slipReason("//host/share"));
        assertEquals("nom vide", AarExtractor.slipReason(""));
    }

    private static void assertNullSafe(String reason) {
        org.junit.jupiter.api.Assertions.assertNull(reason);
    }
}
