package jo.joide.tasks;

import java.util.List;
import java.util.Objects;

import jo.joide.platform.Problem;

/**
 * Résultat final d'une tâche dans un build : statut, diagnostics,
 * durées et mémoire.
 *
 * <p>Le résultat ne dépend pas de l'ordre d'exécution : à graphe et
 * entrées égaux, deux builds produisent les mêmes résultats, dans
 * l'ordre du graphe.</p>
 */
public final class TaskResult {

    private final String name;
    private final String taskType;
    private final TaskStatus status;
    private final List<Problem> diagnostics;
    private final TaskTimings timings;
    private final long usedHeapBytes;

    /**
     * @param name          nom de la tâche
     * @param taskType      type de la tâche
     * @param status        statut final
     * @param diagnostics   problèmes rapportés (erreurs, avertissements)
     * @param timings       durées mesurées
     * @param usedHeapBytes tas utilisé observé au plus haut point de
     *                      la tâche (avant/après)
     */
    public TaskResult(String name, String taskType, TaskStatus status,
                      List<Problem> diagnostics, TaskTimings timings,
                      long usedHeapBytes) {
        this.name = Objects.requireNonNull(name, "name");
        this.taskType = Objects.requireNonNull(taskType, "taskType");
        this.status = Objects.requireNonNull(status, "status");
        this.diagnostics = List.copyOf(
                Objects.requireNonNull(diagnostics, "diagnostics"));
        this.timings = Objects.requireNonNull(timings, "timings");
        this.usedHeapBytes = usedHeapBytes;
    }

    /** @return le nom de la tâche */
    public String name() {
        return name;
    }

    /** @return le type de la tâche */
    public String taskType() {
        return taskType;
    }

    /** @return le statut final */
    public TaskStatus status() {
        return status;
    }

    /** @return les problèmes rapportés */
    public List<Problem> diagnostics() {
        return diagnostics;
    }

    /** @return les durées mesurées */
    public TaskTimings timings() {
        return timings;
    }

    /** @return le tas utilisé observé, en octets */
    public long usedHeapBytes() {
        return usedHeapBytes;
    }

    /**
     * @return vrai si la tâche a produit du travail (exécutée ou
     *         restaurée du cache)
     */
    public boolean didWork() {
        return status == TaskStatus.SUCCEEDED || status == TaskStatus.FROM_CACHE;
    }

    /**
     * @return vrai si le statut est terminal réussi
     */
    public boolean isSuccessful() {
        return status == TaskStatus.SUCCEEDED || status == TaskStatus.UP_TO_DATE
                || status == TaskStatus.FROM_CACHE;
    }

    @Override
    public String toString() {
        return "TaskResult[name=" + name + ", taskType=" + taskType
                + ", status=" + status + ", diagnostics=" + diagnostics
                + ", timings=" + timings + ", usedHeapBytes=" + usedHeapBytes
                + "]";
    }
}
