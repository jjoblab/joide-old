package jo.joide.model.toml;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * Robustesse de l'analyseur TOML par mutations pseudo-aléatoires :
 * deux mille documents mutés (octets modifiés, troncatures,
 * insertions) ne lèvent jamais d'exception et produisent toujours un
 * document diagnostiqué ou sain.
 */
class TomlFuzzTest {

    private static final long SEED = 20260115L;
    private static final int MUTATIONS = 2_000;

    private static final String BASE = """
            # fichier de projet de reference
            schema = "joide.module/1"
            name = "app"
            type = "android-library"
            count = 42
            actif = true

            [android]
            min-sdk = 28

            [[sources]]
            name = "main"
            source = ["src/main/java", "src/main/kotlin"]

            [[dependencies]]
            path = ":core"
            scope = "api"
            variants = ["debug", "release"]
            """;

    @Test
    void mutatedDocumentsNeverThrow() {
        SplittableRandom random = new SplittableRandom(SEED);
        int diagnosed = 0;
        for (int i = 0; i < MUTATIONS; i++) {
            String mutation = mutate(BASE, random);
            try {
                TomlDocument document = Toml.parse(mutation);
                assertNotNull(document.root());
                assertNotNull(document.problems());
                if (!document.isClean()) {
                    diagnosed++;
                }
            } catch (RuntimeException e) {
                throw new AssertionError("mutation " + i + " (graine " + SEED
                        + ") a leve une exception : " + e, e);
            }
        }
        assertTrue(diagnosed > 0,
                "au moins une mutation doit produire un diagnostic");
    }

    /**
     * Applique une mutation : remplacement, suppression ou insertion
     * d'octets, ou troncature.
     */
    private static String mutate(String base, SplittableRandom random) {
        byte[] bytes = base.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int kind = random.nextInt(4);
        switch (kind) {
            case 0: { // remplacement
                int at = random.nextInt(bytes.length);
                bytes[at] = (byte) random.nextInt(256);
                break;
            }
            case 1: { // suppression
                int at = random.nextInt(bytes.length);
                byte[] next = new byte[bytes.length - 1];
                System.arraycopy(bytes, 0, next, 0, at);
                System.arraycopy(bytes, at + 1, next, at, next.length - at);
                bytes = next;
                break;
            }
            case 2: { // insertion
                int at = random.nextInt(bytes.length + 1);
                byte[] next = new byte[bytes.length + 1];
                System.arraycopy(bytes, 0, next, 0, at);
                next[at] = (byte) random.nextInt(256);
                System.arraycopy(bytes, at, next, at + 1, bytes.length - at);
                bytes = next;
                break;
            }
            default: { // troncature
                int cut = random.nextInt(bytes.length);
                byte[] next = new byte[cut];
                System.arraycopy(bytes, 0, next, 0, cut);
                bytes = next;
                break;
            }
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
