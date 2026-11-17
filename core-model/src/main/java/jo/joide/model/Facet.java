package jo.joide.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Une facette : la configuration propre à un domaine (Android,
 * Kotlin…), sans que le noyau connaisse ce domaine.
 *
 * <p>Les valeurs sont des chaînes et des entiers, lues par le
 * pipeline concerné ; le noyau se contente de les transporter.</p>
 */
public final class Facet {

    private final String type;
    private final Map<String, String> entries;

    /**
     * @param type    identifiant du domaine (ex. « android »)
     * @param entries entrées de configuration
     */
    public Facet(String type, Map<String, String> entries) {
        if (type == null || !type.matches("[a-z][a-z0-9-]{0,30}")) {
            throw new IllegalArgumentException("invalid facet type: " + type);
        }
        this.type = type;
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    /** @return l'identifiant du domaine */
    public String type() {
        return type;
    }

    /** @return les entrées de configuration, non modifiables */
    public Map<String, String> entries() {
        return entries;
    }

    /**
     * @param key entrée cherchée
     * @return la valeur, ou {@code null}
     */
    public String entry(String key) {
        return entries.get(key);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Facet)) {
            return false;
        }
        Facet that = (Facet) other;
        return type.equals(that.type) && entries.equals(that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, entries);
    }

    @Override
    public String toString() {
        return "Facet[" + type + " " + entries + "]";
    }
}
