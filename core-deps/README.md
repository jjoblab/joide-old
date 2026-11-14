# core-deps

Résolution Maven + Gradle Module Metadata, lockfile, checksums, cache disque partagé, extraction sûre des AAR.

| | |
|---|---|
| Paquet | `jo.joide.deps` |
| Nature | JVM pure |
| Dépendances JoIDE | core-platform, core-store |
| Phase de remplissage | P4 |

## Invariants

Lockfile valide = zéro requête réseau ; checksum divergent = échec explicite ; zip-slip bloqué.

## Ce qui n'y est pas (P0)

Squelette : pas de logique. Le contenu arrive à sa phase (ADR-011) ; la règle
de dépendances et les contrôles (langue, API 28) s'appliquent dès maintenant.
