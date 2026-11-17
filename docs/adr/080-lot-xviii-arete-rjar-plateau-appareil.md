# ADR-080 — Lot XVIII : l'arête du R.jar pour Kotlin et le plateau
# d'appareil de la route console

- **Statut** : Accepted (le terrain du 2026-11-17 : deux échecs —
  « cannot read input file: $OUT/android/:app/r.abi.manifest » sur
  `:app:compileKotlin` et « No class roots are found in the JDK
  path: /apex/com.android.art » sur `console:compileKotlin`)
- **Date** : 2026-11-17
- **Analyse experte** : deux études en parallèle — le moteur de
  CodeAssist (référence de terrain) et le moteur joide (les deux
  échecs) — dont les conclusions se recoupent sans contradiction.

## Diagnostic (A) — l'arête manquante

L'entrée « `<mod>:R.jar` » du classpath porte
`r.abi.manifest` en FileInput et sert aux DEUX tâches de compilation
(`upstreamJava` ET `upstreamKotlin`, AndroidTaskFactory:307-314 ;
`JvmCompileTask.inputs()` déclare chaque `entry.manifestInput`,
JvmCompileTask.java:96-98 ; `BuildEngine.ensureInputHashes` échoue
AVANT l'exécution sur un FileInput absent, BuildEngine.java:519-522).
Or l'arête vers le producteur (`<mod>:generateRJar`) n'existait que
sur `compileJava` (AndroidTaskFactory:391-393) : le scheduler
PARALLÈLE démarrait `compileKotlin` à t=0 — aucune dépendance dure —
alors que `generateRJar` attendait aapt2 link. `RJarTask` est
irréprochable (il écrit TOUJOURS le manifeste en cas de succès,
même sans R.java).

**Correctif** : l'arête jumelle
`graph.dependsOn(compileKotlin, <mod>:generateRJar)` à côté de
l'historique (conditionnée `hasKotlin`). Test de graphe nouveau
(AndroidTaskFactoryGraphTest : `dependentsOf("app:generateRJar")`
contient les DEUX tâches de compilation).

## Diagnostic (B) — le plateau d'appareil

Sur la route console JVM, `JvmTaskFactory` créait la tâche Kotlin
avec `sansJdk = false` (« bureau : le JDK réel fournit les racines »
— JvmTaskFactory.java:251-257 d'avant le lot). Sur l'appareil,
`java.home` = `/apex/com.android.art` (ART : ni jar ni racines) —
kotlinc (embarqué ou ouvrier forké) sonde ce chemin et échoue en
« No class roots are found ». Le lot XIV n'avait corrigé que la
route Android (`-no-jdk` inconditionnel). De plus, AUCUN jar ne
jouait java.* sur la route console (android.jar n'existe que pour la
route Android) — et kotlinc a TOUJOURS `-no-stdlib`, il lui faut
donc un plateau au classpath.

**La manière CodeAssist** (rapport expert, KotlinJvmCompiler.kt:105,
:146-155 ; AndroidBuildSystem.kt:215-217) : `-no-stdlib` toujours,
`-no-jdk` sur ART, android.jar FONDU dans `-classpath` (kotlinc JVM
n'a pas de `-bootclasspath`), kotlin-stdlib extrait d'un pack.

**Correctif** (trois pièces) :
1. `BuildRunner.surAppareil()` — la détection d'ART (vendor
   « Android » ou `java.home` sous `/apex`, la même sonde que
   CodeAssist) ; `rootsOf` ancre $SDK pour la route JVM sur
   appareil.
2. `BuildRunner.graphOf` (route JVM) : sur appareil,
   `ToolchainLocator.locate()` fournit android.jar — il TÉLÉCHARGE
   les pièces manquantes épinglées SHA-256 — et le plateau entre
   dans la fabrique (`withPlateauAppareil`).
3. `JvmTaskFactory.withPlateauAppareil` : le plateau (android.jar)
   entre dans le classpath des DEUX langages (kotlinc ET ecj lisent
   java.* du classpath ; le manifeste ABI suit le schéma des jars
   externes, donc haché en cache) et la tâche Kotlin reçoit
   `sansJdk = plateauAppareil != null` — le bureau garde le JDK réel,
   le comportement d'avant le lot y reste ENTIER (test dédié :
   sans plateau, PAS de `-no-jdk`, PAS d'entrée platform).

La stdlib Kotlin : le pilote forké la prépose déjà
(`jarStdlib` du pack épinglé, KotlinForkedDriver.java:244-245) — le
pilote EFFECTIF sur l'APK allégé ; rien à changer.

## Conséquences

- Un projet console JVM compile sur l'appareil dès que le cache SDK
  est servable (le premier build Android l'a rempli, ou locate()
  télécharge) ; sur desktop, zéro changement.
- `compileKotlin` Android ne peut plus s'exécuter avant son
  producteur de R.jar ; le graphe est complet par les DEUX langages.
- Suites build-jvm, build-daemon, build-android au vert ; trois
  tests nouveaux (le graphe des deux arêtes, le plateau avec
  `-no-jdk` et les deux classpaths, le bureau inchangé).
- La feuille de route langages (Java, Kotlin Compose, KMP) est
  posée dans `docs/feuille-de-route-langages.md` — le plan
  CodeAssist-parité issu de l'analyse experte.
