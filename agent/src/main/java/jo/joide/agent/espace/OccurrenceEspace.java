package jo.joide.agent.espace;

/**
 * Une occurrence textuelle dans le projet : fichier, ligne (1-based),
 * extrait borné — la réponse de {@code search_text} et
 * {@code find_symbol}.
 */
public final class OccurrenceEspace {
    /** Le fichier relatif. */
    public final String fichier;
    /** La ligne (1-based). */
    public final int ligne;
    /** L'extrait (borné côté outil). */
    public final String extrait;

    /** @param fichier le fichier @param ligne la ligne @param extrait l'extrait */
    public OccurrenceEspace(String fichier, int ligne, String extrait) {
        this.fichier = fichier;
        this.ligne = ligne;
        this.extrait = extrait;
    }
}
