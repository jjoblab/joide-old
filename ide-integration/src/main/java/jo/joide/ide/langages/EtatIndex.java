package jo.joide.ide.langages;

/**
 * État de l'index disque CIDX réutilisé par les moteurs — les phases
 * sont honnêtes (jamais un « prêt » menteur : un index PARTIEL liste
 * ses entrées écartées, un index ERREUR vit en repli par sondage).
 *
 * @param phase   INITIAL (jamais synchronisé), CONSTRUCTION, PRET,
 *                PARTIEL ou ERREUR
 * @param total   nombre d'entrées indexables du classpath
 * @param faits   entrées déjà construites ou reprises valides
 * @param avertissements avertissements du dernier passage
 * @param erreurs erreurs du dernier passage
 */
public record EtatIndex(String phase, int total, int faits,
        java.util.List<String> avertissements,
        java.util.List<String> erreurs) {

    /** État d'un index jamais synchronisé. */
    static EtatIndex initial() {
        return new EtatIndex("INITIAL", 0, 0, java.util.List.of(),
                java.util.List.of());
    }
}
