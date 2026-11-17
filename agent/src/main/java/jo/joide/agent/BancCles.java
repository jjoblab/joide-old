package jo.joide.agent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jo.joide.platform.Clock;

/**
 * Le banc de clés d'un fournisseur (lot Q, ADR-062 ; le
 * « KeyFailover » de l'ADR-É9) : les clés de l'utilisateur dans SON
 * ordre — principale d'abord, secours ensuite. Chaque requête part
 * vers la première clé qui n'est pas au banc ; une clé refusée pour
 * une limite, ou rejetée, s'assoit au banc un temps, la suivante
 * prend le relais, et la clé principale REVIENT en première ligne
 * dès son temps écoulé : c'est un failover, pas un round-robin.
 *
 * <p>Les bancs sont différenciés par nature de limite :</p>
 * <ul>
 *   <li>limite à la minute (429) : le délai ÉNONCÉ par le
 *       fournisseur, sinon 60 s — jamais moins d'une seconde ;</li>
 *   <li>limite du jour ou quota/crédit : une heure (la borne
 *       journalière dépend du fournisseur) ;</li>
 *   <li>plafond zéro (modèle hors plan) : une heure aussi — une
 *       autre CLÉ peut avoir le modèle dans son plan ;</li>
 *   <li>clé rejetée (auth) : À VIE — elle ne guérit pas seule, le
 *       banc se reconstruit quand l'utilisateur édite les clés.</li>
 * </ul>
 *
 * <p>Thread-safe ; horloge injectée (les tests à horloge fausse
 * prouvent les bancs et le retour de la clé principale).</p>
 */
public final class BancCles {

    /** L'assise d'une limite à la minute sans délai énoncé (ms). */
    public static final long ASSISE_MINUTE_MS = 60_000L;
    /** L'assise d'une limite du jour, d'un quota, d'un hors-plan (ms). */
    public static final long ASSISE_JOUR_MS = 60 * 60_000L;
    /** La plafond de l'assise « à vie » pour une clé rejetée. */
    public static final long ASSISE_A_VIE_MS = Long.MAX_VALUE / 2;
    /** Le délai minimum d'une assise (une retente immédiate ne sert à rien). */
    public static final long ASSISE_MINIMUM_MS = 1_000L;

    /** Les clés, dans l'ordre de l'utilisateur (principale d'abord). */
    public final List<String> cles;

    private final long[] assisesJusqua;
    private final ErreursLlm.Genre[] raisons;
    private final Clock horloge;

    /**
     * @param cles   les clés dans l'ordre utilisateur (blancs et
     *               doublons écartés)
     * @param horloge l'horloge injectée (null : système)
     */
    public BancCles(List<String> cles, Clock horloge) {
        List<String> nettoyees = new ArrayList<>();
        for (String cle : cles == null ? List.<String>of() : cles) {
            String n = cle == null ? "" : cle.trim();
            if (!n.isEmpty() && !nettoyees.contains(n)) {
                nettoyees.add(n);
            }
        }
        this.cles = List.copyOf(nettoyees);
        this.assisesJusqua = new long[this.cles.size()];
        this.raisons = new ErreursLlm.Genre[this.cles.size()];
        this.horloge = horloge == null ? Clock.system() : horloge;
    }

    /** @return le nombre de clés du banc */
    public int taille() {
        return cles.size();
    }

    /**
     * @return l'INDEX de la première clé utilisable maintenant, ou -1
     *         quand toutes sont au banc
     */
    public synchronized int prochaine() {
        long maintenant = horloge.wallTimeMillis();
        for (int k = 0; k < assisesJusqua.length; k++) {
            if (assisesJusqua[k] <= maintenant) {
                return k;
            }
        }
        return -1;
    }

    /**
     * Assied la clé d'index donné pour la nature de refus classée.
     *
     * @param index    l'index de la clé
     * @param genre    le genre classé de l'erreur
     * @param delaiMs  le délai énoncé par le fournisseur en ms, ou -1
     */
    public synchronized void asseoir(int index,
                                     ErreursLlm.Genre genre,
                                     long delaiMs) {
        long assise = switch (genre) {
            case LIMITE_DEBIT -> delaiMs > 0
                    ? Math.max(delaiMs, ASSISE_MINIMUM_MS)
                    : ASSISE_MINUTE_MS;
            case CLE_INVALIDE, CLE_SANS_DROIT -> ASSISE_A_VIE_MS;
            default -> ASSISE_JOUR_MS;
        };
        assisesJusqua[index] = horloge.wallTimeMillis() + assise;
        raisons[index] = genre;
    }

    /**
     * @return le temps (ms) jusqu'à ce qu'une clé soit de nouveau
     *         utilisable ; 0 quand une l'est déjà
     */
    public synchronized long delaiProchaine() {
        long maintenant = horloge.wallTimeMillis();
        long plusTot = Long.MAX_VALUE;
        for (long assise : assisesJusqua) {
            plusTot = Math.min(plusTot, assise);
        }
        if (plusTot == Long.MAX_VALUE) {
            return 0;
        }
        return Math.max(0, plusTot - maintenant);
    }

    /** @return la raison de la clé qui revient la plus tôt, ou null */
    public synchronized ErreursLlm.Genre raisonProchaine() {
        long maintenant = horloge.wallTimeMillis();
        int meilleure = -1;
        for (int k = 0; k < assisesJusqua.length; k++) {
            if (meilleure < 0
                    || assisesJusqua[k] < assisesJusqua[meilleure]) {
                meilleure = k;
            }
        }
        if (meilleure < 0 || assisesJusqua[meilleure] <= maintenant) {
            return null;
        }
        return raisons[meilleure];
    }

    /**
     * @return le compte de clés au banc, par raison — le message
     *         agrégé honnête quand tout le monde est assis
     */
    public synchronized Map<ErreursLlm.Genre, Integer> bancParRaison() {
        long maintenant = horloge.wallTimeMillis();
        Map<ErreursLlm.Genre, Integer> compte = new EnumMap<>(
                ErreursLlm.Genre.class);
        for (int k = 0; k < assisesJusqua.length; k++) {
            if (assisesJusqua[k] > maintenant && raisons[k] != null) {
                compte.merge(raisons[k], 1, Integer::sum);
            }
        }
        return compte;
    }

    /** Le libellé français d'une raison, pour le message agrégé. */
    public static String libelle(ErreursLlm.Genre genre) {
        return switch (genre) {
            case LIMITE_DEBIT -> "rate limited";
            case LIMITE_JOUR -> "at the daily limit";
            case QUOTA_EPUISE -> "out of quota or credit";
            case CLE_INVALIDE -> "rejected (invalid key)";
            case CLE_SANS_DROIT -> "without this model on the plan";
            default -> genre.name().toLowerCase();
        };
    }

    /**
     * @param genre le genre d'une erreur de fournisseur
     * @return vrai si ce genre fait basculer vers la clé suivante —
     *         seuls les refus qui peuvent être DIFFÉRENTS sur une
     *         autre clé basculent (débit, jour, quota, hors plan,
     *         auth) ; tout ce qui échouerait pareil partout (requête
     *         mal formée, contexte trop long, réseau) ne bascule pas
     */
    public static boolean bascule(ErreursLlm.Genre genre) {
        return switch (genre) {
            case LIMITE_DEBIT, LIMITE_JOUR, QUOTA_EPUISE,
                    CLE_INVALIDE, CLE_SANS_DROIT -> true;
            default -> false;
        };
    }
}
