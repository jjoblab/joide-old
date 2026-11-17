package jo.joide.app.android.accueil;

/**
 * La salutation datée de la barre d'application (ADR-É3) : « Bonjour »
 * le jour, « Bonsoir » le soir (pluriel au féminin le soir — la
 * légende s'accorde), suivie de la date du jour.
 */
public final class Salutation {

    private Salutation() {
    }

    /** Le moment de la journée porté par la légende. */
    public enum Moment {
        /** Salutation du jour (5 h à 17 h 59). */
        BONJOUR,
        /** Salutation du soir (18 h à 4 h 59). */
        BONSOIR
    }

    /**
     * @param heure l'heure locale du jour (0 à 23)
     * @return BONSOIR à partir de 18 h et avant 5 h, BONJOUR sinon
     */
    public static Moment de(int heure) {
        if (heure >= 18 || heure < 5) {
            return Moment.BONSOIR;
        }
        return Moment.BONJOUR;
    }
}
