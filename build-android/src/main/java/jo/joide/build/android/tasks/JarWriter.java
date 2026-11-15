package jo.joide.build.android.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jo.joide.build.android.packaging.ApkWriter;

/**
 * Écriture d'un jar déterministe depuis un répertoire de classes :
 * entrées triées par chemin, horodatages fixes, compression standard.
 * Le même répertoire produit toujours les mêmes octets.
 */
final class JarWriter {

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
        ApkWriter writer = new ApkWriter();
        List<Path> files = new ArrayList<>();
        try (var walk = Files.walk(classesDir)) {
            walk.filter(Files::isRegularFile).sorted().forEach(files::add);
        }
        for (Path file : files) {
            String name = classesDir.relativize(file).toString()
                    .replace('\\', '/');
            writer.deflatedFile(name, file);
        }
        writer.writeTo(output);
    }
}
