package jo.joide.imports.gradle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import jo.joide.platform.Problem;

/**
 * Interpolation des chaînes Groovy et Kotlin : {@code $ident} et
 * {@code ${expr}}, résolus contre les portées connues (propriétés
 * Gradle, ext, versions du catalogue).
 *
 * <p>Une référence non résolue est signalée et laissée telle quelle
 * — jamais ignorée en silence (ADR-040), jamais d'exception.</p>
 */
public final class Interpolation {

    private Interpolation() {
    }

    /**
     * Résout les interpolations d'une chaîne.
     *
     * @param raw       chaîne brute (échappements déjà décodés)
     * @param scope     résolution d'un nom ({@code null} si inconnu)
     * @param problems  réceptacle des références non résolues
     * @param location  localisation lisible, pour les rapports
     * @return la chaîne résolue ; les références inconnues restent
     *         écrites telles quelles
     */
    public static String resolve(String raw, Function<String, String> scope,
                                 List<Problem> problems, String location) {
        if (raw == null || raw.indexOf('$') < 0) {
            return raw;
        }
        StringBuilder out = new StringBuilder(raw.length());
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c != '$' || i + 1 >= raw.length()) {
                out.append(c);
                i++;
                continue;
            }
            char next = raw.charAt(i + 1);
            if (next == '$') {
                out.append('$');
                i += 2;
                continue;
            }
            if (next == '{') {
                int end = raw.indexOf('}', i + 2);
                if (end < 0) {
                    out.append(c);
                    i++;
                    continue;
                }
                String name = raw.substring(i + 2, end).trim();
                out.append(lookup(name, raw, scope, problems, location));
                i = end + 1;
                continue;
            }
            if (Character.isJavaIdentifierStart(next)) {
                int end = i + 1;
                while (end < raw.length()
                        && (Character.isJavaIdentifierPart(
                                raw.charAt(end)))) {
                    end++;
                }
                String name = raw.substring(i + 1, end);
                out.append(lookup(name, raw, scope, problems, location));
                i = end;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String lookup(String name, String raw,
                                 Function<String, String> scope,
                                 List<Problem> problems,
                                 String location) {
        String value = scope.apply(name);
        if (value != null) {
            return value;
        }
        problems.add(Problem.warn("interpolation-unresolved",
                "unresolved reference '${" + name + "}' in " + location));
        return "${" + name + "}";
    }

    /**
     * @param raw chaîne brute
     * @return vrai si la chaîne contient encore une référence non
     *         résolue après passage par {@link #resolve}
     */
    public static boolean hasUnresolved(String raw) {
        if (raw == null) {
            return false;
        }
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == '$') {
                return true;
            }
        }
        return false;
    }

    /** @return une portée vide, pour les tests et les valeurs fixes */
    public static Function<String, String> emptyScope() {
        return name -> null;
    }

    /**
     * Combine des portées : la première qui connaît le nom gagne.
     *
     * @param scopes portées, par ordre de priorité
     * @return la portée combinée
     */
    @SafeVarargs
    public static Function<String, String> chain(
            Function<String, String>... scopes) {
        return name -> {
            for (Function<String, String> scope : scopes) {
                String value = scope.apply(name);
                if (value != null) {
                    return value;
                }
            }
            return null;
        };
    }

    /** @return une portée depuis une table */
    public static Function<String, String> scopeOf(
            Map<String, String> values) {
        Map<String, String> copy = new LinkedHashMap<>(values);
        return copy::get;
    }
}
