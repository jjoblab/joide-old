package jo.joide.deps.lock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Une entrée du lockfile : l'artefact, son empreinte, sa taille, son
 * dépôt d'origine et ses arêtes. Immuable.
 */
public final class LockfileEntry {

    public final jo.joide.deps.Coordinate coordinate;
    public final String sha256;
    public final long size;
    public final String repositoryId;
    public final List<LockfileEdge> edges;

    public LockfileEntry(jo.joide.deps.Coordinate coordinate, String sha256,
                         long size, String repositoryId,
                         List<LockfileEdge> edges) {
        this.coordinate = Objects.requireNonNull(coordinate, "coordinate");
        this.sha256 = Objects.requireNonNull(sha256, "sha256");
        if (sha256.length() != 64 || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("bad sha256: " + sha256);
        }
        this.size = size;
        this.repositoryId = Objects.requireNonNull(repositoryId,
                "repositoryId");
        this.edges = LockfileEdge.sortAll(edges);
    }

    /** @return « g:a » de l'entrée */
    public String ga() {
        return coordinate.groupId() + ":" + coordinate.artifactId();
    }

    @Override
    public String toString() {
        return coordinate + " sha256=" + sha256.substring(0, 8) + "… repo="
                + repositoryId;
    }

    static LockfileEntry parse(String line) {
        // « g:a:v[:c]@ext sha256=<hex> size=<n> repo=<id> »
        String[] parts = line.trim().split("\\s+");
        if (parts.length != 4) {
            return null;
        }
        jo.joide.deps.Coordinate coordinate;
        try {
            coordinate = jo.joide.deps.Coordinate.parse(parts[0]);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String sha256 = value(parts[1], "sha256=");
        String sizeText = value(parts[2], "size=");
        String repo = value(parts[3], "repo=");
        if (sha256 == null || sizeText == null || repo == null) {
            return null;
        }
        long size;
        try {
            size = Long.parseLong(sizeText);
        } catch (NumberFormatException e) {
            return null;
        }
        try {
            return new LockfileEntry(coordinate, sha256, size, repo,
                    new ArrayList<>());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String value(String part, String prefix) {
        return part.startsWith(prefix) ? part.substring(prefix.length())
                : null;
    }
}
