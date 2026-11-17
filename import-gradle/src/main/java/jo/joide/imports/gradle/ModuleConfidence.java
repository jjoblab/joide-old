package jo.joide.imports.gradle;

/**
 * Niveau de confiance d'un module importé : ce que l'import a compris
 * sans perte.
 *
 * <p>Les seuils sont fixes et documentés (ADR-040) : une confiance
 * haute signifie une lecture sans aucun avertissement ; moyenne, des
 * avertissements mais aucune erreur ; basse, au moins une erreur ou
 * plus de cinq avertissements.</p>
 */
public enum ModuleConfidence {

    /** Aucun problème signalé sur ce module. */
    HIGH,

    /** Des avertissements, aucune erreur, cinq au plus. */
    MEDIUM,

    /** Au moins une erreur, ou plus de cinq avertissements. */
    LOW;

    /** Seuil d'avertissements au-delà duquel la confiance est basse. */
    public static final int WARN_CEILING = 5;

    /**
     * @param errors nombre de problèmes d'erreur
     * @param warns  nombre de problèmes d'avertissement
     * @return le niveau correspondant
     */
    public static ModuleConfidence of(int errors, int warns) {
        if (errors > 0 || warns > WARN_CEILING) {
            return LOW;
        }
        return warns > 0 ? MEDIUM : HIGH;
    }
}
