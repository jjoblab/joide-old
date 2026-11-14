# build-daemon

Process :build (service de premier plan), IPC, compilateurs chauds, admission mémoire, reprise après kill.

| | |
|---|---|
| Paquet | `jo.joide.build.daemon` |
| Nature | Android (placeholder P0) |
| Dépendances JoIDE | build-android |
| Phase de remplissage | P6 |

## Invariants

Le contexte se reconstruit depuis le disque ; la mort du daemon n'est jamais celle de l'UI.

## Ce qui n'y est pas (P0)

Squelette : pas de logique. Le contenu arrive à sa phase (ADR-011) ; la règle
de dépendances et les contrôles (langue, API 28) s'appliquent dès maintenant.
