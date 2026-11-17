# ADR-045 — Modules Android JoIDE et câblage du conditionnel (P8)

- **Statut** : Accepted
- **Date** : 2026-11-16

## Contexte

ADR-015 (P0) prévoyait l'inclusion conditionnelle des modules Android
pour P6/P8 ; ADR-013 différait lint NewApi vers les modules Android ;
ADR-011 gèle le graphe des modules (toute arête est un changement
explicite). P8 apporte les deux premiers modules Android du dépôt :
`ide-integration` (bibliothèque) et `app-android` (application).

## Décision

1. **Conditionnel** : `JOIDE_SKIP_ANDROID=1` prime sur toute détection ;
   sinon `ANDROID_HOME` → `ANDROID_SDK_ROOT` → `local.properties`
   (`sdk.dir`). Sans SDK, les modules Android ne sont pas inclus et le
   `check` des modules purs reste le critère P0 — pas une dégradation.
2. **Chaîne** : AGP 9.5.0 sur le wrapper Gradle 9.8.2 du dépôt (le
   couple de celeris, prouvé en famille) ; le plugin AGP est appliqué
   par les conventions de build-logic (classpath par catalogue,
   jamais de version en dur dans les modules).
3. **Niveaux** : minSdk 28 (ADR-009 — les briques sont à 24/26, toutes
   ≤ 28) ; compileSdk 37 (+ minor 2) couvre celeris 36 et LayoutLib
   37.2 ; le produit vise le niveau 28 (D4) : contrôle API 28 par le
   même lecteur de pool de constantes que les modules purs, appliqué
   aux classes de la variante release, plus lint bloquant sans
   baseline (NewApi — ADR-013). Amendé en lot A : le scan de pool de
   constantes n'est PAS appliqué aux modules Android — leur fichier de
   signatures couvre la seule surface java/javax du niveau 28 (aucune
   classe de plateforme n'y figure) ; le contrôle API 28 des modules
   Android est porté par lint NewApi seul (qui couvre android.* ET
   java.* contre minSdk 28).
4. **Arête nouvelle** `app-android -> ide-integration` : changement
   explicite du contrat (ADR-011) — l'application hôte monte l'IDE
   derrière les interfaces JoIDE d'ide-integration, jamais les briques
   directement. Ordre descendant du graphe respecté (ide-integration
   avant app-android).
5. **Le plugin `java` n'est jamais appliqué aux modules Android** (conflit
   avec AGP) : les conventions Android répliquent les parties utiles de
   joide.java-conventions (javac `-Xlint:all -Werror`, JUnit 5, règle de
   dépendances, encodage).

## Options

| Option | Verdict | Raison |
|---|---|---|
| Modules JVM compilés contre android.jar (façon build-android) | écartée | un IDE hôte a un manifeste, des ressources, un cycle de vie AGP ; un faux JVM module mentirait |
| Deux modules AGP conditionnels (retenue) | retenue | conforme à ADR-015, critère P0 intact sans SDK |

## Conséquences

- Sans SDK, `tools-test` et la règle de dépendances ne voient pas les
  modules Android : leurs arêtes restent déclarées dans
  `gradle/module-graph.txt` (contrat), la vérification ne s'applique
  qu'aux modules inclus.
- Le bac à sable de développement fournit le SDK par
  `work/android-sdk` (plateformes 36/37.2, build-tools 36.0.0/37.0.0,
  licences acceptées) — documenté dans `docs/dev.md`.
