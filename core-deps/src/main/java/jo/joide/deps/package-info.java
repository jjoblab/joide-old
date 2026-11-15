/**
 * Résolution Maven + Gradle Module Metadata, lockfile joide.lock,
 * checksums vérifiés, cache disque partagé adressé par contenu,
 * extraction AAR protégée zip-slip, transport HTTPS vérifié TLS.
 *
 * <p>Invariants : lockfile valide = zéro requête réseau ; checksum
 * divergent = échec explicite ; zip-slip bloqué ; les instantanés et
 * sélecteurs dynamiques sont résolus une fois puis figés ; deux
 * résolutions des mêmes demandes donnent un lockfile identique octet
 * pour octet.</p>
 */
package jo.joide.deps;
