package jo.joide.agent;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le quota ÉNONCÉ par un fournisseur dans une erreur de limite
 * (ADR-É9, cas d'école du 429 ; note clean-room n° 3 § 4) : la
 * métrique (requêtes contre jetons d'entrée), la fenêtre (minute
 * contre jour) et, quand le fournisseur la nomme, la limite. Le
 * raffinement regarde le CODE d'abord (429 = transitoire) et le
 * wording ensuite : un plafond à ZÉRO signifie « modèle hors plan »,
 * une fenêtre journalière est la limite du jour, sinon la limite à
 * la minute. Cette information nourrit l'apprentissage du
 * {@link RegulateurDebit} (lot Q) : une limite énoncée devient la
 * nouvelle borne.
 */
public final class QuotaFournisseur {

    /** La métrique plafonnée. */
    public enum Metrique {
        /** Les requêtes par fenêtre. */
        REQUETES,
        /** Les jetons d'entrée par fenêtre. */
        JETONS_ENTREE
    }

    /** La fenêtre du plafond. */
    public enum Fenetre {
        /** Une fenêtre glissante d'une minute. */
        MINUTE,
        /** La fenêtre journalière du fournisseur. */
        JOUR
    }

    /** La métrique plafonnée. */
    public final Metrique metrique;
    /** La fenêtre du plafond. */
    public final Fenetre fenetre;
    /** La limite énoncée, ou -1 (non énoncée). */
    public final long limite;

    private static final Pattern LIMITE_NOMMEE = Pattern.compile(
            "limit[:\\s]+(\\d+)");

    /** @param metrique la métrique @param fenetre la fenêtre @param limite la limite énoncée ou -1 */
    public QuotaFournisseur(Metrique metrique, Fenetre fenetre,
                            long limite) {
        this.metrique = Objects.requireNonNull(metrique, "metric");
        this.fenetre = Objects.requireNonNull(fenetre, "window");
        this.limite = limite;
    }

    /**
     * Détecte un plafond à zéro dans le corps d'une erreur de quota —
     * le cas d'école de l'ADR-É9 : un plafond zéro signifie que le
     * modèle n'est pas dans le plan de la clé.
     *
     * @param corps le corps de l'erreur
     * @return vrai si un plafond zéro est énoncé
     */
    public static boolean plafondZero(String corps) {
        if (corps == null) {
            return false;
        }
        String bas = corps.toLowerCase(Locale.ROOT);
        Matcher m = LIMITE_NOMMEE.matcher(bas);
        return m.find() && "0".equals(m.group(1));
    }

    /**
     * Raffine la métrique et la fenêtre par le wording d'un 429 —
     * le code a déjà gagné (transitoire), le wording dit QUELLE
     * limite : jetons par minute, requêtes par minute, fenêtre du
     * jour.
     *
     * @param corps le corps de l'erreur
     * @return le quota raffiné (limite non énoncée : -1), ou null
     *         si le wording n'en dit rien de classable
     */
    public static QuotaFournisseur raffiner(String corps) {
        if (corps == null) {
            return null;
        }
        String bas = corps.toLowerCase(Locale.ROOT);
        boolean jetons = bas.contains("tokens per minute")
                || bas.contains("token per minute")
                || bas.contains("tpm");
        boolean requetes = bas.contains("requests per minute")
                || bas.contains("request per minute")
                || bas.contains("rpm");
        boolean jour = bas.contains("per day") || bas.contains("daily")
                || bas.contains("until tomorrow");
        if (!jetons && !requetes) {
            return null;
        }
        Metrique metrique = jetons ? Metrique.JETONS_ENTREE
                : Metrique.REQUETES;
        Fenetre fenetre = jour ? Fenetre.JOUR : Fenetre.MINUTE;
        long limite = -1;
        Matcher m = LIMITE_NOMMEE.matcher(bas);
        if (m.find()) {
            try {
                limite = Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                limite = -1;
            }
        }
        return new QuotaFournisseur(metrique, fenetre, limite);
    }

    @Override
    public String toString() {
        return "Quota[" + metrique + "/" + fenetre
                + (limite >= 0 ? "/" + limite : "") + "]";
    }
}
