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
