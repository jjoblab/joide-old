package jo.joide.vfs;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import jo.joide.platform.Hash128;
import jo.joide.platform.Hashing;
import jo.joide.store.FileStamp;

/**
 * Empreintes d'arbre par dossier : chaque dossier résume ses enfants
 * (fichiers et sous-dossiers) par un hachage déterministe de leurs
 * noms et signaux de changement.
 *
 * <p>Les enfants sont traités par nom trié : l'empreinte d'un dossier
 * ne dépend jamais de l'ordre de découverte du système de fichiers.
 * Le recalcul ne visite que les dossiers touchés, leurs ancêtres et
 * leurs enfants immédiats.</p>
 */
public final class MerkleTrees {

    private static final long TREE_SEED = 0x54524545L;

    private MerkleTrees() {
    }

    /**
     * Calcule les empreintes de tous les dossiers d'un arbre.
     *
     * @param entries états de fichiers par chemin relatif, triés
     * @return les empreintes, par chemin de dossier (chaîne vide pour
     *         la racine)
     */
    public static Map<String, Hash128> computeAll(TreeMap<String, FileStamp> entries) {
        Set<String> dirs = allDirs(entries.keySet());
        Map<String, Hash128> hashes = new HashMap<>();
        for (String dir : deepestFirst(dirs)) {
            hashes.put(dir, dirDigest(entries, dirs, hashes, dir));
        }
        return hashes;
    }

    /**
     * Recalcule les empreintes des dossiers touchés et de leurs
     * ancêtres, en place, sans toucher aux autres.
     *
     * @param entries     états de fichiers par chemin relatif, triés
     * @param hashes      empreintes existantes, modifiées en place
     * @param touchedDirs chemins de dossiers dont le contenu a changé
     */
    public static void recompute(TreeMap<String, FileStamp> entries,
                                 Map<String, Hash128> hashes,
                                 Set<String> touchedDirs) {
        Set<String> dirs = allDirs(entries.keySet());
        Set<String> dirty = withAncestors(touchedDirs);
        for (String dir : deepestFirst(dirty)) {
            hashes.put(dir, dirDigest(entries, dirs, hashes, dir));
        }
    }

    /**
     * Empreinte d'un dossier : noms et empreintes des enfants immédiats
     * (fichiers et sous-dossiers), triés par nom.
     */
    private static Hash128 dirDigest(TreeMap<String, FileStamp> entries,
                                     Set<String> dirs,
                                     Map<String, Hash128> hashes,
                                     String dir) {
        String prefix = dir.isEmpty() ? "" : dir + "/";
        StringBuilder input = new StringBuilder(256);
        // fichiers immédiats : parcours par préfixe dans l'ordre trié
        for (Map.Entry<String, FileStamp> entry
                : entries.tailMap(prefix).entrySet()) {
            String path = entry.getKey();
            if (!path.startsWith(prefix)) {
                break;
            }
            String rest = path.substring(prefix.length());
            if (rest.indexOf('/') < 0) {
                input.append(rest).append('\u0000')
                        .append(fileNodeDigest(rest, entry.getValue()).toHex())
                        .append('\u0000');
            }
        }
        // sous-dossiers immédiats
        for (String candidate : dirs) {
            if (candidate.length() <= prefix.length()
                    || !candidate.startsWith(prefix)) {
                continue;
            }
            if (candidate.indexOf('/', prefix.length()) < 0) {
                Hash128 sub = hashes.get(candidate);
                if (sub != null) {
                    input.append(candidate.substring(prefix.length()))
                            .append('\u0000')
                            .append("d:").append(sub.toHex()).append('\u0000');
                }
            }
        }
        return Hashing.fast128(input.toString().getBytes(StandardCharsets.UTF_8),
                TREE_SEED);
    }

    /**
     * Empreinte d'un nœud fichier : nom et signaux de changement.
     */
    static Hash128 fileNodeDigest(String fileName, FileStamp stamp) {
        StringBuilder input = new StringBuilder(fileName.length() + 64);
        input.append(fileName).append('\u0000')
                .append(stamp.size()).append('\u0000')
                .append(stamp.mtimeNs()).append('\u0000')
                .append(stamp.fileKey() == null ? "" : stamp.fileKey())
                .append('\u0000')
                .append(stamp.contentHash() == null ? "" : stamp.contentHash())
                .append('\u0000');
        return Hashing.fast128(input.toString().getBytes(StandardCharsets.UTF_8),
                TREE_SEED);
    }

    /**
     * Tous les dossiers d'un arbre, racine comprise.
     */
    private static Set<String> allDirs(Set<String> paths) {
        Set<String> dirs = new TreeSet<>();
        dirs.add("");
        for (String path : paths) {
            String dir = VfsSnapshot.parentOf(path);
            while (!dir.isEmpty() && dirs.add(dir)) {
                dir = VfsSnapshot.parentOf(dir);
            }
        }
        return dirs;
    }

    /**
     * Les dossiers donnés plus tous leurs ancêtres.
     */
    private static Set<String> withAncestors(Set<String> touched) {
        Set<String> all = new TreeSet<>();
        Deque<String> pending = new ArrayDeque<>(touched);
        while (!pending.isEmpty()) {
            String dir = pending.poll();
            if (all.add(dir)) {
                String parent = VfsSnapshot.parentOf(dir);
                if (!parent.equals(dir) && !parent.isEmpty()) {
                    pending.add(parent);
                } else if (parent.isEmpty() && !dir.isEmpty()) {
                    pending.add("");
                }
            }
        }
        return all;
    }

    /**
     * Les dossiers triés du plus profond au moins profond : un dossier
     * est traité après tous ses sous-dossiers.
     */
    private static List<String> deepestFirst(Set<String> dirs) {
        List<String> order = new ArrayList<>(dirs);
        order.sort((a, b) -> {
            int byDepth = Integer.compare(depth(b), depth(a));
            return byDepth != 0 ? byDepth : a.compareTo(b);
        });
        return order;
    }

    private static int depth(String dir) {
        if (dir.isEmpty()) {
            return 0;
        }
        int count = 1;
        for (int i = 0; i < dir.length(); i++) {
            if (dir.charAt(i) == '/') {
                count++;
            }
        }
        return count;
    }
}
