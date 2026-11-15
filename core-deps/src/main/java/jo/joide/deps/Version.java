package jo.joide.deps;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Une version Maven avec l'ordre de comparaison documenté par
 * l'ADR-029 (ordre maison, aligné sur les usages observés de Maven) :
 * les segments numériques se comparent en valeur ; les qualificatifs
 * connus suivent {@code alpha < beta < milestone < rc < snapshot <
 * publication < sp} ; un qualificatif inconnu est au-dessus de
 * {@code sp} et se compare lexicographiquement entre inconnus ;
 * une liste qui s'arrête vaut numériquement 0 (donc « 1.0 » égale
 * « 1.0.0 ») et vaut « publication » face à un qualificatif (donc
 * « 1.0-alpha » est inférieure à « 1.0 »).
 *
 * <p>Une version est un instantané (« SNAPSHOT ») si sa forme
 * littérale se termine par {@code -SNAPSHOT}, sans casse. Les
 * instantanés distants concrets (avec horodatage et numéro de
 * construction) ne portent pas le suffixe : leur caractère
 * d'instantané vient des métadonnées, pas de la chaîne.</p>
 */
public final class Version implements Comparable<Version> {

    private static final int RANK_ALPHA = 1;
    private static final int RANK_BETA = 2;
    private static final int RANK_MILESTONE = 3;
    private static final int RANK_RC = 4;
    private static final int RANK_SNAPSHOT = 5;
    private static final int RANK_RELEASE = 6;
    private static final int RANK_SP = 7;
    private static final int RANK_UNKNOWN = 8;

    private final String raw;
    private final List<Object> tokens;

    private Version(String raw, List<Object> tokens) {
        this.raw = raw;
        this.tokens = tokens;
    }

    /**
     * @param text version littérale
     * @return la version analysée
     * @throws IllegalArgumentException si vide ou mal formée
     */
    public static Version parse(String text) {
        Objects.requireNonNull(text, "text");
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("empty version");
        }
        List<Object> tokens = tokenize(trimmed);
        if (tokens == null) {
            throw new IllegalArgumentException("invalid version: " + text);
        }
        return new Version(trimmed, tokens);
    }

    /** Coupe sur les points et les tirets ; {@code null} si invalide. */
    private static List<Object> tokenize(String text) {
        List<Object> out = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '.' || c == '-') {
                i++;
                continue;
            }
            int start = i;
            while (i < text.length() && text.charAt(i) != '.'
                    && text.charAt(i) != '-') {
                i++;
            }
            String token = text.substring(start, i);
            if (token.isEmpty()) {
                return null;
            }
            Long numeric = tryLong(token);
            out.add(numeric != null ? numeric : token);
        }
        if (out.isEmpty()) {
            return null;
        }
        return out;
    }

    private static Long tryLong(String token) {
        if (token.length() > 18) {
            return null;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        return Long.parseLong(token);
    }

    /** @return la forme littérale, exactement comme analysée */
    public String raw() {
        return raw;
    }

    /** @return vrai si la forme littérale finit par « -SNAPSHOT » */
    public boolean isSnapshot() {
        return raw.regionMatches(true, raw.length() - "-SNAPSHOT".length(),
                "-SNAPSHOT", 0, "-SNAPSHOT".length());
    }

    @Override
    public int compareTo(Version other) {
        int n = Math.max(tokens.size(), other.tokens.size());
        for (int i = 0; i < n; i++) {
            Object left = i < tokens.size() ? tokens.get(i) : END;
            Object right = i < other.tokens.size() ? other.tokens.get(i)
                    : END;
            int c = compareItems(left, right);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    /** Marqueur de fin de liste : vaut 0 en numérique, publication en qualificatif. */
    private static final Object END = new Object();

    private static int compareItems(Object left, Object right) {
        boolean leftNum = left instanceof Long || left == END;
        boolean rightNum = right instanceof Long || right == END;
        if (leftNum && rightNum) {
            long a = left == END ? 0L : (Long) left;
            long b = right == END ? 0L : (Long) right;
            return Long.compare(a, b);
        }
        if (!leftNum && !rightNum) {
            return compareQualifiers((String) left, (String) right);
        }
        // Un numérique face à un qualificatif : le numérique gagne
        // (« 1.0.1 » > « 1.0-alpha ») ; la fin de liste face à un
        // qualificatif vaut publication, donc le qualificatif perd
        // (« 1.0-alpha » < « 1.0 ») — sauf « sp » qui reste au-dessus.
        if (leftNum) {
            if (left == END) {
                String r = (String) right;
                return RANK_RELEASE > qualifierRank(r) ? 1
                        : RANK_RELEASE < qualifierRank(r) ? -1 : 0;
            }
            return 1;
        }
        if (right == END) {
            String l = (String) left;
            return qualifierRank(l) > RANK_RELEASE ? 1
                    : qualifierRank(l) < RANK_RELEASE ? -1 : 0;
        }
        return -1;
    }

    private static int compareQualifiers(String left, String right) {
        int lr = qualifierRank(left);
        int rr = qualifierRank(right);
        if (lr != rr) {
            return Integer.compare(lr, rr);
        }
        if (lr == RANK_UNKNOWN) {
            return left.toLowerCase(Locale.ROOT)
                    .compareTo(right.toLowerCase(Locale.ROOT));
        }
        return 0;
    }

    private static int qualifierRank(String qualifier) {
        switch (qualifier.toLowerCase(Locale.ROOT)) {
            case "a":
            case "alpha":
                return RANK_ALPHA;
            case "b":
            case "beta":
                return RANK_BETA;
            case "m":
            case "milestone":
                return RANK_MILESTONE;
            case "rc":
            case "cr":
                return RANK_RC;
            case "snapshot":
                return RANK_SNAPSHOT;
            case "":
            case "ga":
            case "final":
            case "release":
                return RANK_RELEASE;
            case "sp":
                return RANK_SP;
            default:
                return RANK_UNKNOWN;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Version)) {
            return false;
        }
        return compareTo((Version) other) == 0;
    }

    @Override
    public int hashCode() {
        // L'égalité est l'ordre : réduire les zéros de fin pour que
        // « 1.0 » et « 1.0.0 » partagent le même code.
        List<Object> trimmed = new ArrayList<>(tokens);
        while (!trimmed.isEmpty() && Long.valueOf(0L).equals(
                trimmed.get(trimmed.size() - 1))) {
            trimmed.remove(trimmed.size() - 1);
        }
        return trimmed.hashCode();
    }

    @Override
    public String toString() {
        return raw;
    }
}
