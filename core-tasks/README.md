# core-tasks

Moteur de tâches de JoIDE : graphe validé, ordonnanceur sans barrière
de niveau, empreintes relocalisables, cache de sorties adressé par
contenu et build scan.

## Contenu

- **API de tâche** — `TaskDefinition` (nom, type, entrées et sorties
  typées, version d'outil, empreinte d'options, revendication de
  ressources), `TaskContext` (jeton d'annulation, delta d'entrées par
  fichier, résolution de chemins ancrés), `TaskExecution` (réussite
  ou échec avec diagnostics structurés).
- **Graphe** — `TaskGraph` : dépendances dures, arêtes d'ordre
  « mustRunAfter », inférence optionnelle sortie vers entrée par
  index de préfixes (trie), détection de cycle par Kahn unique avec
  reconstruction du chemin exact, rejet des contraintes d'ordre
  contraires aux dépendances.
- **Ordonnanceur** — `TaskScheduler` : file de prêtes par compteurs
  de dépendances, priorité par chemin critique restant
  (`CriticalPaths`, pondéré par les durées historiques du store),
  admission de ressources (`ResourceBudget` : unités de cœur,
  mémoire, exclusivité, plafonds rétrécissables), modes
  `FAIL_FAST` et `KEEP_GOING`, annulation coopérative sans tâche
  orpheline.
- **Moteur** — `BuildEngine` : chaîne de décision à jour (empreinte
  → enregistrement → vérification des sorties signalées sales →
  cache → exécution), passe à chaud sans accès disque ni store
  quand rien n'a changé, hachage paresseux avec réutilisation par
  signaux inchangés, écritures groupées en une transaction de fin de
  build.
- **Cache de sorties** — `OutputCache` : entrées immuables
 adressées par empreinte d'entrées, restauration exacte, éviction
  « le moins récemment utilisé » avec suppression des fichiers.
- **Observabilité** — `BuildReport` (résultat par tâche dans l'ordre
  du graphe, durées par étape, tas utilisé) et `BuildScanWriter`
  (export JSON détaillé et résumé lisible, sans dépendance).

## Utilisation

```java
BuildEngine engine = BuildEngine.builder()
        .roots(rootSet)              // racines nommées $PROJECT, $OUT…
        .store(store)                // BuildStore, possédé par le fil appelant
        .clock(Clock.system())
        .executor(pool)              // exécuteur borné des tâches
        .outputCacheDir(cacheDir)
        .build();

BuildReport report = engine.build(graph, dirtySets, snapshots, annulation);
BuildScanWriter.writeJson(report, cheminJson);
```

Le même moteur se rappelle à chaud : l'état des dernières exécutions
vit en mémoire entre les builds d'un même démon et est rechargé du
store à froid.

## Tests

`./gradlew :core-tasks:test` — propriétés de l'ordonnanceur sur
graphes aléatoires à graine fixe (plus de mille exécutions sous
perturbation des délais), preuve structurelle de l'absence de
barrière de niveau, admission mémoire avec budget artificiel, build
sans changement au tier L sans accès au store (garde armée) et en
cent millisecondes, relocalisation des empreintes, restauration
depuis le cache, erreurs de lecture explicites, delta d'entrées,
modes d'échec et export de scan.
