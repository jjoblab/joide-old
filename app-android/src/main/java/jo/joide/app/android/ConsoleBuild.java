package jo.joide.app.android;

import java.util.List;

import jo.joide.deps.json.JsonValue;

/**
 * Formatage des lignes de la console de build : chaque événement du
 * protocole P6 devient une ligne lisible, pure fonction du cadre —
 * testable sur la JVM sans Android.
 *
 * <p>Lot K (ADR-É6) : le formatage gagne la STRUCTURE que la console
 * v2 affiche — nature colorée, phase du pipeline, cible de problème
 * ouvrable — sans que la moindre logique ne migre dans la vue. La
 * méthode {@link #ligne(String, long, JsonValue)} (lot F) reste : le
 * texte d'une ligne structurée est exactement le sien.</p>
 */
public final class ConsoleBuild {

    /** Nombre maximal de lignes gardées (la console est plafonnée). */
    public static final int PLAFOND_LIGNES = 400;

    /** La nature d'une ligne — couleur et filtre du transcript. */
    public enum Nature {
        /** Ligne du système (démon, écarts, bilans). */
        SYSTEME,
        /** Ligne de phase du pipeline. */
        PHASE,
        /** Ligne de tâche. */
        TACHE,
        /** Ligne d'erreur. */
        ERREUR,
        /** Ligne d'avertissement. */
        AVERTISSEMENT
    }

    /** Les phases du pipeline JoIDE déduites des noms de tâches. */
    public enum Phase {
        /** generateSources, processResources. */
        PREPARATION,
        /** compileKotlin, compileJava. */
        COMPILATION,
        /** jar. */
        ASSEMBLAGE
    }

    /** Une ligne structurée du transcript : nature, texte, liens. */
    public record Ligne(Nature nature, String texte, Phase phase,
                        String tache, String cible) {

        /** Une ligne système (démon, bilans, écarts). */
        public static Ligne systeme(String texte) {
            return new Ligne(Nature.SYSTEME, texte, null, null, null);
        }

        /** Une ligne de phase du pipeline. */
        public static Ligne phase(String texte, Phase phase) {
            return new Ligne(Nature.PHASE, texte, phase, null, null);
        }

        /** Une ligne de tâche. */
        public static Ligne tache(String texte, Phase phase, String tache) {
            return new Ligne(Nature.TACHE, texte, phase, tache, null);
        }

        /** Une ligne d'erreur, avec sa cible ouvrable éventuelle. */
        public static Ligne erreur(String texte, String tache, String cible) {
            return new Ligne(Nature.ERREUR, texte, null, tache, cible);
        }
    }

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

    /**
     * Structure un événement du démon en lignes du transcript v2 : le
     * bilan final porte ses problèmes en lignes d'erreur séparées,
     * chaque tâche porte sa phase, une tâche échouée est une erreur.
     *
     * @param type  le type d'événement
     * @param id    l'identifiant de requête lié
     * @param corps le cadre complet de l'événement
     * @return les lignes (une la plupart du temps), jamais null
     */
    public static List<Ligne> structurer(String type, long id,
                                         JsonValue corps) {
        String texte = ligne(type, id, corps);
        return switch (type == null ? "" : type) {
            case "task" -> {
                String tache = corps.member("name").asString();
                String statut = corps.member("status").asString();
                if ("FAILED".equals(statut)) {
                    yield List.of(new Ligne(Nature.ERREUR,
                            texte.trim(), phaseDe(tache), tache, null));
                }
                yield List.of(Ligne.tache(texte.trim(),
                        phaseDe(tache), tache));
            }
            case "build-finished" -> {
                List<Ligne> lignes = new java.util.ArrayList<>();
                lignes.add(Ligne.systeme(texte.split("\n")[0].trim()));
                for (JsonValue probleme
                        : corps.member("problems").items()) {
                    String brut = probleme.asString();
                    lignes.add(Ligne.erreur(brut, tacheDe(brut),
                            cibleDe(brut)));
                }
                yield lignes;
            }
            case "build-refused", "error" ->
                    List.of(Ligne.erreur(texte, null, null));
            case "hello", "warmed", "pong", "build-started" ->
                    List.of(Ligne.systeme(texte));
            default -> List.of(Ligne.systeme(texte));
        };
    }

    /** La phase du pipeline d'après le nom de tâche (module:verbe). */
    public static Phase phaseDe(String nomTache) {
        if (nomTache == null) {
            return null;
        }
        if (nomTache.endsWith(":compileKotlin")
                || nomTache.endsWith(":compileJava")) {
            return Phase.COMPILATION;
        }
        if (nomTache.endsWith(":jar")) {
            return Phase.ASSEMBLAGE;
        }
        if (nomTache.endsWith(":generateSources")
                || nomTache.endsWith(":processResources")) {
            return Phase.PREPARATION;
        }
        return null;
    }

    /**
     * La cible ouvrable d'un problème (« fichier:ligne »), extraite du
     * texte transporté par le protocole — ou null si le problème n'est
     * pas localisé (le pipeline actuel ne transporte pas la
     * localisation : le lien reste alors honnêtement absent, jamais
     * faux ; ADR-052).
     */
    public static String cibleDe(String probleme) {
        if (probleme == null) {
            return null;
        }
        java.util.regex.Matcher cible = MOTIF_CIBLE
                .matcher(probleme);
        if (cible.find()) {
            return cible.group(1) + ":" + Integer.parseInt(
                    cible.group(2));
        }
        return null;
    }

    /** Le nom de tâche en tête d'un problème transporté, ou null. */
    public static String tacheDe(String probleme) {
        if (probleme == null) {
            return null;
        }
        int separation = probleme.indexOf(" : ");
        return separation > 0 ? probleme.substring(0, separation)
                : null;
    }

    private static final java.util.regex.Pattern MOTIF_CIBLE =
            java.util.regex.Pattern.compile(
                    "(\\S+\\.(?:java|kt|kts)):(\\d+)");

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
