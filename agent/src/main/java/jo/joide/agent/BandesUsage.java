package jo.joide.agent;

/**
 * Les bandes d'alerte de la comptabilité d'usage (lot Q, ADR-062) :
 * l'usage d'un couple clé+modèle, rapporté à ses plafonds connus par
 * le régulateur, se lit en trois bandes — normale, attention,
 * critique. La bande prévient AVANT le 429 : « proche de la limite
 * à la minute » est une information utile au moment de composer la
 * requête suivante, pas une erreur après coup.
 *
 * <p>Sans plafond connu (occupation 0), la bande est toujours
 * normale : une alerte sans référence mesurée serait une invention.</p>
 */
public final class BandesUsage {

    /** La bande d'occupation. */
    public enum Bande {
        /** Sous 70 % du plafond connu : rien à signaler. */
        NORMALE,
        /** 70–90 % du plafond connu : approcher la limite. */
        ATTENTION,
        /** 90 % et plus du plafond connu : le prochain 429 est
         *  probable, la régulation fait son travail. */
        CRITIQUE
    }

    /** Le seuil de la bande ATTENTION (fraction du plafond). */
    public static final double SEUIL_ATTENTION = 0.70;
    /** Le seuil de la bande CRITIQUE (fraction du plafond). */
    public static final double SEUIL_CRITIQUE = 0.90;

    private BandesUsage() {
    }

    /**
     * @param occupation l'occupation de la fenêtre (0..1 ; 0 sans
     *                   plafond connu)
     * @return la bande correspondante
     */
    public static Bande bandeDe(double occupation) {
        if (occupation >= SEUIL_CRITIQUE) {
            return Bande.CRITIQUE;
        }
        if (occupation >= SEUIL_ATTENTION) {
            return Bande.ATTENTION;
        }
        return Bande.NORMALE;
    }

    /**
     * La bande requêtes d'un couple clé+modèle : l'occupation
     * requêtes du régulateur (0 sans plafond connu → NORMALE).
     *
     * @param regulateur le régulateur du couple
     * @return la bande requêtes
     */
    public static Bande bandeRequetes(RegulateurDebit regulateur) {
        return bandeDe(regulateur.occupationRpm());
    }

    /**
     * La bande jetons d'un couple clé+modèle, avec la requête
     * suivante en vue : l'occupation jetons du régulateur incluant
     * l'estimation de la requête à venir (prévenir AVANT, pas après).
     *
     * @param regulateur    le régulateur du couple
     * @param jetonsEstimes l'estimation des jetons de la requête
     *                      suivante
     * @return la bande jetons
     */
    public static Bande bandeJetons(RegulateurDebit regulateur,
                                    long jetonsEstimes) {
        return bandeDe(regulateur.occupationTpm(jetonsEstimes));
    }
}
