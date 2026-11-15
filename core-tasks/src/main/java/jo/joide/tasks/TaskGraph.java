package jo.joide.tasks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import jo.joide.platform.RootedPath;

/**
 * Graphe de tâches validé : dépendances dures, arêtes d'ordre
 * « mustRunAfter » et, si l'inférence est activée, dépendances
 * implicites sortie vers entrée.
 *
 * <p>La validation rejette les cycles de dépendances avec le chemin
 * exact du cycle, les références inconnues et les contraintes d'ordre
 * contraires aux dépendances. Les dépendances explicites sont
 * prioritaires : une arête inférée qui duplique une arête explicite
 * n'est pas ajoutée deux fois.</p>
 */
public final class TaskGraph {

    private final Map<String, TaskDefinition> tasks;
    private final Map<String, Set<String>> hardDeps;
    private final Map<String, Set<String>> dependents;
    private final Map<String, Set<String>> orderSuccessors;
    private final List<String> insertionOrder;
    private final List<String> topologicalOrder;
    private final boolean inferred;

    private TaskGraph(Map<String, TaskDefinition> tasks,
                      Map<String, Set<String>> hardDeps,
                      Map<String, Set<String>> dependents,
                      Map<String, Set<String>> orderSuccessors,
                      List<String> insertionOrder,
                      List<String> topologicalOrder,
                      boolean inferred) {
        this.tasks = tasks;
        this.hardDeps = hardDeps;
        this.dependents = dependents;
        this.orderSuccessors = orderSuccessors;
        this.insertionOrder = insertionOrder;
        this.topologicalOrder = topologicalOrder;
        this.inferred = inferred;
    }

    /**
     * @return un bâtisseur de graphe
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return les noms des tâches dans l'ordre d'enregistrement
     */
    public List<String> names() {
        return insertionOrder;
    }

    /**
     * @param name nom de la tâche
     * @return la définition enregistrée
     * @throws IllegalArgumentException si le nom est inconnu
     */
    public TaskDefinition task(String name) {
        TaskDefinition task = tasks.get(name);
        if (task == null) {
            throw new IllegalArgumentException("unknown task: " + name);
        }
        return task;
    }

    /**
     * @param name nom de la tâche
     * @return les dépendances dures, non modifiables
     */
    public Set<String> hardDepsOf(String name) {
        return hardDeps.getOrDefault(name, Set.of());
    }

    /**
     * @param name nom de la tâche
     * @return les tâches qui dépendent durement de celle-ci
     */
    public Set<String> dependentsOf(String name) {
        return dependents.getOrDefault(name, Set.of());
    }

    /**
     * @param name nom de la tâche
     * @return les tâches qui doivent s'exécuter après celle-ci, au
     *         sens de l'ordre seul
     */
    public Set<String> orderSuccessorsOf(String name) {
        return orderSuccessors.getOrDefault(name, Set.of());
    }

    /**
     * @return un ordre topologique des noms (tout ordre valide)
     */
    public List<String> topologicalOrder() {
        return topologicalOrder;
    }

    /**
     * @return le nombre de tâches du graphe
     */
    public int size() {
        return tasks.size();
    }

    /**
     * @return vrai si l'inférence par préfixe de chemins a été activée
     */
    public boolean isInferred() {
        return inferred;
    }

    /**
     * Bâtisseur de graphe de tâches.
     */
    public static final class Builder {

        private final Map<String, TaskDefinition> tasks = new LinkedHashMap<>();
        private final Map<String, Set<String>> hardDeps = new LinkedHashMap<>();
        private final Map<String, Set<String>> orderEdges = new LinkedHashMap<>();
        private boolean inferByPath = false;

        /**
         * Enregistre une tâche.
         *
         * @param task définition de la tâche
         * @return ce bâtisseur
         * @throws TaskGraphException si le nom est déjà pris
         */
        public Builder register(TaskDefinition task) {
            Objects.requireNonNull(task, "task");
            if (tasks.containsKey(task.name())) {
                throw new TaskGraphException(
                        "duplicate task name: " + task.name());
            }
            tasks.put(task.name(), task);
            return this;
        }

        /**
         * Déclare une dépendance dure : la tâche ne démarre qu'après
         * la fin de chacune de ses dépendances.
         *
         * @param taskName tâche dépendante
         * @param onNames  tâches attendues
         * @return ce bâtisseur
         */
        public Builder dependsOn(String taskName, String... onNames) {
            Objects.requireNonNull(taskName, "taskName");
            Objects.requireNonNull(onNames, "onNames");
            Set<String> deps = hardDeps.computeIfAbsent(taskName,
                    key -> new LinkedHashSet<>());
            for (String on : onNames) {
                deps.add(Objects.requireNonNull(on, "on"));
            }
            return this;
        }

        /**
         * Déclare une contrainte d'ordre : la tâche s'exécute après
         * une autre si les deux sont prêtes, sans dépendance de
         * données.
         *
         * @param taskName  tâche ordonnée
         * @param afterNames tâches à terminer d'abord
         * @return ce bâtisseur
         */
        public Builder mustRunAfter(String taskName, String... afterNames) {
            Objects.requireNonNull(taskName, "taskName");
            Objects.requireNonNull(afterNames, "afterNames");
            Set<String> after = orderEdges.computeIfAbsent(taskName,
                    key -> new LinkedHashSet<>());
            for (String name : afterNames) {
                after.add(Objects.requireNonNull(name, "name"));
            }
            return this;
        }

        /**
         * Active l'inférence des dépendances par préfixe de chemins :
         * une tâche dont une entrée est couverte par la sortie d'une
         * autre en dépend implicitement.
         *
         * @return ce bâtisseur
         */
        public Builder inferByPath() {
            this.inferByPath = true;
            return this;
        }

        /**
         * Valide et fige le graphe.
         *
         * @return le graphe validé
         * @throws TaskGraphException sur référence inconnue, dépendance
         *         vers soi-même, cycle ou contrainte d'ordre contraire
         *         aux dépendances
         */
        public TaskGraph build() {
            validateReferences();
            Map<String, Set<String>> deps = new LinkedHashMap<>();
            for (Map.Entry<String, Set<String>> entry : hardDeps.entrySet()) {
                deps.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
            }
            boolean inferredEdges = false;
            if (inferByPath) {
                inferredEdges = inferEdges(deps);
            }
            Map<String, Set<String>> dependents = invert(tasks, deps);
            List<String> topo = kahnOrCycle(tasks, deps, dependents);
            Map<String, Set<String>> successors = invertOrder(tasks, orderEdges);
            validateOrderConstraints(tasks, deps, successors, topo);
            return new TaskGraph(tasks, freeze(deps), dependents, successors,
                    new ArrayList<>(tasks.keySet()), topo, inferredEdges);
        }

        private void validateReferences() {
            for (Map.Entry<String, Set<String>> entry : hardDeps.entrySet()) {
                String name = entry.getKey();
                if (!tasks.containsKey(name)) {
                    throw new TaskGraphException(
                            "dependency declared for unknown task: " + name);
                }
                for (String dep : entry.getValue()) {
                    if (!tasks.containsKey(dep)) {
                        throw new TaskGraphException(
                                "task " + name + " depends on unknown task "
                                        + dep);
                    }
                    if (dep.equals(name)) {
                        throw new TaskGraphException(
                                "task depends on itself: " + name);
                    }
                }
            }
            for (Map.Entry<String, Set<String>> entry : orderEdges.entrySet()) {
                String name = entry.getKey();
                if (!tasks.containsKey(name)) {
                    throw new TaskGraphException(
                            "ordering declared for unknown task: " + name);
                }
                for (String after : entry.getValue()) {
                    if (!tasks.containsKey(after)) {
                        throw new TaskGraphException(
                                "task " + name + " ordered after unknown task "
                                        + after);
                    }
                    if (after.equals(name)) {
                        throw new TaskGraphException(
                                "task ordered after itself: " + name);
                    }
                }
            }
        }

        private boolean inferEdges(Map<String, Set<String>> deps) {
            PathTrie outputs = new PathTrie();
            for (TaskDefinition task : tasks.values()) {
                for (RootedPath output : task.outputs().paths()) {
                    outputs.insert(output, task.name());
                }
            }
            boolean added = false;
            for (TaskDefinition consumer : tasks.values()) {
                List<RootedPath> paths = new ArrayList<>();
                paths.addAll(consumer.inputs().filePaths());
                paths.addAll(consumer.inputs().dirPaths());
                for (RootedPath input : paths) {
                    for (String producer : outputs.producersCovering(input)) {
                        if (producer.equals(consumer.name())) {
                            continue;
                        }
                        Set<String> existing = deps.computeIfAbsent(
                                consumer.name(), key -> new LinkedHashSet<>());
                        if (existing.add(producer)) {
                            added = true;
                        }
                    }
                }
            }
            return added;
        }

        private Map<String, Set<String>> invert(Map<String, TaskDefinition> tasks,
                                                Map<String, Set<String>> deps) {
            Map<String, Set<String>> dependents = new LinkedHashMap<>();
            for (String name : tasks.keySet()) {
                dependents.put(name, new LinkedHashSet<>());
            }
            for (Map.Entry<String, Set<String>> entry : deps.entrySet()) {
                for (String dep : entry.getValue()) {
                    dependents.get(dep).add(entry.getKey());
                }
            }
            return dependents;
        }

        private Map<String, Set<String>> invertOrder(Map<String, TaskDefinition> tasks,
                                                     Map<String, Set<String>> orderEdges) {
            Map<String, Set<String>> successors = new LinkedHashMap<>();
            for (String name : tasks.keySet()) {
                successors.put(name, new LinkedHashSet<>());
            }
            for (Map.Entry<String, Set<String>> entry : orderEdges.entrySet()) {
                for (String after : entry.getValue()) {
                    successors.get(after).add(entry.getKey());
                }
            }
            return successors;
        }

        private List<String> kahnOrCycle(Map<String, TaskDefinition> tasks,
                                         Map<String, Set<String>> deps,
                                         Map<String, Set<String>> dependents) {
            Map<String, Integer> remaining = new LinkedHashMap<>();
            for (String name : tasks.keySet()) {
                Set<String> own = deps.get(name);
                remaining.put(name, own == null ? 0 : own.size());
            }
            Queue<String> ready = new ArrayDeque<>();
            for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
                if (entry.getValue() == 0) {
                    ready.add(entry.getKey());
                }
            }
            List<String> order = new ArrayList<>(tasks.size());
            while (!ready.isEmpty()) {
                String name = ready.poll();
                order.add(name);
                for (String dependent : dependents.get(name)) {
                    int left = remaining.merge(dependent, -1, Integer::sum);
                    if (left == 0) {
                        ready.add(dependent);
                    }
                }
            }
            if (order.size() < tasks.size()) {
                throw new TaskGraphException(
                        "dependency cycle: " + findCycle(remaining, deps));
            }
            return order;
        }

        private String findCycle(Map<String, Integer> remaining,
                                 Map<String, Set<String>> deps) {
            Set<String> inside = new LinkedHashSet<>();
            for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
                if (entry.getValue() > 0) {
                    inside.add(entry.getKey());
                }
            }
            String start = inside.stream().sorted().findFirst().orElseThrow();
            List<String> path = new ArrayList<>();
            Map<String, Integer> positions = new LinkedHashMap<>();
            String current = start;
            while (!positions.containsKey(current)) {
                positions.put(current, path.size());
                path.add(current);
                current = inside.stream()
                        .filter(deps.getOrDefault(current, Set.of())::contains)
                        .sorted().findFirst().orElse(start);
            }
            List<String> cycle = path.subList(positions.get(current), path.size());
            cycle.add(current);
            return String.join(" -> ", cycle);
        }

        private void validateOrderConstraints(Map<String, TaskDefinition> tasks,
                                              Map<String, Set<String>> deps,
                                              Map<String, Set<String>> successors,
                                              List<String> topo) {
            Map<String, Integer> indexes = new LinkedHashMap<>();
            for (int i = 0; i < topo.size(); i++) {
                indexes.put(topo.get(i), i);
            }
            Map<String, BitSet> reach = new LinkedHashMap<>();
            for (String name : topo) {
                BitSet mask = new BitSet(topo.size());
                Set<String> own = deps.get(name);
                if (own != null) {
                    for (String dep : own) {
                        mask.set(indexes.get(dep));
                        mask.or(reach.get(dep));
                    }
                }
                reach.put(name, mask);
            }
            for (Map.Entry<String, Set<String>> entry : successors.entrySet()) {
                String before = entry.getKey();
                for (String after : entry.getValue()) {
                    if (reach.get(before).get(indexes.get(after))) {
                        throw new TaskGraphException(
                                "ordering constraint contradicts dependencies: "
                                        + after + " must run after " + before
                                        + " but " + before + " requires " + after);
                    }
                }
            }
        }

        private Map<String, Set<String>> freeze(Map<String, Set<String>> deps) {
            Map<String, Set<String>> frozen = new LinkedHashMap<>();
            for (Map.Entry<String, Set<String>> entry : deps.entrySet()) {
                frozen.put(entry.getKey(), Collections.unmodifiableSet(
                        new LinkedHashSet<>(entry.getValue())));
            }
            return frozen;
        }
    }
}
