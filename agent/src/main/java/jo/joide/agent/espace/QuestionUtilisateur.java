package jo.joide.agent.espace;

import java.util.List;

/**
 * Une question à l'utilisateur (outil ask_user) : une à quatre
 * questions par appel, deux à quatre options chacune — l'hôte
 * propose toujours « Autre » en texte libre et « passer » (ADR-É9,
 * l'UI de l'agent).
 */
public final class QuestionUtilisateur {
    /** La question posée. */
    public final String question;
    /** Les options proposées (2 à 4). */
    public final List<String> options;

    /** @param question la question @param options les options */
    public QuestionUtilisateur(String question, List<String> options) {
        this.question = question;
        this.options = List.copyOf(options);
    }
}
