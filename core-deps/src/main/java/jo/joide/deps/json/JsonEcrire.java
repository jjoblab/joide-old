package jo.joide.deps.json;

import java.util.List;
import java.util.Map;

/**
 * Écriture JSON compacte, compagne du parseur maison (ADR-028) :
 * objets, tableaux, chaînes échappées, nombres et booléens. L'ordre
 * des clés est celui de l'insertion — les formats écrits ici (cadres
 * du démon, requêtes des fournisseurs LLM, sessions de l'agent) ne
 * dépendent jamais d'un ordre imposé par le lecteur.
 *
 * <p>Les valeurs null écrivent {@code null} ; une valeur inconnue
 * s'écrit comme chaîne de son {@code toString()} — jamais
 * d'exception de sérialisation sur un format de protocole.</p>
 */
public final class JsonEcrire {

    private JsonEcrire() {
    }

    /**
     * Écrit un objet JSON depuis des paires clé-valeur.
     *
     * @param entries paires dans l'ordre d'écriture
     * @return le texte JSON de l'objet
     */
    public static String objet(Object... entries) {
        StringBuilder sortie = new StringBuilder(64);
        objet(sortie, entries);
        return sortie.toString();
    }

    /**
     * Écrit un tableau JSON.
     *
     * @param valeurs les éléments dans l'ordre
     * @return le texte JSON du tableau
     */
    public static String tableau(List<?> valeurs) {
        StringBuilder sortie = new StringBuilder(64);
        valeur(sortie, valeurs);
        return sortie.toString();
    }

    /**
     * Écrit une valeur libre (objet passé en varargs, tableau, table,
     * valeur déjà analysée) — le format des corps de requête des
     * fournisseurs LLM.
     *
     * @param quelconque la valeur
     * @return le texte JSON
     */
    public static String valeur(Object quelconque) {
        StringBuilder sortie = new StringBuilder(64);
        valeur(sortie, quelconque);
        return sortie.toString();
    }

    /** Écrit un objet dans le constructeur donné. */
    private static void objet(StringBuilder sortie, Object... entries) {
        sortie.append('{');
        for (int i = 0; i + 1 < entries.length; i += 2) {
            if (i > 0) {
                sortie.append(',');
            }
            chaine(sortie, String.valueOf(entries[i]));
            sortie.append(':');
            valeur(sortie, entries[i + 1]);
        }
        sortie.append('}');
    }

    /** Écrit une valeur quelconque. */
    private static void valeur(StringBuilder sortie, Object valeur) {
        if (valeur == null) {
            sortie.append("null");
        } else if (valeur instanceof String texte) {
            chaine(sortie, texte);
        } else if (valeur instanceof Number || valeur instanceof Boolean) {
            sortie.append(valeur);
        } else if (valeur instanceof JsonValue json) {
            ecrireJson(sortie, json);
        } else if (valeur instanceof Map<?, ?> table) {
            sortie.append('{');
            boolean premier = true;
            for (Map.Entry<?, ?> entree : table.entrySet()) {
                if (!premier) {
                    sortie.append(',');
                }
                premier = false;
                chaine(sortie, String.valueOf(entree.getKey()));
                sortie.append(':');
                valeur(sortie, entree.getValue());
            }
            sortie.append('}');
        } else if (valeur instanceof List<?> liste) {
            sortie.append('[');
            for (int i = 0; i < liste.size(); i++) {
                if (i > 0) {
                    sortie.append(',');
                }
                valeur(sortie, liste.get(i));
            }
            sortie.append(']');
        } else if (valeur instanceof Object[] tableau) {
            valeur(sortie, List.of(tableau));
        } else {
            chaine(sortie, String.valueOf(valeur));
        }
    }

    /** Réécrit une valeur déjà analysée (aller-retour lecture/écriture). */
    private static void ecrireJson(StringBuilder sortie, JsonValue json) {
        switch (json.type()) {
            case NULL -> sortie.append("null");
            case STRING -> chaine(sortie, json.asString());
            case NUMBER -> sortie.append(json.asNumber());
            case BOOLEAN -> sortie.append(json.asBoolean());
            case ARRAY -> {
                sortie.append('[');
                List<JsonValue> elements = json.items();
                for (int i = 0; i < elements.size(); i++) {
                    if (i > 0) {
                        sortie.append(',');
                    }
                    ecrireJson(sortie, elements.get(i));
                }
                sortie.append(']');
            }
            case OBJECT -> {
                sortie.append('{');
                List<String> cles = json.keys();
                for (int i = 0; i < cles.size(); i++) {
                    if (i > 0) {
                        sortie.append(',');
                    }
                    chaine(sortie, cles.get(i));
                    sortie.append(':');
                    ecrireJson(sortie, json.member(cles.get(i)));
                }
                sortie.append('}');
            }
            default -> sortie.append("null");
        }
    }

    /** Écrit une chaîne échappée. */
    private static void chaine(StringBuilder sortie, String texte) {
        sortie.append('"');
        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            switch (c) {
                case '"' -> sortie.append("\\\"");
                case '\\' -> sortie.append("\\\\");
                case '\n' -> sortie.append("\\n");
                case '\r' -> sortie.append("\\r");
                case '\t' -> sortie.append("\\t");
                case '\b' -> sortie.append("\\b");
                case '\f' -> sortie.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sortie.append(String.format("\\u%04x", (int) c));
                    } else {
                        sortie.append(c);
                    }
                }
            }
        }
        sortie.append('"');
    }
}
