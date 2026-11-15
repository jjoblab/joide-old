package jo.joide.deps;

import java.util.Comparator;
import java.util.Objects;

/**
 * Une coordonnée d'artefact Maven : groupe, artefact, version,
 * classifieur et extension. La forme canonique suit Maven :
 * {@code groupe:artefact:version[:classifieur][@extension]}.
 *
 * <p>Immuable. L'extension vaut {@code jar} par défaut quand la
 * coordonnée est écrite sans extension ; {@code pom} désigne le
 * descripteur.</p>
 */
public final class Coordinate implements Comparable<Coordinate> {

    private final String groupId;
    private final String artifactId;
    private final Version version;
    private final String classifier;
    private final String extension;

    private Coordinate(String groupId, String artifactId, Version version,
                       String classifier, String extension) {
        this.groupId = validate(groupId, "groupId");
        this.artifactId = validate(artifactId, "artifactId");
        this.version = version;
        this.classifier = (classifier == null || classifier.isEmpty())
                ? null : validate(classifier, "classifier");
        this.extension = validate(extension, "extension");
    }

    /**
     * @param groupId    identifiant de groupe
     * @param artifactId identifiant d'artefact
     * @param version    version (jamais {@code null} ici)
     * @param classifier classifieur éventuel
     * @param extension  extension (« jar », « aar », « pom »…)
     * @return la coordonnée complète
     */
    public static Coordinate of(String groupId, String artifactId,
                                Version version, String classifier,
                                String extension) {
        return new Coordinate(groupId, artifactId,
                Objects.requireNonNull(version, "version"),
                classifier, extension);
    }

    /**
     * Analyse une coordonnée canonique. La version est obligatoire
     * pour la résolution ; les formes sans version sont rejetées.
     *
     * @param text coordonnée « g:a:v[:c][@ext] »
     * @return la coordonnée
     * @throws IllegalArgumentException si la forme est invalide
     */
    public static Coordinate parse(String text) {
        Objects.requireNonNull(text, "text");
        String rest = text;
        String extension = "jar";
        int at = rest.indexOf('@');
        if (at >= 0) {
            extension = rest.substring(at + 1);
            if (extension.isEmpty()) {
                throw new IllegalArgumentException("empty extension: " + text);
            }
            rest = rest.substring(0, at);
        }
        String[] parts = rest.split(":", -1);
        if (parts.length < 3 || parts.length > 4) {
            throw new IllegalArgumentException("invalid coordinate: " + text);
        }
        String classifier = parts.length == 4 ? parts[3] : null;
        if (classifier != null && classifier.isEmpty()) {
            throw new IllegalArgumentException("empty classifier: " + text);
        }
        return new Coordinate(parts[0], parts[1],
                Version.parse(parts[2]), classifier, extension);
    }

    private static String validate(String value, String what) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("empty " + what);
        }
        return value;
    }

    /** @return le groupe */
    public String groupId() {
        return groupId;
    }

    /** @return l'artefact */
    public String artifactId() {
        return artifactId;
    }

    /** @return la version */
    public Version version() {
        return version;
    }

    /** @return le classifieur, ou {@code null} */
    public String classifier() {
        return classifier;
    }

    /** @return l'extension */
    public String extension() {
        return extension;
    }

    /** @return « g:a » sans version */
    public String ga() {
        return groupId + ":" + artifactId;
    }

    /**
     * @return la coordonnée sans extension ni classifieur, avec
     * version (« g:a:v[:c] ») — la clé d'identification d'artefact
     */
    public String key() {
        return version + (classifier != null ? ":" + classifier : "");
    }

    /**
     * @return la forme canonique avec extension explicite
     */
    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        out.append(groupId).append(':').append(artifactId).append(':')
                .append(version);
        if (classifier != null) {
            out.append(':').append(classifier);
        }
        return out.append('@').append(extension).toString();
    }

    @Override
    public int compareTo(Coordinate other) {
        int c = groupId.compareTo(other.groupId);
        if (c != 0) {
            return c;
        }
        c = artifactId.compareTo(other.artifactId);
        if (c != 0) {
            return c;
        }
        c = version.compareTo(other.version);
        if (c != 0) {
            return c;
        }
        c = Objects.compare(classifier, other.classifier,
                Comparator.nullsFirst(String::compareTo));
        if (c != 0) {
            return c;
        }
        return extension.compareTo(other.extension);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Coordinate)) {
            return false;
        }
        Coordinate that = (Coordinate) other;
        return groupId.equals(that.groupId)
                && artifactId.equals(that.artifactId)
                && version.equals(that.version)
                && Objects.equals(classifier, that.classifier)
                && extension.equals(that.extension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, classifier,
                extension);
    }
}
