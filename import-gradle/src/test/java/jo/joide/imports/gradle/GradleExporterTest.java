package jo.joide.imports.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jo.joide.model.ContentRoot;
import jo.joide.model.DependencyRef;
import jo.joide.model.DependencyScope;
import jo.joide.model.Facet;
import jo.joide.model.Module;
import jo.joide.model.SourceSet;
import jo.joide.model.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Export Gradle : déterminisme, aller-retour import(export(modèle)) ≡
 * modèle, EXPORT-NOTES pour ce qui n'a pas d'équivalent (§7 P7).
 */
class GradleExporterTest {

    @TempDir
    Path tempDir;

    private static Module jvmModule() {
        Map<String, SourceSet> sets = new LinkedHashMap<>();
        sets.put("main", new SourceSet("main", List.of(
                new ContentRoot("src/main/java", jo.joide.model.ContentRole.SOURCE),
                new ContentRoot("src/main/resources", jo.joide.model.ContentRole.RESOURCES))));
        sets.put("test", new SourceSet("test", List.of(
                new ContentRoot("src/test/java", jo.joide.model.ContentRole.SOURCE),
                new ContentRoot("src/test/resources", jo.joide.model.ContentRole.RESOURCES))));
        return new Module(":core:network", "network",
                jo.joide.model.ModuleType.JVM_LIBRARY, sets,
                List.of(
                        DependencyRef.external(
                                "org.slf4j:slf4j-api:2.0.9",
                                DependencyScope.API, Set.of()),
                        DependencyRef.external(
                                "com.google.code.gson:gson:2.11.0",
                                DependencyScope.IMPLEMENTATION, Set.of()),
                        DependencyRef.external(
                                "org.junit.jupiter:junit-jupiter:5.10.2",
                                DependencyScope.TEST_IMPLEMENTATION,
                                Set.of())),
                Map.of());
    }

    private static Module androidApp() {
        Map<String, SourceSet> sets = new LinkedHashMap<>();
        sets.put("main", new SourceSet("main", List.of(
                new ContentRoot("src/main/java", jo.joide.model.ContentRole.SOURCE),
                new ContentRoot("src/main/res", jo.joide.model.ContentRole.ANDROID_RES),
                new ContentRoot("branding/res", jo.joide.model.ContentRole.ANDROID_RES))));
        sets.put("test", new SourceSet("test", List.of(
                new ContentRoot("src/test/java", jo.joide.model.ContentRole.SOURCE))));
        Map<String, String> androidEntries = new LinkedHashMap<>();
        androidEntries.put("manifest", "src/main/AndroidManifest.xml");
        androidEntries.put("namespace", "com.example.app");
        androidEntries.put("compile-sdk", "35");
        androidEntries.put("application-id", "com.example.app");
        androidEntries.put("min-sdk", "24");
        androidEntries.put("target-sdk", "35");
        androidEntries.put("version-code", "3");
        androidEntries.put("version-name", "1.1.0");
        androidEntries.put("build-types", "release");
        androidEntries.put("minify-release", "true");
        androidEntries.put("flavors", "free,paid");
        androidEntries.put("dimension-free", "mode");
        androidEntries.put("compose", "true");
        return new Module(":app", "app",
                jo.joide.model.ModuleType.ANDROID_APPLICATION, sets,
                List.of(
                        DependencyRef.toModule(":core:network",
                                DependencyScope.IMPLEMENTATION, Set.of()),
                        DependencyRef.external(
                                "androidx.core:core-ktx:1.15.0",
                                DependencyScope.IMPLEMENTATION, Set.of()),
                        DependencyRef.external(
                                "com.squareup.leakcanary:leakcanary-android:2.14",
                                DependencyScope.IMPLEMENTATION,
                                Set.of("debug")),
                        DependencyRef.external(
                                "com.google.android.gms:play-services-ads:23.6.0",
                                DependencyScope.IMPLEMENTATION,
                                Set.of("free")),
                        DependencyRef.external(
                                "org.assertj:assertj-core:3.26.3",
                                DependencyScope.TEST_IMPLEMENTATION,
                                Set.of("debug"))),
                Map.of("android", new Facet("android", androidEntries),
                        "kotlin", new Facet("kotlin", Map.of())));
    }

    private static Workspace sampleWorkspace() {
        Map<String, Module> modules = new LinkedHashMap<>();
        modules.put(":app", androidApp());
        modules.put(":core:network", jvmModule());
        return new Workspace("aller-retour", Map.of("main",
                new jo.joide.model.Project("main", modules)),
                Map.of("android.min-sdk", "24"));
    }

    @Test
    void exportIsDeterministic() {
        GradleExporter.ExportResult first =
                GradleExporter.export(sampleWorkspace());
        GradleExporter.ExportResult second =
                GradleExporter.export(sampleWorkspace());
        assertEquals(first.files(), second.files(),
                "deux exports du meme modele sont octets identiques");
        assertTrue(first.files().containsKey("settings.gradle.kts"));
        assertTrue(first.files().containsKey("app/build.gradle.kts"));
        assertTrue(first.files().containsKey(
                "core/network/build.gradle.kts"));
        assertTrue(first.files().containsKey("EXPORT-NOTES.md"));
    }

    @Test
    void roundTripIsIdentityOnRepresentableModel() throws IOException {
        Workspace model = sampleWorkspace();
        GradleExporter.ExportResult export =
                GradleExporter.export(model);
        Path dir = tempDir.resolve("export");
        GradleExporter.write(export, dir);
        GradleImporter.ImportResult result =
                GradleImporter.importProject(dir);
        Workspace reloaded = result.workspace();

        assertEquals(model.name(), reloaded.name(),
                () -> result.report().render());
        assertEquals(model.allModules().keySet(),
                reloaded.allModules().keySet());
        for (Map.Entry<String, Module> entry
                : model.allModules().entrySet()) {
            Module expected = entry.getValue();
            Module actual = reloaded.module(entry.getKey());
            assertEquals(expected.contentId(), actual.contentId(),
                    () -> "module " + entry.getKey() + " differ apres"
                            + " aller-retour :\nattendu "
                            + expected.canonicalForm() + "\nobtenu  "
                            + actual.canonicalForm() + "\n"
                            + result.report().render());
        }
        assertEquals(model.setting("android.min-sdk"),
                reloaded.setting("android.min-sdk"));
    }

    @Test
    void roundTripOfImportedSamplesIsIdentity() throws IOException {
        Path samples = Path.of("src", "test", "resources",
                "gradle-samples");
        // le projet multi-modules avec catalogue : l'import produit des
        // coordonnees concretes, l'export les ecrit, la relecture est
        // identique module par module
        GradleImporter.ImportResult imported =
                GradleImporter.importProject(samples.resolve(
                        "multi-catalog"));
        GradleExporter.ExportResult export =
                GradleExporter.export(imported.workspace());
        Path dir = tempDir.resolve("reexport");
        GradleExporter.write(export, dir);
        GradleImporter.ImportResult reloaded =
                GradleImporter.importProject(dir);
        for (Map.Entry<String, Module> entry
                : imported.workspace().allModules().entrySet()) {
            Module expected = entry.getValue();
            Module actual = reloaded.workspace().module(entry.getKey());
            assertNotEquals(null, actual, entry.getKey());
            assertEquals(expected.contentId(), actual.contentId(),
                    () -> entry.getKey() + " :\nattendu "
                            + expected.canonicalForm() + "\nobtenu  "
                            + actual.canonicalForm() + "\n"
                            + reloaded.report().render());
        }
        assertTrue(reloaded.report().errorCount() == 0,
                () -> reloaded.report().render());
    }

    @Test
    void exportNotesMentionUnrepresentableData() {
        Map<String, SourceSet> sets = new LinkedHashMap<>();
        sets.put("main", new SourceSet("main", List.of(
                new ContentRoot("src/main/java", jo.joide.model.ContentRole.SOURCE),
                new ContentRoot("src/gen/java", jo.joide.model.ContentRole.GENERATED))));
        Module module = new Module(":gen", "gen",
                jo.joide.model.ModuleType.JVM_LIBRARY, sets, List.of(),
                Map.of("metriques", new Facet("metriques",
                        Map.of("seuil", "42"))));
        Workspace workspace = new Workspace("notes", Map.of("main",
                new jo.joide.model.Project("main",
                        Map.of(":gen", module))), Map.of());
        GradleExporter.ExportResult export =
                GradleExporter.export(workspace);
        String notes = export.notes();
        assertTrue(notes.contains("metriques"),
                "la facette sans equivalent Gradle doit etre notee : "
                        + notes);
        assertTrue(notes.contains("generated"),
                "le role de contenu sans equivalent doit etre note : "
                        + notes);
        assertTrue(export.files().get("gen/build.gradle.kts")
                .contains("src/main/java"));
    }

    @Test
    void exportedFilesUseKotlinDsl() {
        GradleExporter.ExportResult export =
                GradleExporter.export(sampleWorkspace());
        String settings = export.files().get("settings.gradle.kts");
        assertTrue(settings.contains("rootProject.name = \"aller-retour\""));
        assertTrue(settings.contains("include(\":app\", \":core:network\")"));
        String app = export.files().get("app/build.gradle.kts");
        assertTrue(app.contains("id(\"com.android.application\")"));
        assertTrue(app.contains("id(\"org.jetbrains.kotlin.android\")"));
        assertTrue(app.contains("namespace = \"com.example.app\""));
        assertTrue(app.contains("compileSdk = 35"));
        assertTrue(app.contains("minSdk = 24"));
        assertTrue(app.contains("debugImplementation("));
        assertTrue(app.contains("freeImplementation("));
        assertTrue(app.contains("testDebugImplementation("));
        assertTrue(app.contains("project(\":core:network\")"));
        assertTrue(app.contains("isMinifyEnabled = true"));
        assertTrue(app.contains("create(\"free\")"));
        assertTrue(app.contains("dimension = \"mode\""));
        assertTrue(app.contains("compose = true"));
        assertTrue(app.contains("res.setSrcDirs(listOf("
                + "\"src/main/res\", \"branding/res\"))"));
    }
}
