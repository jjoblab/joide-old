package jo.joide.deps;

import java.util.Objects;

/**
 * L'exigence de version d'une dépendance : exacte, plage, ou sélecteur
 * dynamique ({@code latest.release}, {@code latest.integration}).
 *
 * <p>Une exigence exacte marquée stricte (réservée au lockfile et à
 * « strictly » côté ponts) ne participe pas à l'arbitrage des
 * conflits : elle s'impose.</p>
 */
public final class VersionRequirement {

    /** Nature de l'exigence. */
    public enum Kind {
        /** Version littérale. */
        EXACT,
        /** Plage de versions. */
        RANGE,
        /** Dernière publication non instantané. */
        LATEST_RELEASE,
        /** Dernière version, instantanés compris. */
        LATEST_INTEGRATION
    }

    private final Kind kind;
    private final Version exact;
    private final VersionRange range;
    private final boolean strict;
    private final String raw;

    private VersionRequirement(Kind kind, Version exact, VersionRange range,
                               boolean strict, String raw) {
        this.kind = kind;
        this.exact = exact;
        this.range = range;
        this.strict = strict;
        this.raw = raw;
    }

    /**
     * @param text exigence : version littérale, plage, « 1.0+ »
     *             (plage [1.0,)), « + » ((,)), « latest.release »,
     *             « latest.integration »
     * @return l'exigence correspondante
     * @throws IllegalArgumentException si la forme est inconnue
     */
    public static VersionRequirement parse(String text) {
        Objects.requireNonNull(text, "text");
        String t = text.trim();
        String lower = t.toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("latest.release")) {
            return new VersionRequirement(Kind.LATEST_RELEASE, null, null,
                    false, t);
        }
        if (lower.equals("latest.integration")) {
            return new VersionRequirement(Kind.LATEST_INTEGRATION, null, null,
                    false, t);
        }
        if (t.equals("+")) {
            return new VersionRequirement(Kind.RANGE, null,
                    VersionRange.parse("(,)"), false, t);
        }
        if (t.endsWith("+") && t.length() > 1) {
            Version lowerBound = Version.parse(t.substring(0, t.length() - 1));
            VersionRange open = VersionRange.parse("[" + lowerBound.raw()
                    + ",)");
            return new VersionRequirement(Kind.RANGE, null, open, false, t);
        }
        char first = t.charAt(0);
        if (first == '[' || first == '(') {
            return new VersionRequirement(Kind.RANGE, null,
                    VersionRange.parse(t), false, t);
        }
        return new VersionRequirement(Kind.EXACT, Version.parse(t), null,
                false, t);
    }

    /**
     * @param version version imposée
     * @return une exigence exacte stricte (lockfile, « strictly »)
     */
    public static VersionRequirement strict(Version version) {
        return new VersionRequirement(Kind.EXACT,
                Objects.requireNonNull(version, "version"), null, true,
                version.raw());
    }

    /** @return la nature de l'exigence */
    public Kind kind() {
        return kind;
    }

    /** @return la version exacte, ou {@code null} */
    public Version exact() {
        return exact;
    }

    /** @return la plage, ou {@code null} */
    public VersionRange range() {
        return range;
    }

    /** @return vrai si l'exigence s'impose à l'arbitrage */
    public boolean isStrict() {
        return strict;
    }

    /**
     * @param version version candidate
     * @return vrai si la version satisfait l'exigence (les sélecteurs
     *         dynamiques répondent toujours vrai : leur choix passe
     *         par les métadonnées, pas par la filtration)
     */
    public boolean accepts(Version version) {
        switch (kind) {
            case EXACT:
                return exact.compareTo(version) == 0;
            case RANGE:
                return range.contains(version);
            default:
                return true;
        }
    }

    @Override
    public String toString() {
        return raw;
    }
}
