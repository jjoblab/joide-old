package jo.joide.build.jvm.compile;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Inventaire d'un répertoire de sortie de compilation : les classes
 * ÉCRITES par un lot se détectent par différence d'inventaire avant
 * et après (taille + horodatage). Partagé par le pilote embarqué et
 * le pilote forké (lot VII) : le répertoire de sortie vit toujours
 * côté appelant, jamais dans la machine virtuelle du compilateur.
 */
public final class CompileInventory {

    private CompileInventory() {
    }

    /**
     * @param dir répertoire de sortie (peut ne pas exister)
     * @return l'inventaire : chemin relatif → taille et horodatage
     */
    public static Map<String, SizeAndTime> of(Path dir) {
        Map<String, SizeAndTime> files = new TreeMap<>();
        if (!Files.isDirectory(dir)) {
            return files;
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file,
                                                 BasicFileAttributes attrs) {
                    if (file.getFileName().toString().endsWith(".class")) {
                        files.put(dir.relativize(file).toString()
                                        .replace('\\', '/'),
                                new SizeAndTime(attrs.size(),
                                        attrs.lastModifiedTime().toMillis()));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // inventaire vide : chaque classe paraîtra nouvelle, ce qui
            // est conservateur et sans effet sur la correction
        }
        return files;
    }

    /**
     * Les entrées ajoutées ou modifiées entre deux inventaires.
     *
     * @param before inventaire avant le lot
     * @param after  inventaire après le lot
     * @return les chemins relatifs écrits, ordre stable
     */
    public static TreeSet<String> written(Map<String, SizeAndTime> before,
                                          Map<String, SizeAndTime> after) {
        TreeSet<String> written = new TreeSet<>();
        for (Map.Entry<String, SizeAndTime> entry : after.entrySet()) {
            SizeAndTime previous = before.get(entry.getKey());
            if (previous == null || !previous.equals(entry.getValue())) {
                written.add(entry.getKey());
            }
        }
        return written;
    }

    /** Taille et horodatage d'une classe de sortie. */
    public static final class SizeAndTime {
        final long size;
        final long lastModified;

        SizeAndTime(long size, long lastModified) {
            this.size = size;
            this.lastModified = lastModified;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SizeAndTime)) {
                return false;
            }
            SizeAndTime that = (SizeAndTime) other;
            return size == that.size && lastModified == that.lastModified;
        }

        @Override
        public int hashCode() {
            return (int) (size * 31 + lastModified);
        }
    }
}
