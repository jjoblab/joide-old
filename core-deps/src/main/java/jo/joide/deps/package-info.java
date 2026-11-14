/**
 * Résolution Maven + Gradle Module Metadata, lockfile, checksums, cache disque partagé, extraction sûre des AAR.
 *
 * <p>Invariants : lockfile valide = zéro requête réseau ; checksum divergent = échec explicite ; zip-slip bloqué.
 */
package jo.joide.deps;
