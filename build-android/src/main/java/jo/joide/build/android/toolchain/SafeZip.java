package jo.joide.build.android.toolchain;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extraction de fichiers zip quelconques vers un répertoire, protégée
 * contre le zip-slip par la même politique que l'extraction d'AAR du
 * résolveur de dépendances : noms normalisés en barres obliques, tout
 * « .. », chemin absolu, lecteur Windows ou barre oblique inverse
 * refusé ; nombre d'entrées et volume total plafonnés ; extraction en
 * zone d'attente puis déplacement atomique, idempotente par marqueur.
 */
public final class SafeZip {

    /** Un refus d'extraction : entrée et raison. */
    public static final class Rejected extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /** @param message raison du refus */
        public Rejected(String message) {
            super(message);
        }
    }

    private static final int MAX_ENTRIES = 65_536;
    private static final long MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024;
    private static final String MARKER = ".joide-zip-extracted";
    private static final int COPY_BUFFER = 64 * 1024;

    /**
     * Extrait l'archive vers {@code targetDir} ; si le marqueur
     * présent correspond à l'empreinte du fichier, l'extraction est
     * déjà faite et le répertoire est renvoyé tel quel.
     *
     * @param zipFile   fichier archive
     * @param sha256    empreinte du contenu (marqueur d'idempotence)
     * @param targetDir répertoire d'extraction
     * @throws IOException   si l'extraction échoue
     * @throws Rejected      si une entrée tente le zip-slip
     */
    public static void extract(Path zipFile, String sha256, Path targetDir)
            throws IOException {
        Path marker = targetDir.resolve(MARKER);
        if (Files.isRegularFile(marker)
                && sha256.equals(Files.readString(marker))) {
            return;
        }
        Path staging = targetDir.resolveSibling(
                targetDir.getFileName() + ".staging-" + System.nanoTime());
        deleteTree(staging);
        deleteTree(targetDir);
        Files.createDirectories(staging);
        long total = 0;
        int count = 0;
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_ENTRIES) {
                    throw new Rejected("too many zip entries (max "
                            + MAX_ENTRIES + ")");
                }
                String name = entry.getName();
                String reason = slipReason(name);
                if (reason != null) {
                    throw new Rejected(reason + " : " + name);
                }
                Path out = staging.resolve(name);
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                if (out.getParent() != null) {
                    Files.createDirectories(out.getParent());
                }
                total += entry.getSize();
                if (total > MAX_TOTAL_BYTES) {
                    throw new Rejected("zip too large (max "
                            + MAX_TOTAL_BYTES + " bytes)");
                }
                try (InputStream in = zip.getInputStream(entry);
                        OutputStream os = Files.newOutputStream(out)) {
                    byte[] buffer = new byte[COPY_BUFFER];
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        os.write(buffer, 0, read);
                    }
                }
                if (entry.getTime() >= 0) {
                    Files.setLastModifiedTime(out,
                            java.nio.file.attribute.FileTime.fromMillis(
                                    entry.getTime()));
                }
            }
        } catch (IOException | RuntimeException e) {
            deleteTree(staging);
            throw e;
        }
        Files.writeString(staging.resolve(MARKER), sha256);
        try {
            Files.move(staging, targetDir,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(staging, targetDir);
        }
    }

    /**
     * Raison du refus pour un nom d'entrée, ou {@code null} s'il est
     * acceptable.
     *
     * @param name nom de l'entrée dans l'archive
     * @return la raison du refus, ou {@code null}
     */
    static String slipReason(String name) {
        if (name == null || name.isEmpty()) {
            return "empty entry name";
        }
        if (name.indexOf('\\') >= 0) {
            return "backslash in entry name";
        }
        if (name.startsWith("/")) {
            return "absolute entry name";
        }
        if (name.length() > 1 && name.charAt(1) == ':'
                && name.substring(0, 1).matches("[A-Za-z]")) {
            return "windows drive in entry name";
        }
        for (String part : name.split("/")) {
            if (part.isEmpty() && !name.endsWith("/")) {
                return "empty path segment in entry name";
            }
            if (part.equals("..")) {
                return "parent traversal in entry name";
            }
            if (part.equals(".")) {
                return "current directory segment in entry name";
            }
        }
        return null;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private SafeZip() {
    }
}
