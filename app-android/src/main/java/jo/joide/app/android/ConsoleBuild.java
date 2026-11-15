package jo.joide.app.android;

import java.util.List;

import jo.joide.deps.json.JsonValue;

/**
 * Formatage des lignes de la console de build : chaque événement du
 * protocole P6 devient une ligne lisible, pure fonction du cadre —
 * testable sur la JVM sans Android.
 */
public final class ConsoleBuild {

    /** Nombre maximal de lignes gardées (la console est plafonnée). */
    public static final int PLAFOND_LIGNES = 400;

    private ConsoleBuild() {
    }

    /**
     * Formate un événement du démon en ligne de console.
     *
     * @param type le type d'événement (« hello », « task »…)
     * @param id   l'identifiant de requête lié, ou -1
     * @param corps le cadre complet de l'événement
     * @return la ligne, jamais null
     */
    public static String ligne(String type, long id, JsonValue corps) {
        return switch (type == null ? "" : type) {
            case "hello" -> "démon connecté (tas "
                    + (corps.member("maxHeapBytes").asLong()
                    / (1024L * 1024L)) + " Mio)";
            case "build-started" -> "build #" + id + " démarré";
            case "task" -> "  " + corps.member("name").asString() + " : "
                    + statutLisible(corps.member("status").asString());
            case "build-finished" -> finLisible(id, corps);
            case "build-refused" -> "build refusé ("
                    + corps.member("reason").asString() + ") : "
                    + corps.member("message").asString();
            case "warmed" -> "préchauffage fait ("
                    + corps.member("bytes").asLong() + " octets)";
            case "pong" -> "démon répondant";
            case "error" -> "erreur du démon : "
                    + corps.member("message").asString();
            default -> type + " #" + id;
        };
    }

    private static String finLisible(long id, JsonValue corps) {
        StringBuilder ligne = new StringBuilder("build #").append(id);
        if (corps.member("success").asBoolean()) {
            ligne.append(" réussi");
        } else {
            ligne.append(" échoué");
        }
        ligne.append(" — ").append(corps.member("executed").asLong())
                .append(" exécutée(s), ")
                .append(corps.member("fromCache").asLong())
                .append(" du cache, ")
                .append(corps.member("upToDate").asLong())
                .append(" à jour");
        Object duree = corps.member("wallMillis").asNumber();
        if (duree != null) {
            ligne.append(" en ").append(corps.member("wallMillis")
                    .asLong() / 1000.0).append(" s");
        }
        List<JsonValue> problemes = corps.member("problems").items();
        for (JsonValue probleme : problemes) {
            ligne.append('\n').append("  ! ").append(probleme.asString());
        }
        return ligne.toString();
    }

    private static String statutLisible(String statut) {
        return switch (statut == null ? "" : statut) {
            case "STARTED" -> "démarrée";
            case "SUCCEEDED" -> "réussie";
            case "FROM_CACHE" -> "restaurée du cache";
            case "UP_TO_DATE" -> "à jour";
            case "FAILED" -> "échouée";
            case "CANCELLED" -> "annulée";
            default -> statut == null ? "?" : statut;
        };
    }
}
