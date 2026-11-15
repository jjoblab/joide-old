package jo.joide.store;

/**
 * Échec d'accès au store : le travail en cours est annulé, jamais
 * laissé à moitié appliqué.
 */
public class StoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message message en anglais, destiné au journal
     * @param cause   cause première, peut être {@code null}
     */
    public StoreException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message message en anglais, destiné au journal
     */
    public StoreException(String message) {
        super(message);
    }
}
