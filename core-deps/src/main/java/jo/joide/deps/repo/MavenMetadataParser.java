package jo.joide.deps.repo;

import java.util.ArrayList;
import java.util.List;
import jo.joide.deps.xml.Xml;
import jo.joide.deps.xml.XmlNode;

/**
 * Analyse d'un maven-metadata.xml (niveau artefact ou niveau
 * version). Tolérant : les sections illisibles produisent des
 * problèmes ; le modèle reste partiel tant qu'un arbre existe.
 */
public final class MavenMetadataParser {

    /** Un problème d'analyse. */
    public static final class Problem {
        public final String message;

        Problem(String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return "maven-metadata: " + message;
        }
    }

    /** Le résultat : métadonnées (jamais null si racine lue) et problèmes. */
    public static final class Result {
        public final MavenMetadata metadata;
        public final List<Problem> problems;

        Result(MavenMetadata metadata, List<Problem> problems) {
            this.metadata = metadata;
            this.problems = List.copyOf(problems);
        }
    }

    private MavenMetadataParser() {
    }

    /**
     * @param text contenu du maven-metadata.xml
     * @return les métadonnées et les problèmes
     */
    public static Result parse(String text) {
        List<Problem> problems = new ArrayList<>();
        Xml.Document doc = Xml.parse(text);
        for (Xml.Problem xp : doc.problems) {
            problems.add(new Problem(xp.toString()));
        }
        if (doc.root == null) {
            return new Result(null, problems);
        }
        if (!"metadata".equals(doc.root.name())) {
            problems.add(new Problem("root element is not <metadata>"));
            return new Result(null, problems);
        }
        XmlNode versioning = doc.root.child("versioning");
        if (versioning == null) {
            problems.add(new Problem("missing <versioning>"));
            return new Result(null, problems);
        }
        List<String> versions = new ArrayList<>();
        XmlNode versionsEl = versioning.child("versions");
        if (versionsEl != null) {
            for (XmlNode v : versionsEl.children("version")) {
                String text2 = v.text();
                if (text2 != null && !text2.isEmpty()) {
                    versions.add(text2);
                }
            }
        }
        String latest = text(versioning, "latest");
        String release = text(versioning, "release");
        // Niveau version : l'état d'instantané.
        XmlNode snapshotEl = versioning.child("snapshot");
        MavenMetadata.Snapshot snapshot = null;
        if (snapshotEl != null) {
            String timestamp = text(snapshotEl, "timestamp");
            String buildNumber = text(snapshotEl, "buildNumber");
            String localCopy = text(snapshotEl, "localCopy");
            List<MavenMetadata.SnapshotVersion> svs = new ArrayList<>();
            XmlNode svsEl = versioning.child("snapshotVersions");
            if (svsEl != null) {
                for (XmlNode sv : svsEl.children("snapshotVersion")) {
                    String value = text(sv, "value");
                    String ext = text(sv, "extension");
                    String cls = text(sv, "classifier");
                    if (value != null) {
                        svs.add(new MavenMetadata.SnapshotVersion(value, ext,
                                cls));
                    }
                }
            }
            snapshot = new MavenMetadata.Snapshot(timestamp, buildNumber,
                    localCopy, svs);
        }
        return new Result(new MavenMetadata(versions, latest, release,
                snapshot), problems);
    }

    private static String text(XmlNode parent, String childName) {
        XmlNode c = parent.child(childName);
        return c != null ? c.text() : null;
    }
}
