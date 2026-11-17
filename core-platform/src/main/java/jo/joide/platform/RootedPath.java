package jo.joide.platform;

import java.util.Objects;

/**
 * Chemin relatif à une racine nommée : la seule forme de chemin
 * manipulée par le noyau.
 *
 * <p>Un chemin ancré n'est jamais absolu : il se compose d'une racine
 * et d'une suite de segments séparés par des barres obliques, sans
 * segment vide, point ni remontée. Deux chemins ancrés égaux désignent
 * donc toujours le même fichier pour une installation donnée, et
 * aucune empreinte ne peut contenir de chemin absolu.</p>
 */
public final class RootedPath implements Comparable<RootedPath> {

    private final RootName root;
    private final String relative;

    private RootedPath(RootName root, String relative) {
        this.root = root;
        this.relative = relative;
    }

    /**
     * @param root     racine du chemin
     * @param relative segments séparés par des barres obliques, ou une
     *                 chaîne vide pour la racine elle-même
     * @return le chemin ancré normalisé
     * @throws IllegalArgumentException si les segments vident la règle
     *         (segment vide, point, remontée, barre inversée, chemin absolu)
     */
    public static RootedPath of(RootName root, String relative) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(relative, "relative");
        if (relative.isEmpty()) {
            return new RootedPath(root, "");
        }
        if (relative.startsWith("/") || relative.endsWith("/")
                || relative.contains("\\") || relative.contains("//")) {
            throw new IllegalArgumentException("malformed relative path: " + relative);
        }
        for (String segment : relative.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("malformed segment in path: " + relative);
            }
        }
        return new RootedPath(root, relative);
    }

    /**
     * Analyse la forme textuelle « $ROOT/segment/segment ».
     *
     * @param text la forme textuelle, jamais {@code null}
     * @return le chemin ancré correspondant
     * @throws IllegalArgumentException si la racine ou les segments sont invalides
     */
    public static RootedPath parse(String text) {
        Objects.requireNonNull(text, "text");
        int slash = text.indexOf('/');
        String rootPart = slash < 0 ? text : text.substring(0, slash);
        RootName root = RootName.parse(rootPart);
        if (slash < 0) {
            return new RootedPath(root, "");
        }
        return of(root, text.substring(slash + 1));
    }

    /** @return la racine du chemin */
    public RootName root() {
        return root;
    }

    /**
     * @return les segments relatifs, séparés par des barres obliques ;
     *         chaîne vide pour la racine elle-même
     */
    public String relative() {
        return relative;
    }

    /**
     * @return le nombre de segments (0 pour la racine)
     */
    public int depth() {
        return relative.isEmpty() ? 0 : relative.split("/").length;
    }

    /**
     * @param child segment supplémentaire, sans barre oblique
     * @return le chemin prolongé d'un segment
     * @throws IllegalArgumentException si le segment est vide ou mal formé
     */
    public RootedPath resolve(String child) {
        if (child == null || child.isEmpty() || child.contains("/")) {
            throw new IllegalArgumentException("invalid child segment: " + child);
        }
        return of(root, relative.isEmpty() ? child : relative + "/" + child);
    }

    /**
     * @return le chemin du parent
     * @throws IllegalStateException si ce chemin est déjà la racine
     */
    public RootedPath parent() {
        if (relative.isEmpty()) {
            throw new IllegalStateException("root path has no parent");
        }
        int last = relative.lastIndexOf('/');
        return new RootedPath(root, last < 0 ? "" : relative.substring(0, last));
    }

    /**
     * @return le dernier segment
     * @throws IllegalStateException si ce chemin est déjà la racine
     */
    public String fileName() {
        if (relative.isEmpty()) {
            throw new IllegalStateException("root path has no file name");
        }
        int last = relative.lastIndexOf('/');
        return last < 0 ? relative : relative.substring(last + 1);
    }

    @Override
    public int compareTo(RootedPath other) {
        int byRoot = root.id().compareTo(other.root.id());
        return byRoot != 0 ? byRoot : relative.compareTo(other.relative);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RootedPath)) {
            return false;
        }
        RootedPath that = (RootedPath) other;
        return root.equals(that.root) && relative.equals(that.relative);
    }

    @Override
    public int hashCode() {
        return root.hashCode() * 31 + relative.hashCode();
    }

    @Override
    public String toString() {
        return relative.isEmpty() ? root.toString() : root + "/" + relative;
    }
}
