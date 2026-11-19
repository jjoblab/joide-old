# ADR-082 — Lot XX : la refonte « tout embarqué » (la manière
# CodeAssist, audit à l'appui) — et la règle de l'immutabilité

- **Statut** : Accepted (question du propriétaire : « Est-ce que
  CodeAssist utilise tous ces opérations et modifications dans son
  approche ? » ; rappel de sa décision en session antérieure :
  refonte totale pour reproduire CodeAssist)
- **Date** : 2026-11-17
- **Terrain déclencheur** : « compilateur Kotlin différé
  indisponible : pack size mismatch for kotlin : 20989461 octets,
  attendus 22198577 » — ma faute du lot XIX : j'ai MUTÉ un artefact
  publié (le pack Kotlin de la release toolchain-v1) en y ajoutant
  le plugin Compose, sans que le fichier de la release fût remplacé.
  L'appareil vérifie la taille épinglée : divergence = échec nommé.

## Ce que l'audit de CodeAssist établit (2026-11-17)

Réponse à la question : **non, CodeAssist ne fait aucune de ces
opérations** — citations dans son dépôt :

1. **Aucun téléchargement d'outillage à l'exécution**, décision
   explicite : « Build-tools and command-line tools are not offered:
   on-device the build uses bundled tools (aapt2, in-process
   D8/apksig) » (`AndroidSdkInstaller.kt:26-27`). Compilateur
   Kotlin, D8/R8, apksig, aapt2, ecj, plugins Compose et stdlib sont
   tous embarqués dans l'APK à la construction. Seuls téléchargements
   runtime : les plateformes SDK (SDK Manager), le src.zip JDK
   (bureau, docs), les dépendances UTILISATEURS (fonctionnement
   normal d'un IDE) et le store de projets.
2. **Aucun épinglage taille/SHA sur l'outillage** : épinglage par
   coordonnée Maven seulement ; la seule vérification d'un binaire
   fetché à la construction (aapt2) est magie ELF + ≥ 100 Ko + un
   marqueur de tag — une vérification de PLAUSIBILITÉ, pas un pin
   (`build.gradle.kts:953-958`).
3. **Aucune re-publication** : le dépôt ne re-héberge rien — il
   consomme le `-for-ide` officiel de JetBrains et les releases
   ReVanced ; les binaires fetchés sont COMMITTÉS (jniLibs
   libaapt2.so 4 ABI, android.jar 27,8 Mo).
4. **Le compilateur Kotlin est dexé par AGP dans l'APK**, avec les
   passes ART branchées en instrumentation ASM de construction
   (`KotlincArtPlugin.kt:23-33`) ; la VM forkée prend pour classpath
   **l'APK de l'application lui-même** (`ForkedKotlinCompiler.kt:67-76`),
   persistante par défaut, repli in-process.
5. **Coût assumé** : APK ~180 Mo (~130-188 Mo de dex) contre
   l'APK allégé de joide (~30 Mo) + packs différés.

## La règle de l'immutabilité (leçon du terrain)

Un artefact PUBLIÉ ne change JAMAIS. Tout ajout vit dans l'APK ou
dans un NOUVEAU fichier versionné (le nom du pack porte déjà sa
version : `kotlin-compiler-2.3.0.dex.zip` — monter Kotlin = NOUVEAU
nom = addition, jamais remplacement). Le lot XIX a violé cette
règle ; le lot XX la rétablit et la grave ici.

## Le lot XX : le pack redevient immuable, Compose vit dans l'APK

1. **Revert de la mutation** : la recette du pack reprend son
   contenu exact du lot XVI (sans Compose) et les empreintes
   anciennes (20 989 461 / `9a9f48fc…`) ; le regen doit reproduire
   le SHA du fichier PUBLIÉ à l'octet près (vérifié) — l'appareil
   se remet à marcher SANS aucune action sur la release.
2. **Compose dans l'APK** (la première pierre de la refonte) :
   - CLASSES du plugin : `implementation(libs.compose.compiler.plugin)`
     dans app-android — dexées par AGP dans l'APK (933 Ko de jar,
     seule la stdlib comme transitive, déjà embarquée) ;
   - JAR ORIGINAL : asset `toolchain/kotlin-compose-compiler-plugin.jar`
     (tâche `stagerPluginCompose`, jamais committé — même règle que
     le jniLibs) ;
   - l'app extrait l'asset UNE fois et pose `joide.compose.plugin.jar`
     (`ProprietesConstruction`, UNE vérité pour les deux processus) ;
   - le CLASSPATH DE L'OUVRIÈRE porte l'APK (`joide.app.apk`) — le
     registrar se charge du dex de l'APK, le pack reste en tête
     (ses classes de compilateur gagnent) — EXACTEMENT la manière
     CodeAssist ;
   - `homeOuvriere` extrait les ressources de la PREMIÈRE entrée
     `.dex.zip` du classpath (l'APK ajouté ne casse plus l'extraction) ;
   - bureau inchangé : la ressource build-jvm du lot XIX reste le
     repli du pilote embarqué.
3. **Coût APK** : ~1,5-2 Mo (dex du plugin + jar asset) — bien
   en dessous du pas de plus du compilateur entier.

## La refonte « tout embarqué » — par étapes (le programme)

Le propriétaire a tranché : reproduire CodeAssist. Les étapes, dans
l'ordre du risque croissant, chacune laissant les suites vertes :

| Étape | Contenu | Coût APK | Ce qui disparaît |
|---|---|---|---|
| S1 (fait, lot XX) | Compose dans l'APK (classes dexées + jar asset + APK au classpath du fork) | ~2 Mo | la mutation du pack Compose |
| S2 | r8 + bundletool + guava dans l'APK (dexés par AGP — le pack ne les dexait déjà que par D8) | ~14 Mo | 2 packs + leurs épinglages |
| S3 | le compilateur Kotlin dans l'APK : le patcheur ART (lot XI) branché en tâche Gradle AVANT le dexage AGP (la manière CodeAssist `transformClassesWith`), stdlib jar en asset extrait (CodeAssist `BundledKotlinStdlib`), ressources kotlinc en asset zip | ~45 Mo | le pack Kotlin + le home ouvrière |
| S4 | android.jar + keystore debug en assets (android.jar strip de ses res/ — CodeAssist le fait : 27,8 → ~11 Mo) | ~11 Mo | le téléchargement de plateforme pour le niveau embarqué |
| S5 | supprimer PacksOutils/ToolchainLocator (hors aapt2 android et plateformes additionnelles) — l'APK EST l'outillage | — | TOUTE la mécanique de téléchargement |

À la fin : zéro téléchargement d'outillage, zéro épinglage à
re-publier, zéro danse de release — un build Android sur appareil
qui marche dès l'installation, comme CodeAssist. Les packs restants
(plateformes autres que celle embarquée) deviennent des téléchargements
UTILISATEURS du SDK Manager, pas de l'outillage.

## Conséquences

- Le terrain du « pack size mismatch » ne peut plus se produire :
  le pack Kotlin de la release n'a plus jamais à changer.
- Les suites build-jvm/build-android restent vertes ; deux tests
  nouveaux (propriété d'abord ; extraction multi-entrées).
- La release GitHub ne porte PLUS que des fichiers immuables.
