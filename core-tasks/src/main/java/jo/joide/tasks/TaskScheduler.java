package jo.joide.tasks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;

import jo.joide.platform.CancelToken;
import jo.joide.platform.Problem;

/**
 * Ordonnanceur de tâches : file de prêtes par compteurs de
 * dépendances, priorité par chemin critique restant, admission de
 * ressources et annulation propre.
 *
 * <p>Il n'y a aucune barrière par niveau : une tâche devient éligible
 * dès que son dernier prédécesseur dur se termine, même si d'autres
 * tâches du même niveau tournent encore. Les contraintes d'ordre
 * « mustRunAfter » ne créent pas de dépendance ; si un cycle d'ordre
 * pur affame la file alors que rien ne tourne, la tâche la plus
 * urgente est lancée malgré la contrainte, pour ne jamais
 * s'interbloquer.</p>
 *
 * <p>Garanties : aucune tâche ne démarre avant la fin de ses
 * dépendances dures ; après une annulation, le retour n'a lieu qu'une
 * fois toutes les tâches lancées terminées (aucune orpheline) ; les
 * résultats sont indexés par tâche dans l'ordre d'enregistrement du
 * graphe et ne dépendent pas de l'ordre d'exécution.</p>
 */
public final class TaskScheduler {

    private final ExecutorService pool;
    private final SchedulingMode mode;
    private final BuildEvents events;

    /**
     * @param pool   exécuteur injecté des fils de tâches, borné
     * @param mode   mode de traitement des échecs
     * @param events écouteur d'événements, jamais {@code null} ; les
     *               rappels sont appelés sous le verrou interne et ne
     *               doivent jamais bloquer
     */
    public TaskScheduler(ExecutorService pool, SchedulingMode mode,
                         BuildEvents events) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.events = Objects.requireNonNull(events, "events");
    }

    /**
     * Étape d'une tâche, exécutée sur un fil du pool par le moteur :
     * décision à jour, exécution éventuelle et empreintes.
     */
    public interface TaskStep {

        /**
         * @param task         définition de la tâche à traiter
         * @param cancellation jeton d'annulation effectif du build
         * @return le résultat final de la tâche, jamais {@code null}
         */
        TaskResult run(TaskDefinition task, CancelToken cancellation);
    }

    /**
     * Ordonnance le graphe jusqu'à terminaison, échec terminal ou
     * annulation. Si le fil appelant est interrompu, l'annulation est
     * déclenchée, attendue, le drapeau d'interruption restauré, et
     * les résultats réels (partiels) sont retournés.
     *
     * @param graph          graphe validé
     * @param priorities     poids de chemin critique restant par nom
     *                       de tâche (plus grand = plus urgent) ;
     *                       une entrée absente vaut zéro
     * @param budget         budget d'admission des ressources
     * @param externalCancel annulation demandée de l'extérieur, peut
     *                       être {@code null}
     * @param step           étape à exécuter pour chaque tâche
     * @return les résultats par tâche, dans l'ordre d'enregistrement
     *         du graphe
     */
    public Map<String, TaskResult> run(TaskGraph graph,
                                       Map<String, Long> priorities,
                                       ResourceBudget budget,
                                       CancelToken externalCancel,
                                       TaskStep step) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(priorities, "priorities");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(step, "step");
        Run run = new Run(graph, priorities, budget,
                externalCancel == null ? CancelToken.NEVER : externalCancel);
        try {
            run.execute(step);
        } catch (InterruptedException interrupted) {
            run.drainAfterInterruption();
            Thread.currentThread().interrupt();
        }
        Map<String, TaskResult> ordered = new LinkedHashMap<>();
        for (int i = 0; i < run.names.size(); i++) {
            ordered.put(run.names.get(i), run.terminalResult(i));
        }
        return ordered;
    }

    /**
     * État mutable d'une exécution : tout accès sous {@link #lock}.
     */
    private final class Run {

        final Object lock = new Object();
        final TaskGraph graph;
        final List<String> names;
        final Map<String, Integer> indexes = new HashMap<>();
        final long[] priority;
        final int[] pendingDeps;
        final int[] pendingOrder;
        final TaskResult[] results;
        final PriorityQueue<Integer> ready;
        final ResourceBudget budget;
        final CancelToken.Cancellation internal = CancelToken.create();
        final CancelToken external;
        int inFlight;
        int finished;
        boolean stopping;

        Run(TaskGraph graph, Map<String, Long> priorities,
            ResourceBudget budget, CancelToken external) {
            this.graph = graph;
            this.names = graph.names();
            this.budget = budget;
            this.external = external;
            int total = names.size();
            this.priority = new long[total];
            this.pendingDeps = new int[total];
            this.pendingOrder = new int[total];
            this.results = new TaskResult[total];
            this.ready = new PriorityQueue<>((a, b) -> {
                int byPriority = Long.compare(priority[b], priority[a]);
                return byPriority != 0 ? byPriority
                        : names.get(a).compareTo(names.get(b));
            });
            for (int i = 0; i < total; i++) {
                String name = names.get(i);
                indexes.put(name, i);
                priority[i] = priorities.getOrDefault(name, 0L);
                pendingDeps[i] = graph.hardDepsOf(name).size();
                pendingOrder[i] = countOrderPredecessors(graph, name);
                ResourceClaim claim = graph.task(name).resourceClaim();
                if (claim.cpu() > budget.cpuCap()
                        || claim.memoryMb() > budget.memoryCap()) {
                    throw new TaskSchedulingException(
                            "task claim exceeds budget: " + name + " claims "
                                    + claim.cpu() + " cpu units and "
                                    + claim.memoryMb() + " MB, budget allows "
                                    + budget.cpuCap() + " and " + budget.memoryCap());
                }
            }
            external.onCancel(this::initiateStop);
        }

        void execute(TaskStep step) throws InterruptedException {
            synchronized (lock) {
                for (int i = 0; i < names.size(); i++) {
                    if (pendingDeps[i] == 0) {
                        ready.add(i);
                    }
                }
                submitEligible(step);
                // le retour n'a lieu qu'une fois toutes les tâches
                // terminées ET tous les vols achevés : aucune
                // orpheline ne survit au build
                while (finished < names.size() || inFlight > 0) {
                    if (!stopping && inFlight == 0 && ready.isEmpty()) {
                        throw new IllegalStateException(
                                "scheduler stalled with no runnable task: "
                                        + pendingNames());
                    }
                    lock.wait();
                    submitEligible(step);
                }
            }
        }

        void drainAfterInterruption() {
            initiateStop();
            boolean interrupted = false;
            synchronized (lock) {
                while (finished < names.size() || inFlight > 0) {
                    try {
                        lock.wait();
                    } catch (InterruptedException stopped) {
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Soumet toutes les tâches prêtes admises par le budget. Une
         * tâche dont les prédécesseurs d'ordre ne sont pas finis
         * attend ; si aucun travail n'est en cours et que la file ne
         * contient que des tâches bloquées par l'ordre (cycle d'ordre
         * pur), la contrainte est relâchée une fois pour la tâche la
         * plus urgente.
         */
        void submitEligible(TaskStep step) {
            boolean relaxed = false;
            while (!stopping && !ready.isEmpty()) {
                List<Integer> snapshot = new ArrayList<>(ready);
                int chosen = -1;
                for (int candidate : snapshot) {
                    if (results[candidate] != null) {
                        ready.remove(candidate);
                        chosen = -2;
                        break;
                    }
                    if (!relaxed && pendingOrder[candidate] > 0) {
                        continue;
                    }
                    if (!budget.tryAcquire(claimOf(candidate))) {
                        continue;
                    }
                    ready.remove(candidate);
                    chosen = candidate;
                    break;
                }
                if (chosen == -2) {
                    continue;
                }
                if (chosen < 0) {
                    if (!relaxed && inFlight == 0) {
                        relaxed = true;
                        continue;
                    }
                    return;
                }
                launch(chosen, step);
            }
        }

        void launch(int index, TaskStep step) {
            inFlight++;
            events.taskStarted(names.get(index));
            // pas d'annulation du futur : une tâche en file doit
            // démarrer pour constater l'arrêt et libérer sa
            // revendication ; l'arrêt des tâches en vol passe par le
            // jeton coopératif et ses écouteurs
            pool.execute(() -> executeStep(index, step));
        }

        void executeStep(int index, TaskStep step) {
            // une tâche annulée pendant qu'elle était en file
            // d'attente ne s'exécute pas : elle libère simplement sa
            // revendication et rend son compte de vol
            if (isTerminal(index)) {
                abandon(index);
                return;
            }
            TaskDefinition task = graph.task(names.get(index));
            TaskResult result;
            try {
                result = step.run(task, effectiveToken());
            } catch (Throwable failure) {
                result = crashedResult(task, failure);
            }
            finalize(index, result);
        }

        void abandon(int index) {
            synchronized (lock) {
                budget.release(claimOf(index));
                inFlight--;
                // l'événement de fin est émis sous le verrou : son
                // horodatage précède toujours le démarrage des tâches
                // qu'il débloque
                events.taskFinished(results[index]);
                lock.notifyAll();
            }
        }

        void finalize(int index, TaskResult result) {
            synchronized (lock) {
                budget.release(claimOf(index));
                inFlight--;
                boolean alreadyCounted = results[index] != null;
                results[index] = result;
                if (!alreadyCounted) {
                    finished++;
                }
                String name = names.get(index);
                for (String dependent : graph.dependentsOf(name)) {
                    int depIndex = indexes.get(dependent);
                    if (--pendingDeps[depIndex] == 0
                            && results[depIndex] == null) {
                        ready.add(depIndex);
                    }
                }
                for (String successor : graph.orderSuccessorsOf(name)) {
                    pendingOrder[indexes.get(successor)]--;
                }
                if (result.status() == TaskStatus.FAILED) {
                    if (mode == SchedulingMode.FAIL_FAST) {
                        initiateStop();
                    } else {
                        skipDependentsOf(name);
                    }
                } else if (result.status() == TaskStatus.CANCELLED) {
                    initiateStop();
                }
                // l'événement de fin est émis sous le verrou : son
                // horodatage précède toujours le démarrage des tâches
                // qu'il débloque
                events.taskFinished(result);
                lock.notifyAll();
            }
        }

        void skipDependentsOf(String failed) {
            List<String> frontier = new ArrayList<>(graph.dependentsOf(failed));
            while (!frontier.isEmpty()) {
                String current = frontier.remove(frontier.size() - 1);
                int index = indexes.get(current);
                if (results[index] != null) {
                    continue;
                }
                results[index] = terminalResult(index, TaskStatus.SKIPPED_UPSTREAM);
                finished++;
                frontier.addAll(graph.dependentsOf(current));
            }
        }

        void initiateStop() {
            synchronized (lock) {
                stopping = true;
                internal.cancel();
                // toute tâche non terminée est annulée, y compris
                // celles en file d'attente : leur exécution
                // éventuelle constatera l'état terminal et libérera
                // ses ressources ; une exécution déjà lancée garde
                // son résultat réel
                for (int i = 0; i < names.size(); i++) {
                    if (results[i] == null) {
                        results[i] = terminalResult(i, TaskStatus.CANCELLED);
                        finished++;
                    }
                }
                lock.notifyAll();
            }
        }

        CancelToken effectiveToken() {
            return new CancelToken() {
                @Override
                public boolean isCancelled() {
                    return isStopping() || internal.token().isCancelled()
                            || external.isCancelled();
                }

                @Override
                public void onCancel(Runnable listener) {
                    // un même écouteur peut être appelé une fois par
                    // jeton sous-jacent : il doit être idempotent
                    internal.token().onCancel(listener);
                    external.onCancel(listener);
                }
            };
        }

        boolean isStopping() {
            synchronized (lock) {
                return stopping;
            }
        }

        boolean isTerminal(int index) {
            synchronized (lock) {
                return results[index] != null;
            }
        }

        TaskResult terminalResult(int index) {
            TaskResult result = results[index];
            return result == null
                    ? terminalResult(index, TaskStatus.CANCELLED) : result;
        }

        TaskResult terminalResult(int index, TaskStatus status) {
            String name = names.get(index);
            return new TaskResult(name, graph.task(name).taskType(), status,
                    List.of(), TaskTimings.ZERO, 0);
        }

        ResourceClaim claimOf(int index) {
            return graph.task(names.get(index)).resourceClaim();
        }

        String pendingNames() {
            List<String> pending = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                if (results[i] == null) {
                    pending.add(names.get(i));
                }
            }
            return String.join(", ", pending);
        }
    }

    private static int countOrderPredecessors(TaskGraph graph, String name) {
        int count = 0;
        for (String other : graph.names()) {
            if (graph.orderSuccessorsOf(other).contains(name)) {
                count++;
            }
        }
        return count;
    }

    private static TaskResult crashedResult(TaskDefinition task,
                                            Throwable failure) {
        Problem problem = Problem.error("task-crash",
                failure.getClass().getSimpleName() + ": " + failure.getMessage())
                .at(task.name());
        return new TaskResult(task.name(), task.taskType(), TaskStatus.FAILED,
                List.of(problem), TaskTimings.ZERO, 0);
    }
}
