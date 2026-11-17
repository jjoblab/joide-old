package jo.joide.app.android.ui.choix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import jo.joide.app.android.ui.Graphite;

import org.junit.jupiter.api.Test;

/**
 * Choix du bouton : variantes, états. Le désactivé abaisse
 * l'opacité sans changer les couleurs ; l'appuyé éclaircit en
 * sombre, assombrit en clair ; la variante texte reçoit une teinte
 * accent à 12 % quand elle est appuyée.
 */
class ChoixBoutonTest {

    private static final Graphite.Palette S = Graphite.sombre();
    private static final Graphite.Palette C = Graphite.clair();

    @Test
    void variantesAuRepos() {
        ChoixBouton.Apparence primaire = ChoixBouton.apparence(S,
                ChoixBouton.Variante.PRIMAIRE, false, false);
        assertEquals(S.accent, primaire.fond, "primaire : fond accent");
        assertEquals(S.texteSurAccent, primaire.texte,
                "primaire : texte sur accent");
        assertEquals(1f, primaire.opacite, "primaire actif : opacite 1");

        ChoixBouton.Apparence tonal = ChoixBouton.apparence(S,
                ChoixBouton.Variante.TONAL, false, false);
        assertEquals(S.surfaceHaute, tonal.fond, "tonal : fond surfaceHaute");
        assertEquals(S.texte, tonal.texte, "tonal : texte normal");

        ChoixBouton.Apparence texte = ChoixBouton.apparence(S,
                ChoixBouton.Variante.TEXTE, false, false);
        assertEquals(0, texte.fond, "texte : fond transparent");
        assertEquals(S.accent, texte.texte, "texte : libelle accent");
    }

    @Test
    void desactiveAbaisseLOpaciteSansToucherAuxCouleurs() {
        for (ChoixBouton.Variante variante : ChoixBouton.Variante.values()) {
            ChoixBouton.Apparence eteint = ChoixBouton.apparence(S,
                    variante, true, true);
            ChoixBouton.Apparence allume = ChoixBouton.apparence(S,
                    variante, false, false);
            assertEquals(0.4f, eteint.opacite,
                    variante + " desactive : opacite 40 %");
            assertEquals(allume.fond, eteint.fond,
                    variante + " desactive : memes couleurs, jamais estompees");
            assertEquals(allume.texte, eteint.texte,
                    variante + " desactive : meme texte");
        }
    }

    @Test
    void appuyeEclaircitEnSombreAssombritEnClair() {
        ChoixBouton.Apparence sombre = ChoixBouton.apparence(S,
                ChoixBouton.Variante.PRIMAIRE, false, true);
        assertEquals(Graphite.melange(0xFFFFFFFF, 0.08f, S.accent),
                sombre.fond, "sombre appuye : 8 % de blanc sur l'accent");
        assertNotEquals(S.accent, sombre.fond, "l'appui se voit");

        ChoixBouton.Apparence clair = ChoixBouton.apparence(C,
                ChoixBouton.Variante.TONAL, false, true);
        assertEquals(Graphite.melange(0xFF000000, 0.06f, C.surfaceHaute),
                clair.fond, "clair appuye : 6 % de noir sur surfaceHaute");
        assertNotEquals(C.surfaceHaute, clair.fond, "l'appui se voit");
    }

    @Test
    void appuyeTeinteLaVarianteTexte() {
        ChoixBouton.Apparence teinte = ChoixBouton.apparence(S,
                ChoixBouton.Variante.TEXTE, false, true);
        assertEquals(Graphite.melange(S.accent, 0.12f, S.fond),
                teinte.fond, "texte appuye : accent a 12 % du fond");
        assertEquals(S.accent, teinte.texte, "texte appuye : libelle accent");
    }
}
