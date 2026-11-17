package jo.joide.agent;

/**
 * L'usage d'une requête, exposé dès le premier jour (ADR-É9 : « la
 * comptabilité du cache est exposée ») : entrées, sorties ET lectures
 * de cache — le pied « cached » qui régresse est le symptôme visible
 * d'un préfixe qui a dérivé.
 */
public final class UsageLlm {

    /** Les jetons d'entrée facturés pleinement. */
    public final long entrees;
    /** Les jetons de sortie. */
    public final long sorties;
    /** Les jetons lus depuis le cache du prompt (facturés ~0,1×). */
    public final long lecturesCache;

    /** @param entrees jetons d'entrée @param sorties jetons de sortie @param lecturesCache lectures de cache */
    public UsageLlm(long entrees, long sorties, long lecturesCache) {
        this.entrees = entrees;
        this.sorties = sorties;
        this.lecturesCache = lecturesCache;
    }

    /** L'usage nul. */
    public static UsageLlm zero() {
        return new UsageLlm(0, 0, 0);
    }

    /** @param autre l'usage à additionner @return la somme (un tour somme ses requêtes) */
    public UsageLlm plus(UsageLlm autre) {
        return new UsageLlm(entrees + autre.entrees,
                sorties + autre.sorties,
                lecturesCache + autre.lecturesCache);
    }

    @Override
    public String toString() {
        return entrees + " in / " + sorties + " out / "
                + lecturesCache + " cached";
    }
}
