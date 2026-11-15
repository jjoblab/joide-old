package jo.joide.tasks;

/**
 * Définition d'une tâche : identité, entrées, sorties et code
 * d'exécution.
 *
 * <p>L'identité de la tâche pour les empreintes est fonction de ses
 * entrées, de la version de l'outil et des options : changer la
 * version ou une option invalide la tâche même si aucun fichier
 * n'a changé. Le code s'exécute sur un fil du pool, doit écouter le
 * jeton d'annulation et peut être interrompu.</p>
 */
public interface TaskDefinition {

    /**
     * @return le nom unique de la tâche dans le graphe, par exemple
     *         « module-app:compileJava »
     */
    String name();

    /**
     * @return le type de la tâche (« compile », « dex », « package »),
     *         clé de l'estimation de durée par défaut
     */
    String taskType();

    /**
     * @return les entrées typées, dans un ordre stable
     */
    TaskInputs inputs();

    /**
     * @return les sorties typées, dans un ordre stable
     */
    TaskOutputs outputs();

    /**
     * @return la version de l'outil qui exécute la tâche, chaîne
     *         stable (par exemple la version du compilateur)
     */
    String toolVersion();

    /**
     * @return l'empreinte textuelle des options de la tâche : deux
     *         exécutions aux options égales donnent la même chaîne
     */
    String optionsFingerprint();

    /**
     * @return la revendication de ressources pour l'admission
     */
    default ResourceClaim resourceClaim() {
        return ResourceClaim.DEFAULT;
    }

    /**
     * Exécute la tâche sur un fil du pool. L'implémentation doit
     * consulter le jeton d'annulation à ses points de reprise et
     * peut propager une interruption.
     *
     * @param context contexte d'exécution, jamais {@code null}
     * @return le résultat de l'exécution, jamais {@code null}
     * @throws InterruptedException si le fil a été interrompu pendant
     *                              une étape annulable
     */
    TaskExecution execute(TaskContext context) throws InterruptedException;
}
