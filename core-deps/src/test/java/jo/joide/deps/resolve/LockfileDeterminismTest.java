package jo.joide.deps.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import jo.joide.deps.DependencyRequest;
import jo.joide.deps.Exposure;
import jo.joide.deps.VersionRequirement;
import jo.joide.deps.cache.DependencyCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Preuve ADR-007 : le lockfile est identique octet pour octet sur
 * deux résolutions indépendantes (caches distincts, lockfiles
 * distincts), et une résolution avec lockfile valide ne fait
 * AUCUNE requête réseau.
 */
class LockfileDeterminismTest {

    @TempDir
    Path tmp;

    private void populate(RepoFixture fixture) throws IOException {
        // Une pyramide : lib -> api, run ; api -> base ; run -> base2.
        artifact(fixture, "org.dep:base", "1.0", null);
        artifact(fixture, "org.dep:base2", "1.2", null);
        artifact(fixture, "org.dep:api", "2.0",
                "<dependency><groupId>org.dep</groupId>"
                        + "<artifactId>base</artifactId><version>1.0</version>"
                        + "</dependency>");
        artifact(fixture, "org.dep:run", "2.1",
                "<dependency><groupId>org.dep</groupId>"
                        + "<artifactId>base2</artifactId>"
                        + "<version>1.2</version><scope>runtime</scope>"
                        + "</dependency>");
        artifact(fixture, "org.example:lib", "1.0",
                "<dependency><groupId>org.dep</groupId>"
                        + "<artifactId>api</artifactId><version>2.0</version>"
                        + "</dependency>"
                        + "<dependency><groupId>org.dep</groupId>"
                        + "<artifactId>run</artifactId><version>2.1</version>"
                        + "<scope>runtime</scope></dependency>");
    }

    /** Écrit un artefact complet : POM + jar avec checksums. */
    private void artifact(RepoFixture fixture, String ga, String v,
                          String depsXml) throws IOException {
        String g = ga.split(":")[0];
        String a = ga.split(":")[1];
        String pom = "<project><groupId>" + g + "</groupId>"
                + "<artifactId>" + a + "</artifactId><version>" + v
                + "</version>"
                + (depsXml == null ? "" : "<dependencies>" + depsXml
                + "</dependencies>")
                + "</project>";
        fixture.pom(g, a, v, pom);
        fixture.artifact(g, a, v, "jar", RepoFixture.jarBytes(ga + ":" + v));
    }

    @Test
    void twoIndependentResolutionsGiveIdenticalLockfiles() throws IOException {
        // Résolution 1 : cache vierge, sans lockfile préexistant.
        RepoFixture fixture1 = RepoFixture.create(tmp.resolve("repo1"));
        populate(fixture1);
        Path lock1 = tmp.resolve("a/joide.lock");
        Files.createDirectories(lock1.getParent());
        ResolutionResult first = new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture1.toRemote("central"))
                        .cache(new DependencyCache(tmp.resolve("cache1")))
                        .lockfile(lock1)
                        .maxParallelDownloads(2)
                        .build())
                .resolve(List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("1.0"), Exposure.API)));
        assertTrue(first.success(), () -> first.problems.toString());
        assertTrue(first.lockfileRewritten);
        String text1 = Files.readString(lock1);

        // Résolution 2 : tout autre répertoire, mêmes entrées.
        RepoFixture fixture2 = RepoFixture.create(tmp.resolve("repo2"));
        populate(fixture2);
        Path lock2 = tmp.resolve("b/joide.lock");
        Files.createDirectories(lock2.getParent());
        ResolutionResult second = new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture2.toRemote("central"))
                        .cache(new DependencyCache(tmp.resolve("cache2")))
                        .lockfile(lock2)
                        .maxParallelDownloads(3)
                        .build())
                .resolve(List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("1.0"), Exposure.API)));
        assertTrue(second.success(), () -> second.problems.toString());
        String text2 = Files.readString(lock2);

        // Octet pour octet — preuve de l'ADR-007.
        assertEquals(text1, text2, "lockfiles differ");
        byte[] b1 = Files.readAllBytes(lock1);
        byte[] b2 = Files.readAllBytes(lock2);
        assertTrue(java.util.Arrays.equals(b1, b2));
        // Le lockfile contient les cinq artefacts, l'ordre est trié.
        assertEquals(5, second.artifacts.size());
        assertTrue(text1.startsWith("joide.lock v1\n"));
    }

    @Test
    void validLockfileMeansZeroNetworkRequests() throws IOException {
        // Résolution initiale qui pose le lockfile et remplit le cache.
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        populate(fixture);
        Path lock = tmp.resolve("lock/joide.lock");
        Files.createDirectories(lock.getParent());
        DependencyCache cache = new DependencyCache(tmp.resolve("cache"));
        Resolver bootstrap = new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture.toRemote("central"))
                        .cache(cache).lockfile(lock).build());
        ResolutionResult first = bootstrap.resolve(List.of(
                new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("1.0"), Exposure.API)));
        assertTrue(first.success(), () -> first.problems.toString());
        int requestsSoFar = fixture.transport.callCount();
        assertTrue(requestsSoFar > 0);

        // Résolution suivante : le transport désormais hostile —
        // chaque requête serait un échec. Le lockfile doit couvrir.
        fixture.transport.failAll("réseau interdit en test");
        ResolutionResult warm = new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture.toRemote("central"))
                        .cache(cache).lockfile(lock).build())
                .resolve(List.of(new DependencyRequest(
                        "org.example", "lib", VersionRequirement.parse("1.0"),
                        Exposure.API)));
        assertTrue(warm.success(), () -> warm.problems.toString());
        assertTrue(warm.lockfileFollowed);
        assertEquals(0, warm.stats.requests);
        // Aucune nouvelle requête depuis l'hostilité du transport.
        assertEquals(requestsSoFar, fixture.transport.callCount());
        // Et le lockfile n'a pas été réécrit.
        assertTrue(!warm.lockfileRewritten);
        // Les artefacts redonnés sont les mêmes, empreintes comprises.
        assertEquals(first.artifacts.size(), warm.artifacts.size());
        for (int i = 0; i < warm.artifacts.size(); i++) {
            assertEquals(first.artifacts.get(i).sha256,
                    warm.artifacts.get(i).sha256);
            assertEquals(first.artifacts.get(i).coordinate.toString(),
                    warm.artifacts.get(i).coordinate.toString());
        }
    }
}
