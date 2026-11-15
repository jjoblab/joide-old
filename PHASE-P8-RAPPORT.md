# PHASE P8 — RAPPORT

- **Date** : 2026-11-16
- **Base** : P7 validée (`59618dc`)
- **Modules remplis** : `ide-integration` (paquets `jo.joide.ide.sync`,
  `jo.joide.ide.langages`, `jo.joide.ide.editeur`, `jo.joide.ide.apercu`)
  et `app-android` (`jo.joide.app.android` complet) + évolutions
  additives de `build-daemon` (serveur socket, factory `over`),
  `core-store` (constructeur de `StoreException`), `core-model`
  (aucune — le modèle P1 a suffi)

## 1. Ce qui est fait

L'IDE : une application Android qui ouvre un projet, y monte les
moteurs de langage, l'éditeur et l'aperçu de layouts des briques du
propriétaire — derrière des interfaces JoIDE uniquement — et y
construit, dans un service de premier plan isolé par processus.

La phase est arrivée en six lots : A (conditionnel Android, squelette
d'application, consommation des briques en binaire), B (adaptateur
ProjetLsp v11), C (moteurs de langage code-lsp), D (éditeur celeris),
E (aperçu LayoutLib), F (application complète). Les lots A à E sont
détaillés dans leurs commits (`1b0a7e4` à `0555452`) ; le présent
rapport clôt la phase et détaille le lot F.

### Lot F — l'application (ce lot)

- **Le service de build (ADR-043, réalisé)** — `BuildService`, service
  de premier plan du processus `:build` : il héberge `HoteDemonSocket`
  (nouveau dans build-daemon) — un serveur TCP sur l'adresse de
  rebouclage qui sert le protocole P6 au-dessus d'un `BuildRunner`
  possédé. La boucle de protocole est EXTRAITE du démon de bureau
  (`BoucleDemon`) : un seul protocole, deux transports (entrée
  standard au bureau, socket locale sur appareil). Le client gagne la
  factory `DaemonClient.over(InputStream, OutputStream)` : la mort du
  démon y est une fin de canal (`DaemonDiedException`), l'UI survit,
  le build suivant relance le service. Le port éphémère est publié
  par fichier atomique (`filesDir/demon-build.port`) ; une connexion
  à la fois, le runner — compilateurs chauds compris — survit aux
  reconnexions (prouvé par test : `hotCompilers` identique entre deux
  connexions successives).
- **Le store système (P1 : « l'implémentation Android du même contrat
  vivra avec le démon » — réalisé)** — `StoreAndroid` :
  `android.database.sqlite`, même schéma que le store de bureau (P1),
  journal écriture d'avance, transactions, éviction LRU (politique
  pure, testée sur la JVM). La reprise après mort du démon relit le
  disque sur l'appareil comme au bureau.
- **L'écran IDE (`MainActivity`)** — ouverture par le sélecteur
  système, détection (`ProjectDetector`, lot A), import par copie vers
  le stockage privé (`ImporteurProjet`, ADR-046 : les moteurs et le
  pipeline exigent de vrais chemins de fichiers), puis montage : la
  synchronisation (lot B) résout les externes par le résolveur P4
  (Maven Central, cache privé), les moteurs (lot C) tournent derrière
  l'éditeur celeris (lot D), l'aperçu (lot E) rend les mises en page
  du projet à chaque frappe (débouncé par la brique). L'écriture des
  fichiers part sur un fil dédié à fusion (le dernier texte gagne).
  Console de build : les écarts s'affichent AVANT le lancement
  (modules Android non construits sur appareil, dépendances non
  résolues, AAR), puis les événements du protocole en direct. Une case
  « en processus » bascule sur le runner dans la machine virtuelle de
  l'UI, annoncée dans la console (ADR-043 point 4, jamais silencieuse).
- **L'APK du lot F** — `:app-android:assembleDebug` : 88 Mo, minSdk 28
  (D4), service `:build` déclaré (`android:process=":build"`,
  non exporté), permissions INTERNET et FOREGROUND_SERVICE, toutes les
  classes du lot F vérifiées dans les dex (BuildService, StoreAndroid,
  BoucleDemon, HoteDemonSocket…). C'est l'APK promis par le lot E pour
  la preuve du rendu réel : le câblage complet y vit (l'aperçu
  configuré depuis le modèle de langage, rendu dans l'activité) ;
  l'exécution sur appareil est la validation du propriétaire.

## 2. Critères d'acceptation P8, un par un

| Critère (feuille de route) | Preuve | État |
|---|---|---|
| Application Android (UI) | `MainActivity` : écran IDE réel — import, moteurs, éditeur, aperçu, console ; état vide fonctionnel ; logique testable dans des classes pures (`ProjectDetector`, `FichiersProjet`, `SpecsBuild`, `ConsoleBuild`, `ImporteurProjet`) | **Fait** |
| Services de premier plan | `BuildService` (processus `:build`, notification persistante, ADR-043) ; `DaemonClient.over` + `HoteDemonSocket` + `BoucleDemon` ; la mort du démon = événement de flux (§6.7) | **Fait** |
| Intégration celeris derrière interfaces | lot D (`SceneEditeur`) — types du cadre uniquement | **Fait** (lot D) |
| Intégration code-lsp derrière interfaces | lots B (`LanguageModelSync`) et C (`MoteursLangage`) — CIDX réutilisé, SANS `:host` (ADR-044) | **Fait** (lots B, C) |
| Intégration LayoutLib derrière interfaces | lot E (`ApercuLayouts`) — livrée après déclaration D-1 du propriétaire | **Fait** (lot E) |
| Aucun type tiers ne fuit | `jo.joide.ide.*` et `jo.joide.app.*` publics uniquement ; briques en `implementation` (ADR-010/044) ; lint + règle de dépendances verts | **Fait** |
| `check` vert sans SDK | critère P0 intact : modules purs seuls, BUILD SUCCESSFUL (4 min 29 s) | **Fait** |
| `check` vert avec SDK | modules Android inclus (AGP 9.5.0, lint bloquant sans baseline, 2 travailleurs) | **Fait** |
| APK produit | `assembleDebug` : 88 Mo, structure vérifiée (manifeste, service, dex) | **Fait** |

## 3. Détail des tests (429 cas, 0 échec)

| Module | Cas | Contenu |
|---|---|---|
| core-platform, tools-bench, tools-test | 43 | inchangés (P0–P7) |
| core-store | 17 | inchangés |
| core-vfs / core-model | 59 | inchangés |
| core-tasks | 49 | inchangés |
| core-deps | 72 | inchangés |
| build-jvm / build-android | 67 | inchangés |
| build-daemon | 16 | + `HoteDemonSocketTest` (3) : build JVM complet sur socket réelle puis reconnexion à compilateurs chauds (hotCompilers identiques entre connexions) ; mort de l'hôte = `DaemonDiedException` chez le client qui survit (fin du fil de lecture attendue, `alive()` faux) ; build refusé pour budget mémoire sans mort du canal (refus motivé, ping/pong ensuite) |
| import-gradle | 35 | inchangés (P7) |
| ide-integration | 56 | lots B (16), C (16), D (13), E (11) — détaillés dans leurs commits |
| app-android | 15 | `ProjectDetectorTest` (lot A, 3) + lot F : `SpecsBuildTest` (3 — vrai workspace chargé du disque : sources relatives, jars des externes, refus Android/non résolu/AAR avec écarts), `ConsoleBuildTest` (3 — salutation, tâches, fin de build avec problèmes, refus), `FichiersProjetTest` (3 — parcours réel, filtre, exclusions, mises en page à toute profondeur), `StoreAndroidEvictionTest` (2 — éviction LRU pure) |

Les tests du démon socket font tourner le VRAI `BuildRunner` (build
ecj réel) à travers le VRAI transport socket — pas de transport
factice.

## 4. Écarts par rapport au prompt

1. **Builds Android sur appareil** : le pipeline Android exige
   D8/R8/aapt2, non embarqués dans l'APK (taille doublée, aucune
   demande) — les modules Android d'un projet sont listés en écarts
   de la console, jamais construits en silence (ADR-046). Décision
   de périmètre rapportée au propriétaire (§7).
2. **Projets Gradle dans l'application** : mode compatibilité
   (bannière du lot A) — l'application n'a pas accès à `import-gradle`
   (graphe ADR-011) ; l'échappatoire est l'export P7 (ADR-041/042).
3. **Import par copie** : l'accès direct SAF (provider de système de
   fichiers) exigerait un VFS parallèle non mesuré (ADR-046) ; la
   copie duplique le stockage — backlog.
4. **Exécution sur appareil** : le bac à sable de développement n'a
   pas d'émulateur (ni KVM) ; l'APK assemble et embarque tout le
   câblage (vérifié structurellement), l'exécution réelle — rendu,
   service, build — est la validation du propriétaire sur appareil.
   La chaîne protocolaire est prouvée sur la JVM par
   `HoteDemonSocketTest` (build réel à travers la socket).
5. **Lockfile sur appareil** : le résolveur y tourne sans lockfile
   (cache privé) — la reproductibilité s'en remet au cache, backlog.
6. **Rendu réel (report du lot E)** : `RenderService` exige un
   `Context` Android — prouvé côté brique par ses propres tests ; le
   câblage de l'activité est livré dans l'APK du lot F, l'exécution
   visible est la validation sur appareil (voir écart 4).

## 5. Mesures

| Mesure | Valeur |
|---|---|
| APK de débogage | 88 Mo (25 dex) — moteur JoIDE entier + trois briques |
| Tests | 429 exécutés, 0 échec (358 purs + 56 ide-integration + 15 app-android) |
| `check` sans SDK | BUILD SUCCESSFUL, 4 min 29 s (bac à sable à 4 Go, 2 travailleurs) |
| `check` avec SDK | BUILD SUCCESSFUL (modules Android : lint bloquant compris) |
| Mémoire du bac à sable | assembleDebug : démon Gradle réduit à 1024 Mo pour laisser le travailleur D8 vivre (pic système observé ~2,0 Go) ; un démon à 3 Go y est tué par le noyau (consigné dans docs/dev.md, notes P8) |
| Reconnexion du démon | `hotCompilers` identique entre deux connexions successives (le runner et ses compilateurs chauds survivent à la reconnexion — test) |

## 6. Dette et risques ouverts

Les entrées ouvertes sont dans `docs/backlog.md` (après P8) :
builds Android sur appareil (décision du propriétaire), accès direct
SAF, gestion du stockage des projets importés et lockfile sur
appareil, couverture protocole non servie par les moteurs (vides
honnêtes du lot D), rétrécissement R8 de l'application (règles de
conservation à écrire).

Risques : l'index CIDX et les sessions Kotlin consomment la mémoire
du processus UI (§6.8 règle 4 : les budgets des services de langage
vivent dans des process distincts — le démon y est, les moteurs pas
encore ; à mesurer sur appareil avant décision) ; le port du service
est publié par fichier (suffisant pour une UI unique, à revoir si un
jour plusieurs clients).

## 7. Questions au propriétaire

1. **D-3 (binaires JetBrains)** — l'APK embarque `java-syntax`
   (dépôt intellij-dependencies, JetBrains Open-Source Build Terms)
   via le moteur Java : la demande reste « en attente ». L'APK livré
   est un build de validation ; sa DISTRIBUTION (boutique, partage)
   exige la confirmation D-3 — ou son remplacement.
2. **D8/R8 dans l'APK** : embarquer le pipeline Android complet
   (~doubler la taille de l'APK, écrire les règles de conservation
   R8) pour construire des applications Android SUR l'appareil —
   oui, non, ou P9 ?
3. **Bascule en processus** : la case « en processus » convient-elle
   comme annonce (ADR-043 point 4) ou exigez-vous un réglage par
   appareil (mémoire disponible) ?
4. **Quota d'espace du démon** (question P6/P7 reprise) : le store
   système de l'appareil vit sans quota ; lui en poser un (et lequel)
   avant P9 ?

## 8. Arrêt pour validation

Conformément à la feuille de route (règle de passage 2) :
`./gradlew check` vert sur tous les modules (avec et sans SDK), ADR
043 à 046, CHANGES P8, backlog mis à jour, présent rapport, APK de
débogage assemblé et vérifié. **Arrêt pour validation du propriétaire
— P8 est la dernière phase de la feuille de route P0–P8.**
