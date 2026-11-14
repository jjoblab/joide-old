package jo.joide.buildlogic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Graphe de modules autorisé, lu depuis gradle/module-graph.txt (ADR-011).
 *
 * <p>Le fichier définit une ligne par module, dans l'ordre descendant du
 * graphe (les modules les plus bas d'abord) : « nom -&gt; dep1 dep2 ».
 * Les invariants contrôlés à la lecture : toute dépendance citée existe
 * comme module ; aucune arête ne remonte (l'indice de la dépendance est
 * toujours strictement inférieur à celui du module) ; le graphe est
 * acyclique (Kahn, avec reconstruction d'un cycle pour le message).</p>
 */
public final class ModuleGraph {

    /** Ordre de déclaration (descendant) : indice -&gt; nom. */
    private final List<String> order = new ArrayList<>();
    /** Nom -&gt; indice de déclaration. */
    private final Map<String, Integer> index = new HashMap<>();
    /** Nom -&gt; dépendances directes autorisées (triées comme déclarées). */
    private final Map<String, List<String>> allowed = new LinkedHashMap<>();

    private ModuleGraph() {
    }

    /**
     * Charge et valide le fichier ; toute violation lève
     * {@link IllegalArgumentException} avec un message en anglais (D3).
     *
     * @param file chemin du fichier module-graph.txt
     * @return le graphe validé
     */
    public static ModuleGraph load(Path file) {
        Objects.requireNonNull(file, "file");
        ModuleGraph graph = new ModuleGraph();
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int arrow = line.indexOf("->");
                if (arrow < 0) {
                    throw new IllegalArgumentException(
                            "module graph line without '->': \"" + raw + "\"");
                }
                String name = line.substring(0, arrow).trim();
                graph.declare(name);
                for (String dep : line.substring(arrow + 2).trim().split("\\s+")) {
                    if (!dep.isEmpty()) {
                        graph.edge(name, dep);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "cannot read module graph: " + file, e);
        }
        graph.checkAcyclic();
        return graph;
    }

    private void declare(String name) {
        if (index.containsKey(name)) {
            throw new IllegalArgumentException(
                    "module declared twice in graph: " + name);
        }
        index.put(name, order.size());
        order.add(name);
        allowed.put(name, new ArrayList<>());
    }

    private void edge(String from, String to) {
        if (!index.containsKey(to)) {
            throw new IllegalArgumentException(
                    "unknown module in graph: '" + to + "' (required by "
                            + from + ")");
        }
        if (index.get(to) >= index.get(from)) {
            throw new IllegalArgumentException(
                    "graph must be descending-only: '" + from + "' -> '"
                            + to + "' points upward or sideways");
        }
        List<String> deps = allowed.get(from);
        if (!deps.contains(to)) {
            deps.add(to);
        }
    }

    private void checkAcyclic() {
        // Kahn ; le graphe est descendant par construction, le contrôle
        // reste là par défense (et servira au message de cycle).
        Map<String, Integer> remaining = new HashMap<>();
        for (String name : order) {
            remaining.put(name, allowed.get(name).size());
        }
        Map<String, List<String>> reverse = new HashMap<>();
        for (String name : order) {
            for (String dep : allowed.get(name)) {
                reverse.computeIfAbsent(dep, k -> new ArrayList<>()).add(name);
            }
        }
        Deque<String> ready = new ArrayDeque<>();
        for (String name : order) {
            if (remaining.get(name) == 0) {
                ready.add(name);
            }
        }
        int visited = 0;
        while (!ready.isEmpty()) {
            String name = ready.poll();
            visited++;
            for (String up : reverse.getOrDefault(name, new ArrayList<>())) {
                int left = remaining.merge(up, -1, Integer::sum);
                if (left == 0) {
                    ready.add(up);
                }
            }
        }
        if (visited != order.size()) {
            throw new IllegalArgumentException(
                    "module graph contains a cycle");
        }
    }

    /** @return vrai si le module est déclaré dans le graphe */
    public boolean knows(String module) {
        return index.containsKey(module);
    }

    /** @return les dépendances directes autorisées du module */
    public List<String> dependenciesOf(String module) {
        List<String> deps = allowed.get(module);
        if (deps == null) {
            throw new IllegalArgumentException("unknown module: " + module);
        }
        return deps;
    }

    /** @return la liste ordonnée (descendante) des modules déclarés */
    public List<String> modules() {
        return order;
    }
}
