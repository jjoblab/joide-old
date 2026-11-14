package jo.joide.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Propriétés du graphe de modules planifié : couches
 * strictement descendantes, acyclicité (Kahn indépendant), au moins une
 * dépendance par module au-dessus de la base, hubs concentrateurs.
 */
class ModuleGraphPlannerTest {

    @Test
    void graphIsDescendingAcyclicAndConnected() {
        for (TierSpec tier : TierSpec.values()) {
            List<ModuleGraphPlanner.PlannedModule> modules =
                    ModuleGraphPlanner.plan(tier);
            assertEquals(tier.modules, modules.size(),
                    "module count for tier " + tier);
            Map<String, Integer> layer = new HashMap<>();
            for (ModuleGraphPlanner.PlannedModule module : modules) {
                layer.put(module.name, module.layer);
            }
            for (ModuleGraphPlanner.PlannedModule module : modules) {
                if (module.layer == 0) {
                    continue;
                }
                assertTrue(!module.dependencies.isEmpty(),
                        module.name + " above base must depend on something");
                for (String dep : module.dependencies) {
                    assertTrue(layer.get(dep) < module.layer,
                            module.name + " -> " + dep
                                    + " must point to a lower layer");
                }
            }
            // Kahn indépendant : le graphe doit être acyclique.
            assertEquals(modules.size(), kahnVisited(modules),
                    "graph must be acyclic for tier " + tier);
        }
    }

    /** Kahn minimal : nombre de sommets visitables (taille si acyclique). */
    private static int kahnVisited(
            List<ModuleGraphPlanner.PlannedModule> modules) {
        Map<String, Integer> remaining = new HashMap<>();
        Map<String, List<String>> reverse = new HashMap<>();
        for (ModuleGraphPlanner.PlannedModule module : modules) {
            remaining.put(module.name, module.dependencies.size());
            for (String dep : module.dependencies) {
                reverse.computeIfAbsent(dep, k -> new ArrayList<>())
                        .add(module.name);
            }
        }
        Deque<String> ready = new ArrayDeque<>();
        for (ModuleGraphPlanner.PlannedModule module : modules) {
            if (module.dependencies.isEmpty()) {
                ready.add(module.name);
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
        return visited.size();
    }

    @Test
    void hubsConcentrateIncomingEdges() {
        for (TierSpec tier : TierSpec.values()) {
            List<ModuleGraphPlanner.PlannedModule> modules =
                    ModuleGraphPlanner.plan(tier);
            Map<String, Integer> inDegree = new HashMap<>();
            for (ModuleGraphPlanner.PlannedModule module : modules) {
                for (String dep : module.dependencies) {
                    inDegree.merge(dep, 1, Integer::sum);
                }
            }
            double overall = 0;
            for (int degree : inDegree.values()) {
                overall += degree;
            }
            overall /= modules.size();
            double hubTotal = 0;
            int hubCount = 0;
            for (ModuleGraphPlanner.PlannedModule module : modules) {
                if (module.hub) {
                    hubTotal += inDegree.getOrDefault(module.name, 0);
                    hubCount++;
                }
            }
            if (hubCount > 0) {
                double hubAverage = hubTotal / hubCount;
                assertTrue(hubAverage > overall,
                        "hubs must concentrate in-degree for tier " + tier
                                + " (hub avg " + hubAverage
                                + ", overall avg " + overall + ")");
            }
        }
    }

    @Test
    void generatorWritesExactBudgetAndExpectedStructure() throws IOException {
        Path out = Files.createTempDirectory("joide-planner");
        try {
            ProjectGenerator.Stats stats =
                    ProjectGenerator.generate(TierSpec.S, out);
            assertEquals(TierSpec.S.totalFiles, stats.files);
            assertEquals(TierSpec.S.totalFiles,
                    stats.javaFiles + stats.kotlinFiles + stats.otherFiles);
            assertTrue(Files.isRegularFile(out.resolve("joide.toml")),
                    "workspace TOML must exist at root");
            assertTrue(Files.isRegularFile(
                    out.resolve("m000/src/main/AndroidManifest.xml")),
                    "module m000 manifest must exist");
        } finally {
            BenchMain.deleteRecursively(out);
        }
    }
}
