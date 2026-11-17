package jo.joide.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import jo.joide.platform.AtomicFiles;

/**
 * Dépôt d'espace de travail : charge depuis le disque, enregistre par
 * transaction atomique et publie chaque nouvel espace aux abonnés.
 *
 * <p>Une écriture prépare d'abord tous les fichiers en temporaires
 * voisins, puis les renomme tous : un lecteur voit l'ancien ensemble
 * complet ou le nouveau, jamais un mélange. Si un renommage échoue en
 * cours de lot, les fichiers déjà renommés sont restaurés depuis une
 * sauvegarde en mémoire ; les temporaires restants sont supprimés.
 * Rien n'est publié sans un lot complet.</p>
 */
public final class WorkspaceRepository {

    private static final String BATCH_TEMP_SUFFIX = ".joide-batch";

    private final Path rootDir;
    private final List<Consumer<Workspace>> listeners = new CopyOnWriteArrayList<>();

    /**
     * @param rootDir répertoire racine contenant joide.toml
     */
    public WorkspaceRepository(Path rootDir) {
        this.rootDir = rootDir.toAbsolutePath().normalize();
    }

    /**
     * @return le résultat du chargement depuis le disque
     */
    public WorkspaceLoad load() {
        WorkspaceLoad load = WorkspaceLoader.load(rootDir);
        load.workspace().ifPresent(this::publish);
        return load;
    }

    /**
     * Enregistre une édition : tous les fichiers de l'édition sont
     * écrits, puis le nouvel espace est publié.
     *
     * @param edit édition à appliquer (nouvel espace + fichiers)
     * @throws IOException si une écriture échoue ; rien n'est publié et
     *                     le disque est restauré au mieux
     */
    public void save(WorkspaceEdit edit) throws IOException {
        Map<Path, String> pending = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : edit.files().entrySet()) {
            pending.put(rootDir.resolve(entry.getKey()), entry.getValue());
        }
        writeAllAtomically(pending);
        publish(edit.workspace());
    }

    /**
     * @param listener abonné aux nouveaux espaces publiés
     */
    public void addListener(Consumer<Workspace> listener) {
        listeners.add(listener);
    }

    private void publish(Workspace workspace) {
        for (Consumer<Workspace> listener : listeners) {
            listener.accept(workspace);
        }
    }

    /**
     * Écriture en deux passes : tous les temporaires, puis tous les
     * renommages, avec restauration en cas d'échec.
     */
    private static void writeAllAtomically(Map<Path, String> files) throws IOException {
        List<Path[]> pairs = new ArrayList<>();
        try {
            for (Map.Entry<Path, String> entry : files.entrySet()) {
                Path target = entry.getKey();
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Path temp = target.resolveSibling(
                        target.getFileName() + BATCH_TEMP_SUFFIX);
                Files.deleteIfExists(temp);
                Files.writeString(temp, entry.getValue());
                pairs.add(new Path[] {temp, target});
            }
        } catch (IOException | RuntimeException e) {
            deleteTemps(pairs);
            throw e instanceof IOException io ? io
                    : new IOException("workspace staging failed", e);
        }
        Map<Path, byte[]> backups = new HashMap<>();
        List<Path> renamed = new ArrayList<>();
        try {
            for (Path[] pair : pairs) {
                if (Files.isRegularFile(pair[1])) {
                    backups.put(pair[1], Files.readAllBytes(pair[1]));
                }
                AtomicFiles.moveAtomically(pair[0], pair[1]);
                renamed.add(pair[1]);
            }
        } catch (IOException | RuntimeException e) {
            restore(renamed, backups);
            deleteTemps(pairs);
            throw e instanceof IOException io ? io
                    : new IOException("workspace rename failed", e);
        }
    }

    private static void deleteTemps(List<Path[]> pairs) {
        for (Path[] pair : pairs) {
            try {
                Files.deleteIfExists(pair[0]);
            } catch (IOException suppressed) {
                // repris par la prochaine écriture
            }
        }
    }

    private static void restore(List<Path> renamed, Map<Path, byte[]> backups) {
        for (Path target : renamed) {
            byte[] backup = backups.get(target);
            try {
                if (backup != null) {
                    Files.write(target, backup);
                } else {
                    Files.deleteIfExists(target);
                }
            } catch (IOException suppressed) {
                // restauration au mieux : le lot est de toute façon cassé
            }
        }
    }
}
