package jo.joide.app.android.ui.choix;

import jo.joide.app.android.ui.Graphite;

/**
 * Choix purs du bouton : Variante × états → apparence. La vue
 * {@code Bouton} lit le résultat de cette classe et ne décide rien
 * seule (doctrine « l'UI reste mince ») ; les tests JVM la
 * vérifient sans SDK.
 *
 * <p>Politique (ADR-É2/048) : trois variantes ; l'état désactivé
 * abaisse l'opacité à 40 % SANS changer les couleurs (jamais un
 * texte estompé porteur d'information) ; l'état appuyé éclaircit le
 * fond en sombre (8 % de blanc), l'assombrit en clair (6 % de
 * noir) ; la variante texte reçoit une teinte accent à 12 % du fond
 * quand elle est appuyée.</p>
 */
public final class ChoixBouton {

    private ChoixBouton() {
    }

    /** Les trois variantes du kit (ADR-É2). */
    public enum Variante {
        PRIMAIRE, TONAL, TEXTE
    }

    /** Une apparence de bouton : fond, couleur de texte, opacité. */
    public static final class Apparence {
        public final int fond;
        public final int texte;
        public final float opacite;

        Apparence(int fond, int texte, float opacite) {
            this.fond = fond;
            this.texte = texte;
            this.opacite = opacite;
        }
    }

    /** L'apparence d'un bouton pour sa variante et ses états. */
    public static Apparence apparence(Graphite.Palette palette,
                                      Variante variante,
                                      boolean desactive,
                                      boolean appuye) {
        int fond;
        int texte;
        switch (variante) {
            case PRIMAIRE -> {
                fond = palette.accent;
                texte = palette.texteSurAccent;
            }
            case TEXTE -> {
                fond = 0;
                texte = palette.accent;
            }
            default -> {
                fond = palette.surfaceHaute;
                texte = palette.texte;
            }
        }
        if (!desactive && appuye) {
            if (variante == Variante.TEXTE) {
                fond = Graphite.melange(palette.accent, 0.12f,
                        palette.fond);
            } else if (Graphite.estSombre(palette)) {
                fond = Graphite.melange(0xFFFFFFFF, 0.08f, fond);
            } else {
                fond = Graphite.melange(0xFF000000, 0.06f, fond);
            }
        }
        float opacite = desactive ? 0.4f : 1f;
        return new Apparence(fond, texte, opacite);
    }
}
