# PHASE P7 — RAPPORT

- **Date** : 2026-11-15
- **Base** : P6 validée (`66915eb`)
- **Module rempli** : `import-gradle` (paquets `jo.joide.imports.gradle`
  et `jo.joide.imports.gradle.script`) + évolutions additives de
  `core-model` (tables en ligne du parseur TOML, `canonicalForm`
  public), du plugin de conventions (passage des propriétés
  `joide.export.*` aux tests) et des scripts de vérification

## 1. Ce qui est fait

L'import/export Gradle : lire un projet Gradle sans jamais l'exécuter,
en rendre un modèle JoIDE honnête — tout ce qui manque est dit — et
savoir le réécrire comme un projet Gradle qui se construit pour de
vrai.

- **Parseur statique et tolérant (ADR-039)** — un lecteur lexical
  (cinq styles de chaînes, commentaires, identifiants) et un parseur
  par blocs à accolades couvrent les deux dialectes (Groovy et Kotlin
  DSL) : appels parenthésés ou nus, arguments nommés Groovy, virgules
  finales Kotlin, paramètres génériques (`listOf<String>()`), chaînes
  d'appels (`libs.versions.x.get().toInt()` garde sa structure),
  affectations et `+=`. Jamais une exception — testé par ordures et
  par fuzz (cinq cents entrées aléatoires) ; une ceinture
  défensive attrape même une exception interne. Les frontières
  d'analyse s'arrêtent aux virgules et parenthèses fermantes : une
  anomalie n'avale jamais la suite du fichier (leçon apprise du
  projet réel, §4).
- **Lecteurs** — `gradle.properties` (continuations de ligne à la
  `java.util.Properties`), catalogue `libs.versions.toml` (versions,
  bibliothèques par module/groupe/nom, références de version, versions
  riches `strictly`/`require`/`ref`, greffons, alias pointillés
  `libs.foo.bar` → `foo-bar`), `settings.gradle(.kts)` (includes,
  nom racine, blocs de contexte rapportés), `build.gradle(.kts)`
  (greffons avec versions et `apply false`, bloc `android` complet —
  identité, `defaultConfig`, `buildTypes`, `productFlavors`,
  `buildFeatures`, désucrage —, dépendances, `ext` aplatie, ensembles
  de sources au premier niveau **et** dans le bloc android). Les
  valeurs android acceptent les références de version du catalogue
  (`compileSdk = libs.versions.compileSdk.get().toInt()`), la
  signature du monde réel. L'interpolation `$var`/`${expr}` résout
  contre ext, propriétés et versions du catalogue ; l'irrésolu est
  rapporté.
- **Import + SyncReport (ADR-040)** — `GradleImporter` rend l'espace
  de travail JoIDE et le rapport exhaustif : chaque dépendance hors
  modèle (ksp, kapt, tests instrumentés, plateformes BOM, fichiers
  locaux, alias sans version, classifieurs), chaque bloc non mappé,
  chaque valeur non littérale y figure avec gravité, code stable et
  localisation. Confiance par module à seuils fixes : HIGH sans
  aucun problème, MEDIUM avec des avertissements (≤ 5, aucune
  erreur), LOW au-delà. L'import ne répare rien : un projet sans
  include devient un module racine explicitement signalé, un build
  racine qui applique des greffons est une perte rapportée (ses
  variables `ext` nourrissent encore l'interpolation).
- **Export (ADR-041)** — `GradleExporter` rend tous les fichiers en
  mémoire, déterministes (octets identiques d'un export à l'autre),
  puis les écrit : `settings.gradle.kts` (dépôts `google()` +
  `mavenCentral()` quand il faut, `pluginManagement`), un
  `build.gradle.kts` par module (greffons **avec versions** portés
  par la facette `gradle`, bloc android, ensembles de sources
  explicites dès qu'ils diffèrent des défauts, dépendances à l'ordre
  du modèle avec configurations à variantes
  `freeDebugImplementation`), `gradle.properties`
  (`android.useAndroidX`), et `EXPORT-NOTES.md` : chaque donnée sans
  équivalent fidèle y est listée — jamais une perte silencieuse.
- **Sémantique AGP 9 apprise du réel** — la vérification à vrai
  Gradle a révélé qu'AGP 9 intègre Kotlin et refuse le greffon
  `org.jetbrains.kotlin.android`, et que Kotlin 2.0 exige le greffon
  du compilateur Compose. L'import et l'export traduisent ces faits
  (support Kotlin implicite sous AGP ≥ 9, avertissement
  `compose-compiler-plugin-missing`, export du compilateur Compose
  par la facette `gradle`) et l'aller-retour reste l'identité.
- **Échappatoire conçue (ADR-042)** — le build Gradle déporté
  (machine/CI, hors appareil) est conçu : l'appareil exporte, la
  machine exécute un Gradle complet, les artefacts reviennent ; le
  chemin `SYNC` renvoie les erreurs d'évaluation que l'import
  statique ne peut pas deviner. Hors noyau, interfaces en P8.

## 2. Critères d'acceptation P7, un par un

| Critère (§7 P7) | Preuve | Statut |
|---|---|---|
| Import de ≥ 5 projets réels/échantillons (au moins un multi-modules avec catalogue et un Kotlin + Compose) : modèle correct ou SyncReport explicite | cinq échantillons sous `src/test/resources/gradle-samples/` importés par `GradleImporterTest` : `jvm-groovy` (Groovy, interpolation par propriétés), `multi-catalog` (trois modules, catalogue complet, AGP 9, buildTypes), `compose-app` (Kotlin + Compose, BOM, `debugImplementation`), `flavors` (parfums, désucrage, sourceSets Groovy, dépendances à parfums), `poisoned` (constructions non mappées, SyncReport exhaustif, confiance LOW) ; **plus un projet réel** : `android/architecture-samples`@ee66e15 (2025-07-17) importé — deux modules corrects (types, namespaces, `compile-sdk=35`/`min-sdk=21` résolus du catalogue, compose, 30 + 11 dépendances), 48 problèmes listés, chacun réel (ksp, androidTest, BOM, variable locale) | **Fait** |
| Aller-retour import/export ≡ modèle | `GradleExporterTest.roundTripIsIdentityOnRepresentableModel` : modèle riche (application android avec variantes/parfums/chemins imbriqués `:core:network`, dépendances à variantes debug/free, tests variantés, racines de sources non standard) exporté puis réimporté — égalité des identifiants de contenu module par module, du nom et du réglage `android.min-sdk` ; `roundTripOfImportedSamplesIsIdentity` : l'échantillon multi-modules importé, exporté, réimporté — identité, zéro erreur | **Fait** |
| L'export se construit dans un vrai Gradle (vérifié manuellement ou par script hors appareil) | `scripts/verify-export-gradle.sh` lance la distribution Gradle 9.8.2 du wrapper sur les exports : **JVM** — `compileJava` + `jar` verts, sources réelles ajoutées (une dépendance externe Gson résolue, une dépendance de module croisée), jars produits ; **Android** — `assembleDebug` vert sur l'export de l'échantillon multi-modules importé : AGP 9.3.3 résolu et appliqué, Kotlin intégré, compilateur Compose, SDK trouvé, **APK de debug de 4 Mo produit** (quatre fichiers dex) | **Fait** |
| Aucun script de build exécuté pendant l'import (test) | double preuve : comportementale — `GradleImporterTest.poisonedProjectNeverCrashesAndReportsEverything` importe des scripts contenant `System.exit(42/43/44)`, écritures de marqueurs, `Runtime.exec`, `apply from` : l'import réussit, le module est lu, aucun marqueur n'existe, le processus vit ; structurelle — `NoExecutionDisciplineTest` scanne les sources principales et rejette toute API d'exécution (`ProcessBuilder`, `Runtime`, `System.exit`, `javax.script`, `GroovyShell`, `groovy.lang`, `Class.forName`, `URLClassLoader`, `ScriptEngine`, `.exec(`) et toute dépendance aux runtimes Gradle/Groovy | **Fait** |

## 3. Détail des tests ajoutés en P7 (35 cas, module import-gradle)

| Test | Ce qu'il prouve |
|---|---|
| `ScriptParserTest.groovyCallWithStringsAndMapArgs` | arguments nommés Groovy, appels à plusieurs arguments |
| `ScriptParserTest.kotlinBlocksAndAssignments` | blocs imbriqués Kotlin, `alias(…) apply false`, `minSdk 24` et `minSdk = 24` |
| `ScriptParserTest.dependenciesWithClosuresAndChains` | notations de dépendances, `tasks.register("clean") { }` |
| `ScriptParserTest.extAssignmentsAndInterpolationKeptRaw` | `ext` bloc et pointé, `def`, interpolation conservée brute |
| `ScriptParserTest.garbageNeverThrows` / `fuzzBytesNeverThrow` | ordures et fuzz (500 entrées) : jamais d'exception |
| `ScriptParserTest.sourceSetsGroovyAndKotlinForms` | `srcDirs +=`, affectations de listes, `getByName("main")` |
| `ScriptParserTest.buildTypesAndFlavorsNestedBlocks` | `create("free") { }` et `free { }`, `isMinifyEnabled` |
| `ScriptParserTest.commentsAndStringsAreUnderstood` | accolades et guillemets dans commentaires et chaînes |
| `ScriptParserTest.tripleQuotedStringsSurvive` | chaînes multi-lignes |
| `ScriptParserTest.pluginShortNamesAndApplyFalse` | greffons courts Groovy (`java`, `library`) |
| `ReadersTest` (9 cas) | propriétés (commentaires, séparateurs, continuations), catalogue (références, versions riches, alias pointillés, ordures), réglages (Groovy/Kotlin, includeFlat rapporté), interpolation (résolue, `$` échappé, irrésolu rapporté) |
| `GradleImporterTest` (7 cas) | les cinq échantillons (modèle attendu + entrées de rapport attendues, poison sans exécution), module racine unique, répertoire absent |
| `NoExecutionDisciplineTest` (2 cas) | aucune API d'exécution dans les sources, aucune dépendance Gradle/Groovy |
| `GradleExporterTest` (5 cas) | déterminisme octet par octet, aller-retour ≡ modèle (modèle riche et échantillon importé), EXPORT-NOTES (facette et rôle sans équivalent), formes DSL (configurations à variantes, `create("free")`, `setSrcDirs`) |
| `ExportRealGradleTest` (2 cas, armés par propriétés) | vrais Gradle : JVM `compileJava`+`jar`, Android `assembleDebug` (APK exigé) |

## 4. Écarts par rapport au prompt

| Écart | Détail | Décision |
|---|---|---|
| **Défauts trouvés en phase par le projet réel** | l'import initial de `architecture-samples` rendait le module `:app` sans dépendances : une virgule finale Kotlin (`setOf(…, )`) détraquait la fermeture d'arguments et le rétablissement avalait la fin du fichier (dont le bloc `dependencies`) ; `libs.versions.compileSdk.get().toInt()` n'était pas résoluble | corrigés tous les deux : frontières d'analyse aux virgules/parenthèses, virgules finales reconnues, chaînes d'appels structurées, références de version du catalogue résolues dans les valeurs android — le projet réel sert maintenant de preuve (30 dépendances, réglages résolus) |
| **AGP 9 et Kotlin 2 : sémantiques imposées par le réel** | l'export Android a échoué dans le vrai Gradle tant qu'il écrivait le greffon `org.jetbrains.kotlin.android` (refusé par AGP 9) et tant que le compilateur Compose manquait (exigé par Kotlin 2.0) | traduits dans l'import/export (ADR-041, point 5) ; les échantillons suivent le monde AGP 9 ; couple AGP 9.3.3 / Gradle 9.8.2 vérifié constructible |
| **Plateformes BOM non résolues** | `implementation(platform(libs.compose.bom))` et les alias gérés par BOM (sans version au catalogue) sont rapportés puis ignorés — la résolution exigerait le résolveur P4 en lecture seule (réseau) | choix d'honnêteté : l'import est hors-ligne, la perte est au SyncReport (`dependency-platform`, `dependency-catalog-unversioned`) ; résolution proposée au backlog, décision au propriétaire |
| **kapt/ksp, tests instrumentés non modélisés** | configurations `ksp`, `kspTest`, `androidTest*` hors modèle (pas de portées pour le traitement d'annotations ni les tests instrumentés) | rapportées à chaque occurrence (`dependency-scope-unknown`) ; représentation future au backlog |
| **Variables locales des scripts** | `val composeBom = platform(...)` puis `implementation(composeBom)` : la référence locale n'est pas résolue statiquement | rapportée (`dependency-bare-identifier`) ; évaluation partielle pensable, au backlog |
| **Records Java refusés par le contrôle API 28** | les seize types porteurs de valeurs avaient été écrits en records ; le contrôle API 28 les a rejeter à juste titre (`java/lang/Record` n'existe pas au niveau 28, le désucrage restant à vérifier — backlog ADR-009/013) : JoIDE est le premier module à en employer dans les sources principales | convertis en classes finales immuables à accesseurs, conforme à la convention du dépôt (comme `ExecutionRecord` de core-tasks) ; le contrôle est repassé vert — il a fait son travail |
| **Modules multi-plateformes** | un module KMP est importé avec le type `kotlin-multiplatform` (le modèle est extensible) et un avertissement : le pipeline ne le construit pas | honnête, documenté, pas de perte silencieuse |
| **`WorkspaceLoader` plat vs chemins imbriqués** | l'import produit des chemins `:core:ui` en mémoire ; le lecteur TOML JoIDE (`joide.toml`) n'accepte que des includes plats — l'import ne sérialise pas en TOML JoIDE | documenté : l'espace importé vit en mémoire (P8 le consommera) ; l'extension du dialecte TOML est une question de propriétaire |
| **Vérification à vrai Gradle : réseau de la machine de développement** | Maven Central bridait la machine (429) pendant les téléchargements | l'init-script `scripts/export-verification-init.gradle.kts` fait passer la vérification par un miroir — contournement documenté, l'export lui-même déclare `mavenCentral()` fidèlement |
| **Cas pathologique AGP ≥ 9 + greffon Kotlin android** | un projet qui déclarerait les deux (refusé par Gradle) est importé mais son aller-retour n'est pas l'identité (le greffon incompatible disparaît à l'export) | documenté (ADR-041) : un tel projet ne se construit pas sous Gradle ; la perte est dans l'ordre des choses |

## 5. Mesures

Bac à sable 2 cœurs partagés — indicatif :

| Mesure | Obtenu |
|---|---|
| Import `jvm-groovy` (1 module, Groovy) | 1 ms (meilleur de 5) |
| Import `multi-catalog` (3 modules + catalogue) | 3 ms |
| Import `compose-app` / `flavors` / `poisoned` | 1 ms chacun |
| Import `architecture-samples` (projet réel, 2 modules, ~40 alias) | 3 ms |
| Rapport du projet réel | 48 problèmes (0 erreur, 38 avertissements, 10 informations) ; confiance `:app` LOW (36 avertissements), `:shared-test` MEDIUM |
| Vérification JVM à vrai Gradle | `compileJava` + `jar` verts, deux jars produits |
| Vérification Android à vrai Gradle | `assembleDebug` vert (1 min 49 s, 61 tâches), APK de debug 4 Mo (4 dex) |
| Tests du module | 35 cas, 0 échec |
| `./gradlew check` complet | **355 tests, 0 échec** (320 en P6) |

## 6. Dette et risques ouverts

- Voir `docs/backlog.md`, bloc « après P7 » : résolution des BOM,
  kapt/ksp et tests instrumentés, variables locales, quota d'espace
  (question P6 reprise), implémentation de l'échappatoire déportée
  (ADR-042) en P8.
- L'import couvre le sous-ensemble des formes que les projets réels
  emploient ; chaque forme hors sous-ensemble est rapportée — le
  risque n'est pas la mauvaise lecture mais la lecture incomplète,
  toujours visible au SyncReport.
- `WorkspaceLoader` (TOML plat) ne sérialise pas les espaces importés
  à chemins imbriqués : l'import rend un modèle en mémoire, la
  sérialisation est une question de propriétaire (P8).

## 7. Questions au propriétaire

1. **Résolution des BOM à l'import** : autoriser l'import à consulter
   le résolveur P4 (réseau, lecture seule, lockfile) pour épingler
   les versions gérées par BOM — ou rester hors-ligne et rapporter ?
2. **kapt/ksp et tests instrumentés** : ajouter au modèle des portées
   dédiées (`ANNOTATION_PROCESSOR`, `INSTRUMENTED_TEST_*`) pour
   l'import et l'aller-retour, ou laisser au SyncReport ?
3. **Quota d'espace du démon** (question P6) : avant l'IDE (P8) ou
   après ?
4. **Dialecte TOML JoIDE** : étendre `joide.toml` aux includes
   imbriqués (`:core:ui`) pour sérialiser les espaces importés, ou
   garder les espaces importés en mémoire jusqu'à P8 ?

## 8. Arrêt pour validation

Conformément à la feuille de route (règle de passage 2) :
`./gradlew check` vert sur tous les modules, ADR-039 à 042, CHANGES,
backlog mis à jour, présent rapport. **Arrêt pour validation du
propriétaire avant P8.**
