package jo.joide.model.toml;

import jo.joide.platform.Severity;

/**
 * Problème d'analyse TOML : gravité, ligne, colonne et message.
 *
 * <p>L'analyseur ne lève jamais d'exception sur une entrée malformée :
 * chaque anomalie devient un problème, la partie saine du document
 * est conservée.</p>
 */
public final class TomlProblem {

    private final Severity severity;
    private final int line;
    private final int column;
    private final String message;

    /**
     * @param line    numéro de ligne, à partir de 1
     * @param column  colonne, à partir de 1
     * @param message description du problème, en français
     */
    public TomlProblem(int line, int column, String message) {
        this.severity = Severity.ERROR;
        this.line = line;
        this.column = column;
        this.message = java.util.Objects.requireNonNull(message, "message");
    }

    /** @return la gravité, toujours erreur pour l'instant */
    public Severity severity() {
        return severity;
    }

    /** @return le numéro de ligne */
    public int line() {
        return line;
    }

    /** @return la colonne */
    public int column() {
        return column;
    }

    /** @return le message */
    public String message() {
        return message;
    }

    @Override
    public String toString() {
        return "ligne " + line + ":" + column + " — " + message;
    }
}
