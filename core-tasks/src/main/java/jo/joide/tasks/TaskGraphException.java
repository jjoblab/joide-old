package jo.joide.tasks;

/**
 * Anomalie de graphe de tâches : référence inconnue, cycle de
 * dépendances ou contrainte d'ordre contraire aux dépendances.
 *
 * <p>Le message décrit l'anomalie exacte, cycle compris.</p>
 */
public final class TaskGraphException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message description de l'anomalie, en anglais
     */
    public TaskGraphException(String message) {
        super(message);
    }
}
