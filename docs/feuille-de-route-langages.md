# Feuille de route langages — Java, Kotlin Compose, KMP sur
# l'appareil (la parité CodeAssist)

*Issue de l'analyse experte du moteur CodeAssist (2026-11-17,
rapport complet dans le fil de travail) et du diagnostic du moteur
joide (ADR-076 pour la route Android, ADR-080 pour le plateau
console). Chaque chantier cite la pièce CodeAssist à reproduire.*

## Où joide en est

| Capacité | État joide | La référence CodeAssist |
|---|---|---|
| Kotlin (JVM, appareil) | opérationnel : embarqué ou VM forkée, `-no-stdlib`/`-no-reflect`, `-no-jdk` + android.jar au classpath (routes Android et console, lots XIV et XVIII) | K2 in-process sur ART, mêmes drapeaux (KotlinJvmCompiler.kt) |
| Java (appareil) | opérationnel : ECJ tolère l'absence de jrt, android.jar au classpath, l'API javax.* (SourceVersion) dexée dans l'APK (lot XIX) | ECJ batch + `ImageFreeJavaCompiler` pour compliance ≥ 9 + le même jar API committé |
| Chaîne Android | opérationnelle jusqu'à l'APK signé et le bundle : aapt2 compile/link (manifeste enrichie, lot XVII), R.jar, dex, package, signature (lots U→XVII) ; aapt2 conscient de l'ABI (lot XIX : binaire Android épinglé sur appareil) | mergeResources → aapt2 → R.jar ASM → K2 → ecj → D8 → apksigner |
| Compose | **branché (lots XIX-XX)** : sonde `Composable.class` jars+dirs, `-Xplugin` zéro `-P`, plugin dans l'APK (lot XX : classes dexées + jar asset + APK au classpath de l'ouvrière) — reste la résolution des AAR androidx.compose | plugin `-Xplugin` + sonde de classpath (ComposeCompilerPlugin.kt) |
| Incrémental Kotlin | non : full recompile à chaque build | manifeste texte + ABI maison, PAS l'IC officielle |
| KMP | absent | compilation « collapsed » unique (commonMain + plateforme) |

## La refonte « tout embarqué » (décision du propriétaire, ADR-082)

L'audit CodeAssist (2026-11-17) établit que la référence n'a
AUCUNE mécanique de packs téléchargés : tout vit dans l'APK, la VM
forkée prend l'APK pour classpath. Le programme en cinq étapes est
posé dans l'ADR-082 — S1 (Compose dans l'APK) est fait au lot XX ;
le pack de la release redevient immuable (le « pack size mismatch »
du terrain ne peut plus se reproduire).

## Chantier 1 — le compilateur Compose (priorité haute) — FAIT au lot XIX

Le schéma CodeAssist, transposé tel quel (voir ADR-081) : la sonde
`androidx/compose/runtime/Composable.class` (jars ET répertoires),
`-Xplugin` sans AUCUNE option `-P`, le jar du plugin venant du pack
(appareil) ou des ressources de build-jvm (bureau), le registrar
DEXÉ dans le pack. **Le reste du chantier** : la résolution des
AAR androidx.compose (classes.jar — l'extraction AAR partielle de
SpecsBuild) et leurs transitives, sans lesquelles aucun projet
Compose réel n'a le runtime au classpath.

Le schéma de référence complet (gardé pour l'historique) :

1. **Le jar du plugin** (`compose-compiler-plugin-for-ide`, version
   VERROUILLÉE sur la version de Kotlin) est bundled comme
   ressource et EXTRAIT sur le disque — kotlinc exige un fichier jar
   pour lire `META-INF/services` (ComposeCompilerPlugin.kt:22-24,
   :59-76).
2. **Branchement** : `-Xplugin <jar>` et les options
   `-P plugin:androidx.compose.compiler.plugins.kotlin:…`
   (KotlinJvmCompiler.kt:111-113).
3. **Activation par sonde** : la présence de
   `androidx/compose/runtime/Composable.class` dans un jar ou dir du
   module déclenche le plugin — AUCUN réglage utilisateur
   (ComposeCompilerPlugin.kt:48, :83-92).
4. **Subtilité ART** : le jar ne sert qu'au descripteur ; le
   registrar (`ComposePluginRegistrar`) doit être DEXÉ dans l'APK
   hôte — kotlinc le résout par délégation parentale vers le
   classloader d'app, un `.class` de jar ne se définit pas au
   runtime ART (ComposeCompilerPlugin.kt:25-29).

Pour joide : la liste des plugins (`BUILTIN_KOTLIN_COMPILER_PLUGINS`
chez CodeAssist) devient une brique de `KotlinArgs` — Compose,
Serialization, Parcelize — avec la même sonde ; le registrar dans
l'APK hôte exige une publication de l'app dédiée (comme la
publication `-for-ide` de CodeAssist).

## Chantier 2 — l'incrémental Kotlin maison (priorité haute)

L'IC officielle (`-Xincremental`, lookups, caches mmap) casse sur
ART — CodeAssist le documente explicitement (« no compiler IC caches
which mmap on ART », IncrementalKotlinCompiler.kt:26-27) et joide a
DÉJÀ l'infrastructure : le moteur ABI/manifestes du JVM incrémental
(AbiCache, compile-state) est la même idée.

Le schéma à reproduire :
- manifeste texte `.ic/manifest.txt` : hash par source, mapping
  source→.class via `-Xreport-output-files`, hash de CONTEXTE
  (classpath, plugins, opt-ins) ;
- snapshot ABI par classe (surface publique + blob `@kotlin.Metadata`
  via `kotlin-metadata-jvm` — corps et privés exclus ; une fonction
  inline fait hasher le fichier entier, KotlinAbi.kt:30-59) ;
- fast path : recompiler les seuls fichiers modifiés, classes
  intactes fournies en binaire + `-Xfriend-paths` (préserve
  l'`internal`) ; toute divergence ABI ou de contexte → full
  recompile conservatif ; échec du path incrémental → full
  silencieux.

## Chantier 3 — KMP « collapsed » (priorité moyenne)

Pas de pipeline KMP complet (metadata compilation, cibles iOS) — la
compilation COLLAPSÉE suffit pour compiler un module KMP pour sa
cible Android/JVM (le choix assumé de CodeAssist,
ModuleFeatures.kt:8-34) :
- l'importeur collapse `commonMain` + `androidMain` (+ sets
  intermédiaires) en UNE compilation (GradleModelImporter.kt:270-283) ;
- kotlinc : `multiPlatform = true`, `expectActualClasses = true` et
  `-Xcommon-sources` avec les sources communes passées DEUX fois
  (en freeArgs ET en commonSources — faute de quoi « 'expect' and
  corresponding 'actual' are declared in the same module »,
  KotlinJvmCompiler.kt:162-172).

Pour joide : une facette `kmp` dans le modèle de modules (les
source-sets common/android) et les trois drapeaux dans `KotlinArgs`.

## Chantier 4 — Java 9+ robuste (priorité basse, gardé pour plus tard)

android.jar sur `-bootclasspath` est refusé par ecj à compliance ≥ 9
et le jimage d'ART n'existe pas : CodeAssist bascule sur le
compilateur interne ecj avec un `INameEnvironment` non-modulaire qui
résout java.* directement depuis les bytes de android.jar
(ImageFreeJavaCompiler.kt:74-105). joide est à compliance 17 via
android.jar en classpath simple — ça marche aujourd'hui ; le
`INameEnvironment` ne devient utile que si des constructions
modulaires (module-info) sont demandées.

## Chantier 5 — R.jar en bytecode ASM (optimisation)

CodeAssist émet les classes R en bytecode ASM directement (pas de
compilation ecj des R.java, RBytecodeGenerator.kt:16-31) et ne garde
le R.java que comme source de résolution pour kotlinc. joide compile
les R.java avec ECJ — correct mais coûteux sur de gros `--extra-packages`.
À replanifier quand les projets embarquent de nombreuses
bibliothèques AAR (la liaison aapt2 des AAR tiers, déjà notée en
écart honnête dans SpecsBuild.extraireAar).
