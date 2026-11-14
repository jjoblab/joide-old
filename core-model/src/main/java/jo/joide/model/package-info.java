/**
 * Modèle de projet déclaratif (Workspace/Module/SourceSet/Facet), TOML, snapshots immuables, transactions.
 *
 * <p>Invariants : lecteurs sur snapshots immuables ; chargement tolérant : un module illisible est signalé, jamais fatal.
 */
package jo.joide.model;
