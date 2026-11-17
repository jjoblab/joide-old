package jo.joide.platform;

import java.util.Objects;

/**
 * Nom d'une racine de chemins : identifiant court en capitales
 * désignant une base stable de l'installation.
 *
 * <p>Les racines standard sont {@code PROJECT} (projet ouvert),
 * {@code SDK} (plate-forme Android), {@code M2} (cache Maven partagé)
 * et {@code OUT} (sorties de build). D'autres racines peuvent être
 * déclarées localement.</p>
 */
public final class RootName {

    /** Racine du projet ouvert. */
    public static final RootName PROJECT = new RootName("PROJECT");

    /** Racine de la plate-forme Android. */
    public static final RootName SDK = new RootName("SDK");

    /** Racine du cache d'artefacts partagé entre projets. */
    public static final RootName M2 = new RootName("M2");

    /** Racine des sorties de build. */
    public static final RootName OUT = new RootName("OUT");

    private final String id;

    private RootName(String id) {
        this.id = id;
    }

    /**
     * @param id identifiant en capitales : une lettre majuscule puis des
     *           lettres majuscules, chiffres ou tirets bas (16 caractères
     *           au plus)
     * @return la racine correspondante
     * @throws IllegalArgumentException si l'identifiant est mal formé
     */
    public static RootName of(String id) {
        if (id == null || !id.matches("[A-Z][A-Z0-9_]{0,15}")) {
            throw new IllegalArgumentException("invalid root name: " + id);
        }
        return new RootName(id);
    }

    /** @return l'identifiant, sans le signe dollar */
    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RootName)) {
            return false;
        }
        return id.equals(((RootName) other).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "$" + id;
    }

    /**
     * @param text forme « $PROJECT »
     * @return la racine correspondante
     * @throws IllegalArgumentException si la forme ou l'identifiant est invalide
     */
    public static RootName parse(String text) {
        Objects.requireNonNull(text, "text");
        if (!text.startsWith("$") || text.length() < 2) {
            throw new IllegalArgumentException("root name must start with '$': " + text);
        }
        return of(text.substring(1));
    }
}
