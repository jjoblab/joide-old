package jo.joide.ide.langages;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.concurrent.TimeUnit;

/**
 * Fixture des tests de moteurs : compile une VRAIE classe (javac du JDK
 * courant, en processus externe — le android.jar factice des tests
 * unitaires Android ne porte pas javax.tools) et la met en jar — les
 * moteurs consomment du bytecode réel, jamais un faux fichier
 * d'octets.
 */
final class CompilateurDeClasse {

    private CompilateurDeClasse() {
    }

    /**
     * Compile la source donnée et produit un jar contenant la classe.
     *
     * @param tmp        répertoire de travail
     * @param nomFichier nom du fichier source (« OutilB.java »)
     * @param source     source Java complète
     * @param nomJar      nom du jar produit (« util-b.jar »)
     * @return le chemin du jar
     */
    static Path compilerEnJar(Path tmp, String nomFichier, String source,
            String nomJar) throws IOException {
        Path sources = Files.createDirectories(tmp.resolve("srcs"));
        Path sorties = Files.createDirectories(tmp.resolve("classes"));
        Files.writeString(sources.resolve(nomFichier), source,
                StandardCharsets.UTF_8);
        Path javac = javac();
        if (javac == null) {
            throw new IllegalStateException(
                    "javac not found under java.home="
                            + System.getProperty("java.home"));
        }
        Process compilation = new ProcessBuilder(
                javac.toString(),
                "-d", sorties.toString(),
                sources.resolve(nomFichier).toString())
                .redirectErrorStream(true)
                .start();
        try {
            String sortie = new String(
                    compilation.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            boolean fini = compilation.waitFor(60, TimeUnit.SECONDS);
            if (!fini || compilation.exitValue() != 0) {
                throw new IllegalStateException(
                        "fixture class compilation failed: "
                                + sortie);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("compilation interrupted", e);
        }
        Path jar = tmp.resolve(nomJar);
        try (JarOutputStream flux = new JarOutputStream(
                Files.newOutputStream(jar))) {
            ajouterRepertoire(flux, sorties, sorties);
        }
        return jar;
    }

    private static Path javac() {
        String domicile = System.getProperty("java.home");
        if (domicile == null) {
            return null;
        }
        for (String executable : List.of("javac", "javac.exe")) {
            Path candidat = Path.of(domicile, "bin", executable);
            if (Files.isExecutable(candidat)) {
                return candidat;
            }
        }
        return null;
    }

    private static void ajouterRepertoire(JarOutputStream flux,
            Path racine, Path dossier) throws IOException {
        try (var entrees = Files.list(dossier)) {
            List<Path> enfants = entrees.sorted().toList();
            for (Path enfant : enfants) {
                if (Files.isDirectory(enfant)) {
                    ajouterRepertoire(flux, racine, enfant);
                    continue;
                }
                String nom = racine.relativize(enfant).toString()
                        .replace('\\', '/');
                flux.putNextEntry(new JarEntry(nom));
                Files.copy(enfant, flux);
                flux.closeEntry();
            }
        }
    }
}
