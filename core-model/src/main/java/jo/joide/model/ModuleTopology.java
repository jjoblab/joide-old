package jo.joide.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Topologie du graphe de modules : niveaux topologiques, dépendances
 * inversées et détection de cycle avec reconstruction du chemin.
 *
 * <p>Les niveaux servent à l'ordonnancement général (un module de
 * niveau 2 dépend de modules de niveaux inférieurs) ; les dépendances
 * inverses servent à l'invalidation incrémentale. L'ordre de sortie
 * est toujours trié par nom : deux espaces égaux produisent la même
 * topologie.</p>
 */
public final class ModuleTopology {

    private final Map<String, Integer> levels;
    private final Map<String, Set<String>> reverseDependencies;
    private final List<List<String>> levelOrder;

    private ModuleTopology(Map<String, Integer> levels,
                           Map<String, Set<String>> reverseDependencies,
                           List<List<String>> levelOrder) {
        this.levels = levels;
        this.reverseDependencies = reverseDependencies;
        this.levelOrder = levelOrder;
    }

    /**
     * Construit la topologie d'un espace de travail.
     *
     * @param workspace espace de travail à analyser
     * @return la topologie correspondante
     * @throws ModelException si une dépendance pointe un module inconnu
     *         ou si le graphe contient un cycle
     */
    public static ModuleTopology of(Workspace workspace) {
        Objects.requireNonNull(workspace, "workspace");
        Map<String, Module> modules = workspace.allModules();
        Map<String, Set<String>> direct = new TreeMap<>();
        for (Map.Entry<String, Module> entry : modules.entrySet()) {
            Set<String> deps = new TreeSet<>();
            for (DependencyRef dependency : entry.getValue().dependencies()) {
                if (dependency.isModule()) {
                    if (!modules.containsKey(dependency.modulePath())) {
                        throw new ModelException("unknown module dependency: "
                                + entry.getKey() + " -> " + dependency.modulePath());
                    }
                    deps.add(dependency.modulePath());
                }
            }
            direct.put(entry.getKey(), deps);
        }
        // Kahn : niveaux et détection de cycle
        Map<String, Integer> remaining = new TreeMap<>();
        Map<String, Set<String>> reverse = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : direct.entrySet()) {
            remaining.put(entry.getKey(), entry.getValue().size());
            for (String dep : entry.getValue()) {
                reverse.computeIfAbsent(dep, k -> new TreeSet<>())
                        .add(entry.getKey());
            }
        }
        Map<String, Integer> levels = new TreeMap<>();
        List<List<String>> order = new ArrayList<>();
        Deque<String> ready = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }
        if (ready.isEmpty() && !remaining.isEmpty()) {
            throw cycle(direct);
        }
        while (!ready.isEmpty()) {
            List<String> level = new ArrayList<>();
            Deque<String> next = new ArrayDeque<>();
            // vider la file courante : tous les modules prêts forment un niveau
            for (String name : ready) {
                level.add(name);
                levels.put(name, order.size());
            }
            java.util.Collections.sort(level);
            for (String name : level) {
                for (String dependent : reverse.getOrDefault(
                        name, Set.of())) {
                    if (remaining.merge(dependent, -1, Integer::sum) == 0) {
                        next.add(dependent);
                    }
                }
            }
            order.add(level);
            ready = next;
            if (ready.isEmpty() && order.stream().mapToInt(List::size).sum()
                    != modules.size()) {
                throw cycle(direct);
            }
        }
        Map<String, Set<String>> reverseView = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : reverse.entrySet()) {
            reverseView.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return new ModuleTopology(levels, reverseView,
                order.stream().map(List::copyOf).toList());
    }

    /** Reconstruit un cycle par parcours en profondeur, pour un message utile. */
    private static ModelException cycle(Map<String, Set<String>> direct) {
        Map<String, Integer> state = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        for (String start : direct.keySet()) {
            if (state.getOrDefault(start, 0) != 0) {
                continue;
            }
            Deque<String> stack = new ArrayDeque<>();
            stack.push(start);
            state.put(start, 1);
            while (!stack.isEmpty()) {
                String current = stack.peek();
                String unvisited = null;
                for (String dep : direct.getOrDefault(current, Set.of())) {
                    int depState = state.getOrDefault(dep, 0);
                    if (depState == 1) {
                        // cycle trouvé : remonter par les parents
                        List<String> path = new ArrayList<>();
                        path.add(dep);
                        String walk = current;
                        while (!walk.equals(dep)) {
                            path.add(walk);
                            walk = parent.get(walk);
                        }
                        path.add(dep);
                        java.util.Collections.reverse(path);
                        return new ModelException("module cycle: "
                                + String.join(" -> ", path));
                    }
                    if (depState == 0) {
                        unvisited = dep;
                        break;
                    }
                }
                if (unvisited != null) {
                    parent.put(unvisited, current);
                    state.put(unvisited, 1);
                    stack.push(unvisited);
                } else {
                    state.put(current, 2);
                    stack.pop();
                }
            }
        }
        return new ModelException("module cycle detected");
    }

    /** @return le niveau topologique de chaque module */
    public Map<String, Integer> levels() {
        return levels;
    }

    /** @return les dépendances inverses : module -> modules qui en dépendent */
    public Map<String, Set<String>> reverseDependencies() {
        return reverseDependencies;
    }

    /** @return les niveaux dans l'ordre, chaque niveau trié par nom */
    public List<List<String>> levelOrder() {
        return levelOrder;
    }

    /**
     * @param modulePath chemin de module
     * @return le niveau du module
     * @throws IllegalArgumentException si le module est inconnu
     */
    public int levelOf(String modulePath) {
        Integer level = levels.get(modulePath);
        if (level == null) {
            throw new IllegalArgumentException("unknown module: " + modulePath);
        }
        return level;
    }

    /**
     * @param modulePath chemin de module
     * @return les modules qui en dépendent directement
     */
    public Set<String> dependentsOf(String modulePath) {
        return reverseDependencies.getOrDefault(modulePath, Set.of());
    }
}
