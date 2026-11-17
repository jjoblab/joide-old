package jo.joide.deps.resolve;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import jo.joide.deps.Coordinate;
import jo.joide.deps.aar.AarContents;
import jo.joide.deps.lock.LockfileEdge;

/**
 * Un artefact résolu : coordonnée, empreinte, dépôt d'origine, fichier
 * du cache partagé, arêtes vers ses dépendances, et contenu extrait
 * si AAR. Immuable.
 */
public final class ResolvedArtifact {

    public final Coordinate coordinate;
    public final String sha256;
    public final long size;
    public final String repositoryId;
    public final Path file;
    public final boolean aar;
    public final AarContents aarContents;
    public final List<LockfileEdge> edges;

    public ResolvedArtifact(Coordinate coordinate, String sha256, long size,
                            String repositoryId, Path file, boolean aar,
                            AarContents aarContents,
                            List<LockfileEdge> edges) {
        this.coordinate = Objects.requireNonNull(coordinate, "coordinate");
        this.sha256 = Objects.requireNonNull(sha256, "sha256");
        this.size = size;
        this.repositoryId = Objects.requireNonNull(repositoryId, "repo");
        this.file = file;
        this.aar = aar;
        this.aarContents = requireAar(aar, aarContents);
        this.edges = List.copyOf(edges);
    }

    private static AarContents requireAar(boolean aar, AarContents contents) {
        if (!aar) {
            return null;
        }
        return Objects.requireNonNull(contents, "aarContents");
    }

    /** @return « g:a » */
    public String ga() {
        return coordinate.groupId() + ":" + coordinate.artifactId();
    }

    @Override
    public String toString() {
        return coordinate + "#" + sha256.substring(0, 8);
    }
}
