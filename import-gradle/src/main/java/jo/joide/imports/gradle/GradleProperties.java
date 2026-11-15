package jo.joide.imports.gradle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lecture tolérante d'un fichier {@code gradle.properties} : paires
 * clef-valeur, commentaires, séparateurs « = » ou «  », échappements
 * de fin de ligne.
 *
 * <p>La lecture ne lève jamais ; les lignes mal formées sont ignorées
 * avec un problème. Contrairement à {@code java.util.Properties},
 * l'entrée est décodée en UTF-8 (les projets réels écrivent de
 * l'UTF-8) et la lecture garde l'ordre (ADR-039).</p>
 */
public final class GradleProperties {

    private final Map<String, String> values;
    private final List<String> order;

    private GradleProperties(Map<String, String> values,
                             List<String> order) {
        this.values = values;
        this.order = order;
    }

    /**
     * @param content contenu du fichier
     * @return les propriétés lues, éventuellement vides
     */
    public static GradleProperties parse(String content) {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> order = new java.util.ArrayList<>();
        if (content == null) {
            return new GradleProperties(values, order);
        }
        StringBuilder logical = new StringBuilder(content.length());
        boolean continuation = false;
        for (String rawLine : content.split("\n", -1)) {
            String line = rawLine.endsWith("\r")
                    ? rawLine.substring(0, rawLine.length() - 1)
                    : rawLine;
            if (continuation) {
                line = stripLeadingBlanks(line);
            }
            if (line.endsWith("\\")) {
                // comme java.util.Properties : l'anti-slash, la fin de
                // ligne et les blancs de tête de la suite sont perdus
                logical.append(line, 0, line.length() - 1);
                continuation = true;
                continue;
            }
            logical.append(line);
            continuation = false;
            parseLine(logical.toString(), values, order);
            logical.setLength(0);
        }
        if (!logical.isEmpty()) {
            parseLine(logical.toString(), values, order);
        }
        return new GradleProperties(values, order);
    }

    private static String stripLeadingBlanks(String line) {
        int start = 0;
        while (start < line.length()
                && (line.charAt(start) == ' ' || line.charAt(start) == '\t')) {
            start++;
        }
        return line.substring(start);
    }

    private static void parseLine(String line, Map<String, String> values,
                                  List<String> order) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")
                || trimmed.startsWith("!")) {
            return;
        }
        int separator = indexOfSeparator(trimmed);
        if (separator < 0) {
            return;
        }
        String key = unescape(trimmed.substring(0, separator).trim());
        String value = unescape(trimmed.substring(separator + 1)
                .trim());
        if (!key.isEmpty()) {
            values.put(key, value);
            order.add(key);
        }
    }

    private static int indexOfSeparator(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '=' || c == ':') {
                return i;
            }
        }
        return -1;
    }

    private static String unescape(String text) {
        return text.replace("\\=", "=").replace("\\:", ":")
                .replace("\\ ", " ").replace("\\\\", "\\");
    }

    /**
     * @param key clef cherchée
     * @return la valeur, si présente
     */
    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    /** @return toutes les paires, dans l'ordre du fichier */
    public Map<String, String> values() {
        return values;
    }

    /** @return les clefs dans l'ordre du fichier */
    public List<String> order() {
        return order;
    }

    @Override
    public String toString() {
        return "GradleProperties[" + values.size() + " entries]";
    }
}
