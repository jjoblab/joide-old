/**
 * Le Model Context Protocol dans ses deux sens (lot R, ADR-063) : le
 * CLIENT — configuration `.mcp.json` au format Claude Code/Cursor,
 * transports Streamable HTTP et SSE historique sur le port du lot N,
 * OAuth 2.1 complet, normalisation des schémas tiers, outils
 * `mcp__<serveur>__<outil>` mutants sauf readOnlyHint, et LA règle :
 * un appel ne se retente que si le serveur a prouvé qu'il n'a pas
 * exécuté l'outil ; le SERVEUR — les outils JoIDE exposés sur un
 * bouclage vérifié Host/Origin, jobs longs découplés en task_status.
 *
 * <p>Invariants du paquet :</p>
 * <ul>
 *   <li>aucun type Android, aucune brique — l'hôte fournit les
 *       outils et le store chiffré des signatures ;</li>
 *   <li>le JSON passe par le parseur maison de core-deps (ADR-028) ;</li>
 *   <li>les transports se testent hors ligne par des faux, comme les
 *       fournisseurs — et le chemin réel se prouve client contre
 *       serveur dans la même JVM.</li>
 * </ul>
 */
package jo.joide.agent.mcp;
