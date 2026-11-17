package jo.joide.model;

import java.util.Objects;

/**
 * Une racine de contenu : un répertoire du module et le rôle de ce
 * qu'il contient.
 */
public final class ContentRoot {

    private final String path;
    private final ContentRole role;

    /**
     * @param path chemin relatif au module, sans barre oblique finale
     * @param role rôle du contenu
     */
    public ContentRoot(String path, ContentRole role) {
        if (path == null || path.isEmpty() || path.startsWith("/")
                || path.endsWith("/") || path.contains("\\")
                || path.split("/")[0].isEmpty()) {
            throw new IllegalArgumentException("invalid content root path: " + path);
        }
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("invalid content root path: " + path);
            }
        }
        this.path = path;
        this.role = Objects.requireNonNull(role, "role");
    }

    /** @return le chemin relatif au module */
    public String path() {
        return path;
    }

    /** @return le rôle du contenu */
    public ContentRole role() {
        return role;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ContentRoot)) {
            return false;
        }
        ContentRoot that = (ContentRoot) other;
        return path.equals(that.path) && role.id().equals(that.role.id());
    }

    @Override
    public int hashCode() {
        return path.hashCode() * 31 + role.id().hashCode();
    }

    @Override
    public String toString() {
        return path + " (" + role.id() + ")";
    }
}
