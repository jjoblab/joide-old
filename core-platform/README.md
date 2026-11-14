# core-platform

Socle transversal : hachage, racines de chemins nommées, journalisation, types de résultat, utilitaires de concurrence.

| | |
|---|---|
| Paquet | `jo.joide.platform` |
| Nature | JVM pure |
| Dépendances JoIDE | aucune |
| Phase de remplissage | P1 |

## Invariants

Aucune dépendance vers un autre module JoIDE ; aucun accès disque hors racines nommées ; API 28 stricte.

## Ce qui n'y est pas (P0)

Squelette : pas de logique. Le contenu arrive à sa phase (ADR-011) ; la règle
de dépendances et les contrôles (langue, API 28) s'appliquent dès maintenant.
