package jo.joide.deps.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import jo.joide.deps.Checksums;
import jo.joide.deps.Coordinate;
import jo.joide.deps.DependencyRequest;
import jo.joide.deps.Exposure;
import jo.joide.deps.VersionRequirement;
import jo.joide.deps.cache.DependencyCache;
import jo.joide.deps.lock.EdgeKind;
import jo.joide.deps.lock.LockfileEdge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Résolution : fermeture transitive, portées POM, exclusions,
 * nomenclatures, conflits « la plus récente gagne », strictement
 * GMM, variantes avec renvoi available-at.
 */
class ResolverTest {

    @TempDir
    Path tmp;

    private DependencyCache newCache() throws IOException {
        return new DependencyCache(tmp.resolve("cache"));
    }

    private ResolverConfig.Builder config(RepoFixture fixture)
            throws IOException {
        return new ResolverConfig.Builder()
                .repository(fixture.toRemote("test"))
                .cache(newCache())
                .maxParallelDownloads(2);
    }

    private String pomOf(String ga, String version, String deps) {
        return "<project><groupId>" + ga.split(":")[0] + "</groupId>"
                + "<artifactId>" + ga.split(":")[1] + "</artifactId>"
                + "<version>" + version + "</version>"
                + (deps == null ? "" : "<dependencies>" + deps
                + "</dependencies>")
                + "</project>";
    }

    private String dep(String ga, String v, String scope) {
        return "<dependency><groupId>" + ga.split(":")[0] + "</groupId>"
                + "<artifactId>" + ga.split(":")[1] + "</artifactId>"
                + "<version>" + v + "</version>"
                + (scope == null ? "" : "<scope>" + scope + "</scope>")
                + "</dependency>";
    }

    /** Écrit un artefact complet : POM seul + jar avec checksums. */
    private void artifact(RepoFixture f, String ga, String v, String depsXml)
            throws IOException {
        String g = ga.split(":")[0];
        String a = ga.split(":")[1];
        f.pom(g, a, v, pomOf(ga, v, depsXml));
        f.artifact(g, a, v, "jar", RepoFixture.jarBytes(ga + ":" + v));
    }

    @Test
    void pomOnlyClosureFollowsScopes() throws IOException {
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        artifact(fixture, "org.dep:api", "2.0", null);
        artifact(fixture, "org.dep:run", "2.1", null);
        artifact(fixture, "org.dep:prov", "2.2", null);
        artifact(fixture, "org.dep:opt", "2.3", null);
        artifact(fixture, "org.dep:tst", "3.0", null);
        String optionalDep = "<dependency><groupId>org.dep</groupId>"
                + "<artifactId>opt</artifactId><version>2.3</version>"
                + "<optional>true</optional></dependency>";
        String libDeps = dep("org.dep:api", "2.0", null)
                + dep("org.dep:run", "2.1", "runtime")
                + dep("org.dep:prov", "2.2", "provided")
                + optionalDep
                + dep("org.dep:tst", "3.0", "test");
        artifact(fixture, "org.example:lib", "1.0", libDeps);

        ResolutionResult result = new Resolver(config(fixture).build()).resolve(
                List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("1.0"), Exposure.API)));
        assertTrue(result.success(), () -> result.problems.toString());
        assertEquals(4, result.artifacts.size());
        // Les genres d'arête suivent les portées Maven.
        ResolvedArtifact lib = result.artifacts.stream()
                .filter(x -> x.ga().equals("org.example:lib")).findFirst()
                .orElseThrow();
        assertEquals(3, lib.edges.size());
        for (LockfileEdge edge : lib.edges) {
            if (edge.target.artifactId().equals("api")) {
                assertEquals(EdgeKind.API, edge.kind);
            } else if (edge.target.artifactId().equals("run")) {
                assertEquals(EdgeKind.RUNTIME, edge.kind);
            } else if (edge.target.artifactId().equals("prov")) {
                assertEquals(EdgeKind.COMPILE, edge.kind);
            }
        }
        // L'optionnelle et la test n'entrent jamais.
        for (ResolvedArtifact a : result.artifacts) {
            assertFalse("opt".equals(a.coordinate.artifactId()));
            assertFalse("tst".equals(a.coordinate.artifactId()));
        }
    }

    @Test
    void gmmVariantSelectionWithAvailableAt() throws IOException {
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        // Le module référencé par le renvoi, DANS LE MÊME RÉPERTOIRE
        // DE VERSION (c'est la sémantique d'available-at).
        // L'AAR est un vrai zip : manifeste + classes.jar.
        byte[] aar = aarZip();
        String referenced = "{"
                + "\"formatVersion\": \"1.0\","
                + "\"variants\": ["
                + "  {\"name\": \"releaseApiElements\","
                + "   \"attributes\": {\"org.gradle.usage\": \"java-api\","
                + "     \"org.jetbrains.kotlin.platform.type\": \"androidjvm\"},"
                + "   \"files\": [{\"name\": \"ui-1.0.aar\", \"url\": \"ui-1.0.aar\","
                + "     \"size\": " + aar.length + ","
                + "     \"checksums\": {\"sha256\": \"" + Checksums.sha256Hex(aar)
                + "\"}}],"
                + "   \"dependencies\": ["
                + "     {\"group\": \"org.dep\", \"module\": \"core\","
                + "      \"version\": \"2.0\"}"
                + "   ]},"
                + "  {\"name\": \"releaseRuntimeElements\","
                + "   \"attributes\": {\"org.gradle.usage\": \"java-runtime\","
                + "     \"org.jetbrains.kotlin.platform.type\": \"androidjvm\"},"
                + "   \"files\": [{\"name\": \"ui-1.0.aar\", \"url\": \"ui-1.0.aar\","
                + "     \"size\": " + aar.length + ","
                + "     \"checksums\": {\"sha256\": \"" + Checksums.sha256Hex(aar)
                + "\"}}],"
                + "   \"dependencies\": ["
                + "     {\"group\": \"org.dep\", \"module\": \"core\","
                + "      \"version\": \"2.0\"},"
                + "     {\"group\": \"org.dep\", \"module\": \"extruntime\","
                + "      \"version\": \"1.0\"}"
                + "   ]}"
                + "]}";
        fixture.writeText("org/example/ui/1.0/ui-1.0-referenced.module",
                referenced);
        // La racine : variantes renvoi vers le module référencé.
        String root = "{"
                + "\"formatVersion\": \"1.0\","
                + "\"variants\": ["
                + "  {\"name\": \"releaseApiElements\","
                + "   \"attributes\": {\"org.gradle.usage\": \"java-api\","
                + "     \"org.jetbrains.kotlin.platform.type\": \"androidjvm\"},"
                + "   \"available-at\": {\"url\": \"ui-1.0-referenced.module\"}},"
                + "  {\"name\": \"releaseRuntimeElements\","
                + "   \"attributes\": {\"org.gradle.usage\": \"java-runtime\","
                + "     \"org.jetbrains.kotlin.platform.type\": \"androidjvm\"},"
                + "   \"available-at\": {\"url\": \"ui-1.0-referenced.module\"}}"
                + "]}";
        fixture.gmm("org.example", "ui", "1.0", root);
        fixture.write("org/example/ui/1.0/ui-1.0.aar", aar);
        fixture.writeText("org/example/ui/1.0/ui-1.0.aar.sha256",
                Checksums.sha256Hex(aar) + "\n");
        artifact(fixture, "org.dep:core", "2.0", null);
        artifact(fixture, "org.dep:extruntime", "1.0", null);

        ResolutionResult result = new Resolver(config(fixture).build()).resolve(
                List.of(new DependencyRequest("org.example", "ui",
                        VersionRequirement.parse("1.0"), Exposure.API)));
        assertTrue(result.success(), () -> result.problems.toString());
        // ui (aar) + core (api) + extruntime (runtime).
        assertEquals(3, result.artifacts.size());
        ResolvedArtifact ui = result.artifacts.stream()
                .filter(x -> x.ga().equals("org.example:ui")).findFirst()
                .orElseThrow();
        assertTrue(ui.aar);
        assertNotNull(ui.aarContents);
        assertTrue(java.nio.file.Files.isRegularFile(ui.aarContents.classesJar));
        assertEquals("aar", ui.coordinate.extension());
        // Les dépendances viennent du module référencé, la variante
        // runtime ajoute extruntime.
        assertEquals(2, ui.edges.size());
        LockfileEdge coreEdge = ui.edges.stream()
                .filter(e -> "core".equals(e.target.artifactId()))
                .findFirst().orElseThrow();
        assertEquals(EdgeKind.API, coreEdge.kind);
        LockfileEdge runtimeEdge = ui.edges.stream()
                .filter(e -> "extruntime".equals(e.target.artifactId()))
                .findFirst().orElseThrow();
        assertEquals(EdgeKind.RUNTIME, runtimeEdge.kind);
    }

    /** Un AAR factice mais VRAI zip : manifeste, classes.jar, libs. */
    private static byte[] aarZip() throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip =
                new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("AndroidManifest.xml"));
            zip.write("<manifest/>".getBytes());
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("classes.jar"));
            zip.write("bytecode factice".getBytes());
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("libs/x.jar"));
            zip.write("x".getBytes());
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    void newestWinsOnConflictingPaths() throws IOException {
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        fixture.artifactMetadata("org.conflict", "lib", "<metadata>"
                + "<versioning><versions>"
                + "<version>1.0</version><version>1.5</version>"
                + "<version>1.9</version>"
                + "</versions></versioning></metadata>");
        artifact(fixture, "org.conflict:lib", "1.9", null);
        artifact(fixture, "org.conflict:lib", "1.5", null);
        artifact(fixture, "org.conflict:lib", "1.0", null);
        artifact(fixture, "app:first", "1.0",
                dep("org.conflict:lib", "[1.0,2.0)", null));
        artifact(fixture, "app:second", "1.0",
                dep("org.conflict:lib", "1.5", null));

        ResolutionResult result = new Resolver(config(fixture).build()).resolve(
                List.of(new DependencyRequest("app", "first",
                                VersionRequirement.parse("1.0"), Exposure.API),
                        new DependencyRequest("app", "second",
                                VersionRequirement.parse("1.0"), Exposure.API)));
        assertTrue(result.success(), () -> result.problems.toString());
        ResolvedArtifact lib = result.artifacts.stream()
                .filter(x -> x.ga().equals("org.conflict:lib")).findFirst()
                .orElseThrow();
        assertEquals("1.9", lib.coordinate.version().raw());
    }

    @Test
    void bomPinsVersionsOfUnversionedDependencies() throws IOException {
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        String libPom = "<project>"
                + "<groupId>org.example</groupId>"
                + "<artifactId>lib</artifactId><version>1.0</version>"
                + "<dependencyManagement><dependencies>"
                + "<dependency><groupId>org.dep</groupId>"
                + "<artifactId>trans</artifactId><version>2.0</version>"
                + "</dependency></dependencies></dependencyManagement>"
                + "<dependencies>"
                + "<dependency><groupId>org.dep</groupId>"
                + "<artifactId>trans</artifactId>"
                + "</dependency>"
                + "</dependencies></project>";
        fixture.pom("org.example", "lib", "1.0", libPom);
        fixture.artifact("org.example", "lib", "1.0", "jar",
                RepoFixture.jarBytes("lib"));
        artifact(fixture, "org.dep:trans", "2.0", null);

        ResolutionResult result = new Resolver(config(fixture).build()).resolve(
                List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("1.0"), Exposure.API)));
        assertTrue(result.success(), () -> result.problems.toString());
        ResolvedArtifact trans = result.artifacts.stream()
                .filter(x -> x.ga().equals("org.dep:trans")).findFirst()
                .orElseThrow();
        assertEquals("2.0", trans.coordinate.version().raw());
    }

    @Test
    void strictlyGmmImposesItsVersion() throws IOException {
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        fixture.artifactMetadata("org.strict", "lib", "<metadata>"
                + "<versioning><versions>"
                + "<version>1.2</version><version>2.0</version>"
                + "</versions></versioning></metadata>");
        artifact(fixture, "org.strict:lib", "1.2", null);
        artifact(fixture, "org.strict:lib", "2.0", null);
        // app:1.0 — GMM avec une dépendance strictly 1.2.
        byte[] appJar = RepoFixture.jarBytes("app");
        String appGmm = "{"
                + "\"formatVersion\": \"1.0\",\"variants\": ["
                + "{\"name\": \"apiElements\","
                + " \"attributes\": {\"org.gradle.usage\": \"java-api\"},"
                + " \"files\": [{\"name\": \"app-1.0.jar\", \"url\": \"app-1.0.jar\","
                + "   \"size\": " + appJar.length + "}],"
                + " \"dependencies\": ["
                + "  {\"group\": \"org.strict\", \"module\": \"lib\","
                + "   \"version\": {\"strictly\": \"1.2\"}}"
                + " ]}]}";
        fixture.gmm("app", "strictapp", "1.0", appGmm);
        fixture.artifact("app", "strictapp", "1.0", "jar", appJar);
        // other:1.0 — POM avec lib 2.0 exacte.
        artifact(fixture, "app:other", "1.0",
                dep("org.strict:lib", "2.0", null));

        ResolutionResult result = new Resolver(config(fixture).build()).resolve(
                List.of(new DependencyRequest("app", "strictapp",
                                VersionRequirement.parse("1.0"), Exposure.API),
                        new DependencyRequest("app", "other",
                                VersionRequirement.parse("1.0"), Exposure.API)));
        assertTrue(result.success(), () -> result.problems.toString());
        ResolvedArtifact lib = result.artifacts.stream()
                .filter(x -> x.ga().equals("org.strict:lib")).findFirst()
                .orElseThrow();
        assertEquals("1.2", lib.coordinate.version().raw());
    }

    @Test
    void aBomCannotBeAResolvedRoot() throws IOException {
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        fixture.pom("org.example", "bom", "1.0", "<project>"
                + "<groupId>org.example</groupId><artifactId>bom</artifactId>"
                + "<version>1.0</version><packaging>pom</packaging>"
                + "</project>");

        ResolutionResult result = new Resolver(config(fixture).build()).resolve(
                List.of(new DependencyRequest("org.example", "bom",
                        VersionRequirement.parse("1.0"), Exposure.API)));
        assertFalse(result.success());
        assertTrue(result.problems.stream().anyMatch(
                p -> p.isError() && p.message().contains("nomenclature")));
    }

    @Test
    void missingArtifactIsAnExplicitError() throws IOException {
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        ResolutionResult result = new Resolver(config(fixture).build()).resolve(
                List.of(new DependencyRequest("org.ghost", "lib",
                        VersionRequirement.parse("1.0"), Exposure.API)));
        assertFalse(result.success());
        assertTrue(result.problems.stream().anyMatch(p -> p.isError()));
        assertNull(result.artifacts.stream()
                .filter(x -> x.ga().equals("org.ghost:lib")).findFirst()
                .orElse(null));
    }

    @Test
    void exclusionsAmputateTheDeclaringSubtree() throws IOException {
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        // lib:1.0 dépend de mid:1.0 (compile) qui dépend de evil:1.0.
        artifact(fixture, "org.dep:evil", "1.0", null);
        artifact(fixture, "org.dep:mid", "1.0",
                dep("org.dep:evil", "1.0", null));
        String libDeps = "<dependency><groupId>org.dep</groupId>"
                + "<artifactId>mid</artifactId><version>1.0</version>"
                + "<exclusions><exclusion><groupId>org.dep</groupId>"
                + "<artifactId>evil</artifactId></exclusion></exclusions>"
                + "</dependency>";
        artifact(fixture, "org.example:lib", "1.0", libDeps);

        ResolutionResult result = new Resolver(config(fixture).build()).resolve(
                List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("1.0"), Exposure.API)));
        assertTrue(result.success(), () -> result.problems.toString());
        // mid entre, jamais evil.
        assertEquals(2, result.artifacts.size());
        for (ResolvedArtifact a : result.artifacts) {
            assertFalse("evil".equals(a.coordinate.artifactId()));
        }
        assertNotNull(result.artifacts.stream()
                .filter(x -> x.coordinate.artifactId().equals("mid"))
                .findFirst().orElseThrow());
    }
}
