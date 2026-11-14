# core-vfs

Snapshot persistant de l'arbre de fichiers et journal de changements fusionné (éditeur, watcher, rattrapage).

| | |
|---|---|
| Paquet | `jo.joide.vfs` |
| Nature | JVM pure |
| Dépendances JoIDE | core-platform, core-store |
| Phase de remplissage | P1 |

## Invariants

Chemins toujours relatifs à une racine nommée ; hash paresseux ; sur doute, re-scan.

## Ce qui n'y est pas (P0)

Squelette : pas de logique. Le contenu arrive à sa phase (ADR-011) ; la règle
de dépendances et les contrôles (langue, API 28) s'appliquent dès maintenant.
