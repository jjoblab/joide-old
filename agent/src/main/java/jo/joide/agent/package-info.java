/**
 * Le noyau de l'agent JoIDE (lot N, ADR-055/É9) : module PUR, pair de
 * core-tasks — modèle neutre à signatures opaques, boucle à
 * historique append-only, taxonomie d'erreurs actionnables,
 * permissions, sessions à double moitié, fournisseurs Anthropic et
 * OpenAI-compatible derrière un transport unique.
 *
 * <p>Invariants du paquet :</p>
 * <ul>
 *   <li>aucun type Android, aucune brique — le port
 *       {@code EspaceTravailAgent} (lot O) vit côté ide-integration ;</li>
 *   <li>la concurrence est explicite : pools bornés de
 *       {@code WorkerPools}, jamais de fils créés à la main
 *       (ADR-016) ;</li>
 *   <li>le JSON passe par le parseur maison de core-deps (ADR-028)
 *       et son écriture compacte {@code JsonEcrire} ;</li>
 *   <li>aucune clé d'API dans le code : le stockage chiffré est un
 *       port (ADR-058), implémenté côté application.</li>
 * </ul>
 */
package jo.joide.agent;
