package jo.joide.platform;

/**
 * Horloge injectée : source unique du temps pour le code de production.
 */
public interface Clock {

    /**
     * @return l'horloge système réelle
     */
    static Clock system() {
        return SystemClock.INSTANCE;
    }

    /**
     * @return un compteur monotone en nanosecondes, adapté aux mesures
     *         de durée
     */
    long nanoTime();

    /**
     * @return l'instant courant en millisecondes depuis l'époque, adapté
     *         aux affichages et aux horodatages
     */
    long wallTimeMillis();

    /**
     * Implémentation système de l'horloge.
     */
    final class SystemClock implements Clock {
        static final SystemClock INSTANCE = new SystemClock();

        private SystemClock() {
        }

        @Override
        public long nanoTime() {
            return System.nanoTime();
        }

        @Override
        public long wallTimeMillis() {
            return System.currentTimeMillis();
        }
    }
}
