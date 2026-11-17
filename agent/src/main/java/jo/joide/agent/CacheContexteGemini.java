package jo.joide.agent;

import jo.joide.platform.Clock;

/**
 * L'état PAR SESSION du cache de contexte serveur de Gemini (lot Q,
 * ADR-061) : l'instruction système + les déclarations d'outils — le
 * plus gros envoyé à chaque itération d'un tour — se posent côté
 * fournisseur ({@code cachedContents}) et se référencent ensuite par
 * nom, facturés une fois au lieu d'à chaque requête.
 *
 * <p>La politique est CONSERVATRICE, pour que le caching ne soit
 * jamais une perte nette :</p>
 * <ul>
 *   <li>rien au premier tour (un chat à un seul tour ne doit pas
 *       payer une création de cache) ;</li>
 *   <li>pas de création sous le minimum cachable du fournisseur
 *       (2 048 jetons estimés) — la demande serait refusée ;</li>
 *   <li>repli inline sur tout échec, ET échec mémorisé par clé de
 *       charge utile — jamais une retente de création à chaque
 *       tour ;</li>
 *   <li>recréation une minute AVANT l'expiration (un tour long ne
 *       référence jamais un nom expiré).</li>
 * </ul>
 *
 * <p>La clé de cache : modèle + empreinte du système + surface
 * d'outils. Horloge injectée (les tests prouvent TTL et
 * rafraîchissement à horloge fausse).</p>
 */
public final class CacheContexteGemini {

    /** Le TTL d'un cache serveur (secondes). */
    public static final long TTL_SECONDES = 3600L;
    /** La marge de rafraîchissement avant expiration (ms). */
    public static final long MARGE_EXPIRATION_MS = 60_000L;
    /** Le minimum cachable du fournisseur (jetons estimés). */
    public static final int MINIMUM_JETONS = 2_048;

    private int tour;
    private String nom;
    private String cleCourante;
    private String cleEchouee;
    private long expirationMs;
    private final Clock horloge;

    /**
     * La fabrique du nom de cache — appelée (et son résultat posé)
     * seulement quand un cache frais est réellement nécessaire.
     */
    public interface Createur {

        /**
         * @return le nom du cache créé ({@code cachedContents/…}),
         *         ou null sur échec (repli inline)
         */
        String creer();
    }

    /** @param horloge l'horloge injectée (null : système) */
    public CacheContexteGemini(Clock horloge) {
        this.horloge = horloge == null ? Clock.system() : horloge;
    }

    /** Le cache du premier tour de session. */
    public CacheContexteGemini() {
        this(null);
    }

    /**
     * @param cle            la clé de charge utile (modèle + système +
     *                       outils)
     * @param jetonsEstimes  l'estimation des jetons cachables
     * @param createur       la fabrique du nom
     * @return le nom à référencer, ou null (repli inline)
     */
    public synchronized String resoudre(String cle, int jetonsEstimes,
                                        Createur createur) {
        tour++;
        if (tour <= 1) {
            return null;
        }
        if (cle.equals(cleEchouee)) {
            return null;
        }
        if (nom != null && cle.equals(cleCourante)
                && !presDeLEnregistrement()) {
            return nom;
        }
        if (jetonsEstimes < MINIMUM_JETONS) {
            cleEchouee = cle;
            return null;
        }
        String cree;
        try {
            cree = createur.creer();
        } catch (Exception e) {
            cree = null;
        }
        if (cree == null || cree.isBlank()) {
            cleEchouee = cle;
            return null;
        }
        nom = cree;
        cleCourante = cle;
        cleEchouee = null;
        expirationMs = horloge.wallTimeMillis()
                + TTL_SECONDES * 1000L;
        return cree;
    }

    private boolean presDeLEnregistrement() {
        return horloge.wallTimeMillis()
                > expirationMs - MARGE_EXPIRATION_MS;
    }
}
