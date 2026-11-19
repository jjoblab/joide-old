package jo.joide.build.android.tasks;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * Écriture d'un jar déterministe depuis un répertoire de classes :
 * entrées triées par chemin, horodatages fixes, compression standard.
 * Le même répertoire produit toujours les mêmes octets.
 *
 * <p>Lot XX/f : le format est le jar STANDARD du JDK
 * ({@code JarOutputStream}) et non plus l'écrivain APK
 * ({@code ApkWriter} — entrées STORED alignées pour l'mmap de
 * resources.arsc, en-têtes manuels). Le R.jar ne sert qu'aux
 * compilations (ecj, kotlinc) et au hachage ABI : jamais
 * installé, jamais mmapé — et sur ART le ZipFile du compilateur
 * (libziparchive) est PLUS STRICT que le bureau sur les
 * particularités d'un zip d'APK : un jar qu'il n'ouvre pas se nomme
 * « Classpath entry points to a non-existent location » puis
 * « Unresolved reference 'R' » (le terrain du 2026-11-18, PREMIER
 * build appareil ayant atteint compileKotlin — l'ouvrière était
 * fraîche, le problème vivait dans les OCTETS du jar, pas dans un
 * cache). La manière éprouvée : un jar ordinaire de
 * {@code JarOutputStream}, qui porte META-INF/MANIFEST.MF MÊME sans
 * classes (un jar à zéro entrée reste lisible partout).</p>
 */
final class JarWriter {

    /** L'horodatage fixe des entrées (1980-01-01, la DOS epoch) :
     *  la déterminisme des octets, requise par le hachage ABI. */
    private static final long HORODATAGE_FIXE = 315532800000L;

    private JarWriter() {
    }

    /**
     * Écrit le jar.
     *
     * @param classesDir répertoire des classes
     * @param output     fichier jar produit
     * @throws IOException si la lecture ou l'écriture échoue
     */
    static void write(Path classesDir, Path output) throws IOException {
        List<Path> files = new ArrayList<>();
        try (var walk = Files.walk(classesDir)) {
            walk.filter(Files::isRegularFile).sorted().forEach(files::add);
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        Manifest manifest = new Manifest();
        Attributes principales = manifest.getMainAttributes();
        principales.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        principales.put(new Attributes.Name("Created-By"), "joide");
        try (OutputStream brut = Files.newOutputStream(output);
             JarOutputStream jar = new JarOutputStream(brut,
                     manifest)) {
            for (Path file : files) {
                String name = classesDir.relativize(file).toString()
                        .replace('\\', '/');
                JarEntry entry = new JarEntry(name);
                entry.setTime(HORODATAGE_FIXE);
                jar.putNextEntry(entry);
                Files.copy(file, jar);
                jar.closeEntry();
            }
        }
    }
}
