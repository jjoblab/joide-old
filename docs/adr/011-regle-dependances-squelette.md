# ADR-011 — Règle de dépendances et squelette des quatorze modules

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

§5.1 du prompt : dépendances uniquement descendantes, test automatique qui échoue si une dépendance interdite apparaît ; modules purs constructibles sans SDK Android.

## Décision

Les 14 modules existent dès P0 (creux ou fonctionnels selon la phase) et la liste des arêtes autorisées vit dans un fichier de données unique, gradle/module-graph.txt (ordre descendant). Application à trois étages : contrôle à la configuration de chaque build (plugin build-logic, après évaluation, toutes les configurations) ; validation des données (acyclicité par Kahn, ordre descendant) par tools-test ; échec volontaire prouvé par TestKit sur un mini-projet branché au vrai build-logic. Un module absent du graphe est une erreur.

## Options

| Option | Verdict | Raison |
|---|---|---|
| Liste d'arêtes + plugin (retenue) | retenue | une seule source de vérité, appliquée à chaque build, testable |
| ArchUnit ou équivalent | écartée | §10 : aucun outil d'analyse statique tiers |
| Revue manuelle | écartée | aucun garde-fou automatique |

## Conséquences

Le graphe est un contrat : toute nouvelle arête est un changement explicite (commit du fichier + test). Le conditionnel Android (modules SDK) est documenté en ADR-015 pour P6/P8.
