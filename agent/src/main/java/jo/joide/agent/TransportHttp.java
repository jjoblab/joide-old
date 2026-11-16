package jo.joide.agent;

import java.util.List;
import java.util.Map;

import jo.joide.platform.CancelToken;

/**
 * Le port de transport HTTP unique derrière les fournisseurs
 * (ADR-É9 : « un seul transport … rend toute la couche testable
 * hors ligne en rejouant des trames SSE enregistrées »). Trois
 * méthodes : un POST en flux SSE, un POST simple, un GET simple.
 *
 * <p>Le transport ne retente QUE les échecs d'avant-émission
 * (connexion refusée avant l'envoi) — jamais un flux coupé en pleine
 * réponse : les octets déjà affichés ne se dupliquent pas. Les 429
 * ne se retentent pas ici : la boucle les attend VISIBLEMENT.
 * L'implémentation OkHttp (D-5, ADR-056) vit dans
 * {@code TransportOkHttp} ; les tests rejouent des trames par un
 * faux.</p>
 */
public interface TransportHttp {

    /**
     * La réponse d'un POST en flux : statut, en-têtes et trames SSE.
     */
    interface ReponseSse {
        /** @return le code HTTP (200 attendu) */
        int statut();

        /** @param nom l'en-tête cherché @return sa première valeur, ou null */
        String entete(String nom);

        /**
         * Lit la trame suivante du flux.
         *
         * @return la trame, ou null à la fin du flux
         * @throws java.io.IOException si le flux meurt en cours de lecture
         */
        TrameSse suivante() throws java.io.IOException;
    }

    /** Une trame SSE : événement nommé (facultatif) et données. */
    final class TrameSse {
        /** Le nom d'événement (champ {@code event:}), ou null. */
        public final String evenement;
        /** Les données assemblées (champs {@code data:} de la trame). */
        public final String donnees;

        /** @param evenement le nom d'événement ou null @param donnees les données */
        public TrameSse(String evenement, String donnees) {
            this.evenement = evenement;
            this.donnees = donnees;
        }
    }

    /**
     * La réponse d'une requête simple : statut, en-têtes et corps.
     */
    interface Reponse {
        /** @return le code HTTP */
        int statut();

        /** @param nom l'en-tête cherché @return sa première valeur, ou null */
        String entete(String nom);

        /** @return le corps en texte */
        String corps() throws java.io.IOException;
    }

    /**
     * POST en flux server-sent events.
     *
     * @param url     l'URL absolue
     * @param entetes les en-têtes (authentification, contenu)
     * @param corps   le corps JSON
     * @param jeton   l'annulation (le flux se ferme)
     * @return la réponse en flux (statut 2xx seulement)
     * @throws EchecHttp si le fournisseur répond hors 2xx (le corps
     *                   d'erreur voyage avec, jamais jeté)
     * @throws java.io.IOException si la connexion échoue
     */
    ReponseSse postSse(String url, Map<String, String> entetes,
                       String corps, CancelToken jeton)
            throws java.io.IOException;

    /**
     * POST simple.
     *
     * @param url     l'URL absolue
     * @param entetes les en-têtes
     * @param corps   le corps
     * @param jeton   l'annulation
     * @return la réponse
     * @throws java.io.IOException si la connexion échoue
     */
    Reponse post(String url, Map<String, String> entetes, String corps,
                 CancelToken jeton) throws java.io.IOException;

    /**
     * GET simple.
     *
     * @param url     l'URL absolue
     * @param entetes les en-têtes
     * @param jeton   l'annulation
     * @return la réponse
     * @throws java.io.IOException si la connexion échoue
     */
    Reponse get(String url, Map<String, String> entetes,
                CancelToken jeton) throws java.io.IOException;

    /**
     * La réponse OUVERTE : le corps se lit ligne à ligne (le lot R,
     * ADR-063 : le client MCP Streamable HTTP décide au vu des
     * EN-TÊTES si le corps est un JSON complet ou un flux
     * d'événements — la lecture doit rester vivante pour s'arrêter à
     * sa propre réponse).
     */
    interface ReponseOuverte {

        /** @return le code HTTP */
        int statut();

        /** @param nom l'en-tête cherché @return sa première valeur, ou null */
        String entete(String nom);

        /**
         * @return la ligne suivante du corps, ou null à la fin
         * @throws java.io.IOException si la lecture échoue
         */
        String ligne() throws java.io.IOException;

        /**
         * Ferme le corps AVANT sa fin (l'annulation d'une lecture,
         * la clôture d'une session) — la lecture bloquée en profite
         * et la connexion est rendue au pool.
         */
        default void fermer() {
            // rien par défaut : un corps entièrement lu se ferme seul
        }
    }

    /**
     * POST avec corps OUVERT (lot R, ADR-063 : le transport MCP).
     *
     * @param url     l'URL absolue
     * @param entetes les en-têtes
     * @param corps   le corps
     * @param jeton   l'annulation
     * @return la réponse ouverte (statut 2xx seulement)
     * @throws EchecHttp si le serveur répond hors 2xx (le corps
     *                   d'erreur voyage avec)
     * @throws java.io.IOException si la connexion échoue
     */
    default ReponseOuverte postOuvert(String url,
                                      Map<String, String> entetes,
                                      String corps, CancelToken jeton)
            throws java.io.IOException {
        throw new UnsupportedOperationException(
                "postOuvert not implemented by this transport");
    }

    /**
     * GET en flux SSE (lot R, ADR-063 : le repli SSE historique du
     * client MCP ouvre un flux qui reste vivant).
     *
     * @param url     l'URL absolue
     * @param entetes les en-têtes
     * @param jeton   l'annulation
     * @return la réponse ouverte
     * @throws EchecHttp si le serveur répond hors 2xx
     * @throws java.io.IOException si la connexion échoue
     */
    default ReponseOuverte getSse(String url,
                                  Map<String, String> entetes,
                                  CancelToken jeton)
            throws java.io.IOException {
        throw new UnsupportedOperationException(
                "getSse not implemented by this transport");
    }

    /**
     * DELETE simple (lot R, ADR-063 : la clôture de session MCP).
     *
     * @param url     l'URL absolue
     * @param entetes les en-têtes
     * @param jeton   l'annulation
     * @return la réponse
     * @throws java.io.IOException si la connexion échoue
     */
    default Reponse supprimer(String url, Map<String, String> entetes,
                              CancelToken jeton)
            throws java.io.IOException {
        throw new UnsupportedOperationException(
                "supprimer not implemented by this transport");
    }

    /** La réponse d'erreur lisible d'un fournisseur (statut + corps). */
    final class EchecHttp extends java.io.IOException {
        private static final long serialVersionUID = 1L;

        /** Le code HTTP. */
        public final int statut;
        /** Les en-têtes de la réponse (pour Retry-After, etc.) — jamais sérialisés. */
        public final transient Map<String, List<String>> entetes;
        /** Le corps d'erreur brut. */
        public final String corpsBrut;

        /** @param statut le code @param entetes les en-têtes @param corps le corps brut */
        public EchecHttp(int statut, Map<String, List<String>> entetes,
                         String corps) {
            super("HTTP " + statut + ": " + corps);
            this.statut = statut;
            this.entetes = entetes;
            this.corpsBrut = corps == null ? "" : corps;
        }
    }
}
