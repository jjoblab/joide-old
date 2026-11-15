package jo.joide.imports.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import jo.joide.model.DependencyRef;
import jo.joide.model.DependencyScope;
import jo.joide.model.Module;
import jo.joide.model.ModuleType;
import jo.joide.model.Workspace;
import jo.joide.platform.Problem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Import des cinq projets échantillons : Groovy simple, multi-modules
 * avec catalogue, Kotlin + Compose, parfums et variantes, projet
 * irrésolu — chacun produit un modèle correct ou un SyncReport
 * explicite (§7 P7 du prompt).
 */
class GradleImporterTest {

    private static final Path SAMPLES =
            Paths.get("src", "test", "resources", "gradle-samples");

    @TempDir
    Path tempDir;

    private Path sample(String name) throws IOException {
        Path source = SAMPLES.resolve(name);
        Path target = tempDir.resolve(name);
        copy(source, target);
        return target;
    }

    private static void copy(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (var walk = Files.walk(source)) {
            for (Path file : walk.filter(Files::isRegularFile)
                    .collect(Collectors.toList())) {
                Path to = target.resolve(source.relativize(file)
                        .toString());
                Files.createDirectories(to.getParent());
                Files.copy(file, to);
            }
        }
    }

    @Test
    void jvmGroovyInterpolatesProperties() throws IOException {
        GradleImporter.ImportResult result =
                GradleImporter.importProject(sample("jvm-groovy"));
        Workspace workspace = result.workspace();
        assertEquals("jvm-groovy-sample", workspace.name());
        Module lib = workspace.module(":lib");
        assertNotNull(lib);
        assertEquals(ModuleType.JVM_LIBRARY, lib.type());
        List<DependencyRef> deps = lib.dependencies();
        assertEquals(4, deps.size());
        assertEquals("org.slf4j:slf4j-api:2.0.9", deps.get(0).coordinates());
        assertEquals(DependencyScope.API, deps.get(0).scope());
        assertEquals("com.google.guava:guava:33.0-jre",
                deps.get(1).coordinates());
        assertEquals("org.junit.jupiter:junit-jupiter:5.10.2",
                deps.get(2).coordinates());
        assertEquals(DependencyScope.TEST_IMPLEMENTATION,
                deps.get(2).scope());
        assertEquals("org.junit.platform:junit-platform-launcher:1.10.2",
                deps.get(3).coordinates());
        assertEquals(DependencyScope.TEST_RUNTIME_ONLY,
                deps.get(3).scope());
        assertTrue(result.report().confidenceByModule()
                .get(":lib") == ModuleConfidence.HIGH,
                () -> result.report().render());
    }

    @Test
    void multiCatalogWithVersionCatalogAndNestedModules()
            throws IOException {
        GradleImporter.ImportResult result =
                GradleImporter.importProject(sample("multi-catalog"));
        Workspace workspace = result.workspace();
        assertEquals("multi-catalog-sample", workspace.name());
        assertEquals("24", workspace.setting("android.min-sdk")
                .orElseThrow());
        Module app = workspace.module(":app");
        assertNotNull(app);
        assertEquals(ModuleType.ANDROID_APPLICATION, app.type());
        assertTrue(app.facets().containsKey("kotlin"));
        assertEquals("com.example.multicatalog.app",
                app.facets().get("android").entry("namespace"));
        assertEquals("35", app.facets().get("android").entry("compile-sdk"));
        assertEquals("1.1.0",
                app.facets().get("android").entry("version-name"));
        // seul le type release est déclaré : debug est implicite en AGP
        assertEquals("release",
                app.facets().get("android").entry("build-types"));
        assertEquals("true",
                app.facets().get("android").entry("minify-release"));
        List<DependencyRef> appDeps = app.dependencies();
        // platform(BOM) et alias sans version (gérés par le BOM) sont
        // ignorés avec avertissement : 6 dépendances tenables
        assertEquals(6, appDeps.size());
        assertEquals(":core:ui", appDeps.get(0).modulePath());
        assertEquals(":core:network", appDeps.get(1).modulePath());
        assertEquals("androidx.core:core-ktx:1.15.0",
                appDeps.get(2).coordinates());
        assertEquals("androidx.activity:activity-compose:1.9.3",
                appDeps.get(4).coordinates());
        assertEquals("junit:junit:4.13.2", appDeps.get(5).coordinates());

        Module ui = workspace.module(":core:ui");
        assertNotNull(ui);
        assertEquals(ModuleType.ANDROID_LIBRARY, ui.type());
        assertEquals("true", ui.facets().get("android").entry("compose"));
        // ui-tooling est géré par le BOM (sans version au catalogue) :
        // ignoré avec avertissement, jamais en silence
        assertTrue(ui.dependencies().stream().noneMatch(
                d -> d.coordinates() != null && d.coordinates()
                        .contains("ui-tooling")));
        assertTrue(result.report().problems().stream().anyMatch(
                p -> p.code().equals("dependency-catalog-unversioned")
                        && p.message().contains("ui.tooling")));

        Module network = workspace.module(":core:network");
        assertNotNull(network);
        assertEquals(ModuleType.JVM_LIBRARY, network.type());
        assertTrue(result.report().problems().stream().anyMatch(
                p -> p.code().equals("dependency-platform")),
                "platform/BOM must be reported, not silently ignored");
        assertTrue(result.report().problems().stream().anyMatch(
                p -> p.code().equals("dependency-catalog-unversioned")),
                "BOM-managed aliases must be reported as unversioned");
        assertTrue(result.report().problems().stream().anyMatch(
                p -> p.code().equals("dependency-scope-unknown")
                        && p.message().contains("androidTestImplementation")),
                "androidTest configuration must be reported");
    }

    @Test
    void composeAppBuildFeaturesAndVariants() throws IOException {
        GradleImporter.ImportResult result =
                GradleImporter.importProject(sample("compose-app"));
        Workspace workspace = result.workspace();
        Module app = workspace.module(":app");
        assertNotNull(app);
        assertEquals(ModuleType.ANDROID_APPLICATION, app.type());
        assertEquals("com.example.composeapp",
                app.facets().get("android").entry("application-id"));
        assertEquals("28", workspace.setting("android.min-sdk")
                .orElseThrow());
        DependencyRef leakcanary = app.dependencies().stream()
                .filter(d -> d.coordinates() != null && d.coordinates()
                        .startsWith("com.squareup.leakcanary"))
                .findFirst().orElseThrow();
        assertTrue(leakcanary.variants().contains("debug"));
        assertEquals(ModuleConfidence.MEDIUM,
                result.report().confidenceByModule().get(":app"));
    }

    @Test
    void flavorsAndVariantScopedDependencies() throws IOException {
        GradleImporter.ImportResult result =
                GradleImporter.importProject(sample("flavors"));
        Workspace workspace = result.workspace();
        Module app = workspace.module(":app");
        assertNotNull(app);
        assertEquals(ModuleType.ANDROID_APPLICATION, app.type());
        assertEquals("free,paid",
                app.facets().get("android").entry("flavors"));
        assertEquals("mode",
                app.facets().get("android").entry("dimension-free"));
        assertEquals("com.android.tools:desugar_jdk_libs:2.1.3",
                app.facets().get("android").entry("desugar-libraries"));
        DependencyRef ads = app.dependencies().stream()
                .filter(d -> d.coordinates() != null && d.coordinates()
                        .contains("play-services-ads"))
                .findFirst().orElseThrow();
        assertTrue(ads.variants().contains("free"));
        DependencyRef billing = app.dependencies().stream()
                .filter(d -> d.coordinates() != null && d.coordinates()
                        .contains("billing"))
                .findFirst().orElseThrow();
        assertTrue(billing.variants().contains("paid"));
        DependencyRef assertj = app.dependencies().stream()
                .filter(d -> d.coordinates() != null && d.coordinates()
                        .contains("assertj"))
                .findFirst().orElseThrow();
        assertEquals(DependencyScope.TEST_IMPLEMENTATION, assertj.scope());
        assertTrue(assertj.variants().contains("debug"));
        List<String> javaSources = app.sourceSets().get("main").roots()
                .stream().filter(root -> root.role().id().equals("source"))
                .map(root -> root.path()).collect(Collectors.toList());
        assertTrue(javaSources.contains("src/main/java"));
        assertTrue(javaSources.contains("gen/java"));
        List<String> resDirs = app.sourceSets().get("main").roots()
                .stream().filter(root -> root.role().id()
                        .equals("android-res"))
                .map(root -> root.path()).collect(Collectors.toList());
        assertEquals(List.of("src/main/res", "src brandings/res"), resDirs);
        assertTrue(result.report().problems().stream().anyMatch(
                p -> p.code().equals("dependency-scope-unknown")
                        && p.message().contains("annotationProcessor")));
    }

    @Test
    void poisonedProjectNeverCrashesAndReportsEverything()
            throws IOException {
        Path projectDir = sample("poisoned");
        GradleImporter.ImportResult result =
                GradleImporter.importProject(projectDir);
        Workspace workspace = result.workspace();
        Module app = workspace.module(":app");
        assertNotNull(app, "the module must be imported despite poison");
        assertEquals(ModuleType.ANDROID_APPLICATION, app.type());
        assertTrue(app.dependencies().stream().anyMatch(
                d -> "com.squareup.okhttp3:okhttp:4.12.0"
                        .equals(d.coordinates())),
                "group/name/version map notation must be assembled");
        assertTrue(app.dependencies().stream().anyMatch(
                d -> "com.google.dagger:dagger:2.52".equals(d.coordinates())),
                "literal with exclusion block must be kept");
        SyncReport report = result.report();
        List<String> codes = report.problems().stream()
                .map(Problem::code).distinct().collect(Collectors.toList());
        for (String expected : List.of("dependency-local-files",
                "dependency-platform", "dependency-test-fixtures",
                "dependency-scope-unknown", "dependency-block-skipped",
                "root-module-not-mappable", "root-toplevel-skipped",
                "build-toplevel-skipped", "android-block-not-mapped")) {
            assertTrue(codes.contains(expected),
                    "expected problem code " + expected + " in " + codes);
        }
        assertEquals(ModuleConfidence.LOW,
                report.confidenceByModule().get(":app"));
        assertFalse(Files.exists(projectDir.resolve(
                "settings-poison-marker.txt")));
        assertFalse(Files.exists(projectDir.resolve(
                "build-poison-marker.txt")));
        assertFalse(Files.exists(projectDir.resolve("app")
                .resolve("app-poison-marker.txt")));
        assertFalse(Files.exists(projectDir.resolve(
                "settings-exec-marker")));
        assertFalse(Files.exists(projectDir.resolve("build-exec-marker")));
    }

    @Test
    void singleModuleAtRootWithoutSettings() throws IOException {
        Path projectDir = tempDir.resolve("solitaire");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("settings.gradle"),
                "rootProject.name = 'solitaire'");
        Files.writeString(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java-library'
                }
                dependencies {
                    api 'org.slf4j:slf4j-api:2.0.9'
                }
                """);
        GradleImporter.ImportResult result =
                GradleImporter.importProject(projectDir);
        Module module = result.workspace().allModules().values().stream()
                .findFirst().orElseThrow();
        assertEquals(ModuleType.JVM_LIBRARY, module.type());
        assertEquals("solitaire", module.name());
        assertEquals(1, module.dependencies().size());
        assertTrue(result.report().problems().stream().anyMatch(
                p -> p.code().equals("import-root-as-module")));
    }

    @Test
    void absentDirectoryReportsErrorWithoutThrowing() {
        GradleImporter.ImportResult result = GradleImporter.importProject(
                tempDir.resolve("inexistant"));
        assertTrue(result.workspace().allModules().isEmpty());
        assertTrue(result.report().problems().stream().anyMatch(
                p -> p.code().equals("settings-file-missing")));
    }
}
