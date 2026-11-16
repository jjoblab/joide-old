package jo.joide.agent;

/**
 * Les événements d'un tour de l'agent — ce que l'UI vivra au lot P
 * (ADR-É9, l'UI de l'agent) : deltas de texte et de raisonnement,
 * appels d'outils début/fin/refusé, fichiers changés, attente
 * visible, compaction, fin de tour avec sa raison, erreur classée.
 * Le transcript visible se construit à partir de ces événements —
 * l'UI ne déduit rien, le service est le modèle d'état.
 */
public final class EvenementAgent {

    /** Le genre d'événement. */
    public enum Genre {
        /** Un delta de texte de la réponse. */
        TEXTE_DELTA,
        /** Un delta de raisonnement (visible puis replié). */
        RAISONNEMENT_DELTA,
        /** Un appel d'outil commence. */
        OUTIL_DEBUT,
        /** Un appel d'outil se termine (avec son résultat borné). */
        OUTIL_FIN,
        /** Un appel d'outil a été refusé (permission ou plan seul). */
        OUTIL_REFUSE,
        /** Des fichiers ont changé (ce que l'écriture a réellement fait). */
        FICHIERS_CHANGES,
        /** Une attente visible (limite de débit) avec son décompte. */
        ATTENTE,
        /** L'historique vient d'être compacté (avant → après). */
        COMPACTATION,
        /** Le tour est fini (avec sa raison). */
        FIN_TOUR,
        /** Une erreur classée (la taxonomie dit quoi faire). */
        ERREUR
    }

    /** La raison de fin de tour. */
    public enum RaisonFin {
        /** Le modèle a répondu sans appeler d'outil. */
        TERMINE,
        /** Le plafond d'itérations : le dernier tour SANS outils a rapporté l'état (passation). */
        PLAFOND,
        /** Annulé par l'utilisateur. */
        ANNULE,
        /** Le tour s'arrête sur une erreur classée. */
        ERREUR
    }

    /** Le genre. */
    public final Genre genre;
    /** Le texte (deltas, messages). */
    public final String texte;
    /** Le nom de l'outil (OUTIL_*). */
    public final String outil;
    /** L'identifiant d'appel (OUTIL_*). */
    public final String idAppel;
    /** Les secondes restantes (ATTENTE). */
    public final long secondes;
    /** Les caractères avant/après (COMPACTATION). */
    public final long avant;
    /** Les caractères après (COMPACTATION). */
    public final long apres;
    /** La raison de fin (FIN_TOUR). */
    public final RaisonFin raison;
    /** L'erreur classée (ERREUR), ou null. */
    public final ErreursLlm.ErreurLlm erreur;

    private EvenementAgent(Genre genre, String texte, String outil,
                           String idAppel, long secondes, long avant,
                           long apres, RaisonFin raison,
                           ErreursLlm.ErreurLlm erreur) {
        this.genre = genre;
        this.texte = texte;
        this.outil = outil;
        this.idAppel = idAppel;
        this.secondes = secondes;
        this.avant = avant;
        this.apres = apres;
        this.raison = raison;
        this.erreur = erreur;
    }

    /** @param texte le delta @return un delta de texte */
    public static EvenementAgent texte(String texte) {
        return new EvenementAgent(Genre.TEXTE_DELTA, texte, "", "",
                0, 0, 0, null, null);
    }

    /** @param texte le delta @return un delta de raisonnement */
    public static EvenementAgent raisonnement(String texte) {
        return new EvenementAgent(Genre.RAISONNEMENT_DELTA, texte, "",
                "", 0, 0, 0, null, null);
    }

    /** @param idAppel l'identifiant @param outil le nom @return un début d'appel */
    public static EvenementAgent outilDebut(String idAppel, String outil) {
        return new EvenementAgent(Genre.OUTIL_DEBUT, "", outil, idAppel,
                0, 0, 0, null, null);
    }

    /** @param idAppel l'identifiant @param outil le nom @param resultat le résultat borné @return une fin d'appel */
    public static EvenementAgent outilFin(String idAppel, String outil,
                                          String resultat) {
        return new EvenementAgent(Genre.OUTIL_FIN, resultat, outil,
                idAppel, 0, 0, 0, null, null);
    }

    /** @param idAppel l'identifiant @param outil le nom @param motif le refus motivé @return un refus */
    public static EvenementAgent outilRefuse(String idAppel, String outil,
                                             String motif) {
        return new EvenementAgent(Genre.OUTIL_REFUSE, motif, outil,
                idAppel, 0, 0, 0, null, null);
    }

    /** @param detail les fichiers changés @return l'événement fichiers */
    public static EvenementAgent fichiersChanges(String detail) {
        return new EvenementAgent(Genre.FICHIERS_CHANGES, detail, "",
                "", 0, 0, 0, null, null);
    }

    /** @param secondes le décompte annulable @return une attente visible */
    public static EvenementAgent attente(long secondes) {
        return new EvenementAgent(Genre.ATTENTE, "", "", "", secondes,
                0, 0, null, null);
    }

    /** @param avant caractères avant @param apres caractères après @return la compaction */
    public static EvenementAgent compaction(long avant, long apres) {
        return new EvenementAgent(Genre.COMPACTATION, "", "", "", 0,
                avant, apres, null, null);
    }

    /** @param raison la raison @return la fin de tour */
    public static EvenementAgent fin(RaisonFin raison) {
        return new EvenementAgent(Genre.FIN_TOUR, "", "", "", 0, 0, 0,
                raison, null);
    }

    /** @param erreur l'erreur classée @return l'événement d'erreur */
    public static EvenementAgent erreur(ErreursLlm.ErreurLlm erreur) {
        return new EvenementAgent(Genre.ERREUR, erreur.getMessage(), "",
                "", 0, 0, 0, RaisonFin.ERREUR, erreur);
    }

    @Override
    public String toString() {
        return "EvenementAgent[" + genre + "]";
    }
}
