package jo.joide.platform;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Ancrage des racines nommées sur le système de fichiers réel.
 *
 * <p>Le jeu de racines est la seule pièce qui connaît des chemins
 * absolus : tout le reste du noyau manipule des {@link RootedPath}.
 * La racine {@code OUT} pointe vers un répertoire de sorties, la racine
 * {@code M2} vers le cache d'artefacts partagé.</p>
 */
public final class RootSet {

    private final Map<RootName, Path> anchors;

    private RootSet(Map<RootName, Path> anchors) {
        this.anchors = anchors;
    }

    /**
     * @param anchors association racine vers répertoire ; les chemins
     *                relatifs sont convertis en chemins absolus
     * @return le jeu de racines correspondant
     * @throws NullPointerException si la table ou une valeur est nulle
     */
    public static RootSet of(Map<RootName, Path> anchors) {
        Objects.requireNonNull(anchors, "anchors");
        Map<RootName, Path> copy = new LinkedHashMap<>();
        for (Map.Entry<RootName, Path> entry : anchors.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "root");
            Objects.requireNonNull(entry.getValue(), "path");
            copy.put(entry.getKey(), entry.getValue().toAbsolutePath().normalize());
        }
        return new RootSet(Collections.unmodifiableMap(copy));
    }

    /** @return les racines ancrées */
    public Set<RootName> roots() {
        return anchors.keySet();
    }

    /**
     * @param root racine demandée
     * @return le répertoire d'ancrage
     * @throws IllegalArgumentException si la racine n'est pas ancrée
     */
    public Path anchor(RootName root) {
        Path path = anchors.get(root);
        if (path == null) {
            throw new IllegalArgumentException("root not anchored: " + root);
        }
        return path;
    }

    /**
     * @param path chemin ancré
     * @return le chemin absolu correspondant
     * @throws IllegalArgumentException si la racine n'est pas ancrée
     */
    public Path resolve(RootedPath path) {
        Objects.requireNonNull(path, "path");
        return anchor(path.root()).resolve(path.relative());
    }

    /**
     * Retrouve le chemin ancré d'un chemin absolu.
     *
     * @param absolute chemin absolu existant ou non
     * @return le chemin ancré équivalent
     * @throws IllegalArgumentException si le chemin sort de toutes les racines
     */
    public RootedPath relativize(Path absolute) {
        Objects.requireNonNull(absolute, "absolute");
        Path normalized = absolute.toAbsolutePath().normalize();
        RootName best = null;
        Path bestRelative = null;
        for (Map.Entry<RootName, Path> entry : anchors.entrySet()) {
            Path anchor = entry.getValue();
            if (normalized.startsWith(anchor)) {
                Path relative = anchor.relativize(normalized);
                if (best == null || relative.getNameCount() < bestRelative.getNameCount()) {
                    best = entry.getKey();
                    bestRelative = relative;
                }
            }
        }
        if (best == null) {
            throw new IllegalArgumentException(
                    "path outside anchored roots: " + normalized);
        }
        return RootedPath.of(best, toSlashString(bestRelative));
    }

    private static String toSlashString(Path path) {
        if (path.getNameCount() == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < path.getNameCount(); i++) {
            if (i > 0) {
                out.append('/');
            }
            out.append(path.getName(i));
        }
        return out.toString();
    }

    /** @return la table des ancrages, non modifiable */
    public Map<RootName, Path> asMap() {
        return anchors;
    }
}
