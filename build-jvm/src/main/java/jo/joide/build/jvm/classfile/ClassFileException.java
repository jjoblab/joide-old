package jo.joide.build.jvm.classfile;

/**
 * Fichier de classe illisible ou non conforme : toujours transformé
 * en diagnostic explicite par l'appelant, jamais en état silencieux.
 */
public class ClassFileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message message en anglais (D3), ASCII
     */
    public ClassFileException(String message) {
        super(message);
    }

    /**
     * @param message message en anglais (D3), ASCII
     * @param cause   cause technique
     */
    public ClassFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
