package jo.joide.app.android.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import jo.joide.app.android.atelier.EtatAtelier;

/**
 * ADR-085 (décision 5, lot A1) — {@code Graphite.java} reste la source
 * de vérité ; les thèmes {@code Sombre}/{@code Clair} descendent de
 * {@code Theme.Material3.*} avec des RÔLES Material 3 branchés sur les
 * jetons existants. Ce test (JVM, lecture de fichiers seule) vérifie :
 * les parents M3 des deux thèmes, chaque rôle requis DÉFINI et résolu
 * vers une couleur EXISTANTE, l'égalité des dimens avec
 * {@code Graphite} (rayons, espace, échelle typographique), les styles
 * {@code TextAppearance.Jo.*}/{@code ShapeAppearance.Jo.*} (parents M3,
 * valeurs par @dimen), les slots de forme et de texte du thème, et la
 * parité du qualificatif {@code w720dp} avec
 * {@code EtatAtelier.SEUIL_GRAND_ECRAN_DP} — jamais de jeton en l'air.
 */
class ThemesM3Test {

    /** Les ressources de valeurs du module. */
    private static final Path RES = Path.of("src", "main", "res");

    /** Les rôles M3 que les deux thèmes DOIVENT définir et résoudre. */
    private static final List<String> ROLES = List.of(
            "colorPrimary", "colorOnPrimary", "colorPrimaryContainer",
            "colorOnPrimaryContainer",
            "colorSecondary", "colorOnSecondary", "colorSecondaryContainer",
            "colorOnSecondaryContainer",
            "colorTertiary", "colorOnTertiary", "colorTertiaryContainer",
            "colorOnTertiaryContainer",
            "colorSurface", "colorOnSurface", "colorSurfaceVariant",
            "colorOnSurfaceVariant",
            "colorSurfaceContainerLowest", "colorSurfaceContainerLow",
            "colorSurfaceContainer", "colorSurfaceContainerHigh",
            "colorSurfaceContainerHighest", "colorSurfaceDim",
            "colorSurfaceBright",
            "colorOnBackground", "colorOutline", "colorOutlineVariant",
            "colorError", "colorOnError", "colorErrorContainer",
            "colorOnErrorContainer",
            "colorSurfaceInverse", "colorOnSurfaceInverse",
            "colorPrimaryInverse", "android:colorBackground");

    /** Les slots typographiques que le thème branche sur Jo.*. */
    private static final List<String> SLOTS_TEXTE = List.of(
            "textAppearanceBodySmall", "textAppearanceBodyMedium",
            "textAppearanceBodyLarge", "textAppearanceTitleMedium",
            "textAppearanceTitleLarge", "textAppearanceHeadlineMedium",
            "textAppearanceLabelSmall", "textAppearanceLabelLarge");

    /** Les slots de forme que le thème branche sur Jo.*. */
    private static final List<String> SLOTS_FORME = List.of(
            "shapeAppearanceSmallComponent", "shapeAppearanceMediumComponent",
            "shapeAppearanceLargeComponent");

    /** L'égalité Graphite ↔ dimens attendue (nom ↔ constante). */
    private static final Map<String, String> DIMENS = Map.ofEntries(
            Map.entry("jo_rayon_carte", dp(Graphite.RAYON_CARTE)),
            Map.entry("jo_rayon_composant", dp(Graphite.RAYON_COMPOSANT)),
            Map.entry("jo_rayon_puce", dp(Graphite.RAYON_PUCE)),
            Map.entry("jo_grille", dp(Graphite.GRILLE)),
            Map.entry("jo_marge", dp(Graphite.MARGE)),
            Map.entry("jo_gouttiere", dp(Graphite.GOUTTIERE)),
            Map.entry("jo_tuile", dp(Graphite.TUILE)),
            Map.entry("jo_texte_legende", sp(Graphite.LEGENDE)),
            Map.entry("jo_texte_legende_interligne",
                    spInterligne(Graphite.LEGENDE)),
            Map.entry("jo_texte_note", sp(Graphite.NOTE)),
            Map.entry("jo_texte_note_interligne", spInterligne(Graphite.NOTE)),
            Map.entry("jo_texte_corps", sp(Graphite.CORPS)),
            Map.entry("jo_texte_corps_interligne",
                    spInterligne(Graphite.CORPS)),
            Map.entry("jo_texte_sous_titre", sp(Graphite.SOUS_TITRE)),
            Map.entry("jo_texte_sous_titre_interligne",
                    spInterligne(Graphite.SOUS_TITRE)),
            Map.entry("jo_texte_titre", sp(Graphite.TITRE)),
            Map.entry("jo_texte_titre_interligne",
                    spInterligne(Graphite.TITRE)),
            Map.entry("jo_texte_titre_ecran", sp(Graphite.TITRE_ECRAN)),
            Map.entry("jo_texte_titre_ecran_interligne",
                    spInterligne(Graphite.TITRE_ECRAN)),
            Map.entry("jo_texte_code", sp(Graphite.CODE)),
            Map.entry("jo_texte_code_interligne", spInterligne(Graphite.CODE)),
            Map.entry("jo_texte_code_compact", sp(Graphite.CODE_COMPACT)),
            Map.entry("jo_texte_code_compact_interligne",
                    spInterligne(Graphite.CODE_COMPACT)));

    @Test
    void lesDeuxThemesDescendentDeMaterial3() throws Exception {
        Map<String, Map<String, String>> themes = styles(
                RES.resolve("values").resolve("themes.xml").toFile());
        assertEquals("Theme.Material3.Dark.NoActionBar",
                themes.get("Theme.JoIDE.Sombre").get("_parent"),
                "Sombre doit descendre de Theme.Material3.Dark (D2)");
        assertEquals("Theme.Material3.Light.NoActionBar",
                themes.get("Theme.JoIDE.Clair").get("_parent"),
                "Clair doit descendre de Theme.Material3.Light (D2)");
    }

    @Test
    void chaqueRoleM3EstDefiniEtResoutUnJetonExistant() throws Exception {
        Map<String, Map<String, String>> themes = styles(
                RES.resolve("values").resolve("themes.xml").toFile());
        Map<String, String> couleurs = enfants(
                document(RES.resolve("values").resolve("colors.xml").toFile()),
                "color");
        for (String nomTheme : List.of("Theme.JoIDE.Sombre",
                "Theme.JoIDE.Clair")) {
            Map<String, String> items = themes.get(nomTheme);
            assertNotNull(items, nomTheme + " doit exister");
            for (String role : ROLES) {
                String valeur = items.get(role);
                assertTrue(valeur != null && valeur.startsWith("@color/"),
                        nomTheme + " doit poser le rôle " + role
                                + " sur un jeton @color/");
                assertTrue(couleurs.containsKey(
                                valeur.substring("@color/".length())),
                        nomTheme + " : " + role + " référence une couleur "
                                + "absente : " + valeur);
            }
            for (String slot : SLOTS_TEXTE) {
                assertTrue(items.containsKey(slot),
                        nomTheme + " doit brancher " + slot);
            }
            for (String slot : SLOTS_FORME) {
                assertTrue(items.containsKey(slot),
                        nomTheme + " doit brancher " + slot);
            }
        }
    }

    @Test
    void lesDimensEgalentGraphite() throws Exception {
        Map<String, String> xml = enfants(
                document(RES.resolve("values").resolve("dimens.xml")
                        .toFile()), "dimen");
        for (Map.Entry<String, String> attendu : DIMENS.entrySet()) {
            assertEquals(attendu.getValue(), xml.get(attendu.getKey()),
                    "le dimen " + attendu.getKey()
                            + " doit égaler Graphite (source de vérité)");
        }
    }

    @Test
    void lesTextAppearanceJoPortentLEchelleEtUnParentM3() throws Exception {
        Map<String, Map<String, String>> styles = styles(
                RES.resolve("values").resolve("styles.xml").toFile());
        Map<String, String> dimens = enfants(
                document(RES.resolve("values").resolve("dimens.xml").toFile()),
                "dimen");
        List<String> attendus = List.of(
                "TextAppearance.Jo.Legende", "TextAppearance.Jo.Note",
                "TextAppearance.Jo.Corps", "TextAppearance.Jo.SousTitre",
                "TextAppearance.Jo.Titre", "TextAppearance.Jo.TitreEcran",
                "TextAppearance.Jo.Code", "TextAppearance.Jo.CodeCompact");
        for (String nom : attendus) {
            Map<String, String> style = styles.get(nom);
            assertNotNull(style, nom + " doit exister dans styles.xml");
            assertTrue(style.get("_parent") != null
                            && style.get("_parent")
                            .startsWith("TextAppearance.Material3."),
                    nom + " doit hériter d'un TextAppearance.Material3.*");
            String taille = style.get("android:textSize");
            String interligne = style.get("android:lineHeight");
            assertTrue(taille != null && taille.startsWith("@dimen/")
                            && interligne != null
                            && interligne.startsWith("@dimen/"),
                    nom + " : taille et interligne par @dimen, jamais "
                            + "de littéral");
            assertTrue(dimens.containsKey(taille.substring("@dimen/"
                    .length())), nom + " : dimen inconnue " + taille);
            assertTrue(dimens.containsKey(
                            interligne.substring("@dimen/".length())),
                    nom + " : dimen inconnue " + interligne);
            String familleAttendue;
            if (nom.endsWith("Code") || nom.endsWith("CodeCompact")) {
                familleAttendue = "monospace";
            } else if (nom.endsWith("SousTitre") || nom.endsWith("Titre")) {
                // la graisse 600 de Graphite se rapproche par
                // sans-serif-medium (500) — nuance consignée dans ADR-085
                familleAttendue = "sans-serif-medium";
            } else {
                familleAttendue = "sans-serif";
            }
            assertEquals(familleAttendue, style.get("android:fontFamily"),
                    nom + " : la famille doit suivre Graphite");
        }
    }

    @Test
    void lesShapeAppearanceJoPortentLesRayons() throws Exception {
        Map<String, Map<String, String>> styles = styles(
                RES.resolve("values").resolve("styles.xml").toFile());
        Map<String, String> dimens = enfants(
                document(RES.resolve("values").resolve("dimens.xml").toFile()),
                "dimen");
        assertEquals(dp(Graphite.RAYON_COMPOSANT),
                dimens.get(styleCorner(styles, "ShapeAppearance.Jo.Composant")
                        .substring("@dimen/".length())));
        assertEquals(dp(Graphite.RAYON_CARTE),
                dimens.get(styleCorner(styles, "ShapeAppearance.Jo.Carte")
                        .substring("@dimen/".length())));
        assertEquals(dp(Graphite.RAYON_PUCE),
                dimens.get(styleCorner(styles, "ShapeAppearance.Jo.Pilule")
                        .substring("@dimen/".length())));
    }

    @Test
    void leSeuilGrandEcranEstCoherentAvecLEtat() throws Exception {
        assertEquals("false", enfants(document(
                        RES.resolve("values").resolve("bools.xml").toFile()),
                "bool").get("est_grand_ecran"),
                "l'étroit écran est le cas général");
        String attendu = "values-w" + EtatAtelier.SEUIL_GRAND_ECRAN_DP + "dp";
        Path dossier = RES.resolve(attendu);
        assertTrue(Files.isDirectory(dossier),
                "le qualificatif de largeur doit égaler "
                        + "EtatAtelier.SEUIL_GRAND_ECRAN_DP : " + attendu
                        + " (w, PAS sw — suit rotation et multi-fenêtre)");
        assertEquals("true", enfants(document(
                        dossier.resolve("bools.xml").toFile()), "bool")
                        .get("est_grand_ecran"),
                "la largeur disponible atteinte signifie grand écran");
        // aucun autre qualificatif de largeur ne doit exister : une seule
        // source de vérité pour le seuil
        try (var parcours = Files.list(RES)) {
            List<Path> variantes = parcours.filter(d -> {
                String nom = d.getFileName().toString();
                return nom.startsWith("values-w") && nom.endsWith("dp");
            }).toList();
            assertEquals(List.of(dossier), variantes,
                    "un seul qualificatif de largeur — celui du seuil");
        }
    }

    // ---- lectures et utilitaires -----------------------------------

    /** Le cornerSize @dimen/… d'un style de forme. */
    private static String styleCorner(Map<String, Map<String, String>> styles,
                                       String nom) {
        Map<String, String> style = styles.get(nom);
        assertNotNull(style, nom + " doit exister dans styles.xml");
        String coin = style.get("cornerSize");
        assertTrue(coin != null && coin.startsWith("@dimen/"),
                nom + " doit porter cornerSize @dimen/");
        return coin;
    }

    /** name → (items) de chaque style d'un fichier de styles. */
    private static Map<String, Map<String, String>> styles(File xml)
            throws Exception {
        Map<String, Map<String, String>> styles = new LinkedHashMap<>();
        NodeList noeuds = document(xml).getElementsByTagName("style");
        for (int i = 0; i < noeuds.getLength(); i++) {
            Element style = (Element) noeuds.item(i);
            Map<String, String> items = new LinkedHashMap<>();
            String parent = style.getAttribute("parent");
            if (!parent.isEmpty()) {
                items.put("_parent", parent);
            }
            NodeList enfants = style.getElementsByTagName("item");
            for (int j = 0; j < enfants.getLength(); j++) {
                Element item = (Element) enfants.item(j);
                items.put(item.getAttribute("name"),
                        item.getTextContent().trim());
            }
            styles.put(style.getAttribute("name"), items);
        }
        return styles;
    }

    /** name → valeur des enfants d'une balise donnée. */
    private static Map<String, String> enfants(Document doc, String balise) {
        Map<String, String> valeurs = new LinkedHashMap<>();
        NodeList noeuds = doc.getElementsByTagName(balise);
        for (int i = 0; i < noeuds.getLength(); i++) {
            Element e = (Element) noeuds.item(i);
            valeurs.put(e.getAttribute("name"),
                    e.getTextContent().trim());
        }
        return valeurs;
    }

    private static Document document(File xml) throws Exception {
        DocumentBuilderFactory fabrique =
                DocumentBuilderFactory.newInstance();
        fabrique.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);
        return fabrique.newDocumentBuilder().parse(xml);
    }

    /** Une dimension Graphite en dp, telle qu'elle vit en XML. */
    private static String dp(int valeur) {
        return valeur + "dp";
    }

    /** Une taille de texte Graphite en sp (« 13.5sp »). */
    private static String sp(Graphite.StyleTexte style) {
        return nombre(style.taille) + "sp";
    }

    /** Un interligne Graphite en sp. */
    private static String spInterligne(Graphite.StyleTexte style) {
        return nombre(style.interligne) + "sp";
    }

    /** Le nombre sans zéro final parasite (12.0 → 12 ; 13.5 → 13.5). */
    private static String nombre(float valeur) {
        return valeur == (long) valeur
                ? String.valueOf((long) valeur)
                : String.valueOf(valeur);
    }
}
