package jo.joide.deps.gmm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * GMM : variantes, attributs, fichiers, dépendances, renvois.
 */
class GmmParserTest {

    private static final String MODULE = "{"
            + "\"formatVersion\": \"1.0\","
            + "\"variants\": ["
            + "  {"
            + "    \"name\": \"releaseApiElements\","
            + "    \"attributes\": {"
            + "      \"org.gradle.usage\": \"java-api\","
            + "      \"org.jetbrains.kotlin.platform.type\": \"androidjvm\""
            + "    },"
            + "    \"available-at\": {"
            + "      \"url\": \"lib-1.0.module\","
            + "      \"attributes\": { \"org.gradle.category\": \"library\" }"
            + "    }"
            + "  },"
            + "  {"
            + "    \"name\": \"releaseRuntimeElements\","
            + "    \"attributes\": {"
            + "      \"org.gradle.usage\": \"java-runtime\""
            + "    },"
            + "    \"files\": ["
            + "      {"
            + "        \"name\": \"lib-1.0.aar\","
            + "        \"url\": \"lib-1.0.aar\","
            + "        \"size\": 2048,"
            + "        \"checksums\": {"
            + "          \"sha1\": \"1111111111111111111111111111111111111111\","
            + "          \"sha256\": \"2222222222222222222222222222222222222222"
            + "222222222222222222222222\""
            + "        }"
            + "      }"
            + "    ],"
            + "    \"dependencies\": ["
            + "      {"
            + "        \"group\": \"org.dep\","
            + "        \"module\": \"trans\","
            + "        \"version\": { \"requires\": \"[1.0,2.0)\" },"
            + "        \"optional\": true,"
            + "        \"excludes\": [{ \"group\": \"org.skip\", \"module\": \"me\" }]"
            + "      }"
            + "    ]"
            + "  }"
            + "]"
            + "}";

    @Test
    void parsesVariantsAndSelectionData() {
        GmmParser.Result r = GmmParser.parse(MODULE);
        assertTrue(r.problems.isEmpty(), () -> r.problems.toString());
        assertEquals(2, r.module.variants.size());
        GmmModule.GmmVariant api = r.module.variant("releaseApiElements");
        assertEquals("java-api", api.attr("org.gradle.usage"));
        assertEquals("androidjvm",
                api.attr("org.jetbrains.kotlin.platform.type"));
        // Renvoi : l'url relative et les attributs du renvoi.
        assertEquals("lib-1.0.module", api.availableAtUrl);
        assertEquals("library",
                api.availableAtAttributes.get("org.gradle.category"));
        // Variante d'exécution : fichiers et dépendances.
        GmmModule.GmmVariant runtime = r.module.variant(
                "releaseRuntimeElements");
        assertEquals(1, runtime.files.size());
        GmmModule.GmmFile file = runtime.files.get(0);
        assertEquals("lib-1.0.aar", file.name);
        assertEquals("aar", file.extension());
        assertEquals(2048, file.size);
        assertEquals(64, file.sha256.length());
        assertEquals(1, runtime.dependencies.size());
        GmmModule.GmmDependency dep = runtime.dependencies.get(0);
        assertEquals("org.dep:trans", dep.ga());
        // Version objet : « requires » extraite, pas « strictly ».
        assertEquals("[1.0,2.0)", dep.version);
        assertTrue(!dep.versionStrictly);
        assertTrue(dep.optional);
        assertTrue(dep.excludes.contains("org.skip:me"));
    }

    @Test
    void brokenDocumentsAreReported() {
        GmmParser.Result broken = GmmParser.parse("{");
        assertNull(broken.module);
        assertTrue(!broken.problems.isEmpty());
        GmmParser.Result notObject = GmmParser.parse("[1]");
        assertNull(notObject.module);
        assertTrue(!notObject.problems.isEmpty());
        // Document lisible mais incomplet : formatVersion manquant.
        GmmParser.Result partial = GmmParser.parse(
                "{\"variants\":[{\"name\":\"v\"}]}");
        assertEquals(1, partial.module.variants.size());
        assertTrue(!partial.problems.isEmpty());
    }

    @Test
    void formatVersionIsKeptAsText() {
        GmmParser.Result r = GmmParser.parse(MODULE);
        assertEquals("1.0", r.module.formatVersion);
    }
}
