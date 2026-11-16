package jo.joide.agent.mcp;

/**
 * Les erreurs du client MCP (lot R, ADR-063) : chaque genre porte sa
 * conséquence — la règle de sécurité centrale distingue ce qui se
 * retente (le serveur a PROVÉ qu'il n'a pas exécuté l'outil : session
 * expirée, identifiants refusés avant examen) de ce qui ne se retente
 * JAMAIS (une rupture en cours d'appel : l'outil a pu s'exécuter ou
 * non, un outil mutant n'est jamais lancé deux fois).
 */
public class ErreurMcp extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** @param message le diagnostic */
    public ErreurMcp(String message) {
        super(message);
    }

    /** @param message le diagnostic @param cause la cause */
    public ErreurMcp(String message, Throwable cause) {
        super(message, cause);
    }

    /** Le serveur a répondu une erreur JSON-RPC. */
    public static final class RefusServeur extends ErreurMcp {
        private static final long serialVersionUID = 1L;

        /** Le code JSON-RPC de l'erreur, ou null. */
        public final Integer code;

        /** @param message le message du serveur @param code le code JSON-RPC */
        public RefusServeur(String message, Integer code) {
            super(message);
            this.code = code;
        }
    }

    /** Le serveur veut des identifiants qu'il n'a pas reçus (401, ou 403 avec insufficient_scope). */
    public static final class AuthRequise extends ErreurMcp {
        private static final long serialVersionUID = 1L;

        /** Le défi WWW-Authenticate, qui nomme où découvrir comment se connecter. */
        public final String defi;

        /** @param defi le défi @param message le diagnostic */
        public AuthRequise(String defi, String message) {
            super(message == null
                    ? "This server needs you to sign in." : message);
            this.defi = defi;
        }
    }

    /** Le serveur a oublié cette session (404 sur une requête porteuse d'identifiant) — une ré-connexion recommence. */
    public static final class SessionPerimee extends ErreurMcp {
        private static final long serialVersionUID = 1L;

        /** La session a expiré. */
        public SessionPerimee() {
            super("The MCP session expired.");
        }
    }

    /** Le serveur n'a pas répondu à la méthode dans le délai accordé. */
    public static final class DelaiDepasse extends ErreurMcp {
        private static final long serialVersionUID = 1L;

        /** La méthode restée sans réponse. */
        public final String methode;

        /** @param methode la méthode @param delaiMs le délai accordé */
        public DelaiDepasse(String methode, long delaiMs) {
            super("The server did not answer " + methode
                    + " within " + delaiMs / 1000 + " s.");
            this.methode = methode;
        }
    }

    /** Un échec HTTP qui n'est ni un défi d'authentification ni une session expirée. */
    public static final class EchecHttp extends ErreurMcp {
        private static final long serialVersionUID = 1L;

        /** Le code HTTP reçu. */
        public final int statut;

        /** @param statut le code @param message le diagnostic */
        public EchecHttp(int statut, String message) {
            super(message);
            this.statut = statut;
        }
    }
}
