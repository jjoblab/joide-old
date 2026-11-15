package jo.joide.tasks;

import java.util.List;
import java.util.Objects;

/**
 * Résultat complet d'un build : le résultat de chaque tâche dans
 * l'ordre du graphe, les durées globales et le pic de tas.
 */
public final class BuildReport {

    private final long wallStartMillis;
    private final long wallNanos;
    private final List<TaskResult> results;
    private final long peakHeapBytes;

    /**
     * @param wallStartMillis instant de début du build, en
     *                        millisecondes depuis l'époque
     * @param wallNanos       durée mur du build en nanosecondes
     * @param results         résultats des tâches, dans l'ordre
     *                        d'enregistrement du graphe
     * @param peakHeapBytes   pic de mémoire de tas observé pendant le
     *                        build
     */
    public BuildReport(long wallStartMillis, long wallNanos,
                       List<TaskResult> results, long peakHeapBytes) {
        this.wallStartMillis = wallStartMillis;
        this.wallNanos = wallNanos;
        this.results = List.copyOf(Objects.requireNonNull(results, "results"));
        this.peakHeapBytes = peakHeapBytes;
    }

    /** @return l'instant de début du build, en millisecondes */
    public long wallStartMillis() {
        return wallStartMillis;
    }

    /** @return la durée mur du build, en nanosecondes */
    public long wallNanos() {
        return wallNanos;
    }

    /** @return les résultats des tâches, dans l'ordre du graphe */
    public List<TaskResult> results() {
        return results;
    }

    /** @return le pic de mémoire de tas observé, en octets */
    public long peakHeapBytes() {
        return peakHeapBytes;
    }

    /**
     * @param status statut recherché
     * @return le nombre de tâches dans ce statut
     */
    public long count(TaskStatus status) {
        return results.stream().filter(result -> result.status() == status)
                .count();
    }

    /**
     * @return vrai si aucune tâche n'a échoué ni été annulée
     */
    public boolean successful() {
        return count(TaskStatus.FAILED) == 0
                && count(TaskStatus.CANCELLED) == 0
                && count(TaskStatus.SKIPPED_UPSTREAM) == 0;
    }

    /**
     * @return la liste des tâches qui ont échoué
     */
    public List<TaskResult> failures() {
        return results.stream()
                .filter(result -> result.status() == TaskStatus.FAILED)
                .toList();
    }

    @Override
    public String toString() {
        return "BuildReport[wallStartMillis=" + wallStartMillis
                + ", wallNanos=" + wallNanos + ", results=" + results
                + ", peakHeapBytes=" + peakHeapBytes + "]";
    }
}
