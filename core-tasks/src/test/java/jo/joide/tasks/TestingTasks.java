package jo.joide.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import jo.joide.platform.Clock;
import jo.joide.tasks.TaskInput.PropertyInput;

/**
 * Outillage des tests du moteur : tâches factices instrumentées,
 * collecteur d'événements horodatés et génération déterministe de
 * graphes.
 */
public final class TestingTasks {

    private TestingTasks() {
        // outillage de test
    }

    /**
     * Tâche factice : compte ses exécutions et exécute une action
     * injectée (écriture de sorties, blocage sur loquet, échec).
     */
    public static final class RecordingTask implements TaskDefinition {

        /** Action exécutée par la tâche. */
        public interface Action {
            /** @return le résultat de l'exécution */
            TaskExecution run(TaskContext context) throws InterruptedException;
        }

        private final String name;
        private final String taskType;
        private final TaskInputs inputs;
        private final TaskOutputs outputs;
        private final ResourceClaim claim;
        private volatile Action action = context -> TaskExecution.ok();
        private final AtomicInteger executions = new AtomicInteger();

        /**
         * @param name     nom de la tâche
         * @param taskType type de la tâche
         * @param inputs   entrées
         * @param outputs  sorties
         * @param claim    revendication de ressources
         */
        public RecordingTask(String name, String taskType, TaskInputs inputs,
                             TaskOutputs outputs, ResourceClaim claim) {
            this.name = Objects.requireNonNull(name, "name");
            this.taskType = Objects.requireNonNull(taskType, "taskType");
            this.inputs = Objects.requireNonNull(inputs, "inputs");
            this.outputs = Objects.requireNonNull(outputs, "outputs");
            this.claim = Objects.requireNonNull(claim, "claim");
        }

        /**
         * @param next action à exécuter
         * @return cette tâche
         */
        public RecordingTask acts(Action next) {
            this.action = Objects.requireNonNull(next, "next");
            return this;
        }

        /** @return le nombre d'exécutions réelles de la tâche */
        public int executions() {
            return executions.get();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String taskType() {
            return taskType;
        }

        @Override
        public TaskInputs inputs() {
            return inputs;
        }

        @Override
        public TaskOutputs outputs() {
            return outputs;
        }

        @Override
        public String toolVersion() {
            return "test-tool-1";
        }

        @Override
        public String optionsFingerprint() {
            return "options-1";
        }

        @Override
        public ResourceClaim resourceClaim() {
            return claim;
        }

        @Override
        public TaskExecution execute(TaskContext context) throws InterruptedException {
            executions.incrementAndGet();
            return action.run(context);
        }
    }

    /**
     * Collecteur d'événements horodatés : débuts et fins de tâches
     * observés par l'ordonnanceur.
     */
    public static final class EventLog implements BuildEvents {

        private final Map<String, Long> startedAt = new ConcurrentHashMap<>();
        private final Map<String, Long> finishedAt = new ConcurrentHashMap<>();
        private final Map<String, TaskResult> results =
                new ConcurrentHashMap<>();
        private final Clock clock;
        private final List<String> order = new ArrayList<>();

        /**
         * @param clock horloge injectée des mesures
         */
        public EventLog(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
        }

        @Override
        public synchronized void taskStarted(String taskName) {
            startedAt.put(taskName, clock.nanoTime());
            order.add(taskName);
        }

        @Override
        public synchronized void taskFinished(TaskResult result) {
            finishedAt.put(result.name(), clock.nanoTime());
            results.put(result.name(), result);
        }

        /** @return l'instant de début d'une tâche, en nanosecondes */
        public long startedAt(String taskName) {
            Long at = startedAt.get(taskName);
            if (at == null) {
                throw new IllegalArgumentException("never started: " + taskName);
            }
            return at;
        }

        /** @return l'instant de fin d'une tâche, en nanosecondes */
        public long finishedAt(String taskName) {
            Long at = finishedAt.get(taskName);
            if (at == null) {
                throw new IllegalArgumentException("never finished: " + taskName);
            }
            return at;
        }

        /** @return vrai si la tâche a démarré */
        public boolean hasStarted(String taskName) {
            return startedAt.containsKey(taskName);
        }

        /** @return vrai si la tâche est terminée */
        public boolean hasFinished(String taskName) {
            return finishedAt.containsKey(taskName);
        }

        /** @return le résultat observé d'une tâche */
        public TaskResult result(String taskName) {
            return results.get(taskName);
        }

        /** @return l'ordre des débuts observés */
        public synchronized List<String> startOrder() {
            return new ArrayList<>(order);
        }
    }

    /**
     * Génère un graphe aléatoire acyclique déterministe : chaque
     * tâche dépend d'au plus trois tâches antérieures.
     *
     * @param seed      graine du générateur
     * @param nodeCount nombre de tâches
     * @param infer     active l'inférence par préfixe
     * @return le graphe et les tâches créées
     */
    public static Generated randomDag(long seed, int nodeCount, boolean infer) {
        Random random = new Random(seed);
        TaskGraph.Builder builder = TaskGraph.builder();
        if (infer) {
            builder.inferByPath();
        }
        List<RecordingTask> tasks = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            String name = "t" + i;
            RecordingTask task = new RecordingTask(name, "noop",
                    TaskInputs.builder().build(),
                    TaskOutputs.builder().build(), ResourceClaim.LIGHT);
            tasks.add(task);
            builder.register(task);
            int deps = random.nextInt(4);
            for (int d = 0; d < deps && i > 0; d++) {
                builder.dependsOn(name, "t" + random.nextInt(i));
            }
        }
        return new Generated(builder.build(), tasks);
    }

    /**
     * Graphe généré avec ses tâches.
     *
     * @param graph graphe validé
     * @param tasks tâches dans l'ordre de création
     */
    public record Generated(TaskGraph graph, List<RecordingTask> tasks) {
    }

    /**
     * Ajoute une entrée propriété « ordre » à une tâche factice pour
     * la rendre distincte en empreinte.
     *
     * @param task  tâche à modifier
     * @param value valeur de la propriété
     * @return une nouvelle tâche équivalente avec la propriété
     */
    public static RecordingTask withProperty(RecordingTask task, String value) {
        return new RecordingTask(task.name(), task.taskType(),
                TaskInputs.builder().add(new PropertyInput("order", value))
                        .build(),
                task.outputs(), task.resourceClaim());
    }

    /**
     * Convertit le résultat d'une exécution de tâche en résultat final
     * de step d'ordonnanceur.
     *
     * @param task      tâche exécutée
     * @param execution résultat de l'exécution
     * @return le résultat final
     */
    public static TaskResult resultOf(TaskDefinition task,
                                      TaskExecution execution) {
        if (execution instanceof TaskExecution.Ok) {
            return new TaskResult(task.name(), task.taskType(),
                    TaskStatus.SUCCEEDED, List.of(), TaskTimings.ZERO, 0);
        }
        return new TaskResult(task.name(), task.taskType(), TaskStatus.FAILED,
                ((TaskExecution.Failed) execution).diagnostics(),
                TaskTimings.ZERO, 0);
    }

    /**
     * @param graph graphe
     * @return une priorité nulle pour chaque tâche du graphe
     */
    public static Map<String, Long> zeroPriorities(TaskGraph graph) {
        Map<String, Long> priorities = new java.util.HashMap<>();
        for (String name : graph.names()) {
            priorities.put(name, 0L);
        }
        return priorities;
    }
}
