package jo.joide.agent.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jo.joide.deps.json.Json;
import jo.joide.deps.json.JsonEcrire;
import jo.joide.deps.json.JsonValue;

/**
 * La normalisation des schémas tiers (lot R) : un mauvais schéma
 * ferait échouer la REQUÊTE ENTIÈRE du fournisseur — les {@code $ref}
 * s'inlinent bornés, les tableaux de types réduisent, les enums ne
 * restent que de chaînes, le niveau supérieur se force en objet.
 */
class NormalisationSchemasTest {

    @Test
    void lesRefsSInlinentEtLesDefsSeLisent() {
        JsonValue schema = Json.parse("""
                {
                  "$defs": { "Id": { "type": "string" } },
                  "type": "object",
                  "properties": {
                    "id": { "$ref": "#/$defs/Id" },
                    "liste": { "type": "array",
                               "items": { "$ref": "#/$defs/Id" } }
                  },
                  "required": ["id"]
                }""").root;
        JsonValue nettoye = NormalisationSchemas.normaliser(schema);
        String texte = JsonEcrire.valeur(nettoye);
        assertTrue(texte.contains("\"type\":\"string\""),
                "le $ref est inline : " + texte);
        assertTrue(!texte.contains("$ref"), texte);
        assertTrue(texte.contains("required"), texte);
    }

    @Test
    void lesTableauxDeTypesReduisentAuPremierNonNull() {
        JsonValue schema = Json.parse(
                "{\"type\":\"object\",\"properties\":{\"x\":"
                        + "{\"type\":[\"string\",\"null\"]}}}")
                .root;
        String texte = JsonEcrire.valeur(
                NormalisationSchemas.normaliser(schema));
        assertTrue(texte.contains("\"x\":{\"type\":\"string\"}}"),
                "un type SEUL, pas un tableau : " + texte);
        assertTrue(!texte.contains("[\"string\""), texte);
    }

    @Test
    void lesEnumsDeChainesSeulsPassent() {
        JsonValue schema = Json.parse("""
                {"type":"object","properties":{
                  "a":{"type":"string","enum":["rouge","vert"]},
                  "b":{"type":"string","enum":[1,2]}}}""").root;
        String texte = JsonEcrire.valeur(
                NormalisationSchemas.normaliser(schema));
        assertTrue(texte.contains("\"enum\":[\"rouge\",\"vert\"]"),
                texte);
        assertTrue(!texte.contains("\"enum\":[1,2]"),
                "les enums non-chaînes sont coupés (Gemini les "
                        + "exige) : " + texte);
    }

    @Test
    void unAnyOfAUneOptionSeDeplie() {
        JsonValue schema = Json.parse("""
                {"type":"object","properties":{
                  "x":{"anyOf":[{"type":"string"},{"type":"null"}]}}}""")
                .root;
        String texte = JsonEcrire.valeur(
                NormalisationSchemas.normaliser(schema));
        assertTrue(texte.contains("\"x\":{\"type\":\"string\"}}"),
                "l'option null écartée, la seule restante dépliée : "
                        + texte);
        assertTrue(!texte.contains("anyOf"), texte);
    }

    @Test
    void unTableauSansItemsRecoitUnDefaut() {
        JsonValue schema = Json.parse(
                "{\"type\":\"object\",\"properties\":{\"x\":"
                        + "{\"type\":\"array\"}}}").root;
        String texte = JsonEcrire.valeur(
                NormalisationSchemas.normaliser(schema));
        assertTrue(texte.contains("\"items\":{\"type\":\"string\"}"),
                texte);
    }

    @Test
    void leNiveauSuperieurSeForceEnObjet() {
        // un serveur qui envoie un schéma non-objet ne casse pas la
        // requête entière du fournisseur
        JsonValue schema = Json.parse(
                "{\"type\":\"string\"}").root;
        JsonValue nettoye = NormalisationSchemas.normaliser(schema);
        assertEquals("object", nettoye.member("type").asString());
        assertEquals("object", nettoye.member("properties").type()
                == JsonValue.Type.OBJECT ? "object" : "pas-objet");
    }

    @Test
    void uneReferenceRecusiveFinitEnObjetSimple() {
        JsonValue schema = Json.parse("""
                {"$defs":{"noeud":{"type":"object","properties":{
                    "enfant":{"$ref":"#/$defs/noeud"}}}},
                 "type":"object","properties":{
                    "racine":{"$ref":"#/$defs/noeud"}}}""").root;
        Map<String, JsonValue> defs = Map.of("#/$defs/noeud",
                Json.parse("{\"type\":\"object\",\"properties\":{"
                        + "\"enfant\":{\"$ref\":\"#/$defs/noeud\"}}}").root);
        // la profondeur 12 borne l'inlining : jamais une récursion
        JsonValue nettoye = NormalisationSchemas.normaliser(schema);
        String texte = JsonEcrire.valeur(nettoye);
        assertTrue(texte.length() < 4_000,
                "la récursion ne s'étend pas à l'infini : "
                        + texte.length());
    }

    @Test
    void lesFormatsInconnusDeGeminiPartent() {
        JsonValue schema = Json.parse("""
                {"type":"object","properties":{
                  "a":{"type":"string","format":"uri"},
                  "b":{"type":"string","format":"date-time"}}}""").root;
        String texte = JsonEcrire.valeur(
                NormalisationSchemas.normaliser(schema));
        assertTrue(texte.contains("date-time"), texte);
        assertTrue(!texte.contains("\"uri\""),
                "les formats hors liste surs sont coupés : " + texte);
    }
}
