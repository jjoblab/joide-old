package jo.joide.platform;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrôle des écritures atomiques : contenu complet, remplacement,
 * propreté des fichiers temporaires.
 */
class AtomicFilesTest {

    @Test
    void writesFullContent(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("a.toml");
        AtomicFiles.writeString(target, "schema = \"joide.module/1\"");
        assertEquals("schema = \"joide.module/1\"",
                Files.readString(target, StandardCharsets.UTF_8));
        assertEquals(0, countTempFiles(dir));
    }

    @Test
    void replacesExistingContent(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("a.toml");
        Files.writeString(target, "ancien");
        AtomicFiles.writeString(target, "nouveau contenu plus long");
        assertEquals("nouveau contenu plus long",
                Files.readString(target, StandardCharsets.UTF_8));
        assertEquals(0, countTempFiles(dir));
    }

    @Test
    void binaryRoundTrip(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("b.bin");
        byte[] content = new byte[100_000];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i * 7);
        }
        AtomicFiles.writeBytes(target, content);
        assertArrayEquals(content, Files.readAllBytes(target));
    }

    @Test
    void tempFileCleanedWhenWriteFails(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("c.txt");
        Files.writeString(target, "original");
        // un répertoire du même nom que le fichier temporaire fait
        // échouer l'ouverture en écriture
        Path blocked = dir.resolve("c.txt.joide-tmp");
        Files.createDirectory(blocked);
        assertThrows(IOException.class,
                () -> AtomicFiles.writeString(target, "remplacement"));
        assertEquals("original", Files.readString(target));
        assertTrue(Files.isDirectory(blocked));
    }

    private static int countTempFiles(Path dir) throws IOException {
        List<Path> leftovers = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (entry.getFileName().toString().endsWith(".joide-tmp")) {
                    leftovers.add(entry);
                }
            }
        }
        return leftovers.size();
    }
}
