package jo.joide.deps.lock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Le lockfile joide.lock : l'ensemble des artefacts résolus, leur
 * empreinte, leur dépôt d'origine et le graphe des dépendances —
 * sérialisé de façon déterministe (tri par coordonnée, LF, UTF-8),
 * identique octet pour octet entre deux résolutions des mêmes
 * demandes (preuve exigée par l'ADR-007).
 *
 * <p>En lecture : tolérant — toute ligne mal formée produit un
 * problème et est ignorée ; les entrées restent utilisables.</p>
 */
public final class Lockfile {

    /** L'en-tête de format. */
    public static final String HEADER = "joide.lock v1";

    /** Un problème de lecture ou d'écriture. */
    public static final class Problem {
        public final String message;

        Problem(String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return "lockfile: " + message;
        }
    }

    private final List<LockfileEntry> entries;

    private Lockfile(List<LockfileEntry> entries) {
        this.entries = entries;
    }

    /**
     * @param entries entrées (doublons interdits par la construction)
     * @return un lockfile trié et vérifié
     * @throws IllegalArgumentException sur doublon de coordonnée
     */
    public static Lockfile of(List<LockfileEntry> entries) {
        Map<String, LockfileEntry> byKey = new TreeMap<>();
        for (LockfileEntry e : entries) {
            String key = e.coordinate.toString();
            if (byKey.containsKey(key)) {
                throw new IllegalArgumentException("duplicate entry: " + key);
            }
            byKey.put(key, e);
        }
        List<LockfileEntry> sorted = new ArrayList<>(byKey.values());
        return new Lockfile(sorted);
    }

    /**
     * @param text texte du lockfile
     * @return le lockfile et les problèmes de lecture
     */
    public static ParseResult parse(String text) {
        List<Problem> problems = new ArrayList<>();
        List<LockfileEntry> entries = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        boolean headerSeen = false;
        LockfileEntry current = null;
        for (int ln = 0; ln < lines.length; ln++) {
            String raw = lines[ln];
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!headerSeen) {
                if (HEADER.equals(line)) {
                    headerSeen = true;
                    continue;
                }
                problems.add(new Problem("ligne " + (ln + 1)
                        + " : en-tete manquant"));
                // Tolérant : on continue sans en-tête, l'entrée suivante
                // est une entrée d'artefact.
            }
            if (line.startsWith("->")) {
                LockfileEdge edge = LockfileEdge.parse(line);
                if (edge == null) {
                    problems.add(new Problem("ligne " + (ln + 1)
                            + " : arête mal formée"));
                } else if (current == null) {
                    problems.add(new Problem("ligne " + (ln + 1)
                            + " : arête orpheline"));
                } else {
                    current.edges.add(edge);
                }
                continue;
            }
            LockfileEntry entry = LockfileEntry.parse(line);
            if (entry == null) {
                problems.add(new Problem("ligne " + (ln + 1)
                        + " : entrée mal formée"));
                current = null;
                continue;
            }
            current = entry;
            entries.add(entry);
        }
        // Arêtes ajoutées après construction : les trier par entrée.
        for (LockfileEntry e : entries) {
            LockfileEdge.sortInPlace(e.edges);
        }
        return new ParseResult(new Lockfile(entries), problems);
    }

    /** Résultat de lecture : lockfile et problèmes. */
    public static final class ParseResult {
        public final Lockfile lockfile;
        public final List<Problem> problems;

        ParseResult(Lockfile lockfile, List<Problem> problems) {
            this.lockfile = lockfile;
            this.problems = List.copyOf(problems);
        }
    }

    /** @return les entrées, triées par coordonnée */
    public List<LockfileEntry> entries() {
        return entries;
    }

    /**
     * @return une carte « g:a » → entrées (un même g:a peut avoir
     *         plusieurs classifieurs)
     */
    public Map<String, List<LockfileEntry>> byGa() {
        Map<String, List<LockfileEntry>> out = new HashMap<>();
        for (LockfileEntry e : entries) {
            out.computeIfAbsent(e.ga(), k -> new ArrayList<>()).add(e);
        }
        return out;
    }

    /**
     * @param ga  « g:a »
     * @param key « v[:c] » (version et classifieur)
     * @return l'entrée correspondante, ou {@code null}
     */
    public LockfileEntry find(String ga, String key) {
        for (LockfileEntry e : entries) {
            if (e.ga().equals(ga) && e.coordinate.key().equals(key)) {
                return e;
            }
        }
        return null;
    }

    /**
     * @return la sérialisation déterministe (LF, UTF-8)
     */
    public String serialize() {
        StringBuilder out = new StringBuilder();
        out.append(HEADER).append('\n');
        for (LockfileEntry e : entries) {
            out.append(e.coordinate).append(" sha256=").append(e.sha256)
                    .append(" size=").append(e.size)
                    .append(" repo=").append(e.repositoryId).append('\n');
            for (LockfileEdge edge : e.edges) {
                out.append("  ").append(edge).append('\n');
            }
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return "joide.lock(" + entries.size() + " entrées)";
    }
}
