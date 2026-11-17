# ADR-É4 — Écran de création de projet (assistant en trois étapes)

- **Statut** : Accepted (design — implémentation en lots futurs)
- **Date** : 2026-11-16
- **Maquette** : `design/creation-projet.html`

## Contexte

P8 n'a aucune création : on ne peut entrer que par import SAF d'un
projet existant. La plainte du propriétaire : « Il n'y a même pas
l'option de créer de projet ». CodeAssist (ADR-É1, leçon 3) prouve la
forme : une galerie de modèles, puis un formulaire dont les champs
sont déclarés par le modèle choisi — un modèle apporte ses champs
sans changer l'écran.

## Décision

Un assistant plein écran, trois étapes, progression toujours visible :
**1 Modèle — 2 Identité — 3 Récapitulatif**. Le retour système remonte
d'une étape ; depuis l'étape 1 il quitte l'assistant (rien n'est créé
avant « Créer le projet »).

### Étape 1 — Modèle (galerie)

Recherche par nom, puces de catégorie (Tous, Applications,
Bibliothèques), cartes de modèle verticales : tuile tonale, nom,
description d'une ligne, **note de franchise** quand le modèle a une
limite. Un modèle doit être sélectionné pour continuer (le bouton
reste désactivé, jamais un message d'erreur après coup).

Quatre modèles v2, alignés sur les capacités réelles de P8 — jamais
une promesse :

| Modèle | Contenu généré | Franchise portée par la carte |
|---|---|---|
| Application Android | workspace `joide.toml` multi-modules `:app` (facette Android 37.2, minSdk 28) + `:core` (JVM), manifest, mise en page d'accueil, activité Kotlin | « Édition, diagnostics et aperçu sur l'appareil ; l'APK se construit au bureau (P8) » |
| Console JVM | un module `:console`, `main` Kotlin, exécution et tests sur l'appareil | aucune (entièrement constructible sur l'appareil) |
| Bibliothèque JVM | un module `:socle`, tests JUnit 5 | aucune |
| Vide | `joide.toml` minimal sans module | « vous ajoutez les modules ensuite » |

### Étape 2 — Identité (formulaire piloté par le modèle)

Les champs viennent du modèle (leçon 3), pas de l'écran : nom
(pré-rempli par défaut unique), modules proposés (éditables : ajouter,
retirer), paquet Android pour le modèle Application, minSdk en segment
(28 par défaut, D4 : targetSdk reste 28). Validation au fil de la
saisie (identifiants légaux, nom non vide) ; le bouton Continuer
reflète la validité — jamais d'alerte tardive.

### Étape 3 — Récapitulatif

Récapitulatif en lignes (modèle, nom, modules, paquet, minSdk),
**arborescence générée** en code compact, note de franchise du modèle
reprise telle quelle, bouton « Créer le projet » (accent). La
création écrit dans le stockage privé de JoIDE (ADR-046 : l'original
n'existe pas, il n'y a rien à copier), inscrit le projet dans la
liste d'accueil (ADR-É3) et ouvre l'atelier.

## États et franchise

- Génération rapide, atomique : tout ou rien ; en cas d'échec
  d'écriture, l'écran reste sur l'étape 3 avec l'erreur nominale —
  jamais un projet à moitié créé passé sous silence.
- Les modèles mentent jamais : ce que la carte dit, le projet le
  fait ; ce que l'appareil ne fait pas (build APK Android en P8), la
  carte le dit à l'étape 1 et le récapitulatif le répète.
- Les modèles sont des données (liste de définitions + générateurs
  purs testables JVM), pas du code d'écran : un modèle v3 s'ajoute
  sans toucher l'assistant.

## Conséquences

- Le générateur (définitions → arborescence de fichiers) est une
  classe pure ; l'UI reste mince.
- Après création : atelier ouvert sur le fichier d'entrée du modèle
  (l'activité pour Android, le main pour la console) — jamais un
  atelier vide.
