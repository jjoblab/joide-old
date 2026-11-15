package jo.joide.deps;

import java.util.Objects;

/**
 * Une demande de dépendance soumise au résolveur : groupe, artefact,
 * exigence de version et mode d'exposition. La fermeture transitive
 * est toujours résolue (api + exécution) ; l'exposition ne sert
 * qu'au consommateur pour assembler ses classpaths.
 */
public final class DependencyRequest {

    private final String groupId;
    private final String artifactId;
    private final VersionRequirement requirement;
    private final Exposure exposure;

    /**
     * @param groupId     identifiant de groupe
     * @param artifactId  identifiant d'artefact
     * @param requirement exigence de version (jamais stricte : la
     *                    stricte est réservée au lockfile)
     * @param exposure    mode d'exposition
     */
    public DependencyRequest(String groupId, String artifactId,
                             VersionRequirement requirement,
                             Exposure exposure) {
        this.groupId = Objects.requireNonNull(groupId, "groupId");
        this.artifactId = Objects.requireNonNull(artifactId, "artifactId");
        if (groupId.isEmpty() || artifactId.isEmpty()) {
            throw new IllegalArgumentException("empty group or artifact");
        }
        this.requirement = Objects.requireNonNull(requirement, "requirement");
        if (requirement.isStrict()) {
            throw new IllegalArgumentException(
                    "strict requirement is lockfile-only");
        }
        this.exposure = Objects.requireNonNull(exposure, "exposure");
    }

    /** @return le groupe */
    public String groupId() {
        return groupId;
    }

    /** @return l'artefact */
    public String artifactId() {
        return artifactId;
    }

    /** @return « g:a » */
    public String ga() {
        return groupId + ":" + artifactId;
    }

    /** @return l'exigence de version */
    public VersionRequirement requirement() {
        return requirement;
    }

    /** @return le mode d'exposition */
    public Exposure exposure() {
        return exposure;
    }

    @Override
    public String toString() {
        return ga() + ":" + requirement + "|" + exposure.name().toLowerCase(
                java.util.Locale.ROOT);
    }
}
