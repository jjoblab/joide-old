# build-jvm

Pipeline JVM : compilation Java/Kotlin pilotée, ABI par classe, graphe classe-classe, jar, ressources.

| | |
|---|---|
| Paquet | `jo.joide.build.jvm` |
| Nature | JVM pure |
| Dépendances JoIDE | core-tasks, core-deps |
| Phase de remplissage | P3 |

## Invariants

Build incrémental identique au build propre ; repli complet documenté et seuillé.

## Ce qui n'y est pas (P0)

Squelette : pas de logique. Le contenu arrive à sa phase (ADR-011) ; la règle
de dépendances et les contrôles (langue, API 28) s'appliquent dès maintenant.
