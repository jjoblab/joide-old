package jo.joide.deps.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Parseur JSON : objets, tableaux, échappements, tolérance.
 */
class JsonTest {

    @Test
    void parsesASampleModuleFile() {
        String text = "{\n"
                + "  \"formatVersion\": \"1.0\",\n"
                + "  \"variants\": [\n"
                + "    {\n"
                + "      \"name\": \"apiElements\",\n"
                + "      \"attributes\": {"
                + " \"org.gradle.usage\": \"java-api\","
                + " \"org.gradle.libraryelements\": \"jar\" },\n"
                + "      \"files\": [\n"
                + "        { \"name\": \"lib-1.0.jar\", \"url\": \"lib-1.0.jar\","
                + " \"size\": 1024,\n"
                + "          \"checksums\": { \"sha1\": \"ab\","
                + " \"sha256\": \"cd\" } }\n"
                + "      ],\n"
                + "      \"dependencies\": [\n"
                + "        { \"group\": \"g\", \"module\": \"m\","
                + " \"version\": \"1.0\" }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}";
        Json.Document doc = Json.parse(text);
        assertTrue(doc.healthy());
        assertEquals(JsonValue.Type.OBJECT, doc.root.type());
        assertEquals("1.0", doc.root.member("formatVersion").asString());
        JsonValue variant = doc.root.member("variants").items().get(0);
        assertEquals("apiElements", variant.member("name").asString());
        assertEquals("java-api",
                variant.member("attributes").member("org.gradle.usage")
                        .asString());
        JsonValue file = variant.member("files").items().get(0);
        assertEquals("lib-1.0.jar", file.member("name").asString());
        assertEquals(1024, file.member("size").asLong());
        assertEquals("cd", file.member("checksums").member("sha256")
                .asString());
    }

    @Test
    void decodesEscapes() {
        Json.Document doc = Json.parse(
                "{\"s\": \"a\\nb\\u0041\\t/\\\\\", \"t\": [true, false, null]}");
        assertTrue(doc.healthy());
        assertEquals("a\nbA\t/\\", doc.root.member("s").asString());
        assertEquals(Boolean.TRUE, doc.root.member("t").items().get(0)
                .asBoolean());
        assertTrue(doc.root.member("t").items().get(2).isNull());
    }

    @Test
    void toleratesTrailingCommas() {
        Json.Document doc = Json.parse("{\"a\": [1, 2,], \"b\": {\"k\": 1,}}");
        assertTrue(doc.healthy());
        assertEquals(2, doc.root.member("a").items().size());
    }

    @Test
    void rejectsBrokenDocuments() {
        assertTrue(!Json.parse("{\"a\": ").healthy());
        assertTrue(!Json.parse("[1, 2").healthy());
        assertTrue(!Json.parse("{\"a\" 1}").healthy());
        assertTrue(!Json.parse("nul").healthy());
        Json.Document trailing = Json.parse("{} x");
        assertTrue(!trailing.healthy());
    }

    @Test
    void memberOfNonObjectIsNull() {
        Json.Document doc = Json.parse("[1,2]");
        assertNull(doc.root.member("x").asString());
        assertEquals(0, doc.root.items().get(0).keys().size());
    }
}
