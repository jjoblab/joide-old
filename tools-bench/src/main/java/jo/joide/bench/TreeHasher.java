package jo.joide.bench;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Hash d'arbre (Merkle) sur un répertoire (ADR-014).
 *
 * <p>Chaque fichier contribue SHA-256(chemin relatif LF + SHA-256 du
 * contenu) ; les fichiers sont combinés dans l'ordre lexicographique
 * exact de leur chemin relatif. Le résultat identifie l'arbre entier :
 * c'est la preuve de reproductibilité du générateur (deux générations à
 * graine égale doivent produire la même racine).</p>
 */
public final class TreeHasher {

    /** Statistiques d'un arbre hashé. */
    public static final class Result {
        /** Racine Merkle, hexadécimale minuscule. */
        public final String root;
        /** Nombre de fichiers. */
        public final int files;
        /** Nombre total d'octets écrits. */
        public final long bytes;

        Result(String root, int files, long bytes) {
            this.root = root;
            this.files = files;
            this.bytes = bytes;
        }
    }

    private TreeHasher() {
    }

    /**
     * Hash un répertoire complet.
     *
     * @param root répertoire à hasher
     * @return la racine Merkle et les statistiques
     * @throws IOException si un fichier est illisible (jamais un
     *                     digest silencieux, §4.2)
     */
    public static Result hashTree(Path root) throws IOException {
        Objects.requireNonNull(root, "root");
        MessageDigest digest = newDigest();
        List<Path> files = new ArrayList<>();
        collect(root, root, files);
        Collections.sort(files);
        long bytes = 0;
        for (Path file : files) {
            String relative = root.relativize(file).toString()
                    .replace('\\', '/');
            byte[] content = Files.readAllBytes(file);
            bytes += content.length;
            digest.update(relative.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(newDigest().digest(content));
        }
        return new Result(toHex(digest.digest()), files.size(), bytes);
    }

    private static void collect(Path root, Path dir, List<Path> files)
            throws IOException {
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                children.add(path);
            }
        }
        Collections.sort(children);
        for (Path child : children) {
            if (Files.isDirectory(child)) {
                collect(root, child, files);
            } else {
                files.add(child);
            }
        }
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est garanti par la plateforme : jamais silencieux.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xf, 16));
            out.append(Character.forDigit(b & 0xf, 16));
        }
        return out.toString();
    }
}
