package jo.joide.app.android.atelier;

import java.util.Locale;

/**
 * Lot L3 de l'atelier v2 (ADR-083) — l'état PUR de la feuille basse
 * (§3.6 du prompt v2). Toute la géométrie et les décisions :
 * les trois détentes (replié 72 dp · mi-hauteur 55 % de la hauteur
 * utile · plein 90 %), le cycle au toucher de poignée, l'ancrage au
 * détente la plus proche après un glissement, le fling (> 1 200 dp/s
 * vers la détente suivante dans le sens du geste), la section active
 * avec bouclage horizontal. Aucune vue : la vue lue est
 * {@code FeuilleBasse}, les tests JVM sont ici.
 */
public final class EtatFeuille {

    /** Les sections de la feuille, dans l'ordre du rail. */
    public enum Section {
        /** Le build (ConsoleBuild / EtatConsole). */
        CONSOLE,
        /** Les diagnostics du moteur de langage (LSP). */
        PROBLEMES,
        /** Le journal de l'IDE lui-même. */
        JOURNAL,
        /** La sortie de l'application de l'utilisateur. */
        LOGCAT
    }

    /** La détente repliée : poignée 20 + en-tête 52 (§3.6). */
    public static final int HAUTEUR_REPLIE_DP = 72;
    /** La mi-hauteur : 55 % de la hauteur utile. */
    public static final double FRACTION_MI = 0.55;
    /** Le plein : 90 % de la hauteur utile. */
    public static final double FRACTION_PLEINE = 0.90;
    /** Le seuil de fling (dp/s) qui saute à la détente suivante. */
    public static final int FLING_DP_S = 1200;

    /** La détente actuelle de la feuille. */
    public enum Detente {
        /** Replié : 72 dp. */
        REPLIE,
        /** Mi-hauteur : 55 %. */
        MI,
        /** Plein : 90 %. */
        PLEINE
    }

    /** La section affichée. */
    private Section section = Section.CONSOLE;
    /** La détente actuelle. */
    private Detente detente = Detente.REPLIE;

    /** @return la section active (une seule à la fois, §3.6). */
    public Section section() {
        return section;
    }

    /** @return la détente actuelle. */
    public Detente detente() {
        return detente;
    }

    /** La section passe à la suivante, AVEC BOUCLAGE (glissement
     *  horizontal de l'en-tête, §3.6). */
    public Section sectionSuivante() {
        section = Section.values()
                [(section.ordinal() + 1) % Section.values().length];
        return section;
    }

    /** La section passe à la précédente, AVEC BOUCLAGE. */
    public Section sectionPrecedente() {
        Section[] valeurs = Section.values();
        section = valeurs[(section.ordinal() + valeurs.length - 1)
                % valeurs.length];
        return section;
    }

    /** Une section précise est demandée (rail). */
    public void poserSection(Section demandee) {
        section = demandee == null ? Section.CONSOLE : demandee;
    }

    /** Le cycle au TOUCHER de poignée : replié → mi → plein →
     *  replié (§3.6). */
    public Detente cycle() {
        detente = switch (detente) {
            case REPLIE -> Detente.MI;
            case MI -> Detente.PLEINE;
            case PLEINE -> Detente.REPLIE;
        };
        return detente;
    }

    /** Une détente précise est demandée (l'écran pilote depuis son
     *  état — la console dépliée). */
    public void poserDetente(Detente demandee) {
        detente = demandee == null ? Detente.REPLIE : demandee;
    }

    /** La hauteur en dp d'une détente pour la hauteur utile
     *  demandée (la hauteur de l'écran moins ce qui ne glisse pas). */
    public static int hauteurDe(Detente detente, int hauteurUtileDp) {
        return switch (detente) {
            case REPLIE -> HAUTEUR_REPLIE_DP;
            case MI -> (int) Math.round(hauteurUtileDp * FRACTION_MI);
            case PLEINE -> (int) Math.round(
                    hauteurUtileDp * FRACTION_PLEINE);
        };
    }

    /** La hauteur actuelle (dp) pour la hauteur utile demandée. */
    public int hauteurDp(int hauteurUtileDp) {
        return hauteurDe(detente, hauteurUtileDp);
    }

    /** L'ANCRAGE : après un glissement relâché à la hauteur demandée,
     *  la feuille s'ancre à la DÉTENTE LA PLUS PROCHE — bornée aux
     *  détes (sous le replié, elle remonte au replié ; au-dessus du
     *  plein, elle redescend au plein). */
    public Detente ancrer(int hauteurDp, int hauteurUtileDp) {
        Detente prochaine = null;
        int distance = Integer.MAX_VALUE;
        for (Detente candidate : Detente.values()) {
            int ecart = Math.abs(
                    hauteurDe(candidate, hauteurUtileDp) - hauteurDp);
            if (ecart < distance) {
                distance = ecart;
                prochaine = candidate;
            }
        }
        detente = prochaine;
        return detente;
    }

    /** Le FLING : au-delà de 1 200 dp/s, la détente SUIVANTE dans le
     *  sens du geste (positif = vers le haut = agrandir) ; en dessous,
     *  l'ancrage classique s'applique (retour null = à ancrer). */
    public Detente fling(int vitesseDpS) {
        if (Math.abs(vitesseDpS) < FLING_DP_S) {
            return null;
        }
        detente = switch (detente) {
            case REPLIE -> vitesseDpS > 0 ? Detente.MI : Detente.REPLIE;
            case MI -> vitesseDpS > 0 ? Detente.PLEINE : Detente.REPLIE;
            case PLEINE -> vitesseDpS > 0 ? Detente.PLEINE
                    : Detente.MI;
        };
        return detente;
    }

    /** @return vrai si la feuille est dépliée au-delà de la barre
     *  (le FAB se cache, §3.7). */
    public boolean cacheLeFab() {
        return detente != Detente.REPLIE;
    }

    @Override
    public String toString() {
        return String.format(Locale.FRENCH,
                "Feuille{%s, %s}", section, detente);
    }
}
