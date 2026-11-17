# ADR-006 — Index des sources segmenté sur disque (LSM)

- **Statut** : Proposed (décision finale par mesure en P8)
- **Date** : 2026-11-15

## Contexte

Constat 8 de la revue : l'index des sources en mémoire croît avec le projet ; le README analysé admet des lenteurs sur gros projets.

## Décision

Index des sources en LSM (memtable plafonnée, segments immuables fusionnés en arrière-plan, requêtes sur l'union) avec budget mémoire dur et dégradation gracieuse. MAIS (§6.8 règle 5 du prompt) : code-lsp possède déjà un index disque (CIDX) — on le réutilise avant d'écrire le nôtre ; l'LSM ne se construit que si une mesure montre que CIDX ne tient pas le budget mémoire au tier XL.

## Options

| Option | Verdict | Raison |
|---|---|---|
| Réutiliser CIDX d'abord | retenue en P8 | réutiliser avant d'écrire (règle 5) |
| LSM dédié si la mesure l'exige | conditionnelle | décision par mesure, pas par intuition |

## Conséquences

Statut Proposed : la décision définitive appartient à la mesure P8 ; aucun travail d'index n'est engagé avant.
