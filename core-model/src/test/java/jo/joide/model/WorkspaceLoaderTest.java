package jo.joide.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import jo.joide.platform.Hash128;
import jo.joide.platform.Problem;
import jo.joide.platform.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrôle du chargement tolérant : un module corrompu est signalé, le
 * reste charge ; les schémas, portées et rôles inconnus sont
 * diagnostiqués sans jamais bloquer les autres modules.
 */
class WorkspaceLoaderTest {

    private static void writeWorkspace(Path root, String... includes)
            throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("schema = \"joide.workspace/1\"\nname = \"bench\"\n\n");
        out.append("[android]\nmin-sdk = 28\n\n");
        out.append("[modules]\ninclude = [");
        for (int i = 0; i < includes.length; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append('"').append(includes[i]).append('"');
        }
        out.append("]\n");
        Files.writeString(root.resolve("joide.toml"), out);
    }

    private static void writeModule(Path root, String name, String content)
            throws IOException {
        Path dir = root.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("joide-module.toml"), content);
    }

    @Test
    void loadsACompleteWorkspace(@TempDir Path root) throws IOException {
        writeWorkspace(root, "core", "app");
        writeModule(root, "core", """
                schema = "joide.module/1"
                name = "core"
                type = "jvm-library"

                [[sources]]
                name = "main"
                source = ["src/main/java"]
                resources = ["src/main/resources"]

                [[dependencies]]
                coordinates = "com.google.guava:guava:33.0.0-jre"
                scope = "api"
                """);
        writeModule(root, "app", """
                schema = "joide.module/1"
                name = "app"
                type = "android-application"

                [[sources]]
                name = "main"
                source = ["src/main/java"]

                [facets.android]
                manifest = "src/main/AndroidManifest.xml"

                [[dependencies]]
                path = ":core"
                scope = "implementation"
                variants = ["debug"]
                """);

        WorkspaceLoad load = WorkspaceLoader.load(root);

        assertTrue(load.isUsable(), () -> load.problems().toString());
        Workspace workspace = load.workspace().orElseThrow();
        assertEquals("bench", workspace.name());
        assertEquals(2, workspace.allModules().size());
        Module app = workspace.module(":app");
        assertNotNull(app);
        assertEquals("android-application", app.type().id());
        assertEquals(1, app.sourceSets().size());
        assertEquals(1, app.sourceSets().get("main").roots().size());
        assertEquals("src/main/java", app.sourceSets().get("main").roots().get(0).path());
        assertEquals("src/main/AndroidManifest.xml",
                app.facets().get("android").entry("manifest"));
        assertEquals(1, app.dependencies().size());
        assertEquals(":core", app.dependencies().get(0).modulePath());
        assertEquals(DependencyScope.IMPLEMENTATION, app.dependencies().get(0).scope());
        Module core = workspace.module(":core");
        assertEquals("com.google.guava:guava:33.0.0-jre",
                core.dependencies().get(0).coordinates());
        assertEquals("28", workspace.setting("android.min-sdk").orElse(null));
    }

    @Test
    void corruptedModuleIsReportedOthersLoad(@TempDir Path root) throws IOException {
        writeWorkspace(root, "sain", "casse", "absent");
        writeModule(root, "sain", """
                schema = "joide.module/1"
                name = "sain"
                type = "jvm-library"
                """);
        // module corrompu : TOML cassé (chaine non fermee + egale manquant)
        writeModule(root, "casse", "name = \"casse\nschema = \"joide.module/1\"\n");

        WorkspaceLoad load = WorkspaceLoader.load(root);

        assertTrue(load.workspace().isPresent(), "le workspace charge quand meme");
        Map<String, Module> modules = load.workspace().orElseThrow().allModules();
        assertEquals(1, modules.size(), "seul le module sain est charge");
        assertNotNull(modules.get(":sain"));
        List<Problem> errors = load.errors();
        assertFalse(errors.isEmpty(), "les modules casses sont signales");
        boolean casseSignale = errors.stream()
                .anyMatch(p -> p.message().contains("casse"));
        assertTrue(casseSignale, "le module casse est nomme dans les problemes");
        boolean absentSignale = errors.stream()
                .anyMatch(p -> p.message().contains("absent"));
        assertTrue(absentSignale, "le module absent est signale");
    }

    @Test
    void neverRewritesAnUnreadableFile(@TempDir Path root) throws IOException {
        writeWorkspace(root, "casse");
        Path moduleFile = root.resolve("casse/joide-module.toml");
        writeModule(root, "casse", "name = \"casse\n");
        String before = Files.readString(moduleFile);

        WorkspaceLoader.load(root);

        assertEquals(before, Files.readString(moduleFile),
                "un fichier illisible n'est jamais reecrit");
    }

    @Test
    void missingWorkspaceFileFailsCleanly(@TempDir Path root) {
        WorkspaceLoad load = WorkspaceLoader.load(root);
        assertTrue(load.workspace().isEmpty());
        assertEquals(1, load.errors().size());
    }

    @Test
    void unknownSchemaIsRejected(@TempDir Path root) throws IOException {
        writeWorkspace(root);
        Files.writeString(root.resolve("joide.toml"),
                "schema = \"joide.workspace/99\"\nname = \"x\"\n");
        WorkspaceLoad load = WorkspaceLoader.load(root);
        assertTrue(load.workspace().isEmpty());
        assertTrue(load.errors().size() == 1);
    }

    @Test
    void unknownKeysAndRolesAreInfoNotErrors(@TempDir Path root) throws IOException {
        writeWorkspace(root, "seul");
        writeModule(root, "seul", """
                schema = "joide.module/1"
                name = "seul"
                type = "jvm-library"
                mystere = "cle inconnue"

                [[sources]]
                name = "main"
                source = ["src/main/java"]
                RoleMajuscule = ["quelque/part"]
                """);
        WorkspaceLoad load = WorkspaceLoader.load(root);
        assertTrue(load.workspace().isPresent());
        assertTrue(load.errors().isEmpty(), () -> load.problems().toString());
        assertTrue(load.problems().stream().anyMatch(p -> p.severity() == Severity.INFO),
                "les cles inconnues sont des informations, pas des erreurs");
    }

    @Test
    void invalidScopeDropsDependencyKeepsModule(@TempDir Path root) throws IOException {
        writeWorkspace(root, "ok", "portee-folle");
        writeModule(root, "ok", "schema = \"joide.module/1\"\nname = \"ok\"\n"
                + "type = \"jvm-library\"\n");
        writeModule(root, "portee-folle", """
                schema = "joide.module/1"
                name = "portee-folle"
                type = "jvm-library"

                [[dependencies]]
                path = ":ok"
                scope = "magique"
                """);
        WorkspaceLoad load = WorkspaceLoader.load(root);
        // la dépendance invalide est écartée et signalée ; le module
        // lui-même reste utilisable avec ses autres dépendances
        assertEquals(2, load.workspace().orElseThrow().allModules().size());
        assertTrue(load.errors().stream()
                .anyMatch(p -> p.message().contains("portee")));
        assertEquals(0,
                load.workspace().orElseThrow().module(":portee-folle")
                        .dependencies().size());
    }

    @Test
    void moduleContentIdIsRelocatable(@TempDir Path root) throws IOException {
        writeWorkspace(root, "a", "b");
        writeModule(root, "a",
                "schema = \"joide.module/1\"\nname = \"a\"\ntype = \"jvm-library\"\n"
                        + "[[dependencies]]\npath = \":b\"\nscope = \"api\"\n");
        writeModule(root, "b",
                "schema = \"joide.module/1\"\nname = \"b\"\ntype = \"jvm-library\"\n");

        Workspace first = WorkspaceLoader.load(root).workspace().orElseThrow();
        Hash128 before = first.module(":a").contentId();

        // copie du projet a un autre endroit : meme definition, meme empreinte
        Path copy = Files.createDirectory(root.resolve("copie"));
        try (var walk = Files.walk(root)) {
            for (Path source : walk.filter(Files::isRegularFile).toList()) {
                if (source.startsWith(copy)) {
                    continue;
                }
                Path target = copy.resolve(root.relativize(source).toString());
                Files.createDirectories(target.getParent());
                Files.copy(source, target);
            }
        }
        Workspace second = WorkspaceLoader.load(copy).workspace().orElseThrow();
        assertEquals(before, second.module(":a").contentId(),
                "l'identifiant de contenu ne depend pas de l'emplacement");
    }
}
