package jo.joide.build.android.toolchain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Extraction protégée contre le zip-slip : les sept formes d'attaque
 * sont refusées une par une, l'extraction heureuse est idempotente
 * par marqueur.
 */
class SafeZipTest {

    @TempDir
    Path temp;

    @Test
    void slipFormsAreRejected() throws IOException {
        String[] attacks = {
            "../escape.xml",
            "/absolute.xml",
            "a/../../escape.xml",
            "C:\\windows\\evil.xml",
            "a/./b.xml",
            "a//b.xml",
            "//network/share.xml"
        };
        for (String attack : attacks) {
            Path zip = zipOf("entry", attack, "data".getBytes());
            SafeZip.Rejected rejected = assertThrows(SafeZip.Rejected.class,
                    () -> SafeZip.extract(zip, "deadbeef",
                            temp.resolve("out-" + Math.abs(attack.hashCode()))),
                    "form must be rejected: " + attack);
            assertNotNull(rejected.getMessage());
        }
    }

    @Test
    void happyPathExtractsAndIsIdempotent() throws IOException {
        Path zip = zipOf("ok", "res/values.xml", "<resources/>".getBytes());
        Path out = temp.resolve("out");
        SafeZip.extract(zip, "sha-1", out);
        assertTrue(Files.isRegularFile(out.resolve("res/values.xml")));
        assertEquals("<resources/>", Files.readString(out.resolve("res/values.xml")));
        // idempotence : le marqueur court-circuite la seconde passe
        SafeZip.extract(zip, "sha-1", out);
        assertTrue(Files.isRegularFile(out.resolve(".joide-zip-extracted")));
        // une empreinte différente réextrait
        SafeZip.extract(zip, "sha-2", out);
        assertEquals("sha-2",
                Files.readString(out.resolve(".joide-zip-extracted")));
    }

    @Test
    void policyReasonsMatchForm() {
        assertNull(SafeZip.slipReason("res/values.xml"));
        assertNull(SafeZip.slipReason("dir/"));
        assertEquals("parent traversal in entry name",
                SafeZip.slipReason("../x"));
        assertEquals("backslash in entry name", SafeZip.slipReason("a\\b"));
        assertEquals("absolute entry name", SafeZip.slipReason("/a"));
        assertEquals("windows drive in entry name", SafeZip.slipReason("C:/x"));
        assertEquals("empty entry name", SafeZip.slipReason(""));
    }

    private Path zipOf(String name, String entry, byte[] content)
            throws IOException {
        Path file = temp.resolve(name + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(content);
            zip.closeEntry();
        }
        return file;
    }
}
