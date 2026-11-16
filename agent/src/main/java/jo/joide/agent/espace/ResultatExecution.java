package jo.joide.agent.espace;

/**
 * Le résultat borné d'une exécution (run_task, run_tests,
 * run_program) : succès, code de sortie, durée — la QUEUE de la
 * sortie est conservée (les journaux intéressants sont à la fin),
 * l'amputée s'annonce.
 */
public final class ResultatExecution {
    /** Vrai si l'exécution a réussi dans son délai. */
    public final boolean succes;
    /** Le code de sortie, -1 si interrompu au délai. */
    public final int codeSortie;
    /** La durée en millisecondes. */
    public final long dureeMs;
    /** La sortie (queue conservée, bornée côté outil). */
    public final String sortie;
    /** Vrai si l'exécution a été interrompue au délai. */
    public final boolean interrompu;

    /** @param succes le succès @param codeSortie le code @param dureeMs la durée @param sortie la sortie @param interrompu l'interruption */
    public ResultatExecution(boolean succes, int codeSortie,
                             long dureeMs, String sortie,
                             boolean interrompu) {
        this.succes = succes;
        this.codeSortie = codeSortie;
        this.dureeMs = dureeMs;
        this.sortie = sortie;
        this.interrompu = interrompu;
    }
}
