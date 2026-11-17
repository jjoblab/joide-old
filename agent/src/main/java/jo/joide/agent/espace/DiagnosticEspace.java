package jo.joide.agent.espace;

/**
 * Un diagnostic de fichier vu par l'agent : gravité, message,
 * position — la réponse de {@code get_diagnostics}.
 */
public final class DiagnosticEspace {
    /** La gravité. */
    public final String gravite;
    /** Le message du moteur. */
    public final String message;
    /** Le fichier relatif. */
    public final String fichier;
    /** La ligne (1-based), 0 si inconnue. */
    public final int ligne;

    /** @param gravite la gravité @param message le message @param fichier le fichier @param ligne la ligne */
    public DiagnosticEspace(String gravite, String message,
                            String fichier, int ligne) {
        this.gravite = gravite;
        this.message = message;
        this.fichier = fichier;
        this.ligne = ligne;
    }
}
