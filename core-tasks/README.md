# core-tasks

Moteur de tâches : graphe, ordonnanceur à file de prêtes (chemin critique, admission mémoire), empreintes, up-to-date.

| | |
|---|---|
| Paquet | `jo.joide.tasks` |
| Nature | JVM pure |
| Dépendances JoIDE | core-model, core-store, core-vfs |
| Phase de remplissage | P2 |

## Invariants

Déterminisme à graphe et entrées égaux ; en cas de doute, sur-invalider ; aucune empreinte avec chemin absolu.

## Ce qui n'y est pas (P0)

Squelette : pas de logique. Le contenu arrive à sa phase (ADR-011) ; la règle
de dépendances et les contrôles (langue, API 28) s'appliquent dès maintenant.
