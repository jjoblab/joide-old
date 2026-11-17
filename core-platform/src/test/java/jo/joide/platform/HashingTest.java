package jo.joide.platform;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrôle de l'empreinte 128 bits sur des vecteurs calculés par la
 * mise en œuvre de référence publique de MurmurHash3, plus les
 * propriétés de base.
 */
class HashingTest {

    /**
     * Vecteurs de référence : longueur, graine, mots de l'empreinte.
     * Les données sont les octets 0, 1, 2… sauf littéraux pour 13 et 43.
     */
    private static final long[][] VECTORS = {
            {0, 0L, 0x0000000000000000L, 0x0000000000000000L},
            {1, 0L, 0x4610abe56eff5cb5L, 0x51622daa78f83583L},
            {3, 0L, 0xb872a12fef53e6beL, 0xfb6255c252b396b6L},
            {15, 0L, 0x47231598fd4925e9L, 0xcd846dee88c67de9L},
            {16, 0L, 0x444924b591903f30L, 0xab906456762fe845L},
            {17, 0L, 0x5c76f40f9fe7c20eL, 0xc15f026b9edaa824L},
            {31, 42L, 0x5fc4e026c822c888L, 0x343304c5c7aa92ebL},
            {256, 0L, 0x1c99c313dc6f12b9L, 0x70d6077fab34cc1eL},
            {256, 1234567890L, 0xba1f104364cf410bL, 0x4d42900fdc18e60dL},
            {13, 0L, 0xf1512dd1d2d665dfL, 0x2c326650a8f3c564L},
            {43, 0L, 0xe34bbc7bbc071b6cL, 0x7a433ca9c49a9347L},
    };

    @Test
    void referenceVectors() {
        for (long[] vector : VECTORS) {
            int length = (int) vector[0];
            long seed = vector[1];
            byte[] data = new byte[length];
            for (int i = 0; i < length; i++) {
                data[i] = (byte) i;
            }
            if (length == 43) {
                data = "The quick brown fox jumps over the lazy dog".getBytes();
            } else if (length == 13) {
                data = "Hello, world!".getBytes();
            }
            Hash128 hash = Hashing.fast128(data, seed);
            assertEquals(vector[2], hash.high(),
                    "mot fort, longueur " + length + ", graine " + seed);
            assertEquals(vector[3], hash.low(),
                    "mot faible, longueur " + length + ", graine " + seed);
        }
    }

    @Test
    void deterministicAcrossCalls() {
        byte[] data = "determinisme".getBytes();
        assertEquals(Hashing.fast128(data, 7L), Hashing.fast128(data, 7L));
    }

    @Test
    void differentSeedsDiffer() {
        byte[] data = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17};
        assertNotEquals(Hashing.fast128(data, 0L), Hashing.fast128(data, 1L));
    }

    @Test
    void fileHashEqualsMemoryHash(@TempDir Path dir) throws IOException {
        byte[] data = new byte[150_000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 31);
        }
        Path file = dir.resolve("big.bin");
        Files.write(file, data);
        assertEquals(Hashing.fast128(data, 3L), Hashing.fast128(file, 3L));
    }

    @Test
    void sha256KnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb924"
                        + "27ae41e4649b934ca495991b7852b855",
                Hashing.toHex(Hashing.sha256(new byte[0])));
        assertEquals("ba7816bf8f01cfea414140de5dae2223"
                        + "b00361a396177a9cb410ff61f20015ad",
                Hashing.toHex(Hashing.sha256("abc".getBytes())));
    }

    @Test
    void sha256FileMatchesMemory(@TempDir Path dir) throws IOException {
        byte[] data = "contenu de fichier".getBytes();
        Path file = dir.resolve("f.txt");
        Files.write(file, data);
        assertArrayEquals(Hashing.sha256(data), Hashing.sha256(file));
    }

    @Test
    void hexRoundTrip() {
        Hash128 hash = Hashing.fast128("round trip".getBytes(), 5L);
        assertEquals(hash, Hash128.fromHex(hash.toHex()));
        assertThrows(IllegalArgumentException.class,
                () -> Hash128.fromHex("courte"));
        assertThrows(IllegalArgumentException.class,
                () -> Hash128.fromHex("zz32chars-invalid-hex-chars-xxxxxxxx"));
    }

    @Test
    void toBytesBigEndian() {
        Hash128 hash = new Hash128(0x0011223344556677L, 0x8899aabbccddeeffL);
        byte[] bytes = hash.toBytes();
        assertEquals(16, bytes.length);
        assertEquals(0x00, bytes[0]);
        assertEquals(0x77, bytes[7]);
        assertEquals((byte) 0x88, bytes[8]);
        assertEquals((byte) 0xff, bytes[15]);
        assertTrue(hash.toHex().startsWith("0011223344556677"));
    }
}
