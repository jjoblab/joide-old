package jo.joide.app.android.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * La « capture » du lot G contre la maquette de référence : le test
 * lit les variables CSS du bloc {@code :root} de
 * {@code docs/ecrans/design/index.html} (les jetons y sont
 * textuels, ADR-É2) et les compare, jeton par jeton, aux constantes
 * de {@link Graphite#sombre()} — les maquettes sont sombres. La
 * conformité est prouvée par test, la validation visuelle sur
 * appareil revient au propriétaire (ADR-048, écart P8 n° 4 : pas
 * d'émulateur au bac à sable).
 */
class JetonMaquetteTest {

    private static final Graphite.Palette S = Graphite.sombre();

    @Test
    void jetonsConformesALaMaquette() throws IOException {
        Map<String, String> jetons = jetons(maquette());
        assertEquals("#0E1116", hex(S.fond), "fond vs --fond");
        assertEquals("#151920", hex(S.surface), "surface vs --surface");
        assertEquals("#1B212A", hex(S.surfaceHaute),
                "surfaceHaute vs --surfaceHaute");
        assertEquals("#0B0E12", hex(S.puits), "puits vs --puits");
        assertEquals("#262D38", hex(S.bord), "bord vs --bord");
        assertEquals("#1D232C", hex(S.bordFin), "bordFin vs --bordFin");
        assertEquals("#E7ECF3", hex(S.texte), "texte vs --texte");
        assertEquals("#9AA6B5", hex(S.texteSecondaire),
                "texteSecondaire vs --texte2");
        assertEquals("#64707F", hex(S.texteEstompe),
                "texteEstompe vs --texte3");
        assertEquals("#82AAFF", hex(S.accent), "accent vs --accent");
        assertEquals("#3DDC84", hex(S.reussite), "reussite vs --reussite");
        assertEquals("#E5C07B", hex(S.alerte), "alerte vs --alerte");
        assertEquals("#E06C75", hex(S.erreur), "erreur vs --erreur");
        assertEquals("#C678DD", hex(S.violet), "violet vs --violet");
        assertEquals("#C678DD", hex(S.syntaxe.motCle), "synMot vs --synMot");
        assertEquals("#E5C07B", hex(S.syntaxe.type), "synType vs --synType");
        assertEquals("#61AFEF", hex(S.syntaxe.fonction),
                "synFonction vs --synFonction");
        assertEquals("#98C379", hex(S.syntaxe.chaine),
                "synChaine vs --synChaine");
        assertEquals("#D19A66", hex(S.syntaxe.nombre),
                "synNombre vs --synNombre");
        assertEquals("#7C8695", hex(S.syntaxe.commentaire),
                "synCommentaire vs --synCommentaire");
        assertEquals("#D6DAE3", hex(S.syntaxe.defaut),
                "synDefaut vs --synDefaut");
    }

    @Test
    void laMaquettePorteTousLesJetons() throws IOException {
        Map<String, String> jetons = jetons(maquette());
        String[] attendus = {"fond", "surface", "surfaceHaute", "puits",
                "bord", "bordFin", "texte", "texte2", "texte3", "accent",
                "reussite", "alerte", "erreur", "violet", "synMot",
                "synType", "synFonction", "synChaine", "synNombre",
                "synCommentaire", "synDefaut"};
        for (String nom : attendus) {
            assertNotNull(jetons.get(nom),
                    "la maquette doit porter le jeton --" + nom
                            + " (jetons presents : " + jetons.keySet() + ")");
        }
    }

    @Test
    void laMaquettePorteLaFamilleMono() throws IOException {
        String texte = new String(
                Files.readAllBytes(maquette()), StandardCharsets.UTF_8);
        Matcher bloc = Pattern.compile(":root\\s*\\{([^}]*)\\}")
                .matcher(texte);
        assertTrue(bloc.find(), "le bloc :root doit exister");
        assertTrue(bloc.group(1).contains("monospace"),
                "la maquette declare une famille monospace pour le code");
    }

    private static String hex(int couleur) {
        return String.format(Locale.ROOT, "#%06X", couleur & 0xFFFFFF);
    }

    private static Path maquette() throws IOException {
        Path courant = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 5 && courant != null; i++) {
            Path candidat = courant.resolve("docs").resolve("ecrans")
                    .resolve("design").resolve("index.html");
            if (Files.isRegularFile(candidat)) {
                return candidat;
            }
            courant = courant.getParent();
        }
        throw new IOException("design/index.html not found from "
                + Paths.get("").toAbsolutePath());
    }

    private static Map<String, String> jetons(Path html) throws IOException {
        String texte = new String(Files.readAllBytes(html),
                StandardCharsets.UTF_8);
        Matcher bloc = Pattern.compile(":root\\s*\\{([^}]*)\\}")
                .matcher(texte);
        assertTrue(bloc.find(), "le bloc :root doit exister");
        Map<String, String> valeurs = new HashMap<>();
        Matcher m = Pattern
                .compile("--([A-Za-z0-9]+)\\s*:\\s*#([0-9A-Fa-f]{6})")
                .matcher(bloc.group(1));
        while (m.find()) {
            valeurs.put(m.group(1),
                    m.group(2).toUpperCase(Locale.ROOT));
        }
        return valeurs;
    }
}
