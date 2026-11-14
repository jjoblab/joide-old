# import-gradle

Lecture statique et tolérante des projets Gradle (settings, build, catalogues) et export vers Gradle.

| | |
|---|---|
| Paquet | `jo.joide.imports.gradle` |
| Nature | JVM pure |
| Dépendances JoIDE | core-model, core-deps |
| Phase de remplissage | P7 |

## Invariants

Aucun script de build exécuté ; tout point non résolu figure au SyncReport.

## Ce qui n'y est pas (P0)

Squelette : pas de logique. Le contenu arrive à sa phase (ADR-011) ; la règle
de dépendances et les contrôles (langue, API 28) s'appliquent dès maintenant.
