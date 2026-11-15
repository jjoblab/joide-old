package jo.joide.deps;

import java.util.Objects;

/**
 * Comment une dépendance s'expose à son consommateur — en écho des
 * portées du modèle sans en dépendre (core-deps ne voit pas
 * core-model) : les ponts (import-gradle, build-android) traduisent.
 */
public enum Exposure {

    /** Visible des dépendants : fait partie de l'interface compilée. */
    API,

    /** Invisible des dépendants à la compilation. */
    IMPLEMENTATION,

    /** Présente à la compilation seulement, non transitive. */
    COMPILE_ONLY,

    /** Présente à l'exécution seulement. */
    RUNTIME_ONLY
}
