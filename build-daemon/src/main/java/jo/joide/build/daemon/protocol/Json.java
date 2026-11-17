package jo.joide.build.daemon.protocol;

import java.util.List;
import java.util.Map;

/**
 * Écriture JSON compacte pour les cadres du protocole : objets,
 * tableaux, chaînes échappées, nombres et booléens. L'ordre des
 * clées est celui de l'insertion — le protocole ne dépend jamais de
 * l'ordre. Aucune lecture : l'analyse des cadres entrants passe par
 * l'analyseur JSON du résolveur de dépendances.
 */
public final class Json {

    private Json() {
    }

    /**
     * Écrit un objet JSON depuis des paires clé-valeur.
     *
     * @param entries paires dans l'ordre d'écriture
     * @return le texte JSON
     */
    public static String object(Object... entries) {
        StringBuilder out = new StringBuilder(64);
        out.append('{');
        for (int i = 0; i + 1 < entries.length; i += 2) {
            if (i > 0) {
                out.append(',');
            }
            string(out, String.valueOf(entries[i]));
            out.append(':');
            value(out, entries[i + 1]);
        }
        out.append('}');
        return out.toString();
    }

    private static void value(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            string(out, text);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                string(out, String.valueOf(entry.getKey()));
                out.append(':');
                value(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                value(out, list.get(i));
            }
            out.append(']');
        } else {
            string(out, String.valueOf(value));
        }
    }

    private static void string(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
