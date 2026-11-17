package jo.joide.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Une dépendance déclarée : soit un module du même espace de travail
 * (par chemin), soit une coordonnée externe. Les deux sont exclusifs.
 */
public final class DependencyRef {

    private final String modulePath;
    private final String coordinates;
    private final DependencyScope scope;
    private final Set<String> variants;

    private DependencyRef(String modulePath, String coordinates,
                          DependencyScope scope, Set<String> variants) {
        this.modulePath = modulePath;
        this.coordinates = coordinates;
        this.scope = Objects.requireNonNull(scope, "scope");
        this.variants = Set.copyOf(variants);
    }

    /**
     * @param modulePath chemin du module dépendu (ex. « :app »)
     * @param scope      portée de la dépendance
     * @param variants   variantes concernées, éventuellement vides
     * @return une dépendance de module
     */
    public static DependencyRef toModule(String modulePath,
                                         DependencyScope scope,
                                         Set<String> variants) {
        if (modulePath == null || !modulePath.startsWith(":")
                || modulePath.length() < 2) {
            throw new IllegalArgumentException("invalid module path: " + modulePath);
        }
        return new DependencyRef(modulePath, null, scope, variants);
    }

    /**
     * @param coordinates coordonnée « groupe:artefact:version »
     * @param scope       portée de la dépendance
     * @param variants    variantes concernées
     * @return une dépendance externe
     */
    public static DependencyRef external(String coordinates,
                                         DependencyScope scope,
                                         Set<String> variants) {
        if (coordinates == null || coordinates.split(":").length != 3) {
            throw new IllegalArgumentException(
                    "invalid coordinates: " + coordinates);
        }
        return new DependencyRef(null, coordinates, scope, variants);
    }

    /** @return le chemin du module dépendu, ou {@code null} si externe */
    public String modulePath() {
        return modulePath;
    }

    /** @return la coordonnée externe, ou {@code null} si module */
    public String coordinates() {
        return coordinates;
    }

    /** @return vrai si la dépendance pointe un module du workspace */
    public boolean isModule() {
        return modulePath != null;
    }

    /** @return la portée */
    public DependencyScope scope() {
        return scope;
    }

    /** @return les variantes concernées, triées */
    public Set<String> variants() {
        return variants;
    }

    /**
     * @return une représentation canonique stable, pour les empreintes
     */
    public String canonical() {
        StringBuilder out = new StringBuilder();
        out.append(modulePath != null ? modulePath : coordinates)
                .append('|').append(scope.name().toLowerCase(java.util.Locale.ROOT));
        if (!variants.isEmpty()) {
            List<String> sorted = new ArrayList<>(new LinkedHashSet<>(variants));
            java.util.Collections.sort(sorted);
            for (String variant : sorted) {
                out.append('|').append(variant);
            }
        }
        return out.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DependencyRef)) {
            return false;
        }
        DependencyRef that = (DependencyRef) other;
        return Objects.equals(modulePath, that.modulePath)
                && Objects.equals(coordinates, that.coordinates)
                && scope == that.scope
                && variants.equals(that.variants);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modulePath, coordinates, scope, variants);
    }

    @Override
    public String toString() {
        return canonical();
    }
}
