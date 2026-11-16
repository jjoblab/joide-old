# app-android

Application Android hôte (accueil, création, IDE, console de build,
service de premier plan `:build`).

| | |
|---|---|
| Paquet | `jo.joide.app` |
| Nature | Android (ADR-045) |
| Dépendances JoIDE | build-daemon, ide-integration |
| Phase de remplissage | P8 (lots A-F), V2 lots G-I (ADR-048/049/050) |

## État des lots H et I (groupe 1 de la V2)

- **`MainActivity`** — l'accueil « Mes projets » (ADR-É3, lot H) :
  hub de projets, écran de lancement. Aucun moteur n'y monte — il
  est instantané. Carte « Reprendre » (le plus récemment ouvert,
  fusionné avec la première carte, jamais doublé), « Nouveau
  projet » primaire, « Importer » tonal (SAF, action secondaire
  visible), section « Projets (n) » au compteur exact, une seule
  ligne de méta par carte, menu ⋮ (renommer l'affichage,
  supprimer avec ses DEUX conséquences distinctes : retirer de la
  liste / effacer du stockage), recherche filtrante, état vide de
  première classe. La logique vit dans les classes pures
  `accueil/` : `EtatAccueil` (tri, fusion, compteur, filtre),
  `ProjetsRecents` (persistance JSON atomique, ADR-049),
  `ClassifieurProjet` (le type d'une carte), `AgeRelatif`,
  `Salutation`.
- **`ecrans/creation/EcranCreation`** — l'assistant de création en
  trois étapes (ADR-É4, lot I) : galerie de modèles avec franchise
  sur la carte, formulaire piloté par le modèle (paquet et minSdk
  seulement pour les modèles Android, le segment propose 28 et 30),
  récapitulatif exact (l'arborescence EST la liste des fichiers
  qui seront écrits), création atomique tout ou rien puis ouverture
  de l'atelier sur le fichier d'entrée. Les modèles sont des
  fichiers TOML déclaratifs `joide.template/1` dans
  `assets/modeles/` (ADR-050) — un modèle v3 s'ajoute sans toucher
  l'assistant. La logique vit dans `creation/` : `ModeleProjet`
  (analyse), `GenerateurProjet` (modèle + identité → arborescence,
  config dérivée par les écrivains canoniques de core-model),
  `EcrireProjet` (temporaire voisin puis renommage atomique, chemins
  hors projet refusés — zip-slip), `EtatCreation` (machine à états,
  validations au fil de la saisie, refus de retrait motivé).
- **`ecrans/ide/EcranIde`** — l'écran IDE de P8 (lots A-F), déplacé
  du lanceur vers cet écran : la racine du projet, la bannière de
  compatibilité Gradle et le fichier d'entrée arrivent par extras
  d'intention. Montage des moteurs de langage (lot C) derrière
  l'éditeur celeris (lot D) et l'aperçu de layouts (lot E), console
  de build branchée au service de premier plan. Le lot J
  (atelier adaptatif) remplacera cet empilement programmatique.
- **`ui/`** — le kit Graphite du lot G (ADR-048), étendu au lot I :
  `Icone` (glyphes dessinés au canvas), `Segment` (options
  exclusives) et `ChoixSegment` ; `Carte.setTonale` (l'accentuation
  de la reprise et du modèle choisi), `Champ.sansEtiquette`, les
  dialogues `menu`/`saisie`/`destructive`/`information` à message
  libre. L'écran d'exercice du kit est retiré au lot H comme le
  lot G l'annonçait — les composants restent, prouvés par leurs
  tests.

## Ce qu'il y a (état du lot F, inchangé)

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
- Chaîne de langue : `values/` en anglais (défaut),
  `values-fr/` en français — même ensemble de clés (contrôle n° 5
  du script de langue, ADR-048).

## Ce qui n'y est pas (P8, groupe 1 de la V2)

- Builds Android sur appareil : D8/R8/aapt2 ne sont pas embarqués
  (décision de taille, ADR-046 — backlog, décision du propriétaire) ;
  l'outil bureau qui constitue une spec Android depuis un workspace
  natif reste à câbler (ADR-050, la carte du modèle le nomme).
- Exécution des mains et des tests sur l'appareil : le pipeline sait
  (`JvmRunTask`), l'UI ne propose pas encore le bouton (backlog,
  ADR-050).
- Import Gradle dans l'application : mode compatibilité (bannière) ;
  l'échappatoire est l'export P7 (ADR-041/042).
- Accès direct SAF sans copie, gestion du stockage des projets
  importés, lockfile sur appareil : backlog P8.
- Exécution sur appareil validée par le propriétaire (l'APK assemble
  et embarque tout le câblage ; les preuves d'exécution complètes —
  accueil, création, import, rendu, service, build — sont la
  validation).
