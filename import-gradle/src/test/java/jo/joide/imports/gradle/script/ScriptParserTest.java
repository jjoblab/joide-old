package jo.joide.imports.gradle.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Tests du parseur statique tolérant : les deux dialectes, les
 * formes d'instructions, la tolérance aux ordures — jamais
 * d'exception (ADR-039).
 */
class ScriptParserTest {

    @Test
    void groovyCallWithStringsAndMapArgs() {
        Script script = ScriptParser.parse(
                "apply plugin: 'com.android.application'\n"
                        + "include ':app', ':lib'\n");
        Optional<ScriptStmt> apply = script.findCall("apply");
        assertTrue(apply.isPresent());
        assertEquals(1, apply.get().args().size());
        ScriptExpr.MapExpr map =
                assertInstanceOf(ScriptExpr.MapExpr.class,
                        apply.get().args().get(0));
        assertEquals("plugin", ((ScriptExpr.Literal) map.entries()
                .get(0).key()).raw());
        Optional<ScriptStmt> include = script.findCall("include");
        assertTrue(include.isPresent());
        assertEquals(2, include.get().args().size());
        assertEquals(":app", ((ScriptExpr.Literal) include.get()
                .args().get(0)).decoded());
    }

    @Test
    void kotlinBlocksAndAssignments() {
        Script script = ScriptParser.parse("""
                plugins {
                    id("com.android.application")
                    alias(libs.plugins.kotlin.android) apply false
                }
                android {
                    namespace = "com.example.app"
                    compileSdk = 35
                    defaultConfig {
                        minSdk 24
                    }
                }
                """);
        Script plugins = script.findCall("plugins").orElseThrow().block();
        Optional<ScriptStmt> id = plugins.findCall("id");
        assertTrue(id.isPresent());
        assertEquals("com.android.application",
                id.get().firstStringArgument().orElseThrow());
        Optional<ScriptStmt> alias = plugins.findCall("alias");
        assertTrue(alias.isPresent());
        ScriptExpr.Reference ref = assertInstanceOf(
                ScriptExpr.Reference.class, alias.get().args().get(0));
        assertEquals(List.of("libs", "plugins", "kotlin", "android"),
                ref.chain());
        Script android = script.findCall("android").orElseThrow().block();
        assertEquals("com.example.app",
                android.findString("namespace").orElseThrow());
        assertEquals("35",
                android.findString("compileSdk").orElseThrow());
        Script defaults = android.findCall("defaultConfig")
                .orElseThrow().block();
        assertEquals("24", defaults.findProperty("minSdk").orElseThrow());
    }

    @Test
    void dependenciesWithClosuresAndChains() {
        Script script = ScriptParser.parse("""
                dependencies {
                    implementation("org.slf4j:slf4j-api:2.0.9")
                    api(project(":core"))
                    testImplementation(libs.junit)
                    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")
                    implementation(platform(libs.compose.bom))
                }
                tasks.register("clean") {
                    delete(rootProject.layout.buildDirectory)
                }
                """);
        Script deps = script.findCall("dependencies").orElseThrow().block();
        List<ScriptStmt> calls = deps.calls();
        assertEquals(5, calls.size());
        assertEquals("org.slf4j:slf4j-api:2.0.9",
                calls.get(0).firstStringArgument().orElseThrow());
        ScriptExpr.CallExpr project = assertInstanceOf(
                ScriptExpr.CallExpr.class, calls.get(1).args().get(0));
        assertEquals("project", project.name());
        ScriptStmt register = script.findCall("tasks.register")
                .orElseThrow();
        assertTrue(register.block() != null);
        assertEquals("clean", register.firstStringArgument().orElseThrow());
    }

    @Test
    void extAssignmentsAndInterpolationKeptRaw() {
        Script script = ScriptParser.parse(
                "ext { slf4jVersion = '2.0.9' }\n"
                        + "ext.copyright = \"2026\"\n"
                        + "def outDir = \"build/${slf4jVersion}\"\n");
        Script ext = script.findCall("ext").orElseThrow().block();
        assertEquals("2.0.9", ext.findString("slf4jVersion").orElseThrow());
        assertEquals("2026",
                script.findString("ext.copyright").orElseThrow());
        Optional<String> out = script.findString("outDir");
        assertEquals("build/${slf4jVersion}", out.orElseThrow());
    }

    @Test
    void garbageNeverThrows() {
        List<String> garbage = List.of(
                "",
                "}{",
                "((((",
                "dependencies {",
                "implementation(",
                "\"unterminated",
                "a.b.c.d. . .",
                "???",
                "android { defaultConfig { minSdk } } } }",
                "foo [bar: baz, broken",
                "\uD83D\uDE00\uD83D\uDE01 emoji soup",
                "val x = listOf(1, 2");
        for (String source : garbage) {
            Script script = ScriptParser.parse(source);
            assertTrue(script != null,
                    "parser must return a script for: " + source);
        }
    }

    @Test
    void fuzzBytesNeverThrow() {
        long seed = 20261115L;
        for (int round = 0; round < 500; round++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int length = (int) ((seed >>> 33) % 120);
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < length; i++) {
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                int alphabet = "abc{}()\"'`.,:=+-/*\n xyz".length();
                text.append("abc{}()\"'`.,:=+-/*\n xyz"
                        .charAt((int) ((seed >>> 33) % alphabet)));
            }
            Script script = ScriptParser.parse(text.toString());
            assertTrue(script != null);
        }
    }

    @Test
    void sourceSetsGroovyAndKotlinForms() {
        Script groovy = ScriptParser.parse("""
                sourceSets {
                    main {
                        java.srcDirs += "gen/java"
                        resources.srcDirs = ["gen/res"]
                    }
                }
                """);
        Script sets = groovy.findCall("sourceSets").orElseThrow().block();
        Script main = sets.findCall("main").orElseThrow().block();
        ScriptStmt plus = main.findAssign("java.srcDirs").orElseThrow();
        assertEquals("+=", plus.operator());
        ScriptStmt replace = main.findAssign("resources.srcDirs")
                .orElseThrow();
        assertEquals("=", replace.operator());

        Script kotlin = ScriptParser.parse("""
                sourceSets {
                    getByName("main") {
                        java.srcDir("build/generated")
                    }
                }
                """);
        Script kSets = kotlin.findCall("sourceSets").orElseThrow().block();
        ScriptStmt named = kSets.calls().get(0);
        assertEquals("getByName", named.name());
        assertEquals("main", named.firstStringArgument().orElseThrow());
        assertTrue(named.block().findCall("java.srcDir").isPresent());
    }

    @Test
    void buildTypesAndFlavorsNestedBlocks() {
        Script script = ScriptParser.parse("""
                android {
                    buildTypes {
                        release {
                            isMinifyEnabled = true
                        }
                    }
                    productFlavors {
                        create("free") {
                            dimension = "mode"
                        }
                    }
                }
                """);
        Script android = script.findCall("android").orElseThrow().block();
        Script types = android.findCall("buildTypes").orElseThrow().block();
        Script release = types.findCall("release").orElseThrow().block();
        assertEquals("true",
                release.findString("isMinifyEnabled").orElseThrow());
        Script flavors = android.findCall("productFlavors")
                .orElseThrow().block();
        ScriptStmt free = flavors.findCall("create").orElseThrow();
        assertEquals("free", free.firstStringArgument().orElseThrow());
        assertEquals("mode", free.block().findString("dimension")
                .orElseThrow());
    }

    @Test
    void commentsAndStringsAreUnderstood() {
        Script script = ScriptParser.parse(
                "// commentaire avec des accolades } { et des quotes \"\n"
                        + "/* bloc { non } compté */\n"
                        + "rootProject.name = \"mon-projet\" // fin\n"
                        + "println \"chaine avec } accolade et ' quote\"\n");
        assertEquals("mon-projet",
                script.findString("rootProject.name").orElseThrow());
        Optional<ScriptStmt> println = script.findCall("println");
        assertTrue(println.isPresent());
        assertEquals("chaine avec } accolade et ' quote",
                println.get().firstStringArgument().orElseThrow());
    }

    @Test
    void tripleQuotedStringsSurvive() {
        Script script = ScriptParser.parse(
                "docs = \"\"\"ligne 1\nligne 2 avec } et {\"\"\"\n");
        assertEquals("ligne 1\nligne 2 avec } et {",
                script.findString("docs").orElseThrow());
    }

    @Test
    void pluginShortNamesAndApplyFalse() {
        Script script = ScriptParser.parse(
                "plugins { java library }\n");
        Script plugins = script.findCall("plugins").orElseThrow().block();
        List<ScriptStmt> calls = plugins.calls();
        assertEquals(2, calls.size());
        assertEquals("java", calls.get(0).name());
        assertEquals(0, calls.get(0).args().size());
        assertEquals("library", calls.get(1).name());
    }
}
