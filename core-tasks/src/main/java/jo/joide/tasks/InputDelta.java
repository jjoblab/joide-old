package jo.joide.tasks;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import jo.joide.platform.RootedPath;

/**
 * Delta par fichier des entrées d'une tâche depuis sa dernière
 * exécution réussie : fichiers ajoutés, modifiés ou supprimés.
 *
 * <p>Le delta est calculé par le moteur à partir de l'ensemble sale
 * du système de fichiers virtuel et de la liste des entrées de la
 * tâche : les compilateurs le consomment directement, sans maintenir
 * d'état parallèle.</p>
 */
public final class InputDelta {

    private static final InputDelta EMPTY =
            new InputDelta(Set.of(), Set.of(), Set.of());

    private final Set<RootedPath> added;
    private final Set<RootedPath> modified;
    private final Set<RootedPath> removed;

    private InputDelta(Set<RootedPath> added, Set<RootedPath> modified,
                       Set<RootedPath> removed) {
        this.added = added;
        this.modified = modified;
        this.removed = removed;
    }

    /**
     * @return le delta vide, partagé
     */
    public static InputDelta empty() {
        return EMPTY;
    }

    /**
     * @param added    fichiers ajoutés
     * @param modified fichiers modifiés
     * @param removed  fichiers supprimés
     * @return un delta de fichiers
     */
    public static InputDelta of(Set<RootedPath> added, Set<RootedPath> modified,
                                Set<RootedPath> removed) {
        Objects.requireNonNull(added, "added");
        Objects.requireNonNull(modified, "modified");
        Objects.requireNonNull(removed, "removed");
        if (added.isEmpty() && modified.isEmpty() && removed.isEmpty()) {
            return EMPTY;
        }
        return new InputDelta(
                Collections.unmodifiableSet(new LinkedHashSet<>(added)),
                Collections.unmodifiableSet(new LinkedHashSet<>(modified)),
                Collections.unmodifiableSet(new LinkedHashSet<>(removed)));
    }

    /** @return les fichiers ajoutés */
    public Set<RootedPath> added() {
        return added;
    }

    /** @return les fichiers modifiés */
    public Set<RootedPath> modified() {
        return modified;
    }

    /** @return les fichiers supprimés */
    public Set<RootedPath> removed() {
        return removed;
    }

    /**
     * @return vrai si aucune entrée n'a changé
     */
    public boolean isEmpty() {
        return added.isEmpty() && modified.isEmpty() && removed.isEmpty();
    }

    /**
     * @return le nombre total de fichiers changés
     */
    public int size() {
        return added.size() + modified.size() + removed.size();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InputDelta)) {
            return false;
        }
        InputDelta that = (InputDelta) other;
        return added.equals(that.added) && modified.equals(that.modified)
                && removed.equals(that.removed);
    }

    @Override
    public int hashCode() {
        return added.hashCode() * 31 + modified.hashCode() * 7
                + removed.hashCode();
    }

    @Override
    public String toString() {
        return "InputDelta[+" + added.size() + ",~" + modified.size()
                + ",-" + removed.size() + "]";
    }
}
