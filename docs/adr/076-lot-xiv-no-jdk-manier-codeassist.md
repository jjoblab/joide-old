# ADR-076 — Lot XIV : -no-jdk, la manière CodeAssist — le compilateur
# ne cherche plus de JDK sous ART

- **Statut** : Accepted (le terrain du 2026-11-17 : la VM ouvrière
  démarre enfin — le W^X du lot XIII est dépassé — mais le
  compilateur s'arrête sur « No class roots are found in the JDK
  path: /apex/com.android.art »)
- **Date** : 2026-11-17

## Contexte

Le logcat du terrain montre deux progrès puis un blocage nouveau :

1. `dalvikvm64` DÉMARRE (« Using generational CollectorTypeCMC GC »,
   « Priority-to-niceness mapping ») : la lecture seule du lot XIII a
   levé l'abort W^X — plus de « Writable dex file », plus de
   `Check failed: system_class_loader != nullptr`, plus de 134.
2. Le compilateur Kotlin se charge et refuse d'aller plus loin :
   **« No class roots are found in the JDK path: /apex/com.android.art »**.

La cause : `KotlinArgs` posait `-no-stdlib` et `-no-reflect` mais
JAMAIS `-no-jdk`. Sans ce drapeau, `K2JVMCompiler` cherche les
racines JDK sous `java.home` — au bureau, un vrai JDK (jrt-fs.jar),
ça passe sans bruit ; sur l'appareil, `java.home` est
`/apex/com.android.art` (ART : boot.oat/boot.art, AUCUN jar), la
détection échoue et le compilateur s'arrête avant d'avoir compilé.

La manière CodeAssist (`ForkedKotlincArtSpike.kt`, la forme CLI des
arguments que leur `KotlinJvmCompiler` construit) :

```kotlin
"-classpath", listOf(fixture.androidJar, fixture.stdlibJar)...,
"-no-jdk", "-no-stdlib", "-no-reflect",
"-jvm-target", "1.8", "-Xuse-fir-lt=false", ...
```

— `java.*` se résout depuis **android.jar du classpath fourni**, le
compilateur ne consulte JAMAIS `java.home`. Leur commentaire KSP le
dit de l'autre côté du mur : « ART's java.home does not [expose
lib/jrt-fs.jar] — java.* resolves from android.jar on the module's
compile classpath, so jdkHome stays null ».

joide a déjà la moitié de la forme : `AndroidTaskFactory` pose
android.jar (« bibliothèque de plateforme ») dans `upstreamKotlin`
ET `upstreamJava` de chaque module — il ne manquait que le drapeau.

## Décision

- `CompileRequest` porte un champ explicite `noJdk` (bâtisseur
  `noJdk(boolean)`), documenté : vrai = la manière CodeAssist,
  `java.*` du classpath ; faux = le bureau inchangé.
- `KotlinArgs.args()` pose `-no-jdk` quand la demande le réclame —
  la même fabrique alimente le pilote embarqué (bureau) et la VM
  forkée (appareil), donc les deux routes le reçoivent.
- `IncrementalJvmCompile`/`JvmCompileTask` transportent l'option ;
  l'empreinte d'options (`spec()`) l'inclut — une bascule
  invalide l'incrémental correctement.
- `AndroidTaskFactory` : `-no-jdk` pour KOTLIN (vrai) — son
  classpath porte android.jar ; les tâches Java (ECJ) ne changent
  pas (faux : l'option ne les concerne pas).
- `JvmTaskFactory` (modules JVM purs au bureau) : faux — le JDK réel
  fournit les racines, le comportement desktop ne change pas.
- NOT : un JDK simulé (répertoire `jre/lib/rt.jar` faké sur le
  disque de l'appareil) — CodeAssist ne le fait pas, android.jar
  suffit et le champ explicite garde la sémantique lisible.
- NOT : changer `KotlinWorkerMain` — les arguments restent construits
  côté parent, l'ouvrière les consomme tels quels.

## Conséquences

- Le prochain terrain doit compiler : le compilateur ne cherche plus
  de JDK sous ART ; `java.*` vient d'android.jar déjà au classpath.
- Les essais desktop prouvent les deux chemins (`KotlinArgsTest`) :
  la demande Android contient `-no-jdk` (+ `-no-stdlib`,
  `-no-reflect`), la demande bureau ne le contient pas. Suites
  build-jvm, build-daemon et build-android au vert.
- Le récit du terrain est maintenant un couloir nommé étape par
  étape : 134 W^X (lot XIII, levé) → pas de JDK sous ART (lot XIV,
  levé) → la prochaine pierre du mur, s'il y en a une, aura un nom.
