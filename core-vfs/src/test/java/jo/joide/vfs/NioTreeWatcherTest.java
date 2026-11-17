package jo.joide.vfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import jo.joide.platform.CancelToken;
import jo.joide.platform.WorkerPools;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrôle du surveilleur de bureau sur de vrais événements du système
 * de fichiers, avec budget de surveillance borné.
 */
class NioTreeWatcherTest {

    private static final Duration MAX_WAIT = Duration.ofSeconds(10);

    private ExecutorService executor;

    @BeforeEach
    void createExecutor() {
        executor = WorkerPools.newSingleWriter("watcher-test");
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    /**
     * Attend qu'une condition soit vraie : interrogations courtes et
     * bornées sur un système externe réel, avec délai maximal.
     */
    private static void awaitUntil(String message, java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + MAX_WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timeout: " + message);
            }
            Thread.sleep(20L);
        }
    }

    @Test
    void reportsCreateModifyDelete(@TempDir Path dir) throws Exception {
        Recording listener = new Recording();
        try (NioTreeWatcher watcher = new NioTreeWatcher(64, executor)) {
            watcher.start(dir, listener, CancelToken.NEVER);
            awaitUntil("le budget de surveillance est pose",
                    () -> watcher.watchCount() >= 1);

            Files.writeString(dir.resolve("a.txt"), "v1");
            awaitUntil("creation vue", () -> !listener.events.isEmpty());
            Files.writeString(dir.resolve("a.txt"), "v2");
            awaitUntil("modification vue", () -> listener.count("a.txt") >= 2);
            Files.delete(dir.resolve("a.txt"));
            awaitUntil("suppression vue", () -> listener.hasChange(
                    "a.txt", FileChange.DELETED));
        }
        assertEquals(List.of(), listener.overflow,
                "aucun debordement attendu a ce volume");
    }

    @Test
    void watchesNewSubdirectories(@TempDir Path dir) throws Exception {
        Recording listener = new Recording();
        try (NioTreeWatcher watcher = new NioTreeWatcher(64, executor)) {
            watcher.start(dir, listener, CancelToken.NEVER);
            awaitUntil("surveillance posee", () -> watcher.watchCount() >= 1);

            Path sub = dir.resolve("src");
            Files.createDirectories(sub);
            // attendre l'enregistrement du nouveau dossier AVANT d'y
            // écrire : la surveillance d'un dossier créé ne couvre pas
            // les événements antérieurs à son enregistrement
            awaitUntil("sous-dossier surveille", () -> watcher.watchCount() >= 2);
            Files.writeString(sub.resolve("F.java"), "class F {}");
            awaitUntil("le fichier du sous-dossier est vu",
                    () -> listener.events.stream()
                            .anyMatch(e -> e.path.equals("src/F.java")));
        }
    }

    @Test
    void watchBudgetIsReportedNotExceeded(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("sub1"));
        Files.createDirectories(dir.resolve("sub2"));
        Recording listener = new Recording();
        try (NioTreeWatcher watcher = new NioTreeWatcher(2, executor)) {
            watcher.start(dir, listener, CancelToken.NEVER);
            awaitUntil("budget de deux dossiers atteint",
                    () -> watcher.watchCount() == 2);
            assertTrue(!listener.watchLimited.isEmpty(),
                    "les sous-arbres au-dela du budget doivent etre signales");
            assertTrue(watcher.watchCount() <= 2,
                    "le budget n'est jamais depasse");
        }
    }

    /** Journal d'écoute : événements, débordements, budgets. */
    private static final class Recording implements TreeWatcher.Listener {
        final List<Event> events = new CopyOnWriteArrayList<>();
        final List<String> overflow = new CopyOnWriteArrayList<>();
        final List<String> watchLimited = new CopyOnWriteArrayList<>();

        int count(String path) {
            int total = 0;
            for (Event event : events) {
                if (event.path.equals(path)) {
                    total++;
                }
            }
            return total;
        }

        boolean hasChange(String path, FileChange change) {
            for (Event event : events) {
                if (event.path.equals(path) && event.change == change) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void onFileEvent(String relativePath, FileChange change) {
            events.add(new Event(relativePath, change));
        }

        @Override
        public void onOverflow(String relativeDir) {
            overflow.add(relativeDir);
        }

        @Override
        public void onWatchLimitReached(String relativeDir) {
            watchLimited.add(relativeDir);
        }
    }

    private record Event(String path, FileChange change) {
    }
}
