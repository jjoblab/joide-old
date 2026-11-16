package jo.joide.agent.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonEcrire;
import jo.joide.deps.json.JsonValue;

/**
 * Les schémas d'outils TIERS, réduits au sous-ensemble que chaque
 * fournisseur accepte (lot R, ADR-063) : les serveurs écrivent du
 * JSON Schema complet ({@code $ref}, {@code $defs}, {@code oneOf},
 * tableaux de types, formats inconnus de Gemini) et UN MAUVAIS SCHÉMA
 * ÉCHOUE LA REQUÊTE ENTIÈRE — donc la réduction se fait avant envoi,
 * en ne gardant que ce qui veut dire quelque chose pour le modèle.
 */
public final class NormalisationSchemas {

    /** Les clés gardées (le reste est coupé, jamais interprété). */
    private static final List<String> GARDEES = List.of(
            "type", "description", "properties", "required", "items",
            "enum", "anyOf", "minimum", "maximum", "minItems",
            "maxItems", "title", "default", "nullable", "format");

    /** Les formats sûrs (les autres, Gemini ne les a jamais entendus). */
    private static final List<String> FORMATS_SURS = List.of(
            "date-time", "date", "enum");

    /** La profondeur maximale d'inlining d'un $ref (un type récursif finit en objet simple). */
    private static final int PROFONDEUR_MAX = 12;

    private NormalisationSchemas() {
    }

    /**
     * Normalise un schéma d'outil tiers.
     *
     * @param schema le schéma reçu du serveur
     * @return le schéma réduit (niveau supérieur FORCÉ en objet)
     */
    public static JsonValue normaliser(JsonValue schema) {
        Map<String, JsonValue> definitions = new LinkedHashMap<>();
        JsonValue defs = schema.member("$defs");
        if (defs.type() == JsonValue.Type.OBJECT) {
            for (String k : defs.keys()) {
                definitions.put("#/$defs/" + k, defs.member(k));
            }
        }
        JsonValue anciennes = schema.member("definitions");
        if (anciennes.type() == JsonValue.Type.OBJECT) {
            for (String k : anciennes.keys()) {
                definitions.put("#/definitions/" + k,
                        anciennes.member(k));
            }
        }
        Map<String, Object> nettoye = nettoyer(schema, definitions, 0);
        Map<String, Object> sortie = new LinkedHashMap<>();
        Object proprietes = nettoye.get("properties");
        if (!(proprietes instanceof Map)) {
            proprietes = new LinkedHashMap<String, Object>();
        }
        sortie.put("type", "object");
        sortie.put("properties", proprietes);
        Object requis = nettoye.get("required");
        List<?> liste = null;
        if (requis instanceof List<?> l) {
            liste = l;
        } else if (requis instanceof JsonValue v
                && v.type() == JsonValue.Type.ARRAY) {
            List<String> chaines = new ArrayList<>();
            for (JsonValue item : v.items()) {
                if (item.asString() != null) {
                    chaines.add(item.asString());
                }
            }
            liste = chaines;
        }
        if (liste != null && !liste.isEmpty()) {
            List<String> valides = new ArrayList<>();
            for (Object item : liste) {
                if (item instanceof String s
                        && ((Map<?, ?>) proprietes).containsKey(s)) {
                    valides.add(s);
                }
            }
            if (!valides.isEmpty()) {
                sortie.put("required", valides);
            }
        }
        return Json.parse(JsonEcrire.valeur(sortie)).root;
    }

    /** Le nettoyage récursif : structures mutables équivalentes. */
    private static Map<String, Object> nettoyer(JsonValue valeur,
                                                Map<String, JsonValue> definitions,
                                                int profondeur) {
        Map<String, Object> sortie = new LinkedHashMap<>();
        if (profondeur > PROFONDEUR_MAX
                || valeur.type() != JsonValue.Type.OBJECT) {
            sortie.put("type", "object");
            return sortie;
        }
        JsonValue reference = valeur.member("$ref");
        if (reference.type() == JsonValue.Type.STRING) {
            JsonValue cible = definitions.get(reference.asString());
            if (cible == null) {
                sortie.put("type", "object");
                return sortie;
            }
            return nettoyer(cible, definitions, profondeur + 1);
        }
        for (String cle : valeur.keys()) {
            JsonValue item = valeur.member(cle);
            switch (cle) {
                case "type" -> {
                    // ["string","null"] : le premier non-null est ce
                    // qui est voulu — les fournisseurs veulent UN type
                    if (item.type() == JsonValue.Type.ARRAY) {
                        String choisi = null;
                        for (JsonValue option : item.items()) {
                            String t = option.asString();
                            if (t != null && !"null".equals(t)) {
                                choisi = t;
                                break;
                            }
                        }
                        sortie.put("type", choisi == null ? "string"
                                : choisi);
                    } else if (item.type() == JsonValue.Type.STRING) {
                        sortie.put("type", item.asString());
                    }
                }
                case "properties" -> {
                    if (item.type() == JsonValue.Type.OBJECT) {
                        Map<String, Object> props = new LinkedHashMap<>();
                        for (String k : item.keys()) {
                            props.put(k, nettoyer(item.member(k),
                                    definitions, profondeur + 1));
                        }
                        sortie.put("properties", props);
                    }
                }
                case "items" -> sortie.put("items", nettoyer(item,
                        definitions, profondeur + 1));
                case "anyOf", "oneOf" -> {
                    if (item.type() == JsonValue.Type.ARRAY) {
                        List<Object> gardees = new ArrayList<>();
                        for (JsonValue option : item.items()) {
                            Map<String, Object> net = nettoyer(option,
                                    definitions, profondeur + 1);
                            if (!"null".equals(net.get("type"))) {
                                gardees.add(net);
                            }
                        }
                        if (gardees.size() == 1
                                && gardees.get(0) instanceof Map<?, ?> seule) {
                            for (Map.Entry<?, ?> entree : seule
                                    .entrySet()) {
                                sortie.putIfAbsent(
                                        String.valueOf(entree.getKey()),
                                        entree.getValue());
                            }
                        } else if (!gardees.isEmpty()) {
                            sortie.put("anyOf", gardees);
                        }
                    }
                }
                case "format" -> {
                    String format = item.asString();
                    if (format != null
                            && FORMATS_SURS.contains(format)) {
                        sortie.put("format", format);
                    }
                }
                case "enum" -> {
                    // les enums de chaînes seulement (Gemini les exige)
                    if (item.type() == JsonValue.Type.ARRAY) {
                        boolean chainesSeules = true;
                        for (JsonValue option : item.items()) {
                            if (option.type() != JsonValue.Type.STRING) {
                                chainesSeules = false;
                                break;
                            }
                        }
                        if (chainesSeules) {
                            List<String> valeurs = new ArrayList<>();
                            for (JsonValue option : item.items()) {
                                valeurs.add(option.asString());
                            }
                            sortie.put("enum", valeurs);
                        }
                    }
                }
                default -> {
                    // la valeur passe telle quelle : l'écriture
                    // compacte sérialise JsonValue, List, Map, scalaire
                    if (GARDEES.contains(cle)) {
                        sortie.put(cle, item);
                    }
                }
            }
        }
        if (!sortie.containsKey("type")
                && !sortie.containsKey("anyOf")) {
            sortie.put("type", sortie.containsKey("properties")
                    ? "object" : "string");
        }
        if ("array".equals(sortie.get("type"))
                && !sortie.containsKey("items")) {
            sortie.put("items", Map.of("type", "string"));
        }
        return sortie;
    }
}
