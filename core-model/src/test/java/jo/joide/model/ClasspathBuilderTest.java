package jo.joide.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Contrôle de l'assemblage de classpath : ordre déterministe,
 * déduplication, promotion de portée, règles de conflit de versions et
 * identifiant de contenu stable.
 */
class ClasspathBuilderTest {

    private static Module module(String path, DependencyRef... deps) {
        return new Module(path, path.substring(1), ModuleType.JVM_LIBRARY,
                Map.of(), List.of(deps), Map.of());
    }

    private static DependencyRef onModule(String target, DependencyScope scope) {
        return DependencyRef.toModule(target, scope, Set.of());
    }

    private static DependencyRef external(String coordinates, DependencyScope scope) {
        return DependencyRef.external(coordinates, scope, Set.of());
    }

    @Test
    void directAndTransitiveApiDependencies() {
        Workspace workspace = new Workspace("w", Map.of("main", new Project("main",
                Map.of(
                        ":base", module(":base"),
                        ":milieu", module(":milieu",
                                onModule(":base", DependencyScope.API)),
                        ":app", module(":app",
                                onModule(":milieu", DependencyScope.API))))),
                Map.of());
        ClasspathBuilder builder = new ClasspathBuilder(workspace,
                ModuleTopology.of(workspace), ClasspathBuilder.ConflictRule.NEWEST_WINS);
        Classpath classpath = builder.build(workspace.module(":app"));
        assertEquals(List.of(":base", ":milieu"),
                classpath.entries().stream()
                        .map(ClasspathEntry::modulePath).toList(),
                "l'ordre suit les niveaux topologiques");
    }

    @Test
    void implementationIsNotTransitive() {
        Workspace workspace = new Workspace("w", Map.of("main", new Project("main",
                Map.of(
                        ":base", module(":base"),
                        ":milieu", module(":milieu",
                                onModule(":base", DependencyScope.IMPLEMENTATION)),
                        ":app", module(":app",
                                onModule(":milieu", DependencyScope.API))))),
                Map.of());
        ClasspathBuilder builder = new ClasspathBuilder(workspace,
                ModuleTopology.of(workspace), ClasspathBuilder.ConflictRule.NEWEST_WINS);
        Classpath classpath = builder.build(workspace.module(":app"));
        assertEquals(List.of(":milieu"),
                classpath.entries().stream()
                        .map(ClasspathEntry::modulePath).toList(),
                "une dependance d'implementation ne fuit pas vers l'aval");
    }

    @Test
    void duplicateTargetsAreDeduplicatedWithStrongestScope() {
        Workspace workspace = new Workspace("w", Map.of("main", new Project("main",
                Map.of(
                        ":base", module(":base"),
                        ":app", module(":app",
                                onModule(":base", DependencyScope.IMPLEMENTATION),
                                external("g:a:1.0", DependencyScope.IMPLEMENTATION),
                                external("g:a:2.0", DependencyScope.API),
                                onModule(":base", DependencyScope.API))))),
                Map.of());
        ClasspathBuilder builder = new ClasspathBuilder(workspace,
                ModuleTopology.of(workspace), ClasspathBuilder.ConflictRule.NEWEST_WINS);
        Classpath classpath = builder.build(workspace.module(":app"));
        assertEquals(2, classpath.size(), "module + externe, sans doublon");
        ClasspathEntry moduleEntry = classpath.entries().stream()
                .filter(e -> e.modulePath() != null).findFirst().orElseThrow();
        assertEquals(DependencyScope.API, moduleEntry.scope(),
                "la portee la plus forte gagne");
        ClasspathEntry externalEntry = classpath.entries().stream()
                .filter(e -> e.coordinates() != null).findFirst().orElseThrow();
        assertEquals("2.0", externalEntry.version(), "la plus recente gagne");
    }

    @Test
    void strictlyRuleRejectsAnyConflict() {
        Workspace workspace = new Workspace("w", Map.of("main", new Project("main",
                Map.of(
                        ":app", module(":app",
                                external("g:a:1.0", DependencyScope.API),
                                external("g:a:1.1", DependencyScope.IMPLEMENTATION))))),
                Map.of());
        ClasspathBuilder builder = new ClasspathBuilder(workspace,
                ModuleTopology.of(workspace), ClasspathBuilder.ConflictRule.STRICTLY);
        ModelException failure = assertThrows(ModelException.class,
                () -> builder.build(workspace.module(":app")));
        assertTrue(failure.getMessage().contains("g:a"));
    }

    @Test
    void contentIdIsStableAndRelocatable() {
        Workspace workspace = new Workspace("w", Map.of("main", new Project("main",
                Map.of(
                        ":base", module(":base"),
                        ":app", module(":app",
                                onModule(":base", DependencyScope.API),
                                external("g:a:1.0", DependencyScope.API))))),
                Map.of());
        ClasspathBuilder builder = new ClasspathBuilder(workspace,
                ModuleTopology.of(workspace), ClasspathBuilder.ConflictRule.NEWEST_WINS);
        Classpath first = builder.build(workspace.module(":app"));
        Classpath second = builder.build(workspace.module(":app"));
        assertEquals(first.contentId(), second.contentId(),
                "meme definition, meme identifiant");
        assertEquals(first.contentId().toHex().length(), 32);
    }

    @Test
    void testAndRuntimeOnlyScopesAreExcluded() {
        Workspace workspace = new Workspace("w", Map.of("main", new Project("main",
                Map.of(
                        ":base", module(":base"),
                        ":app", module(":app",
                                onModule(":base", DependencyScope.TEST_IMPLEMENTATION),
                                onModule(":base", DependencyScope.COMPILE_ONLY))))),
                Map.of());
        ClasspathBuilder builder = new ClasspathBuilder(workspace,
                ModuleTopology.of(workspace), ClasspathBuilder.ConflictRule.NEWEST_WINS);
        Classpath classpath = builder.build(workspace.module(":app"));
        assertTrue(classpath.isEmpty(),
                "test et compile-only ne font pas partie du classpath principal");
    }

    @Test
    void versionOrderingIsNumericNotLexicographic() {
        assertTrue(ClasspathBuilder.compareVersions("1.10.0", "1.9.0") > 0);
        assertTrue(ClasspathBuilder.compareVersions("2.0.0", "10.0.0") < 0);
        assertTrue(ClasspathBuilder.compareVersions("1.0", "1.0.1") < 0);
        assertTrue(ClasspathBuilder.compareVersions("1.0.0", "1.0.0") == 0);
    }
}
