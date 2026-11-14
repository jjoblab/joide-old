# tools-bench

Générateur déterministe de projets synthétiques S/M/L/XL, harnais de mesure, baselines commitées.

| | |
|---|---|
| Paquet | `jo.joide.bench` |
| Nature | JVM pure |
| Dépendances JoIDE | aucune |
| Phase de remplissage | P0 |

## Invariants

Mêmes octets à graine égale ; aucune mesure sans trois exécutions ; graine affichée.

## Ce qui n'y est pas (P0)

Squelette : pas de logique. Le contenu arrive à sa phase (ADR-011) ; la règle
de dépendances et les contrôles (langue, API 28) s'appliquent dès maintenant.
