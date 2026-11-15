package jo.joide.app.android;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Classification d'un répertoire de projet à l'ouverture : natif JoIDE
 * (joide.toml), projet Gradle (import statique requis — mode
 * compatibilité), ou inconnu.
 *
 * <p>La décision ne dépend que de la liste des noms de fichiers à la
 * racine : testable sur la JVM, sans aucun couplage au stockage.
 *
 * <p>Jamais {@code null} ; {@link Kind} jamais {@code null}.
 */
public enum ProjectDetector {
    ;

    /** Sorties possibles de la détection. */
    public enum Kind {
        NATIVE, GRADLE, UNKNOWN
    }

    /** Résultat immuable de la détection d'un répertoire racine. */
    public record Detection(Kind kind, String detail) {
        public Optional<String> compatBanner() {
            return kind == Kind.GRADLE
                    ? Optional.of(detail)
                    : Optional.empty();
        }
    }

    /**
     * Détecte la nature du projet depuis les noms de fichiers racine.
     *
     * @param rootFileNames noms des entrées à la racine du répertoire
     *        choisi (jamais {@code null}, ordre indifférent)
     * @return la détection ; jamais {@code null}
     */
    public static Detection detect(List<String> rootFileNames) {
        boolean toml = false;
        boolean gradle = false;
        for (String name : rootFileNames) {
            String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
            if ("joide.toml".equals(n)) {
                toml = true;
            }
            if ("build.gradle".equals(n) || "build.gradle.kts".equals(n)
                    || "settings.gradle".equals(n) || "settings.gradle.kts".equals(n)) {
                gradle = true;
            }
        }
        if (toml) {
            return new Detection(Kind.NATIVE, "joide.toml");
        }
        if (gradle) {
            return new Detection(Kind.GRADLE,
                    "projet Gradle — import statique requis (mode compatibilité)");
        }
        return new Detection(Kind.UNKNOWN, "ni joide.toml ni script Gradle");
    }
}
