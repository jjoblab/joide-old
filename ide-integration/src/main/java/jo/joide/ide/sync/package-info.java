/**
 * Synchronisation du modèle pour les services de langage (P8, lot B) :
 * la publication du modèle JoIDE (core-model + résolution core-deps)
 * sous forme d'instantanés « ProjetLsp » immuables consommés par les
 * moteurs de la brique code-lsp.
 *
 * <p>Aucun type de brique ne fuit hors du module (ADR-044) : les
 * signatures de ce paquet qui mentionnent la brique ne sont pas
 * compilables hors {@code ide-integration} (dépendances
 * {@code implementation}).</p>
 */
package jo.joide.ide.sync;
