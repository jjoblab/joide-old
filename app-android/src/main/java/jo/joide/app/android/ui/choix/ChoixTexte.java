package jo.joide.app.android.ui.choix;

/**
 * Politique de troncature du kit (ADR-048) : une seule ligne, la
 * coupe se fait au dernier mot qui tient, puis l'ellipse « … ». La
 * vue {@code Bouton} (texte dessiné au canvas) s'en sert quand le
 * texte déborde ; les tests JVM vérifient la politique sans
 * mesurer de police — la mesure en pixels reste à la vue.
 */
public final class ChoixTexte {

    private ChoixTexte() {
    }

    private static final String ELLIPSE = "\u2026";

    /**
     * Coupe {@code texte} à un budget de caractères (hors ellipse),
     * au dernier mot qui tient ; sans espace dans la fenêtre, la
     * coupe est dure.
     *
     * @param texte  le texte à couper ; vide reste vide
     * @param budget le nombre maximal de caractères conservés
     *               (hors ellipse) ; 0 ou moins donne l'ellipse seule
     */
    public static String coupeParMot(String texte, int budget) {
        if (texte == null || texte.isEmpty()) {
            return "";
        }
        if (budget <= 0) {
            return ELLIPSE;
        }
        if (texte.length() <= budget) {
            return texte;
        }
        int coupe = 0;
        for (int i = 0; i < Math.min(budget, texte.length()); i++) {
            if (texte.charAt(i) == ' ') {
                coupe = i;
            }
        }
        if (coupe > 0) {
            return texte.substring(0, coupe).trim() + ELLIPSE;
        }
        return texte.substring(0, budget) + ELLIPSE;
    }
}
