package jo.joide.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import jo.joide.platform.RootName;
import jo.joide.platform.RootedPath;

/**
 * Validation du graphe de tâches : références, cycles avec chemin
 * exact, contraintes d'ordre contraires, inférence par préfixe.
 */
class TaskGraphTest {

    private static TaskDefinition named(String name) {
        return new TestingTasks.RecordingTask(name, "noop",
                TaskInputs.builder().build(), TaskOutputs.builder().build(),
                ResourceClaim.LIGHT);
    }

    private static TaskDefinition io(String name, String input, String output) {
        RootedPath in = RootedPath.of(RootName.PROJECT, input);
        RootedPath out = RootedPath.of(RootName.OUT, output);
        return new TestingTasks.RecordingTask(name, "noop",
                TaskInputs.builder().file(in).build(),
                TaskOutputs.builder().file(out).build(), ResourceClaim.LIGHT);
    }

    private static TaskDefinition produces(String name, RootedPath input,
                                           RootedPath output) {
        return new TestingTasks.RecordingTask(name, "noop",
                TaskInputs.builder().file(input).build(),
                TaskOutputs.builder().file(output).build(), ResourceClaim.LIGHT);
    }

    @Test
    void cycleIsRejectedWithExactPath() {
        TaskGraphException failure = assertThrows(TaskGraphException.class,
                () -> TaskGraph.builder()
                        .register(named("a"))
                        .register(named("b"))
                        .register(named("c"))
                        .dependsOn("a", "b")
                        .dependsOn("b", "c")
                        .dependsOn("c", "a")
                        .build());
        assertTrue(failure.getMessage().contains("cycle"),
                "message: " + failure.getMessage());
        assertTrue(failure.getMessage().contains(" -> "),
                "cycle path: " + failure.getMessage());
    }

    @Test
    void unknownReferenceIsRejected() {
        assertThrows(TaskGraphException.class, () -> TaskGraph.builder()
                .register(named("a"))
                .dependsOn("a", "ghost")
                .build());
        assertThrows(TaskGraphException.class, () -> TaskGraph.builder()
                .register(named("a"))
                .mustRunAfter("a", "ghost")
                .build());
        assertThrows(TaskGraphException.class, () -> TaskGraph.builder()
                .dependsOn("ghost", "a")
                .register(named("a"))
                .build());
    }

    @Test
    void selfDependencyIsRejected() {
        assertThrows(TaskGraphException.class, () -> TaskGraph.builder()
                .register(named("a"))
                .dependsOn("a", "a")
                .build());
    }

    @Test
    void duplicateNameIsRejected() {
        assertThrows(TaskGraphException.class, () -> TaskGraph.builder()
                .register(named("a"))
                .register(named("a"))
                .build());
    }

    @Test
    void orderingContradictingDependenciesIsRejected() {
        // b doit suivre a, mais a dépend de b : interblocage
        assertThrows(TaskGraphException.class, () -> TaskGraph.builder()
                .register(named("a"))
                .register(named("b"))
                .dependsOn("a", "b")
                .mustRunAfter("b", "a")
                .build());
    }

    @Test
    void inferenceByPrefixAddsDependency() {
        // produce écrit dans OUT/lib ; consume lit OUT/lib/x.kt : le
        // préfixe crée l'arête produce -> consume
        RootedPath produced = RootedPath.of(RootName.OUT, "lib/x.kt");
        TaskGraph inferred = TaskGraph.builder()
                .register(produces("produce", RootedPath.of(RootName.PROJECT,
                        "src/a.txt"), produced))
                .register(produces("consume", produced,
                        RootedPath.of(RootName.OUT, "final.txt")))
                .inferByPath()
                .build();
        assertTrue(inferred.hardDepsOf("consume").contains("produce"),
                "inference must add produce -> consume, got "
                        + inferred.hardDepsOf("consume"));

        TaskGraph explicit = TaskGraph.builder()
                .register(produces("produce", RootedPath.of(RootName.PROJECT,
                        "src/a.txt"), produced))
                .register(produces("consume", produced,
                        RootedPath.of(RootName.OUT, "final.txt")))
                .build();
        assertTrue(explicit.hardDepsOf("consume").isEmpty(),
                "without inference, no dependency");
    }

    @Test
    void inferenceDoesNotDuplicateExplicitEdges() {
        RootedPath produced = RootedPath.of(RootName.OUT, "lib/x.kt");
        TaskGraph graph = TaskGraph.builder()
                .register(produces("produce", RootedPath.of(RootName.PROJECT,
                        "src/a.txt"), produced))
                .register(produces("consume", produced,
                        RootedPath.of(RootName.OUT, "final.txt")))
                .dependsOn("consume", "produce")
                .inferByPath()
                .build();
        assertEquals(Set.of("produce"), graph.hardDepsOf("consume"));
    }

    @Test
    void inferenceCycleIsRejected() {
        // a produit ce que b lit, b produit ce que a lit : cycle inféré
        RootedPath aProduces = RootedPath.of(RootName.OUT, "a/x.txt");
        RootedPath bProduces = RootedPath.of(RootName.OUT, "b/x.txt");
        assertThrows(TaskGraphException.class, () -> TaskGraph.builder()
                .register(produces("a", bProduces, aProduces))
                .register(produces("b", aProduces, bProduces))
                .inferByPath()
                .build());
    }

    @Test
    void topologicalOrderRespectsDependencies() {
        TaskGraph graph = TaskGraph.builder()
                .register(named("leaf1"))
                .register(named("leaf2"))
                .register(named("middle"))
                .dependsOn("middle", "leaf1", "leaf2")
                .register(named("top"))
                .dependsOn("top", "middle")
                .build();
        List<String> order = graph.topologicalOrder();
        assertTrue(order.indexOf("leaf1") < order.indexOf("middle"));
        assertTrue(order.indexOf("leaf2") < order.indexOf("middle"));
        assertTrue(order.indexOf("middle") < order.indexOf("top"));
    }

    @Test
    void orderEdgesDoNotCreateDependencies() {
        TaskGraph graph = TaskGraph.builder()
                .register(named("a"))
                .register(named("b"))
                .mustRunAfter("b", "a")
                .build();
        assertTrue(graph.hardDepsOf("b").isEmpty());
        assertEquals(Set.of("b"), graph.orderSuccessorsOf("a"));
    }
}
