package jo.joide.deps.lock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Une arête du lockfile : l'artefact dépendu et son genre.
 * Immuable.
 */
public final class LockfileEdge {

    public final jo.joide.deps.Coordinate target;
    public final EdgeKind kind;

    public LockfileEdge(jo.joide.deps.Coordinate target, EdgeKind kind) {
        this.target = Objects.requireNonNull(target, "target");
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    @Override
    public String toString() {
        return "-> " + target + " " + kind.text();
    }

    /** Ordre des arêtes : cible puis genre. */
    public static final java.util.Comparator<LockfileEdge> ORDER = (x, y) -> {
        int c = x.target.compareTo(y.target);
        return c != 0 ? c : x.kind.compareTo(y.kind);
    };

    public static void sortInPlace(List<LockfileEdge> edges) {
        edges.sort(ORDER);
    }

    static LockfileEdge parse(String line) {
        // « -> g:a:v[:c]@ext genre »
        String body = line.trim();
        if (!body.startsWith("->")) {
            return null;
        }
        String[] parts = body.substring(2).trim().split("\\s+");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new LockfileEdge(jo.joide.deps.Coordinate.parse(parts[0]),
                    EdgeKind.parse(parts[1]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static List<LockfileEdge> sortAll(List<LockfileEdge> edges) {
        List<LockfileEdge> out = new ArrayList<>(edges);
        out.sort(ORDER);
        return out;
    }
}
