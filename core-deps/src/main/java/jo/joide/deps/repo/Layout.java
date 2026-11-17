package jo.joide.deps.repo;

import jo.joide.deps.Version;

/**
 * La disposition Maven 2 : les chemins relatifs d'un dépôt pour une
 * coordonnée, son POM, son « .module », ses métadonnées et ses
 * checksums. Tout en statique pur.
 */
public final class Layout {

    private Layout() {
    }

    /** @param groupId groupe @return le groupe en chemin (« a.b » → « a/b ») */
    public static String groupPath(String groupId) {
        return groupId.replace('.', '/');
    }

    /** @param g groupe @param a artefact
     *  @return le chemin du répertoire de l'artefact (toutes versions) */
    public static String artifactDir(String g, String a) {
        return groupPath(g) + "/" + a;
    }

    /**
     * @param g     groupe
     * @param a     artefact
     * @param v     version littérale
     * @param c     classifieur éventuel (peut être {@code null})
     * @param ext   extension
     * @return le nom de fichier « a-v[-c].ext »
     */
    public static String fileName(String g, String a, String v, String c,
                                  String ext) {
        StringBuilder out = new StringBuilder(a).append('-').append(v);
        if (c != null && !c.isEmpty()) {
            out.append('-').append(c);
        }
        return out.append('.').append(ext).toString();
    }

    /** @param g groupe @param a artefact @param v version
     *  @return le répertoire de la version */
    public static String versionDir(String g, String a, String v) {
        return artifactDir(g, a) + "/" + v;
    }

    /**
     * @param g   groupe
     * @param a   artefact
     * @param v   version littérale
     * @param c   classifieur éventuel
     * @param ext extension
     * @return le chemin relatif de l'artefact dans le dépôt
     */
    public static String artifactPath(String g, String a, String v, String c,
                                      String ext) {
        return versionDir(g, a, v) + "/" + fileName(g, a, v, c, ext);
    }

    /** @param g groupe @param a artefact @param v version
     *  @return le chemin relatif du POM */
    public static String pomPath(String g, String a, String v) {
        return versionDir(g, a, v) + "/" + fileName(g, a, v, null, "pom");
    }

    /** @param g groupe @param a artefact @param v version
     *  @return le chemin relatif du « .module » (GMM) */
    public static String modulePath(String g, String a, String v) {
        return versionDir(g, a, v) + "/" + fileName(g, a, v, null, "module");
    }

    /** @param g groupe @param a artefact
     *  @return le chemin relatif du maven-metadata.xml de l'artefact */
    public static String artifactMetadataPath(String g, String a) {
        return artifactDir(g, a) + "/maven-metadata.xml";
    }

    /** @param g groupe @param a artefact @param v version
     *  @return le chemin relatif du maven-metadata.xml de la version */
    public static String versionMetadataPath(String g, String a, String v) {
        return versionDir(g, a, v) + "/maven-metadata.xml";
    }

    /**
     * @param base    chemin de base (URL ou fichier)
     * @param relPath chemin relatif
     * @return base + « / » + relPath sans double barre
     */
    public static String join(String base, String relPath) {
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1)
                : base;
        return b + "/" + relPath;
    }

    /**
     * Le suffixe d'instantané local d'une version : la partie
     * littérale « -SNAPSHOT » est remplacée par l'instantané concret
     * quand les métadonnées le donnent.
     *
     * @param base      version de base (« 1.0-SNAPSHOT »)
     * @param timestamp horodatage des métadonnées (peut être {@code null})
     * @param build     numéro de construction (peut être {@code null})
     * @return « 1.0-horodatage-numero » ou {@code null} si incomplet
     */
    public static String concreteSnapshot(String base, String timestamp,
                                          String build) {
        if (timestamp == null || build == null || timestamp.isEmpty()
                || build.isEmpty()) {
            return null;
        }
        String prefix = snapshotBase(base);
        if (prefix == null) {
            return null;
        }
        return prefix + "-" + timestamp + "-" + build;
    }

    /**
     * @param version version littérale
     * @return la partie avant « -SNAPSHOT », ou {@code null} si la
     *         version n'est pas un instantané littéral
     */
    public static String snapshotBase(String version) {
        if (version == null) {
            return null;
        }
        int idx = version.toUpperCase(java.util.Locale.ROOT)
                .lastIndexOf("-SNAPSHOT");
        if (idx < 0) {
            return null;
        }
        return version.substring(0, idx);
    }

    /**
     * La base d'une version concrète d'instantané : « 1.0-20260115.
     * 142030-7 » a pour base « 1.0 ». Reconnaît la forme
     * horodatage-numéro ; une version sans cette forme n'a pas de
     * base concrète.
     *
     * @param version version littérale
     * @return la base, ou {@code null} si la forme n'est pas celle
     *         d'un instantané concret
     */
    public static String concreteBase(String version) {
        if (version == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "^(.+)-(\\d{8}\\.\\d{6})-(\\d+)$").matcher(version);
        return m.matches() ? m.group(1) : null;
    }

    /**
     * @param version version
     * @return vrai si la version littérale porte « -SNAPSHOT »
     */
    public static boolean isLiteralSnapshot(Version version) {
        return snapshotBase(version.raw()) != null;
    }
}
