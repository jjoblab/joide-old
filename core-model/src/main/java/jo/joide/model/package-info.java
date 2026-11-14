/**
 * Modèle de projet déclaratif (Workspace/Module/SourceSet/Facet), TOML, snapshots immuables, transactions.
 *
 * <p>Invariants : lecteurs sur snapshots immuables ; chargement tolérant : un module illisible est signalé, jamais fatal.
 *
 * <p>Phase de remplissage : P1 (squelette en P0, ADR-011).</p>
 */
package jo.joide.model;
