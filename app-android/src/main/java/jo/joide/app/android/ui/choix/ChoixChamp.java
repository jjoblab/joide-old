package jo.joide.app.android.ui.choix;

import jo.joide.app.android.ui.Graphite;

/**
 * Choix purs du champ (étiquette au-dessus, ADR-É2) : états →
 * couleurs de bord, d'étiquette et de texte. La vue {@code Champ}
 * applique le résultat ; les tests JVM le vérifient.
 *
 * <p>Politique (maquette création-projet.html, écart pixel
 * consigné ADR-048) : repos — bord fin 1 dp, étiquette secondaire ;
 * focus — bord accent 2 dp, étiquette accent ; désactivé — bord fin
 * 1 dp, étiquette et valeur estompées.</p>
 */
public final class ChoixChamp {

    private ChoixChamp() {
    }

    /** Une apparence de champ. */
    public static final class Apparence {
        public final int couleurBord;
        public final float epaisseurBordDp;
        public final int couleurEtiquette;
        public final int couleurTexte;

        Apparence(int couleurBord, float epaisseurBordDp,
                  int couleurEtiquette, int couleurTexte) {
            this.couleurBord = couleurBord;
            this.epaisseurBordDp = epaisseurBordDp;
            this.couleurEtiquette = couleurEtiquette;
            this.couleurTexte = couleurTexte;
        }
    }

    /** L'apparence d'un champ selon ses états. */
    public static Apparence apparence(Graphite.Palette palette,
                                      boolean focus,
                                      boolean desactive) {
        if (desactive) {
            return new Apparence(palette.bordFin, 1f,
                    palette.texteEstompe, palette.texteEstompe);
        }
        if (focus) {
            return new Apparence(palette.accent, 2f,
                    palette.accent, palette.texte);
        }
        return new Apparence(palette.bordFin, 1f,
                palette.texteSecondaire, palette.texte);
    }
}
