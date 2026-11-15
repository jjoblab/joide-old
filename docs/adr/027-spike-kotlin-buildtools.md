# ADR-027 — Spike Kotlin : incrémental maison retenu, API Build Tools écartée pour le noyau

- **Statut** : Accepted (révisable sur appareil à P9)
- **Date** : 2026-11-15

## Contexte

§6.4 : « évaluer l'API Build Tools de Kotlin (compilation incrémentale
intégrée) face à ton propre incrémental ; choisis par mesure (correction +
temps + mémoire sur ART) ». Exigence structurante : le **compile avoidance
inter-modules par ABI** (§6.4) — modifier un corps en amont ne recompile pas
l'aval — n'existe pas dans le compilateur Kotlin : il faut l'ajouter autour,
quel que soit le choix.

## Décision

Retenir l'**incrémental maison** (lots au point fixe par `ImpactAnalysis`,
manifestes ABI par module, compilation jointe Kotlin-puis-Java pilotée par
`KotlinCompilerDriver`) et ne pas adopter l'API Build Tools
(`org.jetbrains.kotlin.buildtools.api`) comme noyau. Révisable à P9 avec des
mesures sur appareil (temps + mémoire), critères ci-dessous.

## Mesures et constats

Côté maison (exécutable, preuves au rapport P3) :

- correction : test différentiel 200 séquences à graine fixe,
  incrémental ≡ propre octet pour octet (Java et Kotlin, multi-modules),
  136 s sur JVM de bureau ;
- granularité mesurée : corps de méthode amont → 0 tâche aval recompilée ;
  signature publique → dépendants seuls ;
- mémoire : le pilote compile lot par lot, jamais le module entier en
  une unité ; les empreintes vivent dans le store, pas en tas.

Côté API Build Tools (surface vérifiée dans l'artefact réel,
`kotlin-build-tools-api` 2.4.10 du cache Gradle — la vérification §4.2 a été
faite sur les classes chargées, pas sur la documentation) :

- `CompilationService` + `BaseCompilationOperation` pilotent une compilation
  **par module** dont l'état incrémental (`MODULE_BUILD_DIR`,
  `BACKUP_CLASSES`, `FORCE_RECOMPILATION`…) appartient à la bibliothèque,
  sous un format interne versionné avec le compilateur ;
- l'entrée est un `SourcesChanges` (ensembliste de fichiers), la sortie un
  `CompilationResult` : **aucun concept d'ABI inter-modules** — le paquet
  `buildtools.api.abi` est un outil de *validation* de compatibilité binaire
  (dump/compare de texte), pas du compile avoidance ;
- l'API est marquée `@ExperimentalBuildToolsApi`/`@DelicateBuildToolsApi` ;
- l'état et l'ordonnancement étant dans la bibliothèque, la remplacer par
  dessus l'ABI maison (le seul moyen de satisfaire §6.4) reviendrait à payer
  deux états incrémentaux pour un seul utilisé.

## Options

| Option | Verdict | Raison |
|---|---|---|
| API Build Tools comme noyau | écartée | état interne opaque, granularité module, pas d'ABI inter-modules, API expérimentale ; l'exigence §6.4 exigerait l'ABI maison par-dessus |
| Incrémental maison (retenue) | retenue | couvre §6.4 entièrement, mesuré, lot par lot (mémoire bornée), état dans le store vérifiable |
| Hybride (Build Tools pour le intra-module) | écartée | deux états incrémentaux concurrents sur un même répertoire de sortie ; risque de divergence sans moyen de contrôle |

## Conséquences

- Le pilote embarqué `KotlinCompilerDriver` (compilateur embarqué 2.3.0,
  `-no-stdlib`, racines Java étalées pour la résolution jointe) reste le seul
  chemin de compilation Kotlin.
- Réévaluation à P9 sur l'appareil de référence avec trois mesures convenues
  à l'avance : temps mur d'une édition de corps (feuille, module central),
  pic de tas du compilateur, et comportement sous budget mémoire réduit. Si
  l'API Build Tools devance sur les trois SANS perdre l'ABI inter-modules,
  réécrire l'ADR.
- Aucun code n'est aujourd'hui couplé à l'API Build Tools : le coût de la
  réévaluation est nul tant que le pilote reste derrière `CompilerDriver`.
