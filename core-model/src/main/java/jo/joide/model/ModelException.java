package jo.joide.model;

/**
 * Échec structurel du modèle : graphe cyclique, chemin inconnu,
 * définition incohérente.
 */
public class ModelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message message en anglais, destiné au journal
     */
    public ModelException(String message) {
        super(message);
    }
}
