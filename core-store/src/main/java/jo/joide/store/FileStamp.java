package jo.joide.store;

import java.util.Objects;

/**
 * État d'un fichier tel que mémorisé dans le snapshot du store :
 * chemin, taille, date de modification, clé de fichier et empreinte
 * de contenu.
 *
 * <p>L'empreinte est paresseuse : elle vaut {@code null} tant que le
 * contenu n'a pas été haché, et reste {@code null} si les signaux de
 * changement (taille, date, clé) n'ont pas bougé depuis le dernier
 * hachage connu.</p>
 */
public final class FileStamp {

    private final String path;
    private final long size;
    private final long mtimeNs;
    private final String fileKey;
    private final String contentHash;

    /**
     * @param path        chemin relatif à la racine du snapshot
     * @param size        taille en octets
     * @param mtimeNs     date de modification en nanosecondes
     * @param fileKey     clé de fichier du système (peut être {@code null})
     * @param contentHash empreinte de contenu en hexadécimal, ou
     *                    {@code null} si non calculée
     */
    public FileStamp(String path, long size, long mtimeNs,
                     String fileKey, String contentHash) {
        this.path = Objects.requireNonNull(path, "path");
        this.size = size;
        this.mtimeNs = mtimeNs;
        this.fileKey = fileKey;
        this.contentHash = contentHash;
    }

    /** @return le chemin relatif */
    public String path() {
        return path;
    }

    /** @return la taille en octets */
    public long size() {
        return size;
    }

    /** @return la date de modification en nanosecondes */
    public long mtimeNs() {
        return mtimeNs;
    }

    /**
     * @return la clé de fichier du système, ou {@code null} si absente
     */
    public String fileKey() {
        return fileKey;
    }

    /**
     * @return l'empreinte de contenu en hexadécimal, ou {@code null}
     */
    public String contentHash() {
        return contentHash;
    }

    /**
     * @param newHash empreinte fraîchement calculée
     * @return le même état avec l'empreinte remplacée
     */
    public FileStamp withContentHash(String newHash) {
        return new FileStamp(path, size, mtimeNs, fileKey, newHash);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FileStamp)) {
            return false;
        }
        FileStamp that = (FileStamp) other;
        return size == that.size
                && mtimeNs == that.mtimeNs
                && path.equals(that.path)
                && Objects.equals(fileKey, that.fileKey)
                && Objects.equals(contentHash, that.contentHash);
    }

    @Override
    public int hashCode() {
        int result = path.hashCode();
        result = 31 * result + (int) (size ^ (size >>> 32));
        result = 31 * result + (int) (mtimeNs ^ (mtimeNs >>> 32));
        result = 31 * result + Objects.hashCode(fileKey);
        result = 31 * result + Objects.hashCode(contentHash);
        return result;
    }

    @Override
    public String toString() {
        return "FileStamp[" + path + " size=" + size
                + " mtime=" + mtimeNs
                + (contentHash == null ? "" : " hash=" + contentHash) + "]";
    }
}
