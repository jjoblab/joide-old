package jo.joide.ide.apercu;

import java.util.List;
import java.util.Objects;

/**
 * Configuration d'un aperçu, vue JoIDE — les ressources et le thème
 * du projet prévisualisé, lus du modèle de langage ou posés par
 * l'hôte.
 *
 * <p>Les contenus XML (thèmes, styles, attributs) sont les CONTENUS
 * des fichiers, pas leurs chemins : le résolveur de la brique les
 * enregistre par contenu. Le nom du thème n'est JAMAIS deviné — null
 * retombe au thème de l'hôte, jamais un échec (contrat de la brique).</p>
 *
 * <p>Lot L (ADR-É7/ADR-053) : la densité de rendu devient un réglage
 * réel de la configuration — les dimensions cibles sont posées par
 * l'hôte en PIXELS (dp × densité choisie), le convertisseur de la
 * brique traduit les dp du XML avec la densité choisie. La valeur
 * {@link #DENSITE_CONTEXTE} (0) conserve le comportement du lot E :
 * la densité du contexte hôte.</p>
 *
 * @param dossierRes   chemin absolu du dossier {@code res/} principal
 *                     du module prévisualisé, ou null (ressources
 *                     natives de l'hôte)
 * @param nomTheme     nom du thème du projet, ou null (thème hôte)
 * @param themesJour   contenu de {@code values/themes.xml}, ou null
 * @param themesNuit   contenu de {@code values-night/themes.xml}, ou null
 * @param styles       contenu de {@code values/styles.xml}, ou null
 * @param attrs        contenu de {@code values/attrs.xml}, ou null
 * @param largeurCible largeur cible en pixels (1080 par défaut)
 * @param hauteurCible hauteur cible en pixels (1920 par défaut)
 * @param densiteRendu densité du convertisseur (1, 2, 3…), ou
 *                     {@link #DENSITE_CONTEXTE} pour celle de l'hôte
 * @param modeNuit     vrai pour les qualificateurs {@code -night}
 * @param avertissements écarts rencontrés à la constitution (fichier
 *                     illisible…), jamais une omission muette
 */
public record ConfigurationApercu(String dossierRes, String nomTheme,
        String themesJour, String themesNuit, String styles,
        String attrs, int largeurCible, int hauteurCible,
        float densiteRendu, boolean modeNuit,
        List<String> avertissements) {

    /** Dimensions cibles par défaut de la brique (1080×1920). */
    public static final int LARGEUR_DEFAUT = 1080;

    /** Hauteur cible par défaut. */
    public static final int HAUTEUR_DEFAUT = 1920;

    /** Densité inconnue : le convertisseur prend celle du contexte. */
    public static final float DENSITE_CONTEXTE = 0f;

    /** La configuration vide : ressources natives, thème hôte. */
    public static ConfigurationApercu vide() {
        return new ConfigurationApercu(null, null, null, null, null,
                null, LARGEUR_DEFAUT, HAUTEUR_DEFAUT, DENSITE_CONTEXTE,
                false, List.of());
    }

    /**
     * Construit une version de cette configuration avec un autre nom
     * de thème (l'hôte choisit le thème de l'aperçu).
     *
     * @param theme le nom du thème, ou null pour le thème hôte
     * @return la configuration avec le thème posé
     */
    public ConfigurationApercu avecTheme(String theme) {
        Objects.requireNonNull(avertissements);
        return new ConfigurationApercu(dossierRes, theme, themesJour,
                themesNuit, styles, attrs, largeurCible, hauteurCible,
                densiteRendu, modeNuit, avertissements);
    }

    /**
     * Construit une version avec le mode nuit posé.
     *
     * @param nuit vrai pour les qualificateurs {@code -night}
     * @return la configuration avec le mode posé
     */
    public ConfigurationApercu avecModeNuit(boolean nuit) {
        Objects.requireNonNull(avertissements);
        return new ConfigurationApercu(dossierRes, nomTheme, themesJour,
                themesNuit, styles, attrs, largeurCible, hauteurCible,
                densiteRendu, nuit, avertissements);
    }

    /**
     * Construit une version avec la densité de rendu posée (lot L).
     *
     * @param densite la densité du convertisseur (1, 2, 3…), ou
     *                {@link #DENSITE_CONTEXTE}
     * @return la configuration avec la densité posée
     */
    public ConfigurationApercu avecDensite(float densite) {
        Objects.requireNonNull(avertissements);
        return new ConfigurationApercu(dossierRes, nomTheme, themesJour,
                themesNuit, styles, attrs, largeurCible, hauteurCible,
                densite < 0f ? DENSITE_CONTEXTE : densite, modeNuit,
                avertissements);
    }

    /**
     * Construit une version avec les dimensions cibles posées (lot L)
     * — en pixels : l'hôte convertit ses dp ({@code dp × densité}).
     *
     * @param largeur  la largeur cible en pixels
     * @param hauteur  la hauteur cible en pixels
     * @return la configuration avec les dimensions posées
     */
    public ConfigurationApercu avecDimensions(int largeur, int hauteur) {
        Objects.requireNonNull(avertissements);
        return new ConfigurationApercu(dossierRes, nomTheme, themesJour,
                themesNuit, styles, attrs, largeur, hauteur,
                densiteRendu, modeNuit, avertissements);
    }
}
