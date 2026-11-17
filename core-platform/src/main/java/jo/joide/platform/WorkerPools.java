package jo.joide.platform;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fabrique des pools de fils pour le code applicatif.
 *
 * <p>Le code de production ne crée jamais ses propres fils : il
 * reçoit un exécuteur injecté. Les pools créés ici utilisent des fils
 * démons nommés, identifiables dans les diagnostiques de threads.</p>
 */
public final class WorkerPools {

    private WorkerPools() {
    }

    /**
     * @param name préfixe des noms de fils (caractères simples)
     * @param size nombre de fils, au moins 1
     * @return un pool fixe à file d'attente non bornée
     * @throws IllegalArgumentException si la taille est invalide
     */
    public static ExecutorService newFixed(String name, int size) {
        requireName(name);
        if (size < 1) {
            throw new IllegalArgumentException("pool size must be at least 1: " + size);
        }
        return Executors.newFixedThreadPool(size, namedFactory(name));
    }

    /**
     * @param name préfixe des noms de fils
     * @return un pool à un seul fil, pour les écriteurs exclusifs
     */
    public static ExecutorService newSingleWriter(String name) {
        requireName(name);
        return Executors.newSingleThreadExecutor(namedFactory(name));
    }

    private static void requireName(String name) {
        if (name == null || !name.matches("[a-z][a-z0-9-]{0,30}")) {
            throw new IllegalArgumentException("invalid pool name: " + name);
        }
    }

    private static ThreadFactory namedFactory(String name) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, name + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Suggère un degré de parallélisme d'attente des entrées-sorties :
     * le double des cœurs disponibles, plafonné.
     *
     * @return un nombre de fils pour un travail d'attente
     */
    public static int ioParallelism() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.min(16, Math.max(2, cores * 2));
    }

    /**
     * Suggère un degré de parallélisme de calcul : les cœurs disponibles.
     *
     * @return un nombre de fils pour un travail de calcul
     */
    public static int cpuParallelism() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }
}
