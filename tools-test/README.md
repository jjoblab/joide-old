# tools-test

Fixtures et tests des contrôles P0 : règle de dépendances, politique de langue, compatibilité API 28.

| | |
|---|---|
| Paquet | `jo.joide.checks` |
| Nature | JVM pure |
| Dépendances JoIDE | aucune |
| Phase de remplissage | P0 |

## Invariants

Aucun test flaky toléré ; tout échec volontaire est une preuve, pas un accident.

## Ce qui n'y est pas (P0)

Squelette : pas de logique. Le contenu arrive à sa phase (ADR-011) ; la règle
de dépendances et les contrôles (langue, API 28) s'appliquent dès maintenant.
