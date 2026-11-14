# build-android

Pipeline Android par variante : aapt2, R, dex par scopes, merge par buckets, package, signature, AAB, R8.

| | |
|---|---|
| Paquet | `jo.joide.build.android` |
| Nature | JVM pure |
| Dépendances JoIDE | build-jvm |
| Phase de remplissage | P5 |

## Invariants

Travail proportionnel à la modification ; cache de dex partageable entre projets.

## Ce qui n'y est pas (P0)

Squelette : pas de logique. Le contenu arrive à sa phase (ADR-011) ; la règle
de dépendances et les contrôles (langue, API 28) s'appliquent dès maintenant.
