# ADR-040 — SyncReport : rien d'ignoré en silence, confiance par module

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

L'import Gradle (§6.9) lit des projets écrits pour Gradle, pas pour
JoIDE : une partie du contenu n'a pas d'équivalent dans le modèle
(greffons de traitement d'annotations, tests instrumentés, plateformes
BOM, tâches personnalisées, dépôts). Le prompt interdit l'ignorance
silencieuse : « tout ce qui n'a pas pu être résolu est listé ». Il
demande aussi « un niveau de confiance par module » : l'utilisateur
doit savoir, en un coup d'œil, jusqu'où il peut faire confiance à un
module importé.

## Décision

1. **Le rapport est la sortie première de l'import**.
   `GradleImporter.importProject` rend un couple
   (`Workspace`, `SyncReport`) : l'espace de travail lu — même
   partiellement — et la liste exhaustive des problèmes. Chaque
   problème porte une gravité (`ERROR`/`WARN`/`INFO`), un code court
   stable en anglais (`dependency-catalog-unversioned`,
   `android-block-not-mapped`, `compose-compiler-plugin-missing`…) et
   une localisation (fichier du module, ligne quand le parseur la
   connaît).

2. **Trois gravités, une sémantique fixe**. `ERROR` : le point
   empêche de construire le module tel quel (bloc android absent d'un
   module android). `WARN` : le point est perdu pour JoIDE mais
   signalé — dépendance hors modèle (ksp, tests instrumentés,
   plateforme BOM, fichier local, alias sans version), valeur non
   résolue, greffon alias inconnu. `INFO` : le point est du contexte
   Gradle sans équivalent structurel (dépôts, `pluginManagement`,
   options de compilation, blocs non mappés) — rapporté pour
   l'exhaustivité, sans perte pour le modèle.

3. **Confiance à seuils fixes, par module**.
   `ModuleConfidence.of(erreurs, avertissements)` : `HIGH` — aucun
   problème d'aucune gravité ; `MEDIUM` — des avertissements, aucune
   erreur, cinq au plus ; `LOW` — au moins une erreur, ou plus de
   cinq avertissements. Les informations seules ne dégradent pas :
   elles décrivent du contexte, pas des pertes. Le seuil de cinq est
   arbitré : au-delà, le module a perdu trop de sa définition pour
   qu'une confiance moyenne soit honnête.

4. **Attribution par module**. Les problèmes de lecture d'un module
   sont localisés à son fichier de build ; le rapport rend la
   confiance par chemin de module et un rendu texte hiérarchisé
   (totaux, confiance par module, puis problèmes).

5. **L'import ne répare jamais**. Un fichier illisible est une erreur
   et un module vide, jamais une reconstruction silencieuse. Un
   projet sans `settings.gradle` et avec un build racine devient un
   module unique à la racine — avec une information explicite
   (`import-root-as-module`). Un build racine qui applique des
   greffons alors que des modules sont inclus est une perte signalée
   (`root-module-not-mappable`) : ses variables `ext` nourrissent
   encore l'interpolation, mais le projet racine lui-même n'a pas de
   place dans la disposition JoIDE.

## Conséquences

- Le projet réel importé en preuve (`architecture-samples`@ee66e15)
  rend 48 problèmes : chaque dépendance ksp, chaque configuration
  `androidTest*`, chaque alias géré par BOM y figure — l'utilisateur
  voit exactement ce que JoIDE n'a pas pris.
- Le modèle reste pur : les pertes vivent dans le rapport, pas dans
  des champs `maybe` du modèle.
- Les seuils sont publics et testés ; si le ressenti des utilisateurs
  (P8) demande des seuils différents, c'est un changement de
  constante, pas d'architecture.
