package jo.joide.platform;

/**
 * Niveau de gravité d'un problème signalé à l'utilisateur.
 */
public enum Severity {
    /** Information sans conséquence sur le résultat. */
    INFO,

    /** Anomalie sans blocage, à corriger. */
    WARN,

    /** Échec : l'opération concernée n'a pas pu aboutir. */
    ERROR
}
