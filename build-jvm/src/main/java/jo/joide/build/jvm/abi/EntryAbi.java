package jo.joide.build.jvm.abi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jo.joide.build.jvm.classfile.ClassFileException;
import jo.joide.build.jvm.classfile.ClassFileReader;
import jo.joide.build.jvm.classfile.ClassModel;

/**
 * Calcul du manifeste ABI d'une entrée de classpath : fichier jar ou
 * répertoire de classes.
 *
 * <p>Les classes {@code module-info} sont ignorées (descripteur de
 * module, sans effet sur la compilation aval) ; {@code package-info}
 * est conservée (annotations de paquet). Une entrée illisible lève
 * une exception : le pipeline transforme l'échec en diagnostic
 * explicite, jamais en ABI silencieusement vide.</p>
 */
public final class EntryAbi {

    private EntryAbi() {
        // utilitaire statique
    }

    /**
     * @param jarOrDir fichier jar ou répertoire de classes
     * @param maxClasses garde-fou anti-bombe : entrées de classes
     *                   au-delà de ce nombre rejetées
     * @return le manifeste ABI de l'entrée
     * @throws IOException si la lecture échoue
     */
    public static AbiManifest compute(Path jarOrDir, int maxClasses)
            throws IOException {
        Map<String, String> entries = new TreeMap<>();
        if (Files.isDirectory(jarOrDir)) {
            scanDirectory(jarOrDir, jarOrDir, entries, maxClasses);
        } else {
            scanJar(jarOrDir, entries, maxClasses);
        }
        return AbiManifest.of(entries);
    }

    private static void scanDirectory(Path root, Path dir,
                                      Map<String, String> entries,
                                      int maxClasses) throws IOException {
        try (var stream = Files.list(dir)) {
            var children = stream.sorted().toList();
            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    scanDirectory(root, child, entries, maxClasses);
                } else if (child.getFileName().toString().endsWith(".class")) {
                    if (entries.size() >= maxClasses) {
                        throw new IOException("class count exceeds limit in "
                                + root);
                    }
                    addEntry(entries, Files.readAllBytes(child));
                }
            }
        }
    }

    private static void scanJar(Path jar, Map<String, String> entries,
                                int maxClasses) throws IOException {
        long totalRaw = 0;
        try (ZipInputStream zip = new ZipInputStream(
                Files.newInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (!name.endsWith(".class")) {
                    continue;
                }
                if (entry.getSize() > 0
                        && entry.getSize() > 64L * 1024 * 1024) {
                    throw new IOException("oversized class entry: " + name);
                }
                byte[] bytes = readBounded(zip, 64L * 1024 * 1024, name);
                totalRaw += bytes.length;
                if (totalRaw > 512L * 1024 * 1024) {
                    throw new IOException("jar class data too large: " + jar);
                }
                if (entries.size() >= maxClasses) {
                    throw new IOException("class count exceeds limit in "
                            + jar);
                }
                addEntry(entries, bytes);
            }
        }
    }

    private static byte[] readBounded(InputStream in, long limit, String name)
            throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) > 0) {
            total += read;
            if (total > limit) {
                throw new IOException("entry too large: " + name);
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static void addEntry(Map<String, String> entries, byte[] bytes) {
        ClassModel model;
        try {
            model = ClassFileReader.read(bytes);
        } catch (ClassFileException e) {
            throw new ClassFileException(
                    "unreadable class in classpath entry: " + e.getMessage(),
                    e);
        }
        if ("module-info".equals(model.binaryName)) {
            return;
        }
        entries.put(model.binaryName, ClassAbi.of(model).hash());
    }
}
