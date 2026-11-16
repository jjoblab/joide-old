package jo.joide.agent.mcp;

import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonEcrire;
import jo.joide.deps.json.JsonValue;
import jo.joide.platform.Clock;

/**
 * Une signature MCP stockée (lot R, ADR-063) : le jeton d'accès, son
 * refresh, ses points de terminaison et l'identifiant client — elle
 * se rafraîchit seule, et la ressource pour laquelle elle a été émise
 * repart au refresh (RFC 8707). Sérialisée en JSON maison pour le
 * stockage chiffré de l'hôte.
 */
public final class JetonsMcp {

    /** Le jeton d'accès. */
    public final String acces;
    /** Le jeton de refresh, ou null. */
    public final String refresh;
    /** L'expiration (epoch ms), ou null quand le serveur n'a rien dit. */
    public final Long expireA;
    /** Le point de terminaison des jetons. */
    public final String pointJetons;
    /** L'identifiant client enregistré. */
    public final String clientId;
    /** Le secret client, ou null (client public). */
    public final String clientSecret;
    /** La ressource pour laquelle les jetons ont été émis, ou null. */
    public final String ressource;

    /** @param acces le jeton d'accès @param refresh le refresh @param expireA l'expiration @param pointJetons le point des jetons @param clientId l'identifiant client @param clientSecret le secret @param ressource la ressource */
    public JetonsMcp(String acces, String refresh, Long expireA,
                     String pointJetons, String clientId,
                     String clientSecret, String ressource) {
        this.acces = acces;
        this.refresh = refresh;
        this.expireA = expireA;
        this.pointJetons = pointJetons;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.ressource = ressource;
    }

    /**
     * @param horloge l'horloge (null : système)
     * @return vrai si la signature expire dans moins d'une minute —
     *         rafraîchie AVANT d'échouer en cours d'appel
     */
    public boolean perimee(Clock horloge) {
        if (expireA == null) {
            return false;
        }
        long maintenant = (horloge == null
                ? jo.joide.platform.Clock.system() : horloge)
                .wallTimeMillis();
        return maintenant >= expireA - 60_000L;
    }

    /** @return la signature en JSON (le stockage chiffré de l'hôte la pose) */
    public String versJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"acces\":").append(JsonEcrire.valeur(acces));
        if (refresh != null) {
            json.append(",\"refresh\":")
                    .append(JsonEcrire.valeur(refresh));
        }
        if (expireA != null) {
            json.append(",\"expireA\":").append(expireA);
        }
        json.append(",\"pointJetons\":")
                .append(JsonEcrire.valeur(pointJetons));
        json.append(",\"clientId\":")
                .append(JsonEcrire.valeur(clientId));
        if (clientSecret != null) {
            json.append(",\"clientSecret\":")
                    .append(JsonEcrire.valeur(clientSecret));
        }
        if (ressource != null) {
            json.append(",\"ressource\":")
                    .append(JsonEcrire.valeur(ressource));
        }
        json.append("}");
        return json.toString();
    }

    /** @param json la signature sérialisée @return les jetons, ou null si illisible */
    public static JetonsMcp depuisJson(String json) {
        Json.Document document = Json.parse(json == null ? "" : json);
        JsonValue racine = document.root;
        if (racine == null || !document.problems.isEmpty()
                || racine.type() != JsonValue.Type.OBJECT) {
            return null;
        }
        String acces = racine.member("acces").asString();
        String point = racine.member("pointJetons").asString();
        String client = racine.member("clientId").asString();
        if (acces == null || point == null || client == null) {
            return null;
        }
        Long expireA = racine.member("expireA").asNumber() != null
                ? racine.member("expireA").asLong() : null;
        return new JetonsMcp(acces, racine.member("refresh").asString(),
                expireA, point, client,
                racine.member("clientSecret").asString(),
                racine.member("ressource").asString());
    }
}
