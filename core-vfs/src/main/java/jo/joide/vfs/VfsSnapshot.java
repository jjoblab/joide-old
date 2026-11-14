package jo.joide.vfs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import jo.joide.platform.Hash128;
import jo.joide.store.FileStamp;

/**
 * Instantané immuable de l'arbre de fichiers d'une racine : états par
 * fichier, empreinte d'arbre par dossier.
 *
 * <p>Les lecteurs ne voient jamais ce contenu muter : une mise à jour
 * produit un nouvel instantané. L'empreinte d'un dossier résume les
 * noms et signaux de changement de ses enfants ; elle permet de
 * répondre en temps constant à « ce dossier a-t-il changé ? ».</p>
 */
public final class VfsSnapshot {

    private final Map<String, FileStamp> entries;
    private final Map<String, Hash128> dirHashes;

    private VfsSnapshot(Map<String, FileStamp> entries, Map<String, Hash128> dirHashes) {
        this.entries = entries;
        this.dirHashes = dirHashes;
    }

    /**
     * @return un instantané vide
     */
    public static VfsSnapshot empty() {
        return new VfsSnapshot(Map.of(), Map.of());
    }

    /**
     * Construit un instantané depuis des états de fichiers, en
     * calculant les empreintes de dossiers.
     *
     * @param stamps états de fichiers, chemins relatifs uniques
     * @return l'instantané correspondant
     */
    public static VfsSnapshot of(List<FileStamp> stamps) {
        TreeMap<String, FileStamp> map = new TreeMap<>();
        for (FileStamp stamp : stamps) {
            map.put(stamp.path(), stamp);
        }
        Map<String, Hash128> dirHashes =
                MerkleTrees.computeAll(map);
        return new VfsSnapshot(
                Collections.unmodifiableMap(map),
                Collections.unmodifiableMap(dirHashes));
    }

    /**
     * @return les états de fichiers, par chemin relatif
     */
    public Map<String, FileStamp> entries() {
        return entries;
    }

    /**
     * @return les empreintes d'arbre, par chemin de dossier relatif
     *         (chaîne vide pour la racine)
     */
    public Map<String, Hash128> dirHashes() {
        return dirHashes;
    }

    /**
     * @param path chemin relatif
     * @return l'état du fichier, ou {@code null} si absent
     */
    public FileStamp entry(String path) {
        return entries.get(path);
    }

    /**
     * @param dirPath chemin de dossier relatif, chaîne vide pour la racine
     * @return l'empreinte d'arbre du dossier, ou {@code null} si inconnue
     */
    public Hash128 dirHash(String dirPath) {
        return dirHashes.get(dirPath);
    }

    /**
     * @return le nombre de fichiers suivis
     */
    public int fileCount() {
        return entries.size();
    }

    /**
     * Compare cet instantané à un instantané plus ancien et produit
     * l'ensemble des fichiers sales : ajoutés, modifiés (taille, date
     * ou clé de fichier différente), supprimés.
     *
     * @param older l'instantané de référence, plus ancien
     * @return les fichiers sales entre les deux instantanés
     */
    public DirtySet diffAgainst(VfsSnapshot older) {
        if (older == null || older.entries.isEmpty()) {
            if (entries.isEmpty()) {
                return DirtySet.empty();
            }
            return DirtySet.of(entries.keySet(), Set.of(), Set.of());
        }
        Set<String> added = new java.util.LinkedHashSet<>();
        Set<String> modified = new java.util.LinkedHashSet<>();
        Set<String> removed = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, FileStamp> entry : entries.entrySet()) {
            FileStamp before = older.entries.get(entry.getKey());
            if (before == null) {
                added.add(entry.getKey());
            } else if (signalsChanged(before, entry.getValue())) {
                modified.add(entry.getKey());
            }
        }
        for (String path : older.entries.keySet()) {
            if (!entries.containsKey(path)) {
                removed.add(path);
            }
        }
        return DirtySet.of(added, modified, removed);
    }

    /**
     * Signaux de changement : taille, date en nanosecondes ou clé de
     * fichier différentes.
     */
    static boolean signalsChanged(FileStamp before, FileStamp after) {
        return before.size() != after.size()
                || before.mtimeNs() != after.mtimeNs()
                || !java.util.Objects.equals(before.fileKey(), after.fileKey());
    }

    /**
     * Reconstruit un instantané en remplaçant certains états et en
     * supprimant d'autres chemins ; seules les empreintes de dossiers
     * touchées sont recalculées.
     *
     * @param upserts états ajoutés ou modifiés
     * @param removed chemins supprimés
     * @return le nouvel instantané
     */
    public VfsSnapshot withUpdates(List<FileStamp> upserts, Set<String> removed) {
        TreeMap<String, FileStamp> next = new TreeMap<>(entries);
        java.util.Set<String> touchedDirs = new java.util.LinkedHashSet<>();
        for (FileStamp upsert : upserts) {
            next.put(upsert.path(), upsert);
            touchedDirs.add(parentOf(upsert.path()));
        }
        for (String path : removed) {
            next.remove(path);
            touchedDirs.add(parentOf(path));
        }
        if (touchedDirs.isEmpty()) {
            return this;
        }
        Map<String, Hash128> hashes = new LinkedHashMap<>(dirHashes);
        MerkleTrees.recompute(next, hashes, touchedDirs);
        return new VfsSnapshot(
                Collections.unmodifiableMap(next),
                Collections.unmodifiableMap(hashes));
    }

    /**
     * Reconstruit un instantané en injectant des empreintes de contenu
     * fraîchement calculées, sans re-statuer les fichiers.
     *
     * @param hashesByPath empreintes de contenu, par chemin relatif
     * @return le nouvel instantané
     */
    public VfsSnapshot withContentHashes(Map<String, String> hashesByPath) {
        List<FileStamp> upserts = new ArrayList<>();
        for (Map.Entry<String, String> entry : hashesByPath.entrySet()) {
            FileStamp stamp = entries.get(entry.getKey());
            if (stamp != null) {
                upserts.add(stamp.withContentHash(entry.getValue()));
            }
        }
        return withUpdates(upserts, Set.of());
    }

    /** @return le chemin du dossier parent, chaîne vide à la racine */
    static String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }
}
