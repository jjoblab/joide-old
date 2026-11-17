package jo.joide.tasks;

import java.util.Objects;

/**
 * Budget de ressources de l'ordonnanceur : garde-fou d'admission des
 * tâches par unités de cœur, mémoire et exclusivité.
 *
 * <p>Le budget peut rétrécir en cours de build (pression mémoire du
 * système) : les revendications en cours restent acquises, mais
 * aucune nouvelle tâche n'est admise au-dessus du nouveau plafond
 * tant que la somme des revendications en cours ne redescend pas.</p>
 */
public final class ResourceBudget {

    private final Object lock = new Object();
    private long capCpu;
    private long capMemoryMb;
    private long usedCpu;
    private long usedMemoryMb;
    private int running;
    private int exclusiveRunning;

    /**
     * @param cpuUnits    plafond d'unités de cœur
     * @param memoryMb    plafond de mémoire en mégaoctets
     */
    public ResourceBudget(long cpuUnits, long memoryMb) {
        if (cpuUnits < 0 || memoryMb < 0) {
            throw new IllegalArgumentException(
                    "negative budget: cpu=" + cpuUnits + " memoryMb=" + memoryMb);
        }
        this.capCpu = cpuUnits;
        this.capMemoryMb = memoryMb;
    }

    /**
     * Tente d'admettre une revendication.
     *
     * @param claim revendication de la tâche candidate
     * @return vrai si la revendication est acquise (à libérer ensuite)
     */
    public boolean tryAcquire(ResourceClaim claim) {
        Objects.requireNonNull(claim, "claim");
        synchronized (lock) {
            boolean blocksOthers = claim.exclusive() && running > 0;
            boolean blockedByExclusive = exclusiveRunning > 0;
            if (blocksOthers || blockedByExclusive) {
                return false;
            }
            if (usedCpu + claim.cpu() > capCpu
                    || usedMemoryMb + claim.memoryMb() > capMemoryMb) {
                return false;
            }
            usedCpu += claim.cpu();
            usedMemoryMb += claim.memoryMb();
            running++;
            if (claim.exclusive()) {
                exclusiveRunning++;
            }
            return true;
        }
    }

    /**
     * Libère une revendication acquise.
     *
     * @param claim revendication à libérer
     */
    public void release(ResourceClaim claim) {
        Objects.requireNonNull(claim, "claim");
        synchronized (lock) {
            usedCpu -= claim.cpu();
            usedMemoryMb -= claim.memoryMb();
            running--;
            if (claim.exclusive()) {
                exclusiveRunning--;
            }
        }
    }

    /**
     * Ajuste les plafonds : rétrécit sous pression mémoire, s'élargit
     * quand la pression passe.
     *
     * @param cpuUnits nouveau plafond d'unités de cœur
     * @param memoryMb nouveau plafond de mémoire
     */
    public void setCaps(long cpuUnits, long memoryMb) {
        if (cpuUnits < 0 || memoryMb < 0) {
            throw new IllegalArgumentException(
                    "negative cap: cpu=" + cpuUnits + " memoryMb=" + memoryMb);
        }
        synchronized (lock) {
            this.capCpu = cpuUnits;
            this.capMemoryMb = memoryMb;
        }
    }

    /**
     * @return le plafond courant d'unités de cœur
     */
    public long cpuCap() {
        synchronized (lock) {
            return capCpu;
        }
    }

    /**
     * @return le plafond courant de mémoire en mégaoctets
     */
    public long memoryCap() {
        synchronized (lock) {
            return capMemoryMb;
        }
    }

    /**
     * @return la mémoire actuelle des revendications en cours, en Mo
     */
    public long usedMemoryMb() {
        synchronized (lock) {
            return usedMemoryMb;
        }
    }

    /**
     * @return les unités de cœur des revendications en cours
     */
    public long usedCpu() {
        synchronized (lock) {
            return usedCpu;
        }
    }

    /**
     * @return le nombre de tâches en cours
     */
    public int runningCount() {
        synchronized (lock) {
            return running;
        }
    }
}
