package jo.joide.tasks;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jo.joide.platform.Clock;

/**
 * Échantillonneur de tas : relève périodiquement la mémoire utilisée
 * pendant un build pour en rapporter le pic.
 *
 * <p>L'échantillonneur s'appuie sur un exécuteur injecté : le moteur
 * n'en crée jamais. Sans exécuteur fourni, il reste inactif et le pic
 * mesuré se limite aux relevés avant/après de chaque tâche.</p>
 */
public final class HeapSampler {

    private final ScheduledExecutorService executor;
    private final long intervalMillis;
    private final AtomicLong peakBytes = new AtomicLong();
    private volatile ScheduledFuture<?> task;

    /**
     * @param executor       exécuteur injecté, peut être {@code null}
     * @param intervalMillis cadence d'échantillonnage en millisecondes
     */
    public HeapSampler(ScheduledExecutorService executor, long intervalMillis) {
        this.executor = executor;
        this.intervalMillis = intervalMillis;
    }

    /**
     * Démarre l'échantillonnage, sans effet sans exécuteur.
     */
    public void start() {
        record();
        if (executor == null) {
            return;
        }
        task = executor.scheduleAtFixedRate(this::record, intervalMillis,
                intervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Arrête l'échantillonnage ; les relevés directs restent
     * possibles.
     */
    public void stop() {
        ScheduledFuture<?> current = task;
        if (current != null) {
            current.cancel(false);
            task = null;
        }
        record();
    }

    /**
     * @return le pic de mémoire utilisée observé, en octets
     */
    public long peakBytes() {
        return peakBytes.get();
    }

    /**
     * Observe la mémoire utilisée immédiatement, par exemple autour
     * d'une tâche.
     *
     * @return la mémoire utilisée au moment de l'appel
     */
    public long record() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        peakBytes.accumulateAndGet(used, Math::max);
        return used;
    }
}
