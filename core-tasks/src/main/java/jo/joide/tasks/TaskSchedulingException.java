package jo.joide.tasks;

/**
 * Impossibilité d'ordonnancer : une revendication de tâche dépasse
 * le budget de ressources tout entier.
 *
 * <p>Le message nomme la tâche et chiffre la revendication et le
 * budget.</p>
 */
public final class TaskSchedulingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message description de l'impossibilité, en anglais
     */
    public TaskSchedulingException(String message) {
        super(message);
    }
}
