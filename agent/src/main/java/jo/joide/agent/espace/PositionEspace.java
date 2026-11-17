package jo.joide.agent.espace;

/**
 * Une position de symbole dans le projet — la réponse de
 * {@code go_to_definition} et {@code find_references}.
 */
public final class PositionEspace {
    /** Le fichier relatif. */
    public final String fichier;
    /** La ligne (1-based). */
    public final int ligne;
    /** La colonne (1-based). */
    public final int colonne;
    /** Le nom du symbole, si connu. */
    public final String nom;

    /** @param fichier le fichier @param ligne la ligne @param colonne la colonne @param nom le nom */
    public PositionEspace(String fichier, int ligne, int colonne,
                          String nom) {
        this.fichier = fichier;
        this.ligne = ligne;
        this.colonne = colonne;
        this.nom = nom;
    }
}
