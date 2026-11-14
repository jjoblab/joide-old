package jo.joide.bench;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Sonde de pic de tas.
 *
 * <p>Approximation assumée : un fil démon échantillonne
 * {@code Runtime.totalMemory() - Runtime.freeMemory()} toutes les 5 ms
 * et retient le maximum observé. {@code MemoryMXBean} n'existe pas au
 * niveau 28 : la sonde reste compatible avec le
 * contrôle API 28 tout en donnant un ordre de grandeur comparable entre
 * exécutions. La précision « pic réel » nécessitera une
 * instrumentation appareil dédiée.</p>
 */
public final class HeapProbe implements AutoCloseable {

    private static final long SAMPLE_MS = 5;

    private final AtomicLong peakUsed = new AtomicLong();
    private final Thread sampler;

    private volatile boolean running = true;

    /** Démarre la sonde. */
    public HeapProbe() {
        final Runtime runtime = Runtime.getRuntime();
        sampler = new Thread(() -> {
            while (running) {
                long used = runtime.totalMemory() - runtime.freeMemory();
                peakUsed.accumulateAndGet(used, Math::max);
                try {
                    Thread.sleep(SAMPLE_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "bench-heap-probe");
        sampler.setDaemon(true);
        sampler.start();
    }

    /** @return le pic de tas utilisé observé, en octets */
    public long peakUsedBytes() {
        return peakUsed.get();
    }

    @Override
    public void close() {
        running = false;
        sampler.interrupt();
    }
}
