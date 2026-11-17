package jo.joide.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Poids de chemin critique restant : la priorité d'une tâche est la
 * durée de son plus lourd chemin vers une tâche terminale.
 */
class CriticalPathsTest {

    private static TaskDefinition named(String name, String type) {
        return new TestingTasks.RecordingTask(name, type,
                TaskInputs.builder().build(), TaskOutputs.builder().build(),
                ResourceClaim.LIGHT);
    }

    @Test
    void chainHeadOutweighsIsolatedTask() {
        // longHead (3 s) -> longTail (2 s) ; short (1 s) isolée
        TaskGraph graph = TaskGraph.builder()
                .register(named("longHead", "compile"))
                .register(named("longTail", "compile"))
                .register(named("short", "compile"))
                .dependsOn("longTail", "longHead")
                .build();
        Map<String, Long> durations = new HashMap<>();
        durations.put("longHead", 3000L);
        durations.put("longTail", 2000L);
        durations.put("short", 1000L);
        Map<String, Long> weights = CriticalPaths.weights(graph, durations,
                Map.of());
        assertEquals(5000L, weights.get("longHead"),
                "chain head carries the whole chain");
        assertEquals(2000L, weights.get("longTail"));
        assertEquals(1000L, weights.get("short"));
        assertTrue(weights.get("longHead") > weights.get("short"));
    }

    @Test
    void missingDurationsFallBackToTypeDefaults() {
        TaskGraph graph = TaskGraph.builder()
                .register(named("compileTask", "compile"))
                .register(named("unknownTask", "exotic"))
                .build();
        Map<String, Long> weights = CriticalPaths.weights(graph, Map.of(),
                Map.of("compile", 1500L));
        assertEquals(1500L, weights.get("compileTask"));
        assertEquals(500L, weights.get("unknownTask"),
                "unknown types fall back to the generic default");
    }

    @Test
    void diamondTakesHeaviestBranch() {
        // head -> (left: 1 s -> tail) et (right: 5 s -> tail)
        TaskGraph graph = TaskGraph.builder()
                .register(named("head", "compile"))
                .register(named("left", "compile"))
                .register(named("right", "compile"))
                .register(named("tail", "compile"))
                .dependsOn("left", "head")
                .dependsOn("right", "head")
                .dependsOn("tail", "left", "right")
                .build();
        Map<String, Long> durations = new HashMap<>();
        durations.put("head", 100L);
        durations.put("left", 1000L);
        durations.put("right", 5000L);
        durations.put("tail", 100L);
        Map<String, Long> weights = CriticalPaths.weights(graph, durations,
                Map.of());
        assertEquals(100L + 5000L + 100L, weights.get("head"),
                "head carries the heaviest branch");
    }
}
