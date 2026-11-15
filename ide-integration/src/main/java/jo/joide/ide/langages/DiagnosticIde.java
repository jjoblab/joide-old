package jo.joide.ide.langages;

/**
 * Diagnostic d'un moteur de langage, vue JoIDE (aucun type de brique).
 *
 * <p>Les décalages sont absolus dans le texte du document (fin
 * exclusive) — la conversion vers lignes/colonnes appartient à
 * l'hôte.</p>
 *
 * @param gravite   ERREUR, AVERTISSEMENT ou INFO
 * @param message   message lisible (jamais null)
 * @param debut     décalage de début (UTF-16, inclus)
 * @param fin       décalage de fin (UTF-16, exclusif)
 * @param code      code stable du diagnostic (ex. « ecj:… », « kt.… »,
 *                  « xml.… »), {@code null} si le moteur n'en fournit
 *                  pas
 */
public record DiagnosticIde(Gravite gravite, String message,
        int debut, int fin, String code) {

    /** Gravité d'un diagnostic, indépendante des moteurs. */
    public enum Gravite { ERREUR, AVERTISSEMENT, INFO }
}
