package jo.joide.deps;

import java.util.Objects;

/**
 * Une plage de versions Maven : bornes incluses ou exclues, l'une ou
 * l'autre pouvant être ouverte. Les formes admises : {@code [1.0]},
 * {@code [1.0,2.0)}, {@code (,1.0]}, {@code [1.0,)}, {@code (,)}.
 * Une plage sans crochets n'est pas une plage (c'est une version
 * exacte, traitée par {@link VersionRequirement}).
 *
 * <p>Immuable.</p>
 */
public final class VersionRange {

    private final Version lower;
    private final boolean lowerInclusive;
    private final Version upper;
    private final boolean upperInclusive;
    private final String raw;

    private VersionRange(Version lower, boolean lowerInclusive,
                         Version upper, boolean upperInclusive, String raw) {
        this.lower = lower;
        this.lowerInclusive = lowerInclusive;
        this.upper = upper;
        this.upperInclusive = upperInclusive;
        this.raw = raw;
    }

    /**
     * @param text plage « [a,b] », « [a,b) », « (a,b) », « (a,b] »,
     *             « [a] », « [a,) », « (,b] », « (,) » ; espaces
     *             tolérés autour des bornes
     * @return la plage
     * @throws IllegalArgumentException si la forme est invalide
     */
    public static VersionRange parse(String text) {
        Objects.requireNonNull(text, "text");
        String t = text.trim();
        if (t.length() < 2
                || (t.charAt(0) != '[' && t.charAt(0) != '(')
                || (t.charAt(t.length() - 1) != ']' && t.charAt(t.length() - 1) != ')')) {
            throw new IllegalArgumentException("invalid range: " + text);
        }
        boolean lowerInclusive = t.charAt(0) == '[';
        boolean upperInclusive = t.charAt(t.length() - 1) == ']';
        String body = t.substring(1, t.length() - 1);
        String[] bounds = body.split(",", -1);
        Version lower;
        Version upper;
        boolean bothInclusive;
        if (bounds.length == 1) {
            // Forme « [1.2.3] » : pincement exact, bornes égales.
            lower = Version.parse(bounds[0].trim());
            upper = lower;
            bothInclusive = true;
        } else if (bounds.length == 2) {
            String lowerText = bounds[0].trim();
            String upperText = bounds[1].trim();
            lower = lowerText.isEmpty() ? null : Version.parse(lowerText);
            upper = upperText.isEmpty() ? null : Version.parse(upperText);
            bothInclusive = false;
        } else {
            throw new IllegalArgumentException("invalid range: " + text);
        }
        if (lower == null && !lowerInclusive) {
            // « (, » : l'infini bas est toujours inclus dans la pratique
            // Maven ; on l'accepte tel quel.
            lowerInclusive = true;
        }
        if (lower != null && upper != null && lower.compareTo(upper) > 0) {
            throw new IllegalArgumentException("lower above upper: " + text);
        }
        if (bothInclusive) {
            lowerInclusive = true;
            upperInclusive = true;
        }
        return new VersionRange(lower, lowerInclusive, upper, upperInclusive,
                t);
    }

    /** @return la borne basse, ou {@code null} si ouverte */
    public Version lower() {
        return lower;
    }

    /** @return la borne haute, ou {@code null} si ouverte */
    public Version upper() {
        return upper;
    }

    /** @return vrai si la borne basse est incluse */
    public boolean lowerInclusive() {
        return lowerInclusive;
    }

    /** @return vrai si la borne haute est incluse */
    public boolean upperInclusive() {
        return upperInclusive;
    }

    /**
     * @param version version candidate
     * @return vrai si la version est dans la plage
     */
    public boolean contains(Version version) {
        if (lower != null) {
            int c = version.compareTo(lower);
            if (c < 0 || (c == 0 && !lowerInclusive)) {
                return false;
            }
        }
        if (upper != null) {
            int c = version.compareTo(upper);
            if (c > 0 || (c == 0 && !upperInclusive)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return raw;
    }
}
