package jo.joide.app.android.ui.choix;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jo.joide.app.android.ui.Graphite;

import org.junit.jupiter.api.Test;

/**
 * Choix du mouvement : latéral sur téléphone, fondu sur grand écran
 * (la distinction « ScreenTransition », ADR-É2), 240 ms entre
 * écrans, 180 ms pour les composants.
 */
class ChoixMouvementTest {

    @Test
    void ecranLateralSurTelephone() {
        ChoixMouvement.TransitionEcran transition =
                ChoixMouvement.ecran(false);
        assertEquals(ChoixMouvement.Transition.LATERALE, transition.type,
                "telephone : entree laterale directionnelle");
        assertEquals(240, transition.dureeMs, "240 ms entre ecrans");
    }

    @Test
    void ecranFonduSurGrandEcran() {
        ChoixMouvement.TransitionEcran transition =
                ChoixMouvement.ecran(true);
        assertEquals(ChoixMouvement.Transition.FONDU, transition.type,
                "grand ecran : fondu discret");
        assertEquals(240, transition.dureeMs, "240 ms entre ecrans");
    }

    @Test
    void composant() {
        assertEquals(180, ChoixMouvement.composant(),
                "180 ms standard pour tiroir et dock");
        assertEquals(Graphite.MOUVEMENT_STANDARD, ChoixMouvement.composant(),
                "le composant suit le jeton du systeme");
    }
}
