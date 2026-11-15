package jo.joide.tasks;

/**
 * Écouteur d'événements de build, pour l'observabilité et les tests.
 *
 * <p>Les callbacks sont appelés depuis les fils d'exécution des
 * tâches : une implémentation doit être tolérante à la concurrence
 * et ne jamais bloquer.</p>
 */
public interface BuildEvents {

    /** Écouteur sans effet, partagé. */
    BuildEvents NOOP = new BuildEvents() {
        @Override
        public void taskStarted(String taskName) {
            // sans effet
        }

        @Override
        public void taskFinished(TaskResult result) {
            // sans effet
        }
    };

    /**
     * Appelé au lancement du code d'une tâche, après admission.
     *
     * @param taskName nom de la tâche
     */
    void taskStarted(String taskName);

    /**
     * Appelé au terme d'une tâche, quel que soit le statut.
     *
     * @param result résultat final de la tâche
     */
    void taskFinished(TaskResult result);
}
