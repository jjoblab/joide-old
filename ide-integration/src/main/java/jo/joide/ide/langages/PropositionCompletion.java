package jo.joide.ide.langages;

/**
 * Proposition de complétion, vue JoIDE.
 *
 * @param libelle       étiquette affichée (jamais null)
 * @param nature        nature de la proposition (nom d'énumération du
 *                      moteur, ex. « CLASSE », « METHODE », « BALISE »)
 * @param texteInsertion texte à insérer, {@code null} = le libellé
 * @param detail        détail (signature, type…), {@code null} si aucun
 * @param importAuto    nom pleinement qualifié à importer avec la
 *                      proposition, {@code null} si aucun
 */
public record PropositionCompletion(String libelle, String nature,
        String texteInsertion, String detail, String importAuto) {
}
