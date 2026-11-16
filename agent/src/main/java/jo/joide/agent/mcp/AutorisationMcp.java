package jo.joide.agent.mcp;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jo.joide.agent.TransportHttp;
import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonEcrire;
import jo.joide.deps.json.JsonValue;
import jo.joide.platform.CancelToken;

/**
 * L'autorisation MCP — OAuth 2.1 conforme à la spécification (lot R,
 * ADR-063) :
 *
 * <ol>
 *   <li>le 401 du serveur nomme ses métadonnées de ressource protégée
 *       (RFC 9728), qui nomment son serveur d'autorisation — sans
 *       elles, son origine propre ;</li>
 *   <li>les métadonnées de l'autorisation (RFC 8414 ou OpenID) donnent
 *       les points de terminaison ;</li>
 *   <li>l'app s'ENREGISTRE dynamiquement (RFC 7591) comme client
 *       public pour l'URI de redirection DE CETTE signature (un port
 *       de bouclage diffère à chaque fois et les serveurs comparent
 *       l'URI exactement) ;</li>
 *   <li>le navigateur fait le code d'autorisation avec PKCE S256
 *       OBLIGATOIRE (un serveur sans S256 est refusé avec un message
 *       qui conseille le jeton en en-tête) et le paramètre
 *       {@code resource} (RFC 8707) ;</li>
 *   <li>le code s'échange contre des jetons ; {@link #rafraichir}
 *       les renouvelle (rotation tolérée : l'ancien tient quand aucun
 *       nouveau ne vient).</li>
 * </ol>
 *
 * <p>Le flux passe par le PORT {@link TransportHttp} partagé ; les
 * secrets ne voyagent jamais dans une URL.</p>
 */
public final class AutorisationMcp {

    private static final SecureRandom ALEA = new SecureRandom();

    /** Tout ce qu'une signature porte entre le navigateur et la redirection. */
    public static final class Tentative {

        /** L'URL à ouvrir dans le navigateur. */
        public final String urlAutorisation;
        /** L'état anti-CSRF (à vérifier contre le redirect). */
        public final String etat;
        /** Le vérificateur PKCE. */
        public final String verificateur;
        /** L'URI de redirection de CETTE signature. */
        public final String redirectUri;
        /** Le point de terminaison des jetons. */
        public final String pointJetons;
        /** L'identifiant client enregistré. */
        public final String clientId;
        /** Le secret client, ou null. */
        public final String clientSecret;
        /** La ressource demandée (RFC 8707). */
        public final String ressource;

        Tentative(String urlAutorisation, String etat,
                  String verificateur, String redirectUri,
                  String pointJetons, String clientId,
                  String clientSecret, String ressource) {
            this.urlAutorisation = urlAutorisation;
            this.etat = etat;
            this.verificateur = verificateur;
            this.redirectUri = redirectUri;
            this.pointJetons = pointJetons;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.ressource = ressource;
        }
    }

    private AutorisationMcp() {
    }

    /**
     * Découvre la configuration d'autorisation du serveur, enregistre
     * un client et construit l'URL de navigateur.
     *
     * @param urlServeur     l'URL du serveur MCP
     * @param redirectUri    l'URI de redirection de cette signature
     * @param defiAuth       le défi WWW-Authenticate du 401, ou null
     * @param transport      le transport partagé
     * @param jeton          l'annulation
     * @return la tentative prête pour le navigateur
     */
    public static Tentative commencer(String urlServeur,
                                      String redirectUri,
                                      String defiAuth,
                                      TransportHttp transport,
                                      CancelToken jeton) {
        String ressource = ressourceCanonique(urlServeur);
        Map<String, String> defi = analyserDefi(defiAuth);
        JsonValue metadonneesRessource = metadonneesRessource(
                urlServeur, defi.get("resource_metadata"), transport,
                jeton);
        String emetteur = null;
        if (metadonneesRessource != null) {
            JsonValue serveurs = metadonneesRessource
                    .member("authorization_servers");
            if (serveurs.type() == JsonValue.Type.ARRAY
                    && !serveurs.items().isEmpty()) {
                emetteur = serveurs.items().get(0).asString();
            }
        }
        if (emetteur == null) {
            emetteur = origine(urlServeur);
        }
        JsonValue meta = metadonneesAutorisation(emetteur, transport,
                jeton);
        String authorize = meta == null ? null
                : meta.member("authorization_endpoint").asString();
        String jetons = meta == null ? null
                : meta.member("token_endpoint").asString();
        String enregistrement = meta == null ? null
                : meta.member("registration_endpoint").asString();
        if (authorize == null) {
            authorize = origine(emetteur) + "/authorize";
        }
        if (jetons == null) {
            jetons = origine(emetteur) + "/token";
        }
        if (enregistrement == null && meta == null) {
            enregistrement = origine(emetteur) + "/register";
        }
        if (enregistrement == null) {
            throw new ErreurMcp("This server's sign-in does not let "
                    + "apps register themselves. Add a token in the "
                    + "server's headers instead (for example "
                    + "Authorization: Bearer <token>).");
        }
        JsonValue methodes = meta == null ? null
                : meta.member("code_challenge_methods_supported");
        if (methodes != null
                && methodes.type() == JsonValue.Type.ARRAY) {
            boolean s256 = false;
            for (JsonValue methode : methodes.items()) {
                if ("S256".equals(methode.asString())) {
                    s256 = true;
                    break;
                }
            }
            if (!s256) {
                throw new ErreurMcp("This server's sign-in does not "
                        + "support PKCE, which JoIDE requires. Add a "
                        + "token in the server's headers instead (for "
                        + "example Authorization: Bearer <token>).");
            }
        }
        String[] client = enregistrerClient(enregistrement,
                redirectUri, transport, jeton);
        String portee = defi.get("scope");
        if (portee == null && metadonneesRessource != null) {
            JsonValue portees = metadonneesRessource
                    .member("scopes_supported");
            if (portees.type() == JsonValue.Type.ARRAY) {
                List<String> valeurs = new ArrayList<>();
                for (JsonValue p : portees.items()) {
                    if (p.asString() != null) {
                        valeurs.add(p.asString());
                    }
                }
                portee = String.join(" ", valeurs);
            }
        }
        String verificateur = jetonAleatoire(48);
        String etat = jetonAleatoire(24);
        StringBuilder url = new StringBuilder(authorize);
        url.append(authorize.contains("?") ? '&' : '?');
        Map<String, String> parametres = new LinkedHashMap<>();
        parametres.put("response_type", "code");
        parametres.put("client_id", client[0]);
        parametres.put("redirect_uri", redirectUri);
        parametres.put("code_challenge", defiPcke(verificateur));
        parametres.put("code_challenge_method", "S256");
        parametres.put("state", etat);
        parametres.put("resource", ressource);
        if (portee != null && !portee.isBlank()) {
            parametres.put("scope", portee);
        }
        boolean premier = true;
        for (Map.Entry<String, String> entree : parametres
                .entrySet()) {
            if (!premier) {
                url.append('&');
            }
            premier = false;
            url.append(encode(entree.getKey())).append('=')
                    .append(encode(entree.getValue()));
        }
        return new Tentative(url.toString(), etat, verificateur,
                redirectUri, jetons, client[0], client[1], ressource);
    }

    /** Échange le code de la redirection contre des jetons. */
    public static JetonsMcp echanger(Tentative tentative, String code,
                                     TransportHttp transport,
                                     CancelToken jeton) {
        return requeteJetons(tentative.pointJetons,
                Map.of("grant_type", "authorization_code",
                        "code", code,
                        "redirect_uri", tentative.redirectUri,
                        "client_id", tentative.clientId,
                        "code_verifier", tentative.verificateur,
                        "resource", tentative.ressource),
                tentative.clientSecret, transport, jeton,
                null);
    }

    /** Renouvelle une signature expirée (rotation du refresh tolérée). */
    public static JetonsMcp rafraichir(JetonsMcp jetons,
                                       TransportHttp transport,
                                       CancelToken jeton) {
        if (jetons.refresh == null) {
            throw new ErreurMcp(
                    "The sign-in expired. Sign in again.");
        }
        Map<String, String> formulaire = new LinkedHashMap<>();
        formulaire.put("grant_type", "refresh_token");
        formulaire.put("refresh_token", jetons.refresh);
        formulaire.put("client_id", jetons.clientId);
        if (jetons.ressource != null) {
            formulaire.put("resource", jetons.ressource);
        }
        return requeteJetons(jetons.pointJetons, formulaire,
                jetons.clientSecret, transport, jeton, jetons.refresh);
    }

    /** La requête de jetons : POST form-encodé, jamais les secrets dans l'URL. */
    private static JetonsMcp requeteJetons(String point,
                                           Map<String, String> champs,
                                           String clientSecret,
                                           TransportHttp transport,
                                           CancelToken jeton,
                                           String refreshPrecedent) {
        StringBuilder formulaire = new StringBuilder();
        boolean premier = true;
        for (Map.Entry<String, String> entree : champs.entrySet()) {
            if (!premier) {
                formulaire.append('&');
            }
            premier = false;
            formulaire.append(encode(entree.getKey())).append('=')
                    .append(encode(entree.getValue()));
        }
        if (clientSecret != null) {
            if (!premier) {
                formulaire.append('&');
            }
            formulaire.append("client_secret=")
                    .append(encode(clientSecret));
        }
        String corpsReponse;
        try {
            TransportHttp.Reponse reponse = transport.post(point,
                    Map.of("content-type",
                            "application/x-www-form-urlencoded",
                            "accept", "application/json"),
                    formulaire.toString(), jeton);
            corpsReponse = reponse.corps();
        } catch (TransportHttp.EchecHttp echec) {
            throw new ErreurMcp("The server did not issue a token: "
                    + resume(echec.corpsBrut));
        } catch (java.io.IOException e) {
            throw new ErreurMcp("The token endpoint is unreachable: "
                    + e.getMessage(), e);
        }
        Json.Document document = Json.parse(corpsReponse);
        JsonValue racine = document.root;
        if (racine == null || !document.problems.isEmpty()
                || racine.type() != JsonValue.Type.OBJECT) {
            throw new ErreurMcp(
                    "The server did not issue a token.");
        }
        String erreur = racine.member("error_description").asString();
        if (erreur == null) {
            erreur = racine.member("error").asString();
        }
        String acces = racine.member("access_token").asString();
        if (acces == null) {
            throw new ErreurMcp("The server did not issue a token"
                    + (erreur == null ? "." : ": " + erreur));
        }
        Long expireA = null;
        String expires = racine.member("expires_in").asString();
        if (expires == null) {
            long nombre = racine.member("expires_in").asLong();
            if (nombre > 0) {
                expireA = System.currentTimeMillis() + nombre * 1000;
            }
        } else {
            try {
                expireA = System.currentTimeMillis()
                        + Long.parseLong(expires) * 1000;
            } catch (NumberFormatException e) {
                // sans expiration énoncée : jamais périssable
            }
        }
        String refresh = racine.member("refresh_token").asString();
        return new JetonsMcp(acces, refresh != null ? refresh
                : refreshPrecedent, expireA, point,
                champs.get("client_id"), clientSecret,
                champs.get("resource"));
    }

    /** L'enregistrement dynamique du client (RFC 7591). */
    private static String[] enregistrerClient(String point,
                                              String redirectUri,
                                              TransportHttp transport,
                                              CancelToken jeton) {
        String corps = JsonEcrire.objet("client_name", "JoIDE",
                "redirect_uris", List.of(redirectUri),
                "grant_types", List.of("authorization_code",
                        "refresh_token"),
                "response_types", List.of("code"),
                "token_endpoint_auth_method", "none");
        String corpsReponse;
        try {
            TransportHttp.Reponse reponse = transport.post(point,
                    Map.of("content-type", "application/json",
                            "accept", "application/json"),
                    corps, jeton);
            corpsReponse = reponse.corps();
        } catch (TransportHttp.EchecHttp echec) {
            throw new ErreurMcp("The server would not register JoIDE "
                    + "for sign-in: " + resume(echec.corpsBrut));
        } catch (java.io.IOException e) {
            throw new ErreurMcp("The registration endpoint is "
                    + "unreachable: " + e.getMessage(), e);
        }
        Json.Document document = Json.parse(corpsReponse);
        JsonValue racine = document.root;
        String clientId = racine == null || !document.problems
                .isEmpty() ? null : racine.member("client_id")
                .asString();
        if (clientId == null) {
            throw new ErreurMcp("The server registered JoIDE without "
                    + "a client id.");
        }
        return new String[]{clientId,
                racine.member("client_secret").asString()};
    }

    /** RFC 9728 : l'URL du défi, puis les emplacements bien connus (le chemin précis d'abord). */
    private static JsonValue metadonneesRessource(String urlServeur,
                                                  String duDefi,
                                                  TransportHttp transport,
                                                  CancelToken jeton) {
        URI uri = URI.create(urlServeur);
        String chemin = uri.getRawPath() == null ? ""
                : uri.getRawPath();
        chemin = chemin.length() > 1 && chemin.endsWith("/")
                ? chemin.substring(0, chemin.length() - 1) : chemin;
        List<String> candidats = new ArrayList<>();
        if (duDefi != null && !duDefi.isBlank()) {
            candidats.add(duDefi);
        }
        String base = origine(urlServeur);
        if (!chemin.isEmpty() && !"/".equals(chemin)) {
            candidats.add(base + "/.well-known/oauth-protected-resource"
                    + chemin);
        }
        candidats.add(base + "/.well-known/oauth-protected-resource");
        return premierJson(candidats, transport, jeton);
    }

    /** RFC 8414 puis OpenID, chaque forme (chemin inséré puis ajouté). */
    private static JsonValue metadonneesAutorisation(String emetteur,
                                                     TransportHttp transport,
                                                     CancelToken jeton) {
        URI uri;
        try {
            uri = URI.create(emetteur);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String chemin = uri.getRawPath() == null ? ""
                : uri.getRawPath();
        chemin = chemin.length() > 1 && chemin.endsWith("/")
                ? chemin.substring(0, chemin.length() - 1) : chemin;
        String base = origine(emetteur);
        List<String> candidats = new ArrayList<>();
        if (chemin.isEmpty() || "/".equals(chemin)) {
            candidats.add(base + "/.well-known/oauth-authorization-server");
            candidats.add(base + "/.well-known/openid-configuration");
        } else {
            candidats.add(base
                    + "/.well-known/oauth-authorization-server" + chemin);
            candidats.add(base + "/.well-known/openid-configuration"
                    + chemin);
            candidats.add(base + chemin
                    + "/.well-known/openid-configuration");
        }
        return premierJson(candidats, transport, jeton);
    }

    /** Le premier GET JSON qui réussit (les échecs passent au suivant). */
    private static JsonValue premierJson(List<String> candidats,
                                         TransportHttp transport,
                                         CancelToken jeton) {
        for (String url : candidats) {
            try {
                TransportHttp.Reponse reponse = transport.get(url,
                        Map.of("accept", "application/json"), jeton);
                Json.Document document = Json.parse(reponse.corps());
                if (document.root != null && document.problems.isEmpty()
                        && document.root.type()
                        == JsonValue.Type.OBJECT) {
                    return document.root;
                }
            } catch (Exception e) {
                // le candidat suivant
            }
        }
        return null;
    }

    /** Le défi {@code Bearer realm="x", resource_metadata="…"} en paramètres. */
    static Map<String, String> analyserDefi(String entete) {
        Map<String, String> parametres = new LinkedHashMap<>();
        if (entete == null || entete.isBlank()) {
            return parametres;
        }
        String corps = entete.substring(entete.indexOf(' ') + 1);
        Pattern motif = Pattern.compile(
                "([A-Za-z_][A-Za-z0-9_-]*)\\s*=\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([^\\s,]+))");
        Matcher m = motif.matcher(corps);
        while (m.find()) {
            String valeur = m.group(2) != null ? m.group(2)
                    .replace("\\\"", "\"") : m.group(3);
            parametres.put(m.group(1).toLowerCase(), valeur);
        }
        return parametres;
    }

    /** L'URL du serveur comme audience du jeton : scheme et hôte en bas de casse, sans fragment ni slash final. */
    static String ressourceCanonique(String urlServeur) {
        URI uri = URI.create(urlServeur).normalize();
        StringBuilder sortie = new StringBuilder();
        sortie.append(uri.getScheme().toLowerCase()).append("://")
                .append(uri.getHost().toLowerCase());
        if (uri.getPort() >= 0 && uri.getPort() != portDefaut(
                uri.getScheme())) {
            sortie.append(':').append(uri.getPort());
        }
        String chemin = uri.getRawPath() == null ? "" : uri.getRawPath();
        sortie.append(chemin);
        // le slash final ne fait pas partie de l'audience (la forme
        // canonique du RFC 8707 observée)
        String rendu = sortie.toString();
        return rendu.endsWith("/") && rendu.length() > 1
                ? rendu.substring(0, rendu.length() - 1) : rendu;
    }

    private static int portDefaut(String scheme) {
        return "https".equals(scheme) ? 443 : 80;
    }

    private static String origine(String url) {
        URI uri = URI.create(url);
        StringBuilder sortie = new StringBuilder();
        sortie.append(uri.getScheme()).append("://")
                .append(uri.getHost());
        if (uri.getPort() >= 0 && uri.getPort() != portDefaut(
                uri.getScheme())) {
            sortie.append(':').append(uri.getPort());
        }
        return sortie.toString();
    }

    /** Le défi PKCE d'un vérificateur (S256 : SHA-256 en base64url sans remplissage). */
    public static String defiPcke(String verificateur) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] empreinte = sha256.digest(verificateur
                    .getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(empreinte);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing", e);
        }
    }

    /** Un jeton aléatoire en base64url. */
    static String jetonAleatoire(int octets) {
        byte[] aleatoire = new byte[octets];
        ALEA.nextBytes(aleatoire);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(aleatoire);
    }

    private static String encode(String valeur) {
        return java.net.URLEncoder.encode(valeur,
                StandardCharsets.UTF_8);
    }

    private static String resume(String texte) {
        String net = texte == null ? "" : texte.replace('\n', ' ')
                .trim();
        return net.length() > 160 ? net.substring(0, 160) + "…" : net;
    }
}
