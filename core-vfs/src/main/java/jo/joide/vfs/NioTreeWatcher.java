package jo.joide.vfs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import jo.joide.platform.CancelToken;

/**
 * Surveilleur d'arbre de bureau : le service de surveillance du
 * système de fichiers, borné à un budget de dossiers.
 *
 * <p>Les dossiers sont enregistrés récursivement jusqu'au budget ;
 * au-delà, le sous-arbre est déclaré non surveillé et le rattrapage
 * par parcours prend le relais. Un débordement de la file du système
 * déclenche la notification de débordement pour le dossier concerné.</p>
 */
public final class NioTreeWatcher implements TreeWatcher {

    private final int maxWatches;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger watches = new AtomicInteger();
    private final ConcurrentHashMap<WatchKey, String> watchedDirs =
            new ConcurrentHashMap<>();
    private volatile WatchService service;
    private volatile Listener listener;
    private volatile CancelToken token;
    private volatile Path rootDir;

    /**
     * @param maxWatches budget de dossiers surveillés simultanément
     * @param executor   exécuteur injecté pour la boucle de lecture
     */
    public NioTreeWatcher(int maxWatches, ExecutorService executor) {
        if (maxWatches < 1) {
            throw new IllegalArgumentException(
                    "maxWatches must be positive: " + maxWatches);
        }
        this.maxWatches = maxWatches;
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public void start(Path rootDir, Listener listener, CancelToken token) {
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.token = Objects.requireNonNull(token, "token");
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("watcher already started");
        }
        try {
            service = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            running.set(false);
            throw new UncheckedIOException("cannot create watch service", e);
        }
        registerRecursively(rootDir, "");
        token.onCancel(this::close);
        executor.execute(this::pollLoop);
    }

    private void registerRecursively(Path dir, String relativeDir) {
        if (token.isCancelled() || watches.get() >= maxWatches) {
            if (listener != null && watches.get() >= maxWatches) {
                listener.onWatchLimitReached(relativeDir);
            }
            return;
        }
        try {
            WatchKey key = dir.register(service,
                    java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
                    java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY,
                    java.nio.file.StandardWatchEventKinds.ENTRY_DELETE);
            watchedDirs.put(key, relativeDir);
            watches.incrementAndGet();
        } catch (IOException e) {
            // dossier disparu ou non surveillable : le rattrapage couvrira
            return;
        }
        try (var stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) {
                    String childRelative = relativeDir.isEmpty()
                            ? child.getFileName().toString()
                            : relativeDir + "/" + child.getFileName();
                    registerRecursively(child, childRelative);
                }
            }
        } catch (IOException e) {
            // arborescence en mutation : le rattrapage couvrira
        }
    }

    private void pollLoop() {
        while (running.get() && !token.isCancelled()) {
            WatchKey key;
            try {
                key = service.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (key == null) {
                continue;
            }
            String relativeDir = watchedDirs.getOrDefault(key, "");
            for (WatchEvent<?> event : key.pollEvents()) {
                dispatch(event, relativeDir);
            }
            boolean valid = key.reset();
            if (!valid) {
                watchedDirs.remove(key);
                watches.decrementAndGet();
            }
        }
    }

    private void dispatch(WatchEvent<?> event, String relativeDir) {
        WatchEvent.Kind<?> kind = event.kind();
        if (kind == java.nio.file.StandardWatchEventKinds.OVERFLOW) {
            listener.onOverflow(relativeDir);
            return;
        }
        Object context = event.context();
        if (!(context instanceof java.nio.file.Path name)) {
            return;
        }
        String relative = relativeDir.isEmpty()
                ? name.toString()
                : relativeDir + "/" + name;
        Path absolute = rootDir.resolve(relative);
        FileChange change;
        if (kind == java.nio.file.StandardWatchEventKinds.ENTRY_CREATE) {
            if (Files.isDirectory(absolute)) {
                registerRecursively(absolute, relative);
                return;
            }
            change = FileChange.CREATED;
        } else if (kind == java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY) {
            change = FileChange.MODIFIED;
        } else if (kind == java.nio.file.StandardWatchEventKinds.ENTRY_DELETE) {
            change = FileChange.DELETED;
        } else {
            return;
        }
        listener.onFileEvent(relative, change);
    }

    /**
     * @return le nombre de dossiers actuellement surveillés
     */
    int watchCount() {
        return watches.get();
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        for (WatchKey key : watchedDirs.keySet()) {
            key.cancel();
        }
        watchedDirs.clear();
        watches.set(0);
        try {
            if (service != null) {
                service.close();
            }
        } catch (IOException e) {
            // fermeture au mieux : le service meurt avec le processus
        }
    }
}
