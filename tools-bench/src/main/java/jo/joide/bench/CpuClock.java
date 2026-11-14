package jo.joide.bench;

import java.lang.management.ManagementFactory;

/**
 * Temps CPU du processus (ADR-014), via l'extension de la JVM de bureau.
 *
 * <p>tools-bench est un module « test-only » (§5.1 du prompt), jamais
 * exécuté sur Android : l'exemption API 28 (ADR-013) lui permet d'utiliser
 * les API de mesure de la JVM. Le temps CPU — contrairement au temps mur —
 * est insensible au bridage d'un ordonnanceur partagé : c'est la métrique
 * de régression temporelle, le temps mur restant informatif.</p>
 */
public final class CpuClock {

    private CpuClock() {
    }

    /** @return le temps CPU cumulé du processus, en nanosecondes */
    public static long processCpuNanos() {
        java.lang.management.OperatingSystemMXBean bean =
                ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean) {
            long nanos = ((com.sun.management.OperatingSystemMXBean) bean)
                    .getProcessCpuTime();
            if (nanos >= 0) {
                return nanos;
            }
        }
        return -1L; // indisponible : la comparaison temporelle s'abstient
    }
}
