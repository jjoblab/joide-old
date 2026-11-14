package jo.joide.vfs;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ensemble de fichiers sales : ajoutés, modifiés ou supprimés depuis
 * la dernière consommation.
 *
 * <p>C'est la sortie unique du journal de changements, consommée par
 * le moteur de tâches. Sur démon chaud, l'absence de changement se
 * constate en temps constant : l'ensemble vide est immuable.</p>
 */
public final class DirtySet {

    private static final DirtySet EMPTY =
            new DirtySet(Set.of(), Set.of(), Set.of());

    private final Set<String> added;
    private final Set<String> modified;
    private final Set<String> removed;

    private DirtySet(Set<String> added, Set<String> modified, Set<String> removed) {
        this.added = added;
        this.modified = modified;
        this.removed = removed;
    }

    /** @return l'ensemble vide, partagé */
    public static DirtySet empty() {
        return EMPTY;
    }

    /**
     * @param added    chemins ajoutés
     * @param modified chemins modifiés
     * @param removed  chemins supprimés
     * @return un ensemble de fichiers sales
     */
    public static DirtySet of(Set<String> added, Set<String> modified,
                              Set<String> removed) {
        if (added.isEmpty() && modified.isEmpty() && removed.isEmpty()) {
            return EMPTY;
        }
        return new DirtySet(
                Collections.unmodifiableSet(new LinkedHashSet<>(added)),
                Collections.unmodifiableSet(new LinkedHashSet<>(modified)),
                Collections.unmodifiableSet(new LinkedHashSet<>(removed)));
    }

    /** @return les chemins ajoutés */
    public Set<String> added() {
        return added;
    }

    /** @return les chemins modifiés */
    public Set<String> modified() {
        return modified;
    }

    /** @return les chemins supprimés */
    public Set<String> removed() {
        return removed;
    }

    /**
     * @return vrai si aucun changement n'est en attente
     */
    public boolean isEmpty() {
        return added.isEmpty() && modified.isEmpty() && removed.isEmpty();
    }

    /**
     * @return le nombre total de chemins sales
     */
    public int size() {
        return added.size() + modified.size() + removed.size();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DirtySet)) {
            return false;
        }
        DirtySet that = (DirtySet) other;
        return added.equals(that.added) && modified.equals(that.modified)
                && removed.equals(that.removed);
    }

    @Override
    public int hashCode() {
        return added.hashCode() * 31 + modified.hashCode() * 7 + removed.hashCode();
    }

    @Override
    public String toString() {
        return "DirtySet[+" + added.size() + " ~" + modified.size()
                + " -" + removed.size() + "]";
    }
}
