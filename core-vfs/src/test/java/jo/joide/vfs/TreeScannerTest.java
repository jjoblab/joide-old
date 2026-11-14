package jo.joide.vfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import jo.joide.platform.CancelToken;
import jo.joide.platform.WorkerPools;
import jo.joide.store.FileStamp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrôle du parcours parallèle : exhaustivité, tri, rejet des
 * racines invalides et annulation immédiate.
 */
class TreeScannerTest {

    private ExecutorService pool;

    @BeforeEach
    void createPool() {
        pool = WorkerPools.newFixed("scan-test", 4);
    }

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    @Test
    void scansNestedTree(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a.txt"), "a");
        Files.createDirectories(dir.resolve("src/main/java"));
        Files.writeString(dir.resolve("src/main/java/Foo.java"), "class Foo {}");
        Files.writeString(dir.resolve("src/main/java/Bar.java"), "class Bar {}");
        Files.createDirectories(dir.resolve("res"));
        Files.writeString(dir.resolve("res/empty.xml"), "<r/>");

        List<FileStamp> stamps = TreeScanner.scan(dir, pool, CancelToken.NEVER);

        assertEquals(List.of(
                "a.txt", "res/empty.xml", "src/main/java/Bar.java",
                "src/main/java/Foo.java"),
                TreeScanner.relativePathsOf(stamps).stream().sorted().toList(),
                "l'ordre de sortie doit être trié et complet");
        FileStamp foo = stamps.stream()
                .filter(s -> s.path().endsWith("Foo.java")).findFirst().orElseThrow();
        assertEquals("class Foo {}".length(), foo.size());
        assertTrue(foo.mtimeNs() > 0);
        assertNotNull(foo.fileKey(), "une clé de fichier doit être lue sur Linux");
    }

    @Test
    void rejectsNonDirectory(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("f.txt");
        Files.writeString(file, "x");
        assertThrows(IOException.class,
                () -> TreeScanner.scan(file, pool, CancelToken.NEVER));
    }

    @Test
    void cancelledTokenAbortsBeforeStart(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a.txt"), "a");
        CancelToken.Cancellation cancellation = CancelToken.create();
        cancellation.cancel();
        assertThrows(CancellationException.class,
                () -> TreeScanner.scan(dir, pool, cancellation.token()));
    }

    @Test
    void symlinkIsNotFollowed(@TempDir Path dir) throws IOException {
        Path outside = Files.createDirectory(dir.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "secret");
        Path root = Files.createDirectory(dir.resolve("root"));
        Files.writeString(root.resolve("inside.txt"), "inside");
        Files.createSymbolicLink(root.resolve("lien"), outside);

        List<FileStamp> stamps = TreeScanner.scan(root, pool, CancelToken.NEVER);

        assertEquals(java.util.Set.of("inside.txt", "lien"),
                java.util.Set.copyOf(TreeScanner.relativePathsOf(stamps)),
                "un lien apparaît pour lui-même, sans suivre sa cible");
    }
}
