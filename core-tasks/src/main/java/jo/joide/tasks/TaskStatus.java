package jo.joide.tasks;

/**
 * Statut final d'une tâche dans un build.
 */
public enum TaskStatus {

    /** La tâche s'est exécutée et a réussi. */
    SUCCEEDED,

    /** Aucune entrée n'a changé : rien à faire, aucun accès disque. */
    UP_TO_DATE,

    /** Sorties restaurées depuis le cache de sorties adressé par contenu. */
    FROM_CACHE,

    /** La tâche s'est exécutée et a échoué, avec diagnostics. */
    FAILED,

    /** La tâche ne s'est pas exécutée : le build a été annulé. */
    CANCELLED,

    /** La tâche ne s'est pas exécutée : une tâche amont a échoué. */
    SKIPPED_UPSTREAM
}
