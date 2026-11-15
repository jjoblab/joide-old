package jo.joide.deps.pom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * POM : parents, propriétés, nomenclatures, exclusions, portées.
 */
class PomParserTest {

    private static final Map<String, String> REPO = new HashMap<>();

    static {
        REPO.put("org.example:parent:1.0", ""
                + "<project>"
                + "<groupId>org.example</groupId>"
                + "<artifactId>parent</artifactId>"
                + "<version>1.0</version>"
                + "<packaging>pom</packaging>"
                + "<properties>"
                + "  <spring.version>5.3.1</spring.version>"
                + "</properties>"
                + "<dependencyManagement>"
                + "  <dependencies>"
                + "    <dependency>"
                + "      <groupId>org.managed</groupId>"
                + "      <artifactId>pin</artifactId>"
                + "      <version>2.0</version>"
                + "    </dependency>"
                + "  </dependencies>"
                + "</dependencyManagement>"
                + "</project>");
        REPO.put("org.example:bom:3.0", ""
                + "<project>"
                + "<groupId>org.example</groupId>"
                + "<artifactId>bom</artifactId>"
                + "<version>3.0</version>"
                + "<packaging>pom</packaging>"
                + "<dependencyManagement>"
                + "  <dependencies>"
                + "    <dependency>"
                + "      <groupId>org.frombom</groupId>"
                + "      <artifactId>bomdep</artifactId>"
                + "      <version>9.9</version>"
                + "    </dependency>"
                + "  </dependencies>"
                + "</dependencyManagement>"
                + "</project>");
    }

    private static PomParser parser(Map<String, String> repo) {
        return new PomParser((g, a, v) -> repo.get(g + ":" + a + ":" + v));
    }

    @Test
    void resolvesParentsPropertiesAndManagement() {
        String pom = ""
                + "<project>"
                + "<parent>"
                + "  <groupId>org.example</groupId>"
                + "  <artifactId>parent</artifactId>"
                + "  <version>1.0</version>"
                + "</parent>"
                + "<artifactId>lib</artifactId>"
                + "<dependencies>"
                + "  <dependency>"
                + "    <groupId>org.springframework</groupId>"
                + "    <artifactId>spring-core</artifactId>"
                + "    <version>${spring.version}</version>"
                + "  </dependency>"
                + "</dependencies>"
                + "</project>";
        PomParser.Result r = parser(REPO).parse(pom);
        assertTrue(r.problems.isEmpty(), () -> r.problems.toString());
        // Héritage : groupId et version viennent du parent ; la
        // propriété du parent interpole la dépendance.
        assertEquals("org.example", r.model.groupId);
        assertEquals("1.0", r.model.version);
        assertEquals("lib", r.model.artifactId);
        assertEquals("pom", r.model.packaging);
        assertEquals(1, r.model.dependencies.size());
        assertEquals("5.3.1", r.model.dependencies.get(0).version);
        // La gestion du parent est héritée.
        assertNotNull(r.model.managed("org.managed:pin"));
        assertEquals("2.0", r.model.managed("org.managed:pin").version);
    }

    @Test
    void expandsBomImportsInPlace() {
        String pom = ""
                + "<project>"
                + "<groupId>g</groupId><artifactId>a</artifactId>"
                + "<version>1</version>"
                + "<dependencyManagement>"
                + "  <dependencies>"
                + "    <dependency>"
                + "      <groupId>org.example</groupId>"
                + "      <artifactId>bom</artifactId>"
                + "      <version>3.0</version>"
                + "      <type>pom</type>"
                + "      <scope>import</scope>"
                + "    </dependency>"
                + "  </dependencies>"
                + "</dependencyManagement>"
                + "</project>";
        PomParser.Result r = parser(REPO).parse(pom);
        assertTrue(r.problems.isEmpty(), () -> r.problems.toString());
        assertNotNull(r.model.managed("org.frombom:bomdep"));
        assertEquals("9.9", r.model.managed("org.frombom:bomdep").version);
    }

    @Test
    void keepsExclusionsOptionalAndScopes() {
        String pom = ""
                + "<project>"
                + "<groupId>g</groupId><artifactId>a</artifactId>"
                + "<version>1</version>"
                + "<packaging>aar</packaging>"
                + "<dependencies>"
                + "  <dependency>"
                + "    <groupId>org.x</groupId>"
                + "    <artifactId>api</artifactId>"
                + "    <version>1.0</version>"
                + "    <scope>provided</scope>"
                + "    <optional>true</optional>"
                + "    <exclusions>"
                + "      <exclusion>"
                + "        <groupId>org.y</groupId>"
                + "        <artifactId>trans</artifactId>"
                + "      </exclusion>"
                + "    </exclusions>"
                + "  </dependency>"
                + "  <dependency>"
                + "    <groupId>org.x</groupId>"
                + "    <artifactId>runtime</artifactId>"
                + "    <version>1.1</version>"
                + "    <scope>runtime</scope>"
                + "  </dependency>"
                + "  <dependency>"
                + "    <groupId>org.x</groupId>"
                + "    <artifactId>test</artifactId>"
                + "    <version>1.2</version>"
                + "    <scope>test</scope>"
                + "  </dependency>"
                + "</dependencies>"
                + "</project>";
        PomParser.Result r = parser(REPO).parse(pom);
        assertTrue(r.problems.isEmpty(), () -> r.problems.toString());
        assertEquals("aar", r.model.packaging);
        assertEquals(3, r.model.dependencies.size());
        PomModel.PomDependency api = r.model.dependencies.get(0);
        assertEquals(PomModel.Scope.PROVIDED, api.scope);
        assertTrue(api.optional);
        assertTrue(api.exclusions.contains("org.y:trans"));
        assertEquals(PomModel.Scope.RUNTIME,
                r.model.dependencies.get(1).scope);
        assertEquals(PomModel.Scope.TEST, r.model.dependencies.get(2).scope);
    }

    @Test
    void interpolatesOwnPropertiesAndBuiltins() {
        String pom = ""
                + "<project>"
                + "<groupId>g</groupId><artifactId>a</artifactId>"
                + "<version>1</version>"
                + "<properties>"
                + "  <v>2.1</v>"
                + "  <who>${project.artifactId}</who>"
                + "</properties>"
                + "<dependencies>"
                + "  <dependency>"
                + "    <groupId>org.z</groupId>"
                + "    <artifactId>dep</artifactId>"
                + "    <version>${v}</version>"
                + "    <classifier>${who}</classifier>"
                + "  </dependency>"
                + "</dependencies>"
                + "</project>";
        PomParser.Result r = parser(REPO).parse(pom);
        assertTrue(r.problems.isEmpty(), () -> r.problems.toString());
        assertEquals("2.1", r.model.dependencies.get(0).version);
        assertEquals("a", r.model.dependencies.get(0).classifier);
        assertEquals("a", r.model.properties.get("who"));
    }

    @Test
    void reportsMissingParentAndBrokenDocuments() {
        String orphan = ""
                + "<project>"
                + "<parent>"
                + "  <groupId>nope</groupId>"
                + "  <artifactId>ghost</artifactId>"
                + "  <version>1</version>"
                + "</parent>"
                + "<artifactId>a</artifactId>"
                + "</project>";
        PomParser.Result r = parser(REPO).parse(orphan);
        assertTrue(!r.problems.isEmpty());
        // groupId manquant (parent introuvable) : modèle refusé.
        assertNull(r.model);

        PomParser.Result broken = parser(REPO).parse("<project><groupId>");
        assertNull(broken.model);
        assertTrue(!broken.problems.isEmpty());
    }

    @Test
    void fetchFailureIsAProblemNotAnException() throws IOException {
        PomParser throwing = new PomParser((g, a, v) -> {
            throw new IOException("network down");
        });
        String pom = ""
                + "<project>"
                + "<parent>"
                + "  <groupId>g</groupId>"
                + "  <artifactId>p</artifactId>"
                + "  <version>1</version>"
                + "</parent>"
                + "<artifactId>a</artifactId>"
                + "</project>";
        PomParser.Result r = throwing.parse(pom);
        assertTrue(!r.problems.isEmpty());
        assertNull(r.model);
    }
}
