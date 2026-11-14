/**
 * Store de build derrière l'interface BuildStore : empreintes de tâches, snapshot de fichiers, durées historiques, état incrémental des compilateurs, index du cache de sorties.
 *
 * <p>Invariants : le store est un cache, toute corruption se reconstruit à vide ; transactions courtes et atomiques ; confiné au fil écrivain ; l'interface reste libre de toute API Android ou JDBC.
 */
package jo.joide.store;
