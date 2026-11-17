# ADR-018 — Analyseur TOML maison, tolérant, sur un sous-ensemble standard

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

Les fichiers de projet (D7 : `joide.toml`, `joide-module.toml`) et, plus tard, les catalogues de versions Gradle sont en TOML. Le critère P1 exige un chargement tolérant : un fichier corrompu est signalé, le reste charge — l'analyseur ne peut donc jamais lever d'exception sur une entrée malformée, et chaque anomalie doit être localisée (ligne, colonne). Le code doit aussi tourner sur Android niveau 28 sans dépendance tierce nouvelle.

## Décision

Un **analyseur maison** (`jo.joide.model.toml.Toml`, ~450 lignes) couvre le sous-ensemble standard nécessaire : commentaires, clés nues ou citées, chaînes basiques avec échappements et chaînes littérales, entiers (décimaux, hexadécimaux, octaux, binaires, séparateurs soulignés), booléens, tableaux multi-lignes, tables `[a.b]` et séries `[[a.b]]`, clés pointées. Les constructions hors sous-ensemble (nombres à virgule, dates, tables en ligne) sont signalées et sautées. La tolérance est prouvée par un test de mutations pseudo-aléatoires (2 000 documents mutés, graine fixe, aucune exception).

Le **schéma canonique** des fichiers de projet n'utilise que ce sous-ensemble, en forme standard : les listes sont des tableaux (`include = ["a", "b"]`), jamais des clés répétées — les projets synthétiques de tools-bench écrivent désormais exactement ce schéma, et un test de validation croisée (dans tools-bench) vérifie que le modèle charge un projet généré.

## Options

| Option | Verdict | Raison |
|---|---|---|
| Analyseur maison sur sous-ensemble (retenue) | retenue | tolérance et diagnostics localisés exigés par le critère ; zéro dépendance ; surface minuscule et testée par fuzzing |
| Bibliothèque TOML tierce (tomlj, jackson-dataformat-toml) | écartée | lève des exceptions sur entrée malformée (contraire au critère) ; dépendance nouvelle à consigner ; diagnostics à retraduire |
| Réutiliser l'analyseur de tools-bench (P0, génération seulement) | écartée | tools-bench écrit, ne lit pas ; et le noyau ne dépend pas du banc |

## Conséquences

Toute évolution du format passe par le sous-ensemble documenté et par les tests (syntaxe, fuzz, validation croisée). Les clés dupliquées gardent la première valeur et sont signalées ; les tables réouvertes sont signalées. L'analyseur construit ses tables sur des cartes vivantes internes (`mutableEntries`), l'API publique restant non modifiable — l'aliasing initial a été corrigé et couvert par les tests d'aller-retour.
