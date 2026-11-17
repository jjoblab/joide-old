package jo.joide.model;

/**
 * Portée d'une dépendance : ce qu'elle expose aux modules en aval.
 */
public enum DependencyScope {
    /** Visible des dépendants, fait partie de l'interface du module. */
    API,

    /** Interne au module, invisible des dépendants à la compilation. */
    IMPLEMENTATION,

    /** Présente à la compilation seulement. */
    COMPILE_ONLY,

    /** Présente à l'exécution seulement. */
    RUNTIME_ONLY,

    /** Dépendance d'API des tests du module. */
    TEST_API,

    /** Dépendance d'implémentation des tests du module. */
    TEST_IMPLEMENTATION,

    /** Dépendance de compilation des tests seulement. */
    TEST_COMPILE_ONLY,

    /** Dépendance d'exécution des tests seulement. */
    TEST_RUNTIME_ONLY;

    /**
     * @return vrai si cette portée ne concerne que les tests
     */
    public boolean isTest() {
        return this == TEST_API || this == TEST_IMPLEMENTATION
                || this == TEST_COMPILE_ONLY || this == TEST_RUNTIME_ONLY;
    }

    /**
     * @param text portée en minuscules, comme dans les fichiers
     * @return la portée correspondante
     * @throws IllegalArgumentException si le texte est inconnu
     */
    public static DependencyScope parse(String text) {
        for (DependencyScope scope : values()) {
            if (scope.name().toLowerCase(java.util.Locale.ROOT).equals(text)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("unknown dependency scope: " + text);
    }
}
