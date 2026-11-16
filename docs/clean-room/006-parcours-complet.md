# Note clean-room n° 6 — le parcours complet : OAuth d'interface, OpenRouter, le build embarqué

- **Date** : 2026-11-17
- **Mandat** : message du propriétaire du 2026-11-17 — « Oui pour le
  flux OAuth d'interface pour compléter le parcours MCP. Ajoute
  aussi openrouter dans le providers. Le emptyview quand aucun
  onglets n'est ouvert, le tiroir pour l'arborescence des fichiers
  du projet. J'ai appuyé sur build, ça dit que d8 et autre n'est
  pas embarque ». Le mandat de consultation CodeAssist du
  2026-11-16 reste le cadre ; la consultation ci-dessous y répond.

## Ce qui a été consulté (et pourquoi)

La documentation publique ne dit RIEN de trois points bloquants :
comment un IDE sur appareil exécute un aapt2 alors que Google ne
publie aucun binaire pour les ABI Android ; comment D8 tourne
sans JVM fille ; comment le rappel OAuth revient dans l'app.

Fichiers consultés (dépôt `tyron12233/CodeAssist`, clone du
2026-11-17) :

| Fichier | Ce qu'il a appris (comportement, jamais du code) |
|---|---|
| `app/ide-android/build.gradle.kts` (bloc on-device native aapt2) | le binaire aapt2 vit en `libaapt2.so` par ABI parce qu'ART n'exécute QUE depuis `nativeLibraryDir` ; la source publie des segments LOAD alignés 16 Ko (les binaires 4 Ko sont tués par SIGSEGV à l'exec sur appareil 16 Ko) ; le téléchargement de build vérifie la magie ELF et la taille |
| `android/android-support/.../tools/D8Dexer.kt` et le commentaire du build.gradle app | D8 est du pur Java : en PROCESSUS sur l'appareil, pas de fork — le fork à gros tas n'existe que là où une JVM fille existe |
| `android/android-support/.../tools/Aapt2Daemon.kt`, `AndroidSdkInstaller.kt` | l'aapt2 long-vivant parle son propre protocole stdin ; sur appareil « bundled tools (aapt2, in-process D8/apksig) » |

## La traduction JoIDE (clean-room)

- **aapt2 embarqué** : tâche Gradle `fetchAapt2` (URL épinglée,
  magie ELF vérifiée, marqueur de source), `jniLibs/<abi>/libaapt2.so`
  pour les quatre ABI, jamais au dépouilleur ;
  `ToolchainLocator.aapt2Embarque()` le trouve par la propriété
  `joide.native.dir` (posée par l'atelier depuis
  `applicationInfo.nativeLibraryDir`).
- **D8 en processus** : le source set tools de build-android
  (InProcessDexer, ApksigSigner, BundletoolAssembler) et les jars
  (r8, apksig, bundletool, guava) vivent DANS l'APK ;
  `BuildRunner` les charge par ServiceLoader puis réflexion, et ne
  fork un déxeur de fusion que si `java.home/bin/java` existe.
- **OAuth d'interface** : l'URI de retour est le schéma maison
  `joide://mcp/oauth` (intent-filter d'un écran de rappel exporté,
  singleTask, hors récents) ; l'état anti-CSRF relie le rappel à la
  tentative ; le code s'échange en POST form-encodé côté
  AutorisationMcp (déjà lot R) — aucune ligne reprise.
