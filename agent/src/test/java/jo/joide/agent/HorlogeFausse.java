package jo.joide.agent;

import java.util.concurrent.atomic.AtomicLong;

import jo.joide.platform.Clock;

/**
 * L'horloge fausse des tests (les critères du lot Q : « tests à
 * horloge fausse (failover, pacer) ») : le temps avance quand les
 * tests le décident — les bancs, les fenêtres et les TTL se prouvent
 * sans dormir.
 */
final class HorlogeFausse implements Clock {

    final AtomicLong mur = new AtomicLong(1_700_000_000_000L);
    final AtomicLong mono = new AtomicLong(0);

    @Override
    public long nanoTime() {
        return mono.addAndGet(1_000_000L);
    }

    @Override
    public long wallTimeMillis() {
        return mur.get();
    }

    /** Avance le temps mural du nombre de millisecondes donné. */
    void avancer(long millisecondes) {
        mur.addAndGet(millisecondes);
    }
}
