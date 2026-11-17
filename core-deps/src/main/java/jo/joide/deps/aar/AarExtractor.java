package jo.joide.deps.aar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extraction d'un AAR vers un répertoire de cache, protégée contre
 * le zip-slip (ADR-032) : noms d'entrées normalisés, tout « .. » ou
 * chemin absolu rejeté, barre oblique inverse refusée ; nombre
 * d'entrées et volume total plafonnés ; extraction en zone d'attente
 * puis déplacement atomique, idempotente par fichier marqueur.
 */
public final class AarExtractor {

    /** Un refus d'extraction : entrée et raison. */
    public static final class Rejected extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public Rejected(String message) {
            super(message);
        }
    }

    private static final int MAX_ENTRIES = 4096;
    private static final long MAX_TOTAL_BYTES = 512L * 1024 * 1024;
    private static final String MARKER = ".joide-extracted";

    /**
     * Extrait l'AAR vers {@code targetDir}. Si le marqueur présent
     * correspond au contenu, l'extraction est déjà faite.
     *
     * @param aarFile   le fichier AAR dans le cache
     * @param sha256    l'empreinte du contenu (marqueur)
     * @param targetDir répertoire d'extraction
     * @return le contenu extrait
     * @throws IOException   si l'extraction échoue
     * @throws Rejected      si une entrée tente le zip-slip
     */
    public AarContents extract(Path aarFile, String sha256, Path targetDir)
            throws IOException {
        Path marker = targetDir.resolve(MARKER);
        if (Files.isRegularFile(marker)
                && sha256.equals(Files.readString(marker))) {
            return AarContents.of(targetDir);
        }
        // Zone d'attente à côté de la cible, puis déplacement atomique.
        Path staging = targetDir.resolveSibling(
                targetDir.getFileName() + ".staging-" + System.nanoTime());
        deleteTree(staging);
        Files.createDirectories(staging);
        long total = 0;
        int count = 0;
        try (ZipFile zip = new ZipFile(aarFile.toFile())) {
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
                long size = entry.getSize();
                if (size > MAX_TOTAL_BYTES) {
                    throw new Rejected("zip entry too large: " + name);
                }
                long written = 0;
                try (InputStream in = zip.getInputStream(entry);
                     OutputStream os = Files.newOutputStream(out)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        written += n;
                        if (written > MAX_TOTAL_BYTES) {
                            throw new Rejected("total zip volume exceeded");
                        }
                        os.write(buffer, 0, n);
                    }
                }
                total += written;
                if (total > MAX_TOTAL_BYTES) {
                    throw new Rejected("total zip volume exceeded");
                }
            }
        } catch (Rejected e) {
            deleteTree(staging);
            throw e;
        }
        // Marqueur d'idempotence.
        Files.writeString(staging.resolve(MARKER), sha256);
        try {
            moveTree(staging, targetDir);
        } catch (IOException e) {
            if (Files.isRegularFile(marker)) {
                deleteTree(staging);
                return AarContents.of(targetDir);
            }
            deleteTree(staging);
            throw e;
        }
        return AarContents.of(targetDir);
    }

    /**
     * La politique zip-slip, pure et testable. La barre finale des
     * entrées de répertoire est admise.
     *
     * @param name nom d'entrée du zip
     * @return la raison du refus, ou {@code null} si le nom est sûr
     */
    static String slipReason(String name) {
        if (name == null || name.isEmpty()) {
            return "nom vide";
        }
        String work = name.endsWith("/") ? name.substring(0,
                name.length() - 1) : name;
        if (work.isEmpty()) {
            return null; // la racine du zip elle-même
        }
        if (work.indexOf('\\') >= 0) {
            return "barre oblique inverse";
        }
        if (work.startsWith("//")) {
            return "chemin réseau";
        }
        if (work.startsWith("/") || work.startsWith("~")) {
            return "chemin absolu";
        }
        // Lecteur Windows « C: ».
        if (work.length() > 1 && work.charAt(1) == ':') {
            return "lecteur windows";
        }
        for (String part : work.split("/")) {
            if (part.isEmpty()) {
                return "segment vide";
            }
            if ("..".equals(part)) {
                return "traversée de répertoire";
            }
            if (".".equals(part)) {
                return "répertoire courant";
            }
        }
        return null;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            // meilleur effort
                        }
                    });
        }
    }

    private static void moveTree(Path from, Path to) throws IOException {
        Files.createDirectories(to);
        try (java.util.stream.Stream<Path> walk = Files.walk(from)) {
            for (Path p : walk.toList()) {
                Path rel = from.relativize(p);
                Path dest = to.resolve(rel);
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    jo.joide.platform.AtomicFiles.moveAtomically(p, dest);
                }
            }
        }
    }
}
