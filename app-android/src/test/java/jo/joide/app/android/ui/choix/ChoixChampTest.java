package jo.joide.app.android.ui.choix;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jo.joide.app.android.ui.Graphite;

import org.junit.jupiter.api.Test;

/**
 * Choix du champ : repos, focus, désactivé — le focus passe le bord
 * à l'accent 2 dp et l'étiquette à l'accent ; le désactivé estompe
 * étiquette et valeur sans jamais changer le bord d'avis.
 */
class ChoixChampTest {

    private static final Graphite.Palette S = Graphite.sombre();

    @Test
    void repos() {
        ChoixChamp.Apparence apparence = ChoixChamp.apparence(S, false,
                false);
        assertEquals(S.bordFin, apparence.couleurBord, "bord fin au repos");
        assertEquals(1f, apparence.epaisseurBordDp, "1 dp au repos");
        assertEquals(S.texteSecondaire, apparence.couleurEtiquette,
                "etiquette secondaire au repos");
        assertEquals(S.texte, apparence.couleurTexte, "valeur normale");
    }

    @Test
    void focus() {
        ChoixChamp.Apparence apparence = ChoixChamp.apparence(S, true,
                false);
        assertEquals(S.accent, apparence.couleurBord, "bord accent au focus");
        assertEquals(2f, apparence.epaisseurBordDp, "2 dp au focus");
        assertEquals(S.accent, apparence.couleurEtiquette,
                "etiquette accent au focus");
        assertEquals(S.texte, apparence.couleurTexte, "valeur normale");
    }

    @Test
    void desactive() {
        ChoixChamp.Apparence apparence = ChoixChamp.apparence(S, true,
                true);
        assertEquals(S.bordFin, apparence.couleurBord,
                "le desactive garde le bord fin, jamais un avis");
        assertEquals(1f, apparence.epaisseurBordDp, "1 dp desactive");
        assertEquals(S.texteEstompe, apparence.couleurEtiquette,
                "etiquette estompee desactivee");
        assertEquals(S.texteEstompe, apparence.couleurTexte,
                "valeur estompee desactivee");
    }
}
