package jo.joide.app.android.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Lot L1 de l'atelier v2 (ADR-083) — {@code Graphite.java} reste la
 * source de vérité des jetons ; {@code res/values/colors.xml} en est
 * la représentation XML pour les layouts. Ce test vérifie l'égalité
 * des DEUX représentations, palette par palette, jeton par jeton :
 * modifier l'une sans l'autre échoue la suite. Les tonaux se
 * recalculent (12 % sur la surface, 14 % pour erreur/alerte, ligne
 * courante 7 % sur le puits) — jamais une valeur en l'air. Enfin les
 * thèmes posent CHAQUE attribut {@code jo_*} déclaré dans
 * {@code attrs.xml}, en ne référençant que des couleurs existantes.
 */
class JetonsXmlTest {

    private static final Path RES = Path.of("src", "main", "res",
            "values");

    /** Le nom XML de chaque champ de palette (sombre, sans suffixe). */
    private static final Map<String, Integer> JETONS_SOMBRE
            = jetons(Graphite.sombre(), "");

    /** Le nom XML de chaque champ de palette claire (suffixe _clair). */
    private static final Map<String, Integer> JETONS_CLAIR
            = jetons(Graphite.clair(), "_clair");

    private static Map<String, Integer> jetons(Graphite.Palette p,
                                               String suffixe) {
        Map<String, Integer> jetons = new LinkedHashMap<>();
        jetons.put("jo_fond" + suffixe, p.fond);
        jetons.put("jo_surface" + suffixe, p.surface);
        jetons.put("jo_surface_haute" + suffixe, p.surfaceHaute);
        jetons.put("jo_puits" + suffixe, p.puits);
        jetons.put("jo_bord" + suffixe, p.bord);
        jetons.put("jo_bord_fin" + suffixe, p.bordFin);
        jetons.put("jo_texte" + suffixe, p.texte);
        jetons.put("jo_texte2" + suffixe, p.texteSecondaire);
        jetons.put("jo_texte3" + suffixe, p.texteEstompe);
        jetons.put("jo_accent" + suffixe, p.accent);
        jetons.put("jo_reussite" + suffixe, p.reussite);
        jetons.put("jo_alerte" + suffixe, p.alerte);
        jetons.put("jo_erreur" + suffixe, p.erreur);
        jetons.put("jo_violet" + suffixe, p.violet);
        jetons.put("jo_texte_sur_accent" + suffixe, p.texteSurAccent);
        jetons.put("jo_texte_sur_reussite" + suffixe,
                p.texteSurReussite);
        jetons.put("jo_rideau" + suffixe, p.rideau);
        jetons.put("jo_syntaxe_mot_cle" + suffixe, p.syntaxe.motCle);
        jetons.put("jo_syntaxe_type" + suffixe, p.syntaxe.type);
        jetons.put("jo_syntaxe_fonction" + suffixe,
                p.syntaxe.fonction);
        jetons.put("jo_syntaxe_chaine" + suffixe, p.syntaxe.chaine);
        jetons.put("jo_syntaxe_nombre" + suffixe, p.syntaxe.nombre);
        jetons.put("jo_syntaxe_commentaire" + suffixe,
                p.syntaxe.commentaire);
        jetons.put("jo_syntaxe_defaut" + suffixe, p.syntaxe.defaut);
        return jetons;
    }

    @Test
    void lesJetonsSombresEgalentGraphite() throws Exception {
        Map<String, String> xml = couleursXml();
        for (Map.Entry<String, Integer> jeton
                : JETONS_SOMBRE.entrySet()) {
            assertEquals(hex(jeton.getValue()), xml.get(jeton.getKey()),
                    "le jeton " + jeton.getKey()
                            + " de colors.xml doit égaler Graphite");
        }
    }

    @Test
    void lesJetonsClairsEgalentGraphite() throws Exception {
        Map<String, String> xml = couleursXml();
        for (Map.Entry<String, Integer> jeton
                : JETONS_CLAIR.entrySet()) {
            assertEquals(hex(jeton.getValue()), xml.get(jeton.getKey()),
                    "le jeton clair " + jeton.getKey()
                            + " de colors.xml doit égaler Graphite");
        }
    }

    @Test
    void lesTonauxSontLesMelangesAnnonces() throws Exception {
        Map<String, String> xml = couleursXml();
        Graphite.Palette s = Graphite.sombre();
        Graphite.Palette c = Graphite.clair();
        // sombre : mélange sur la surface sombre
        assertEquals(hex(t(s.accent, s.surface, 0.12)),
                xml.get("jo_accent_tonal"));
        assertEquals(hex(t(s.reussite, s.surface, 0.12)),
                xml.get("jo_reussite_tonal"));
        assertEquals(hex(t(s.alerte, s.surface, 0.14)),
                xml.get("jo_alerte_tonal"));
        assertEquals(hex(t(s.erreur, s.surface, 0.14)),
                xml.get("jo_erreur_tonal"));
        assertEquals(hex(t(s.violet, s.surface, 0.12)),
                xml.get("jo_violet_tonal"));
        // ligne courante : accent 7 % sur le puits
        assertEquals(hex(t(s.accent, s.puits, 0.07)),
                xml.get("jo_accent_ligne"));
        // clair : mêmes règles sur la surface claire
        assertEquals(hex(t(c.accent, c.surface, 0.12)),
                xml.get("jo_accent_tonal_clair"));
        assertEquals(hex(t(c.reussite, c.surface, 0.12)),
                xml.get("jo_reussite_tonal_clair"));
        assertEquals(hex(t(c.alerte, c.surface, 0.14)),
                xml.get("jo_alerte_tonal_clair"));
        assertEquals(hex(t(c.erreur, c.surface, 0.14)),
                xml.get("jo_erreur_tonal_clair"));
        assertEquals(hex(t(c.violet, c.surface, 0.12)),
                xml.get("jo_violet_tonal_clair"));
        assertEquals(hex(t(c.accent, c.puits, 0.07)),
                xml.get("jo_accent_ligne_clair"));
    }

    @Test
    void lesPastillesSontLesMelangesAnnonces() throws Exception {
        Map<String, String> xml = couleursXml();
        Graphite.Palette s = Graphite.sombre();
        Graphite.Palette c = Graphite.clair();
        assertEquals(hex(t(s.syntaxe.type, s.puits, 0.16)),
                xml.get("jo_pastille_ambre"));
        assertEquals(hex(t(s.violet, s.puits, 0.16)),
                xml.get("jo_pastille_violet"));
        assertEquals(hex(t(s.reussite, s.puits, 0.14)),
                xml.get("jo_pastille_reussite"));
        assertEquals(hex(t(s.accent, s.puits, 0.12)),
                xml.get("jo_pastille_accent"));
        assertEquals(hex(t(c.syntaxe.type, c.puits, 0.16)),
                xml.get("jo_pastille_ambre_clair"));
        assertEquals(hex(t(c.violet, c.puits, 0.16)),
                xml.get("jo_pastille_violet_clair"));
        assertEquals(hex(t(c.reussite, c.puits, 0.14)),
                xml.get("jo_pastille_reussite_clair"));
        assertEquals(hex(t(c.accent, c.puits, 0.12)),
                xml.get("jo_pastille_accent_clair"));
    }

    @Test
    void lesThemesPosentChaqueAttributEtChaqueCouleurExiste()
            throws Exception {
        Map<String, String> couleurs = couleursXml();
        Map<String, String> attributs = attributsXml();
        // les attributs de VUE (declare-styleable du kit, lot L2 ; tuile
        // de projet lot A2 de l'ADR-085) ne sont PAS des jetons de thème :
        // le test de complétude ne porte que les ?attr/jo_* de couleur
        // réellement posés par les thèmes
        attributs.keySet().removeIf(nom -> nom.startsWith(
                "jo_icone_"));
        attributs.keySet().removeIf(nom -> nom.startsWith(
                "jo_tuile_"));
        Document themes = document(RES.resolve("themes.xml").toFile());
        NodeList styles = themes.getElementsByTagName("style");
        assertTrue(styles.getLength() >= 2,
                "les deux thèmes JoIDE doivent exister");
        for (int i = 0; i < styles.getLength(); i++) {
            Element style = (Element) styles.item(i);
            String nomTheme = style.getAttribute("name");
            Map<String, String> poses = new HashMap<>();
            NodeList items = style.getElementsByTagName("item");
            for (int j = 0; j < items.getLength(); j++) {
                Element item = (Element) items.item(j);
                poses.put(item.getAttribute("name"),
                        item.getTextContent().trim());
            }
            for (String attribut : attributs.keySet()) {
                assertTrue(poses.containsKey(attribut),
                        nomTheme + " doit poser ?attr/" + attribut);
            }
            for (Map.Entry<String, String> pose : poses.entrySet()) {
                String reference = pose.getValue();
                if (reference.startsWith("@color/")) {
                    assertTrue(couleurs.containsKey(
                                    reference.substring("@color/".length())),
                            nomTheme + " référence une couleur absente : "
                                    + reference);
                }
            }
        }
    }

    // ---- lectures et utilitaires -----------------------------------

    /** name → valeur hexadécimale (sans #) de colors.xml. */
    private static Map<String, String> couleursXml() throws Exception {
        return enfantsNamed(document(
                RES.resolve("colors.xml").toFile()), "color");
    }

    /** name → format de attrs.xml. */
    private static Map<String, String> attributsXml() throws Exception {
        return enfantsNamed(document(
                RES.resolve("attrs.xml").toFile()), "attr");
    }

    private static Map<String, String> enfantsNamed(Document doc,
                                                    String balise) {
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

    /** La valeur ARGB en notation Android (#RRGGBB ou #AARRGGBB). */
    private static String hex(int argb) {
        if ((argb >>> 24) == 0xFF) {
            return String.format("#%06X", argb & 0xFFFFFF);
        }
        return String.format("#%08X", argb);
    }

    /** Le mélange top*alpha + base*(1-alpha), canal par canal. */
    private static int t(int top, int base, double alpha) {
        int tr = (top >>> 16) & 0xFF;
        int tg = (top >>> 8) & 0xFF;
        int tb = top & 0xFF;
        int br = (base >>> 16) & 0xFF;
        int bg = (base >>> 8) & 0xFF;
        int bb = base & 0xFF;
        int r = (int) Math.round(tr * alpha + br * (1 - alpha));
        int g = (int) Math.round(tg * alpha + bg * (1 - alpha));
        int b = (int) Math.round(tb * alpha + bb * (1 - alpha));
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }
}
