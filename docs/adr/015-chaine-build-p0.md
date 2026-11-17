# ADR-015 — Chaîne de build P0 : Gradle 9.8.2, conventions, conditionnel Android

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

D13 : JoIDE se construit avec Gradle (comme les autres dépôts du propriétaire) ; l'auto-hébergement est un objectif tardif. §10 : aucun outil d'analyse statique, aucune version en dur, toutes dans gradle/libs.versions.toml.

## Décision

Gradle 9.8.2 par wrapper, distribution épinglée par somme de contrôle (même chaîne que celeris). JDK 21 construit, artefacts 17 par options.release (javac garantit la surface 17 ; pas de toolchain à provisionner). Conventions dans build-logic (includeBuild) : joide.java-conventions (javac -Xlint:all -Werror, UTF-8, JUnit 5, règle de dépendances ADR-011, checkApi28 ADR-013) et joide.java-library-conventions. -Werror fait du compilateur le seul outil de qualité (§10 : ni Checkstyle, ni PMD, ni Error Prone, ni seuils de couverture). Catalogue unique gradle/libs.versions.toml ; seule dépendance : JUnit 5.14.4 (test). Le DSL Kotlin reste confiné aux scripts de build ; les classes de build-logic sont en Java. Patron celeris pour l'arrivée des modules Android : inclusion conditionnelle des modules Android si SDK détecté, refus explicite JOIDE_SKIP_ANDROID=1 — documenté ici, appliqué à partir de P6/P8 ; en P0 les 14 modules sont des projets JVM.

## Options

| Option | Verdict | Raison |
|---|---|---|
| Conventions build-logic (retenue) | retenue | un seul endroit, aucun outil tiers |
| Configuration répétée par module | écartée | dérive garantie |
| Maven | écartée | incohérent avec les dépôts du propriétaire |

## Conséquences

Le wrapper (gradlew, jar, propriétés) est repris de celeris — artefacts Gradle Apache-2.0, consignés dans NOTICE.
