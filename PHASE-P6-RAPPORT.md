# PHASE P6 — RAPPORT

- **Date** : 2026-11-15
- **Base** : P5 validée (`f6c601c`), feuille de route poussée
- **Module rempli** : `build-daemon` (paquets `jo.joide.build.daemon`) + évolutions de `build-android`, `core-store` et du graphe des modules

## 1. Ce qui est fait

Le process `:build` : un démon qui vit dans sa propre machine
virtuelle, parle par cadres JSON sur les flux standard, exécute les
pipelines P3/P5 sur le moteur complet, survit à ses propres builds et
meurt proprement quand on le tue — pendant que le client, lui, survit
toujours.

- **Protocole IPC** — cadres de longueur (JSON UTF-8 précédé de sa
  taille, plafond dix Mio, refus des longueurs folles), écriture JSON
  compacte maison (l'analyse passe par l'analyseur du résolveur),
  description de build complète (`BuildSpec` : espaces, modules JVM ou
  Android avec manifestes/ressources/AAR, variantes avec signature et
  rétrécissement). Les événements de tâches partent en direct —
  démarrage et fin — jamais dans un bilan posthume.
- **Client** — `DaemonClient` lance la machine virtuelle fille
  (classpath courant transmis), relaie ses erreurs standard, expose
  les événements en file bloquante ; la mort du démon (OOM, kill) est
  une exception dédiée, le code de sortie est récoltable
  (`awaitExit`). `java.home` plutôt que `ProcessHandle` : le client
  reste sous contrôle API 28 (il voyagera dans l'application
  Android).
- **Exécuteur équivalent** — `BuildRunner` construit le graphe
  (pipeline JVM ou Android selon la description) et le donne au
  moteur complet. Les fabriques et le dexeur en processus sont détenus
  par le démon : un compteur publié dans chaque bilan prouve qu'ils
  ne sont pas recréés (compilateurs chauds). L'exécuteur tourne aussi
  directement dans la machine virtuelle de l'appelant (mode en
  processus, choix du câbleur) — le même code, la même sémantique.
- **Admission mémoire** — le budget de la description traverse
  l'ordonnanceur (revendications par tâche, P2) et l'entrée du démon :
  un budget au-dessus du tas est refusé explicitement, jamais d'OOM
  silencieuse. Les revendications des forks (fusion/R8 : 1 024 Mo)
  sont portées au budget — la comptabilité est celle de la mémoire de
  l'appareil (ADR-038).
- **Reprise après mort** — le démon de bureau ouvre le store SQLite
  persistant à la racine des sorties : les résultats de tâches sont
  persistés par lot (un build tué en plein vol laisse durable le
  travail fini), et l'ensemble sale de la racine des sources est
  différé contre l'instantané persisté du store — le démon n'a pas
  d'observateur entre ses morts, le diff disque le remplace (les
  ensembles explicites d'un observateur priment quand ils existent).
- **Injection de pannes** — requête « crash » armée par
  `-Djoide.daemon.faults=1` uniquement : épuisement réel du tas sans
  spirale de ramasse-miettes, erreur relancée hors de tout rattrapage
  ; le démon de production répond `crash-refused` et survit.
- **Banc de preuve** — source set `bench` : générateur déterministe
  aux gabarits de tools-bench (mêmes comptes, couches et modules
  centraux, sources Java seules), `./gradlew
  :build-daemon:daemonBench -Ptier=L` mesure et échoue si le pic de
  tas dépasse le plafond.

## 2. Critères d'acceptation P6, un par un

| Critère (feuille de route) | Preuve | Statut |
|---|---|---|
| Process `:build` | `DaemonMain` (boucle de protocole, store persistant) lancé par `DaemonClient.spawn` ; test `DaemonLifecycleTest.buildsRunAndSecondBuildKeepsCompilersHot` : salutation, build complet, arrêt propre | **Fait** |
| IPC | `ProtocolTest` (cadres aller-retour, échappements, non-ASCII, plafonds de cadre en lecture et écriture, aller-retour de description, module sans nom refusé) ; les builds réels passent par le protocole | **Fait** |
| Compilateurs chauds | compteurs `hotCompilers`/`hotDexers` publiés dans chaque bilan ; second build du même espace : fabrique non recréée (compteur constant) — JVM (`DaemonLifecycleTest`) et Android (`DaemonAndroidPipelineTest`, dexeur de scopes réutilisé) | **Fait** |
| Admission mémoire | budget au-dessus du tas refusé explicitement (`memoryBudgetAboveHeapIsRefusedExplicitly`) ; revendications des forks portées au budget (R8 1 024 Mo : spec à 1 280, démon à 1 500 — ADR-038) | **Fait** |
| Fork à gros tas pour D8-merge/R8 | `DaemonAndroidPipelineTest` : fusion et rétrécissement R8 dans la machine virtuelle fille d'un gigaoctet pendant que le pic de tas du démon reste sous 700 Mo — l'isolation est la preuve | **Fait** |
| Reprise après kill | `DaemonDeathResumeTest.killNineDuringBuildIsResumedFromDisk` : kill -9 après le treizième module d'une chaîne de seize (trois gros modules en aval), mort constatée par le client, second démon : seule la queue recompile (3 à 16 tâches, jamais tout), build sans changement : zéro tâche, édition du module 0 : seul lui recompile (≤ 2 tâches, le jar change), kill -9 au repos puis retour : zéro re-travail | **Fait** |
| Preuve : OOM forcé | `DaemonDeathResumeTest.forcedOomKillsDaemonClientSurvivesAndDiskResumes` : tas réellement saturé, machine virtuelle morte (sortie non nulle), client survivant, nouveau démon : tout à jour, zéro re-travail ; l'injection non armée est refusée par le démon sain | **Fait** |
| Preuve : kill -9 | ci-dessus — en plein vol et au repos, les deux | **Fait** |
| Preuve : tas ≤ 700 Mo au tier L | `daemonBench` tier L : 200 modules, 30 000 fichiers, 400 tâches, **pic de tas 95 Mo** sous plafond de 700 Mo (le banc échoue au-delà) | **Fait** |
| Preuve : latence mesurée | `daemonBench` : salutation (lancement + poignée de main) 209 ms, ping médian 0 ms / max 49 ms (50 aller-retours), build sans changement 1,6 s (400 tâches consultées) | **Fait** |

## 3. Détail des tests ajoutés en P6 (14 cas)

| Test | Ce qu'il prouve |
|---|---|
| `ProtocolTest.framesRoundTrip` | cadres aller-retour intacts |
| `ProtocolTest.framesRejectOversized` | longueurs folles refusées en lecture (hors plafond, négatives) et écriture |
| `ProtocolTest.jsonEscapesAndNests` | échappements, imbrication, nombres et booléens |
| `ProtocolTest.nonAsciiSurvivesRoundTrip` | UTF-8 complet à travers les cadres |
| `ProtocolTest.buildSpecRoundTripKeepsModulesAsObjects` | régression du double encodage : les modules traversent comme objets, tous les champs lus |
| `ProtocolTest.moduleWithoutNameIsRefused` | description malformée refusée à l'analyse |
| `DaemonLifecycleTest.buildsRunAndSecondBuildKeepsCompilersHot` | build complet par IPC, jar produit, second build sans travail, fabrique non recréée |
| `DaemonLifecycleTest.inProcessModeRunsTheSameRunner` | le même exécuteur dans la machine virtuelle de l'appelant |
| `DaemonLifecycleTest.memoryBudgetAboveHeapIsRefusedExplicitly` | admission mémoire refusée, pas d'OOM silencieuse |
| `DaemonDeathResumeTest.killNineDuringBuildIsResumedFromDisk` | kill -9 en vol et au repos, reprise depuis le disque, sémantique d'ABI à travers le protocole (édition de corps : seul le module édité) |
| `DaemonDeathResumeTest.forcedOomKillsDaemonClientSurvivesAndDiskResumes` | OOM forcé, client survivant, reprise sans re-travail |
| `DaemonDeathResumeTest.faultInjectionIsRefusedWhenNotArmed` | le démon sain refuse l'injection et survit |
| `DaemonAndroidPipelineTest.androidBuildRunsThroughDaemonWithForkedR8` | pipeline Android complet par IPC, R8 en fork d'un gigaoctet, démon maigre, APK signé v1+v2 vérifié au niveau 23, AAB validé par bundletool, second build sans travail |

## 4. Écarts par rapport au prompt

| Écart | Détail | Décision |
|---|---|---|
| **Fils de travail démons** (défaut trouvé en phase) | des fils non démons zombifiaient le démon après OOM : processus vivant, plus personne pour lire | corrigé (ADR-038) — la boucle du démon est son seul fil de vie |
| **Ensembles sales vides** (défaut trouvé en phase) | le démon passait des ensembles vides au moteur : une édition après un build réussi était invisible (le harnais P3 documentait « comme le démon les fournira » — le démon ne les fournissait pas) | corrigé : diff disque contre l'instantané persisté du store (ADR-038) ; limite documentée au backlog (même taille + même grain d'horodatage, jusqu'au câblage de l'observateur) |
| **Double encodage des modules** (défaut trouvé en phase) | `toJson` produisait du texte pour les modules : le démon lisait un nom nul | corrigé + test de régression |
| **Répertoires à R8** (défectuosité latente P5, révélée par le test Android du démon) | le rétrécissement passait les répertoires de classes à R8 (refusés) — jamais exercé en P5 (minify absent de l'acceptation) | corrigé dans `AndroidTaskFactory` : le jar du module, même contenu, entrée de fichier |
| **Quota d'espace du démon** (backlog P5 le mentionnait pour P6) | le périmètre fermé de la feuille de route P6 ne le portait pas | évalué, points d'ancrage en place (`defaultCacheDir()` unique, cache de dex par espace), reporté au backlog P6/P7 — question au propriétaire |
| **Tier L du banc** | sources Java seules (le générateur de tools-bench mélange Java/Kotlin et ressources) : la preuve porte le tas et la latence, pas le mélange de langages | écart documenté ; comptes par module, couches et modules centraux identiques aux gabarits de référence |

## 5. Mesures de performance

Bac à sable 2 cœurs partagés — indicatif ; le verdissement sur la
machine de référence reste pour les phases concernées :

| Mesure | Obtenu |
|---|---|
| `daemonBench` tier S (5 modules, 500 fichiers) | build à froid 2,4 s, pic de tas 48 Mo, sans changement 46 ms |
| `daemonBench` tier M (50 modules, 5 000 fichiers) | pic de tas 62 Mo, sans changement 396 ms |
| `daemonBench` tier L (200 modules, 30 000 fichiers, 400 tâches) | génération 0,9 s ; build à froid **19,0 s**, **pic de tas 95 Mo ≤ 700 Mo** ; sans changement **1,6 s**, zéro tâche |
| Latence du protocole (tier L) | salutation 209 ms ; ping médian 0 ms, max 49 ms (50 aller-retours) |
| Pipeline Android par le démon (application + bibliothèque, minify, signature v1+v2, bundle) | build complet réussi, démon sous 700 Mo pendant le fork R8 d'un gigaoctet, second build zéro tâche |
| `./gradlew check` complet | **320 tests, 0 échec** (306 en P5) |

## 6. Dette et risques ouverts

- Diff par signaux sans observateur : une réécriture de même taille
  dans le même grain d'horodatage est invisible jusqu'au câblage de
  l'observateur (P8) — l'alternative (condensé de contenu de toute la
  racine à chaque build) est au backlog, à mesurer.
- Quota d'espace du démon (éviction du cache de dex et de
  l'outillage) : évalué, reporté — voir backlog P6/P7.
- Le démon exécute les builds séquentiellement (une requête à la
  fois) : l'annulation est traitée, la file de requêtes multiple
  n'est pas demandée avant l'IDE (P8 dira).
- La version du protocole (`version: 1` dans la salutation) est
  vérifiée nulle part : le client et le démon voyagent ensemble dans
  le dépôt ; la négociation d'hétérogénéité attendra un besoin réel.

## 7. Questions pour le propriétaire

1. Le quota d'espace du démon (éviction du cache de dex et de
   l'outillage téléchargé) : avant l'IDE (P8 — le démon le possède
   naturellement) ou avec l'import (P7 — le catalogue des espaces y
   sera connu) ?
2. La limite du diff par signaux (même taille, même grain
   d'horodatage) : acceptez-vous qu'elle vive jusqu'au câblage de
   l'observateur (P8), ou souhaitez-vous le condensé de contenu de
   la racine dès P7 (coût : un hachage de l'arbre à chaque build —
   au tier L, ~25 500 fichiers) ?
3. Les fils de construction du démon sont réglables
   (`-Djoide.daemon.threads`) : convenez-vous que l'IDE (P8) fixera
   la politique par appareil, le démon n'ayant pas d'avis ?

## 8. ADR ajoutés

- **ADR-038** — démon de build : protocole par cadres, admission
  mémoire (les revendications de fork portées au budget), reprise
  après mort (le store est la mémoire, diff disque des ensembles
  sales), fils démons, injection de pannes armée par propriété.

## 9. Reproduction

```bash
export JAVA_HOME=<jdk-21>
./gradlew check                          # 320 tests, 0 échec
./gradlew :build-daemon:test             # 14 cas, dont les morts du démon
./gradlew :build-daemon:daemonBench -Ptier=L   # preuve de tas et latence
```

Les tests Android du démon téléchargent l'outillage épinglé au
premier passage (cache `~/.cache/joide/toolchain` partagé avec P5) —
voir `docs/dev.md`, notes P6.
