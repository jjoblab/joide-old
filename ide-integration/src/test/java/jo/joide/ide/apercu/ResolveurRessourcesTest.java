package jo.joide.ide.apercu;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Les résolveurs de la brique tournent RÉELLEMENT sur la JVM (classes
 * pures de module-resources / module-themes) : résolution des
 * couleurs, chaînes, dimensions et chaînes de thèmes du dossier
 * {@code res/} d'un projet, héritage des styles, mode nuit.
 */
final class ResolveurRessourcesTest {

    @TempDir
    Path tmp;

    private Path res;

    @BeforeEach
    void preparer() throws IOException {
        res = Files.createDirectories(tmp.resolve("app/src/main/res"));
        Path values = Files.createDirectories(res.resolve("values"));
        Files.writeString(values.resolve("colors.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <color name="primaire">#FF6750A4</color>
                    <color name="secondaire">@color/primaire</color>
                    <color name="bordure">#101820</color>
                </resources>
                """);
        Files.writeString(values.resolve("strings.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="app_name">Mon Application</string>
                    <string name="salutation">Bonjour %1$s</string>
                </resources>
                """);
        Files.writeString(values.resolve("dimens.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <dimen name="marge">16dp</dimen>
                    <dimen name="texte">14sp</dimen>
                    <dimen name="bord">1px</dimen>
                </resources>
                """);
    }

    @Test
    void couleursChainesEtDimensionsSeResolvent() throws IOException {
        var resolveur = new jo.layoutlib.resources.ResourceResolverImpl(
                res.toString());
        assertEquals(0xFF6750A4, resolveur.getColor("@color/primaire"));
        assertEquals("Mon Application",
                resolveur.getString("@string/app_name"));
        assertEquals(0xFF6750A4,
                resolveur.getColor("@color/secondaire"),
                "la chaîne d'alias se suit (@color → @color)");
    }

    @Test
    void dimensionsConvertiesParDensite() throws IOException {
        var resolveur = new jo.layoutlib.resources.ResourceResolverImpl(
                res.toString());
        jo.layoutlib.resources.DimensionConverter convertisseur =
                new jo.layoutlib.resources.DimensionConverter(2f, 1f,
                        320f);
        resolveur.setDimensionConverter(convertisseur);
        assertEquals(32f, resolveur.getDimension("@dimen/marge"),
                "16dp en densité 2 = 32 px");
        assertEquals(28f, resolveur.getDimension("@dimen/texte"),
                "14sp en densité 2, échelle 1 = 28 px");
    }

    @Test
    void ressourceAbsenteRendNullJamaisException() throws IOException {
        var resolveur = new jo.layoutlib.resources.ResourceResolverImpl(
                res.toString());
        assertNull(resolveur.getColor("@color/inexistante"));
        assertNull(resolveur.getString("@string/inexistante"));
        assertNull(resolveur.getDimension("@dimen/inexistante"));
    }

    @Test
    void themeHeriteEtResoutLesAttributs() throws IOException {
        var resolveur = new jo.layoutlib.resources.ResourceResolverImpl(
                res.toString());
        jo.layoutlib.themes.ThemeResolverImpl themes =
                new jo.layoutlib.themes.ThemeResolverImpl(resolveur,
                        new jo.layoutlib.resources.DimensionConverter(1f,
                                1f, 160f));
        int declares = themes.registerThemesFile("""
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <style name="BaseTheme" parent="Theme.Material3.DayNight">
                        <item name="colorPrimary">@color/primaire</item>
                    </style>
                    <style name="Theme.App" parent="BaseTheme">
                        <item name="colorSecondary">@color/bordure</item>
                    </style>
                </resources>
                """);
        assertEquals(2, declares, "les deux styles sont parsés");
        themes.setTheme("Theme.App");
        assertEquals(0xFF6750A4,
                themes.getColorAttr("colorPrimary"),
                "l'attribut HÉRITÉ du parent se résout");
        assertEquals(0xFF101820, themes.getColorAttr("colorSecondary"));
        assertNull(themes.getColorAttr("attributInconnu"));
    }

    @Test
    void valeursNuitSeSelectionnentEnModeNuit() throws IOException {
        Path nuit = Files.createDirectories(res.resolve("values-night"));
        Files.writeString(nuit.resolve("colors.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <color name="primaire">#FF000000</color>
                </resources>
                """);
        var resolveur = new jo.layoutlib.resources.ResourceResolverImpl(
                res.toString());
        assertEquals(0xFF6750A4, resolveur.getColor("@color/primaire"));
        resolveur.setNightMode(true);
        assertEquals(0xFF000000, resolveur.getColor("@color/primaire"),
                "le qualificateur -night prend le pas en mode nuit");
    }

    @Test
    void dossierResAbsentRefausExplicite() {
        assertThrows(RuntimeException.class,
                () -> new jo.layoutlib.resources.ResourceResolverImpl(
                        tmp.resolve("introuvable").toString()),
                "un dossier res/ absent refusé, pas silencieusement vide");
    }

    @Test
    void convertisseurDimensionsAuxUnites() {
        jo.layoutlib.resources.DimensionConverter convertisseur =
                new jo.layoutlib.resources.DimensionConverter(2f, 1.5f,
                        320f);
        assertEquals(32f, convertisseur.toPixels("16dp"));
        assertEquals(48f, convertisseur.toPixels("16sp"),
                "16sp en densité 2, échelle 1,5 = 48 px");
        assertEquals(5f, convertisseur.toPixels("5px"));
        assertTrue(convertisseur.isValidDimension("16dp"));
        assertFalse(convertisseur.isValidDimension("bleu"));
    }
}
