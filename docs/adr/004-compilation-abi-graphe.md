# ADR-004 — Compilation incrémentale : ABI et graphe classe-classe

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

Constat 4 de la revue : tout changement d'API publique recompile le module entier (Java comme Kotlin), faute de graphe de dépendances fichier-fichier.

## Décision

Compile avoidance par ABI par classe (membres non privés, signatures, constantes inlinables, métadonnées Kotlin ; corps et privés exclus) ; graphe classe-classe enregistré pendant la compilation (références, super-types, imports, constantes inlinées) persisté dans le store ; à un changement : D = modifiées, puis propagation C = classes à ABI changée, A = sources dépendantes, jusqu'au point fixe ; repli complet documenté au-delà d'un seuil (30 %) ou sur processeur agrégeant. Spike P3 obligatoire : API Build Tools de Kotlin face à l'incrémental maison, décidé par mesure (correction, temps, mémoire sur ART).

## Options

| Option | Verdict | Raison |
|---|---|---|
| Recompilation du module | écartée | le cas d'usage central (module central, gros projet) est exactement le pire cas |
| ABI + graphe (retenue) | retenue | recompile les dépendants et eux seuls ; complexité couverte par le test différentiel |
| Incrémental Kotlin réécrit d'office | écartée en P0 | à trancher par le spike P3, pas par avance |

## Conséquences

Le risque de sous-invalidation est couvert par le test différentiel sur ≥ 200 séquences d'éditions (§7 P3) ; en cas de doute, sur-invalider (§4.2).
