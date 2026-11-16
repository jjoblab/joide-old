package jo.joide.app.android.ui.choix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jo.joide.app.android.ui.Graphite;

import org.junit.jupiter.api.Test;

/**
 * Choix de la puce : repos, sélection, désactivé — la sélection
 * teinte l'accent à 16 % (bord à 45 %) et passe le texte en accent
 * demi-gras ; le désactivé abaisse l'opacité sans changer la teinte.
 */
class ChoixPuceTest {

    private static final Graphite.Palette S = Graphite.sombre();

    @Test
    void repos() {
        ChoixPuce.Apparence apparence = ChoixPuce.apparence(S, false,
                false);
        assertEquals(S.surfaceHaute, apparence.fond, "fond surfaceHaute");
        assertEquals(S.bordFin, apparence.bord, "bord fin");
        assertEquals(S.texteSecondaire, apparence.texte,
                "texte secondaire");
        assertFalse(apparence.gras, "non gras au repos");
        assertEquals(1f, apparence.opacite, "opacite 1");
    }

    @Test
    void selection() {
        ChoixPuce.Apparence apparence = ChoixPuce.apparence(S, true,
                false);
        assertEquals(Graphite.melange(S.accent, 0.16f, S.surface),
                apparence.fond, "selection : accent a 16 % de la surface");
        assertEquals(Graphite.melange(S.accent, 0.45f, S.surface),
                apparence.bord, "selection : bord accent a 45 %");
        assertEquals(S.accent, apparence.texte, "selection : texte accent");
        assertTrue(apparence.gras, "selection : demi-gras");
        assertEquals(1f, apparence.opacite, "selection active : opacite 1");
    }

    @Test
    void desactivee() {
        ChoixPuce.Apparence repos = ChoixPuce.apparence(S, false, true);
        assertEquals(0.4f, repos.opacite, "desactivee : opacite 40 %");
        assertEquals(S.surfaceHaute, repos.fond,
                "desactivee : meme teinte, jamais estomppee en couleur");
        ChoixPuce.Apparence pointee = ChoixPuce.apparence(S, true, true);
        assertEquals(0.4f, pointee.opacite,
                "selection desactivee : opacite 40 %");
        assertEquals(Graphite.melange(S.accent, 0.16f, S.surface),
                pointee.fond, "la teinte de selection est conservee");
    }
}
