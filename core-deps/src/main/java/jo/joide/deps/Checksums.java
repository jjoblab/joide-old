package jo.joide.deps;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Empreintes SHA-256 et SHA-1 en hexadécimal minuscule, et leur
 * comparaison. Les deux algorithmes sont dans le niveau 28 ; la
 * conversion hexadécimale est maison (HexFormat n'y est pas).
 */
public final class Checksums {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Checksums() {
    }

    /**
     * @param bytes contenu
     * @return l'empreinte SHA-256 en hexadécimal minuscule
     */
    public static String sha256Hex(byte[] bytes) {
        return hex(digest("SHA-256").digest(bytes));
    }

    /**
     * @param text texte
     * @return l'empreinte SHA-256 du texte en UTF-8
     */
    public static String sha256OfText(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param bytes contenu
     * @return l'empreinte SHA-1 en hexadécimal minuscule
     */
    public static String sha1Hex(byte[] bytes) {
        return hex(digest("SHA-1").digest(bytes));
    }

    /**
     * Lit un flux en entier en hachant à la volée.
     *
     * @param in       flux (fermé par l'appelant)
     * @param sizeHint taille attendue, pour l'allocation initiale
     * @return le contenu lu
     * @throws IOException si la lecture échoue
     */
    public static byte[] readAll(InputStream in, int sizeHint)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(
                Math.max(sizeHint, 64));
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * @param expected empreinte attendue (tolérante à la casse)
     * @param actual   empreinte calculée
     * @return vrai si identiques après normalisation
     */
    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return expected.trim().toLowerCase(Locale.ROOT)
                .equals(actual.trim().toLowerCase(Locale.ROOT));
    }

    private static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " unavailable", e);
        }
    }

    private static String hex(byte[] digest) {
        char[] out = new char[digest.length * 2];
        for (int i = 0; i < digest.length; i++) {
            int b = digest[i] & 0xFF;
            out[i * 2] = HEX[b >>> 4];
            out[i * 2 + 1] = HEX[b & 0xF];
        }
        return new String(out);
    }
}
