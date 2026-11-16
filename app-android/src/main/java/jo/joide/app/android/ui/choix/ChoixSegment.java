package jo.joide.app.android.ui.choix;

import jo.joide.app.android.ui.Graphite;

/**
 * Choix purs du segment (lot I, maquette création-projet.html) : un
 * contrôle à options exclusives — fond surface haute, rayon 12, les
 * cellules à rayon 9 ; la cellule active porte l'accent à 18 % sur
 * son fond, le texte accent en demi-gras. La vue
 * {@code jo.joide.app.android.ui.Segment} lit le résultat.
 */
public final class ChoixSegment {

    private ChoixSegment() {
    }

    /** L'apparence d'une cellule du segment. */
    public record Apparence(int fond, int texte, boolean gras) {
    }

    /** L'apparence de la cellule d'index donné. */
    public static Apparence apparence(Graphite.Palette palette,
                                      boolean selectionne) {
        if (selectionne) {
            return new Apparence(
                    Graphite.melange(palette.accent, 0.18f,
                            palette.surfaceHaute),
                    palette.accent, true);
        }
        return new Apparence(0, palette.texteSecondaire, false);
    }
}
