package jo.joide.build.daemon.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import jo.joide.deps.json.JsonValue;

/**
 * Cadres et écriture JSON du protocole : aller-retour, échappements,
 * plafond de cadre.
 */
class ProtocolTest {

    @Test
    void framesRoundTrip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Frames writer = new Frames(new ByteArrayInputStream(
                new byte[0]), bytes);
        writer.write("bonjour \"démon\"");
        writer.write("{\"type\":\"hello\"}");
        Frames reader = new Frames(new ByteArrayInputStream(
                bytes.toByteArray()), new ByteArrayOutputStream());
        assertEquals("bonjour \"démon\"", reader.read());
        assertEquals("{\"type\":\"hello\"}", reader.read());
    }

    @Test
    void framesRejectOversized() throws IOException {
        // lecture : préfixe de longueur hors plafond → refus immédiat
        java.io.ByteArrayOutputStream bogus = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream prefix =
                new java.io.DataOutputStream(bogus);
        prefix.writeInt(Frames.MAX_FRAME_BYTES + 1);
        Frames reader = new Frames(new java.io.ByteArrayInputStream(
                bogus.toByteArray()), new ByteArrayOutputStream());
        org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> reader.read());
        // lecture : longueur négative → refus immédiat
        java.io.ByteArrayOutputStream negative =
                new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream minus =
                new java.io.DataOutputStream(negative);
        minus.writeInt(-1);
        Frames negativeReader = new Frames(
                new java.io.ByteArrayInputStream(negative.toByteArray()),
                new ByteArrayOutputStream());
        org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> negativeReader.read());
        // écriture : cadre trop grand → refus avant l'envoi
        String huge = "x".repeat(Frames.MAX_FRAME_BYTES + 1);
        org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> new Frames(new ByteArrayInputStream(new byte[0]),
                        new ByteArrayOutputStream()).write(huge));
    }

    @Test
    void jsonEscapesAndNests() {
        String json = Json.object(
                "type", "task",
                "name", "app:compileJava",
                "problems", List.of("ligne 1\nligne 2", "tab\tfin"),
                "heap", 1234567L,
                "ok", true);
        JsonValue parsed = jo.joide.deps.json.Json.parse(json).root;
        assertEquals("task", parsed.member("type").asString());
        assertEquals("app:compileJava", parsed.member("name").asString());
        assertEquals(1234567L, parsed.member("heap").asLong());
        assertEquals(Boolean.TRUE, parsed.member("ok").asBoolean());
        assertEquals("ligne 1\nligne 2",
                parsed.member("problems").items().get(0).asString());
        assertEquals("tab\tfin",
                parsed.member("problems").items().get(1).asString());
    }

    @Test
    void nonAsciiSurvivesRoundTrip() throws IOException {
        String message = "démon — construit : OK ✓";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new Frames(new ByteArrayInputStream(new byte[0]), bytes)
                .write(Json.object("message", message));
        Frames reader = new Frames(new ByteArrayInputStream(
                bytes.toByteArray()), new ByteArrayOutputStream());
        String frame = reader.read();
        assertTrue(frame.getBytes(StandardCharsets.UTF_8).length
                > message.length());
        JsonValue parsed = jo.joide.deps.json.Json.parse(frame).root;
        assertEquals(message, parsed.member("message").asString());
    }

    @Test
    void buildSpecRoundTripKeepsModulesAsObjects() {
        // régression : les modules étaient sérialisés en texte (double
        // encodage) et le démon lisait un nom nul — un module doit
        // traverser l'aller-retour comme objet JSON
        BuildSpec.ModuleSpec module = new BuildSpec.ModuleSpec(
                "app", true, List.of("app/src/main/java"),
                List.of("app/src/main/res"), "app/src/main/assets",
                "app/src/main/AndroidManifest.xml", List.of("core"),
                List.of("/m2/guava.jar"), List.of("/m2/aar/extrait"));
        BuildSpec spec = new BuildSpec(BuildSpec.Kind.ANDROID,
                "/tmp/projet", "/tmp/out", "/tmp/m2", "17", 4, 512,
                List.of(module), List.of(java.util.Map.of(
                        "buildType", "debug", "minSdk", 28)));
        JsonValue parsed = jo.joide.deps.json.Json.parse(
                Json.object("spec", spec.toJson())).root.member("spec");
        // les modules doivent être des objets, pas des chaînes
        assertEquals(jo.joide.deps.json.JsonValue.Type.OBJECT,
                parsed.member("modules").items().get(0).type());
        BuildSpec back = BuildSpec.fromJson(parsed);
        assertEquals(BuildSpec.Kind.ANDROID, back.kind);
        assertEquals("/tmp/projet", back.project);
        assertEquals(1, back.modules.size());
        BuildSpec.ModuleSpec read = back.modules.get(0);
        assertEquals("app", read.name);
        assertTrue(read.application);
        assertEquals(List.of("app/src/main/java"), read.sources);
        assertEquals(List.of("app/src/main/res"), read.resources);
        assertEquals("app/src/main/assets", read.assets);
        assertEquals("app/src/main/AndroidManifest.xml", read.manifest);
        assertEquals(List.of("core"), read.moduleDependencies);
        assertEquals(List.of("/m2/guava.jar"), read.externalJars);
        assertEquals(List.of("/m2/aar/extrait"), read.externalAarDirs);
        assertEquals(1, back.variants.size());
        assertEquals("debug", back.variants.get(0).get("buildType"));
        assertEquals(28L, back.variants.get(0).get("minSdk"));
    }

    @Test
    void moduleWithoutNameIsRefused() {
        String malformed = "{\"kind\":\"jvm\",\"project\":\"/p\","
                + "\"out\":\"/o\",\"m2\":\"/m\",\"threads\":4,"
                + "\"heapMb\":512,\"modules\":[{\"sources\":[]}],"
                + "\"variants\":[]}";
        JsonValue parsed = jo.joide.deps.json.Json.parse(malformed).root;
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> BuildSpec.fromJson(parsed));
    }
}
