package jo.joide.agent.outils;

import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonValue;

/**
 * Les schémas d'arguments des outils : des LITTÉRAUX JSON analysés
 * par le parseur maison (ADR-028) — stricts par construction
 * (propriétés fermées, types simples), jamais de fabrique de schéma
 * à la main. Un schéma ouvert désactiverait silencieusement le mode
 * strict des fournisseurs (garde-fou de l'ADR-É9).
 */
final class Schemas {

    private Schemas() {
    }

    /** @param litteral le schéma en JSON @return la valeur analysée, épinglée */
    static JsonValue json(String litteral) {
        Json.Document document = Json.parse(litteral);
        if (document.root == null || !document.problems.isEmpty()) {
            throw new IllegalArgumentException(
                    "bad tool schema: " + litteral);
        }
        return document.root;
    }

    /** Le schéma d'un objet avec propriétés (aucun argument requis par défaut). */
    static JsonValue objet(String proprietes) {
        return json("{\"type\":\"object\",\"properties\":"
                + proprietes + ",\"additionalProperties\":false}");
    }

    /** Le schéma sans argument. */
    static JsonValue aucun() {
        return json("{\"type\":\"object\",\"properties\":{},"
                + "\"additionalProperties\":false}");
    }

    /** Lit un argument texte requis. */
    static String texte(JsonValue arguments, String cle) {
        String valeur = arguments.member(cle).asString();
        if (valeur == null || valeur.isEmpty()) {
            throw new IllegalArgumentException(
                    "missing string argument: " + cle);
        }
        return valeur;
    }

    /** Lit un argument texte facultatif (défaut si absent). */
    static String texte(JsonValue arguments, String cle, String defaut) {
        String valeur = arguments.member(cle).asString();
        return valeur == null ? defaut : valeur;
    }

    /** Lit un argument entier facultatif. */
    static int entier(JsonValue arguments, String cle, int defaut) {
        long valeur = arguments.member(cle).asLong();
        return valeur < 0 ? defaut : (int) valeur;
    }

    /** Lit un argument booléen facultatif. */
    static boolean booleen(JsonValue arguments, String cle,
                           boolean defaut) {
        Boolean valeur = arguments.member(cle).asBoolean();
        return valeur == null ? defaut : valeur;
    }
}
