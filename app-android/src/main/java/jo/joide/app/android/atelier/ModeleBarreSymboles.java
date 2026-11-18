package jo.joide.app.android.atelier;

import java.util.List;

/**
 * Lot L2 de l'atelier v2 (ADR-083) — le modèle PUR de la barre de
 * symboles (§3.5 du prompt v2). Touches : Tab (poids 1,6, accent),
 * puis { } ( ) ; " = et ⋯. Chaque touche porte sa variante de
 * « flick haut » — ( → ), { → }, [ ] etc. — et l'insertion
 * intelligente ferme les paires. Aucune vue : la barre gonfle un
 * {@code item_touche} par touche de cette liste.
 */
public final class ModeleBarreSymboles {

    /** Une touche de la barre. */
    public static final class Touche {
        /** Le glyphe affiché. */
        public final String glyphe;
        /** La première touche (Tab) : poids 1,6, accent, 12 sp. */
        public final boolean tab;
        /** Le texte inséré par un toucher (insertion intelligente). */
        public final String insertion;
        /** Le texte inséré par un flick haut (la variante). */
        public final String variante;
        /** Le rang d'actions « ⋯ » (commenter, dupliquer, suivant). */
        public final boolean actions;

        private Touche(String glyphe, boolean tab, String insertion,
                       String variante, boolean actions) {
            this.glyphe = glyphe;
            this.tab = tab;
            this.insertion = insertion;
            this.variante = variante;
            this.actions = actions;
        }
    }

    /** La tabulation. */
    public static final Touche TAB
            = new Touche("Tab", true, "\t", null, false);
    /** Les paires et séparateurs (insertion intelligente : la paire
     *  entière s'insère, le curseur se pose au centre — la vue
     *  l'applique via InputConnection). */
    public static final Touche ACCOLADE_OUVRANTE
            = new Touche("{", false, "{}", "}", false);
    public static final Touche ACCOLADE_FERMANTE
            = new Touche("}", false, "}", null, false);
    public static final Touche PARENTHESE_OUVRANTE
            = new Touche("(", false, "()", ")", false);
    public static final Touche PARENTHESE_FERMANTE
            = new Touche(")", false, ")", null, false);
    public static final Touche POINT_VIRGULE
            = new Touche(";", false, ";", ":", false);
    public static final Touche GUILLEMET
            = new Touche("\"", false, "\"\"", "\"", false);
    public static final Touche EGAL
            = new Touche("=", false, "=", null, false);
    /** La rangée d'actions : commenter, déplacer/dupliquer, suivant. */
    public static final Touche ACTIONS
            = new Touche("⋯", false, null, null, true);

    /** Les touches, dans l'ordre de la maquette. */
    public static List<Touche> touches() {
        return List.of(TAB, ACCOLADE_OUVRANTE, ACCOLADE_FERMANTE,
                PARENTHESE_OUVRANTE, PARENTHESE_FERMANTE,
                POINT_VIRGULE, GUILLEMET, EGAL, ACTIONS);
    }

    /** Le texte à insérer pour un toucher : la paire pour les
     *  ouvrantes (insertion intelligente), le caractère sinon. */
    public static String insertionDe(Touche touche) {
        return touche.insertion;
    }

    /** Le texte à insérer pour un flick haut : la VARIANTE (la
     *  fermante pour une ouvrante) ; une touche sans variante ne
     *  réagit pas au flick (null). */
    public static String varianteDe(Touche touche) {
        return touche.variante;
    }
}
