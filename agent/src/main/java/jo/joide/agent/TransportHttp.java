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
