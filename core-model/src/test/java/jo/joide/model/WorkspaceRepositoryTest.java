package jo.joide.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrôle du dépôt : écriture d'éditions, relecture depuis le disque,
 * publication aux abonnés et allers-retours d'édition.
 */
class WorkspaceRepositoryTest {

    private static void seedWorkspace(Path root) throws IOException {
        Files.writeString(root.resolve("joide.toml"), """
                schema = "joide.workspace/1"
                name = "essai"

                [modules]
                include = ["base"]
                """);
        Files.createDirectories(root.resolve("base"));
        Files.writeString(root.resolve("base/joide-module.toml"), """
                schema = "joide.module/1"
                name = "base"
                type = "jvm-library"
                """);
    }

    @Test
    void loadPublishesToListeners(@TempDir Path root) throws IOException {
        seedWorkspace(root);
        WorkspaceRepository repository = new WorkspaceRepository(root);
        AtomicReference<Workspace> seen = new AtomicReference<>();
        repository.addListener(seen::set);
        WorkspaceLoad load = repository.load();
        assertTrue(load.isUsable());
        assertNotNull(seen.get(), "l'espace charge est publie");
        assertEquals(1, seen.get().allModules().size());
    }

    @Test
    void saveNewModuleThenReload(@TempDir Path root) throws IOException {
        seedWorkspace(root);
        WorkspaceRepository repository = new WorkspaceRepository(root);
        Workspace current = repository.load().workspace().orElseThrow();

        Module nouveau = new Module(":nouveau", "nouveau", ModuleType.JVM_LIBRARY,
                Map.of("main", new SourceSet("main",
                        List.of(new ContentRoot("src/main/java", ContentRole.SOURCE)))),
                List.of(DependencyRef.toModule(":base",
                        DependencyScope.API, Set.of())),
                Map.of());
        repository.save(WorkspaceEdit.withModule(current, nouveau));

        WorkspaceLoad reloaded = repository.load();
        assertTrue(reloaded.isUsable(), () -> reloaded.problems().toString());
        Workspace workspace = reloaded.workspace().orElseThrow();
        assertEquals(2, workspace.allModules().size());
        Module fromDisk = workspace.module(":nouveau");
        assertNotNull(fromDisk);
        assertEquals(nouveau.contentId(), fromDisk.contentId(),
                "l'aller-retour d'ecriture preserve la definition");
        assertTrue(Files.isRegularFile(root.resolve("nouveau/joide-module.toml")));
    }

    @Test
    void removeModuleUpdatesWorkspaceFile(@TempDir Path root) throws IOException {
        seedWorkspace(root);
        WorkspaceRepository repository = new WorkspaceRepository(root);
        Workspace current = repository.load().workspace().orElseThrow();
        repository.save(WorkspaceEdit.withoutModule(current, ":base"));
        WorkspaceLoad reloaded = repository.load();
        assertTrue(reloaded.isUsable());
        assertNull(reloaded.workspace().orElseThrow().module(":base"));
        // le repertoire du module n'est pas detruit : seul le modele change
        assertTrue(Files.isDirectory(root.resolve("base")));
    }

    @Test
    void saveLeavesNoTemporaryFiles(@TempDir Path root) throws IOException {
        seedWorkspace(root);
        WorkspaceRepository repository = new WorkspaceRepository(root);
        Workspace current = repository.load().workspace().orElseThrow();
        Module extra = new Module(":extra", "extra", ModuleType.JVM_LIBRARY,
                Map.of(), List.of(), Map.of());
        repository.save(WorkspaceEdit.withModule(current, extra));
        try (var walk = Files.walk(root)) {
            List<Path> leftovers = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().contains("joide-batch")
                            || p.getFileName().toString().contains("joide-tmp"))
                    .toList();
            assertEquals(List.of(), leftovers, "aucun temporaire ne reste");
        }
    }
}
