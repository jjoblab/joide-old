package jo.joide.build.android.toolchain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Lot XXI — le mur de chargeurs se VÉRIFIE à la source. Les
 * adaptateurs du jeu de sources {@code tools} sont dexés DANS les
 * packs et liés PAR NOM aux classes du moteur qui traversent le
 * chargeur parent : toute classe {@code jo.joide.} qu'ils
 * référencent doit être gardée NON RENOMMÉE dans l'APK par une règle
 * {@code -keep} de {@code app-android/proguard-rules.pro} — sinon
 * R8 l'obfusque et le pack meurt de
 * {@code NoClassDefFoundError: Failed resolution} (le terrain du
 * 2026-11-18 : {@code bundle[debug]} échouait sur
 * {@code Ljo/joide/build/android/packaging/ApkWriter;} — la classe
 * vivait dans l'APK, renommée ; le pack, lui, lie le nom original).
 *
 * <p>Ce test dérive la surface gardée DES règles elles-mêmes (elles
 * ne peuvent pas dériver du test) : chaque référence {@code jo.joide}
 * du jeu {@code tools} doit tomber sous un préfixe
 * {@code -keep class …**}. Un nouvel import hors surface échoue ICI,
 * au bureau — plus jamais sur l'appareil. Les sondes de
 * {@code verifierOmbreApk} (app-android) prouvent ensuite l'APK
 * construit.</p>
 */
class MurEtancheTest {

    /** Le jeu de sources des adaptateurs packés. */
    private static final Path TOOLS = Path.of("src", "tools", "java");

    /** Les règles de conservation de l'APK. */
    private static final Path REGLES = Path.of("..", "app-android",
            "proguard-rules.pro");

    /** Une règle de conservation de paquet : -keep class x.y.** */
    private static final Pattern REGLE_KEEP = Pattern.compile(
            "^-keep\\s+class\\s+([\\w.]+)\\.\\*\\*.*$");

    /** Toute référence pleinement qualifiée au moteur dans le code. */
    private static final Pattern REFERENCE_JO = Pattern.compile(
            "jo\\.joide\\.[\\w.]+");

    /** Le préfixe de son propre paquet : l'adaptateur vit dans le pack. */
    private static final String PAQUET_OUTILS
            = "jo.joide.build.android.tools";

    @Test
    void lesAdaptateursNeReferencentQueLaSurfaceGardee()
            throws IOException {
        List<String> surface = surfaceGardee();
        assertTrue(surface.contains("jo.joide.build.android.packaging"),
                "la surface gardée doit porter packaging (le terrain du "
                        + "2026-11-18) : " + surface);
        List<String> fautes = new ArrayList<>();
        for (Path source : sourcesTools()) {
            for (String ligne : Files.readAllLines(source)) {
                String nue = ligne.trim();
                // les commentaires peuvent CITER des classes sans les
                // lier : seules les lignes de CODE comptent (paquet,
                // imports, références pleinement qualifiées)
                if (nue.startsWith("*") || nue.startsWith("//")
                        || nue.startsWith("/*")) {
                    continue;
                }
                Matcher refs = REFERENCE_JO.matcher(nue);
                while (refs.find()) {
                    String reference = refs.group();
                    if (reference.startsWith(PAQUET_OUTILS)) {
                        continue;
                    }
                    boolean couverte = false;
                    for (String prefixe : surface) {
                        if (reference.startsWith(prefixe + ".")) {
                            couverte = true;
                            break;
                        }
                    }
                    if (!couverte) {
                        fautes.add(source.getFileName() + " → "
                                + reference);
                    }
                }
            }
        }
        assertTrue(fautes.isEmpty(),
                () -> "références du jeu tools HORS surface gardée — "
                        + "ajoute la règle -keep correspondante dans "
                        + "app-android/proguard-rules.pro (le mur de "
                        + "chargeurs : le pack lie ces noms PAR NOM, "
                        + "R8 ne doit pas les renommer) : "
                        + fautes);
    }

    /** Les préfixes gardés, dérivés des règles -keep de l'APK. */
    private static List<String> surfaceGardee() throws IOException {
        assertTrue(Files.isRegularFile(REGLES),
                "règles introuvables : " + REGLES.toAbsolutePath());
        List<String> prefixes = new ArrayList<>();
        for (String ligne : Files.readAllLines(REGLES)) {
            Matcher m = REGLE_KEEP.matcher(ligne.trim());
            if (m.matches()) {
                prefixes.add(m.group(1));
            }
        }
        return prefixes;
    }

    /** Les sources du jeu tools, chemins stables. */
    private static List<Path> sourcesTools() throws IOException {
        assertTrue(Files.isDirectory(TOOLS),
                "jeu de sources tools introuvable : "
                        + TOOLS.toAbsolutePath());
        try (Stream<Path> chemins = Files.walk(TOOLS)) {
            return chemins.filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }
}
