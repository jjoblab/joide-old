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
