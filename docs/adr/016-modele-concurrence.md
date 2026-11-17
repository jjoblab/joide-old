# ADR-016 — Modèle de concurrence : reprise de l'ADR-007 de celeris, transposée au :build

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

L'ADR-007 de celeris (validée par le propriétaire) fixe un modèle d'exécution éprouvé : écrivain unique, lecteurs sur instantanés immuables, exécuteurs et horloge injectés, jetons d'annulation partout, aucun sleep dans les tests, aucune réflexion. Le prompt (§6.3, §10) exige les mêmes principes pour JoIDE.

## Décision

Le modèle est repris à l'identique, transposé : le fil UI devient le fil d'écriture dédié du daemon :build. Écrivain unique pour toute mutation d'état partagé ; travaux sur instantanés immuables, résultats datés, périmés jetés ; exécuteurs et horloge injectés — aucune création dans le code de production ; CancelToken coopératif sur toute opération longue ; aucune donnée mutable partagée sans synchronisation documentée ; pas de static mutable ; pas de coroutines ni de threads virtuels (indisponibles sur ART, D2) ; System.nanoTime confiné à l'outillage de mesure (constat déjà appliqué par le harnais ADR-014).

## Options

| Option | Verdict | Raison |
|---|---|---|
| Reprise ADR-007 transposée (retenue) | retenue | éprouvée dans un dépôt du propriétaire, testable en déterminisme |
| Verrous fins dispersés | écartée | la preuve par l'échec cite l'ADR-007 (146 volatile, 158 Atomic dispersés) |
| Modèle acteur | écartée | détours inutiles pour les mêmes invariants |

## Conséquences

À partir de P1, un test d'architecture léger (règles greppables, sans outil tiers) pourra vérifier l'absence de création d'exécuteur dans le code de production, comme celeris.
