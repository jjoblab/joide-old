package jo.joide.tasks;

/**
 * Durées mesurées d'une tâche, en nanosecondes de l'horloge injectée.
 */
public final class TaskTimings {

    /** Durées nulles, pour les tâches qui ne se sont pas exécutées. */
    public static final TaskTimings ZERO =
            new TaskTimings(0, 0, 0, 0, 0);

    private final long fingerprintNanos;
    private final long checkNanos;
    private final long executionNanos;
    private final long outputHashNanos;
    private final long totalNanos;

    /**
     * @param fingerprintNanos calcul de l'empreinte des entrées
     * @param checkNanos       vérification à jour / décision d'exécution
     * @param executionNanos   code de la tâche, annulation comprise
     * @param outputHashNanos  hachage des sorties après exécution
     * @param totalNanos       somme de toutes les étapes, décision incluse
     */
    public TaskTimings(long fingerprintNanos, long checkNanos,
                       long executionNanos, long outputHashNanos,
                       long totalNanos) {
        this.fingerprintNanos = fingerprintNanos;
        this.checkNanos = checkNanos;
        this.executionNanos = executionNanos;
        this.outputHashNanos = outputHashNanos;
        this.totalNanos = totalNanos;
    }

    /** @return le calcul de l'empreinte des entrées, en nanosecondes */
    public long fingerprintNanos() {
        return fingerprintNanos;
    }

    /** @return la vérification à jour, en nanosecondes */
    public long checkNanos() {
        return checkNanos;
    }

    /** @return l'exécution du code de la tâche, en nanosecondes */
    public long executionNanos() {
        return executionNanos;
    }

    /** @return le hachage des sorties, en nanosecondes */
    public long outputHashNanos() {
        return outputHashNanos;
    }

    /** @return la durée totale observée, en nanosecondes */
    public long totalNanos() {
        return totalNanos;
    }

    @Override
    public String toString() {
        return "TaskTimings[fingerprint=" + fingerprintNanos
                + ", check=" + checkNanos + ", execution=" + executionNanos
                + ", outputHash=" + outputHashNanos + ", total=" + totalNanos
                + "]";
    }
}
