package jo.joide.vfs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.LinkOption;
import java.util.Objects;
import jo.joide.platform.Hash128;
import jo.joide.platform.Hashing;

/**
 * Hachage de contenu stable : un fichier modifié pendant sa lecture
 * est détecté et relu, jusqu'à ce que ses signaux soient restés
 * constants pendant toute la lecture.
 *
 * <p>La fenêtre de granularité de certains systèmes de fichiers peut
 * cacher un changement (même taille, même date) : le statut est pris
 * avant et après la lecture ; toute divergence déclenche une nouvelle
 * tentative. Après plusieurs tentatives, l'échec est explicite —
 * jamais une empreinte d'un état mêlé de deux versions du fichier.</p>
 */
public final class StableHasher {

    /** Nombre maximal de relectures d'un fichier instable. */
    public static final int MAX_ATTEMPTS = 3;

    private final long seed;

    /**
     * @param seed paramètre de mélange du hachage de contenu
     */
    public StableHasher(long seed) {
        this.seed = seed;
    }

    /**
     * Hache le contenu d'un fichier en vérifiant sa stabilité.
     *
     * @param file fichier à hacher
     * @return l'empreinte du contenu lu
     * @throws IOException si la lecture échoue ou si le fichier reste
     *                     instable après {@value MAX_ATTEMPTS} tentatives
     */
    public Hash128 hashStable(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        IOException last = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            BasicFileAttributes before = Files.readAttributes(
                    file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (before.isDirectory()) {
                throw new IOException("not a regular file: " + file);
            }
            Hash128 hash = Hashing.fast128(file, seed);
            BasicFileAttributes after = Files.readAttributes(
                    file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (before.size() == after.size()
                    && before.lastModifiedTime().equals(after.lastModifiedTime())) {
                return hash;
            }
            last = new IOException("file changed during read: " + file);
        }
        throw last != null ? last
                : new IOException("file unstable: " + file);
    }
}
