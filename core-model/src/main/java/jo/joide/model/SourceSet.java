package jo.joide.model;

import java.util.List;
import java.util.Objects;

/**
 * Un ensemble de sources du module : un nom et des racines de contenu.
 */
public final class SourceSet {

    private final String name;
    private final List<ContentRoot> roots;

    /**
     * @param name  nom de l'ensemble (ex. « main », « test »)
     * @param roots racines de contenu, dans l'ordre déclaré
     */
    public SourceSet(String name, List<ContentRoot> roots) {
        if (name == null || !name.matches("[a-z][a-z0-9-]{0,30}")) {
            throw new IllegalArgumentException("invalid source set name: " + name);
        }
        this.name = name;
        this.roots = List.copyOf(roots);
    }

    /** @return le nom */
    public String name() {
        return name;
    }

    /** @return les racines de contenu */
    public List<ContentRoot> roots() {
        return roots;
    }

    /**
     * @param role rôle cherché
     * @return les racines de ce rôle
     */
    public List<ContentRoot> rootsOf(ContentRole role) {
        java.util.ArrayList<ContentRoot> found = new java.util.ArrayList<>();
        for (ContentRoot root : roots) {
            if (root.role().id().equals(role.id())) {
                found.add(root);
            }
        }
        return found;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SourceSet)) {
            return false;
        }
        SourceSet that = (SourceSet) other;
        return name.equals(that.name) && roots.equals(that.roots);
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + roots.hashCode();
    }

    @Override
    public String toString() {
        return "SourceSet[" + name + " " + roots + "]";
    }
}
