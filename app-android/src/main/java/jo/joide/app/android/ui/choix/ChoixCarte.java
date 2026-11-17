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

    /**
     * Le bord de la carte tonale (reprise de l'accueil, modèle choisi
     * de l'assistant — maquette accueil-projets.html) : accent à
     * 35 % sur la surface.
     */
    public static int tonaleBord(Graphite.Palette palette) {
        return Graphite.melange(palette.accent, 0.35f, palette.surface);
    }

    /** Le haut du dégradé de la carte tonale : accent à 8 % sur la surface. */
    public static int tonaleHaut(Graphite.Palette palette) {
        return Graphite.melange(palette.accent, 0.08f, palette.surface);
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
