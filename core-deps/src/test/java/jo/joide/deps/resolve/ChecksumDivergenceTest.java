package jo.joide.deps.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import jo.joide.deps.Checksums;
import jo.joide.deps.DependencyRequest;
import jo.joide.deps.Exposure;
import jo.joide.deps.VersionRequirement;
import jo.joide.deps.cache.DependencyCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Vérification des checksums : .sha256 divergent = échec explicite,
 * .sha1 seul accepté s'il concorde, GMM incorporée fausse = échec,
 * aucune source publiée = accepté avec l'empreinte calculée au
 * lockfile (consigné comme tel).
 */
class ChecksumDivergenceTest {

    @TempDir
    Path tmp;

    private static String pom(String g, String a) {
        return "<project><groupId>" + g + "</groupId><artifactId>" + a
                + "</artifactId><version>1.0</version></project>";
    }

    private DependencyCache cache() throws IOException {
        return new DependencyCache(tmp.resolve("cache"));
    }

    @Test
    void divergentPublishedSha256FailsExplicitly() throws IOException {
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        byte[] good = RepoFixture.jarBytes("good");
        fixture.pom("org.example", "lib", "1.0", pom("org.example", "lib"));
        fixture.artifactWithBadSha256("org.example", "lib", "1.0", "jar",
                good);

        ResolutionResult result = new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture.toRemote("bad"))
                        .cache(cache()).build())
                .resolve(List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("1.0"), Exposure.API)));
        assertFalse(result.success());
        assertTrue(result.problems.stream().anyMatch(p -> p.isError()
                && p.message().contains("≠")), () -> result.problems.toString());
        // Rien n'entre au cache.
        assertEquals(0, result.artifacts.size());
    }

    @Test
    void sha1OnlyIsAcceptedWhenItMatches() throws IOException {
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        byte[] body = RepoFixture.jarBytes("sha1-only");
        String g = "org.example";
        String a = "lib";
        fixture.pom(g, a, "1.0", pom(g, a));
        String relPath = jo.joide.deps.repo.Layout.artifactPath(g, a, "1.0",
                null, "jar");
        fixture.write(relPath, body);
        // Pas de .sha256 ; un .sha1 concordant.
        fixture.writeText(relPath + ".sha1", Checksums.sha1Hex(body) + "\n");

        ResolutionResult result = new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture.toRemote("central"))
                        .cache(cache()).build())
                .resolve(List.of(new DependencyRequest(g, a,
                        VersionRequirement.parse("1.0"), Exposure.API)));
        assertTrue(result.success(), () -> result.problems.toString());
        assertEquals(1, result.artifacts.size());
        assertEquals(Checksums.sha256Hex(body),
                result.artifacts.get(0).sha256);
    }

    @Test
    void divergentGmmEmbeddedChecksumFails() throws IOException {
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        byte[] body = RepoFixture.jarBytes("gmm-embedded");
        String wrong = new StringBuilder(Checksums.sha256Hex(body)).reverse()
                .toString();
        String gmm = "{"
                + "\"formatVersion\": \"1.0\",\"variants\": ["
                + "{\"name\": \"apiElements\","
                + " \"attributes\": {\"org.gradle.usage\": \"java-api\"},"
                + " \"files\": [{\"name\": \"lib-1.0.jar\","
                + " \"url\": \"lib-1.0.jar\", \"size\": " + body.length + ","
                + " \"checksums\": {\"sha256\": \"" + wrong + "\"}}]}]}";
        fixture.gmm("org.example", "lib", "1.0", gmm);
        fixture.pom("org.example", "lib", "1.0", pom("org.example", "lib"));
        fixture.write(jo.joide.deps.repo.Layout.artifactPath("org.example",
                "lib", "1.0", null, "jar"), body);
        // Pas de checksum publié côté dépôt : seule la GMM fait foi.

        ResolutionResult result = new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture.toRemote("central"))
                        .cache(cache()).build())
                .resolve(List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("1.0"), Exposure.API)));
        assertFalse(result.success());
        assertTrue(result.problems.stream().anyMatch(p -> p.isError()
                && p.message().contains("≠")), () -> result.problems.toString());
    }

    @Test
    void noPublishedChecksumIsAcceptedWithComputedOne() throws IOException {
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        byte[] body = RepoFixture.jarBytes("no-checksum");
        fixture.pom("org.example", "lib", "1.0", pom("org.example", "lib"));
        fixture.artifactWithoutChecksums("org.example", "lib", "1.0", "jar",
                body);

        ResolutionResult result = new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture.toRemote("central"))
                        .cache(new DependencyCache(tmp.resolve("cache2")))
                        .lockfile(tmp.resolve("lock/joide.lock"))
                        .build())
                .resolve(List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("1.0"), Exposure.API)));
        assertTrue(result.success(), () -> result.problems.toString());
        assertEquals(Checksums.sha256Hex(body),
                result.artifacts.get(0).sha256);
        // Le lockfile porte l'empreinte calculée : la preuve suivante
        // (zéro réseau) tiendra malgré l'absence de source publiée.
        fixture.transport.failAll("réseau interdit");
        ResolutionResult warm = new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture.toRemote("central"))
                        .cache(new DependencyCache(tmp.resolve("cache2")))
                        .lockfile(tmp.resolve("lock/joide.lock"))
                        .build())
                .resolve(List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("1.0"), Exposure.API)));
        assertTrue(warm.success(), () -> warm.problems.toString());
        assertTrue(warm.lockfileFollowed);
        assertEquals(0, warm.stats.requests);
    }
}
