/**
 * Store de build SQLite WAL derrière l'interface BuildStore : empreintes, snapshot VFS, durées, état incrémental, migrations.
 *
 * <p>Invariants : le store est un cache : toute corruption se reconstruit ; transactions courtes ; écritures atomiques.
 *
 * <p>Phase de remplissage : P1 (squelette en P0, ADR-011).</p>
 */
package jo.joide.store;
