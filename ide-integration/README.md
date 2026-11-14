# ide-integration

Adaptateurs vers les briques existantes (éditeur, serveurs de langage, aperçu) derrière des interfaces JoIDE — P8.

| | |
|---|---|
| Paquet | `jo.joide.ide` |
| Nature | JVM pure |
| Dépendances JoIDE | core-model, build-daemon |
| Phase de remplissage | P8 |

## Invariants

Placeholder en P0 : aucun type tiers ne fuit dans l'API des modules core.

## Ce qui n'y est pas (P0)

Squelette : pas de logique. Le contenu arrive à sa phase (ADR-011) ; la règle
de dépendances et les contrôles (langue, API 28) s'appliquent dès maintenant.
