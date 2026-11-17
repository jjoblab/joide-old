package jo.joide.platform;

import java.util.Objects;

/**
 * Problème signalé à l'utilisateur : gravité, code court stable et
 * message lisible, avec localisation facultative.
 *
 * <p>Les problèmes remplacent les exceptions pour tout ce qui doit
 * être rapporté sans interrompre une opération tolérante (chargement
 * de projet, import, diagnostic). Le code est un identifiant court en
 * anglais, stable entre versions ; le message est destiné à l'interface.</p>
 */
public final class Problem {

    private final Severity severity;
    private final String code;
    private final String message;
    private final String location;

    private Problem(Severity severity, String code, String message, String location) {
        this.severity = Objects.requireNonNull(severity, "severity");
        this.code = Objects.requireNonNull(code, "code");
        this.message = Objects.requireNonNull(message, "message");
        this.location = location;
    }

    /**
     * @param code    code court stable, en anglais
     * @param message message lisible
     * @return un problème de niveau information
     */
    public static Problem info(String code, String message) {
        return new Problem(Severity.INFO, code, message, null);
    }

    /**
     * @param code    code court stable, en anglais
     * @param message message lisible
     * @return un problème de niveau avertissement
     */
    public static Problem warn(String code, String message) {
        return new Problem(Severity.WARN, code, message, null);
    }

    /**
     * @param code    code court stable, en anglais
     * @param message message lisible
     * @return un problème de niveau erreur
     */
    public static Problem error(String code, String message) {
        return new Problem(Severity.ERROR, code, message, null);
    }

    /**
     * @param where localisation lisible (chemin, ligne)
     * @return le même problème, localisé
     */
    public Problem at(String where) {
        return new Problem(severity, code, message, where);
    }

    /** @return la gravité */
    public Severity severity() {
        return severity;
    }

    /** @return le code court stable */
    public String code() {
        return code;
    }

    /** @return le message lisible */
    public String message() {
        return message;
    }

    /**
     * @return la localisation, ou {@code null} si le problème n'est pas
     *         localisé
     */
    public String location() {
        return location;
    }

    /**
     * @return vrai si la gravité est {@link Severity#ERROR}
     */
    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Problem)) {
            return false;
        }
        Problem that = (Problem) other;
        return severity == that.severity
                && code.equals(that.code)
                && message.equals(that.message)
                && Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
        int result = severity.hashCode();
        result = 31 * result + code.hashCode();
        result = 31 * result + message.hashCode();
        result = 31 * result + Objects.hashCode(location);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        out.append(severity).append(" [").append(code).append(']');
        if (location != null) {
            out.append(" (").append(location).append(')');
        }
        return out.append(' ').append(message).toString();
    }
}
