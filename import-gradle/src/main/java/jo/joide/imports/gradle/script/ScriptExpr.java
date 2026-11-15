package jo.joide.imports.gradle.script;

import java.util.List;
import java.util.Objects;

/**
 * Expression d'un script Gradle, en forme tolérante.
 *
 * <p>Le parseur produit ces expressions pour les arguments d'appels
 * et les membres droits d'affectations ; toute forme non comprise
 * devient {@link OtherExpr} avec son texte brut — jamais une
 * exception (ADR-039). Les classes sont immuables, de simples
 * porteurs de valeurs à accesseurs sans préfixe.</p>
 */
public sealed interface ScriptExpr {

    /**
     * Chaîne littérale : contenu brut, style de citation connu.
     */
    final class Literal implements ScriptExpr {

        private final String raw;
        private final Style style;

        /**
         * @param raw   contenu sans les guillemets, échappements inclus
         * @param style style de la littérale
         */
        public Literal(String raw, Style style) {
            this.raw = raw;
            this.style = style;
        }

        /** @return le contenu brut */
        public String raw() {
            return raw;
        }

        /** @return le style de la littérale */
        public Style style() {
            return style;
        }

        /** Styles de littérales. */
        public enum Style {
            /** Guillemets doubles : interpolation possible. */
            DOUBLE,
            /** Apostrophes : pas d'interpolation (Groovy). */
            SINGLE,
            /** Triples guillemets doubles. */
            TRIPLE_DOUBLE,
            /** Triples apostrophes. */
            TRIPLE_SINGLE,
            /** Nombre. */
            NUMBER,
            /** Autre (négatif, etc.). */
            OTHER
        }

        /** @return vrai si cette chaîne peut porter une interpolation */
        public boolean interpolable() {
            return style == Style.DOUBLE || style == Style.TRIPLE_DOUBLE;
        }

        /**
         * @return le contenu avec les échappements simples résolus
         *         ({@code \n}, {@code \t}, {@code \r}, {@code \"},
         *         {@code \'}, {@code \\}, {@code \$}) ; les séquences
         *         inconnues restent telles quelles
         */
        public String decoded() {
            if (style != Style.DOUBLE && style != Style.TRIPLE_DOUBLE) {
                return raw;
            }
            StringBuilder out = new StringBuilder(raw.length());
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c != '\\' || i + 1 >= raw.length()) {
                    out.append(c);
                    continue;
                }
                char next = raw.charAt(++i);
                switch (next) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case '"', '\'', '\\', '$' -> out.append(next);
                    default -> out.append(c).append(next);
                }
            }
            return out.toString();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Literal that)) {
                return false;
            }
            return raw.equals(that.raw) && style == that.style;
        }

        @Override
        public int hashCode() {
            return Objects.hash(raw, style);
        }

        @Override
        public String toString() {
            return "Literal[" + style + " " + raw + "]";
        }
    }

    /**
     * Référence : chaîne d'identifiants ({@code libs.foo.bar},
     * {@code rootProject.name}).
     */
    final class Reference implements ScriptExpr {

        private final List<String> chain;

        /**
         * @param chain segments, au moins un
         */
        public Reference(List<String> chain) {
            this.chain = List.copyOf(chain);
        }

        /** @return les segments */
        public List<String> chain() {
            return chain;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Reference that)) {
                return false;
            }
            return chain.equals(that.chain);
        }

        @Override
        public int hashCode() {
            return chain.hashCode();
        }

        @Override
        public String toString() {
            return "Reference[" + String.join(".", chain) + "]";
        }
    }

    /**
     * Appel en position d'expression : {@code project(":x")},
     * {@code files("y")}.
     */
    final class CallExpr implements ScriptExpr {

        private final List<String> receiver;
        private final String name;
        private final List<ScriptExpr> args;

        /**
         * @param receiver segments avant le nom (éventuellement vides)
         * @param name     nom de la méthode
         * @param args     arguments
         */
        public CallExpr(List<String> receiver, String name,
                        List<ScriptExpr> args) {
            this.receiver = List.copyOf(receiver);
            this.name = Objects.requireNonNull(name, "name");
            this.args = List.copyOf(args);
        }

        /** @return les segments avant le nom */
        public List<String> receiver() {
            return receiver;
        }

        /** @return le nom de la méthode */
        public String name() {
            return name;
        }

        /** @return les arguments */
        public List<ScriptExpr> args() {
            return args;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CallExpr that)) {
                return false;
            }
            return receiver.equals(that.receiver)
                    && name.equals(that.name) && args.equals(that.args);
        }

        @Override
        public int hashCode() {
            return Objects.hash(receiver, name, args);
        }

        @Override
        public String toString() {
            return "CallExpr[" + String.join(".", receiver) + "."
                    + name + " " + args.size() + " args]";
        }
    }

    /**
     * Liste littérale : {@code [a, b, c]}.
     */
    final class ListExpr implements ScriptExpr {

        private final List<ScriptExpr> items;

        /**
         * @param items éléments, éventuellement incomplets
         */
        public ListExpr(List<ScriptExpr> items) {
            this.items = List.copyOf(items);
        }

        /** @return les éléments */
        public List<ScriptExpr> items() {
            return items;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ListExpr that)) {
                return false;
            }
            return items.equals(that.items);
        }

        @Override
        public int hashCode() {
            return items.hashCode();
        }

        @Override
        public String toString() {
            return "ListExpr[" + items.size() + " items]";
        }
    }

    /**
     * Table littérale : {@code [cle: valeur]} ou argument nommé.
     */
    final class MapExpr implements ScriptExpr {

        private final List<Entry> entries;

        /**
         * @param entries paires dans l'ordre
         */
        public MapExpr(List<Entry> entries) {
            this.entries = List.copyOf(entries);
        }

        /** @return les paires, dans l'ordre */
        public List<Entry> entries() {
            return entries;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MapExpr that)) {
                return false;
            }
            return entries.equals(that.entries);
        }

        @Override
        public int hashCode() {
            return entries.hashCode();
        }

        @Override
        public String toString() {
            return "MapExpr[" + entries.size() + " entries]";
        }

        /**
         * Une paire de la table.
         */
        public static final class Entry {

            private final ScriptExpr key;
            private final ScriptExpr value;

            /**
             * @param key   clef
             * @param value valeur
             */
            public Entry(ScriptExpr key, ScriptExpr value) {
                this.key = key;
                this.value = value;
            }

            /** @return la clef */
            public ScriptExpr key() {
                return key;
            }

            /** @return la valeur */
            public ScriptExpr value() {
                return value;
            }

            @Override
            public boolean equals(Object other) {
                if (this == other) {
                    return true;
                }
                if (!(other instanceof Entry that)) {
                    return false;
                }
                return Objects.equals(key, that.key)
                        && Objects.equals(value, that.value);
            }

            @Override
            public int hashCode() {
                return Objects.hash(key, value);
            }

            @Override
            public String toString() {
                return "Entry[" + key + " : " + value + "]";
            }
        }
    }

    /**
     * Fermeture en position d'argument : {@code { ... }}.
     */
    final class ClosureExpr implements ScriptExpr {

        private final Script body;

        /**
         * @param body corps de la fermeture
         */
        public ClosureExpr(Script body) {
            this.body = Objects.requireNonNull(body, "body");
        }

        /** @return le corps de la fermeture */
        public Script body() {
            return body;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ClosureExpr that)) {
                return false;
            }
            return body.equals(that.body);
        }

        @Override
        public int hashCode() {
            return body.hashCode();
        }

        @Override
        public String toString() {
            return "ClosureExpr[" + body + "]";
        }
    }

    /**
     * Tout ce qui n'est pas compris : texte brut conservé.
     */
    final class OtherExpr implements ScriptExpr {

        private final String raw;

        /**
         * @param raw texte de l'expression
         */
        public OtherExpr(String raw) {
            this.raw = raw;
        }

        /** @return le texte brut */
        public String raw() {
            return raw;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof OtherExpr that)) {
                return false;
            }
            return raw.equals(that.raw);
        }

        @Override
        public int hashCode() {
            return raw.hashCode();
        }

        @Override
        public String toString() {
            return "OtherExpr[" + raw + "]";
        }
    }
}
