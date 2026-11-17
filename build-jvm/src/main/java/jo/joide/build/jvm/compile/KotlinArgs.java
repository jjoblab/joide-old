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
        List<String> args = new ArrayList<>();
        args.add("-classpath");
        args.add(joinPaths(request.classpath));
        args.add("-d");
        args.add(request.outputDir.toString());
        args.add("-jvm-target");
        args.add(request.sourceLevel);
        // la bibliothèque standard vient du classpath fourni
        args.add("-no-stdlib");
        args.add("-no-reflect");
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
