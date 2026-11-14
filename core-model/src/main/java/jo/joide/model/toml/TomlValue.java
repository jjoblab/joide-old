package jo.joide.model.toml;

import java.util.List;

/**
 * Une valeur TOML analysée : chaîne, entier, booléen, tableau ou
 * table.
 */
public interface TomlValue {

    /** @return vrai si cette valeur est une chaîne */
    default boolean isString() {
        return this instanceof TomlString;
    }

    /** @return vrai si cette valeur est un entier */
    default boolean isInteger() {
        return this instanceof TomlInteger;
    }

    /** @return vrai si cette valeur est un booléen */
    default boolean isBoolean() {
        return this instanceof TomlBoolean;
    }

    /** @return vrai si cette valeur est un tableau */
    default boolean isArray() {
        return this instanceof TomlArray;
    }

    /** @return vrai si cette valeur est une table */
    default boolean isTable() {
        return this instanceof TomlTable;
    }

    /**
     * @return la valeur comme chaîne, ou {@code null} si elle n'en est
     *         pas une
     */
    default String asString() {
        return this instanceof TomlString s ? s.value() : null;
    }

    /**
     * @return la valeur comme entier, ou {@code null} si elle n'en est
     *         pas un
     */
    default Long asInteger() {
        return this instanceof TomlInteger i ? Long.valueOf(i.value()) : null;
    }

    /**
     * @return la valeur comme booléen, ou {@code null} si elle n'en est
     *         pas un
     */
    default Boolean asBoolean() {
        return this instanceof TomlBoolean b ? Boolean.valueOf(b.value()) : null;
    }

    /**
     * @return la valeur comme tableau, ou {@code null} si elle n'en est
     *         pas un
     */
    default List<TomlValue> asArray() {
        return this instanceof TomlArray a ? a.values() : null;
    }

    /**
     * @return la valeur comme table, ou {@code null} si elle n'en est
     *         pas une
     */
    default java.util.Map<String, TomlValue> asTable() {
        return this instanceof TomlTable t ? t.entries() : null;
    }

    /** Chaîne de caractères TOML. */
    final class TomlString implements TomlValue {
        private final String value;

        /** @param value contenu de la chaîne */
        public TomlString(String value) {
            this.value = java.util.Objects.requireNonNull(value, "value");
        }

        /** @return le contenu */
        public String value() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TomlString s && value.equals(s.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return "\"" + value + "\"";
        }
    }

    /** Entier TOML (64 bits). */
    final class TomlInteger implements TomlValue {
        private final long value;

        /** @param value valeur de l'entier */
        public TomlInteger(long value) {
            this.value = value;
        }

        /** @return la valeur */
        public long value() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TomlInteger i && value == i.value;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(value);
        }

        @Override
        public String toString() {
            return Long.toString(value);
        }
    }

    /** Booléen TOML. */
    final class TomlBoolean implements TomlValue {
        private final boolean value;

        /** @param value valeur du booléen */
        public TomlBoolean(boolean value) {
            this.value = value;
        }

        /** @return la valeur */
        public boolean value() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TomlBoolean b && value == b.value;
        }

        @Override
        public int hashCode() {
            return Boolean.hashCode(value);
        }

        @Override
        public String toString() {
            return Boolean.toString(value);
        }
    }

    /** Tableau TOML. */
    final class TomlArray implements TomlValue {
        private final java.util.ArrayList<TomlValue> values;

        /**
         * @param values éléments du tableau ; la liste est copiée
         */
        public TomlArray(java.util.List<TomlValue> values) {
            this.values = new java.util.ArrayList<>(values);
        }

        /** @return les éléments */
        public java.util.List<TomlValue> values() {
            return java.util.Collections.unmodifiableList(values);
        }

        /**
         * @return la liste interne vivante, réservée à l'analyseur
         *         qui construit le document
         */
        java.util.List<TomlValue> mutableValues() {
            return values;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TomlArray a && values.equals(a.values);
        }

        @Override
        public int hashCode() {
            return values.hashCode();
        }

        @Override
        public String toString() {
            return values.toString();
        }
    }

    /** Table TOML : association ordonnée de clés et de valeurs. */
    final class TomlTable implements TomlValue {
        private final java.util.LinkedHashMap<String, TomlValue> entries;

        /**
         * @param entries entrées de la table, dans l'ordre d'écriture ;
         *               la carte est copiée
         */
        public TomlTable(java.util.Map<String, TomlValue> entries) {
            java.util.LinkedHashMap<String, TomlValue> copy = new java.util.LinkedHashMap<>();
            copy.putAll(entries);
            this.entries = copy;
        }

        /** @return les entrées, non modifiables */
        public java.util.Map<String, TomlValue> entries() {
            return java.util.Collections.unmodifiableMap(entries);
        }

        /**
         * @return la carte interne vivante, réservée à l'analyseur
         *         qui construit le document
         */
        java.util.Map<String, TomlValue> mutableEntries() {
            return entries;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TomlTable t && entries.equals(t.entries);
        }

        @Override
        public int hashCode() {
            return entries.hashCode();
        }

        @Override
        public String toString() {
            return entries.toString();
        }
    }
}
