# app-android

Application Android hôte (UI, console de build, service de premier
plan `:build`) — l'IDE de P8.

| | |
|---|---|
| Paquet | `jo.joide.app` |
| Nature | Android (ADR-045) |
| Dépendances JoIDE | build-daemon, ide-integration |
| Phase de remplissage | P8 (lots A-F), V2 lot G (kit Graphite, ADR-048) |

## Ce qu'il y a (état du lot F)

(Voir plus bas « État du lot G » pour le kit Graphite.)

- **`MainActivity`** — l'écran IDE : ouverture d'un projet par le
  sélecteur système (SAF), détection de sa nature
  (`ProjectDetector`), import par copie vers le stockage privé
  (`ImporteurProjet`, ADR-046), montage des moteurs de langage (lot C)
  derrière l'éditeur celeris (lot D) et l'aperçu de layouts (lot E),
  console de build. L'état vide est un état fonctionnel complet.
  Les projets natifs JoIDE (`joide.toml`) sont montés intégralement ;
  les projets Gradle restent en mode compatibilité (bannière —
  l'échappatoire est l'export P7) ; l'inconnu est dit inconnu.
- **`BuildService`** — service de premier plan du processus `:build`
  (§6.7, ADR-043) : héberge le démon — hôte socket locale sur le
  rebouclage (`HoteDemonSocket` de build-daemon) au-dessus du runner
  équivalent, store persistant sur la base système. Le port éphémère
  est publié dans `filesDir/demon-build.port` (écriture atomique).
  Sa mort est un événement de flux pour l'UI, jamais un zombie :
  l'UI survit, le build suivant relance le service.
- **`StoreAndroid`** — implémentation Android du contrat `BuildStore`
  sur la base système (P1 : « l'implémentation Android du même contrat
  vivra avec le démon »), même schéma que le store de bureau, WAL,
  éviction LRU (politique pure testée sur JVM).
- **`SpecsBuild`** — constitution de la description de build (P6)
  depuis le modèle : modules JVM, sources relatives à la racine, jars
  des externes résolus par la synchronisation ; les modules Android
  sont des écarts listés, jamais construits en silence (D8/R8 non
  embarqués — ADR-046).
- **`ConsoleBuild`**, **`FichiersProjet`**, **`ImporteurProjet`**,
  **`RootNames`**, **`ProjectDetector`** — logique pure testable sur
  la JVM : formatage des lignes de console, parcours des fichiers
  ouvrables, copie SAF, noms racine, détection.

## Invariants

- Le code de l'application ne voit QUE `jo.joide.ide` (interfaces),
  les modules JoIDE et le cadre Android — jamais une brique
  directement (ADR-010/044).
- minSdk 28 / targetSdk 28 (ADR-009, D4) ; lint bloquant sans
  baseline (ADR-013) ; javac `-Xlint:all -Werror`.
- L'UI reste mince : chaque décision testable vit dans une classe
  pure (pas de `android.*`), l'écran ne fait que monter et afficher.

## Ce qui n'y est pas (P8)

- Builds Android sur appareil : D8/R8/aapt2 ne sont pas embarqués
  (décision de taille, ADR-046 — backlog, décision du propriétaire).
- Import Gradle dans l'application : mode compatibilité (bannière) ;
  l'échappatoire est l'export P7 (ADR-041/042).
- Accès direct SAF sans copie, gestion du stockage des projets
  importés, lockfile sur appareil : backlog P8.
- Exécution sur appareil validée par le propriétaire (l'APK du lot F
  assemble et embarque tout le câblage ; les preuves d'exécution
  complètes — rendu réel, service, build — sont la validation).
