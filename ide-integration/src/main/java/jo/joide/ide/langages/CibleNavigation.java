package jo.joide.ide.langages;

/**
 * Cible de navigation (déclaration), vue JoIDE.
 *
 * @param genre    SOURCE_COURANTE, SOURCE_PROJET ou BINAIRE_DECOMPILE
 * @param uri      URI du fichier cible (ou « jdt://… » pour une cible
 *                 binaire décompilée)
 * @param nomSimple nom simple du symbole cible, {@code null} si le
 *                 moteur ne le fournit pas
 * @param debut    décalage de début de la cible dans son fichier
 * @param fin      décalage de fin (exclusif) — plage vide pour une
 *                 cible binaire
 */
public record CibleNavigation(String genre, String uri, String nomSimple,
        int debut, int fin) {
}
