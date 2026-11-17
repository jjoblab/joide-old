package jo.joide.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Contrôle de la topologie des modules : niveaux, dépendances
 * inversées et détection de cycle avec chemin reconstruit.
 */
class ModuleTopologyTest {

    private static Module module(String path, String... deps) {
        java.util.ArrayList<DependencyRef> dependencies = new java.util.ArrayList<>();
        for (String dep : deps) {
            dependencies.add(DependencyRef.toModule(dep,
                    DependencyScope.IMPLEMENTATION, Set.of()));
        }
        return new Module(path, path.substring(1), ModuleType.JVM_LIBRARY,
                Map.of(), dependencies, Map.of());
    }

    @Test
    void levelsAndReverseDependencies() {
        Workspace workspace = new Workspace("w", Map.of("main", new Project("main",
                Map.of(
                        ":base", module(":base"),
                        ":milieu", module(":milieu", ":base"),
                        ":sommet", module(":sommet", ":milieu", ":base")))),
                Map.of());
        ModuleTopology topology = ModuleTopology.of(workspace);
        assertEquals(0, topology.levelOf(":base"));
        assertEquals(1, topology.levelOf(":milieu"));
        assertEquals(2, topology.levelOf(":sommet"));
        assertEquals(List.of(List.of(":base"), List.of(":milieu"), List.of(":sommet")),
                topology.levelOrder());
        assertEquals(Set.of(":milieu", ":sommet"), topology.dependentsOf(":base"));
        assertEquals(Set.of(":sommet"), topology.dependentsOf(":milieu"));
    }

    @Test
    void cycleIsDetectedWithPath() {
        Workspace workspace = new Workspace("w", Map.of("main", new Project("main",
                Map.of(
                        ":a", module(":a", ":b"),
                        ":b", module(":b", ":c"),
                        ":c", module(":c", ":a")))),
                Map.of());
        ModelException failure = assertThrows(ModelException.class,
                () -> ModuleTopology.of(workspace));
        assertTrue(failure.getMessage().contains("cycle"),
                "message utile : " + failure.getMessage());
        for (String name : new String[] {":a", ":b", ":c"}) {
            assertTrue(failure.getMessage().contains(name),
                    "le cycle cite " + name + " : " + failure.getMessage());
        }
    }

    @Test
    void unknownDependencyIsExplicit() {
        Workspace workspace = new Workspace("w", Map.of("main", new Project("main",
                Map.of(":seul", module(":seul", ":fantome")))),
                Map.of());
        assertThrows(ModelException.class, () -> ModuleTopology.of(workspace));
    }

    @Test
    void deterministicOrderRegardlessOfDeclaration() {
        Workspace first = new Workspace("w", Map.of("main", new Project("main",
                Map.of(":x", module(":x", ":y"), ":y", module(":y")))), Map.of());
        Workspace second = new Workspace("w", Map.of("main", new Project("main",
                Map.of(":y", module(":y"), ":x", module(":x", ":y")))), Map.of());
        assertEquals(ModuleTopology.of(first).levelOrder(),
                ModuleTopology.of(second).levelOrder());
    }
}
