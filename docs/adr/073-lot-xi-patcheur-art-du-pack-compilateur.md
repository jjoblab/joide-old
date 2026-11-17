# ADR-073 — Lot XI : le pack du compilateur repatché pour ART — la
manière CodeAssist portée (passes ASM + shims + home de ressources)

- **Statut** : Accepted (mandat du propriétaire du 2026-11-17 :
  « consulte CodeAssist pour voir comment ils ont fait, dans mon cas
  j'ai toujours des erreurs » — sortie 134 des DEUX tentatives à
  512 Mo, la console lot X nommant l'échec sans cause sur stderr)
- **Date** : 2026-11-17

## Contexte

Le lot X avait nommé et survécu à la morte d'ouvrière ; le terrain
revient avec le MÊME SIGABRT (sortie 134 deux fois) et la queue de
stderr vide. La consultation CODE de CodeAssist (son dépôt, ses
passes `kotlinc-art`, son #1748) révèle ce que la lecture de son
architecture avait manqué : **CodeAssist ne lance pas le compilateur
Kotlin tel quel sur ART — il RÉÉCRIT son bytecode**. Le compilateur
(`kotlin-compiler-embeddable`, K2/2.4.0 chez eux) est construit pour
un JVM de bureau et emploie des APIs absentes ou cassées d'ART ; leur
plugin de build porte QUINZE passes ASM découvertes sur appareil, et
leur note d'architecture le dit sans détour : le set des breakages
« is discovered empirically, not guessed ».

Le scan des pools de constantes du jar épinglé de joide
(`kotlin-compiler-embeddable 2.3.0`, 25 581 classes) confirme les
mêmes familles de breakage, RELOCALISÉES sous
`org.jetbrains.kotlin.com.intellij.*` :

1. `java.lang.management.*` (absent d'ART) — quatre classes du
   compilateur l'emploient (`PerformanceManager`,
   `BuildMetricsReporterKt`, `ThreadDumper`,
   `LowMemoryWatcherManager$2`) ;
2. le wrapper `com.intellij.util.containers.Unsafe` relocalisé dont
   le `<clinit>` résout DIX MethodHandles d'{@code sun.misc.Unsafe}
   et jette une Error au premier échec — ART n'a pas le
   `copyMemory` à cinq arguments : `ConcurrentLongObjectHashMap.<clinit>`
   puis tout `KotlinCoreEnvironment` mouraient — AUCUN build Kotlin
   ne pouvait démarrer sur un appareil strict ;
3. `java.lang.ClassValue` (absent d'ART, lien eager) — SEPT
   utilisateurs : cinq classes IntelliJ, `ComputableClassValue` de
   kotlin-reflect ET `ClassValueCtorCache$cache$1` de coroutines
   (les deux sur le chemin du compilateur, manqués par le scan de
   l'ADR-069) ;
4. le dispatch d'interface fastutil d'Android 12/12L (le plus gros
   cluster natif du terrain CodeAssist : SIGSEGV dans
   `artInstanceOfFromCode` sous `PsiBuilderImpl.bind` — le parsing de
   CHAQUE compilation passe par `PsiBuilderImpl`) ;
5. `java.beans.Introspector` (absent) — trois classes ; les
   références de MÉTHODES (`Introspector::decapitalize`) vivent dans
   les BootstrapMethods, invisibles au visiteur d'instructions ;
6. `java.awt.EventQueue` (absent) — `EDT.isCurrentThreadEdt()` ;
7. `PathUtil.getResourcePathForClass` — cherche un `.class` qui
   n'existe plus après dexage et jette, emportant le registre
   d'extensions (`META-INF/extensions/*.xml`).

Et le scan INFIRME une passe de CodeAssist : AUCUN usage réel du
type `java.lang.invoke.VarHandle` dans le pack 2.3.0 (les 2 646 hits
`MethodHandles` sont des bootstraps de lambda, désugarés par D8).

## Décision

### Le patcheur : `packArt` de build-jvm, sept passes

Un source set dédié `packArt` porte `PatcheurJar` (le main), le
registre `PassesArt` et les sept passes — la même interface que
CodeAssist (`Passe` : `nom`, `cible`, `visiteur`), la même politique
d'auteur : « une passe par breakage confirmé » :

1. `management-stub` — les corps qui exécutent management se
   remplacent par des retours triviaux (l'API tree), `initialize
   CurrentThread` garde son affectation de fil, `currentTime` rend un
   vrai `new Time(0,0,0)` ; les sept classes Profiler du daemon ne
   sont PAS servies par l'ouvrière (protocole ligne) : laissées ;
2. `unsafe-find-tolerant` — le corps de `find` enveloppé d'un
   catch-tout qui rend null (les neuf handles se résolvent, le chemin
   rapide MethodHandles reste) et `getAndAddInt` (API cachée refusée
   sous targetSdk > 30, EMPLOYÉE par le TreeBin) délègue au shim
   `UnsafeCompat` — une boucle CAS `getIntVolatile` +
   `compareAndSwapInt`, les opérations ART laisse passer, écrite
   TOUT EN RÉFLEXION (aucune référence de compilation à
   `sun.misc.Unsafe` : l'avertissement propriétaire est une erreur
   sous le `-Werror` du dépôt) ;
3. `class-value-shim` — DÉTECTION LARGE (les octets citent
   `java/lang/ClassValue`) et `ClassRemapper` uniforme :
   super-classes, champs, signatures, descripteurs, instructions —
   les nouveaux utilisateurs d'un jar futur se couvrent seuls ; le
   shim `ClassValueCompat` rejoue `get`/`computeValue`/`remove` avec
   une mémoïsation `ConcurrentHashMap` ;
4. `fastutil-interface-dispatch` — les six sites
   `invokeinterface` d'`IntSet`/`Int2ObjectMap` de
   `MarkerOptionalData` (et de `PsiBuilderImpl$MyTreeStructure`, au
   cas où) partent au shim à appel direct `FastutilDirectCalls`
   (`instanceof` de la classe concrète puis `invokevirtual` —
   dispatch par vtable ; tout AUTRE récepteur garde le repli du même
   appel interface) ; tout appel fastutil NON cartographié dans une
   classe visée échoue le patch — un site nouveau se voit ici, pas
   sur un appareil ;
5. `beans-introspector` — les deux statiques partent au shim
   `IntrospectorCompat` (le `decapitalize` du JDK), dans les
   instructions ET les `bsmArgs` des invokedynamic (l'API tree d'ASM
   9 n'a pas de `BootstrapMethodNode` : chaque
   `InvokeDynamicInsnNode` porte ses arguments de bootstrap) ;
6. `edt-headless` — le corps d'`isCurrentThreadEdt` devient
   `return true` (le compilateur en ligne de commande EST son fil
   privilégié) ;
7. `pathutil-self-locate-redirect` — le corps de
   `getResourcePathForClass` devient `return new File(System
   .getProperty("joide.kotlinc.home", ""))`.

Les frames et maxs se recomputent (`COMPUTE_FRAMES`) : plusieurs
passes remplacent des corps. Le processus du patcheur porte TOUS les
jars du pack sur son classpath — la résolution des super-classes
communes d'ASM charge les types réels (premier tirage du terrain :
`PsiBuilderImpl$SingleLexemeNode` absent du classpath, la tâche
échouait net).

### Les shims dexés DANS le pack

Les quatre shims (`ClassValueCompat`, `UnsafeCompat`,
`FastutilDirectCalls`, `IntrospectorCompat`) vivent dans le même
source set `packArt`, livrés par la tâche `shimsArtJar`, et
`dexPacksOutils` les dexé dans LEUR lot du pack (avant l'ouvrier) :
le chargeur de l'ouvrière les voit, l'APK et le bureau ne les voient
JAMAIS. La tâche Gradle passe chaque jar de `jarsKotlin` par le
patcheur avant l'assainissement ; le régénérateur local du bac à
sable (scripts/, hors dépôt) rejoue la même recette pour produire le
pack épinglé ci-dessous.

### Le `joide.kotlinc.home` du pilote

Le pilote (KotlinForkedDriver) extrait les RESSOURCES du pack (tout
sauf les dex, avec garde anti-slip de zip) dans `kotlin-home` du
répertoire d'outillage — la garde étant le SHA-256 épinglé — et
passe `-Djoide.kotlinc.home` à la VM : le registre d'extensions lit
ses descripteurs d'un vrai système de fichiers. Bureau : null (les
jars portent leurs ressources par classloader, le pilote forké n'y
sert que les essais).

### Le diagnostic garde sa couche (lot X)

La course du lot X est corrigée : `tuerOuvriere` détruit AVANT de
capturer (l'EOF suit la morte, le drain du stderr se TERMINE, la
queue se capture complète — c'est elle qui avalait le « Abort
message » d'ART sur le terrain) et le message d'échec porte
désormais le niveau d'Android et l'état du drapeau JIT, plus un
« stderr : vide » explicite (un abort natif sans message — le
« Fatal signal » ne va qu'au logcat).

### Le pack régénéré, réépinglé, repoussé

Le pack passe de 19 065 892 à {@code TAILLE_KOTLIN} octets
(SHA-256 {@code SHA_KOTLIN}, épinglé dans PacksOutils.java), l'asset
`kotlin-compiler-2.3.0.dex.zip` de la release `toolchain-v1` se
remplace — un appareil qui porte l'ANCIEN pack recacheté re-télé
charge à la première divergence d'empreinte (fichierAssure vérifie
taille + SHA avant usage, pose atomique).

## Conséquences

- Les sept breakages ART confirmés du pack sont parades au dexage :
  le compilateur s'initialise (Unsafe tolérant), charge (ClassValue
  relocalisé, EDT sans awt), parse (fastutil en vtable), démarre son
  registre (management vidé, PathUtil provisionné) — la famille de
  SIGABRT/SIGSEGV/NCDFFE que CodeAssist a éteintes une par une ne
  peut plus produire de « sortie 134 » sans cause.
- Le terrain garde sa boussole : toute morte prochaine porte
  Android X + l'état du JIT + le stderr complet ou « vide ».
- L'épingle bouge : TAILLE_KOTLIN et SHA_KOTLIN portent les valeurs
  du pack régénéré (indiquées dans le commit ; la tâche
  dexPacksOutils régénère byte-identique — même recette, même
  android.jar 37.2, même D8 épinglé) — une divergence reste un
  échec EXPLICITE avec les valeurs à reporter.
- NOT (scan) : VarHandle (aucun usage réel du type), lz4
  `UnsafeUtils`/`NativeMemoryAllocator` (le champ theUnsafe est liste
  grise), les Profiler du daemon (jamais servis), l'inner
  `javax.management` de LowMemoryWatcherManager (jamais chargée du
  CLI) — chacun noté pour un lot futur si le terrain le demande.
- Suitent au vert après le lot : build-jvm (dont les dix essais du
  patcher sur les classes réelles du compilateur), build-android
  (acceptation bout-en-bout), build-daemon.
