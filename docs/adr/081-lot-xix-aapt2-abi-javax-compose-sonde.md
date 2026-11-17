# ADR-081 — Lot XIX : aapt2 conscient de l'ABI, l'API javax.* dans
# l'APK, et le compilateur Compose par sonde de classpath

- **Statut** : Accepted (le terrain du 2026-11-17, troisième vague :
  « aapt2 failed: …/aapt2[74]: syntax error: unexpected '(' » sur
  `app:processAndroidResources`, « NoClassDefFoundError: Failed
  resolution of: Ljavax/lang/model/SourceVersion; » sur
  `app:generateRJar` ; demande du propriétaire : attaque Compose)
- **Date** : 2026-11-17
- **Analyses expertes** : une source d'aapt2 Android vérifiée par
  téléchargement (ELF AArch64 statique, SHA-256 calculés) ; la
  recette CodeAssist pour R.jar en bytecode, le plugin Compose et
  javax.lang.model sur ART (rapports dans le fil de travail).

## Diagnostic (A) — aapt2 : le binaire linux sur un téléphone ARM64

`ensureAapt2` ne connaissait qu'un seul aapt2 : le jar `-linux` de
Google Maven (ELF x86_64). Le lot U embarquait déjà `libaapt2.so`
(jniLibs, ReVanced, 16 Ko aligné) et `locate()` le préfère — mais le
repli téléchargeait TOUJOURS le jar linux : sur ARM64, execve rend
ENOEXEC, bionic (execvp) retombe sur le shell, qui parse l'ELF comme
un script : « syntax error: unexpected '(' ». Le nouvel APK
reconstruit ne portait visiblement pas (ou plus) le `.so` embarqué —
le repli est donc tombé sur le mauvais binaire.

**Correctif** : `ToolchainLocator` devient conscient de l'ABI —
`varianteAapt2(osArch, appareil)` : `android-arm64-v8a`
(téléphone ARM64), `android-x86_64` (émulateur), `linux` (bureau, et
ABI Android inconnue). Sur appareil, le repli télécharge le binaire
ANDROID de ReVanced v1.1.0 — LA MÊME SOURCE que les jniLibs, ELF
statique bionic, segments alignés 16 Ko, épinglé taille + SHA-256
(vérifiés par téléchargement dans le sandbox) — posé nu au cache
(pas d'archive), exécutable. `cachedAapt2` regarde la variante
d'abord et ne rend le chemin linux qu'au bureau. Le binaire
embarqué (`aapt2Embarque`, nativeLibraryDir) reste le premier choix.

## Diagnostic (B) — javax.lang.model : absent d'ART, requis par ecj

ecj 3.40 référence `javax/lang/model/SourceVersion` au chargement du
batch (batch/FileSystem ; 53 classes d'ecj touchent javax/lang/model
— vérifié par l'expert sur le bytecode). ART ne porte AUCUNE de ces
API. **CodeAssist ne l'évite pas : il la fournit** — un jar API
committé (`java-compiler.jar`, 156 classes) dexé dans l'APK, car
« les classes App peuvent vivre dans javax.* ».

**Correctif** : le même mécanisme — `app-android/libs/
java-compiler.jar` (144 classes `javax.lang.model` +
`javax.annotation.processing` + `javax.tools`, extraites du JDK 21
par `scripts/jar-javax-api.sh`), câblé `implementation(files(...))`
dans app-android : dexé dans l'APK, ecj charge (R.jar ET, à
l'avenir, compileJava des modules Java sur l'appareil). Le bureau ne
change pas (le JDK fournit déjà tout).

## Chantier — Compose : la sonde + le branchement du plugin

La recette CodeAssist (ComposeCompilerPlugin.kt), transposée :

1. **Sonde de classpath** (`PluginsKotlin.composeDemande`) :
   `androidx/compose/runtime/Composable.class` cherché dans les jars
   (ZipFile) ET les répertoires du classpath de la demande —
   AUCUN réglage utilisateur ; les projets non-Compose ne paient
   rien (test dédié).
2. **Zéro option `-P`** pour Compose (idem serialization/parcelize
   chez CodeAssist) : `KotlinArgs` gagne le paramètre
   `pluginJars` → `-Xplugin <jar>` (avant -classpath, ordre stable).
3. **Le jar du plugin** : `org.jetbrains.kotlin:
   kotlin-compose-compiler-plugin:2.3.0` (la version du compilateur
   du pack — le pairing standard, non-relocalisé). Deux sources :
   le PACK (ressource `jars/kotlin-compose-compiler-plugin-<v>.jar`,
   à côté de la stdlib du lot XVI) sur l'appareil ; les RESSOURCES
   de build-jvm au bureau (copié au montage depuis le catalogue,
   jamais committé, extrait vers le cache du tmpdir avec mouvement
   atomique). Sonde allumée et jar introuvable = ÉCHEC NOMMÉ (le
   message d'un build sans plugin serait incompréhensible).
4. **Le registrar dexé là où kotlinc charge** : sur l'appareil, la
   VM ouvrière charge ses classes du pack — le plugin est donc DEXÉ
   dans le pack (`jarsKotlin(libs.compose.compiler.plugin)`, passage
   par le patcheur ART comme tout le pack) ; sur ART, un jar ne
   définit pas de classes, le jar ne sert qu'au descripteur
   META-INF/services (l'expertise CodeAssist, ComposeCompilerPlugin
   .kt:25-29).

## Conséquences

- Un APK sans `libaapt2.so` embarqué s'auto-répare : le premier
  build Android télécharge l'aapt2 ARM64 épinglé (4,7 Mo, UNE fois).
- ecj charge sur ART (R.jar d'abord ; les modules Java ensuite).
- Les projets dont le classpath porte Compose compilent avec le
  plugin — reste à câbler (hors lot) : la résolution des AAR
  androidx.compose (extraction classes.jar — déjà notée en écart
  honnête) et leurs dépendances transitives.
- Le pack Kotlin change (plugin dexé + jar en ressource) : taille et
  SHA-256 épinglés MIS À JOUR dans PacksOutils ; le nouveau pack est
  à publier sur la release toolchain-v1.
- Suites build-jvm et build-android vertes ; cinq tests nouveaux
  (sonde jar/répertoire, jar du pack, sonde éteinte, -Xplugin sans
  -P ; variante aapt2).
