package jo.joide.build.jvm.compile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Les arguments du compilateur Kotlin pour une demande de lot —
 * construction SANS AUCUN type Kotlin : la même fabrique alimente le
 * pilote embarqué (bureau, compiler sur le classpath) et le pilote
 * forké (appareil, compilateur dans un pack de dex, lot VII) sans
 * que ce dernier ne référence jamais une classe du compilateur.
 *
 * <p>Les racines Java du module sont passées parmi les sources : le
 * compilateur les lit pour la résolution sans les compiler. Le
 * classpath fourni doit déjà porter la bibliothèque standard
 * (-no-stdlib : le compilateur embarqué n'en ajoute pas de
 * supplémentaire).</p>
 */
public final class KotlinArgs {

    private KotlinArgs() {
    }

    /**
     * @param request demande de compilation
     * @return les arguments du compilateur, ordre stable
     * @throws IOException si une racine Java est illisible
     */
    public static List<String> args(CompileRequest request)
            throws IOException {
        return args(request, List.of(), List.of());
    }

    /**
     * @param request      demande de compilation
     * @param classpathAvant entrées posées EN TÊTE du classpath, AVANT
     *        celles de la demande (lot XVI : la stdlib extraite du
     *        pack par le pilote forké — {@code -no-stdlib} fait de la
     *        stdlib une entrée OBLIGATOIRE du classpath fourni, et le
     *        JAR FS du compilateur ne lit pas les dex du pack)
     * @return les arguments du compilateur, ordre stable
     * @throws IOException si une racine Java est illisible
     */
    public static List<String> args(CompileRequest request,
                                    List<Path> classpathAvant)
            throws IOException {
        return args(request, classpathAvant, List.of());
    }

    /**
     * @param request      demande de compilation
     * @param classpathAvant entrées posées EN TÊTE du classpath (voir
     *        ci-dessus)
     * @param pluginJars les jars des plugins compilateur
     *        ({@code -Xplugin}, un par jar — lot XIX : Compose via la
     *        sonde de classpath de PluginsKotlin ; ZÉRO option -P,
     *        la manière éprouvée)
     * @return les arguments du compilateur, ordre stable
     * @throws IOException si une racine Java est illisible
     */
    public static List<String> args(CompileRequest request,
                                    List<Path> classpathAvant,
                                    List<Path> pluginJars)
            throws IOException {
        List<String> args = new ArrayList<>();
        // les plugins d'abord : kotlinc accepte -Xplugin n'importe où,
        // l'ordre stable garde les builds reproductibles
        for (Path plugin : pluginJars) {
            args.add("-Xplugin");
            args.add(plugin.toString());
        }
        args.add("-classpath");
        args.add(joinPaths(classpathEntiere(request.classpath,
                classpathAvant)));
        args.add("-d");
        args.add(request.outputDir.toString());
        args.add("-jvm-target");
        args.add(request.sourceLevel);
        // la bibliothèque standard vient du classpath fourni
        args.add("-no-stdlib");
        args.add("-no-reflect");
        // la manière éprouvée (ForkedKotlincArtSpike) : -no-jdk —
        // le compilateur ne cherche JAMAIS de racines JDK sous
        // java.home (sur l'appareil ce chemin est
        // /apex/com.android.art : ART, aucun jar — « No class roots
        // are found in the JDK path ») ; java.* se résout depuis le
        // classpath fourni, qui porte android.jar
        if (request.noJdk) {
            args.add("-no-jdk");
        }
        for (Path source : request.sources) {
            args.add(source.toString());
        }
        // racines Java pour la résolution jointe : le compilateur ne
        // parcourt pas les dossiers à la recherche de sources Java,
        // chaque fichier est passé individuellement
        for (Path root : request.javaSourceRootsForResolution) {
            if (Files.isRegularFile(root)) {
                args.add(root.toString());
            } else if (Files.isDirectory(root)) {
                try (var stream = Files.walk(root)) {
                    stream.filter(p -> p.getFileName().toString()
                                    .endsWith(".java"))
                            .sorted()
                            .forEach(p -> args.add(p.toString()));
                }
            }
        }
        return args;
    }

    /** Le classpath final (lot XVI) : les entrées en tête (la stdlib
     *  du pack), puis les entrées demandées — SEULES celles qui
     *  existent : une entrée fantôme ne produit qu'un « Classpath
     *  entry points to a non-existent location » et une résolution
     *  qui sèche (le terrain : {@code core/classes-kotlin} absent au
     *  premier passage). L'ordre de la demande se conserve. */
    static List<Path> classpathEntiere(List<Path> demandees,
                                       List<Path> avant) {
        List<Path> ordre = new ArrayList<>(avant);
        for (Path chemin : demandees) {
            if (Files.isRegularFile(chemin)
                    || Files.isDirectory(chemin)) {
                if (!ordre.contains(chemin)) {
                    ordre.add(chemin);
                }
            }
        }
        return List.copyOf(ordre);
    }

    private static String joinPaths(List<Path> paths) {
        StringBuilder out = new StringBuilder();
        for (Path path : paths) {
            if (out.length() > 0) {
                out.append(java.io.File.pathSeparatorChar);
            }
            out.append(path);
        }
        return out.toString();
    }
}
