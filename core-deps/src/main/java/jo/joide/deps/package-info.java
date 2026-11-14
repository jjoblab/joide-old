/**
 * Résolution Maven + Gradle Module Metadata, lockfile, checksums, cache disque partagé, extraction sûre des AAR.
 *
 * <p>Invariants : lockfile valide = zéro requête réseau ; checksum divergent = échec explicite ; zip-slip bloqué.
 *
 * <p>Phase de remplissage : P4 (squelette en P0, ADR-011).</p>
 */
package jo.joide.deps;
