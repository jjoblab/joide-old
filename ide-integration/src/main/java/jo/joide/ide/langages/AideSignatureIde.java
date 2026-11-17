package jo.joide.ide.langages;

import java.util.List;

/**
 * Aide aux paramètres au curseur, vue JoIDE.
 *
 * @param entrees         les signatures candidates (libellé + noms des
 *                        paramètres)
 * @param signatureActive index de la signature active (peut être négatif)
 * @param parametreActif  index du paramètre courant (peut être négatif)
 */
public record AideSignatureIde(List<Entree> entrees,
        int signatureActive, int parametreActif) {

    /** Une signature candidate. */
    public record Entree(String libelle, List<String> parametres) {
    }
}
