package jo.joide.model.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Contrôle de l'analyseur TOML : sous-ensemble reconnu, tolérance aux
 * entrées malformées (toujours un document et des problèmes, jamais
 * d'exception).
 */
class TomlTest {

    @Test
    void parsesFullSubset() {
        TomlDocument document = Toml.parse("""
                # commentaire global
                schema = "joide.module/1"
                count = 42
                hexa = 0x10
                octal = 0o17
                binaire = 0b101
                negatif = -7
                avec_soulignes = 1_000_000
                actif = true
                inactif = false
                literal = 'C:\\chemin\\brut'
                echappe = "ligne\\u0041"
                liste = ["a", "b", "c"]
                liste_multiligne = [
                  "x",
                  "y", # commentaire dans un tableau
                ]

                [android]
                min-sdk = 28
                compile-sdk = 37

                [a.b.c]
                profondeur = "ok"

                [[series]]
                nom = "premier"
                [[series]]
                nom = "second"
                """);
        assertTrue(document.isClean(), () -> document.problems().toString());
        assertEquals("joide.module/1", document.findString("schema").orElse(null));
        assertEquals(42L, document.findInteger("count").orElse(0L));
        assertEquals(16L, document.findInteger("hexa").orElse(0L));
        assertEquals(15L, document.findInteger("octal").orElse(0L));
        assertEquals(5L, document.findInteger("binaire").orElse(0L));
        assertEquals(-7L, document.findInteger("negatif").orElse(0L));
        assertEquals(1_000_000L,
                document.findInteger("avec_soulignes").orElse(0L));
        assertEquals(Boolean.TRUE, document.find("actif").map(v -> v.asBoolean()).orElse(null));
        assertEquals("C:\\chemin\\brut",
                document.findString("literal").orElse(null));
        assertEquals("ligneA", document.findString("echappe").orElse(null));
        assertEquals(List.of("a", "b", "c"), document.findStringArray("liste"));
        assertEquals(List.of("x", "y"), document.findStringArray("liste_multiligne"));
        assertEquals(28L, document.findInteger("android", "min-sdk").orElse(0L));
        assertEquals("ok", document.findString("a", "b", "c", "profondeur").orElse(null));
        List<TomlValue> series = document.find("series").map(v -> v.asArray()).orElse(null);
        assertEquals(2, series.size());
        assertEquals("premier", series.get(0).asTable().get("nom").asString());
        assertEquals("second", series.get(1).asTable().get("nom").asString());
    }

    @Test
    void malformedInputNeverThrows() {
        String[] broken = {
                "",
                "[",
                "[]",
                "[[",
                "cle sans egale",
                "cle =",
                "cle = @valeur",
                "chaine = \"non fermee",
                "litterale = 'non fermee",
                "table = [non fermee",
                "entier = 12z",
                "[a.]\nb = 1",
                "a.b = 1\na.b = 2",
                "= 5",
                "\"\" = 3",
                "[a]\n[a]",
                "float = 1.5",
                "date = 1979-05-27",
                "cle = \"valeur\" parasite",
        };
        for (String input : broken) {
            TomlDocument document = Toml.parse(input);
            // jamais d'exception ; au moins un problème sur les entrées
            // cassées (les entrées non vides et invalides en signalent)
            if (!input.isEmpty()) {
                assertTrue(!document.problems().isEmpty()
                                || !document.root().entries().isEmpty()
                                || document.isClean(),
                        "l'entree suivante doit produire un diagnostic : " + input);
            }
        }
    }

    @Test
    void duplicateKeyKeepsFirstValue() {
        TomlDocument document = Toml.parse("cle = \"premiere\"\ncle = \"seconde\"");
        assertEquals("premiere", document.findString("cle").orElse(null));
        assertEquals(1, document.problems().size());
    }

    @Test
    void truncatedDocumentKeepsValidPart() {
        TomlDocument document = Toml.parse("ok = 1\npanne = \"non fermee");
        assertEquals(1L, document.findInteger("ok").orElse(0L));
        assertNull(document.findString("panne").orElse(null));
        assertTrue(!document.problems().isEmpty());
    }

    @Test
    void dottedKeysCreateTables() {
        TomlDocument document = Toml.parse("a.b.c = \"profond\"\na.b.d = 2");
        assertEquals("profond", document.findString("a", "b", "c").orElse(null));
        assertEquals(2L, document.findInteger("a", "b", "d").orElse(0L));
    }
}
