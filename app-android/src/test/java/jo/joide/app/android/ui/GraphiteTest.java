package jo.joide.app.android.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Les jetons Graphite en code contre leurs sources : valeurs
 * exactes des deux palettes (ADR-É2 sombre, ADR-048 claire),
 * contrastes AA prouvés par la mathématique (pas affirmés), mélange
 * et tuile tonale, rayons, mouvement, espace, typographie. Un
 * écart ici est un écart de design, pas un détail.
 */
class GraphiteTest {

    private static final Graphite.Palette S = Graphite.sombre();
    private static final Graphite.Palette C = Graphite.clair();

    @Test
    void paletteSombreExacte() {
        assertEquals(0xFF0E1116, S.fond, "fond");
        assertEquals(0xFF151920, S.surface, "surface");
        assertEquals(0xFF1B212A, S.surfaceHaute, "surfaceHaute");
        assertEquals(0xFF0B0E12, S.puits, "puits");
        assertEquals(0xFF262D38, S.bord, "bord");
        assertEquals(0xFF1D232C, S.bordFin, "bordFin");
        assertEquals(0xFFE7ECF3, S.texte, "texte");
        assertEquals(0xFF9AA6B5, S.texteSecondaire, "texteSecondaire");
        assertEquals(0xFF64707F, S.texteEstompe, "texteEstompe");
        assertEquals(0xFF82AAFF, S.accent, "accent");
        assertEquals(0xFF3DDC84, S.reussite, "reussite");
        assertEquals(0xFFE5C07B, S.alerte, "alerte");
        assertEquals(0xFFE06C75, S.erreur, "erreur");
        assertEquals(0xFFC678DD, S.violet, "violet");
        assertEquals(0xFF0B1524, S.texteSurAccent, "texteSurAccent");
        assertEquals(0xFF07130C, S.texteSurReussite, "texteSurReussite");
        assertEquals(0x7A0B0E12, S.rideau, "rideau sombre : puits a 48 %");
        assertEquals(0xFFC678DD, S.syntaxe.motCle, "syn motCle");
        assertEquals(0xFFE5C07B, S.syntaxe.type, "syn type");
        assertEquals(0xFF61AFEF, S.syntaxe.fonction, "syn fonction");
        assertEquals(0xFF98C379, S.syntaxe.chaine, "syn chaine");
        assertEquals(0xFFD19A66, S.syntaxe.nombre, "syn nombre");
        assertEquals(0xFF7C8695, S.syntaxe.commentaire, "syn commentaire");
        assertEquals(0xFFD6DAE3, S.syntaxe.defaut, "syn defaut");
    }

    @Test
    void paletteClaireExacte() {
        assertEquals(0xFFF3F6FA, C.fond, "fond");
        assertEquals(0xFFFFFFFF, C.surface, "surface");
        assertEquals(0xFFE9EEF6, C.surfaceHaute, "surfaceHaute");
        assertEquals(0xFFFFFFFF, C.puits, "puits");
        assertEquals(0xFFD9E1EC, C.bord, "bord");
        assertEquals(0xFFE5EBF3, C.bordFin, "bordFin");
        assertEquals(0xFF1C2430, C.texte, "texte");
        assertEquals(0xFF5A6B80, C.texteSecondaire, "texteSecondaire");
        assertEquals(0xFF8A97A8, C.texteEstompe, "texteEstompe");
        assertEquals(0xFF2E63D6, C.accent, "accent");
        assertEquals(0xFF0D7D45, C.reussite, "reussite");
        assertEquals(0xFF8A6A16, C.alerte, "alerte");
        assertEquals(0xFFC73540, C.erreur, "erreur");
        assertEquals(0xFFA026AA, C.violet, "violet");
        assertEquals(0xFFFFFFFF, C.texteSurAccent, "texteSurAccent");
        assertEquals(0xFFFFFFFF, C.texteSurReussite, "texteSurReussite");
        assertEquals(0x3D1C2430, C.rideau, "rideau clair : 24 %");
        assertEquals(0xFFA026AA, C.syntaxe.motCle, "syn motCle");
        assertEquals(0xFFB45309, C.syntaxe.type, "syn type");
        assertEquals(0xFF2E63D6, C.syntaxe.fonction, "syn fonction");
        assertEquals(0xFF22863A, C.syntaxe.chaine, "syn chaine");
        assertEquals(0xFFC25314, C.syntaxe.nombre, "syn nombre");
        assertEquals(0xFF667385, C.syntaxe.commentaire, "syn commentaire");
        assertEquals(0xFF384556, C.syntaxe.defaut, "syn defaut");
    }

    @Test
    void contrastesAaProuvesSombre() {
        aaSombre("texte/fond", S.texte, S.fond);
        aaSombre("texte/surface", S.texte, S.surface);
        aaSombre("texte/puits", S.texte, S.puits);
        aaSombre("texteSecondaire/surface", S.texteSecondaire, S.surface);
        aaSombre("texteSecondaire/fond", S.texteSecondaire, S.fond);
        aaSombre("texteSecondaire/surfaceHaute",
                S.texteSecondaire, S.surfaceHaute);
        aaSombre("accent/surface", S.accent, S.surface);
        aaSombre("accent/fond", S.accent, S.fond);
        aaSombre("reussite/surface", S.reussite, S.surface);
        aaSombre("alerte/surface", S.alerte, S.surface);
        aaSombre("erreur/surface", S.erreur, S.surface);
        aaSombre("violet/surface", S.violet, S.surface);
        aaSombre("texteSurAccent/accent", S.texteSurAccent, S.accent);
        aaSombre("texteSurReussite/reussite",
                S.texteSurReussite, S.reussite);
        aaSombre("syn commentaire/puits", S.syntaxe.commentaire, S.puits);
        aaSombre("syn chaine/puits", S.syntaxe.chaine, S.puits);
        aaSombre("syn nombre/puits", S.syntaxe.nombre, S.puits);
    }

    @Test
    void contrastesAaProuvesClair() {
        aaClair("texte/fond", C.texte, C.fond);
        aaClair("texte/surface", C.texte, C.surface);
        aaClair("texte/puits", C.texte, C.puits);
        aaClair("texteSecondaire/surface", C.texteSecondaire, C.surface);
        aaClair("texteSecondaire/fond", C.texteSecondaire, C.fond);
        aaClair("texteSecondaire/surfaceHaute",
                C.texteSecondaire, C.surfaceHaute);
        aaClair("accent/surface", C.accent, C.surface);
        aaClair("accent/fond", C.accent, C.fond);
        aaClair("reussite/surface", C.reussite, C.surface);
        aaClair("alerte/surface", C.alerte, C.surface);
        aaClair("erreur/surface", C.erreur, C.surface);
        aaClair("violet/surface", C.violet, C.surface);
        aaClair("texteSurAccent/accent", C.texteSurAccent, C.accent);
        aaClair("texteSurReussite/reussite",
                C.texteSurReussite, C.reussite);
        aaClair("syn commentaire/puits", C.syntaxe.commentaire, C.puits);
        aaClair("syn chaine/puits", C.syntaxe.chaine, C.puits);
        aaClair("syn nombre/puits", C.syntaxe.nombre, C.puits);
        aaClair("syn type/puits", C.syntaxe.type, C.puits);
        aaClair("syn motCle/puits", C.syntaxe.motCle, C.puits);
        aaClair("syn fonction/puits", C.syntaxe.fonction, C.puits);
        aaClair("syn defaut/puits", C.syntaxe.defaut, C.puits);
    }

    private void aaSombre(String couple, int a, int b) {
        assertTrue(Graphite.aa(a, b),
                "sombre, " + couple + " : " + Graphite.contraste(a, b));
    }

    private void aaClair(String couple, int a, int b) {
        assertTrue(Graphite.aa(a, b),
                "clair, " + couple + " : " + Graphite.contraste(a, b));
    }

    @Test
    void tuileTonaleEtMelange() {
        assertEquals(0xFF1A302C,
                Graphite.tuileTonale(S.reussite, S.surface),
                "tuile android sombre : reussite a 12 % sur surface");
        assertEquals(Graphite.melange(S.accent, 0.12f, S.surface),
                Graphite.tuileTonale(S.accent, S.surface),
                "tuile = melange a 12 %, par definition");
        assertEquals(S.surface,
                Graphite.melange(S.accent, 0f, S.surface),
                "melange a 0 % = support");
        assertEquals(S.accent,
                Graphite.melange(S.accent, 1f, S.surface),
                "melange a 100 % = couleur");
    }

    @Test
    void modesEtSens() {
        assertTrue(Graphite.estSombre(S), "sombre est sombre");
        assertFalse(Graphite.estSombre(C), "clair n'est pas sombre");
        assertEquals(S, Graphite.pour(Graphite.Mode.SOMBRE));
        assertEquals(C, Graphite.pour(Graphite.Mode.CLAIR));
    }

    @Test
    void rayonsMouvementEspaceTypographie() {
        assertEquals(16, Graphite.RAYON_CARTE);
        assertEquals(12, Graphite.RAYON_COMPOSANT);
        assertEquals(999, Graphite.RAYON_PUCE);
        assertEquals(180, Graphite.MOUVEMENT_STANDARD);
        assertEquals(240, Graphite.MOUVEMENT_ECRAN);
        assertEquals(4, Graphite.GRILLE);
        assertEquals(20, Graphite.MARGE);
        assertEquals(20, Graphite.GOUTTIERE);
        assertEquals(46, Graphite.TUILE);

        assertEquals(12f, Graphite.LEGENDE.taille);
        assertEquals(16f, Graphite.LEGENDE.interligne);
        assertFalse(Graphite.LEGENDE.mono);
        assertEquals(13f, Graphite.NOTE.taille);
        assertEquals(18f, Graphite.NOTE.interligne);
        assertEquals(15f, Graphite.CORPS.taille);
        assertEquals(22f, Graphite.CORPS.interligne);
        assertEquals(16f, Graphite.SOUS_TITRE.taille);
        assertEquals(600, Graphite.SOUS_TITRE.graisse);
        assertEquals(17f, Graphite.TITRE.taille);
        assertEquals(600, Graphite.TITRE.graisse);
        assertEquals(28f, Graphite.TITRE_ECRAN.taille);
        assertEquals(34f, Graphite.TITRE_ECRAN.interligne);
        assertEquals(700, Graphite.TITRE_ECRAN.graisse);
        assertEquals(13.5f, Graphite.CODE.taille);
        assertEquals(22f, Graphite.CODE.interligne);
        assertTrue(Graphite.CODE.mono);
        assertEquals(12.5f, Graphite.CODE_COMPACT.taille);
        assertEquals(20f, Graphite.CODE_COMPACT.interligne);
        assertTrue(Graphite.CODE_COMPACT.mono);
    }
}
