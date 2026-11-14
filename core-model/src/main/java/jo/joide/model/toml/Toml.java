package jo.joide.model.toml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyseur TOML tolérant : le sous-ensemble utilisé par les fichiers
 * de projet, sans jamais lever d'exception.
 *
 * <p>Sont reconnus : les commentaires, les clés nues ou entre
 * guillemets, les chaînes basiques avec échappements et les chaînes
 * littérales, les entiers (décimaux, hexadécimaux, octaux, binaires,
 * avec séparateurs soulignés), les booléens, les tableaux (y compris
 * sur plusieurs lignes), les tables {@code [a.b]} et les tables en
 * série {@code [[a.b]]}. Toute autre construction (nombres à virgule,
 * dates, tables en ligne) est signalée et sautée ; l'analyse continue
 * sur le reste du document.</p>
 */
public final class Toml {

    private Toml() {
    }

    /**
     * Analyse un document.
     *
     * @param text contenu du document
     * @return le document analysé, avec ses problèmes, jamais d'exception
     */
    public static TomlDocument parse(String text) {
        List<TomlProblem> problems = new ArrayList<>();
        Map<String, TomlValue> root = new LinkedHashMap<>();
        if (text == null) {
            problems.add(new TomlProblem(1, 1, "document absent"));
            return new TomlDocument(new TomlValue.TomlTable(root), problems);
        }
        Parser parser = new Parser(text, problems);
        parser.parseDocument(root);
        return new TomlDocument(new TomlValue.TomlTable(root), problems);
    }

    /** Machine d'analyse : parcours caractère par caractère. */
    private static final class Parser {
        private final String text;
        private final List<TomlProblem> problems;
        private int position;
        private int line = 1;
        private int lineStart;

        Parser(String text, List<TomlProblem> problems) {
            this.text = text;
            this.problems = problems;
        }

        void parseDocument(Map<String, TomlValue> root) {
            Map<String, TomlValue> current = root;
            while (true) {
                skipBlanksAndComments();
                if (position >= text.length()) {
                    return;
                }
                char c = text.charAt(position);
                if (c == '[') {
                    current = parseTableHeader(root);
                } else {
                    parseKeyValue(current);
                }
            }
        }

        /** Saute les espaces, tabulations, fins de ligne et commentaires. */
        private void skipBlanksAndComments() {
            while (position < text.length()) {
                char c = text.charAt(position);
                if (c == ' ' || c == '\t' || c == '\r') {
                    position++;
                } else if (c == '\n') {
                    nextLine();
                    position++;
                } else if (c == '#') {
                    while (position < text.length() && text.charAt(position) != '\n') {
                        position++;
                    }
                } else {
                    return;
                }
            }
        }

        private void skipInlineBlanks() {
            while (position < text.length()) {
                char c = text.charAt(position);
                if (c == ' ' || c == '\t') {
                    position++;
                } else {
                    return;
                }
            }
        }

        private void nextLine() {
            line++;
            lineStart = position + 1;
        }

        private int column() {
            return position - lineStart + 1;
        }

        private void problem(String message) {
            problems.add(new TomlProblem(line, column(), message));
        }

        private void skipToNextLine() {
            while (position < text.length() && text.charAt(position) != '\n') {
                position++;
            }
        }

        /** En-tête de table [a.b] ou de série [[a.b]]. */
        private Map<String, TomlValue> parseTableHeader(Map<String, TomlValue> root) {
            position++; // '['
            boolean arrayOfTables = position < text.length()
                    && text.charAt(position) == '[';
            if (arrayOfTables) {
                position++;
            }
            skipInlineBlanks();
            List<String> path = parseKeyPath();
            skipInlineBlanks();
            boolean closed = false;
            while (position < text.length()) {
                char c = text.charAt(position);
                if (c == ']') {
                    position++;
                    if (arrayOfTables) {
                        if (position < text.length() && text.charAt(position) == ']') {
                            position++;
                            closed = true;
                        }
                        break;
                    }
                    closed = true;
                    break;
                }
                if (c == '\n') {
                    break;
                }
                position++;
            }
            if (!closed || path.isEmpty()) {
                problem("en-tete de table malforme");
                skipToNextLine();
                return root;
            }
            skipInlineBlanks();
            if (position < text.length() && text.charAt(position) == '#') {
                while (position < text.length() && text.charAt(position) != '\n') {
                    position++;
                }
            }
            if (position < text.length() && text.charAt(position) == '\n') {
                nextLine();
                position++;
            } else if (position < text.length()) {
                problem("contenu parasite apres l'en-tete de table");
                skipToNextLine();
            }
            return navigate(root, path, arrayOfTables);
        }

        /** Descente (ou création) jusqu'à la table désignée par le chemin. */
        private Map<String, TomlValue> navigate(Map<String, TomlValue> root,
                                                List<String> path,
                                                boolean arrayOfTables) {
            Map<String, TomlValue> current = root;
            for (int i = 0; i < path.size(); i++) {
                String segment = path.get(i);
                boolean last = i == path.size() - 1;
                TomlValue existing = current.get(segment);
                if (last && arrayOfTables) {
                    List<TomlValue> series;
                    if (existing == null) {
                        TomlValue.TomlArray array = new TomlValue.TomlArray(List.of());
                        current.put(segment, array);
                        series = array.mutableValues();
                    } else if (existing.isArray()) {
                        TomlValue.TomlArray array =
                                (TomlValue.TomlArray) existing;
                        series = array.mutableValues();
                    } else {
                        problem("la cle " + segment + " n'est pas une serie de tables");
                        return root;
                    }
                    TomlValue.TomlTable table = new TomlValue.TomlTable(Map.of());
                    series.add(table);
                    return table.mutableEntries();
                }
                if (existing == null) {
                    TomlValue.TomlTable table = new TomlValue.TomlTable(Map.of());
                    current.put(segment, table);
                    current = table.mutableEntries();
                } else if (existing.isTable()) {
                    current = ((TomlValue.TomlTable) existing).mutableEntries();
                    if (last && !arrayOfTables) {
                        // réouverture d'une table : acceptée, signalée
                        problem("table " + segment + " reouverte");
                    }
                } else if (existing.isArray() && !last) {
                    List<TomlValue> series = existing.asArray();
                    if (!series.isEmpty() && series.get(series.size() - 1).isTable()) {
                        current = ((TomlValue.TomlTable)
                                series.get(series.size() - 1)).mutableEntries();
                    } else {
                        problem("la cle " + segment + " n'est pas une table");
                        return root;
                    }
                } else {
                    problem("la cle " + segment + " n'est pas une table");
                    return root;
                }
            }
            return current;
        }

        /** Chemin de clés séparées par des points. */
        private List<String> parseKeyPath() {
            List<String> path = new ArrayList<>();
            while (true) {
                skipInlineBlanks();
                String key = parseKey();
                if (key == null) {
                    return path;
                }
                path.add(key);
                skipInlineBlanks();
                if (position < text.length() && text.charAt(position) == '.') {
                    position++;
                    continue;
                }
                return path;
            }
        }

        /** Clé nue ou entre guillemets, ou {@code null} si malformée. */
        private String parseKey() {
            if (position >= text.length()) {
                return null;
            }
            char c = text.charAt(position);
            if (c == '"') {
                String value = parseBasicString();
                return value == null ? "" : value;
            }
            int start = position;
            while (position < text.length()) {
                char k = text.charAt(position);
                if ((k >= 'a' && k <= 'z') || (k >= 'A' && k <= 'Z')
                        || (k >= '0' && k <= '9') || k == '_' || k == '-') {
                    position++;
                } else {
                    break;
                }
            }
            if (position == start) {
                return null;
            }
            return text.substring(start, position);
        }

        /** Paire « clé = valeur » dans la table courante. */
        private void parseKeyValue(Map<String, TomlValue> current) {
            List<String> path = parseKeyPath();
            if (path.isEmpty() || path.contains("") || path.contains(null)) {
                problem("cle absente ou malformee");
                skipToNextLine();
                return;
            }
            skipInlineBlanks();
            if (position >= text.length() || text.charAt(position) != '=') {
                problem("signe egale attendu apres la cle");
                skipToNextLine();
                return;
            }
            position++;
            skipInlineBlanks();
            TomlValue value = parseValue();
            if (value == null) {
                problem("valeur absente ou non reconnue");
                skipToNextLine();
                return;
            }
            // fin de ligne propre ?
            skipInlineBlanks();
            if (position < text.length() && text.charAt(position) == '#') {
                while (position < text.length() && text.charAt(position) != '\n') {
                    position++;
                }
            }
            if (position < text.length() && text.charAt(position) != '\n') {
                problem("contenu parasite apres la valeur");
                skipToNextLine();
            }
            if (position < text.length() && text.charAt(position) == '\n') {
                nextLine();
                position++;
            }
            assign(current, path, value);
        }

        /** Affectation par chemin, avec tables intermédiaires. */
        private void assign(Map<String, TomlValue> current, List<String> path,
                            TomlValue value) {
            for (int i = 0; i < path.size() - 1; i++) {
                String segment = path.get(i);
                TomlValue existing = current.get(segment);
                if (existing == null) {
                    TomlValue.TomlTable table = new TomlValue.TomlTable(Map.of());
                    current.put(segment, table);
                    current = table.mutableEntries();
                } else if (existing.isTable()) {
                    current = ((TomlValue.TomlTable) existing).mutableEntries();
                } else {
                    problem("la cle " + segment + " n'est pas une table");
                    return;
                }
            }
            String key = path.get(path.size() - 1);
            if (current.containsKey(key)) {
                problem("cle " + key + " definie plusieurs fois : la premiere gagne");
                return;
            }
            current.put(key, value);
        }

        /** Valeur : chaîne, entier, booléen ou tableau ; sinon {@code null}. */
        private TomlValue parseValue() {
            if (position >= text.length()) {
                return null;
            }
            char c = text.charAt(position);
            switch (c) {
                case '"':
                    String basic = parseBasicString();
                    return basic == null ? null : new TomlValue.TomlString(basic);
                case '\'':
                    String literal = parseLiteralString();
                    return literal == null ? null : new TomlValue.TomlString(literal);
                case '[':
                    return parseArray();
                case 't':
                case 'f':
                    return parseBoolean();
                case '+':
                case '-':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    return parseInteger();
                default:
                    return null;
            }
        }

        /** Chaîne basique avec échappements, ou {@code null}. */
        private String parseBasicString() {
            position++; // guillemet ouvrant
            StringBuilder out = new StringBuilder();
            while (position < text.length()) {
                char c = text.charAt(position);
                if (c == '"') {
                    position++;
                    return out.toString();
                }
                if (c == '\n') {
                    problem("chaine basique non fermee avant la fin de ligne");
                    return null;
                }
                if (c == '\\') {
                    position++;
                    if (position >= text.length()) {
                        problem("echappement en fin de document");
                        return null;
                    }
                    char escape = text.charAt(position);
                    switch (escape) {
                        case 'n': out.append('\n'); position++; break;
                        case 't': out.append('\t'); position++; break;
                        case 'r': out.append('\r'); position++; break;
                        case '"': out.append('"'); position++; break;
                        case '\\': out.append('\\'); position++; break;
                        case 'b': out.append('\b'); position++; break;
                        case 'f': out.append('\f'); position++; break;
                        case 'u':
                        case 'U':
                            int width = escape == 'u' ? 4 : 8;
                            if (position + width >= text.length()) {
                                problem("echappement unicode tronque");
                                return null;
                            }
                            String digits = text.substring(position + 1, position + 1 + width);
                            try {
                                out.append((char) Integer.parseInt(digits, 16));
                                position += 1 + width;
                            } catch (NumberFormatException e) {
                                problem("echappement unicode invalide");
                                return null;
                            }
                            break;
                        default:
                            problem("echappement inconnu : \\" + escape);
                            return null;
                    }
                    continue;
                }
                out.append(c);
                position++;
            }
            problem("chaine basique non fermee avant la fin du document");
            return null;
        }

        /** Chaîne littérale, sans échappement, ou {@code null}. */
        private String parseLiteralString() {
            position++; // apostrophe ouvrante
            int start = position;
            while (position < text.length()) {
                char c = text.charAt(position);
                if (c == '\'') {
                    String value = text.substring(start, position);
                    position++;
                    return value;
                }
                if (c == '\n') {
                    problem("chaine litterale non fermee avant la fin de ligne");
                    return null;
                }
                position++;
            }
            problem("chaine litterale non fermee avant la fin du document");
            return null;
        }

        /** Booléen « true » ou « false ». */
        private TomlValue parseBoolean() {
            if (text.startsWith("true", position)) {
                position += 4;
                return new TomlValue.TomlBoolean(true);
            }
            if (text.startsWith("false", position)) {
                position += 5;
                return new TomlValue.TomlBoolean(false);
            }
            return null;
        }

        /** Entier : décimal, 0x, 0o, 0b, signe et soulignés admis. */
        private TomlValue parseInteger() {
            int start = position;
            if (position < text.length()
                    && (text.charAt(position) == '+' || text.charAt(position) == '-')) {
                position++;
            }
            String radixPrefix = "";
            if (position + 1 < text.length() && text.charAt(position) == '0') {
                char next = text.charAt(position + 1);
                if (next == 'x' || next == 'o' || next == 'b') {
                    radixPrefix = String.valueOf(next);
                    position += 2;
                }
            }
            int digitsStart = position;
            while (position < text.length()) {
                char c = text.charAt(position);
                if ((c >= '0' && c <= '9')
                        || (c >= 'a' && c <= 'f')
                        || (c >= 'A' && c <= 'F')
                        || c == '_') {
                    position++;
                } else {
                    break;
                }
            }
            String digits = text.substring(digitsStart, position).replace("_", "");
            if (digits.isEmpty()) {
                position = start;
                return null;
            }
            try {
                long value;
                if (radixPrefix.equals("x")) {
                    value = Long.parseUnsignedLong(digits, 16);
                } else if (radixPrefix.equals("o")) {
                    value = Long.parseUnsignedLong(digits, 8);
                } else if (radixPrefix.equals("b")) {
                    value = Long.parseUnsignedLong(digits, 2);
                } else {
                    value = Long.parseLong(text.substring(start, position).replace("_", ""));
                }
                return new TomlValue.TomlInteger(value);
            } catch (NumberFormatException e) {
                problem("entier invalide : " + text.substring(start, position));
                position = start;
                return null;
            }
        }

        /** Tableau, éventuellement sur plusieurs lignes. */
        private TomlValue parseArray() {
            position++; // '['
            List<TomlValue> values = new ArrayList<>();
            while (true) {
                skipBlanksAndComments();
                if (position >= text.length()) {
                    problem("tableau non ferme avant la fin du document");
                    return null;
                }
                char c = text.charAt(position);
                if (c == ']') {
                    position++;
                    return new TomlValue.TomlArray(values);
                }
                if (c == ',') {
                    position++;
                    continue;
                }
                TomlValue value = parseValue();
                if (value == null) {
                    problem("element de tableau non reconnu");
                    // avancer d'un caractère pour garantir la progression
                    position++;
                    continue;
                }
                values.add(value);
            }
        }
    }
}
