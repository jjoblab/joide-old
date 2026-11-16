package jo.joide.agent;

/**
 * Un événement du flux d'un fournisseur, traduit en neutre par
 * l'adaptateur — les deltas vivants de l'écriture et du
 * raisonnement, la fin de message avec ses parties COMPLÈTES et
 * l'usage de la requête. Les erreurs ne passent pas par là : elles
 * lèvent {@link ErreurLlm}.
 */
public final class EvenementFlux {

    /** Le genre d'événement. */
    public enum Genre {
        /** Un delta de texte affichable. */
        TEXTE_DELTA,
        /** Un delta de raisonnement. */
        RAISONNEMENT_DELTA,
        /** La fin du message : les parties complètes. */
        FIN_MESSAGE,
        /** L'usage de la requête (entrée, sortie, lectures de cache). */
        USAGE
    }

    /** Le genre. */
    public final Genre genre;
    /** Le texte du delta (TEXTE_DELTA, RAISONNEMENT_DELTA). */
    public final String texte;
    /** Les parties complètes (FIN_MESSAGE). */
    public final java.util.List<PartieContenu> parties;
    /** La raison d'arrêt du fournisseur (FIN_MESSAGE), ou vide. */
    public final String raisonArret;
    /** L'usage (USAGE). */
    public final UsageLlm usage;

    private EvenementFlux(Genre genre, String texte,
                          java.util.List<PartieContenu> parties,
                          String raisonArret, UsageLlm usage) {
        this.genre = genre;
        this.texte = texte;
        this.parties = parties;
        this.raisonArret = raisonArret;
        this.usage = usage;
    }

    /** @param texte le delta @return un delta de texte */
    public static EvenementFlux texte(String texte) {
        return new EvenementFlux(Genre.TEXTE_DELTA, texte,
                java.util.List.of(), "", null);
    }

    /** @param texte le delta @return un delta de raisonnement */
    public static EvenementFlux raisonnement(String texte) {
        return new EvenementFlux(Genre.RAISONNEMENT_DELTA, texte,
                java.util.List.of(), "", null);
    }

    /**
     * @param parties     les parties complètes du message assistant
     * @param raisonArret la raison d'arrêt du fournisseur, ou vide
     * @return la fin de message
     */
    public static EvenementFlux fin(java.util.List<PartieContenu> parties,
                                    String raisonArret) {
        return new EvenementFlux(Genre.FIN_MESSAGE, "", parties,
                raisonArret, null);
    }

    /** @param usage l'usage @return l'événement d'usage */
    public static EvenementFlux usage(UsageLlm usage) {
        return new EvenementFlux(Genre.USAGE, "", java.util.List.of(),
                "", usage);
    }

    @Override
    public String toString() {
        return "EvenementFlux[" + genre + "]";
    }
}
