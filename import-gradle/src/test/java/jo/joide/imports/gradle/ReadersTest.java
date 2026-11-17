package jo.joide.imports.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jo.joide.platform.Problem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests des lecteurs de fichiers : propriétés Gradle, catalogue de
 * versions et réglages (ADR-039, ADR-040).
 */
class ReadersTest {

    @Test
    void gradlePropertiesTolerantForms() {
        GradleProperties properties = GradleProperties.parse(
                "# commentaire\n"
                        + "! autre commentaire\n"
                        + "org.gradle.jvmargs=-Xmx3g\n"
                        + "slf4jVersion : 2.0.9\n"
                        + "key.with\\:colon = valeur\n"
                        + "empty=\n"
                        + "ligne invalide sans separateur\n");
        assertEquals("-Xmx3g",
                properties.get("org.gradle.jvmargs").orElseThrow());
        assertEquals("2.0.9",
                properties.get("slf4jVersion").orElseThrow());
        assertEquals("valeur",
                properties.get("key.with:colon").orElseThrow());
        assertEquals("", properties.get("empty").orElseThrow());
        assertTrue(properties.get("ligne invalide sans separateur")
                .isEmpty());
        assertEquals(List.of("org.gradle.jvmargs", "slf4jVersion",
                "key.with:colon", "empty"), properties.order());
    }

    @Test
    void gradlePropertiesLineContinuation() {
        GradleProperties properties = GradleProperties.parse(
                "long=valeur qui \\\n continue sur deux lignes\n");
        assertEquals("valeur qui continue sur deux lignes",
                properties.get("long").orElseThrow());
    }

    @Test
    void versionCatalogResolvesReferences() {
        List<Problem> problems = new ArrayList<>();
        VersionCatalog catalog = VersionCatalog.parse("""
                [versions]
                kotlin = "2.1.0"
                compose-bom = "2024.12.01"
                guava = "33.0-jre"

                [libraries]
                guava = { module = "com.google.guava:guava", version.ref = "guava" }
                kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version = "2.1.0" }
                compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
                unversioned = "org.slf4j:slf4j-api"

                [plugins]
                android-application = { id = "com.android.application", version.ref = "kotlin" }
                kotlin-android = { id = "org.jetbrains.kotlin.android", version = "2.1.0" }
                """, problems, "libs.versions.toml");
        assertEquals("com.google.guava:guava:33.0-jre",
                catalog.library("guava").orElseThrow().coordinates());
        assertEquals("org.jetbrains.kotlin:kotlin-stdlib:2.1.0",
                catalog.library("kotlin.stdlib").orElseThrow()
                        .coordinates());
        assertEquals("androidx.compose:compose-bom:2024.12.01",
                catalog.library("compose.bom").orElseThrow()
                        .coordinates());
        assertEquals("org.slf4j:slf4j-api",
                catalog.library("unversioned").orElseThrow().ga());
        assertNull(catalog.library("unversioned").orElseThrow()
                .coordinates());
        assertEquals("com.android.application",
                catalog.plugin("android.application").orElseThrow().id());
        assertEquals("org.jetbrains.kotlin.android",
                catalog.plugin("kotlin.android").orElseThrow().id());
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).code()
                .equals("catalog-library-unversioned"));
    }

    @Test
    void versionCatalogRichVersions() {
        List<Problem> problems = new ArrayList<>();
        VersionCatalog catalog = VersionCatalog.parse("""
                [versions]
                locked = { strictly = "[1.0, 2.0[", prefer = "1.5" }

                [libraries]
                alpha = { module = "com.example:alpha", version = { strictly = "1.2.3" } }
                required = { module = "com.example:required", version = { require = "2.0" } }
                """, problems, "libs.versions.toml");
        assertEquals("1.2.3",
                catalog.library("alpha").orElseThrow().version());
        assertEquals("2.0",
                catalog.library("required").orElseThrow().version());
    }

    @Test
    void versionCatalogEmptyAndGarbage() {
        List<Problem> problems = new ArrayList<>();
        VersionCatalog empty = VersionCatalog.parse("", problems, "x");
        assertTrue(empty.libraries().isEmpty());
        VersionCatalog garbage = VersionCatalog.parse(
                "ceci n'est [[pas du toml", problems, "x");
        assertTrue(garbage.libraries().isEmpty());
        assertTrue(problems.stream().anyMatch(
                p -> p.code().equals("catalog-syntax")));
    }

    @TempDir
    Path tempDir;

    @Test
    void settingsGroovyAndKotlin() throws Exception {
        Path groovy = java.nio.file.Files.writeString(
                tempDir.resolve("settings.gradle"),
                "rootProject.name = 'mon-projet'\n"
                        + "include ':app', ':core:ui'\n"
                        + "include ':core:network'\n"
                        + "includeFlat 'annexe'\n"
                        + "pluginManagement {\n"
                        + "    repositories { gradlePluginPortal() }\n"
                        + "}\n");
        List<Problem> problems = new ArrayList<>();
        GradleSettings settings = GradleSettings.read(groovy, problems);
        assertEquals("mon-projet", settings.rootName().orElseThrow());
        assertEquals(List.of(":app", ":core:ui", ":core:network"),
                settings.includes());
        assertTrue(problems.stream().anyMatch(
                p -> p.code().equals("settings-include-flat")));
        assertTrue(problems.stream().anyMatch(
                p -> p.code().equals("settings-block-not-mapped")));

        Path kotlin = java.nio.file.Files.writeString(
                tempDir.resolve("settings.gradle.kts"),
                "rootProject.name = \"kts-projet\"\n"
                        + "include(\":app\", \":lib\")\n");
        List<Problem> ktsProblems = new ArrayList<>();
        GradleSettings kts = GradleSettings.read(kotlin, ktsProblems);
        assertEquals("kts-projet", kts.rootName().orElseThrow());
        assertEquals(List.of(":app", ":lib"), kts.includes());
        assertTrue(ktsProblems.isEmpty());
    }

    @Test
    void settingsUnreadableReportsError() {
        List<Problem> problems = new ArrayList<>();
        GradleSettings settings = GradleSettings.read(
                tempDir.resolve("absent.gradle"), problems);
        assertTrue(settings.includes().isEmpty());
        assertTrue(problems.stream().anyMatch(
                p -> p.code().equals("settings-unreadable")));
    }

    @Test
    void interpolationResolvesAndReports() {
        List<Problem> problems = new ArrayList<>();
        Map<String, String> scope = Map.of("foo", "1.0", "bar.baz", "2.0");
        String resolved = Interpolation.resolve(
                "g:a:${foo} et $foo et ${bar.baz} et ${inconnu}",
                Interpolation.scopeOf(scope), problems, "build.gradle");
        assertEquals("g:a:1.0 et 1.0 et 2.0 et ${inconnu}", resolved);
        assertEquals(1, problems.size());
        assertEquals("interpolation-unresolved", problems.get(0).code());
        assertEquals("a-b", Interpolation.resolve("a-b",
                Interpolation.emptyScope(), problems, "x"));
        assertEquals("$", Interpolation.resolve("$$",
                name -> null, problems, "x"));
        assertEquals("$-", Interpolation.resolve("$-",
                name -> null, problems, "x"));
    }
}
