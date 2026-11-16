package jo.joide.app.android.ui.choix;

import jo.joide.app.android.ui.Graphite;

/**
 * Choix purs de la carte : tuile tonale par type de projet, liseré
 * d'élévation en sombre. La vue {@code Carte} lit le résultat.
 *
 * <p>Tuiles tonales (ADR-É2) : Android → réussite, console JVM →
 * accent, bibliothèque → violet, vide → texte secondaire — toujours
 * le rôle à 12 % sur la surface. Liseré (É2 : « élévation en
 * sombre ») : 5 % de blanc sur la surface, transparent en clair où
 * la distinction passe par les bords (ADR-048).</p>
 */
public final class ChoixCarte {

    private ChoixCarte() {
    }

    /** Le type de projet porté par la tuile (ADR-É2). */
    public enum TypeTuile {
        ANDROID, CONSOLE, BIBLIOTHEQUE, VIDE
    }

    /** Le fond de la tuile : le rôle du type à 12 % sur la surface. */
    public static int tuile(Graphite.Palette palette, TypeTuile type) {
        return Graphite.tuileTonale(role(palette, type), palette.surface);
    }

    /** La couleur du glyphe : le rôle du type, plein. */
    public static int icone(Graphite.Palette palette, TypeTuile type) {
        return role(palette, type);
    }

    /** Le liseré supérieur : 5 % de blanc en sombre, transparent sinon. */
    public static int lisere(Graphite.Palette palette) {
        if (!Graphite.estSombre(palette)) {
            return 0;
        }
        return Graphite.melange(0xFFFFFFFF, 0.05f, palette.surface);
    }

    private static int role(Graphite.Palette palette, TypeTuile type) {
        return switch (type) {
            case ANDROID -> palette.reussite;
            case CONSOLE -> palette.accent;
            case BIBLIOTHEQUE -> palette.violet;
            case VIDE -> palette.texteSecondaire;
        };
    }
}
