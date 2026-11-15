package jo.joide.tasks;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;

import jo.joide.platform.RootedPath;
import jo.joide.platform.RootSet;
import jo.joide.store.BuildStore;
import jo.joide.store.CacheEntry;
import jo.joide.tasks.TaskOutput.DirOutput;
import jo.joide.tasks.TaskOutput.FileOutput;

/**
 * Cache de sorties adressé par contenu : la clé d'une entrée est
 * l'empreinte d'entrées de la tâche, et l'entrée contient une copie
 * intégrale des sorties produites.
 *
 * <p>Le cache restaure les sorties après un nettoyage ou depuis un
 * autre emplacement du même projet : la clé ne contient jamais de
 * chemin absolu. Les entrées sont immuables une fois écrites (copie
 * en zone temporaire puis déplacement atomique) ; l'éviction suit
 * l'ordre d'utilisation le moins récent de l'index du store et
 * supprime les fichiers correspondants. Les copies d'entrées sont
 * sérialisées : une éviction ne peut pas supprimer une entrée en
 * cours de restauration.</p>
 */
public final class OutputCache {

    private final Path root;
    private final long maxTotalBytes;
    private final Map<String, Long> sizes = new ConcurrentHashMap<>();
    private final Map<String, Long> newEntries = new HashMap<>();
    private final Set<String> touched = new CopyOnWriteArraySet<>();
    private final Object entryLock = new Object();
    private long totalBytes;

    /**
     * @param root          dossier racine du cache
     * @param maxTotalBytes plafond de la taille totale des entrées
     */
    public OutputCache(Path root, long maxTotalBytes) {
        this.root = Objects.requireNonNull(root, "root");
        this.maxTotalBytes = maxTotalBytes;
    }

    /**
     * Ouvre le cache : charge l'index depuis le store et écarte les
     * entrées dont le dossier a disparu (cache tronqué à la main).
     *
     * @param store store possédé par le fil appelant
     */
    public void open(BuildStore store) {
        Objects.requireNonNull(store, "store");
        try {
            Files.createDirectories(root);
        } catch (IOException unreadable) {
            throw new IllegalStateException(
                    "cannot create output cache directory: " + root, unreadable);
        }
        synchronized (entryLock) {
            for (CacheEntry entry : store.listCacheEntries()) {
                if (Files.isDirectory(entryDir(entry.fingerprint()))) {
                    sizes.put(entry.fingerprint(), entry.sizeBytes());
                    totalBytes += entry.sizeBytes();
                }
            }
        }
    }

    /**
     * @param fingerprint empreinte d'entrées de la tâche
     * @return vrai si le cache contient une entrée pour cette clé
     */
    public boolean has(String fingerprint) {
        return sizes.containsKey(fingerprint);
    }

    /**
     * @param fingerprint empreinte d'entrées
     * @return la taille de l'entrée en octets, ou {@code null}
     */
    public Long sizeOf(String fingerprint) {
        return sizes.get(fingerprint);
    }

    /**
     * Copie les sorties produites d'une tâche dans une entrée du
     * cache. Aucun accès au store : l'index est persisté à la fin du
     * build.
     *
     * @param fingerprint  empreinte d'entrées de la tâche
     * @param outputs      sorties déclarées de la tâche
     * @param producedFiles fichiers et dossiers produits, par chemin
     *                     ancré déclaré
     * @return la taille de l'entrée en octets
     * @throws IOException si la copie échoue
     */
    public long store(String fingerprint, TaskOutputs outputs,
                      Map<RootedPath, Path> producedFiles) throws IOException {
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(outputs, "outputs");
        Objects.requireNonNull(producedFiles, "producedFiles");
        synchronized (entryLock) {
            Path entry = entryDir(fingerprint);
            Path staging = entry.resolveSibling(
                    entry.getFileName() + ".staging");
            deleteRecursively(staging);
            Path filesDir = staging.resolve("files");
            Files.createDirectories(filesDir);
            Map<String, Long> copiedSizes = new TreeMap<>();
            for (TaskOutput output : outputs.asList()) {
                RootedPath declared = declaredPath(output);
                Path produced = producedFiles.get(declared);
                if (produced == null || !Files.exists(produced)) {
                    continue;
                }
                if (output instanceof FileOutput) {
                    Path target = insideEntry(filesDir, declared);
                    Files.createDirectories(target.getParent());
                    Files.copy(produced, target,
                            StandardCopyOption.REPLACE_EXISTING);
                    copiedSizes.put(target.toString(), Files.size(target));
                } else {
                    copyDirIntoEntry(filesDir, declared, produced, copiedSizes);
                }
            }
            deleteRecursively(entry);
            try {
                Files.move(staging, entry, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException fallback) {
                Files.move(staging, entry);
            }
            long size = 0;
            for (Long fileSize : copiedSizes.values()) {
                size += fileSize;
            }
            Long previous = sizes.put(fingerprint, size);
            totalBytes = previous == null
                    ? totalBytes + size : totalBytes - previous + size;
            newEntries.put(fingerprint, size);
            return size;
        }
    }

    /**
     * Restaure les sorties d'une tâche depuis le cache vers leurs
     * emplacements déclarés, en remplaçant leur contenu.
     *
     * @param fingerprint empreinte d'entrées de la tâche
     * @param outputs     sorties déclarées
     * @param roots       racines pour résoudre les chemins
     * @throws IOException si la restauration échoue
     */
    public void restore(String fingerprint, TaskOutputs outputs, RootSet roots)
            throws IOException {
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(outputs, "outputs");
        Objects.requireNonNull(roots, "roots");
        synchronized (entryLock) {
            Path filesDir = entryDir(fingerprint).resolve("files");
            for (TaskOutput output : outputs.asList()) {
                RootedPath declared = declaredPath(output);
                Path source = filesDir.resolve(declared.root().id())
                        .resolve(declared.relative());
                Path target = roots.resolve(declared);
                if (output instanceof FileOutput) {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target,
                            StandardCopyOption.REPLACE_EXISTING);
                } else {
                    deleteRecursively(target);
                    Files.createDirectories(target);
                    if (Files.isDirectory(source)) {
                        try (Stream<Path> walk = Files.walk(source)) {
                            for (Path file : walk.sorted().toList()) {
                                Path relative = source.relativize(file);
                                Path destination = target.resolve(relative);
                                if (Files.isDirectory(file)) {
                                    Files.createDirectories(destination);
                                } else {
                                    Files.createDirectories(
                                            destination.getParent());
                                    Files.copy(file, destination,
                                            StandardCopyOption.REPLACE_EXISTING);
                                }
                            }
                        }
                    }
                }
            }
            touched.add(fingerprint);
        }
    }

    /**
     * Persiste l'index du cache dans le store et évince si le plafond
     * est dépassé. À appeler dans la transaction de fin de build du
     * moteur.
     *
     * @param store store en cours de transaction
     * @return les empreintes évincées, dont les fichiers sont supprimés
     */
    public List<String> persistWithinTransaction(BuildStore store) {
        Objects.requireNonNull(store, "store");
        List<String> evicted = new ArrayList<>();
        synchronized (entryLock) {
            for (Map.Entry<String, Long> entry : newEntries.entrySet()) {
                store.recordCacheEntry(entry.getKey(), entry.getValue());
            }
            newEntries.clear();
            for (String fingerprint : touched) {
                store.touchCacheEntry(fingerprint);
            }
            touched.clear();
            if (totalBytes > maxTotalBytes) {
                for (CacheEntry victim : store.evictCacheBeyond(maxTotalBytes)) {
                    deleteRecursively(entryDir(victim.fingerprint()));
                    Long size = sizes.remove(victim.fingerprint());
                    if (size != null) {
                        totalBytes -= size;
                    }
                    evicted.add(victim.fingerprint());
                }
            }
        }
        return evicted;
    }

    /**
     * Signale une utilisation récente d'une entrée, pour l'ordre
     * d'éviction.
     *
     * @param fingerprint empreinte d'entrées
     */
    public void touch(String fingerprint) {
        Objects.requireNonNull(fingerprint, "fingerprint");
        touched.add(fingerprint);
    }

    /**
     * Écarte une entrée du cache : l'entrée est retirée de l'index en
     * mémoire et ses fichiers sont supprimés. Utilisé quand une
     * restauration échoue : en cas de doute, sur-invalider.
     *
     * @param fingerprint empreinte de l'entrée à écarter
     */
    public void drop(String fingerprint) {
        Objects.requireNonNull(fingerprint, "fingerprint");
        synchronized (entryLock) {
            deleteRecursively(entryDir(fingerprint));
            Long size = sizes.remove(fingerprint);
            if (size != null) {
                totalBytes -= size;
            }
            newEntries.remove(fingerprint);
            touched.remove(fingerprint);
        }
    }

    /**
     * @return vrai si des changements d'index attendent leur
     *         persistance
     */
    public boolean hasPendingIndexChanges() {
        synchronized (entryLock) {
            return !newEntries.isEmpty() || !touched.isEmpty();
        }
    }

    /**
     * @return la taille totale des entrées en octets
     */
    public long totalBytes() {
        synchronized (entryLock) {
            return totalBytes;
        }
    }

    private static RootedPath declaredPath(TaskOutput output) {
        return output instanceof FileOutput file
                ? file.path() : ((DirOutput) output).path();
    }

    private static Path insideEntry(Path filesDir, RootedPath path) {
        return filesDir.resolve(path.root().id()).resolve(path.relative());
    }

    private static void copyDirIntoEntry(Path filesDir, RootedPath declared,
                                         Path produced,
                                         Map<String, Long> copiedSizes)
            throws IOException {
        try (Stream<Path> walk = Files.walk(produced)) {
            for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                String relative = produced.relativize(file).toString()
                        .replace('\\', '/');
                RootedPath child = RootedPath.of(declared.root(),
                        declared.relative().isEmpty()
                                ? relative : declared.relative() + "/" + relative);
                Path target = insideEntry(filesDir, child);
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                copiedSizes.put(target.toString(), Files.size(target));
            }
        }
    }

    private Path entryDir(String fingerprint) {
        String prefix = fingerprint.length() >= 2
                ? fingerprint.substring(0, 2) : "00";
        return root.resolve(prefix).resolve(fingerprint);
    }

    private static void deleteRecursively(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException unreadable) {
            // une entrée du cache impossible à supprimer ne fait pas
            // échouer le build : elle repassera à l'éviction suivante
        }
    }
}
