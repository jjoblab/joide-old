package jo.joide.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Validation des données du graphe de modules :
 * gradle/module-graph.txt doit décrire exactement les arêtes autorisées
 * du graphe cible, être acyclique et strictement descendant. L'exécution
 * de la règle à la construction (plugin build-logic) est prouvée par
 * DependencyRuleKitTest ; ce test valide les DONNÉES.
 */
class ModuleGraphFileTest {

    /** Arêtes attendues, exactement celles du graphe cible. */
    private static final Map<String, Set<String>> EXPECTED = buildExpected();

    private static Map<String, Set<String>> buildExpected() {
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        expected.put("core-platform", new HashSet<>());
        expected.put("core-store", Set.of("core-platform"));
        expected.put("core-vfs", Set.of("core-platform", "core-store"));
        expected.put("core-model", Set.of("core-platform", "core-vfs"));
        expected.put("core-tasks",
                Set.of("core-model", "core-store", "core-vfs"));
        expected.put("core-deps", Set.of("core-platform", "core-store"));
        // V2 lot N (ADR-055) : le noyau agent, module pur —
        // core-platform et core-deps, jamais Android ni les briques.
        expected.put("agent", Set.of("core-platform", "core-deps"));
        expected.put("build-jvm", Set.of("core-tasks", "core-deps"));
        expected.put("build-android", Set.of("build-jvm", "core-deps"));
        expected.put("build-daemon", Set.of("build-android", "core-store"));
        expected.put("import-gradle", Set.of("core-model", "core-deps"));
        expected.put("tools-bench", Set.of("core-model"));
        expected.put("tools-test", new HashSet<>());
        // P8, ADR-045 : arête nouvelle explicite app-android ->
        // ide-integration (l'application hôte monte l'IDE derrière les
        // interfaces JoIDE — jamais les briques directement).
        // V2 lot O (ADR-059) : l'implémentation du port agent vit
        // dans ide-integration, au-dessus des briques ; core-store
        // est une arête de TEST (store journal, configuration jvmApi
        // — le vrai moteur de build tourne dans les tests).
        expected.put("ide-integration",
                Set.of("core-model", "build-daemon", "agent",
                        "core-store"));
        expected.put("app-android", Set.of("build-daemon", "ide-integration"));
        return expected;
    }

    @Test
    void graphFileMatchesPromptExactly() throws IOException {
        Map<String, Set<String>> graph = read();
        assertEquals(EXPECTED.keySet(), graph.keySet(),
                "declared modules must match the prompt module list");
        for (Map.Entry<String, Set<String>> entry : graph.entrySet()) {
            assertEquals(EXPECTED.get(entry.getKey()), entry.getValue(),
                    "dependencies of " + entry.getKey());
        }
    }

    @Test
    void graphIsAcyclicAndDescending() throws IOException {
        List<String> order = new ArrayList<>();
        Map<String, Set<String>> graph = read();
        for (String line : Files.readAllLines(
                Path.of("..", "gradle", "module-graph.txt"),
                StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                order.add(trimmed.substring(0, trimmed.indexOf("->")).trim());
            }
        }
        // Descendant : toute dépendance est déclarée AVANT son module.
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            index.put(order.get(i), i);
        }
        for (Map.Entry<String, Set<String>> entry : graph.entrySet()) {
            for (String dep : entry.getValue()) {
                assertTrue(index.get(dep) < index.get(entry.getKey()),
                        entry.getKey() + " -> " + dep
                                + " must be declared in descending order");
            }
        }
        // Kahn : acyclicité.
        Map<String, Integer> remaining = new HashMap<>();
        Map<String, List<String>> reverse = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : graph.entrySet()) {
            remaining.put(entry.getKey(), entry.getValue().size());
            for (String dep : entry.getValue()) {
                reverse.computeIfAbsent(dep, k -> new ArrayList<>())
                        .add(entry.getKey());
            }
        }
        Deque<String> ready = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }
        Set<String> visited = new HashSet<>();
        while (!ready.isEmpty()) {
            String name = ready.poll();
            if (!visited.add(name)) {
                continue;
            }
            for (String dependent : reverse.getOrDefault(
                    name, new ArrayList<>())) {
                if (remaining.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }
        assertEquals(graph.size(), visited.size(), "graph must be acyclic");
    }

    private Map<String, Set<String>> read() throws IOException {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        for (String line : Files.readAllLines(
                Path.of("..", "gradle", "module-graph.txt"),
                StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int arrow = trimmed.indexOf("->");
            String name = trimmed.substring(0, arrow).trim();
            Set<String> deps = new HashSet<>();
            for (String dep : trimmed.substring(arrow + 2).trim()
                    .split("\\s+")) {
                if (!dep.isEmpty()) {
                    deps.add(dep);
                }
            }
            graph.put(name, deps);
        }
        return graph;
    }
}
