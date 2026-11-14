package jo.joide.platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Fonctions de hachage du socle : empreinte rapide 128 bits pour la
 * détection de changement, SHA-256 pour la confiance.
 *
 * <p>L'empreinte 128 bits utilise l'algorithme MurmurHash3 (variante
 * 64 bits, sortie 128 bits), exécuté en Java pur, sans mémoire non
 * sécurisée. Le paramètre {@code seed} cloisonne les domaines (fichiers,
 * entrées de tâches, arbres) sans changer d'algorithme.</p>
 */
public final class Hashing {

    private static final long C1 = 0x87c37b91114253d5L;
    private static final long C2 = 0x4cf5ad432745937fL;
    private static final int READ_BUFFER = 64 * 1024;

    private Hashing() {
    }

    /**
     * Empreinte 128 bits d'un tableau d'octets.
     *
     * @param data données à hacher, jamais {@code null}
     * @param seed paramètre de mélange
     * @return l'empreinte des données
     */
    public static Hash128 fast128(byte[] data, long seed) {
        Objects.requireNonNull(data, "data");
        return hashBytes(data, 0, data.length, seed);
    }

    /**
     * Empreinte 128 bits d'un fichier, lue en flux.
     *
     * @param file fichier à hacher
     * @param seed paramètre de mélange
     * @return l'empreinte du contenu du fichier
     * @throws IOException si la lecture échoue ; jamais d'empreinte silencieuse
     */
    public static Hash128 fast128(Path file, long seed) throws IOException {
        Objects.requireNonNull(file, "file");
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            State state = new State(seed);
            ByteBuffer buffer = ByteBuffer.allocate(READ_BUFFER)
                    .order(ByteOrder.LITTLE_ENDIAN);
            while (true) {
                if (!buffer.hasRemaining()) {
                    drain(state, buffer);
                }
                if (channel.read(buffer) < 0) {
                    break;
                }
            }
            buffer.flip();
            state.absorbBlocks(buffer);
            int tailLength = buffer.remaining();
            state.absorbTail(buffer, tailLength);
            return state.finish(channel.size());
        }
    }

    /**
     * Consomme les blocs complets d'un tampon plein puis replace la
     * queue éventuelle en tête pour la suite des lectures.
     */
    private static void drain(State state, ByteBuffer buffer) {
        buffer.flip();
        state.absorbBlocks(buffer);
        buffer.compact();
    }

    /**
     * SHA-256 d'un tableau d'octets.
     *
     * @param data données à hacher, jamais {@code null}
     * @return les 32 octets du condensat
     */
    public static byte[] sha256(byte[] data) {
        Objects.requireNonNull(data, "data");
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * SHA-256 d'un fichier, lu en flux.
     *
     * @param file fichier à hacher
     * @return les 32 octets du condensat
     * @throws IOException si la lecture échoue
     */
    public static byte[] sha256(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ)) {
                byte[] buffer = new byte[READ_BUFFER];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * @param digest condensat à représenter
     * @return sa représentation hexadécimale minuscule
     */
    public static String toHex(byte[] digest) {
        Objects.requireNonNull(digest, "digest");
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            out.append(Character.forDigit((b >>> 4) & 0xF, 16));
            out.append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }

    /**
     * Hachage direct d'un tableau complet.
     */
    private static Hash128 hashBytes(byte[] data, int offset, int length, long seed) {
        State state = new State(seed);
        ByteBuffer view = ByteBuffer.wrap(data, offset, length)
                .order(ByteOrder.LITTLE_ENDIAN);
        state.absorbBlocks(view);
        int tailLength = length & 15;
        state.absorbTail(view, tailLength);
        return state.finish(length);
    }

    /**
     * État de hachage murmur : deux accumulateurs de 64 bits.
     */
    private static final class State {
        private long h1;
        private long h2;

        State(long seed) {
            this.h1 = seed;
            this.h2 = seed;
        }

        /**
         * Absorbe tous les blocs complets de 16 octets du tampon ; les
         * octets restants (moins de 16) sont laissés en place.
         */
        void absorbBlocks(ByteBuffer buffer) {
            while (buffer.remaining() >= 16) {
                mix(buffer.getLong(), buffer.getLong());
            }
        }

        /**
         * Absorbe une queue de {@code tailLength} octets (0 à 15) lue
         * depuis le tampon positionné au bon endroit.
         */
        void absorbTail(ByteBuffer tail, int tailLength) {
            long k1 = 0;
            long k2 = 0;
            for (int i = 0; i < tailLength; i++) {
                int b = tail.get() & 0xFF;
                if (i < 8) {
                    k1 |= ((long) b) << (8 * i);
                } else {
                    k2 |= ((long) b) << (8 * (i - 8));
                }
            }
            if (tailLength > 8) {
                k2 *= C2;
                k2 = Long.rotateLeft(k2, 33);
                k2 *= C1;
                h2 ^= k2;
            }
            if (tailLength > 0) {
                k1 *= C1;
                k1 = Long.rotateLeft(k1, 31);
                k1 *= C2;
                h1 ^= k1;
            }
        }

        private void mix(long k1, long k2) {
            k1 *= C1;
            k1 = Long.rotateLeft(k1, 31);
            k1 *= C2;
            h1 ^= k1;
            h1 = Long.rotateLeft(h1, 27);
            h1 += h2;
            h1 = h1 * 5 + 0x52dce729L;

            k2 *= C2;
            k2 = Long.rotateLeft(k2, 33);
            k2 *= C1;
            h2 ^= k2;
            h2 = Long.rotateLeft(h2, 31);
            h2 += h1;
            h2 = h2 * 5 + 0x38495ab5L;
        }

        Hash128 finish(long totalLength) {
            h1 ^= totalLength;
            h2 ^= totalLength;
            h1 += h2;
            h2 += h1;
            h1 = fmix(h1);
            h2 = fmix(h2);
            h1 += h2;
            h2 += h1;
            return new Hash128(h1, h2);
        }

        private static long fmix(long k) {
            k ^= k >>> 33;
            k *= 0xff51afd7ed558ccdL;
            k ^= k >>> 33;
            k *= 0xc4ceb9fe1a85ec53L;
            k ^= k >>> 33;
            return k;
        }
    }
}
