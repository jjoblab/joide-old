# ADR-009 — Noyau en Java 17 (JDK 21), minSdk 28

- **Statut** : Accepted (décisions D2/D4 du propriétaire)
- **Date** : 2026-11-15

## Contexte

Le propriétaire maintient déjà celeris (Java 17 sur JDK 21) et LayoutLib (Java 17) ; un second langage dans le moteur doublerait le coût de maintenance et le runtime embarqué.

## Décision

Tout le noyau et l'intégration JoIDE sont en Java : source/target 17, construit avec JDK 21 (options.release = 17 dans les conventions, ADR-015). Kotlin n'apparaît que comme dépendance binaire (code-lsp) et comme langage des projets compilés par JoIDE. minSdk 28 : les modules exécutés sur Android n'utilisent que les API du niveau 28, contrôlé dès P0 (ADR-013). Concurrence explicite par pool borné, CancelToken, exécuteurs et horloge injectés ; pas de threads virtuels, pas de sun.misc.Unsafe.

## Options

| Option | Avantages | Inconvénients |
|---|---|---|
| Java (retenue) | cohérent avec celeris/LayoutLib, pas de runtime supplémentaire | pas de coroutines, plus verbeux |
| Kotlin | concision, coroutines | second langage, runtime supplémentaire, incohérent avec les dépôts existants |

## Conséquences

records et sealed interface : adoptables pour les données, mais java.lang.Record est absent du niveau 28 — leur usage dans le code exécuté sur Android attend la validation du désucrage D8 (P5) ; l'ADR-013 les rejette donc jusqu'ici.
