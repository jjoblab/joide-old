package jo.joide.tasks;

import java.util.Objects;

/**
 * Revendication de ressources d'une tâche, déclarée à l'avance et
 * utilisée par l'admission de l'ordonnanceur.
 *
 * <p>Une tâche n'est lancée que si la somme des revendications des
 * tâches en cours reste sous le budget : c'est la prévention des
 * débords mémoire, la charge réelle pouvant dépasser la déclaration
 * (les outils tiers ne sont pas sous contrôle).</p>
 */
public final class ResourceClaim {

    /** Revendication par défaut : un cœur, 64 Mo, non exclusive. */
    public static final ResourceClaim DEFAULT = new ResourceClaim(1, 64, false);

    /** Revendication légère : entrées-sorties ou vérification pure. */
    public static final ResourceClaim LIGHT = new ResourceClaim(0, 8, false);

    private final int cpu;
    private final long memoryMb;
    private final boolean exclusive;

    /**
     * @param cpu       coût processeur en unités de cœur (1 = un
     *                  cœur plein, une tâche d'entrée-sortie peut
     *                  déclarer 0)
     * @param memoryMb  mémoire estimée en mégaoctets
     * @param exclusive vrai si la tâche doit être seule en cours
     */
    public ResourceClaim(int cpu, long memoryMb, boolean exclusive) {
        if (cpu < 0) {
            throw new IllegalArgumentException("negative cpu claim: " + cpu);
        }
        if (memoryMb < 0) {
            throw new IllegalArgumentException(
                    "negative memory claim: " + memoryMb);
        }
        this.cpu = cpu;
        this.memoryMb = memoryMb;
        this.exclusive = exclusive;
    }

    /** @return le coût processeur en unités de cœur */
    public int cpu() {
        return cpu;
    }

    /** @return la mémoire estimée en mégaoctets */
    public long memoryMb() {
        return memoryMb;
    }

    /** @return vrai si la tâche doit s'exécuter seule */
    public boolean exclusive() {
        return exclusive;
    }

    /**
     * @param other une autre revendication
     * @return la somme des deux revendications
     */
    public ResourceClaim plus(ResourceClaim other) {
        return new ResourceClaim(cpu + other.cpu,
                memoryMb + other.memoryMb, exclusive || other.exclusive);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ResourceClaim)) {
            return false;
        }
        ResourceClaim that = (ResourceClaim) other;
        return cpu == that.cpu && memoryMb == that.memoryMb
                && exclusive == that.exclusive;
    }

    @Override
    public int hashCode() {
        return cpu * 31 + Long.hashCode(memoryMb) * 7
                + (exclusive ? 1 : 0);
    }

    @Override
    public String toString() {
        return "ResourceClaim[cpu=" + cpu + ", memoryMb=" + memoryMb
                + ", exclusive=" + exclusive + "]";
    }
}
