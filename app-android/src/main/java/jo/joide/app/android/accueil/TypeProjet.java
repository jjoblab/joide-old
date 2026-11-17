package jo.joide.app.android.accueil;

import java.util.Optional;

import jo.joide.app.android.ui.choix.ChoixCarte;

/**
 * Le type d'un projet tel que l'accueil le porte (ADR-É3) : la tuile
 * tonale de sa carte et le libellé de sa ligne de méta en dérivent.
 *
 * <p>Les quatre premiers types sont ceux du kit (ADR-É2) ; le
 * cinquième ({@code GRADLE}) est le mode compatibilité de P7/P8 — sa
 * tuile est celle du vide (grise), sa ligne de méta porte la franchise
 * de compatibilité, jamais un déguisement.</p>
 */
public enum TypeProjet {

    /** Application Android (au moins un module android-*). */
    ANDROID,

    /** Console JVM (module portant la facette application). */
    CONSOLE,

    /** Bibliothèque JVM (modules jvm-library sans point d'entrée). */
    BIBLIOTHEQUE,

    /** Aucun module (joide.toml minimal). */
    VIDE,

    /** Projet Gradle — mode compatibilité, import statique au bureau. */
    GRADLE;

    /** L'identifiant de sérialisation (projets-recents.json). */
    public String idJson() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** La tuile tonale de la carte (GRADLE prend la tuile vide). */
    public ChoixCarte.TypeTuile tuile() {
        return this == GRADLE ? ChoixCarte.TypeTuile.VIDE
                : ChoixCarte.TypeTuile.valueOf(name());
    }

    /**
     * @param id identifiant sérialisé
     * @return le type correspondant, vide si inconnu
     */
    public static Optional<TypeProjet> deJson(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (TypeProjet type : values()) {
            if (type.idJson().equals(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
