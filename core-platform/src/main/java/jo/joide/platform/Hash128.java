package jo.joide.platform;

/**
 * Empreinte de contenu de 128 bits, valeur immuable.
 *
 * <p>Les instances servent d'identifiant de contenu rapide pour la détection
 * de changement ; la confiance (artefacts, lockfile) passe par SHA-256.</p>
 */
public final class Hash128 {

    private final long high;
    private final long low;

    /**
     * @param high mot de poids fort
     * @param low  mot de poids faible
     */
    public Hash128(long high, long low) {
        this.high = high;
        this.low = low;
    }

    /** @return le mot de poids fort */
    public long high() {
        return high;
    }

    /** @return le mot de poids faible */
    public long low() {
        return low;
    }

    /**
     * @return l'empreinte en 32 caractères hexadécimaux minuscules,
     *         poids fort en tête
     */
    public String toHex() {
        StringBuilder out = new StringBuilder(32);
        appendHalf(out, high);
        appendHalf(out, low);
        return out.toString();
    }

    /**
     * @return l'empreinte en 16 octets, poids fort en tête
     */
    public byte[] toBytes() {
        byte[] out = new byte[16];
        for (int i = 0; i < 8; i++) {
            out[i] = (byte) (high >>> (56 - 8 * i));
            out[8 + i] = (byte) (low >>> (56 - 8 * i));
        }
        return out;
    }

    /**
     * Analyse une empreinte produite par {@link #toHex()}.
     *
     * @param text 32 caractères hexadécimaux
     * @return l'empreinte correspondante
     * @throws IllegalArgumentException si la longueur ou les caractères sont invalides
     */
    public static Hash128 fromHex(String text) {
        if (text == null || text.length() != 32) {
            throw new IllegalArgumentException("hash128 expects 32 hex characters");
        }
        return new Hash128(parseHalf(text, 0), parseHalf(text, 16));
    }

    private static long parseHalf(String text, int offset) {
        long value = 0;
        for (int i = 0; i < 16; i++) {
            int digit = Character.digit(text.charAt(offset + i), 16);
            if (digit < 0) {
                throw new IllegalArgumentException(
                        "invalid hex character at position " + (offset + i));
            }
            value = (value << 4) | digit;
        }
        return value;
    }

    private static void appendHalf(StringBuilder out, long value) {
        for (int i = 15; i >= 0; i--) {
            out.append(Character.forDigit((int) (value >>> (4 * i)) & 0xF, 16));
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Hash128)) {
            return false;
        }
        Hash128 that = (Hash128) other;
        return high == that.high && low == that.low;
    }

    @Override
    public int hashCode() {
        return (int) (high ^ (high >>> 32) ^ low ^ (low >>> 32));
    }

    @Override
    public String toString() {
        return toHex();
    }
}
