package jo.joide.vfs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import jo.joide.platform.CancelToken;
import jo.joide.store.FileStamp;

/**
 * Parcours parallèle d'un arbre de fichiers : une passe de statuts,
 * sans lecture de contenu.
 *
 * <p>Chaque dossier visité est soumis au pool injecté ; l'ordre des
 * résultats est rendu déterministe par un tri final sur les chemins.
 * Les liens symboliques sont enregistrés pour eux-mêmes, jamais
 * suivis : un lien ne fait pas sortir le parcours de sa racine. Une
 * erreur de lecture interrompt le parcours entier : jamais de résultat
 * partiel silencieux.</p>
 */
public final class TreeScanner {

    private TreeScanner() {
    }

    /**
     * Parcourt un arbre et collecte les statuts de ses fichiers.
     *
     * @param root  répertoire racine à parcourir
     * @param pool  pool de fils injecté
     * @param token jeton d'annulation coopérative
     * @return les états de fichiers trouvés, triés par chemin
     * @throws IOException    si la racine n'est pas un répertoire ou si
     *                        un statut échoue
     * @throws java.util.concurrent.CancellationException si le parcours
     *                        est annulé
     */
    public static List<FileStamp> scan(Path root, ExecutorService pool,
                                       CancelToken token) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(pool, "pool");
        Objects.requireNonNull(token, "token");
        token.checkCancelled();
        if (!Files.isDirectory(root)) {
            throw new IOException("not a directory: " + root);
        }
        Walk walk = new Walk(pool, token);
        walk.pending.incrementAndGet();
        walk.visit(root, "");
        try {
            walk.latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("scan interrupted", e);
        }
        walk.token.checkCancelled();
        if (walk.failure != null) {
            throw asIOException(walk.failure);
        }
        List<FileStamp> entries = new ArrayList<>(walk.collected);
        entries.sort(Comparator.comparing(FileStamp::path));
        return Collections.unmodifiableList(entries);
    }

    private static IOException asIOException(Throwable failure) {
        if (failure instanceof IOException io) {
            return io;
        }
        if (failure instanceof UncheckedIOException io) {
            return io.getCause();
        }
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        throw new IllegalStateException("scan failed", failure);
    }

    /**
     * État d'un parcours en cours : collecte, compteurs et échec.
     */
    private static final class Walk {
        private final ExecutorService pool;
        private final CancelToken token;
        private final ConcurrentLinkedQueue<FileStamp> collected =
                new ConcurrentLinkedQueue<>();
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicInteger pending = new AtomicInteger();
        private volatile Throwable failure;

        Walk(ExecutorService pool, CancelToken token) {
            this.pool = pool;
            this.token = token;
        }

        void visit(Path root, String relativeDir) {
            try {
                token.checkCancelled();
                Path dir = relativeDir.isEmpty() ? root
                        : root.resolve(relativeDir);
                List<String> subdirs = new ArrayList<>();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                    for (Path child : stream) {
                        token.checkCancelled();
                        record(child, relativeDir, subdirs);
                    }
                }
                for (String subdir : subdirs) {
                    pending.incrementAndGet();
                    pool.execute(() -> visit(root, subdir));
                }
            } catch (Throwable e) {
                fail(e);
                return;
            }
            finishOne();
        }

        private void record(Path child, String relativeDir, List<String> subdirs)
                throws IOException {
            BasicFileAttributes attributes = Files.readAttributes(
                    child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            String name = child.getFileName().toString();
            String relative = relativeDir.isEmpty()
                    ? name : relativeDir + "/" + name;
            if (attributes.isDirectory()) {
                subdirs.add(relative);
                return;
            }
            collected.add(new FileStamp(
                    relative,
                    attributes.size(),
                    attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS),
                    fileKeyOf(attributes),
                    null));
        }

        private void finishOne() {
            if (pending.decrementAndGet() == 0) {
                latch.countDown();
            }
        }

        private void fail(Throwable e) {
            if (failure == null) {
                failure = e;
            }
            // décrémente au plus une fois pour cette visite : les
            // visites déjà soumises se videront en constatant l'échec
            finishOne();
        }
    }

    /**
     * Clé de fichier du système, ou {@code null} si indisponible.
     */
    private static String fileKeyOf(BasicFileAttributes attributes) {
        Object key = attributes.fileKey();
        return key == null ? null : key.toString();
    }

    /**
     * @param stamps états dont extraire les chemins
     * @return les chemins relatifs, dans l'ordre
     */
    public static Set<String> relativePathsOf(List<FileStamp> stamps) {
        Set<String> paths = new java.util.LinkedHashSet<>();
        for (FileStamp stamp : stamps) {
            paths.add(stamp.path());
        }
        return paths;
    }
}
