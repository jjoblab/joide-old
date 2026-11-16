package jo.joide.agent.mcp;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import jo.joide.platform.CancelToken;
import jo.joide.agent.TransportHttp;

/**
 * Le flux de signature OAuth en INTERFACE (lot U, ADR-066) : le
 * parcours MCP se complète — l'utilisateur TAPPE « Se connecter »,
 * le navigateur ouvre l'URL construite par {@link AutorisationMcp},
 * le serveur rappelle JoIDE par URI de retour, le code s'échange
 * contre des jetons et la connexion reprend.
 *
 * <p>Les tentatives VIVENT ICI, une par serveur nommé, tant que la
 * signature court : l'état anti-CSRF relie le rappel au serveur qui
 * l'a ouvert — un rappel sans tentative attendue est un REFUS
 * explicite, jamais un silence. L'URI de retour est le schéma
 * maison (aucun port local à défendre) ; les secrets ne voyagent
 * jamais dans l'URL de la demande, le code du rappel s'échange en
 * POST form-encodé côté {@link AutorisationMcp}.</p>
 */
public final class FluxOAuthMcp {

    /** L'URI de retour de JoIDE (l'intent-filter du manifeste l'écoute). */
    public static final String URI_RETOUR = "joide://mcp/oauth";

    /** Une tentative vivante : le serveur, la tentative OAuth. */
    private static final class Vivante {
        final String nom;
        final AutorisationMcp.Tentative tentative;

        Vivante(String nom, AutorisationMcp.Tentative tentative) {
            this.nom = nom;
            this.tentative = tentative;
        }
    }

    private final Map<String, Vivante> vivantes = new LinkedHashMap<>();
    private final TransportHttp transport;
    private final CancelToken jeton;

    /**
     * @param transport le transport HTTPS partagé (le même que le
     *                  gestionnaire : un seul port, jamais deux)
     * @param jeton     l'annulation partagée
     */
    public FluxOAuthMcp(TransportHttp transport, CancelToken jeton) {
        this.transport = transport;
        this.jeton = jeton == null ? CancelToken.NEVER : jeton;
    }

    /**
     * Ouvre la signature d'un serveur : découverte, enregistrement
     * dynamique du client, URL de navigateur. La tentative est
     * retenue jusqu'au rappel ou à l'échec.
     *
     * @param nom        le nom du serveur (journal, erreurs)
     * @param urlServeur l'URL du serveur MCP
     * @param defiAuth   le défi WWW-Authenticate du dernier 401, ou
     *                   null (la découverte sans défi reste tentée)
     * @return l'URL à ouvrir dans le navigateur
     * @throws ErreurMcp si le serveur ne permet pas la signature
     */
    public synchronized String demander(String nom, String urlServeur,
                                        String defiAuth) {
        AutorisationMcp.Tentative tentative = AutorisationMcp.commencer(
                urlServeur, URI_RETOUR, defiAuth, transport, jeton);
        vivantes.put(tentative.etat, new Vivante(nom, tentative));
        return tentative.urlAutorisation;
    }

    /**
     * Complète la signature depuis l'URI de retour du navigateur :
     * l'état anti-CSRF retrouve la tentative, le code s'échange
     * contre les jetons.
     *
     * @param urlRedirection l'URI de retour complète
     *                       (joide://mcp/oauth?code=…&state=…)
     * @return {nom du serveur, jetons} — le poseur les remet au
     *         gestionnaire sous l'identité du serveur
     * @throws ErreurMcp rappel inconnu, état divergent, code absent,
     *                   erreur du serveur d'autorisation
     */
    public synchronized Object[] completer(String urlRedirection) {
        Map<String, String> parametres = analyser(urlRedirection);
        String erreur = parametres.get("error_description");
        if (erreur == null) {
            erreur = parametres.get("error");
        }
        if (erreur != null) {
            throw new ErreurMcp(
                    "The sign-in came back with an error: " + erreur);
        }
        String code = parametres.get("code");
        String etat = parametres.get("state");
        if (code == null || code.isBlank() || etat == null) {
            throw new ErreurMcp(
                    "The sign-in callback is missing its code or "
                            + "state. Start the sign-in again.");
        }
        Vivante vivante = vivantes.remove(etat);
        if (vivante == null) {
            throw new ErreurMcp(
                    "This sign-in callback does not match a pending "
                            + "sign-in. Start the sign-in again.");
        }
        JetonsMcp jetons = AutorisationMcp.echanger(vivante.tentative,
                code, transport, jeton);
        return new Object[]{vivante.nom, jetons};
    }

    /** Oublie les tentatives (serveur retiré, gestionnaire vidés). */
    public synchronized void oublierTout() {
        vivantes.clear();
    }

    /** @return les paramètres de requête d'une URI de retour */
    static Map<String, String> analyser(String uri) {
        Map<String, String> sortie = new LinkedHashMap<>();
        if (uri == null) {
            return sortie;
        }
        int requete = uri.indexOf('?');
        if (requete < 0) {
            return sortie;
        }
        for (String morceau
                : uri.substring(requete + 1).split("&")) {
            int egale = morceau.indexOf('=');
            if (egale <= 0) {
                continue;
            }
            String cle = decode(morceau.substring(0, egale));
            String valeur = decode(morceau.substring(egale + 1));
            if (!cle.isEmpty()) {
                sortie.put(cle, valeur);
            }
        }
        return sortie;
    }

    private static String decode(String morceau) {
        try {
            return URLDecoder.decode(morceau, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return morceau;
        }
    }
}
