# core-model

Modèle de projet déclaratif (Workspace/Module/SourceSet/Facet), TOML, snapshots immuables, transactions.

| | |
|---|---|
| Paquet | `jo.joide.model` |
| Nature | JVM pure |
| Dépendances JoIDE | core-platform, core-vfs |
| Phase de remplissage | P1 |

## Invariants

Lecteurs sur snapshots immuables ; chargement tolérant : un module illisible est signalé, jamais fatal.

## Ce qui n'y est pas (P0)

Squelette : pas de logique. Le contenu arrive à sa phase (ADR-011) ; la règle
de dépendances et les contrôles (langue, API 28) s'appliquent dès maintenant.
