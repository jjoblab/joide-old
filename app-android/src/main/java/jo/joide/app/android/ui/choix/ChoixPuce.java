package jo.joide.app.android.ui.choix;

import jo.joide.app.android.ui.Graphite;

/**
 * Choix purs de la puce : états → fond, bord, couleur de texte,
 * graisse, opacité. Deux aspects (ADR-É2) : la puce simple et la
 * puce de catégorie sélectionnable ; la sélection reprend la teinte
 * accent à 16 % avec un bord accent à 45 % (maquette index.html).
 */
public final class ChoixPuce {

    private ChoixPuce() {
    }

    /** Une apparence de puce. */
    public static final class Apparence {
        public final int fond;
        public final int bord;
        public final int texte;
        public final boolean gras;
        public final float opacite;

        Apparence(int fond, int bord, int texte, boolean gras,
                  float opacite) {
            this.fond = fond;
            this.bord = bord;
            this.texte = texte;
            this.gras = gras;
            this.opacite = opacite;
        }
    }

    /** L'apparence d'une puce selon ses états. */
    public static Apparence apparence(Graphite.Palette palette,
                                      boolean selectionnee,
                                      boolean desactivee) {
        int fond;
        int bord;
        int texte;
        boolean gras;
        if (selectionnee) {
            fond = Graphite.melange(palette.accent, 0.16f,
                    palette.surface);
            bord = Graphite.melange(palette.accent, 0.45f,
                    palette.surface);
            texte = palette.accent;
            gras = true;
        } else {
            fond = palette.surfaceHaute;
            bord = palette.bordFin;
            texte = palette.texteSecondaire;
            gras = false;
        }
        float opacite = desactivee ? 0.4f : 1f;
        return new Apparence(fond, bord, texte, gras, opacite);
    }
}
