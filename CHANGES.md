# CHANGES — Analyse IA de CodeAssist (ADR-É9) et plan des lots (ADR-É10)

Le propriétaire rouvre le sujet IA écarté par l'ADR-É1 et change la
méthode (2026-11-16) : « Étendre l'analyse pour les fonctionnalités de
AI (par contre pour l'IA tu dois regarder le code). Tu peux commencer
à planifier les lots. » L'ADR-É9 analyse le code de l'agent de
CodeAssist — 67 fichiers, ≈ 18 700 lignes lus (agent-api, agent-impl,
agent-mcp, agent-ui, ide-core/agent, service Android, doc de
conception, tests) — boucle d'agent, économie des tokens (prompt
caching, compaction), six fournisseurs LLM, robustesse à trois étages,
34 outils + port d'espace de travail, permissions sur diff, MCP dans
les deux sens, UI du chat, service de premier plan, évaluations sans
LLM ; verdict : adopter les principes, pas le code (17 leçons
retenues, 11 risques à ne pas reproduire). L'ADR-É10 propose le plan
des lots d'implémentation : UI v2 (G-M, ADR-É1 à É8) et agent IA
(N-R, ADR-É9), jalons de validation par groupes, module pur agent dans
le graphe, neuf questions D-1 à D-9 au propriétaire avant GO. Aucun
code n'est implémenté ; l'arrêt pour validation du propriétaire
s'applique.

## Ajoutés

| Zone | Fichiers |
|---|---|
| docs/ecrans | `009-analyse-ia-codeassist.md` (ADR-É9 : analyse du code IA de CodeAssist — méthode, cartographie, leçons, risques, verdict) ; `010-plan-lots.md` (ADR-É10 : plan des lots G-R, principes de découpage, jalons, questions D-1 à D-9) |

## Modifiés

| Zone | Fichiers | Nature du changement |
|---|---|---|
| docs/ecrans | `001-analyse-codeassist.md` | famille IA du tableau : verdict « écarter » révisé (références ADR-É9/É10, réouverture par le propriétaire) ; note de révision dans « Ce que JoIDE ne prend pas » |
| docs/ecrans | `README.md` | lignes 009/010 dans l'index ; statut : révision du 2026-11-16, plan proposé au propriétaire |
| Racine | `ROADMAP.md` | ligne V2 (UI v2 + agent IA : en planification, ADR-É10) ; règle 3 du journal clean-room actualisée (deux consultations mandatées consignées) |
| docs/clean-room | `JOURNAL.md` | les deux consultations CodeAssist (écrans puis agent IA, 2026-11-16) consignées avec fichiers, notes produites et impact — mandats du propriétaire, notes sans code |
| Racine | `CHANGES.md` | le présent fichier ; typographie corrigée au passage sur les entrées existantes (espaces insécables) |

## Supprimés

Rien.

# CHANGES — Écrans v2 : analyse CodeAssist, ADR-É1 à É8, maquettes HTML

Après le premier test du propriétaire (2026-11-16) — l'UI P8 est
fonctionnelle mais minimale (SAF unique, aucune création, apparence
brute du cadre) — la série `docs/ecrans` définit l'architecture des
écrans v2 AVANT implémentation : analyse des écrans de CodeAssist
(tyron12233), huit ADR (système de design « Graphite » + un par
écran), maquettes HTML autonomes (preview principal + un par écran).
Aucune ligne de code n'est reprise de CodeAssist (GPL-3.0, lecture
seule, clean-room ADR-008) ; le code P8 n'est pas touché ; aucune
implémentation n'est engagée sans validation du propriétaire.

## Ajoutés

| Zone | Fichiers |
|---|---|
| docs/ecrans | `README.md` (index de la série) ; `001-analyse-codeassist.md` (inventaire complet des écrans de CodeAssist par famille, verdicts, six leçons avec preuves dans le source) ; `002-systeme-design.md` (palette Graphite, typographie à deux familles, composants, implémentation en vues du cadre sans AndroidX, états purs JVM par écran) ; `003-accueil-mes-projets.md` ; `004-creation-projet.md` ; `005-atelier.md` ; `006-console-build.md` ; `007-apercu-mise-en-page.md` ; `008-parametres.md` |
| docs/ecrans/design | `index.html` (preview principal : parcours, galerie des six écrans, jetons, principes de franchise) ; `accueil-projets.html` (peuplé + première ouverture) ; `creation-projet.html` (trois étapes, franchise du modèle sur la carte) ; `atelier.html` (compact, tiroir arborescence, grand écran amarré) ; `console.html` (échoué avec problème cliquable + en cours) ; `apercu.html` (nominal + pendant la frappe) ; `parametres.html` — fichiers autonomes, aucune ressource externe, jetons ADR-É2 textuels |
| scripts (hors dépôt) | capture et audit Playwright des maquettes (débordements, troncatures, coupures) — outils de revue de la série, pas des contrôles du dépôt |

## Modifiés

| Zone | Fichiers | Nature du changement |
|---|---|---|
| Racine | `CHANGES.md` | le présent fichier |

## Supprimés

Rien.

# CHANGES — CI des APK signés (validation P8)

Le propriétaire teste l'application pour la première fois : le
workflow GitHub Actions « APK signés » produit debug et release signés
par la même clé CI stable — chaque génération s'installe sur la
précédente, jamais de désinstallation (exigence du 2026-11-16).
ADR-047.

## Ajoutés

| Zone | Fichiers |
|---|---|
| CI | `.github/workflows/apk.yml` (JDK 21, SDK 36+37.2, publication des briques en Maven Local aux refs épinglées puis cache `~/.m2`, signature par secrets, vérification `apksigner`, nettoyage des artefacts précédents, dépôt debug+release nommés au SHA court) |
| app-android | signature CI conditionnelle dans `build.gradle.kts` (variables `JOIDE_CI_KEYSTORE`, `JOIDE_CI_KEYSTORE_PASSWORD`, `JOIDE_CI_KEY_ALIAS` — sans elles, comportement local inchangé : debug sur keystore local, release non signée) |
| docs | `docs/adr/047-ci-apk-signes.md`, section « CI — APK signés » de `docs/dev.md` (secrets, sauvegarde de la clé, montée de version des briques, équivalent local) |

## Modifiés

| Zone | Fichiers | Nature du changement |
|---|---|---|
| Racine | `CHANGES.md` | le présent fichier |

## Supprimés

Rien.

# CHANGES — P8 (sur la base P7)

L'IDE arrive en six lots : A (conditionnel Android et squelette
d'application), B (adaptateur ProjetLsp v11), C (moteurs de langage
code-lsp), D (éditeur celeris), E (aperçu LayoutLib), F (application
complète : service de build de premier plan, UI IDE, APK).

## Ajoutés

| Zone | Fichiers |
|---|---|
| ide-integration (lots B–E) | `sync` (LanguageModelSync, ProjetLspAdapter, LanguageModelSnapshot, ExternalResolution, Resolutions, ModuleDirectories, AndroidPlatform), `langages` (MoteursLangage, DiagnosticIde, SurvolIde, PropositionCompletion, CibleNavigation, AideSignatureIde, EtatIndex, SessionsKotlin, UnionIndexable, AdaptateurIndexNoms), `editeur` (SceneEditeur, SessionLangageCeleris, CoachJoide, Langages), `apercu` (ApercuLayouts, ConfigurationApercu, ConfigurationsApercu) |
| build-daemon (serveur, lot F) | `BoucleDemon` (boucle de protocole extraite du démon de bureau — un seul protocole), `HoteDemonSocket` (hôte socket locale sur le rebouclage, un BuildRunner pour toutes les connexions — compilateurs chauds à travers les reconnexions, la mort de l'hôte ferme la connexion courante : fin de canal, jamais un zombie) |
| app-android (lot A) | `MainActivity` (état vide fonctionnel, sélecteur système), `ProjectDetector`, `RootNames` |
| app-android (lot F) | `MainActivity` réécrite (écran IDE : import, montage des moteurs, éditeur, aperçu, console de build), `BuildService` (service de premier plan du processus `:build`, port éphémère publié par fichier atomique), `StoreAndroid` (contrat BuildStore sur la base système, schéma du store de bureau, éviction LRU pure testée), `SpecsBuild` (modèle → description de build P6, écarts honnêtes), `ConsoleBuild` (formatage pur des événements), `FichiersProjet` (parcours, filtre, mises en page), `ImporteurProjet` (copie SAF → stockage privé) |
| build-logic | conventions `joide.android-library-conventions`, `joide.android-application-conventions` (AGP 9.5.0, javac strict, lint bloquant, JUnit 5, règle de dépendances) |
| outillage | `.githooks/pre-push` (vérification d'identité exigée par le propriétaire : user.name=jjoblab, user.email=olson12jb@gmail.com, auteur ET compositeur de chaque commit poussé ; activation par `git config core.hooksPath .githooks`) |
| docs | `docs/integration/` (contrats réels des briques celeris/code-lsp/LayoutLib, DEMANDES.md), ADR-043 à ADR-046, `PHASE-P8-RAPPORT.md` |

## Modifiés

| Zone | Fichiers | Nature du changement |
|---|---|---|
| build-daemon | `DaemonClient` | factory `over(InputStream, OutputStream)` (transport par flux, ADR-043) ; `DaemonMain` délègue à `BoucleDemon` — aucun protocole dupliqué |
| ide-integration | `LanguageModelSync` | `derniereResolution()` : le build réutilise les jars de la synchronisation (additif, lot F) |
| core-store | `StoreException` | constructeur à un argument (additif, lot F) |
| Gradle | `settings.gradle.kts`, `gradle/module-graph.txt`, catalogue | conditionnel SDK (ADR-045), arêtes ide-integration et app-android, briques en Maven Local |
| app-android | `AndroidManifest.xml`, `res/values/strings.xml` | service `:build` de premier plan, permissions INTERNET/FOREGROUND_SERVICE, chaînes réelles |
| docs | `docs/dev.md` | notes P8 : SDK, publication des briques (procédure promise par ADR-044), mémoire du bac à sable |
| docs | `docs/backlog.md`, `ROADMAP.md`, `NOTICE`, `docs/third-party.md` | P8 : dettes listées, P8 livrée, briques redistribuées consignées |
| Racine | `CHANGES.md` | le présent fichier |

## Supprimés

Rien.

# CHANGES — P7 (sur la base P6)

## Ajoutés

| Zone | Fichiers |
|---|---|
| import-gradle (parseur) | `script/ScriptToken`, `script/ScriptLexer` (lecteur lexical tolérant, cinq styles de chaînes, commentaires), `script/ScriptExpr` (expressions en forme tolérante, scellées), `script/ScriptStmt` (appels, affectations, inconnues), `script/Script` (instructions ordonnées, problèmes), `script/ScriptParser` (blocs à accolades, dialectes Groovy et Kotlin, virgules finales, génériques, chaînes d'appels, jamais d'exception — ADR-039) |
| import-gradle (lecteurs) | `GradleProperties` (paires, continuations de ligne comme java.util.Properties), `VersionCatalog` (catalogue libs.versions.toml : versions, bibliothèques avec références et versions riches, greffons, alias pointillés), `GradleSettings` (includes, nom racine, blocs rapportés), `GradleBuildFile` (greffons avec versions et alias, bloc android : identité, defaultConfig, buildTypes, productFlavors, buildFeatures, désucrage, ensembles de sources au premier niveau et dans android, dépendances, ext aplatie, valeurs par référence de version du catalogue), `Interpolation` (résolution $var et ${expr} contre les portées chaînées) |
| import-gradle (import) | `GradleImporter` (orchestrateur : réglages, propriétés, catalogue, module racine ou inclus, types, sources, dépendances à portées et variantes, facettes android/kotlin/gradle, min-sdk d'espace), `SyncReport` (rapport exhaustif, rendu hiérarchisé), `ModuleConfidence` (HIGH/MEDIUM/LOW à seuils fixes — ADR-040) |
| import-gradle (export) | `GradleExporter` (export déterministe en mémoire : réglages avec dépôts, build.gradle.kts par module, greffons avec versions, bloc android, ensembles de sources explicites, dépendances à l'ordre du modèle, EXPORT-NOTES.md — ADR-041) |
| import-gradle (tests) | `script/ScriptParserTest` (11 cas : deux dialectes, ordures, fuzz 500 entrées), `ReadersTest` (9 cas : propriétés, catalogue avec tables en ligne, réglages, interpolation), `GradleImporterTest` (7 cas : cinq échantillons — Groovy simple, multi-modules avec catalogue, Kotlin+Compose, parfoms et variantes, projet empoisonné —, module racine unique, répertoire absent), `NoExecutionDisciplineTest` (scan des API d'exécution interdites, absence de dépendance aux runtimes Gradle et Groovy), `GradleExporterTest` (déterminisme, aller-retour ≡ modèle sur modèle riche et sur échantillon importé, EXPORT-NOTES, formes DSL), `ExportRealGradleTest` (vrais Gradle sur les exports : JVM compileJava+jar, Android assembleDebug — armé par propriétés, lancé par script) |
| import-gradle (fixtures) | cinq projets Gradle échantillons sous `src/test/resources/gradle-samples/` : `jvm-groovy` (Groovy, interpolation), `multi-catalog` (multi-modules Kotlin DSL, catalogue, parfum des deux dialectes, AGP 9), `compose-app` (Kotlin+Compose, BOM, variantes debug), `flavors` (Groovy, parfums, désucrage, sourceSets), `poisoned` (constructions non mappées + poison d'exécution : System.exit, marqueurs, exec — jamais exécutés) |
| Scripts | `scripts/verify-export-gradle.sh` (lance la distribution Gradle du wrapper sur les exports, JVM et Android), `scripts/export-verification-init.gradle.kts` (miroir de Central pour la machine de développement, contournement documenté) |
| docs | `docs/adr/039-parseur-gradle-statique.md`, `docs/adr/040-syncreport-confiance.md`, `docs/adr/041-export-gradle-aller-retour.md`, `docs/adr/042-build-gradle-deporte.md` |
| Racine | `PHASE-P7-RAPPORT.md` |

## Modifiés

| Zone | Fichiers | Nature du changement |
|---|---|---|
| core-model | `toml/Toml` | tables en ligne `{ cle = valeur }` lues (avec clefs pointées et tolérance) — requis par les catalogues de versions Gradle ; additif, aucun lecteur existant n'en dépendait (ADR-039, point 6) |
| core-model | `Module` | `canonicalForm()` public — l'aller-retour de l'export compare les identifiants de contenu et diagnostique par forme canonique (additif) |
| build-logic | `joide.java-conventions.gradle.kts` | les propriétés `joide.export.*` de la ligne de commande traversent jusqu'à la JVM de test (armement des vérifications à vrai Gradle) |
| Scripts | `scripts/check-language-policy` | le dépouillement code/chaînes reconnaît les blocs de texte Java (`"""`) : sans quoi leur impair de guillemets inversait l'état et faisait apparaître des contenus de chaînes comme du code (défaut révélé par les sources de vérification générées en P7) |
| import-gradle | `build.gradle.kts`, `README.md` | dépendances réelles (core-model, core-deps) ; contenu P7 |
| docs | `ROADMAP.md` | P6 validée, P7 livrée |
| docs | `docs/backlog.md` | dettes P7 ouvertes, entrées héritées fermées |
| Racine | `CHANGES.md` | le présent fichier |

## Supprimés

Rien.

# CHANGES — P1 (sur la base P0)

# Historique P0


Tous les fichiers sont AJOUTÉS (première livraison du dépôt) ; rien de
modifié, rien de supprimé.

| Zone | Fichiers |
|---|---|
| Racine | `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`, `LICENSE`, `NOTICE`, `README.md`, `CHANGES.md` |
| Gradle | `gradle/libs.versions.toml`, `gradle/module-graph.txt`, `gradle/api28-signatures.txt.gz`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` |
| build-logic | `settings.gradle.kts`, `convention/build.gradle.kts`, 2 conventions précompilées (`joide.java-conventions`, `joide.java-library-conventions`), `ModuleGraph`, `DependencyRuleCheck`, `ClassFileReferences`, `Api28ScanTask` |
| Modules (14) | `build.gradle.kts` + `README.md` + `package-info.java` chacun (`core-platform`, `core-store`, `core-vfs`, `core-model`, `core-tasks`, `core-deps`, `build-jvm`, `build-android`, `build-daemon`, `import-gradle`, `app-android`, `ide-integration`) ; `tools-bench` et `tools-test` en plus de leurs squelettes livrent le générateur, le harnais et les tests |
| tools-bench | `TierSpec`, `ModuleGraphPlanner`, `TypeRegistry`, `JavaWriter`, `KotlinWriter`, `ResourceWriter`, `TomlWriter`, `TreeHasher`, `HeapProbe`, `BenchMetrics`, `ProjectGenerator`, `BenchMain`, `BaselineCompare` + tests (`ReproducibilityTest`, `ModuleGraphPlannerTest`) + `baselines/s.toml`, `baselines/m.toml` |
| tools-test | `LanguagePolicyScriptTest`, `ModuleGraphFileTest`, `DependencyRuleKitTest`, `Api28KitTest` + fixtures (`fixtures/language-violation/**`, `fixtures/language-clean/**`) |
| Scripts | `scripts/check-language-policy` (adapté de celeris), `scripts/generate_api28_signatures.py` |
| docs | `docs/adr/001…016-*.md`, `docs/toolchain.md`, `docs/third-party.md`, `docs/dev.md`, `docs/backlog.md`, `docs/clean-room/JOURNAL.md` |


---

# CHANGES — P1

## Ajoutés

| Zone | Fichiers |
|---|---|
| core-platform (code) | `Hash128`, `Hashing`, `RootName`, `RootedPath`, `RootSet`, `Severity`, `Problem`, `Result`, `CancelToken`, `Clock`, `WorkerPools`, `AtomicFiles` |
| core-platform (tests) | `HashingTest`, `Hash128 inclus`, `RootedPathTest`, `RootSetTest`, `CancelTokenTest`, `AtomicFilesTest`, `PlatformSupportTest` |
| core-store (code principal) | `BuildStore`, `FileStamp`, `StoreWork`, `StoreException` |
| core-store (source set jvm) | `SqliteStores`, `SqliteBuildStore`, `Migrations`, `Migration` |
| core-store (tests) | `SqliteBuildStoreTest`, harnais `JvmSpawn`, `StoreCrashChild`, `StoreCrashAcceptanceTest` |
| core-vfs (code) | `FileChange`, `DirtySet`, `VfsSnapshot`, `MerkleTrees`, `TreeScanner`, `StableHasher`, `SnapshotUpdater`, `ChangeLog`, `TreeWatcher`, `NioTreeWatcher` |
| core-vfs (tests) | `TreeScannerTest`, `MerkleTreesTest`, `ChangeLogTest`, `SnapshotUpdaterTest`, `NioTreeWatcherTest`, `ColdScanTest`, fixture `MemoryStore` |
| core-model (code) | `toml/Toml`, `toml/TomlDocument`, `toml/TomlValue`, `toml/TomlProblem`, `ContentRole`, `ModuleType`, `DependencyScope`, `DependencyRef`, `ContentRoot`, `SourceSet`, `Facet`, `Module`, `Project`, `Workspace`, `ModelException`, `ModuleTopology`, `ClasspathEntry`, `Classpath`, `ClasspathBuilder`, `WorkspaceLoad`, `WorkspaceLoader`, `ModuleWriter`, `WorkspaceEdit`, `WorkspaceRepository` |
| core-model (tests) | `toml/TomlTest`, `toml/TomlFuzzTest`, `WorkspaceLoaderTest`, `ModuleTopologyTest`, `ClasspathBuilderTest`, `WorkspaceRepositoryTest`, `ThousandModulesTest` |
| tools-bench (test) | `SyntheticWorkspaceTest` (validation croisée) |
| docs | `docs/adr/017-empreinte-128-murmur3.md`, `docs/adr/018-parseur-toml-maison.md`, `docs/adr/019-store-jdbc-source-set.md`, `docs/adr/020-harnais-pannes-aretes-test.md`, `PHASE-P1-RAPPORT.md` |

## Modifiés

| Zone | Fichiers | Nature du changement |
|---|---|---|
| Historique git | les 8 commits P0 | auteur réécrit vers `jjoblab <olson12jb@gmail.com>` (demande du propriétaire) |
| Tous les modules | 56 fichiers Java/Kotlin/properties | commentaires et javadocs débarrassés des références ADR/décisions/phases/sections et des dates |
| tools-bench | `TomlWriter`, `ProjectGenerator` | schéma canonique (tableaux standard, `[[sources]]`, `[facets.android]`, `[[dependencies]]`) |
| tools-bench | `baselines/s.toml`, `baselines/m.toml` | régénérées (nouveaux fichiers de projet générés) |
| tools-bench, tools-test | `build.gradle.kts` | arête de test `tools-bench -> core-model` |
| Gradle | `gradle/module-graph.txt` | arête de test commentée ; `tools-test/src/test/.../ModuleGraphFileTest.java` attendus mis à jour |
| Gradle | `gradle/libs.versions.toml` | sqlite-jdbc 3.49.1.0 |
| build-logic | `joide.java-conventions.gradle.kts` | classpath d'exécution des tests transmis aux machines virtuelles filles (propriété posée à l'exécution, compatible cache de configuration) |
| Racine et modules livrés | `README.md` de core-platform, core-store, core-vfs, core-model ; `package-info.java` correspondants | contenu réel P1 |
| docs | `NOTICE`, `docs/third-party.md`, `docs/dev.md` | sqlite-jdbc consigné ; notes P1 |
| Racine | `CHANGES.md` | le présent fichier |

## Supprimés

Rien.

---

# CHANGES — P2 (sur la base P1)

## Ajoutés

| Zone | Fichiers |
|---|---|
| core-tasks (API de tâche) | `TaskInput`, `TaskOutput`, `TaskInputs`, `TaskOutputs`, `InputDelta`, `ResourceClaim`, `TaskExecution`, `TaskDefinition`, `TaskContext`, `TaskStatus`, `TaskTimings`, `TaskResult`, `BuildEvents` |
| core-tasks (graphe) | `TaskGraph`, `TaskGraphException`, `PathTrie` |
| core-tasks (ordonnanceur) | `TaskScheduler`, `TaskSchedulingException`, `ResourceBudget`, `SchedulingMode`, `CriticalPaths` |
| core-tasks (moteur) | `BuildEngine`, `ExecutionRecord`, `LastBuildState`, `TaskFingerprinter`, `OutputHasher`, `OutputCache`, `HeapSampler` |
| core-tasks (observabilité) | `BuildReport`, `BuildScanWriter` |
| core-tasks (tests) | `TestingTasks`, `TurbulentExecutor`, `EngineMemoryStore` (avec `Guard` de traçage), `TaskGraphTest`, `CriticalPathsTest`, `SchedulerPropertyTest`, `NoBarrierTest`, `MemoryAdmissionTest`, `BuildEngineTest`, `HotNoChangeTest`, `OutputCacheTest`, `ExecutionRecordTest` |
| core-store (jvm) | `JournalBuildStore` (prototype alternatif, ADR-021) |
| core-store (tests) | `JournalBuildStoreTest`, `StoreBenchmarkTest` |
| docs | `docs/adr/021-store-alternatif-journal.md`, `docs/adr/022-ordonnanceur-file-de-pretes.md`, `docs/adr/023-decision-a-jour-et-passe-a-chaud.md`, `docs/adr/024-cache-de-sorties-adresse-par-contenu.md` |
| Racine | `PHASE-P2-RAPPORT.md` |

## Modifiés

| Zone | Fichiers | Nature du changement |
|---|---|---|
| core-store | `BuildStore` | interface étendue : `listCacheEntries()` ; `evictCacheBeyond` retourne les entrées évincées (et non plus une somme) pour permettre la suppression des fichiers (ADR-024) |
| core-store | `SqliteBuildStore` | implémentation des deux méthodes étendues |
| core-store | `CacheEntry` (nouveau, main) | entrée d'index du cache de sorties |
| core-store | `SqliteBuildStoreTest` | suivi de l'interface ; suppression de sommeils superflus |
| core-vfs | `MemoryStore` (fixture) | suivi de l'interface étendue |
| core-tasks | `build.gradle.kts` | dépendances réelles (core-store, core-vfs, core-model) ; le module quitte l'état de squelette |
| core-tasks | `README.md`, `package-info.java` | contenu réel P2 |
| docs | `docs/backlog.md` | fermeture des entrées P2 (empreinte 128 bits tranchée par ADR-017 en P1 ; store alternatif tranché par ADR-021) |
| docs | `docs/dev.md` | notes P2 |
| Racine | `CHANGES.md` | le présent fichier |

## Supprimés

Rien.


---

# CHANGES — P3 (sur la base P2)

## Ajoutés

| Zone | Fichiers |
|---|---|
| build-jvm (classfile) | `ClassFileException`, `ClassFileReader`, `ClassModel` (pool complet, attributs d'ABI, `@kotlin.Metadata`, condensé `fileDigest`) |
| build-jvm (abi) | `AbiCache`, `AbiManifest`, `ClassAbi` (drapeau inline, `isInlineFlagged`), `EntryAbi`, `KotlinFacts` |
| build-jvm (graph) | `CompileState`, `ImpactAnalysis`, `SourceHeaderScanner` |
| build-jvm (compile) | `CompileRequest`, `CompileResult`, `CompilerDriver`, `EcjCompilerDriver`, `KotlinCompilerDriver` |
| build-jvm (tasks) | `ClasspathEntry`, `GenerateSourcesTask`, `IncrementalJvmCompile` (ClasspathDiff, BatchOutcome), `JarTask`, `JvmCompileTask`, `JvmRunTask`, `JvmTaskFactory`, `ProcessResourcesTask`, `SourceGenerator` |
| build-jvm (tests) | `ClassFileReaderTest`, `AbiTest`, `GraphTest`, `CompilerDriverTest`, `PipelineHarness`, `MemoryBuildStore`, `PipelineAcceptanceTest`, `DifferentialTest` (200 séquences à graine fixe), `InlineBodyAbiTest` (régression corps inline) |
| core-tasks (tests) | `NestedDirInputTest`, `IntraBuildChainTest` (régressions du correctif `bc1670b`, omises au commit d'origine) |
| docs | `docs/adr/025-abi-membres-inline.md`, `docs/adr/026-choix-ecj.md`, `docs/adr/027-spike-kotlin-buildtools.md` |
| Racine | `PHASE-P3-RAPPORT.md` |

## Modifiés

| Zone | Fichiers | Nature du changement |
|---|---|---|
| core-tasks | `BuildEngine`, `TaskFingerprinter` | trois défauts de correction révélés par le pipeline JVM (entrées de dossier imbriquées, empreintes des propriétés scalaires et hash symétrique des entrées couvrant des sorties, delta d'entrées développant les dossiers) |
| build-jvm | `SourceHeaderScanner` | suivi des besoins du pipeline |
| build-jvm | `AbiTest` | suivi du drapeau inline dans les manifestes |
| docs | `docs/toolchain.md` | ligne P3 fermée (ADR-026) |
| docs | `docs/backlog.md` | spike Kotlin tranché (ADR-027) ; dettes P3 ouvertes listées |
| docs | `docs/dev.md` | notes P3 |
| Racine | `.gitignore` | `/build-logic/convention/build/` ancré |
| Racine | `CHANGES.md` | le présent fichier |

## Supprimés

Rien.


---

# CHANGES — P4 (sur la base P3)

## Ajoutés

| Zone | Fichiers |
|---|---|
| core-deps (coordonnées) | `Coordinate`, `Version`, `VersionRange`, `VersionRequirement`, `Exposure`, `DependencyRequest`, `Checksums` |
| core-deps (métadonnées) | `xml/Xml`, `xml/XmlNode`, `json/Json`, `json/JsonValue`, `pom/PomModel`, `pom/PomParser`, `gmm/GmmModule`, `gmm/GmmParser`, `repo/MavenMetadata`, `repo/MavenMetadataParser` |
| core-deps (dépôts) | `repo/Layout`, `repo/Transport`, `repo/TransportResponse`, `repo/HttpTransport`, `repo/Repository` (`Local`, `Remote`) |
| core-deps (cache) | `cache/DependencyCache` |
| core-deps (lockfile) | `lock/EdgeKind`, `lock/LockfileEdge`, `lock/LockfileEntry`, `lock/Lockfile` |
| core-deps (résolution) | `resolve/ResolvedArtifact`, `resolve/ResolutionResult`, `resolve/ResolverConfig`, `resolve/Resolver` |
| core-deps (AAR) | `aar/AarContents`, `aar/AarExtractor` |
| core-deps (tests) | `VersionTest`, `VersionRangeTest`, `CoordinateTest`, `xml/XmlTest`, `json/JsonTest`, `pom/PomParserTest`, `gmm/GmmParserTest`, `repo/MavenMetadataParserTest`, `repo/HttpTransportTest`, `resolve/RepoFixture`, `resolve/Artifacts`, `resolve/ResolverTest`, `resolve/LockfileDeterminismTest`, `resolve/ChecksumDivergenceTest`, `resolve/SnapshotFreezeTest`, `resolve/OfflineStrictTest`, `resolve/ParallelCapTest`, `lock/LockfileTest`, `cache/DependencyCacheTest`, `aar/AarExtractorTest`, `repo/FakeTransport` |
| docs | `docs/adr/028-parseurs-xml-json-maison.md`, `docs/adr/029-ordre-versions.md`, `docs/adr/030-cache-adresse-par-contenu.md`, `docs/adr/031-transport-https.md`, `docs/adr/032-extraction-aar-zip-slip.md`, `docs/adr/033-lockfile-joide-lock.md` |
| Racine | `PHASE-P4-RAPPORT.md` |

## Modifiés

| Zone | Fichiers | Nature du changement |
|---|---|---|
| core-deps | `build.gradle.kts` | dépendance réelle (`core-platform`) ; le module quitte l'état de squelette |
| core-deps | `README.md`, `src/main/java/jo/joide/deps/package-info.java` | contenu réel P4 |
| docs | `docs/toolchain.md` | ligne P4 fermée (transport HTTPS vérifié TLS) |
| docs | `docs/backlog.md` | dettes P4 ouvertes listées |
| docs | `docs/dev.md` | notes P4 |
| Racine | `CHANGES.md` | le présent fichier |

## Supprimés

Rien.


---

# CHANGES — P5 (sur la base P4)

## Ajoutés

| Zone | Fichiers |
|---|---|
| build-android (toolchain) | `SafeZip`, `Toolchain`, `ToolchainLocator` (épinglage SHA-256, transport HTTPS du résolveur) |
| build-android (aapt2) | `Aapt2` (pilotage processus externe, compilation par fichier et par répertoire, lien binaire et proto, diagnostics) |
| build-android (variantes) | `AndroidVariant` |
| build-android (dex) | `DexInfo` (lecture d'en-tête), `DexBuckets` (répartition multidex), `Dexer` (interface de service), `ForkDexer` (fork à gros tas) |
| build-android (package) | `ApkWriter` (zip déterministe aligné, champ extra comme zipalign) |
| build-android (bundle/sign) | `BundleAssembler`, `ApkSignerService` (interfaces) |
| build-android (tasks) | `AndroidModuleSpec`, `Aapt2CompileTask` (incrémentale par fichier), `Aapt2LinkTask`, `RJarTask` (ecj + manifeste ABI stable), `JarWriter`, `DexScopeTask`, `MergeDexTask`, `PackageTask`, `SignTask`, `AabTask`, `R8Task`, `AndroidTaskFactory` (graphe complet par variante) |
| build-android (source set tools) | `InProcessDexer` (D8/R8 en processus, compilateurs chauds), `ApksigSigner`, `BundletoolAssembler` + enregistrements ServiceLoader |
| build-android (tests) | `SafeZipTest`, `DexBucketsTest`, `ApkWriterTest`, `Aapt2ToolTest`, `DexerToolTest` (dont désucrage des enregistrements), `AndroidPipelineTest` (acceptation complète : APK signé vérifié, AAB validé, incrémental proportionnel, incrémental ≡ propre), `SharedDexCacheTest`, `ToolchainFixture`, `AndroidHarness`, `MemoryBuildStore` |
| core-tasks (tests) | cas « entrée étrangère découverte sur le disque » dans `OutputCacheTest` |
| docs | `docs/adr/034…037-*.md`, `ROADMAP.md` |
| Racine | `PHASE-P5-RAPPORT.md` |

## Modifiés

| Zone | Fichiers | Nature du changement |
|---|---|---|
| core-tasks | `OutputCache` | découverte disque des entrées étrangères à l'ouverture (répertoire partitionné) : le cache de sorties est partageable entre projets aux stores distincts |
| build-android | `build.gradle.kts` | source set `tools` (adaptateurs d'outils, hors contrôle API 28), dépendances r8/apksig/bundletool/guava |
| Gradle | `gradle/module-graph.txt`, `tools-test` attentes | arête build-android -> core-deps (transport, empreintes) |
| Gradle | `gradle/libs.versions.toml` | r8 9.5.32, apksig 9.5.1, bundletool 1.18.3, guava 32.0.1-jre |
| docs | `NOTICE`, `docs/third-party.md` | arrivages P3 consignés (ecj EPL-2.0, compilateur Kotlin) et P5 (r8, apksig, bundletool, aapt2, plateforme) |
| docs | `docs/dev.md`, `docs/backlog.md` | notes P5 ; backlog mis à jour |
| Racine | `build-android/README.md` | contenu réel P5 |

## Supprimés

Rien.


---

# CHANGES — P6 (sur la base P5)

## Ajoutés

| Zone | Fichiers |
|---|---|
| build-daemon (protocol) | `Frames` (cadres de longueur plafonnés), `Json` (écriture compacte), `BuildSpec` (description de build : espaces, modules JVM/Android, variantes) |
| build-daemon (client) | `DaemonClient` (lancement de la machine virtuelle fille, file d'événements, mort du démon en exception, récolte du code de sortie) |
| build-daemon (runner) | `BuildRunner` (exécuteur équivalent en processus : graphe JVM ou Android, événements en direct, fabriques et dexeurs détenus — compilateurs chauds, diff disque de l'ensemble sale contre l'instantané persisté, fils démons) |
| build-daemon (racine) | `package-info.java` (invariants du paquet) |
| build-daemon (source set desktop) | `DaemonMain` (boucle de protocole, store SQLite persistant, préchauffage, injection de pannes armée par `-Djoide.daemon.faults=1`, fils de construction réglables) |
| build-daemon (source set bench) | `DaemonTier`, `BenchProjectGenerator` (générateur déterministe aux gabarits de tools-bench), `DaemonBench` (preuves de tas et de latence par tier) |
| build-daemon (tests) | `ProtocolTest` (cadres, échappements, aller-retour de description, module sans nom refusé), `DaemonLifecycleTest` (build complet par IPC, compilateurs chauds au second build, mode en processus, budget mémoire refusé explicitement), `DaemonDeathResumeTest` (kill -9 en plein build et au repos puis reprise depuis le disque, OOM forcé avec client survivant et reprise, injection refusée non armée), `DaemonAndroidPipelineTest` (pipeline Android complet par IPC : R8 en fork d'un gigaoctet, démon sous 700 Mo, APK signé v1+v2 vérifié, AAB validé, second build sans travail) |
| core-store | exposition du pilote SQLite par la configuration `jvmApi` (le démon ouvre le même contrat que les tests du store) |
| build-android | exposition des adaptateurs d'outils par la configuration `toolsApi` (D8/R8, apksig, bundletool en processus par ServiceLoader dans la machine virtuelle du démon) |
| docs | `docs/adr/038-demon-process-build.md` |
| Racine | `PHASE-P6-RAPPORT.md` |

## Modifiés

| Zone | Fichiers | Nature du changement |
|---|---|---|
| build-daemon | `build.gradle.kts` | source sets `desktop` (déjà), `bench` (nouveau) ; tâche `daemonBench` (preuves à la demande par tier) ; le contrôle « check » compile desktop et bench |
| build-daemon | `README.md` | contenu réel P6 |
| build-android | `AndroidTaskFactory` | défaut latent P5 corrigé : le rétrécissement R8 recevait des répertoires de classes (refusés par R8) — il reçoit le jar du module, même contenu, entrée de fichier ; dépendance au jar ajoutée |
| build-android | `ToolchainLocator` | `defaultCacheDir()` public (le démon et ses filles partagent le même répertoire d'outillage) |
| Gradle | `gradle/module-graph.txt`, `tools-test` attentes | arête build-daemon -> core-store (le démon de bureau ouvre le store persistant) |
| docs | `ROADMAP.md` | P5 validée, P6 livrée |
| docs | `docs/backlog.md` | dettes P6 ouvertes listées (évaluation du quota d'espace reportée) |
| docs | `docs/dev.md` | notes P6 |
| Racine | `CHANGES.md` | le présent fichier |

## Supprimés

Rien.
