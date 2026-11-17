package jo.joide.model;

import java.util.List;
import java.util.Objects;
import jo.joide.platform.Hash128;

/**
 * Une entrée de classpath : un module du workspace (identifié par son
 * identifiant de contenu) ou une coordonnée externe résolue.
 * Immuable.
 */
public final class ClasspathEntry {

    private final String modulePath;
    private final Hash128 moduleContentId;
    private final String coordinates;
    private final String version;
    private final DependencyScope scope;

    private ClasspathEntry(String modulePath, Hash128 moduleContentId,
                           String coordinates, String version,
                           DependencyScope scope) {
        this.modulePath = modulePath;
        this.moduleContentId = moduleContentId;
        this.coordinates = coordinates;
        this.version = version;
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    /**
     * @param module     module du workspace
     * @param scope      portée effective dans ce classpath
     * @return une entrée de module
     */
    public static ClasspathEntry ofModule(Module module, DependencyScope scope) {
        return new ClasspathEntry(module.path(), module.contentId(),
                null, null, scope);
    }

    /**
     * @param coordinates coordonnée « groupe:artefact »
     * @param version     version retenue
     * @param scope       portée
     * @return une entrée externe
     */
    public static ClasspathEntry external(String coordinates, String version,
                                          DependencyScope scope) {
        if (coordinates == null || version == null
                || coordinates.split(":").length != 2) {
            throw new IllegalArgumentException(
                    "invalid external entry: " + coordinates + ":" + version);
        }
        return new ClasspathEntry(null, null, coordinates, version, scope);
    }

    /** @return le chemin du module, ou {@code null} si externe */
    public String modulePath() {
        return modulePath;
    }

    /** @return l'identifiant de contenu du module, ou {@code null} */
    public Hash128 moduleContentId() {
        return moduleContentId;
    }

    /** @return la coordonnée externe, ou {@code null} si module */
    public String coordinates() {
        return coordinates;
    }

    /** @return la version retenue, ou {@code null} si module */
    public String version() {
        return version;
    }

    /** @return la portée effective */
    public DependencyScope scope() {
        return scope;
    }

    /** @return la clé d'identité : chemin du module ou coordonnée */
    String identityKey() {
        return modulePath != null ? "m:" + modulePath : "e:" + coordinates;
    }

    /** @return la forme canonique pour l'empreinte du classpath */
    String canonical() {
        return identityKey() + "|"
                + (version != null ? version : moduleContentId.toHex())
                + "|" + scope.name();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ClasspathEntry)) {
            return false;
        }
        ClasspathEntry that = (ClasspathEntry) other;
        return java.util.Objects.equals(modulePath, that.modulePath)
                && java.util.Objects.equals(moduleContentId, that.moduleContentId)
                && java.util.Objects.equals(coordinates, that.coordinates)
                && java.util.Objects.equals(version, that.version)
                && scope == that.scope;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(modulePath, moduleContentId,
                coordinates, version, scope);
    }

    @Override
    public String toString() {
        return modulePath != null
                ? modulePath + " (" + scope.name().toLowerCase() + ")"
                : coordinates + ":" + version + " ("
                        + scope.name().toLowerCase() + ")";
    }
}
