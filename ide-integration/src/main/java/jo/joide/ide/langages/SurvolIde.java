package jo.joide.ide.langages;

/**
 * Survol d'un symbole, vue JoIDE.
 *
 * @param signature    signature lisible du symbole (jamais null)
 * @param documentation documentation du symbole, {@code null} si aucune
 * @param origine      origine de la déclaration (chemin, jar…),
 *                     {@code null} si inconnue
 */
public record SurvolIde(String signature, String documentation,
        String origine) {
}
