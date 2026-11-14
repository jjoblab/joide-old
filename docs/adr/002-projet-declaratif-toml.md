# ADR-002 — Projet déclaratif TOML, aucun script exécuté

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

Un DSL de build exécutable (Kotlin script Gradle) est puissant mais lent à évaluer et impossible à sécuriser sur appareil ; YAML pose des ambiguïtés de typage.

## Décision

joide.toml (workspace) et joide-module.toml (module), TOML déclaratif, aucun script exécuté pendant la synchronisation (§4.3 du prompt). L'extensibilité passe par des plugins compilés via un SPI. Écritures atomiques (temporaire + rename + fsync), chargement tolérant : un module illisible est signalé et ignoré, jamais un crash. Le sous-ensemble généré par tools-bench est figé sous les schémas joide.workspace/1 et joide.module/1 (ADR-014).

## Options

| Option | Verdict | Raison |
|---|---|---|
| TOML déclaratif (retenue) | retenue | synchro quasi instantanée et sûre, lisible, diffable |
| DSL Kotlin script | écartée | évaluation coûteuse, sécurité délicate |
| YAML | écartée | ambiguïtés de typage |

## Conséquences

Synchronisation sûre et rapide ; moins de flexibilité, compensée par le SPI de plugins et l'import statique de Gradle.
