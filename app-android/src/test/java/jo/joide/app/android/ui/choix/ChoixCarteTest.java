package jo.joide.app.android.ui.choix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import jo.joide.app.android.ui.Graphite;

import org.junit.jupiter.api.Test;

/**
 * Choix de la carte : tuiles tonales par type de projet (le rôle à
 * 12 % sur la surface), glyphe au rôle plein, liseré d'élévation en
 * sombre seul (5 % de blanc sur la surface).
 */
class ChoixCarteTest {

    private static final Graphite.Palette S = Graphite.sombre();
    private static final Graphite.Palette C = Graphite.clair();

    @Test
    void tuilesTonales() {
        assertEquals(Graphite.tuileTonale(S.reussite, S.surface),
                ChoixCarte.tuile(S, ChoixCarte.TypeTuile.ANDROID),
                "android : reussite a 12 %");
        assertEquals(Graphite.tuileTonale(S.accent, S.surface),
                ChoixCarte.tuile(S, ChoixCarte.TypeTuile.CONSOLE),
                "console : accent a 12 %");
        assertEquals(Graphite.tuileTonale(S.violet, S.surface),
                ChoixCarte.tuile(S, ChoixCarte.TypeTuile.BIBLIOTHEQUE),
                "bibliotheque : violet a 12 %");
        assertEquals(Graphite.tuileTonale(S.texteSecondaire, S.surface),
                ChoixCarte.tuile(S, ChoixCarte.TypeTuile.VIDE),
                "vide : texte secondaire a 12 %");
        assertEquals(0xFF1A302C,
                ChoixCarte.tuile(S, ChoixCarte.TypeTuile.ANDROID),
                "valeur concrete de la tuile android sombre");
    }

    @Test
    void glyphesAuRolePlein() {
        assertEquals(S.reussite,
                ChoixCarte.icone(S, ChoixCarte.TypeTuile.ANDROID));
        assertEquals(S.accent,
                ChoixCarte.icone(S, ChoixCarte.TypeTuile.CONSOLE));
        assertEquals(S.violet,
                ChoixCarte.icone(S, ChoixCarte.TypeTuile.BIBLIOTHEQUE));
        assertEquals(S.texteSecondaire,
                ChoixCarte.icone(S, ChoixCarte.TypeTuile.VIDE));
    }

    @Test
    void lisereSombreSeulement() {
        assertEquals(Graphite.melange(0xFFFFFFFF, 0.05f, S.surface),
                ChoixCarte.lisere(S),
                "sombre : 5 % de blanc sur la surface");
        assertEquals(0, ChoixCarte.lisere(C),
                "clair : transparent, la distinction passe par les bords");
        assertNotEquals(0, ChoixCarte.lisere(S), "le lisere se voit en sombre");
    }
}
