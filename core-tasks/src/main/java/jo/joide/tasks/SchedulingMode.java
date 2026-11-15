package jo.joide.tasks;

/**
 * Mode de traitement des échecs d'un build.
 */
public enum SchedulingMode {

    /** Arrêt rapide : à la première tâche échouée, plus rien ne démarre. */
    FAIL_FAST,

    /** Continuer : les tâches indépendantes d'un échec se poursuivent. */
    KEEP_GOING
}
