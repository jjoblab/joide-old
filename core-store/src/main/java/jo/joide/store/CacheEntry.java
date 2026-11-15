package jo.joide.store;

import java.util.Objects;

/**
 * Entrée de l'index du cache de sorties : empreinte d'entrées, taille
 * et horodatages d'utilisation, ordonnés par usage le moins récent.
 */
public final class CacheEntry {

    private final String fingerprint;
    private final long sizeBytes;
    private final long createdNanos;
    private final long lastUsedNanos;

    /**
     * @param fingerprint   empreinte d'entrées, clé du cache de sorties
     * @param sizeBytes     taille totale des fichiers mis en cache
     * @param createdNanos  instant de création, en nanosecondes
     *                      monotones
     * @param lastUsedNanos dernier accès, en nanosecondes monotones
     */
    public CacheEntry(String fingerprint, long sizeBytes,
                      long createdNanos, long lastUsedNanos) {
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.sizeBytes = sizeBytes;
        this.createdNanos = createdNanos;
        this.lastUsedNanos = lastUsedNanos;
    }

    /** @return l'empreinte d'entrées */
    public String fingerprint() {
        return fingerprint;
    }

    /** @return la taille totale des fichiers, en octets */
    public long sizeBytes() {
        return sizeBytes;
    }

    /** @return l'instant de création, en nanosecondes monotones */
    public long createdNanos() {
        return createdNanos;
    }

    /** @return le dernier accès, en nanosecondes monotones */
    public long lastUsedNanos() {
        return lastUsedNanos;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CacheEntry)) {
            return false;
        }
        CacheEntry that = (CacheEntry) other;
        return fingerprint.equals(that.fingerprint)
                && sizeBytes == that.sizeBytes
                && createdNanos == that.createdNanos
                && lastUsedNanos == that.lastUsedNanos;
    }

    @Override
    public int hashCode() {
        return fingerprint.hashCode() * 31 + Long.hashCode(sizeBytes) * 7
                + Long.hashCode(createdNanos) + Long.hashCode(lastUsedNanos);
    }

    @Override
    public String toString() {
        return "CacheEntry[fingerprint=" + fingerprint + ", sizeBytes="
                + sizeBytes + ", createdNanos=" + createdNanos
                + ", lastUsedNanos=" + lastUsedNanos + "]";
    }
}
