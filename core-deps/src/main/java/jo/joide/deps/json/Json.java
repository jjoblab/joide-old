package jo.joide.deps.json;

import java.util.ArrayList;
import java.util.List;

/**
 * Parseur JSON maison, tolérant, pour les fichiers « .module » de
 * Gradle Module Metadata. Aucune bibliothèque tierce (ADR-028) :
 * le format nécessaire est objet, tableau, chaîne (échappements
 * complets), nombre, booléen, null ; l'analyseur n'excige ni
 * virgule finale, ignore les virgules surnuméraires et s'arrête au
 * premier déséquilibre avec un problème explicite.
 */
public final class Json {

    /** Un problème d'analyse : position et message. */
    public static final class Problem {
        public final int position;
        public final String message;

        Problem(int position, String message) {
            this.position = position;
            this.message = message;
        }

        @Override
        public String toString() {
            return "json:" + position + ": " + message;
        }
    }

    /** Le résultat d'analyse : valeur (peut être null) et problèmes. */
    public static final class Document {
        public final JsonValue root;
        public final List<Problem> problems;

        Document(JsonValue root, List<Problem> problems) {
            this.root = root;
            this.problems = List.copyOf(problems);
        }

        /** @return vrai si le document est bien formé */
        public boolean healthy() {
            return root != null && problems.isEmpty();
        }
    }

    private static final int MAX_NODES = 50000;

    private Json() {
    }

    /**
     * @param text document JSON
     * @return la valeur racine (ou {@code null}) et les problèmes
     */
    public static Document parse(String text) {
        List<Problem> problems = new ArrayList<>();
        Parser p = new Parser(text, problems);
        JsonValue root = p.value();
        p.skipSpace();
        if (p.i < p.s.length() && root != null) {
            problems.add(new Problem(p.i, "trailing content"));
        }
        return new Document(root, problems);
    }

    private static final class Parser {
        private final String s;
        private final List<Problem> problems;
        private int i;
        private int nodes;

        Parser(String s, List<Problem> problems) {
            this.s = s;
            this.problems = problems;
        }

        JsonValue value() {
            skipSpace();
            if (i >= s.length()) {
                problems.add(new Problem(i, "empty document"));
                return null;
            }
            char c = s.charAt(i);
            switch (c) {
                case '{':
                    return object();
                case '[':
                    return array();
                case '"':
                    return string();
                case 't':
                    if (s.startsWith("true", i)) {
                        i += 4;
                        return JsonValue.of(true);
                    }
                    break;
                case 'f':
                    if (s.startsWith("false", i)) {
                        i += 5;
                        return JsonValue.of(false);
                    }
                    break;
                case 'n':
                    if (s.startsWith("null", i)) {
                        i += 4;
                        return JsonValue.ofNull();
                    }
                    break;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return number();
                    }
            }
            problems.add(new Problem(i, "unexpected character '" + c + "'"));
            return null;
        }

        private JsonValue object() {
            if (nodes++ > MAX_NODES) {
                problems.add(new Problem(i, "too many nodes"));
                return null;
            }
            i++; // '{'
            List<String> keys = new ArrayList<>();
            List<JsonValue> members = new ArrayList<>();
            skipSpace();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return JsonValue.object(keys, members);
            }
            for (; ; ) {
                skipSpace();
                if (i >= s.length() || s.charAt(i) != '"') {
                    problems.add(new Problem(i, "expected object key"));
                    return null;
                }
                JsonValue key = string();
                if (key == null) {
                    return null;
                }
                skipSpace();
                if (i >= s.length() || s.charAt(i) != ':') {
                    problems.add(new Problem(i, "expected ':' after key"));
                    return null;
                }
                i++;
                JsonValue v = value();
                if (v == null) {
                    return null;
                }
                keys.add(key.asString());
                members.add(v);
                skipSpace();
                if (i >= s.length()) {
                    problems.add(new Problem(i, "unterminated object"));
                    return null;
                }
                char c = s.charAt(i);
                if (c == ',') {
                    i++;
                    skipSpace();
                    // Tolérance : virgule finale avant « } ».
                    if (i < s.length() && s.charAt(i) == '}') {
                        i++;
                        return JsonValue.object(keys, members);
                    }
                    continue;
                }
                if (c == '}') {
                    i++;
                    return JsonValue.object(keys, members);
                }
                problems.add(new Problem(i, "expected ',' or '}'"));
                return null;
            }
        }

        private JsonValue array() {
            if (nodes++ > MAX_NODES) {
                problems.add(new Problem(i, "too many nodes"));
                return null;
            }
            i++; // '['
            List<JsonValue> items = new ArrayList<>();
            skipSpace();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return JsonValue.array(items);
            }
            for (; ; ) {
                JsonValue v = value();
                if (v == null) {
                    return null;
                }
                items.add(v);
                skipSpace();
                if (i >= s.length()) {
                    problems.add(new Problem(i, "unterminated array"));
                    return null;
                }
                char c = s.charAt(i);
                if (c == ',') {
                    i++;
                    skipSpace();
                    if (i < s.length() && s.charAt(i) == ']') {
                        i++;
                        return JsonValue.array(items);
                    }
                    continue;
                }
                if (c == ']') {
                    i++;
                    return JsonValue.array(items);
                }
                problems.add(new Problem(i, "expected ',' or ']'"));
                return null;
            }
        }

        private JsonValue string() {
            i++; // '"'
            StringBuilder out = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '"') {
                    i++;
                    return JsonValue.of(out.toString());
                }
                if (c == '\\' && i + 1 < s.length()) {
                    char esc = s.charAt(i + 1);
                    switch (esc) {
                        case '"':
                            out.append('"');
                            break;
                        case '\\':
                            out.append('\\');
                            break;
                        case '/':
                            out.append('/');
                            break;
                        case 'b':
                            out.append('\b');
                            break;
                        case 'f':
                            out.append('\f');
                            break;
                        case 'n':
                            out.append('\n');
                            break;
                        case 'r':
                            out.append('\r');
                            break;
                        case 't':
                            out.append('\t');
                            break;
                        case 'u':
                            if (i + 5 < s.length()) {
                                try {
                                    out.append((char) Integer.parseInt(
                                            s.substring(i + 2, i + 6), 16));
                                    i += 6;
                                    continue;
                                } catch (NumberFormatException e) {
                                    // tombé plus bas : échec signalé
                                }
                            }
                            problems.add(new Problem(i, "bad unicode escape"));
                            return null;
                        default:
                            problems.add(new Problem(i, "bad escape '\\" + esc + "'"));
                            return null;
                    }
                    i += 2;
                    continue;
                }
                out.append(c);
                i++;
            }
            problems.add(new Problem(i, "unterminated string"));
            return null;
        }

        private JsonValue number() {
            int start = i;
            if (s.charAt(i) == '-') {
                i++;
            }
            while (i < s.length()) {
                char c = s.charAt(i);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E'
                        || c == '+' || c == '-') {
                    i++;
                } else {
                    break;
                }
            }
            return JsonValue.ofNumber(s.substring(start, i));
        }

        void skipSpace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }
    }
}
