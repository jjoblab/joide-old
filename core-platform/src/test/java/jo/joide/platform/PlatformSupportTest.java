package jo.joide.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Contrôle des pools de fils nommés et des types de résultat et de
 * problème associés au socle.
 */
class PlatformSupportTest {

    @Test
    void fixedPoolRunsTasksWithNamedThreads() throws Exception {
        ExecutorService pool = WorkerPools.newFixed("probe", 2);
        List<Future<String>> names = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            names.add(pool.submit(() -> Thread.currentThread().getName()));
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        for (Future<String> name : names) {
            assertTrue(name.get().startsWith("probe-"), name.get());
        }
    }

    @Test
    void poolNameAndSizeAreValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> WorkerPools.newFixed("Bad Name", 2));
        assertThrows(IllegalArgumentException.class,
                () -> WorkerPools.newFixed("ok", 0));
    }

    @Test
    void parallelismHintsArePositive() {
        assertTrue(WorkerPools.cpuParallelism() >= 1);
        assertTrue(WorkerPools.ioParallelism() >= 2);
    }

    @Test
    void problemFactoriesAndLocation() {
        Problem problem = Problem.warn("unknown-key", "cle ignoree").at("joide.toml:12");
        assertEquals(Severity.WARN, problem.severity());
        assertEquals("unknown-key", problem.code());
        assertEquals("joide.toml:12", problem.location());
        assertTrue(problem.toString().contains("WARN"));
        assertTrue(Problem.error("e", "m").isError());
        assertFalse(Problem.info("i", "m").isError());
        assertThrows(NullPointerException.class, () -> Problem.info(null, "m"));
    }

    @Test
    void resultCarriesValueOrProblem() {
        Result<String> ok = Result.ok("valeur");
        assertTrue(ok.isOk());
        assertEquals("valeur", ok.value());
        assertThrows(IllegalStateException.class, ok::problem);
        Result<String> err = Result.err(Problem.error("code", "message"));
        assertFalse(err.isOk());
        assertEquals("code", err.problem().code());
        assertThrows(IllegalStateException.class, err::value);
        assertThrows(NullPointerException.class, () -> Result.ok(null));
    }

    @Test
    void systemClockReportsSaneTime() {
        Clock clock = Clock.system();
        long first = clock.wallTimeMillis();
        long second = clock.wallTimeMillis();
        assertTrue(first > 0);
        assertTrue(second >= first);
        assertTrue(clock.nanoTime() > 0);
    }
}
