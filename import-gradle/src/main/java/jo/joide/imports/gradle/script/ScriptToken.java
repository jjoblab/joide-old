package jo.joide.imports.gradle.script;

/**
 * Jeton du lecteur de scripts Gradle : type, texte et ligne d'origine.
 *
 * <p>Type interne du parseur ; les chaînes gardent leur contenu brut
 * (échappements et interpolation non résolus à ce stade, ADR-039).</p>
 */
final class ScriptToken {

    private final Type type;
    private final String text;
    private final int line;

    /**
     * @param type type du jeton
     * @param text texte du jeton (chaîne : contenu sans les guillemets)
     * @param line ligne d'origine, à partir de 1
     */
    ScriptToken(Type type, String text, int line) {
        this.type = type;
        this.text = text;
        this.line = line;
    }

    /** @return le type du jeton */
    Type type() {
        return type;
    }

    /** @return le texte du jeton */
    String text() {
        return text;
    }

    /** @return la ligne d'origine */
    int line() {
        return line;
    }

    /** Types de jetons produits par le lecteur. */
    enum Type {
        /** Identifiant : lettres, chiffres, souligné, dollar. */
        IDENT,
        /** Chaîne entre guillemets doubles (une ligne). */
        STRING_DOUBLE,
        /** Chaîne entre apostrophes (une ligne). */
        STRING_SINGLE,
        /** Chaîne entre triples guillemets doubles (plusieurs lignes). */
        STRING_TRIPLE_DOUBLE,
        /** Chaîne entre triples apostrophes (plusieurs lignes). */
        STRING_TRIPLE_SINGLE,
        /** Nombre. */
        NUMBER,
        /** Ponctuation d'un caractère. */
        PUNCT,
        /** Fin de l'entrée. */
        EOF
    }
}
