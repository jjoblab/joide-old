package jo.joide.agent.mcp;

import java.util.function.Consumer;

/**
 * Le fil sous un {@link ClientMcp} (lot R, ADR-063) : il délivre les
 * messages JSON-RPC au serveur et remet chaque message reçu au
 * rappel donné au démarrage. Un transport dont les réponses reviennent
 * sur la requête elle-même (Streamable HTTP) les remet AVANT que
 * {@code envoyer} ne revienne ; les autres les remettent depuis un
 * lecteur à eux.
 */
public interface TransportMcp extends AutoCloseable {

    /** Un message JSON-RPC reçu (objet ou tableau d'objets en texte JSON). */
    interface Remettre {
        /** @param message le message reçu en texte JSON */
        void message(String message);
    }

    /** La fermeture du transport par son propre lecteur (cause, ou null). */
    interface OnFerme {
        /** @param cause la cause de la fermeture, ou null */
        void onFerme(Throwable cause);
    }

    /**
     * Ouvre le transport.
     *
     * @param remettre reçoit chaque message du serveur
     * @param onFerme une fois, quand le transport s'en va
     * @throws Exception si l'ouverture échoue (défi d'auth, échec HTTP,
     *                   délai)
     */
    void demarrer(Remettre remettre, OnFerme onFerme) throws Exception;

    /**
     * Envoie un message (texte JSON).
     *
     * @param message le message
     * @throws Exception si l'envoi échoue
     */
    void envoyer(String message) throws Exception;

    /**
     * La version du protocole négociée par l'initialize (pour un
     * transport qui la porte en en-tête).
     *
     * @param versionProtocole la version fixée
     */
    default void negocie(String versionProtocole) {
        // rien par défaut
    }

    @Override
    void close();
}
