# core-store

Store de build SQLite WAL derrière l'interface BuildStore : empreintes, snapshot VFS, durées, état incrémental, migrations.

| | |
|---|---|
| Paquet | `jo.joide.store` |
| Nature | JVM pure |
| Dépendances JoIDE | core-platform |
| Phase de remplissage | P1 |

## Invariants

Le store est un cache : toute corruption se reconstruit ; transactions courtes ; écritures atomiques.

## Ce qui n'y est pas (P0)

Squelette : pas de logique. Le contenu arrive à sa phase (ADR-011) ; la règle
de dépendances et les contrôles (langue, API 28) s'appliquent dès maintenant.
