package jo.joide.deps.resolve;

import java.util.List;
import jo.joide.platform.Problem;

/**
 * Le résultat d'une résolution : les artefacts de la fermeture (triés
 * par coordonnée), les problèmes structurés, et le compte rendu —
 * lockfile suivi ou non (zéro requête réseau), lockfile réécrit ou
 * inchangé, statistiques de transport.
 */
public final class ResolutionResult {

    /** Statistiques de la résolution. */
    public static final class Stats {
        public final int requests;      // appels de transport, tout genre
        public final int downloads;     // fichiers téléchargés
        public final long bytes;        // octets téléchargés

        Stats(int requests, int downloads, long bytes) {
            this.requests = requests;
            this.downloads = downloads;
            this.bytes = bytes;
        }
    }

    public final List<ResolvedArtifact> artifacts;
    public final List<Problem> problems;
    public final boolean lockfileFollowed;
    public final boolean lockfileRewritten;
    public final Stats stats;

    ResolutionResult(List<ResolvedArtifact> artifacts, List<Problem> problems,
                     boolean lockfileFollowed, boolean lockfileRewritten,
                     Stats stats) {
        this.artifacts = List.copyOf(artifacts);
        this.problems = List.copyOf(problems);
        this.lockfileFollowed = lockfileFollowed;
        this.lockfileRewritten = lockfileRewritten;
        this.stats = stats;
    }

    /** @return vrai si aucune erreur bloquante */
    public boolean success() {
        for (Problem p : problems) {
            if (p.isError()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "resolution(" + artifacts.size() + " artefacts, "
                + (lockfileFollowed ? "lockfile suivi" : "résolution complète")
                + ", " + stats.requests + " requêtes)";
    }
}
