# ADR-001 — Moteur de build natif, sans Gradle, avec échappatoire Gradle

- **Statut** : Accepted (décisions D1/D13 du propriétaire)
- **Date** : 2026-11-15

## Contexte

Gradle (daemon + plugin Android) est trop lourd en mémoire et en démarrage sur un appareil ; sur un très gros projet, la seule configuration est coûteuse. L'analyse du rapport joint (revue de CodeAssist) confirme la direction d'un moteur natif mais montre des ordonnanceur, empreintes et incrémental proportionnels à la taille du projet plutôt qu'à la modification.

## Décision

JoIDE embarque son propre moteur de tâches (DAG, entrées/sorties typées, up-to-date par empreintes) qui pilote directement aapt2, ecj/javac, kotlinc, D8/R8, apksig, bundletool. L'import Gradle est statique (sans exécution), l'export Gradle sert d'échappatoire, et l'exécution Gradle déportée (CI/machine) reste une extension optionnelle conçue en P7.

## Options

| Option | Verdict | Raison |
|---|---|---|
| A. Gradle (Tooling API) sur appareil | écartée | RAM et démarrage rédhibitoires sur mobile |
| B. Moteur natif (retenue) | retenue | démarrage, mémoire bornée, incrémental fin ; coût : couvrir les plugins usuels par SPI, import et export |
| C. Bazel/Buck2 | écartée | hors de portée d'un appareil mobile |

## Conséquences

On perd la logique de build arbitraire ; on gagne des budgets mémoire applicables, des builds incrémentaux fins et des diagnostics structurés. Le risque « projet Gradle exotique » est couvert par le SyncReport de l'import (jamais d'ignorance silencieuse) et l'export.
