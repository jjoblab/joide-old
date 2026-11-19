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
 * {@code -no-jdk} — la manière éprouvée ({@code
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
        // l'IDE de référence : -no-jdk, -no-stdlib, -no-reflect)
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

    /**
     * Lot XVI : la stdlib passée « avant » se pose EN TÊTE du
     * classpath et les entrées inexistantes de la demande se
     * FILTRENT — le terrain : « Classpath entry points to a
     * non-existent location: core/classes-kotlin » suivi du
     * « Cannot access built-in declaration 'kotlin.Any' » (une
     * entrée fantôme ne résout rien, la stdlib absente tue tout).
     */
    @Test
    void laStdlibPasseEnTeteEtLesFantomesSeFiltrent(@TempDir Path dossier)
            throws Exception {
        Path source = dossier.resolve("Salut.kt");
        Files.writeString(source, "fun salut(): Int = 1");
        Path sortie = dossier.resolve("sortie");
        Files.createDirectories(sortie);
        Path stdlib = dossier.resolve("kotlin-stdlib-2.3.0.jar");
        Files.writeString(stdlib, "jar");
        Path fantome = dossier.resolve("classes-kotlin");

        CompileRequest demande = new CompileRequest(List.of(source),
                List.of(fantome, sortie), List.of(), sortie, "17",
                "UTF-8", true);
        List<String> args = KotlinArgs.args(demande, List.of(stdlib));
        int classpath = args.indexOf("-classpath");
        assertTrue(classpath >= 0, "le drapeau classpath manque");
        String valeurs = args.get(classpath + 1);
        // la stdlib en tête, l'entrée existante ensuite, le fantôme hors
        assertTrue(valeurs.startsWith(stdlib.toString()),
                "la stdlib doit ouvrir le classpath : " + valeurs);
        assertTrue(valeurs.contains(sortie.getFileName().toString()),
                "l'entrée existante doit rester : " + valeurs);
        assertFalse(valeurs.contains(fantome.toString()),
                "l'entrée fantôme doit se filtrer : " + valeurs);

        // sans entrée « avant » (pilote embarqué du bureau), le
        // comportement historique tient : les fantômes se filtrent
        // aussi, les existants restent
        List<String> sansAvant = KotlinArgs.args(demande);
        String cp = sansAvant.get(sansAvant.indexOf("-classpath") + 1);
        assertFalse(cp.contains(fantome.toString()),
                "le fantôme se filtre aussi sans stdlib : " + cp);
        assertTrue(cp.contains(sortie.getFileName().toString()),
                "l'entrée existante reste : " + cp);
    }
}
