package jo.joide.deps.xml;

import java.util.ArrayList;
import java.util.List;

/**
 * Parseur XML maison, tolérant, sans validation ni espaces de noms
 * résolus (les préfixes restent dans les noms). Motivation (ADR-028) :
 * {@code org.w3c.dom} est absent de la liste des signatures du
 * niveau 28 — le DOM standard est donc inutilisable — et un petit
 * analyseur déterministe suffit aux POM et à maven-metadata.
 *
 * <p>Handle : déclaration XML, commentaires, instructions de
 * traitement, CDATA, les cinq entités prédéfinies, les références
 * numériques décimales et hexadécimales. Les documents mal formés
 * produisent une liste de problèmes ; la racine vaut {@code null}
 * quand aucun élément complet n'a pu être lu.</p>
 */
public final class Xml {

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
            return "xml:" + position + ": " + message;
        }
    }

    /** Le résultat d'analyse : racine (peut être {@code null}) et problèmes. */
    public static final class Document {
        public final XmlNode root;
        public final List<Problem> problems;

        Document(XmlNode root, List<Problem> problems) {
            this.root = root;
            this.problems = List.copyOf(problems);
        }

        /** @return vrai si le document est bien formé */
        public boolean healthy() {
            return root != null && problems.isEmpty();
        }
    }

    private static final int MAX_NODES = 20000;

    private Xml() {
    }

    /**
     * @param text document XML
     * @return la racine (ou {@code null}) et les problèmes
     */
    public static Document parse(String text) {
        List<Problem> problems = new ArrayList<>();
        Parser p = new Parser(text, problems);
        XmlNode root = p.document();
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

        XmlNode document() {
            skipProlog();
            if (i >= s.length()) {
                problems.add(new Problem(i, "empty document"));
                return null;
            }
            if (s.charAt(i) != '<' || i + 1 >= s.length()
                    || s.charAt(i + 1) == '/' || s.charAt(i + 1) == '!') {
                problems.add(new Problem(i, "expected root element"));
                return null;
            }
            XmlNode root = element();
            if (root != null) {
                skipEpilog();
                if (i < s.length()) {
                    problems.add(new Problem(i, "trailing content"));
                }
            }
            return root;
        }

        private void skipProlog() {
            while (i < s.length()) {
                if (s.startsWith("<?", i)) {
                    int end = s.indexOf("?>", i + 2);
                    if (end < 0) {
                        problems.add(new Problem(i,
                                "unterminated processing instruction"));
                        i = s.length();
                        return;
                    }
                    i = end + 2;
                } else if (s.startsWith("<!--", i)) {
                    int end = s.indexOf("-->", i + 4);
                    if (end < 0) {
                        problems.add(new Problem(i, "unterminated comment"));
                        i = s.length();
                        return;
                    }
                    i = end + 3;
                } else if (Character.isWhitespace(s.charAt(i))) {
                    i++;
                } else {
                    return;
                }
            }
        }

        private void skipEpilog() {
            while (i < s.length()) {
                if (s.startsWith("<!--", i)) {
                    int end = s.indexOf("-->", i + 4);
                    if (end < 0) {
                        i = s.length();
                        return;
                    }
                    i = end + 3;
                } else if (Character.isWhitespace(s.charAt(i))) {
                    i++;
                } else {
                    return;
                }
            }
        }

        /** Analyse un élément depuis « < » ; retourne null en cas d'échec. */
        private XmlNode element() {
            if (nodes++ > MAX_NODES) {
                problems.add(new Problem(i, "too many nodes"));
                return null;
            }
            expect('<');
            if (i >= s.length()) {
                problems.add(new Problem(i, "unexpected end"));
                return null;
            }
            int nameStart = i;
            while (i < s.length() && isNameChar(s.charAt(i))) {
                i++;
            }
            if (nameStart == i) {
                problems.add(new Problem(i, "expected element name"));
                return null;
            }
            String name = s.substring(nameStart, i);
            List<XmlNode.Attr> attrs = new ArrayList<>();
            // Boucle d'attributs jusqu'au « > » ou « /> ».
            for (; ; ) {
                skipSpace();
                if (i >= s.length()) {
                    problems.add(new Problem(i, "unterminated tag: " + name));
                    return null;
                }
                char c = s.charAt(i);
                if (c == '>') {
                    i++;
                    break;
                }
                if (c == '/') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '>') {
                        i += 2;
                        return new XmlNode(name, attrs, "", new ArrayList<>());
                    }
                    problems.add(new Problem(i, "stray '/' in " + name));
                    return null;
                }
                if (c == '<') {
                    problems.add(new Problem(i, "'<' inside tag " + name));
                    return null;
                }
                int attrStart = i;
                while (i < s.length() && isNameChar(s.charAt(i))) {
                    i++;
                }
                if (attrStart == i) {
                    problems.add(new Problem(i, "expected attribute in " + name));
                    return null;
                }
                String attrName = s.substring(attrStart, i);
                skipSpace();
                if (i >= s.length() || s.charAt(i) != '=') {
                    problems.add(new Problem(i, "expected '=' after " + attrName));
                    return null;
                }
                i++;
                skipSpace();
                if (i >= s.length() || (s.charAt(i) != '"' && s.charAt(i) != '\'')) {
                    problems.add(new Problem(i, "expected quoted value for "
                            + attrName));
                    return null;
                }
                char quote = s.charAt(i);
                int valueStart = ++i;
                while (i < s.length() && s.charAt(i) != quote) {
                    i++;
                }
                if (i >= s.length()) {
                    problems.add(new Problem(i, "unterminated attribute value"));
                    return null;
                }
                attrs.add(new XmlNode.Attr(attrName,
                        decode(s.substring(valueStart, i))));
                i++;
            }
            return content(name, attrs);
        }

        /** Analyse le contenu d'un élément jusqu'à sa fermeture. */
        private XmlNode content(String name, List<XmlNode.Attr> attrs) {
            List<XmlNode> children = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            for (; ; ) {
                int next = s.indexOf('<', i);
                if (next < 0) {
                    problems.add(new Problem(i, "unterminated element: " + name));
                    return null;
                }
                if (next > i) {
                    text.append(decode(s.substring(i, next)));
                    i = next;
                }
                if (s.startsWith("<!--", i)) {
                    int end = s.indexOf("-->", i + 4);
                    if (end < 0) {
                        problems.add(new Problem(i, "unterminated comment"));
                        return null;
                    }
                    i = end + 3;
                    continue;
                }
                if (s.startsWith("<![CDATA[", i)) {
                    int end = s.indexOf("]]>", i + 9);
                    if (end < 0) {
                        problems.add(new Problem(i, "unterminated CDATA"));
                        return null;
                    }
                    text.append(s, i + 9, end);
                    i = end + 3;
                    continue;
                }
                if (s.startsWith("<?", i)) {
                    int end = s.indexOf("?>", i + 2);
                    if (end < 0) {
                        problems.add(new Problem(i,
                                "unterminated processing instruction"));
                        return null;
                    }
                    i = end + 2;
                    continue;
                }
                if (i + 1 < s.length() && s.charAt(i + 1) == '/') {
                    int close = s.indexOf('>', i);
                    if (close < 0) {
                        problems.add(new Problem(i, "unterminated closing tag"));
                        return null;
                    }
                    String closing = s.substring(i + 2, close).trim();
                    if (!closing.equals(name)) {
                        problems.add(new Problem(i, "mismatched closing tag: "
                                + closing + " for " + name));
                        return null;
                    }
                    i = close + 1;
                    return new XmlNode(name, attrs, text.toString().trim(),
                            children);
                }
                XmlNode child = element();
                if (child == null) {
                    return null;
                }
                children.add(child);
            }
        }

        private void expect(char c) {
            if (i < s.length() && s.charAt(i) == c) {
                i++;
            } else {
                problems.add(new Problem(i, "expected '" + c + "'"));
                i = s.length();
            }
        }

        private void skipSpace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private String decode(String raw) {
            if (raw.indexOf('&') < 0) {
                return raw;
            }
            StringBuilder out = new StringBuilder(raw.length());
            int j = 0;
            while (j < raw.length()) {
                char c = raw.charAt(j);
                if (c != '&') {
                    out.append(c);
                    j++;
                    continue;
                }
                int semi = raw.indexOf(';', j);
                if (semi < 0) {
                    out.append(c);
                    j++;
                    continue;
                }
                String entity = raw.substring(j + 1, semi);
                switch (entity) {
                    case "lt":
                        out.append('<');
                        break;
                    case "gt":
                        out.append('>');
                        break;
                    case "amp":
                        out.append('&');
                        break;
                    case "quot":
                        out.append('"');
                        break;
                    case "apos":
                        out.append('\'');
                        break;
                    default:
                        if (entity.startsWith("#x") || entity.startsWith("#X")) {
                            try {
                                out.append((char) Integer.parseInt(
                                        entity.substring(2), 16));
                            } catch (NumberFormatException e) {
                                out.append('&').append(entity).append(';');
                            }
                        } else if (entity.startsWith("#")) {
                            try {
                                out.append((char) Integer.parseInt(
                                        entity.substring(1)));
                            } catch (NumberFormatException e) {
                                out.append('&').append(entity).append(';');
                            }
                        } else {
                            out.append('&').append(entity).append(';');
                        }
                }
                j = semi + 1;
            }
            return out.toString();
        }

        private static boolean isNameChar(char c) {
            return Character.isLetterOrDigit(c) || c == '-' || c == '_'
                    || c == '.' || c == ':';
        }
    }
}
