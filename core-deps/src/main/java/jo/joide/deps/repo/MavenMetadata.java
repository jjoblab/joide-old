package jo.joide.deps.repo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Un maven-metadata.xml analysé : version publiées d'un artefact
 * (niveau artefact) et, pour un instantané donné (niveau version),
 * l'instantané concret que le dépôt sert — horodatage, numéro de
 * construction et valeurs par extension/classifieur.
 */
public final class MavenMetadata {

    /** Une valeur d'instantané concret pour une extension/classifieur. */
    public static final class SnapshotVersion {
        public final String value;
        public final String extension;
        public final String classifier;

        public SnapshotVersion(String value, String extension,
                               String classifier) {
            this.value = Objects.requireNonNull(value, "value");
            this.extension = extension;
            this.classifier = classifier;
        }

        @Override
        public String toString() {
            return value + (extension != null ? "." + extension : "");
        }
    }

    /** L'état d'instantané d'une version donnée. */
    public static final class Snapshot {
        public final String timestamp;
        public final String buildNumber;
        public final String localCopy; // "true" pour un instantané local
        public final List<SnapshotVersion> snapshotVersions;

        public Snapshot(String timestamp, String buildNumber, String localCopy,
                        List<SnapshotVersion> snapshotVersions) {
            this.timestamp = timestamp;
            this.buildNumber = buildNumber;
            this.localCopy = localCopy;
            this.snapshotVersions = List.copyOf(snapshotVersions);
        }

        /**
         * @param extension  extension voulue (« jar », « aar »)
         * @param classifier classifieur éventuel
         * @return la valeur concrète servie, ou {@code null}
         */
        public String valueFor(String extension, String classifier) {
            for (SnapshotVersion sv : snapshotVersions) {
                if (Objects.equals(sv.extension, extension)
                        && Objects.equals(sv.classifier, classifier)) {
                    return sv.value;
                }
            }
            // Repli : la forme composée horodatage-build.
            if (timestamp != null && buildNumber != null) {
                return null; // le composeur est au résolveur, qui connaît
                             // la version de base.
            }
            return null;
        }
    }

    /** Les versions publiées, dans l'ordre du document. */
    public final List<String> versions;
    public final String latest;
    public final String release;
    public final Snapshot snapshot;

    public MavenMetadata(List<String> versions, String latest, String release,
                         Snapshot snapshot) {
        this.versions = List.copyOf(versions);
        this.latest = latest;
        this.release = release;
        this.snapshot = snapshot;
    }

    /**
     * @return les versions publiées non instantanées (suffixe exclu),
     *         dans l'ordre du document
     */
    public List<String> nonSnapshotVersions() {
        List<String> out = new ArrayList<>();
        for (String v : versions) {
            if (v != null && !v.regionMatches(true, v.length() - 9,
                    "-SNAPSHOT", 0, 9)) {
                out.add(v);
            }
        }
        return out;
    }
}
