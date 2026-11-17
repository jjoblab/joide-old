package jo.joide.tasks;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import jo.joide.store.BuildStore;

/**
 * État chaud des dernières exécutions : les enregistrements des
 * tâches, en mémoire pour un démon chaud, rechargés du store en une
 * seule transaction au premier build.
 *
 * <p>Les écritures sont sans effet immédiat sur le store : le moteur
 * regroupe les enregistrements modifiés à la fin du build et les
 * persiste en une transaction, sur le fil propriétaire du store. Un
 * build interrompu ne laisse donc aucun état à moitié écrit : le
 * build suivant rejoue les tâches non enregistrées.</p>
 */
public final class LastBuildState {

    private static final String MODULE_KEY = "core-tasks";
    private static final String STATE_PREFIX = "last-exec:";

    private final Map<String, ExecutionRecord> records =
            new ConcurrentHashMap<>();

    /**
     * Charge les enregistrements des tâches données depuis le store,
     * en une seule transaction.
     *
     * @param store store possédé par le fil appelant
     * @param tasks tâches à charger
     * @return l'état chaud rempli
     */
    public static LastBuildState load(BuildStore store,
                                      Collection<TaskDefinition> tasks) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(tasks, "tasks");
        LastBuildState state = new LastBuildState();
        if (tasks.isEmpty()) {
            return state;
        }
        store.inTransaction(current -> {
            for (TaskDefinition task : tasks) {
                byte[] bytes = current.findState(MODULE_KEY,
                        STATE_PREFIX + task.name());
                if (bytes == null) {
                    continue;
                }
                try {
                    state.records.put(task.name(), ExecutionRecord.decode(bytes));
                } catch (java.io.IOException unreadable) {
                    // le store est un cache : un enregistrement
                    // illisible est ignoré, la tâche sera rejouée
                    continue;
                }
            }
            return null;
        });
        return state;
    }

    /**
     * @param taskKey clé de la tâche
     * @return l'enregistrement de la dernière exécution, ou
     *         {@code null} si la tâche n'a jamais abouti
     */
    public ExecutionRecord find(String taskKey) {
        return records.get(taskKey);
    }

    /**
     * @param record enregistrement à mémoriser
     */
    public void put(ExecutionRecord record) {
        Objects.requireNonNull(record, "record");
        records.put(record.taskKey(), record);
    }

    /**
     * @return les durées mesurées par nom de tâche
     */
    public Map<String, Long> durations() {
        Map<String, Long> durations = new HashMap<>();
        for (ExecutionRecord record : records.values()) {
            durations.put(record.taskKey(), record.durationMs());
        }
        return durations;
    }

    /**
     * Écrit les enregistrements modifiés dans le store, à appeler
     * <em>à l'intérieur</em> de la transaction de fin de build du
     * moteur.
     *
     * @param store     store en cours de transaction
     * @param changed   enregistrements à écrire
     * @param typesById type de tâche par clé, pour l'historique des
     *                  durées
     */
    public void writeWithinTransaction(BuildStore store,
                                       Collection<ExecutionRecord> changed,
                                       Map<String, String> typesById) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(changed, "changed");
        Objects.requireNonNull(typesById, "typesById");
        for (ExecutionRecord record : changed) {
            store.putState(MODULE_KEY, STATE_PREFIX + record.taskKey(),
                    record.encode());
            store.putFingerprint(record.taskKey(), record.fingerprint());
            store.putDuration(typesById.getOrDefault(record.taskKey(), "task"),
                    record.taskKey(), record.durationMs());
        }
    }
}
