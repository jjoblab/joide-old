package jo.joide.build.jvm.compile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Demande de compilation adressée à un pilote : sources à compiler,
 * classpath (chemins de jar ou de répertoires), répertoire de sortie,
 * options minimales communes.
 *
 * <p>Le classpath est ordonné : l'ordre fait partie de la
 * sémantique de résolution. Les racines Java passées à la résolution
 * Kotlin (compilation jointe) sont listées séparément : elles sont
 * lues, jamais compilées.</p>
 */
public final class CompileRequest {

    /** Fichiers sources à compiler, chemins absolus, ordre stable. */
    public final List<Path> sources;
    /** Classpath ordonné (jar ou répertoires). */
    public final List<Path> classpath;
    /** Racines Java lues pour la résolution (compilation jointe). */
    public final List<Path> javaSourceRootsForResolution;
    /** Répertoire de sortie des classes. */
    public final Path outputDir;
    /** Niveau de source et de cible (« 17 »). */
    public final String sourceLevel;
    /** Encodage des sources. */
    public final String encoding;
    /** Compilateur SANS JDK (lot XIV — la manière éprouvée,
     *  {@code ForkedKotlincArtSpike} : « -no-jdk » avec
     *  {@code android.jar} au classpath fourni) : le compilateur ne
     *  cherche JAMAIS de racines JDK sous {@code java.home} — sur
     *  l'appareil ce chemin est {@code /apex/com.android.art} (ART,
     *  aucun jar) et la détection échoue en « No class roots are
     *  found in the JDK path ». Les classes {@code java.*} se
     *  résolvent alors depuis le classpath fourni, qui porte
     *  android.jar. Au bureau (modules JVM purs) : faux — le JDK
     *  réel fournit les racines, le comportement ne change pas. */
    public final boolean noJdk;

    /**
     * @param sources                   sources à compiler
     * @param classpath                 classpath ordonné
     * @param javaSourceRootsForResolution racines Java pour Kotlin
     * @param outputDir                 répertoire de sortie
     * @param sourceLevel               niveau de source/cible
     * @param encoding                  encodage des sources
     * @param noJdk                     vrai : -no-jdk (compilation
     *                                  Android, java.* du classpath)
     */
    public CompileRequest(List<Path> sources, List<Path> classpath,
                          List<Path> javaSourceRootsForResolution,
                          Path outputDir, String sourceLevel,
                          String encoding, boolean noJdk) {
        this.sources = List.copyOf(Objects.requireNonNull(sources,
                "sources"));
        this.classpath = List.copyOf(Objects.requireNonNull(classpath,
                "classpath"));
        this.javaSourceRootsForResolution = List.copyOf(
                Objects.requireNonNull(javaSourceRootsForResolution,
                        "javaSourceRootsForResolution"));
        this.outputDir = Objects.requireNonNull(outputDir, "outputDir");
        this.sourceLevel = Objects.requireNonNull(sourceLevel,
                "sourceLevel");
        this.encoding = Objects.requireNonNull(encoding, "encoding");
        this.noJdk = noJdk;
        if (!StandardCharsets.UTF_8.name().equalsIgnoreCase(encoding)
                && !"UTF-8".equalsIgnoreCase(encoding)) {
            // les sources du pipeline sont toujours UTF-8 (D10) ;
            // toute autre valeur est une erreur de câblage
            throw new IllegalArgumentException(
                    "only UTF-8 sources are supported, got " + encoding);
        }
    }

    /** @return un bâtisseur de demande */
    public static Builder builder() {
        return new Builder();
    }

    /** Bâtisseur de demande de compilation. */
    public static final class Builder {
        private final List<Path> sources = new ArrayList<>();
        private final List<Path> classpath = new ArrayList<>();
        private final List<Path> javaRoots = new ArrayList<>();
        private Path outputDir;
        private String sourceLevel = "17";
        private String encoding = StandardCharsets.UTF_8.name();
        private boolean noJdk;

        /** @param source source à compiler @return ce bâtisseur */
        public Builder source(Path source) {
            sources.add(Objects.requireNonNull(source, "source"));
            return this;
        }

        /** @param entry entrée de classpath @return ce bâtisseur */
        public Builder classpath(Path entry) {
            classpath.add(Objects.requireNonNull(entry, "entry"));
            return this;
        }

        /** @param root racine Java de résolution @return ce bâtisseur */
        public Builder javaSourceRoot(Path root) {
            javaRoots.add(Objects.requireNonNull(root, "root"));
            return this;
        }

        /** @param dir répertoire de sortie @return ce bâtisseur */
        public Builder outputDir(Path dir) {
            this.outputDir = Objects.requireNonNull(dir, "dir");
            return this;
        }

        /** @param level niveau de source/cible @return ce bâtisseur */
        public Builder sourceLevel(String level) {
            this.sourceLevel = Objects.requireNonNull(level, "level");
            return this;
        }

        /** @param sansJdk vrai : -no-jdk (java.* du classpath —
         *  la manière éprouvée, compilation Android)
         *  @return ce bâtisseur */
        public Builder noJdk(boolean sansJdk) {
            this.noJdk = sansJdk;
            return this;
        }

        /** @return la demande construite */
        public CompileRequest build() {
            return new CompileRequest(sources, classpath, javaRoots,
                    outputDir, sourceLevel, encoding, noJdk);
        }
    }
}
