package jo.joide.build.android.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Écriture déterministe d'archives alignées : mêmes entrées, mêmes
 * octets ; les entrées non compressées tombent sur leur alignement ;
 * l'archive reste lisible par le lecteur zip du JDK.
 */
class ApkWriterTest {

    @TempDir
    Path temp;

    @Test
    void twoWritesAreByteIdentical() throws IOException {
        byte[] first = build();
        byte[] second = build();
        assertTrue(java.util.Arrays.equals(first, second));
    }

    @Test
    void storedEntriesAreAligned() throws IOException {
        byte[] bytes = build();
        Path file = temp.resolve("aligned.apk");
        Files.write(file, bytes);
        try (ZipFile zip = new ZipFile(file.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            boolean sawStored = false;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getMethod() == ZipEntry.STORED
                        && !entry.isDirectory()) {
                    sawStored = true;
                    long offset = dataOffset(zip, entry, bytes);
                    assertEquals(0, offset % 4,
                            "stored entry must be 4-aligned: "
                                    + entry.getName() + " at " + offset);
                }
            }
            assertTrue(sawStored, "archive must contain stored entries");
        }
    }

    @Test
    void jdkZipReaderReadsAllEntries() throws IOException {
        byte[] bytes = build();
        Path file = temp.resolve("readable.apk");
        Files.write(file, bytes);
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry manifest = zip.getEntry("AndroidManifest.xml");
            assertTrue(manifest != null && !manifest.isDirectory());
            String content = new String(zip.getInputStream(manifest)
                    .readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("<manifest/>", content);
            assertEquals(4, zip.size());
        }
    }

    @Test
    void entriesAreSortedByName() throws IOException {
        byte[] bytes = new ApkWriter()
                .deflated("z-last.txt", new byte[] {1})
                .deflated("a-first.txt", new byte[] {2})
                .stored("m-stored.bin", new byte[] {3, 4})
                .toBytes();
        Path file = temp.resolve("sorted.apk");
        Files.write(file, bytes);
        try (ZipFile zip = new ZipFile(file.toFile())) {
            String previous = "";
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                assertTrue(name.compareTo(previous) >= 0,
                        "entries must be sorted: " + previous + " < "
                                + name);
                previous = name;
            }
        }
    }

    private static byte[] build() throws IOException {
        return new ApkWriter()
                .deflated("AndroidManifest.xml",
                        "<manifest/>".getBytes(StandardCharsets.UTF_8))
                .stored("resources.arsc", new byte[257])
                .stored("lib/arm64-v8a/libtest.so", new byte[100],
                        ApkWriter.ALIGN_NATIVE)
                .deflated("assets/data.bin", new byte[] {9, 8, 7})
                .toBytes();
    }

    /**
     * Position des données d'une entrée : balayage des en-têtes
     * locaux depuis le début de l'archive.
     */
    private static long dataOffset(ZipFile zip, ZipEntry entry,
                                   byte[] archive) throws IOException {
        long offset = 0;
        while (offset + 30 <= archive.length) {
            java.nio.ByteBuffer header = java.nio.ByteBuffer.wrap(archive)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN);
            if (header.getInt((int) offset) != 0x04034b50) {
                throw new IOException("no local header at " + offset);
            }
            int nameLength = header.getShort((int) offset + 26) & 0xffff;
            int extraLength = header.getShort((int) offset + 28) & 0xffff;
            String name = new String(archive, (int) (offset + 30),
                    nameLength, StandardCharsets.UTF_8);
            long data = offset + 30 + nameLength + extraLength;
            if (name.equals(entry.getName())) {
                return data;
            }
            long compressed = header.getInt((int) offset + 18)
                    & 0xffffffffL;
            long step = 30 + nameLength + extraLength + compressed;
            if (step <= 0) {
                throw new IOException("empty step at " + offset);
            }
            offset += step;
        }
        throw new IOException("entry not found: " + entry.getName());
    }
}
