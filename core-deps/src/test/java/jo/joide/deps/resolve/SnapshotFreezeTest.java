package jo.joide.deps.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * Gel des versions dynamiques et des instantanés (ADR-007) : un
 * instantané littéral se résout en sa forme concrète, le lockfile la
 * fige — même si le dépôt avance ensuite, la résolution chaude ne
 * bouge plus ; sans lockfile, la nouvelle forme concrète est prise.
 */
class SnapshotFreezeTest {

    @TempDir
    Path tmp;

    private static final String POM = "<project>"
            + "<groupId>org.example</groupId><artifactId>lib</artifactId>"
            + "<version>1.0-SNAPSHOT</version></project>";

    /** Écrit l'instantané pour l'état d'horodatage donné. */
    private void writeSnapshot(RepoFixture fixture, String timestamp,
                               String build) throws IOException {
        String concrete = "1.0-" + timestamp + "-" + build;
        // Métadonnées de version : état d'instantané.
        fixture.versionMetadata("org.example", "lib", "1.0-SNAPSHOT",
                "<metadata><groupId>org.example</groupId>"
                        + "<artifactId>lib</artifactId>"
                        + "<version>1.0-SNAPSHOT</version>"
                        + "<versioning><snapshot>"
                        + "<timestamp>" + timestamp + "</timestamp>"
                        + "<buildNumber>" + build + "</buildNumber>"
                        + "</snapshot><snapshotVersions>"
                        + "<snapshotVersion><extension>jar</extension>"
                        + "<value>" + concrete + "</value></snapshotVersion>"
                        + "</snapshotVersions></versioning></metadata>");
        // POM et artefact sous la forme concrète.
        fixture.writeText(jo.joide.deps.repo.Layout.pomPath("org.example",
                "lib", concrete), POM);
        byte[] body = RepoFixture.jarBytes("snapshot-" + concrete);
        String relPath = jo.joide.deps.repo.Layout.artifactPath("org.example",
                "lib", concrete, null, "jar");
        fixture.write(relPath, body);
        fixture.writeText(relPath + ".sha256",
                jo.joide.deps.Checksums.sha256Hex(body) + "\n");
    }

    @Test
    void snapshotResolvesToConcreteThenFreezesDespiteUpstreamMovement()
            throws IOException {
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        writeSnapshot(fixture, "20260115.142030", "7");
        Path lock = tmp.resolve("lock/joide.lock");
        DependencyCache cache = new DependencyCache(tmp.resolve("cache"));

        // Première résolution : la forme concrète entre au lockfile.
        ResolutionResult first = new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture.toRemote("central"))
                        .cache(cache).lockfile(lock).build())
                .resolve(List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("1.0-SNAPSHOT"),
                        Exposure.API)));
        assertTrue(first.success(), () -> first.problems.toString());
        assertEquals(1, first.artifacts.size());
        assertEquals("1.0-20260115.142030-7",
                first.artifacts.get(0).coordinate.version().raw());
        String lockText = Files.readString(lock);
        assertTrue(lockText.contains("org.example:lib:1.0-20260115.142030-7"));

        // Le dépôt avance : nouvel horodatage, nouveau build.
        writeSnapshot(fixture, "20260201.091500", "8");

        // Résolution chaude : gelée sur l'ancienne forme, zéro requête.
        int callsBefore = fixture.transport.callCount();
        ResolutionResult warm = new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture.toRemote("central"))
                        .cache(cache).lockfile(lock).build())
                .resolve(List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("1.0-SNAPSHOT"),
                        Exposure.API)));
        assertTrue(warm.success(), () -> warm.problems.toString());
        assertTrue(warm.lockfileFollowed);
        assertEquals(0, warm.stats.requests);
        assertEquals(callsBefore, fixture.transport.callCount());
        assertEquals("1.0-20260115.142030-7",
                warm.artifacts.get(0).coordinate.version().raw());
        // Le lockfile n'a pas bougé.
        assertEquals(lockText, Files.readString(lock));

        // Sans lockfile : la nouvelle forme concrète est prise.
        Files.delete(lock);
        ResolutionResult fresh = new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture.toRemote("central"))
                        .cache(cache).lockfile(lock).build())
                .resolve(List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("1.0-SNAPSHOT"),
                        Exposure.API)));
        assertTrue(fresh.success(), () -> fresh.problems.toString());
        assertEquals("1.0-20260201.091500-8",
                fresh.artifacts.get(0).coordinate.version().raw());
        assertNotEquals(lockText, Files.readString(lock));
    }

    @Test
    void dynamicRangeRootIsFrozenByTheLockfile() throws IOException {
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        // Une version stable avec métadonnées d'artefact.
        fixture.artifactMetadata("org.example", "lib", "<metadata>"
                + "<versioning><versions><version>1.0</version>"
                + "<version>2.0</version></versions></versioning></metadata>");
        new Artifacts().write(fixture, "org.example:lib", "1.0");
        new Artifacts().write(fixture, "org.example:lib", "2.0");
        Path lock = tmp.resolve("dyn/joide.lock");
        DependencyCache cache = new DependencyCache(tmp.resolve("cache"));

        // Racine dynamique [1.0,2.0] : choisit 2.0 (la plus récente
        // dans la plage) puis fige.
        ResolutionResult first = new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture.toRemote("central"))
                        .cache(cache).lockfile(lock).build())
                .resolve(List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("[1.0,2.0]"), Exposure.API)));
        assertTrue(first.success(), () -> first.problems.toString());
        assertEquals("2.0", first.artifacts.get(0).coordinate.version().raw());

        // Une 3.0 apparaît : la résolution chaude reste sur 2.0.
        fixture.artifactMetadata("org.example", "lib", "<metadata>"
                + "<versioning><versions><version>1.0</version>"
                + "<version>2.0</version><version>3.0</version>"
                + "</versions></versioning></metadata>");
        ResolutionResult warm = new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture.toRemote("central"))
                        .cache(cache).lockfile(lock).build())
                .resolve(List.of(new DependencyRequest("org.example", "lib",
                        VersionRequirement.parse("[1.0,3.0)"), Exposure.API)));
        assertTrue(warm.success(), () -> warm.problems.toString());
        assertTrue(warm.lockfileFollowed);
        assertEquals(0, warm.stats.requests);
        assertEquals("2.0", warm.artifacts.get(0).coordinate.version().raw());
    }
}
