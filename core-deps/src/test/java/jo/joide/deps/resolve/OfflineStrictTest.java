package jo.joide.deps.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import jo.joide.deps.DependencyRequest;
import jo.joide.deps.Exposure;
import jo.joide.deps.VersionRequirement;
import jo.joide.deps.cache.DependencyCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Hors-ligne strict (ADR-007) : aucune requête réseau n'est tentée ;
 * ce qui manque au cache/lockfile est un échec explicite.
 */
class OfflineStrictTest {

    @TempDir
    Path tmp;

    @Test
    void offlineWithNothingCachedFailsExplicitlyWithoutNetwork()
            throws IOException {
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        // Le dépôt contient l'artefact, mais le transport est compté :
        // en mode hors-ligne strict, aucune requête ne doit partir.
        new Artifacts().write(fixture, "org.example:lib", "1.0");
        fixture.transport.failAll("requête tentée en mode hors-ligne");

        ResolutionResult result = new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture.toRemote("central"))
                        .cache(new DependencyCache(tmp.resolve("cache")))
                        .offlineStrict()
                        .build())
                .resolve(List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("1.0"), Exposure.API)));
        assertFalse(result.success());
        assertTrue(result.problems.stream().anyMatch(p -> p.isError()
                        && p.message().contains("hors-ligne strict")),
                () -> result.problems.toString());
        // Aucune requête tentée, même pour chercher.
        assertEquals(0, result.stats.requests);
        assertEquals(0, fixture.transport.callCount());
    }

    @Test
    void offlineWithValidLockfileStillWorks() throws IOException {
        // Une première résolution en ligne pose le lockfile et le cache.
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        new Artifacts().write(fixture, "org.example:lib", "1.0");
        Path lock = tmp.resolve("lock/joide.lock");
        DependencyCache cache = new DependencyCache(tmp.resolve("cache"));
        assertTrue(new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture.toRemote("central"))
                        .cache(cache).lockfile(lock).build())
                .resolve(List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("1.0"), Exposure.API)))
                .success());

        // Hors-ligne strict : le lockfile valide couvre, zéro requête.
        fixture.transport.failAll("requête tentée en mode hors-ligne");
        ResolutionResult warm = new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture.toRemote("central"))
                        .cache(cache).lockfile(lock)
                        .offlineStrict().build())
                .resolve(List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("1.0"), Exposure.API)));
        assertTrue(warm.success(), () -> warm.problems.toString());
        assertTrue(warm.lockfileFollowed);
        assertEquals(0, warm.stats.requests);
        assertEquals(1, warm.artifacts.size());
    }

    @Test
    void offlineWithLockfileButEmptyCacheFailsExplicitly() throws IOException {
        // Le lockfile existe, mais le cache a été vidé : en hors-ligne
        // strict, on ne va pas le chercher au dépôt.
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        new Artifacts().write(fixture, "org.example:lib", "1.0");
        Path lock = tmp.resolve("lock/joide.lock");
        DependencyCache warmCache = new DependencyCache(tmp.resolve("cache1"));
        assertTrue(new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture.toRemote("central"))
                        .cache(warmCache).lockfile(lock).build())
                .resolve(List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("1.0"), Exposure.API)))
                .success());
        DependencyCache emptyCache = new DependencyCache(tmp.resolve("cache2"));
        int baseline = fixture.transport.callCount();

        fixture.transport.failAll("requête tentée en mode hors-ligne");
        ResolutionResult result = new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture.toRemote("central"))
                        .cache(emptyCache).lockfile(lock)
                        .offlineStrict().build())
                .resolve(List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("1.0"), Exposure.API)));
        assertFalse(result.success());
        assertTrue(result.problems.stream().anyMatch(p -> p.isError()
                && p.message().contains("hors-ligne strict")));
        // Aucune requête AU-DELÀ de la base de référence.
        assertEquals(baseline, fixture.transport.callCount());
        assertEquals(result.stats.requests, 0);
    }
}
