package jo.joide.app.android.atelier;

/**
 * Lot A8 (ADR-085 décision 10) — LA DÉCISION PURE de configuration de
 * l'atelier : donnée la géométrie AVANT et APRÈS un changement, elle
 * répond ce que l'écran doit faire — et ce qu'il ne doit JAMAIS
 * faire. Aucune vue, aucun Bundle, aucun Manifest ici : la logique
 * seule, testée en JVM (critère §6.10 du chantier : « un changement
 * de géométrie n'appelle pas {@code fermerIde()} et ferme les
 * popups »).
 *
 * <p>La géométrie (orientation, {@code screenSize},
 * {@code smallestScreenSize}, {@code screenLayout} — les seuls
 * changements déclarés au manifest de l'atelier) se résume à la
 * CLASSE de disposition : grand écran ({@link EtatAtelier#SEUIL_GRAND_ECRAN_DP},
 * parité avec {@code layout-w720dp}) ou compact. La décision :</p>
 *
 * <ul>
 * <li><b>ré-infler la racine</b> seulement si la classe a changé —
 * le nouveau layout (compact ↔ {@code layout-w720dp}) porte une
 * autre composition ; même classe, même layout : ne pas ré-infler
 * est l'ÉCONOMIE retenue (un layout neuf ne dit rien de plus, le
 * repose coûterait un câblage entier pour rien) ;</li>
 * <li><b>fermer les popups</b> à TOUT changement de configuration
 * (décision 12 : un popup n'est jamais restauré, jamais une fuite au
 * détachement) ;</li>
 * <li><b>ne JAMAIS fermer l'IDE</b> ({@code fermerIde()}) sur la
 * géométrie : la session — scène celeris, moteurs LSP, aperçu,
 * écritures fusionnées — survit, le puits se détache avant la
 * ré-inflation et se rattache après, jamais reconstruit.</li>
 * </ul>
 *
 * <p>Les recréations RESTANTES (thème, langue, taille de police —
 * tout ce que le manifest ne déclare PAS) ne passent pas ici : elles
 * recréent l'activité, l'état d'UI minimal survit par
 * {@code onSaveInstanceState} (export/import de l'{@link EtatAtelier}).</p>
 */
public final class DecisionConfiguration {

    /**
     * La réaction de l'atelier à un changement de configuration :
     * ré-infler la racine (la composition change), fermer les popups
     * (décision 12), fermer l'IDE (JAMAIS sur la géométrie — décision
     * 10 : le champ existe pour que le test JVM l'asserte faux).
     */
    public record Reaction(boolean reinflerRacine, boolean fermerPopups,
                           boolean fermerIde) {
    }

    private DecisionConfiguration() {
    }

    /**
     * La décision sur un changement de GÉOMÉTRIE.
     *
     * @param grandEcranAvant la classe de disposition avant le
     *                        changement (celle de l'état courant)
     * @param grandEcranApres la classe de disposition après (lue de
     *                        la configuration nouvelle)
     * @return la réaction : ré-inflation si la classe a changé, popups
     *         fermés toujours, IDE jamais fermé
     */
    public static Reaction surGeometrie(boolean grandEcranAvant,
                                        boolean grandEcranApres) {
        // même classe → même layout : l'économie documentée ; les
        // popups se ferment quand même (décision 12 : TOUT changement
        // de configuration les ferme — la carte vivrait sur une
        // géométrie dépassée)
        return new Reaction(grandEcranAvant != grandEcranApres,
                true, false);
    }
}
