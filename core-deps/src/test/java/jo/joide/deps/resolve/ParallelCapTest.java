package jo.joide.deps.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jo.joide.deps.DependencyRequest;
import jo.joide.deps.Exposure;
import jo.joide.deps.VersionRequirement;
import jo.joide.deps.cache.DependencyCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Téléchargements parallèles plafonnés : la concurrence réelle du
 * transport ne dépasse jamais le plafond configuré, et tous les
 * fichiers finissent téléchargés.
 */
class ParallelCapTest {

    @TempDir
    Path tmp;

    @Test
    void concurrencyNeverExceedsTheCap() throws IOException {
        RepoFixture fixture = RepoFixture.create(tmp.resolve("repo"));
        // Huit artefacts à télécharger, aucun au cache.
        List<DependencyRequest> requests = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            new Artifacts().write(fixture, "org.multi:lib" + i, "1.0");
            requests.add(new DependencyRequest("org.multi", "lib" + i,
                    VersionRequirement.parse("1.0"), Exposure.API));
        }
        fixture.transport.delay(120);

        ResolutionResult result = new Resolver(
                new ResolverConfig.Builder()
                        .repository(fixture.toRemote("central"))
                        .cache(new DependencyCache(tmp.resolve("cache")))
                        .maxParallelDownloads(2)
                        .build())
                .resolve(requests);
        assertTrue(result.success(), () -> result.problems.toString());
        assertEquals(8, result.artifacts.size());
        assertTrue(fixture.transport.maxConcurrent.get() <= 2,
                () -> "concurrence max " + fixture.transport.maxConcurrent.get());
        // Le plafond a bien été utilisé (sinon le test ne prouverait
        // rien sur le parallélisme réel).
        assertTrue(fixture.transport.maxConcurrent.get() >= 2,
                () -> "concurrence max inattendue : "
                        + fixture.transport.maxConcurrent.get());
        assertEquals(8, result.stats.downloads);
    }
}
