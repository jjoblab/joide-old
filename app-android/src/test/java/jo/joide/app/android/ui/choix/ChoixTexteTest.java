package jo.joide.app.android.ui.choix;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Politique de troncature : une ligne, coupe au dernier mot qui
 * tient, puis l'ellipse ; sans espace dans la fenêtre, coupe dure.
 */
class ChoixTexteTest {

    private static final String ELLIPSE = "\u2026";

    @Test
    void videEtNul() {
        assertEquals("", ChoixTexte.coupeParMot("", 10), "vide reste vide");
        assertEquals("", ChoixTexte.coupeParMot(null, 10),
                "nul devient vide");
    }

    @Test
    void budgetNulOuNegatif() {
        assertEquals(ELLIPSE, ChoixTexte.coupeParMot("Construire", 0),
                "budget 0 : ellipse seule");
        assertEquals(ELLIPSE, ChoixTexte.coupeParMot("Construire", -3),
                "budget negatif : ellipse seule");
    }

    @Test
    void courtPasseTelQuel() {
        assertEquals("Construire", ChoixTexte.coupeParMot("Construire", 30));
        assertEquals("a b", ChoixTexte.coupeParMot("a b", 3),
                "a la limite exacte, aucun caractere n'est coupe");
    }

    @Test
    void coupeAuDernierMotQuiTient() {
        assertEquals("Construire" + ELLIPSE,
                ChoixTexte.coupeParMot("Construire l'application", 12),
                "la coupe tombe apres le mot qui tient, jamais en plein mot");
        assertEquals("a" + ELLIPSE, ChoixTexte.coupeParMot("a b c", 3),
                "fenetre « a b » : le dernier mot entier est « a »");
    }

    @Test
    void coupeDureSansEspace() {
        assertEquals("mon-appl" + ELLIPSE,
                ChoixTexte.coupeParMot("mon-application-android", 8),
                "sans espace dans la fenetre, coupe dure");
    }

    @Test
    void budgetUnCaractere() {
        assertEquals("C" + ELLIPSE,
                ChoixTexte.coupeParMot("Construire", 1),
                "au moins un caractere conserve");
    }
}
