package jo.joide.app.android.accueil;

import java.util.Objects;

/**
 * L'âge relatif d'une dernière ouverture (ADR-É3 : « une seule ligne
 * de méta ») : un couple unité/valeur que la vue traduit en chaîne
 * localisée — la logique de seuils est pure et testée JVM, jamais
 * dans les vues.
 *
 * <p>Seuils : moins d'une minute (« à l'instant »), minutes, heures,
 * « hier » jusqu'à 48 h, jours jusqu'à 7 j, « la semaine dernière »
 * jusqu'à 14 j, semaines jusqu'à 30 j, mois jusqu'à un an, puis
 * « il y a plus d'un an ». Un moment dans le futur retombe sur
 * « à l'instant » (horloge décalée : jamais d'âge négatif affiché).</p>
 */
public final class AgeRelatif {

    private AgeRelatif() {
    }

    /** Les unités d'âge, du plus proche au plus lointain. */
    public enum Unite {
        /** Moins d'une minute. */
        INSTANT,
        /** Minutes écoulées. */
        MINUTES,
        /** Heures écoulées. */
        HEURES,
        /** Entre 24 et 48 heures. */
        HIER,
        /** Jours écoulés (2 à 6). */
        JOURS,
        /** Entre 7 et 14 jours. */
        SEMAINE_DERNIERE,
        /** Semaines écoulées (2 à 4). */
        SEMAINES,
        /** Mois écoulés (moins d'un an). */
        MOIS,
        /** Plus d'un an. */
        ANNEE
    }

    /** Un âge : l'unité et sa valeur (1 pour les unités sans valeur). */
    public record Age(Unite unite, long valeur) {
        /** Valide l'unité (jamais null). */
        public Age {
            Objects.requireNonNull(unite, "unite");
        }
    }

    private static final long MINUTE = 60_000L;
    private static final long HEURE = 60L * MINUTE;
    private static final long JOUR = 24L * HEURE;
    private static final long SEMAINE = 7L * JOUR;
    private static final long MOIS = 30L * JOUR;
    private static final long ANNEE = 365L * JOUR;

    /**
     * @param maintenant l'instant courant (horloge injectée)
     * @param moment la dernière ouverture
     * @return l'âge relatif, jamais négatif
     */
    public static Age de(long maintenant, long moment) {
        long ecarts = maintenant - moment;
        if (ecarts < MINUTE) {
            return new Age(Unite.INSTANT, 1);
        }
        if (ecarts < HEURE) {
            return new Age(Unite.MINUTES, ecarts / MINUTE);
        }
        if (ecarts < JOUR) {
            return new Age(Unite.HEURES, ecarts / HEURE);
        }
        if (ecarts < 2L * JOUR) {
            return new Age(Unite.HIER, 1);
        }
        if (ecarts < SEMAINE) {
            return new Age(Unite.JOURS, ecarts / JOUR);
        }
        if (ecarts < 2L * SEMAINE) {
            return new Age(Unite.SEMAINE_DERNIERE, 1);
        }
        if (ecarts < MOIS) {
            return new Age(Unite.SEMAINES, ecarts / SEMAINE);
        }
        if (ecarts < ANNEE) {
            return new Age(Unite.MOIS, ecarts / MOIS);
        }
        return new Age(Unite.ANNEE, 1);
    }
}
