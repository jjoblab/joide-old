package jo.joide.deps.lock;

/**
 * Le genre d'une arête de dépendance : ce que l'artefact dépendant
 * expose au consommateur. « api » transmet à la compilation,
 * « runtime » seulement à l'exécution, « compile » est une dépendance
 * de compilation non transmise (l'équivalent Maven de « provided »).
 */
public enum EdgeKind {

    API("api"),
    RUNTIME("runtime"),
    COMPILE("compile");

    private final String text;

    EdgeKind(String text) {
        this.text = text;
    }

    /** @return le texte du lockfile */
    public String text() {
        return text;
    }

    /**
     * @param text texte du lockfile
     * @return le genre, ou {@code null} si inconnu
     */
    public static EdgeKind parse(String text) {
        for (EdgeKind k : values()) {
            if (k.text.equals(text)) {
                return k;
            }
        }
        return null;
    }
}
