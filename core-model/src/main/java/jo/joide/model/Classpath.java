package jo.joide.model;

import java.util.List;
import java.util.Objects;
import jo.joide.platform.Hash128;

/**
 * Un classpath assemblé : entrées ordonnées et identifiant stable de
 * contenu. Immuable.
 */
public final class Classpath {

    private final List<ClasspathEntry> entries;
    private final Hash128 contentId;

    /**
     * @param entries entrées dans l'ordre déterministe d'assemblage
     */
    public Classpath(List<ClasspathEntry> entries) {
        this.entries = List.copyOf(entries);
        StringBuilder canonical = new StringBuilder(entries.size() * 32);
        for (ClasspathEntry entry : this.entries) {
            canonical.append(entry.canonical()).append('\u0000');
        }
        this.contentId = jo.joide.platform.Hashing.fast128(
                canonical.toString()
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                0x43504154L);
    }

    /** @return les entrées, dans l'ordre */
    public List<ClasspathEntry> entries() {
        return entries;
    }

    /**
     * @return l'identifiant stable de contenu du classpath, sans aucun
     *         chemin absolu : deux assemblages égaux l'ont égal
     */
    public Hash128 contentId() {
        return contentId;
    }

    /** @return le nombre d'entrées */
    public int size() {
        return entries.size();
    }

    /** @return vrai si le classpath est vide */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Classpath)) {
            return false;
        }
        return contentId.equals(((Classpath) other).contentId)
                && entries.equals(((Classpath) other).entries);
    }

    @Override
    public int hashCode() {
        return contentId.hashCode();
    }

    @Override
    public String toString() {
        return "Classpath[" + entries.size() + " entrees " + contentId.toHex()
                + "]";
    }
}
