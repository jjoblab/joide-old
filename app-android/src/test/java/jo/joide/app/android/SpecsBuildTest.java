package jo.joide.app.android;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.ide.sync.ExternalResolution;
import jo.joide.model.Workspace;
import jo.joide.model.WorkspaceLoad;
import jo.joide.model.WorkspaceLoader;

/**
 * Constitution des specs de build depuis un VRAI workspace chargé du
 * disque (joide.toml) : modules JVM, sources relatives à la racine,
 * jars des externes résolus — et les refus honnêtes (modules Android,
 * dépendances non résolues, AAR).
 */
class SpecsBuildTest {

    @TempDir
    Path temp;

    private Path projetNatif() throws Exception {
        Path racine = Files.createDirectories(temp.resolve("projet"));
        Files.writeString(racine.resolve("joide.toml"), """
                schema = "joide.workspace/1"

                name = "essai"

                [modules]
                include = ["app", "core"]
                """);
        Files.createDirectories(
                racine.resolve("app/src/main/java/demo"));
        Files.writeString(
                racine.resolve("app/joide-module.toml"), """
                        schema = "joide.module/1"
                        name = "app"
                        type = "jvm-library"

                        [[sources]]
                        name = "main"
                        source = ["src/main/java"]

                        [[dependencies]]
                        coordinates = "com.example:lib:1.0"
                        """);
        Files.createDirectories(
                racine.resolve("core/src/main/java/demo"));
        Files.writeString(
                racine.resolve("core/joide-module.toml"), """
                        schema = "joide.module/1"
                        name = "core"
                        type = "jvm-library"

                        [[sources]]
                        name = "main"
                        source = ["src/main/java"]

                        [[dependencies]]
                        path = ":app"
                        """);
        return racine;
    }

    private static ExternalResolution resolutionAvecJar(Path jar) {
        return new ExternalResolution.Builder()
                .add("com.example:lib", jar, false, null)
                .build();
    }

    @Test
    void modulesJvmSourcesRelativesEtJarExterne() throws Exception {
        Path racine = projetNatif();
        WorkspaceLoad charge = WorkspaceLoader.load(racine);
        assertTrue(charge.isUsable(), () -> charge.problems().toString());
        Workspace workspace = charge.workspace().orElseThrow();

        Path jar = temp.resolve("lib-1.0.jar");
        Files.write(jar, new byte[]{0x50, 0x4b});
        SpecsBuild.Constitution constitution = SpecsBuild.jvm(workspace,
                resolutionAvecJar(jar), racine, temp.resolve("out"),
                temp.resolve("m2"));

        assertTrue(constitution.constructible(),
                () -> constitution.ecarts.toString());
        assertNotNull(constitution.spec);
        assertEquals(2, constitution.spec.modules.size());
        jo.joide.build.daemon.protocol.BuildSpec.ModuleSpec app = constitution.spec.modules.get(0);
        assertEquals(":app", app.name);
        assertEquals(List.of("app/src/main/java"), app.sources);
        assertEquals(List.of(jar.toString()), app.externalJars);
        jo.joide.build.daemon.protocol.BuildSpec.ModuleSpec core = constitution.spec.modules.get(1);
        assertEquals(List.of(":app"), core.moduleDependencies);
        assertEquals(SpecsBuild.NIVEAU_SOURCE,
                constitution.spec.sourceLevel);
        assertEquals(SpecsBuild.TAS_PAR_DEFAUT_MIO,
                constitution.spec.heapMb);
    }

    @Test
    void dependanceNonResolueEtAarRapportes() throws Exception {
        Path racine = projetNatif();
        Workspace workspace = WorkspaceLoader.load(racine)
                .workspace().orElseThrow();

        SpecsBuild.Constitution nonResolue = SpecsBuild.jvm(workspace,
                ExternalResolution.empty(), racine, temp.resolve("out"),
                temp.resolve("m2"));
        assertTrue(nonResolue.constructible());
        assertEquals(1, nonResolue.ecarts.size());
        assertTrue(nonResolue.ecarts.get(0).contains(
                "com.example:lib:1.0"), nonResolue.ecarts.get(0));

        Path aar = temp.resolve("lib.aar");
        Files.write(aar, new byte[]{0x50, 0x4b});
        ExternalResolution aarResolu = new ExternalResolution.Builder()
                .add("com.example:lib", aar, true, null)
                .build();
        SpecsBuild.Constitution avecAar = SpecsBuild.jvm(workspace,
                aarResolu, racine, temp.resolve("out"),
                temp.resolve("m2"));
        assertTrue(avecAar.constructible());
        assertEquals(1, avecAar.ecarts.size());
        assertTrue(avecAar.ecarts.get(0).contains("AAR"),
                avecAar.ecarts.get(0));
    }

    @Test
    void modulesAndroidRefusesAvecEcart() throws Exception {
        Path racine = Files.createDirectories(temp.resolve("android"));
        Files.writeString(racine.resolve("joide.toml"), """
                schema = "joide.workspace/1"

                name = "essai"

                [modules]
                include = ["appli"]
                """);
        Files.createDirectories(
                racine.resolve("appli/src/main/java"));
        Files.writeString(
                racine.resolve("appli/joide-module.toml"), """
                        schema = "joide.module/1"
                        name = "appli"
                        type = "android-application"

                        [[sources]]
                        name = "main"
                        source = ["src/main/java"]
                        """);

        Workspace android = WorkspaceLoader.load(racine)
                .workspace().orElseThrow();
        SpecsBuild.Constitution constitution = SpecsBuild.jvm(android,
                ExternalResolution.empty(), racine, temp.resolve("out"),
                temp.resolve("m2"));
        assertFalse(constitution.constructible());
        assertEquals(1, constitution.ecarts.size());
        assertTrue(constitution.ecarts.get(0).contains(":appli"),
                constitution.ecarts.get(0));
    }
}
