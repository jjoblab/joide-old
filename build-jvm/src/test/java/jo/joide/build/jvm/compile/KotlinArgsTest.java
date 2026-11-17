package jo.joide.build.jvm.compile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Les arguments du compilateur Kotlin (lot XIV) : le drapeau
 * {@code -no-jdk} — la manière CodeAssist ({@code
 * ForkedKotlincArtSpike} : « -no-jdk, -no-stdlib, -no-reflect » avec
 * {@code android.jar} au classpath) — suit le champ
 * {@code CompileRequest.noJdk}. Sur l'appareil, {@code java.home} est
 * {@code /apex/com.android.art} (ART, aucun jar) : sans {@code
 * -no-jdk}, la détection JDK échoue en « No class roots are found in
 * the JDK path » AVANT toute compilation. Au bureau, pas de drapeau :
 * le JDK réel fournit les racines, le comportement ne change pas.
 */
class KotlinArgsTest {

    @Test
    void leDrapeauNoJdkSuitLaDemande(@TempDir Path dossier)
            throws Exception {
        Path source = dossier.resolve("Salut.kt");
        Files.writeString(source, "fun salut(): Int = 1");
        Path sortie = dossier.resolve("sortie");
        Files.createDirectories(sortie);

        // la compilation Android (le terrain) : java.* du classpath
        CompileRequest android = new CompileRequest(List.of(source),
                List.of(sortie), List.of(), sortie, "17", "UTF-8",
                true);
        List<String> args = KotlinArgs.args(android);
        assertTrue(args.contains("-no-jdk"),
                "la compilation Android doit poser -no-jdk "
                        + "(java.home n'est PAS un JDK sur l'appareil) : "
                        + args);
        // les drapeaux du classpath-only restent posés (la manière
        // CodeAssist : -no-jdk, -no-stdlib, -no-reflect)
        assertTrue(args.contains("-no-stdlib"));
        assertTrue(args.contains("-no-reflect"));

        // le bureau (modules JVM purs) : le JDK réel fournit les
        // racines — aucun drapeau, le comportement ne change pas
        CompileRequest bureau = new CompileRequest(List.of(source),
                List.of(sortie), List.of(), sortie, "17", "UTF-8",
                false);
        assertFalse(KotlinArgs.args(bureau).contains("-no-jdk"),
                "le bureau garde la détection JDK du compilateur");
    }
}
