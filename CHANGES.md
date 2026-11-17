# CHANGES — Lot XIX : aapt2 conscient de l'ABI, l'API javax.* dans l'APK, Compose par sonde (2026-11-17)

**Le build console JVM est PASSÉ sur l'appareil** (le lot XVIII
tient). La troisième vague de terrain : `app:processAndroidResources
: aapt2 failed: …/aapt2[74]: syntax error: unexpected '('` et
`app:generateRJar : NoClassDefFoundError:
Ljavax/lang/model/SourceVersion;`. Analyses expertes : une source
d'aapt2 Android VÉRIFIÉE par téléchargement (ReVanced v1.1.0, ELF
AArch64 statique, 16 Ko aligné, SHA-256 épinglés) ; la recette
CodeAssist pour javax.lang.model (un jar API committé et dexé dans
l'APK — ecj 3.40 référence SourceVersion au chargement du batch, 53
classes touchées) et pour le plugin Compose (sonde de classpath,
-Xplugin, ZÉRO -P, registrar dexé là où kotlinc charge).

| Étape | Contenu |
|---|---|
| XIX/1 | ToolchainLocator conscient de l'ABI : varianteAapt2 (android-arm64-v8a / android-x86_64 / linux) ; sur appareil, le repli télécharge le binaire ANDROID de ReVanced épinglé (la même source que les jniLibs de l'APK) — JAMAIS le jar linux (l'ELF x86_64 filé au shell par bionic était le « syntax error ») |
| XIX/2 | app-android/libs/java-compiler.jar (144 classes javax.lang.model + javax.annotation.processing + javax.tools, extraites du JDK 21 par scripts/jar-javax-api.sh), dexé dans l'APK — ecj charge sur ART (generateRJar d'abord, les modules Java ensuite) |
| XIX/3 | Compose : PluginsKotlin (sonde androidx/compose/runtime/Composable.class dans jars ET dirs ; jar du plugin : pack jars/ sur appareil, ressources build-jvm au bureau ; échec NOMMÉ si sonde allumée et jar absent) ; KotlinArgs pluginJars → -Xplugin ; les deux pilotes branchés ; ZÉRO option -P |
| XIX/4 | Le plugin Compose (kotlin-compose-compiler-plugin 2.3.0, version du compilateur du pack) : dexé dans le pack Kotlin (jarsKotlin, passage par le patcheur ART) + jar original en ressource jars/ ; PacksOutils taille/SHA à recalculer au regen |
| XIX/5 | Suites build-jvm + build-android vertes ; cinq tests nouveaux |

Le pack Kotlin change : le nouveau kotlin-compiler-2.3.0.dex.zip est
à publier sur la release toolchain-v1 (les empreintes épinglées de
PacksOutils suivent). Reste hors lot (écarts honnêtes) : la
résolution des AAR androidx.compose (classes.jar) et le bouton
Exécuter console.

---

# CHANGES — Lot XVIII : l'arête du R.jar pour Kotlin et le plateau d'appareil de la route console (2026-11-17)

**Les nouveaux logs** (2026-11-17) : `:app:compileKotlin :
cannot read input file: $OUT/android/:app/r.abi.manifest` et
`console:compileKotlin : No class roots are found in the JDK path:
/apex/com.android.art`. Deux analyses expertes en parallèle (le
moteur CodeAssist, référence de terrain ; le moteur joide) se
recoupent : (A) l'entrée R.jar (avec r.abi.manifest en FileInput,
déclaré par upstreamJava ET upstreamKotlin) n'avait son arête
producteur que sur compileJava — le scheduler PARALLÈLE démarrait
compileKotlin avant generateRJar et le moteur échouait AVANT
l'exécution (ensureInputHashes) ; RJarTask est irréprochable (il
écrit TOUJOURS le manifeste en cas de succès). (B) la route console
JVM créait la tâche Kotlin avec sansJdk=false (« bureau : le JDK
réel fournit les racines ») — sur appareil java.home =
/apex/com.android.art (ART, aucune racine), kotlinc échoue ; et
AUCUN jar ne jouait java.* sur cette route (kotlinc a toujours
-no-stdlib : il lui faut un plateau au classpath).

| Étape | Contenu |
|---|---|
| XVIII/1 | AndroidTaskFactory : l'arête jumelle compileKotlin → `<mod>:generateRJar` (conditionnée hasKotlin) à côté de l'historique compileJava |
| XVIII/2 | BuildRunner : surAppareil() (vendor Android ou java.home /apex — la sonde de CodeAssist), ancre $SDK pour la route JVM sur appareil, plateau via ToolchainLocator.locate() (téléchargement épinglé SHA-256 à la demande) |
| XVIII/3 | JvmTaskFactory.withPlateauAppareil : android.jar entre dans le classpath des DEUX langages (kotlinc ET ecj lisent java.* du classpath, manifeste ABI au schéma des jars externes) et la tâche Kotlin porte -no-jdk ; le bureau reste ENTIER (pas de plateau, pas de -no-jdk) |
| XVIII/4 | Suites build-jvm, build-daemon, build-android vertes ; trois tests nouveaux (le graphe des deux arêtes ; plateau + -no-jdk + les deux classpaths ; le bureau inchangé) |

La feuille de route langages (Java, Kotlin Compose, KMP — la parité
CodeAssist) est posée : `docs/feuille-de-route-langages.md`
(Compose : plugin -Xplugin + sonde de classpath + registrar dexé
dans l'app hôte ; incrémental Kotlin maison car l'IC officielle
mmap casse sur ART ; KMP collapsed via -Xcommon-sources ; R.jar ASM ;
INameEnvironment ecj si besoin).

---

# CHANGES — Lot XVII : le manifeste enrichie — aapt2 exige l'attribut package (2026-11-17)

**Le nouveau log** (2026-11-17) : « build #1 échoué — 5 exécutées ·
0 du cache · 0 à jour · 104,4 s » et `:app:linkAndroidResources :
link failed: <manifest> must have a 'package' attribute`. La
progression est franche : les murs d'environnement (W^X, JDK, StAX,
stdlib) sont franchis — la chaîne de build TOURNE sur l'appareil et
l'échec est devenu MÉTIER. Diagnostic sur les artefacts : le gabarit
du projet démo (`assets/modeles/application-android.toml`) écrit un
manifeste SANS `package` (style AGP 8 : le package vit dans
`namespace`) alors que le paquet saisi à la création ne va que dans
`MainActivity.kt` ; `Aapt2LinkTask` passait le manifeste SOURCE brut
à aapt2 ; aapt2 est une couche basse qui exige l'attribut ; et
`Aapt2.LinkOptions.packageName` est `--rename-manifest-package`
(l'identifiant FINAL de l'APK) — il ne remplace pas l'attribut que R
et la table de ressources exigent. La manière AGP : le manifeste
MERGÉ porte l'attribut synthétisé.

| Étape | Contenu |
|---|---|
| XVII/1 | `Aapt2LinkTask` écrit TOUJOURS un manifeste enrichie (`$OUT/android/<module>/manifest-enrichi.xml`, JAMAIS dans les sources) : copie du manifeste source avec `package="…"` injecté s'il manque ; les DEUX liens (APK et proto) le consomment ; il est déclaré en sortie |
| XVII/2 | la chaîne de secours : manifeste `package=` → identifiant d'application de la variante (`identifiantPaquet` — l'application en a toujours un) → dérivation du nom du module, EXACTEMENT la règle d'`identifiantPaquet` (« : » → « _ ») ; une synthèse émet un WARN qui nomme le paquet et le correctif recommandé |
| XVII/3 | le gabarit est corrigé : `package="{paquet}"` dans le manifeste du modèle — le paquet saisi à la création arrive enfin dans le manifeste (nouveaux projets droits) |
| XVII/4 | suites build-jvm, build-android, build-daemon vertes ; cinq tests nouveaux (synthèse, dérivation, manifeste avec paquet intact, balise multiligne, règle EXACTE) |

Le projet DÉJÀ créé du terrain : la synthèse fait passer le lien ; le
WARN recommande d'écrire le paquet dans le manifeste — le paquet
choisi à la création (première ligne de MainActivity.kt, ex. `jo.exemple.monapplication`) est la bonne valeur : R.java se
génère sous ce paquet et l'activité voit `R` sans import.

---

# CHANGES — Lot XVI : le JAR réel de la stdlib dans le pack (2026-11-17)

**Le nouveau log** (2026-11-17) : après la pile StAX du lot XV (le
compilateur charge ses descripteurs), `:core:compileKotlin` sèche sur
« Cannot access built-in declaration 'kotlin.Any'. Ensure that you
have a dependency on the Kotlin standard library. », avec le warning
« Classpath entry points to a non-existent location:
core/classes-kotlin » et la note « mapped buffer unmapping » (sans
gravité). Le diagnostic sur les artefacts : `KotlinArgs` pose
`-no-stdlib` (contrat : le classpath fourni porte la stdlib), mais le
classpath de compile Android (`upstreamKotlin` : android.jar, sorties
amont, R.jar, jars externes) ne contient JAMAIS la stdlib — et kotlinc
résout son classpath avec son JAR FS : des FICHIERS sur disque. Les
classes de la stdlib dexées dans le pack sont invisibles pour lui ; sur
l'appareil il n'existe AUCUN `kotlin-stdlib.jar`. `kotlin.Any` est de
plus une déclaration intrinsèque SANS .class (vérifié : 978 classes
dans le jar, zéro kotlin/Any.class) — sa métadonnée vit dans
`META-INF/kotlin-stdlib.kotlin_module`, que seul le jar fournit. La
manière CodeAssist : leur outil télécharge le jar RÉEL de la stdlib et
le pose au classpath.

| Étape | Contenu |
|---|---|
| XVI/1 | le pack porte le jar ORIGINAL de la stdlib en ressource (`jars/kotlin-stdlib-2.3.0.jar`, +1,68 Mo — le jar Maven RÉSOLU, pas le patché : le compilateur lit du bytecode standard) ; `homeOuvriere` (lot XI) extrait déjà toutes les entrées non-dex du pack dans `kotlin-home/`, garde SHA — le jar arrive gratuitement |
| XVI/2 | le pilote forké pose le jar EN TÊTE du classpath : `KotlinForkedDriver.compile` assure le pack puis `jarStdlib(homeOuvriere(classpath))` AVANT de bâtir les args, passés à `KotlinArgs.args(request, classpathAvant)` ; hors pack (essais bureau), aucune entrée ajoutée |
| XVI/3 | les entrées de classpath inexistantes se FILTRENT (`KotlinArgs.classpathEntiere`) — fin du « points to a non-existent location », l'ordre se conserve |
| XVI/4 | pack vérifié : 265 entrées, le jar stdlib (1 796 996 octets, 978 classes, les 3 `.kotlin_module`), constantes épinglées (`9a9f48fc…`, vérifié par téléchargement retour) ; r8 et bundletool ressortis IDENTIQUES (aucune republication) ; suites build-jvm, build-android, build-daemon vertes ; deux tests nouveaux |

---

# CHANGES — Lot XV : la pile StAX dans le pack, l'analyse complète de CodeAssist (2026-11-17)

**Le mandat du propriétaire** (message du 2026-11-17) : « Cela devient
frustrant, fait une analyse plus approfondie pour comment exactement
CodeAssist fait avec son moteur de build, tous les outils nécessaires
… s'il faut une refonte totale, fais-la, fais tous les tests possibles
» — et le nouveau log : `NoClassDefFoundError: Failed resolution of:
Lorg/jetbrains/kotlin/org/codehaus/stax2/typed/TypedXMLStreamReader`
depuis `PluginDescriptorLoader.loadDescriptorFromDir` à
:core:compileKotlin. L'analyse faite sur les ARTEFACTS (pas sur des
hypothèses) : le pack contient bien `TypedXMLStreamReader` et les 18
classes stax2 shadées, mais AUCUNE classe `javax.xml.stream` définie,
AUCUN fichier de services, et l'aalto shadé de l'embeddable est
partiel (pas d'usines). Au bureau, le JDK fournit l'API StAX ET son
parseur par défaut ; sur ART, `javax.xml.stream` n'existe pas — la
vérification de classe échoue sur sa hiérarchie. CodeAssist le dit
dans son propre catalogue (`libs.versions.toml`) : « StAX on ART
(which ships none): the standalone JSR-173 API, and aalto as its
implementation. Used by the on-device Kotlin compiler (IntelliJ-core's
descriptor parsing) and by R8's resource shrinker » — ils embarquent
`stax-api:1.0-2` + `aalto-xml:1.3.0` + `stax2-api:4.2.1`. L'ADR-077
documente le moteur de CodeAssist en entier (compilateur -for-ide
unshadé fusionné EN PROCESSUS, StAX, -no-jdk + android.jar, args CLI,
VM forkée sondée, W^X, aapt2/bundletool/apksig/d8/desugar/bouncycastle).

| Étape | Contenu |
|---|---|
| XV/1 | les trois jars StAX de CodeAssist (mêmes versions) dans la fermeture `jarsKotlin` du pack : API + impl complète unshadée (les usines que la copie shadée n'a pas) + stax2-api ; ils traversent la pipeline existante (patcheur ART → assainir → dexage par lots → ressources), les `META-INF/services` d'aalto partent avec les usines qu'ils désignent |
| XV/2 | le pack se RÉGÉNÈRE réellement (`:app-android:dexPacksOutils` contre android.jar 35) ; les constantes épinglées se reportent pour les TROIS packs (le marqueur D8/javac de la machine fait dériver r8/bundletool de quelques octets ; les trois assets de la release toolchain-v1 sont remplacés ensemble) |
| XV/3 | vérification matérielle du pack nouveau : 37 descripteurs `javax.xml.stream` définis (dont `XMLStreamReader`), les trois usines aalto (`InputFactoryImpl`, `OutputFactoryImpl`, `EventFactoryImpl`), les trois services corrects, 264 entrées (contre 253) ; SHA-256 recalculé indépendamment = constantes ; suites build-jvm, build-android, build-daemon au vert |

---

# CHANGES — Lot XIV : -no-jdk, la manière CodeAssist (2026-11-17)

**Le mandat du propriétaire** (message du 2026-11-17) : nouveau
logcat après le lot XIII — DEUX progrès visibles : `dalvikvm64`
démarre (« Using generational CollectorTypeCMC GC », plus aucun
« Writable dex file », plus de 134 : le W^X du lot XIII est levé),
le compilateur Kotlin se charge — puis refuse d'aller plus loin :
**« No class roots are found in the JDK path: /apex/com.android.art »**.
Cause : `KotlinArgs` posait `-no-stdlib` et `-no-reflect` mais jamais
`-no-jdk` ; sans ce drapeau, `K2JVMCompiler` cherche les racines JDK
sous `java.home` — au bureau, un vrai JDK, ça passe sans bruit ; sur
l'appareil, ce chemin est ART (`/apex/com.android.art`, aucun jar) et
la détection échoue avant toute compilation. Le lot XIV (ADR-076)
porte la forme complète de CodeAssist (`ForkedKotlincArtSpike.kt` :
« -no-jdk, -no-stdlib, -no-reflect » + android.jar au classpath) sur
la chaîne des demandes de compilation :

| Étape | Contenu |
|---|---|
| XIV/1 | `CompileRequest.noJdk` (champ explicite + bâtisseur) ; `KotlinArgs.args()` pose `-no-jdk` quand la demande le réclame — la même fabrique alimente le pilote embarqué (bureau) et la VM forkée (appareil), les deux routes le reçoivent |
| XIV/2 | `IncrementalJvmCompile`/`JvmCompileTask` transportent l'option et l'incluent dans l'empreinte d'options (`spec()`) — une bascule invalide l'incrémental correctement |
| XIV/3 | `AndroidTaskFactory` : `-no-jdk` pour KOTLIN (son classpath porte déjà android.jar, « bibliothèque de plateforme » — la moitié de la forme CodeAssist était en place) ; `JvmTaskFactory` (bureau, modules JVM purs) : faux — le JDK réel fournit les racines, le comportement desktop ne change pas |
| XIV/4 | l'essai `KotlinArgsTest` prouve les deux chemins : la demande Android contient `-no-jdk` (+ `-no-stdlib`, `-no-reflect`), la demande bureau pas de drapeau — suites build-jvm, build-daemon et build-android au vert |

---

# CHANGES — Lot XIII : le pack en lecture seule, la cause racine du 134 (2026-11-17)

**Le mandat du propriétaire** (message du 2026-11-17) : « Toujours la
même erreur » — MAIS le logcat envoyé cette fois nomme la cause
racine que le stderr vide du lot X ne pouvait pas montrer :
`SecurityException: Writable dex file
'.../files/toolchain/packs/kotlin-compiler-2.3.0.dex.zip' is not
allowed` au démarrage de la VM ouvrière
(`ClassLoader.getSystemClassLoader` → `BaseDexClassLoader` →
`DexFile.openDexFileNative`), puis `Check failed:
system_class_loader != nullptr` — l'abort 134 sans stderr de TOUS
les rapports depuis le lot X. La politique W^X d'Android refuse de
charger un dex/jar MODIFIABLE ; l'échelle de tas des lots X/XII
travaillait sur une cause orthogonale (correcte et utile, mais pas
celle-ci). Le lot XIII (ADR-075) porte la manière CodeAssist
(`ArtKotlinPluginLoader.kt` : « Android 14+ won't load a writable
dex/jar through a class loader (W^X) ») sur le pipeline des packs :

| Étape | Contenu |
|---|---|
| XIII/1 | `PacksOutils.fichierAssure` impose la LECTURE SEULE à CHAQUE résolution (`imposerLectureSeule`, idempotent, « au mieux » — la même tolérance que `ChargeurDexAndroid`, qui le faisait déjà au chargeur en processus depuis le lot VIII) : le point d'étranglement couvre le chargeur en processus ET le classpath de la VM forkée ; surtout, la réparation au chargement rattrape les packs déjà sur le disque posés MODIFIABLES par les versions antérieures (sains en taille + SHA-256, donc JAMAIS re-téléchargés — l'état exact de l'appareil du propriétaire), sans re-téléchargement ni nettoyage manuel |
| XIII/2 | `telechargePinned` fait naître le fichier en lecture seule : le staging passe en lecture seule AVANT le move atomique (le patron exact d'`ArtKotlinPluginLoader` — chmod le temporaire puis move) ; une cible déjà posée se SUPPRIME avant le move (un pack corrompu en lecture seule doit pouvoir se ré-écrire — le fichier se remplace, il ne se ré-ouvre pas en écriture) ; le staging résiduel se nettoie en `finally` |
| XIII/3 | les essais desktop prouvent le chemin exact du terrain (`PacksOutilsTest`) : un pack posé modifiable, sain pour son épinglage, passe en lecture seule au premier `fichierAssure`, reste sain, est idempotent au second passage et son contenu ne bouge pas ; `imposerLectureSeule` est « au mieux » (fichier absent : aucun échec) — suites build-android, build-jvm et build-daemon au vert |

---

# CHANGES — Lot XII : la remontée de rung, l'OOM mid-run nommé (2026-11-17)

**Le mandat du propriétaire** (message du 2026-11-17) : après le lot
XI, toujours « sortie 134 puis 134 » à :core:compileKotlin — MAIS le
diagnostic du lot X parle enfin : « Android 35, inlining JIT actif ;
stderr : vide (un abort natif sans message) », tas 512 Mo aux deux
tentatives. Le lot XII (ADR-074) en tire le fil :

| Étape | Contenu |
|---|---|
| XII/1 | la lecture du terrain : Android 35 (hors des tables de crash connues — le JIT #1748 est ≤ 30, le pack ART-patché du lot XI couvre le reste) + « tas 512 Mo aux DEUX tentatives » = la première tentative RÉPONDAIT puis abortait : le tas était trop PETIT (sur ~4 Go, la garde de démarrage rejette 1024 de justesse — l'échelle plafonne à 768/512, l'OOM d'ART aborte SANS message en plein compile) ; la reprise du lot X restait au même rung — elle DESCEND pour un abort de démarrage et RESTAIT pour une mort après réponse : les deux sens de l'échelle manquaient |
| XII/2 | la REMONTÉE de rung : une morte APRÈS réponse gravit l'échelle de remontée (échelle de démarrage + tas demandé + rung 1536, garde ASSOULIE rung × 2 ≤ 80 % de la RAM, ascendante) — la VM démarre déjà au rung essayé, l'engagement physique suit l'usage, et l'autre option est un build qui échoue ; la garde de DÉMARRAGE (rung × 2 ≤ RAM/2, la LADDER CodeAssist) reste inchangée — elle teste une réservation SANS engagement, un rung trop grand aborte net au démarrage |
| XII/3 | TROIS tentatives par lot (512 → 1024 → 1536 sur ~4 Go) ; l'échec final nomme les TAS ESSAYÉS (« tas essayés : 512 puis 1024 puis 1536 ») et, si la remontée a plafonné sans guérir, porte le soupçon d'OOM avec la RAM et la garde explicites — la prochaine console dira l'un des trois états : succès au rung supérieur, plafond nommé, ou cause stderr réelle |
| XII/4 | les essais desktop prouvent la remontée SANS dépendre de la RAM du bac à sable : la fausse VM journalise son `-Xmx` à chaque lancement (`fausseVmAvecJournal`), le test compare aux échelles calculées (`echelleRemontee`, garde 0.8 : 1024 gravissable sur 3 Go, 1536 sur 4 Go) et rejoue le cas du terrain (échelle [768, 512, 384] du bac à sable : la reprise APRÈS réponse monte 768 → 1024 et le lot réussit) ; les trois morts-sans-réponse restent nommées (TROIS tentatives, descente) |

---

# CHANGES — Lot IX : le rebouclage IPv4, les racines déclarées, le démon qui survit (2026-11-17)

**Le mandat du propriétaire** (message du 2026-11-17) : « La CI est
rouge. J'ai ouvert l'application puis le bouton build » — build #1 :
« service de build injoignable (port publié mais connexion refusée
6 fois — le démon :build meurt juste après publication) », repli en
processus, « :core:compileKotlin : cannot scan input directory:
$PROJECT/core/src/main/kotlin » ; build #2 : « port non publié en
30 s et le processus :build n'a JAMAIS démarré ». Le lot IX (ADR-071)
ferme les DEUX pannes du premier build sur appareil — et le démon
n'était même pas mort.

| Étape | Contenu |
|---|---|
| IX/1 | le REBOUCLAGE IPv4 EXPLICITE : `InetAddress.getLoopbackAddress()` rend **::1** sur Android (libcore) — le démon écoutait sur `[::1]:port` pendant que le client composait `127.0.0.1:port` : connexion refusée à CHAQUE build, démon vivant ; la JVM de bureau rend 127.0.0.1, les tests passaient, l'appareil échouait. Bind explicite `127.0.0.1` (`getByAddress`, test `leServeurEcouteEnIpv4Explicite`), la VIE suit le port à chaque republication (le faux « JAMAIS démarré » du build #2 venait d'un service VIVANT qui ne repassait pas par onCreate), et le message du client n'accuse plus le démon de « mourir » sans preuve |
| IX/2 | les RACINES DÉCLARÉES existent dès la création (`Generation.dossiers` → `EcrireProjet` crée `core/src/main/kotlin` vide : le modèle le déclare, aucun fichier n'y vit) ET le moteur TOLÈRE l'absent (`BuildEngine.scanStamps` : un répertoire d'entrées absent vaut VIDE, sémantique Gradle ; un fichier là où un dossier est attendu reste une erreur) — le cas exact de MyApp est prouvé bout en bout (`declaredButUncreatedSourceRootIsEmptyNotAnError`) |
| IX/3 | le démon SURVIT à ses pannes : chaque requête vit dans SA garde (un cadre illisible se RÉPOND, le canal reste debout), `build` rattrape `Throwable` (un Error coûte le build, pas le service), `accepter` rattrape par connexion et ENREGISTRE la cause — le chien de garde la publie dans `demon-build.err` avant l'arrêt ; l'injection de panne garde sa mort (`PaniqueFatale` traverse les gardes, `DaemonDeathResumeTest` intact) |
| IX/4 | la CI du lot VIII avait construit les DEUX APK puis échoué au STOCKAGE du cache de configuration : `verifierOmbreApk` capturait `layout` dans son `doLast` (« cannot serialize Gradle script object references ») — le bac à sable local tourne `configuration-cache=false`, la faute n'y paraissait jamais ; le chemin de l'APK se résout désormais à la CONFIGURATION (File sérialisable), la commande CI exacte (assembleDebug + assembleRelease, cache actif) passe et STOCKE son entrée |

---

# CHANGES — Lot VIII : le pack qui charge, le service qui parle, l'APK à 11,2 Mo (2026-11-17)

**Le mandat du propriétaire** (message du 2026-11-17) : « D'accord
pour l'option A (R8 minification) » — et le rapport de terrain :
premier build sur appareil, « service de build injoignable (port non
publié en 30 s) — relancez le build », repli en processus, puis
« embedded build tool unavailable: jo.joide.build.android.tools.InProcessDexer
— jars des outils absents et pack différé r8 9.5.32
(r8-9.5.32.dex.zip) indisponible (java.lang.reflect.InvocationTargetException) »
— « vérifie comment CodeAssist a fait ». Le lot VIII (ADR-070)
répond point par point : l'ombre qui empoisonnait le pack part, les
causes voyagent jusqu'à la console, le service distingue ses
silences, et la passe de rétrécissement du backlog P8 livre un APK
de **11,2 Mo**.

| Étape | Contenu |
|---|---|
| VIII/1 | L'OMBRE des adaptateurs packés part de l'APK : leur copie embarquée (lot VI : « le signal de repli ») tuait la route du pack — la délégation parent-d'abord du DexClassLoader servait la copie de l'APK (sans ses classes d'outil) et JAMAIS celle du pack ; `toolsApi` passe en `compileOnly` (la manière CodeAssist : aucune classe d'outil dans l'app), un jar DÉDIÉ `signatureApi` garde `ApksigSigner` seul embarqué (apksig trop léger pour un pack, décision lot VI) ; la tâche `verifierOmbreApk` (finalisée par `assembleDebug`) échoue si l'ombre revient ou si un élément vital disparaît (l'enregistrement du pont se vérifie par CONTENU — R8 renomme légitimement les fichiers de services internes) |
| VIII/2 | les CAUSES voyagent : `BuildRunner.outil()` compose son message avec l'embarquée APLATIE (un `InvocationTargetException` montre sa cible : `NoClassDefFoundError: com/android/tools/r8/Version` dit tout) ET la cause du pack ; `outilDuPack` LÈVE au lieu d'avaler vers stderr (logcat sur l'appareil, jamais la console) |
| VIII/3 | le SERVICE distingue ses silences : `demon-build.vie` (pid, instant) posé à la première ligne d'`onCreate` — le client nomme « le processus :build n'a JAMAIS démarré (restriction ROM ou batterie ?) », « démarré, démon pas prêt » ou « port publié mais connexion refusée N fois » ; le bind 127.0.0.1 est vérifié sain (`getLoopbackAddress()`) |
| VIII/4 | R8 MINIFICATION (backlog P8, option A) : debug NON débogable (AGP désactive R8 sur les builds débogables ; `-Pdebuggable=true` localement), `-dontoptimize` documenté (le noyau a tué le démon R8 à 2,2-2,4 Go sur la machine 4 Go — rétrécissement + obfuscation portent l'essentiel), mémoire directe plafonnée, `lintVital` à la demande ; règles de conservation dédiées : paquets `dex`/`bundle`/`sign` (mur de chargeurs), `ApksigSigner` PAR NOM, pont `ChargeurDexAndroid`, `lsp4j` (réflexion Gson), `kotlinx.serialization`, métadonnées Kotlin, `layoutlib` ; chaque `-dontwarn` nomme sa raison (javac/StAX absents d'ART, pilote bureau du compilateur, « actual » fleet manquant) |
| VIII/5 | le RÉSULTAT : APK 35 012 865 → **11 782 666 o** (33,4 → 11,2 Mo, −66 % ; −92 % depuis ~150 Mo à 4 ABI), UN seul `classes.dex`, `mapping.txt` livré (retrace), signature v2 vérifiée, composants du manifeste présents, garde verte ; les packs INCHANGÉS (`dexPacksOutils` re-vérifie les empreintes épinglées — r8, bundletool, kotlin régénérés à l'identique) ; suites build-daemon/build-android/build-jvm au vert |

---

# CHANGES — Lot VII : le compilateur Kotlin en VM forkée, l'APK à 33,4 Mo (2026-11-17)

**Le mandat du propriétaire** (message du 2026-11-17) : « Continue
avec le prochain poste d'amaigrissement » — le poste désigné par
l'ADR-067 : le compilateur Kotlin (43,6 Mo de dex). Le lot VII
(ADR-069) prend la voie recommandée, la VM FORKÉE à la CodeAssist :
le compilateur quitte l'APK, une VM ouvrière non-zygote le charge
depuis un pack différé, et un build propre révèle la vraie taille
de l'APK — **33,4 Mo**, contre 134,3 au lot V.

| Étape | Contenu |
|---|---|
| VII/1 | l'OUVRIÈRE `KotlinWorkerMain` (source set `workerKotlin` de build-jvm, auto-contenu — aucune classe jo.joide.*) : boucle protocole ligne sur stdin/stdout (arguments en entrée, statut + diagnostics localisés en sortie, échappement des tabulations et sauts de ligne), VM RÉUTILISÉE entre les lots ; les arguments viennent de `KotlinArgs` et l'inventaire des classes écrites de `CompileInventory` — extraits du pilote embarqué, partagés, sans un seul type Kotlin |
| VII/2 | le PILOTE `KotlinForkedDriver` (build-android) : `dalvikvm64 -Xmx1g -cp <pack> KotlinWorkerMain`, aucune classe du compilateur ne touche l'app, la morte d'ouvrière se détecte (EOF) et se re-fork ; `BuildRunner` résout par SONDE `Class.forName(K2JVMCompiler)` — embarqué au bureau, forké sur l'appareil (la sonde ne regarde PAS la stdlib : elle reste embarquée, le constructeur du pilote embarqué ne lèverait pas) ; les fabriques reçoivent le pilote par `withKotlinDriver(…)` |
| VII/3 | le PACK `kotlin-compiler-2.3.0.dex.zip` (19 065 892 o, `ccc17bcd…767b28`, épinglé dans `PacksOutils.KOTLIN`) : l'ouvrière + le compilateur + sa FERMETURE TRANSITIVE du POM (stdlib, script-runtime, reflect — l'essai bout-en-bout a prouvé qu'il manque sinon —, daemon-embeddable, coroutines) + les RESSOURCES non-class (`.kotlin_module`) ; dexage PAR LOTS (6000 classes triées, renumérotation `classes.dex`, `classes2.dex`…) car D8 seul dépasse la mémoire d'une machine modeste ; déterministe — la tâche `dexPacksOutils` vérifie les empreintes, une divergence échoue |
| VII/4 | l'APK : exclusion `kotlin-compiler-embeddable` (seule route : ide-integration) — stdlib et kotlin-metadata-jvm restent (l'ABI `KotlinFacts` les lit) ; l'ouvrier voyage par un RELAIS `kotlinWorkerApi` (build-android → build-jvm, ADR-011) ; **la découverte** : l'empaquetage incrémental d'AGP laissait 58,3 Mo d'entrées ORPHELINES dans l'APK du lot VI (les octets du contenu retiré, morts entre les entrées vivantes) — un build propre fait la vérité : l'APK lot VII **33,4 Mo** (35 012 865 o), signature v2 vérifiée |

---

# CHANGES — Lot VI : la chaîne différée activée, le store qui se soigne, le service qui parle (2026-11-17)

**Le mandat du propriétaire** (message du 2026-11-17) : « Je
t'autorise à faire tout les travaux nécessaire, retirer les trois
implementation de jars d'outils, etc... Essayé aussi de corriger en
parallèle, cette erreur quand j'appuie sur build: service de build
injoignable (port non publié en 30 s) — relancez le build / cannot
create store schema ». Le lot VI (ADR-068) répond point par point —
les packs différés de la release `toolchain-v1` remplacent les jars
embarqués (APK : 134,3 → 96,7 Mo), le store survit à une base
corrompue et montre ses causes, et aucune faute de démarrage du
service `:build` ne reste muette.

| Étape | Contenu |
|---|---|
| VI/1 | la chaîne différée ACTIVÉE : `implementation(libs.r8)`, `libs.bundletool`, `libs.guava` partent du `build.gradle.kts` d'app-android (apksig reste, `toolsApi` garde les adaptateurs — leur échec de linkage EST le signal de repli pack de `BuildRunner.outil()`) ; l'APK perd 33,7 Mo de dex d'outils + 4 Mo de trous de packaging incrémental — 96,7 Mo |
| VI/2 | `StoreAndroid.open()` se soigne : le PRAGMA WAL devient non fatal (journal par défaut au pire), une base EXISTANTE qui rate son schéma part de côté une fois (`build.db.cassé-<ts>` + `-wal`/`-shm`) et une base neuve prend le relais — le store est un CACHE, sa perte coûte un build à froid, jamais « cannot create store schema » à chaque build ; les messages portent le fichier et la cause SQLite brute |
| VI/3 | `BuildService.onCreate()`/`onStartCommand()` gardés (`catch (Throwable)`) : notification refusée, politique de premier plan, canal absent — TOUT échec de démarrage écrit sa cause dans `demon-build.err` et s'arrête propre ; le client montre la cause pendant son attente, plus jamais trente secondes de « port non publié » muet |

---

# CHANGES — Lot V : le build qui part, le fournisseur qui persiste, l'APK qui maigrit (2026-11-17)

**Le mandat du propriétaire** (message du 2026-11-17) : « le build
ne fonctionne pas, j'ai des erreurs, peut-être que je n'utilise pas
la bonne approche et qu'il manque d'autres outils. Il y a aussi un
problème avec les fonctionnalités d'ia, j'ai choisi openrouter,
j'ai mis l'api key, je n'ai pas l'impression que la clé est
sauvegardée […] peu importe le provider choisi la carte erreur
affiche requête échoué no anthropic key. Pour l'abi, je veux
seulement arm64. L'apk mesure à peu près 150mb, j'aimerais savoir
qui est ce qui gonfle l'apk ainsi. Et aussi étudier un moyen pour
le rendre plus petit et l'application pourra télécharger ce qu'il
a besoin une seule fois à la première installation ». Le lot V
(ADR-067) répond point par point — le démon `:build` connaît enfin
son outillage, l'aapt2 s'extrait, la variante debug se signe,
AndroidX se résout, le fournisseur IA persiste au tap de puce,
l'ABI est arm64 seul, et la chaîne différée (packs de dex
téléchargeables une fois, la manière CodeAssist) est prête.

| Étape | Contenu |
|---|---|
| V/1 | `BuildService.onCreate()` pose `joide.toolchain.dir` et `joide.native.dir` DANS le processus `:build` — les propriétés système ne traversent pas les processus, le démon (moteur PAR DÉFAUT) cherchait `user.home` (absent sur ART) et ne voyait pas l'aapt2 embarqué : tout build Android via le service échouait |
| V/2 | `jniLibs.useLegacyPackaging = true` : `libaapt2.so` s'EXTRAIT vers `nativeLibraryDir` (seul répertoire où ART exécute — sans extraction le binaire n'existe jamais sur le disque) ; `abiFilters` = arm64-v8a seul, `fetchAapt2` purge les ABI retirées (~16 Mo gagnés sur l'APK 4-ABI) |
| V/3 | la variante debug SIGNE : `genererKeystoreDebug` (keytool, jamais commis) embarque l'asset `toolchain/debug.keystore`, `PreparationChaine` le copie vers `filesDir/toolchain`, `SpecsBuild.android()` le porte dans la variante — l'APK utilisateur s'installe directement (avant : `unsigned.apk`, ininstallable) |
| V/4 | Google Maven rejoint Maven Central dans le résolveur de l'app (`dl.google.com/android/maven2`) — AndroidX et `com.android.*` se résolvent, plus d'écart « non résolu, absent du classpath » |
| V/5 | le fournisseur IA PERSISTE au tap de puce (`choisirFournisseur` : modèle posé pour lui, partagé si le garde l'accepte, sinon première suggestion — jamais « claude-sonnet-4-5 » chez OpenRouter) ; l'état de clé se voit sous la rangée (tap → réglages) ; titre Gemini du dialogue de saisie ; URL libre dans la signature de la bascule ; le message parti pendant le bind asynchrone se retient et part à la liaison |
| V/6 | la chaîne DIFFÉRÉE : `PacksOutils` (packs de dex épinglés taille+SHA-256, téléchargés UNE fois), pont `ChargeurClassesOutil` (`DexClassLoader` dans app-android, ServiceLoader), repli de `BuildRunner.outil()` embarqué d'abord pack ensuite ; `:app-android:dexPacksOutils` génère les packs DÉTERMINISTES (r8 4,8 Mo, bundletool+guava 5,0 Mo) et échoue sur divergence des constantes — la publication dans la release `toolchain-v1` + le retrait des trois `implementation` de jars allègent l'APK de ~25 Mo (les étapes dans le commentaire du build.gradle.kts) |

---

# CHANGES — Lot U : le parcours complet (2026-11-17)

**Le mandat du propriétaire** (message du 2026-11-17) : « Oui pour
le flux OAuth d'interface pour compléter le parcours MCP. Ajoute
aussi openrouter dans le providers. Le emptyview quand aucun
onglets n'est ouvert, le tiroir pour l'arborescence des fichiers
du projet. J'ai appuyé sur build, ça dit que d8 et autre n'est
pas embarque ». Le lot U (ADR-066, note clean-room n° 6) répond
point par point — OpenRouter dans les fournisseurs, le tiroir de
l'arborescence avec son point d'entrée visible, l'état vide à une
source de vérité, la signature OAuth du parcours MCP, et la
chaîne Android EMBARQUÉE : d8, apksig, bundletool dans l'APK,
aapt2 en jniLibs — l'APK Android se construit sur l'appareil.

| Étape | Contenu | Commit |
|---|---|---|
| U/1 | OpenRouter rejoint les fournisseurs : l'enum s'allonge EN FIN (l'ordinal est le numéraire des favoris et récents), l'URL de base propre dans la fabrique du service, la rangée de clés dans Paramètres, la puce dans le sélecteur — les puces DÉFILENT horizontalement | `a47a139` |
| U/2 | le tiroir de l'arborescence a son point d'entrée VISIBLE sur téléphone (le dossier de la barre supérieure pousse l'arbre, le retour le referme) ; l'état vide du puits EST l'état sans onglet actif — `majPuits()` une seule source de vérité | `4815167` |
| U/3 | le flux OAuth d'interface : `FluxOAuthMcp` (une tentative par serveur, état anti-CSRF), « Se connecter par le navigateur » dans l'écran Connexions, l'écran de rappel exporté `joide://mcp/oauth`, le code échangé contre des jetons posés sous l'identité du serveur, la connexion repart en fond | `5160608` |
| U/4 | d8 et aapt2 EMBARQUÉS : la route `SpecsBuild.constituer()` ouvre le pipeline ANDROID sur appareil (manifeste, android-res, actifs, AAR extraits, variante debug) ; aapt2 en jniLibs par ABI (ReVanced, 16 Ko, ELF vérifié), toolsApi + r8 + apksig + bundletool dans l'APK, déxeur en processus (ServiceLoader puis réflexion), fork réservé aux JVM filles, android.jar et r8 téléchargés épinglés | `c8afaf9` |

---

# CHANGES — Lot T : les popups ancrés et la finition CodeAssist (2026-11-17)

**Le mandat du propriétaire** (message du 2026-11-17) : « L'écran
usages doit similaire a celui de CodeAssist, de même pour la
section thinking et aussi choix de modèles, utiliser des écrans
popups ancre. Tu as mon approbation pour le MPC ». Le lot T
(ADR-065, note clean-room n° 5) répond point par point — les six
écrans du tiroir ancrés à leur appelant, l'écran Usages à
compteurs et bandes, la réflexion vivante, le sélecteur de
modèles d'un tap, l'approbation MCP qui se souvient par projet.

| Étape | Contenu | Commit |
|---|---|---|
| T/1+2 | `PopupAncre` (la mécanique d'ancrage seule : tombée,
montée, hauteur bornée, tap extérieur), le menu ⋯ et le menu +
ancrés, l'historique et les connexions en popups ancrées, la
réflexion vivante (ouverte tant que le tour vit, pliée à la fin,
chevron), la ligne d'activité au-dessus du composeur (réflexion /
outil / rédaction + l'écart au pas d'une seconde), le pied d'usage
en jetons compacts | `ff5d5c1` |
| T/3 | l'écran Usages de la manière CodeAssist : le régulateur
SORT (`FournisseurBascule.occupationModele` → binder — l'écart de
l'ADR-064 se ferme), un COMPTEUR par fenêtre (requêtes / min,
jetons / min) avec pourcentage et bande colorée
(accent→alerte→erreur), les trois pieds compacts, le banc par
raison, Actualiser sans rouvrir | `81a7f66` |
| T/4 | le sélecteur de modèles en popup ancrée : fournisseurs
pointés, recherche, sections Favoris / Récents / Suggestions, la
coche du courant, l'étoile (glyphe ETOILE) qui bascule sans
fermer, favoris et récents persistés par fournisseur, le modèle
personnalisé garde son dialogue | `e76579a` |
| T/5 | l'approbation MCP persistée PAR PROJET, liée à la
signature SHA-256 du `.mcp.json` — le même fichier retrouve ses
serveurs, un fichier changé redemande le geste explicite | `e8a6084` |
| T/6 | l'ADR-065, la note clean-room n° 5, le journal, le
backlog (BANDES et approbation MCP fermés), la maquette aux vues
du lot T | ce commit |

Tests : `:app-android:testDebugUnitTest` et `:agent:test` verts
(MaquetteAgentTest éprouve les vues du lot T).

---

# CHANGES — Lot S : l'agent complet (2026-11-17)

**Le retour du propriétaire sur la V2** (message du 2026-11-17) :
« Le design est peu brute, manque de finesse, compacité. Pour IA,
il manque plusieurs écrans et fonctionnalités, les écrans popups
[…]
Les options de new chat, history, manage provider, connection,
usages, etc.. Les options de add (image, take photo, current file
ou projet ». Le lot S (ADR-064, note clean-room n° 4) répond point
par point — cinq écrans au menu ⋯, quatre attachements au bouton
+, une passe de compacité aux jetons Graphite.

| Étape | Contenu | Commit |
|---|---|---|
| S/1 | le menu ⋯, les conversations multiples (new chat,
historique : reprendre/supprimer, migration de la session unique
du lot P, plafond 50 avec éviction) et la correction des rôles
restaurés (une réponse n'est plus rejouée en message) | `2bd1b82` |
| S/2 | le fournisseur et le modèle IN-SITU (l'écart du lot P
fermé : point de sélection, état de clé, URL) et l'écran
Usages (usage cumulé, banc de clés par raison) | `1e087a2` |
| S/3 | les connexions MCP câblées dans l'hôte : lecture
bornée du `.mcp.json`, écran Connexions, geste d'approbation
explicite, outils distants dans le port du tour | `f11dc6d` |
| S/4 | les attachements : image (SAF, 8 Mio), photo
(FournisseurCameraAgent, zéro AndroidX), fichier courant en bloc
`<file>`, arbre en `<project_tree>`, réducteur BitmapFactory
(1 568 px, JPEG 85), multimodal jusqu'aux fournisseurs,
pixels persistés dans la session | `9c00313` |
| S/5 | la finesse (en-tête compact, bulles au coin d'auteur,
pilule de saisie, état vide à glyphe), la maquette enrichie et
son test de capture, l'ADR-064, la note clean-room n° 4 | ce commit |

Tests : ConversationsAgentTest (9), PieceJointeTest (8),
MaquetteAgentTest (9), EtatAgentChatTest (16) —
`:app-android:testDebugUnitTest` et `:agent:test` verts.

---

# CHANGES — Correction de l'atelier sourd : le rideau des tiroirs (2026-11-17)

**Le symptôme** (rapport du propriétaire) : l'écran atelier d'un
projet ouvert ne réagit plus au toucher PARTOUT — onglets, arbre,
barre supérieure, éditeur — sauf le FAB Construire. « Le focus est
occupé, repris ailleurs » : en réalité ce n'était pas le focus,
c'était la DÉLIVRANCE des touches qui était capturée avant l'atelier.

**La cause** (PIEGE-31, `TiroirPousse`) : le rideau du tiroir porte
`setOnClickListener` (fermeture au toucher du rideau) — or sous
Android, `setOnClickListener` rend la vue CLIQUABLE par effet de bord
(contrat `View.setOnClickListener`). Le rideau fermé restait donc
cliquable, et un rideau invisible (alpha 0) de MATCH_PARENT posé sur
toute la racine AVALE chaque ACTION_DOWN avant qu'il n'atteigne le
contenu : la hiérarchie atelier — deux tiroirs plein écran (arbre
sur téléphone, agent aux deux tailles) posés AVANT le FAB — rendait
tout l'écran muet, le FAB ajouté APRÈS restant au-dessus de la pile et
seul vivant. Latent depuis le lot G : l'atelier a toujours été
sourd avant le premier cycle ouvrir/fermer du tiroir.

**La correction** (`app-android/ui/TiroirPousse.java`) : la
cliquabilité du rideau suit strictement l'état — muet à la
construction (l'ordre du code : listener PUIS `setClickable(false)`),
allumé à l'ouverture, éteint DÈS le début de la fermeture, et
ré-éteint à chaque layout fermé (défense en profondeur). Aucune API
changée, aucun appelant retouché.

| Zone | Fichiers | Nature du changement |
|---|---|---|
| app-android | `ui/TiroirPousse.java` | le rideau fermé n'est plus cliquable (PIEGE-31) — l'atelier reçoit ses touches |
| Racine | `CHANGES.md` | le présent filet |

# CHANGES — Lots Q et R : la robustesse (Gemini, banc de clés, bascule, régulateur) et le MCP (ADR-É10/061/062/063)

Les lots Q et R clôt la piste 2 du plan ADR-É10 : l'agent v1.1 —
trois fournisseurs, la robustesse à trois étages complète, et
l'écosystème MCP dans ses deux sens. La consultation clean-room n° 3
a été consignée au préalable (mandat explicite du propriétaire :
regarder le design de CodeAssist, le journal et la note
`docs/clean-room/003-robustesse-mcp.md` portent la traçabilité),
l'implémentation reste clean-room — Java 17 pur module `agent`,
identifiants ASCII, Javadoc française, le graphe des modules et le
catalogue des dépendances INCHANGÉS.

## Lot Q — Robustesse et Gemini (ADR-061/062)

**Le fournisseur Gemini** (ADR-061) : `streamGenerateContent?alt=sse`
sur l'URL officielle surchargeable, la clé par en-tête
`x-goog-api-key` (jamais dans l'URL) ; rôles `user`/`model`, les
résultats d'outils en parts `functionResponse` (nom de corrélation
avant `#`), les appels ENTIERS en parts `functionCall` (jamais
diffusés) ; la `thoughtSignature` se range dans la partie appel
(fabrique `PartieContenu.appelSigne`, signature opaque) et revient
sur la `functionCall` au tour suivant — sans elle l'outil est refusé ;
pas d'identifiant d'appel chez Gemini : le nom sert de corrélation,
dédoublonné par suffixe `#N` ; pas de rôle système en conversation :
l'ancrage stable part en `system_instruction`, la note volatile se
plie en part `<system-reminder>` du DERNIER tour utilisateur — le
préfixe stable ne dérive pas (le garde du test le prouve) ;
`usageMetadata` : `promptTokenCount` CONTIENT les lectures de cache,
l'adaptateur soustrait et le modèle neutre garde les entrées non
cachées ; pas d'événement terminal : usage et fin s'émettent à la fin
du flux, un flux qui n'a rien rendu est ILLISIBLE (jamais un tour
vide muet que la boucle lirait comme une fin), les `finishReason` de
sécurité classent en `CONTENU_REFUSE` actionnable ; les schémas
d'outils perdent `additionalProperties` à toute profondeur (Gemini
les rejette).

**Le cache de contexte serveur** (`CacheContexteGemini`, horloge
injectée) : l'instruction système + les déclarations d'outils se
posent côté fournisseur (`cachedContents`, TTL 3 600 s) et se
référencent par `cachedContent` — le système et les outils ne repartent
PLUS inline quand le cache est référencé (l'API le rejette). La
politique est conservatrice : rien au premier tour, pas de création
sous 2 048 jetons estimés, repli inline sur tout échec ET échec
mémorisé par clé de charge utile, recréation une minute AVANT
l'expiration — le caching ne peut jamais être une perte nette.
`FournisseurGemini` déclare `compactionDeleguee() = true` : l'élagage
local se désactive quand ce fournisseur est choisi.

**Les budgets de réflexion** au dialecte de la génération : niveau
(`thinkingLevel`) pour Gemini 3, budget (`thinkingBudget`) pour la
famille 2.5 — envoyer le mauvais échoue la requête entière ; `minimal`
devient `low` sur un Pro 3, le minimum 128 force le budget sur un Pro
2.5 ; `maxOutputTokens` couvre réflexion ET réponse ENSEMBLE (la
marge de réflexion s'ajoute au plafond demandé, plafond absolu
65 536) — sinon un effort élevé dépense tout le plafond en réflexion
et rend un tour vide coupé à `MAX_TOKENS`.

**Le banc de clés** (`BancCles`) : l'ordre de l'utilisateur
(principale d'abord, blâmes et doublons écartés) — un failover, pas
un round-robin, la clé principale revient dès son temps écoulé ; les
bancs différenciés par nature de limite : minute → le délai ÉNONCÉ
sinon 60 s (jamais moins d'une seconde), jour/quota/hors-plan → 1 h,
auth → À VIE (le banc se reconstruit à l'édition des clés) ; seuls
les genres qui peuvent différer d'une clé à l'autre font basculer.

**La bascule** (`FournisseurBascule`, décorateur `FournisseurLlm`) :
une requête refusée AVANT TOUTE SORTIE repart sur la clé suivante,
JAMAIS après la première sortie (le texte déjà affiché ne se duplique
pas) ; quand tout le banc est assis : l'attente courte (≤ 120 s)
devient une `LIMITE_DEBIT` porteuse du délai que la boucle attend
visiblement, sinon le message agrégé honnête compte les clés par
raison et conseille quoi faire ; un banc d'une clé est un
pass-through honnête ; la régulation préventive vit DANS la bascule
(un `RegulateurDebit` par couple clé+modèle, l'attente courte est
découpée pour traverser l'annulation).

**Le régulateur préventif** (`RegulateurDebit`) : attendre AVANT la
requête au lieu de récolter un 429 — fenêtre glissante de 60 s sur
requêtes ET jetons d'entrée estimés (≈ 4 caractères/jeton, image ≈
1 500) ; les limites du plus spécifique au plus général : configurées,
puis ÉNONCÉES par le fournisseur (`QuotaFournisseur` porté par
l'erreur — la révision N/O), puis le plafond OBSERVÉ (jetons ×9/10,
requêtes en vol − 1) ; sans limite connue, il n'attend jamais ; les
limites apprises SURVIVENT à la conversation (le régulateur vit hors
du tour — le critère du lot).

**Les bandes d'usage** (`BandesUsage`) : NORMALE (< 70 %),
ATTENTION (70–90 %), CRITIQUE (≥ 90 %) sur l'occupation des fenêtres,
la requête à venir comptée — prévenir AVANT ; sans plafond connu,
l'occupation est 0 et la bande est NORMALE (une alerte sans référence
mesurée serait une invention).

**Les images** : le genre `IMAGE` du modèle neutre
(`PartieContenu.image`, type de média + base64) sérialisé chez les
trois fournisseurs (bloc `image` Anthropic, `data:` URI OpenAI,
`inline_data` Gemini) — avant, une partie image était avalée
muettement ; la réduction se fait CÔTÉ CLIENT AVANT l'historique
(`ReductionImages`, budget d'octets décodés, ré-encodage par le
`Reducteur` de l'hôte — BitmapFactory côté Android), le repli honnête
remplace l'image par une note qui dit ce qui s'est passé — jamais une
omission muette.

**La révision N/O qui porte le lot** : `ErreurLlm` transporte le
`QuotaFournisseur` énoncé (métrique requêtes/jetons, fenêtre
minute/jour, limite — champ transient) ; le 429 à PLAFOND ZÉRO est le
modèle hors plan de la clé (`QUOTA_EPUISE` — le cas d'école de
l'ADR-É9 : la bascule changera de clé, retenter ne sert à rien) ; la
précédence du wording crédit/quota est explicitée (un 429 rate limit
qui cite le crédit reste transitoire) ; le raisonnement Anthropic est
tagué `anthropic:` et ne repart que chez lui (le même garde que celui
d'OpenAI du lot N) — une signature d'un autre fournisseur serait
refusée par l'API.

**Le câblage hôte** (app-android) : `Reglages.FournisseurIa` gagne
GEMINI ; la rangée de clé Gemini rejoint la section IA des Paramètres
(vingt rangées, libellés fr/en — la parité values/values-fr) ;
le champ clé du stockage chiffré porte PLUSIEURS clés séparées par
saut de ligne ou virgule ; `AgentService` enveloppe le fournisseur
dans `BancCles` + `FournisseurBascule` DÉTENUS PAR SERVICE (les
limites apprises survivent à la conversation), reconstruits seulement
quand le fournisseur ou l'ensemble des clés change.

## Lot R — MCP client puis serveur (ADR-063)

**Le paquet `jo.joide.agent.mcp`**, pur module : aucune dépendance de
plus, le graphe des modules et le catalogue inchangés.

**Le port transport s'étend ADDITIVEMENT** : `postOuvert` (le corps
se lit ligne à ligne — le Streamable HTTP décide au vu des EN-TÊTES
si le corps est un JSON complet ou un flux d'événements et s'arrête à
SA réponse), `getSse` (le flux vivant du repli), `supprimer` (le
DELETE de clôture) — défauts qui refusent honnêtement, implémentés
par OkHttp et le faux de test ; la règle du lot N tient (hors 2xx,
`EchecHttp` voyage avec son corps).

**Le client** (`ClientMcp`) : la poignée de main `initialize`
(protocole 2025-06-18 proposé, celui du serveur fixé, AUCUNE
capacité déclarée — un `ping` reçoit sa réponse vide, le reste
method-not-found) ; `tools/list` paginé mais BORNÉ (vingt pages) ;
`tools/call` aplati : blocs texte joints, images supportées listées,
ressources texte ou notées binaires, liens et audio DÉCRITS plutôt
que perdus, contenu structuré seulement sans texte, plafond
40 000 caractères avec coupure qui conseille, `isError` honnête ;
délais 30 s de poignée, 180 s d'appel ; les demandes du serveur
répondent hors fil ; la déconnexion complète les appels en attente
avec la cause.

**LA règle de sécurité** (`GestionnaireMcp`) : un appel ne se retente
que si le serveur a PROUVÉ qu'il n'a pas exécuté l'outil — session
expirée (une reconnexion), identifiants refusés avant examen (un
refresh de signature) ; toute autre rupture EN COURS d'appel répond
« il a pu s'exécuter ou non » — un outil mutant n'est JAMAIS lancé
deux fois (le test le prouve par comptage) ; un échec JSON-RPC ou un
délai ne se retentent pas non plus ; les états publiés
(DESACTIVE/CONNEXION/CONNECTE/AUTH_REQUISE/ECHEC/NON_SUPPORTE) et la
version d'outils incrémentée : l'UI affiche sans déduire ; les
connexions vont EN ARRIÈRE-PLAN — un serveur lent ne retient jamais
le chat ; le repli SSE historique se tente une fois pour un refus 4xx
du Streamable, l'échec ORIGINAL rapporté si le repli échoue aussi.

**Les transports** : Streamable HTTP (POST unique, session en
`Mcp-Session-Id`, `MCP-Protocol-Version` après négociation, DELETE
best-effort daemon, PAS de flux GET permanent) et le vieux SSE
historique (GET flux + `endpoint` + POST). La configuration lit le
format `.mcp.json` Claude Code/Cursor (racine `mcpServers` ou
`servers`, inférence URL/commande, `headers`, `disabled`, entrées
sans sens sautées) ; l'IDENTITÉ est l'URL ou la commande+arguments —
les signatures et l'approbation se lient à elle ; stdio est lu mais
jamais lancé ici (NON_SUPPORTE honnête sur Android).

**OAuth 2.1 complet** (`AutorisationMcp`) : RFC 9728 (métadonnées de
ressource protégée, du défi du 401 puis les emplacements bien connus)
→ RFC 8414/OpenID → RFC 7591 (enregistrement dynamique comme client
public pour l'URI de redirection de CETTE signature) → PKCE S256
OBLIGATOIRE (le vecteur de test de la RFC 7636 prouve le calcul ;
un serveur sans S256 est refusé avec le conseil du jeton en en-tête)
→ RFC 8707 (`resource`, l'URL canonique comme audience) → échange et
refresh (rotation tolérée). Les secrets partent en corps
form-encodé, JAMAIS dans une URL ; la signature se stocke chiffrée
côté hôte et se rafraîchit avant l'échec (périssable à une minute de
son expiration).

**La normalisation des schémas tiers** (`NormalisationSchemas`) :
`$ref` inlinés bornés en profondeur (12), tableaux de types réduits
au premier non-null, enums de CHAÎNES seulement, `anyOf` consolidé,
`items` par défaut pour les tableaux, liste blanche de clés,
niveau supérieur FORCÉ en objet — un mauvais schéma ferait échouer la
requête entière du fournisseur.

**Les outils distants** (`OutilMcpDistant`) : nommés
`mcp__<serveur>__<outil>` dans les caractères que tous les
fournisseurs acceptent, 64 au plus, dédoublonnés par suffixe (deux
serveurs dont les noms se nettoient pareil survivent tous deux) ;
MUTANTS sauf `readOnlyHint` — soumis au même gate que les écritures
locales ; la description préfixée du serveur, tronquée à 1 000 ; le
résumé d'arguments porte les trois premiers scalaires.

**Le serveur local** (`ServeurMcpLocal`) : `ServerSocket` lié à
127.0.0.1 UNIQUEMENT (non configurable par intention — le transport
n'authentifie personne et ses outils éditent le projet) ; la
vérification LOCALE couvre le DNS rebinding : `Host` local obligatoire
et un `Origin` PRÉSENT doit être local (absent = client natif accepté,
présent et étranger = 403 — le test braque un attaquant et le refuse) ;
corps plafonné à 16 Mio, socket à 30 s, Connection: close, un fil par
connexion ; la lecture d'en-tête OCTET PAR OCTET en ISO-8859-1 et le
corps de EXACTEMENT `Content-Length` octets (la leçon consignée d'un
emoji qui bloquait la lecture jusqu'au délai de socket — le test renvoie
un résultat à caractères multi-octets) ; sessions créées à
l'initialize (`Mcp-Session-Id`), mode requête-réponse, GET 405 (les
clients SDK replient), DELETE ferme la session ; les annotations du
protocole se déduisent du modèle (`mutant` → `readOnlyHint: false`) ;
classes plutôt que records (l'API 28 n'a pas de `java.lang.Record`,
le checkApi28 l'a prouvé).

**Les jobs longs découplés** : au-delà du seuil (90 s), le travail
CONTINUE sur un pool borné et le client reçoit « toujours en cours —
appelez `task_status`, NE RELANCEZ PAS » ; `task_status` est une
pseudo-outil read-only consultable sans limite, elle rend le résultat
ou l'erreur du job quand il finit — le test prouve que le travail a
fini TOUT SEUL sans que l'outil ait été relancé.

## Acceptation

- `:agent:test` 180/0 — 80 tests nouveaux au lot (Gemini, banc,
  bascule, régulateur, bandes, images, config MCP, normalisation,
  autorisation, client↔serveur RÉELS en JVM, gestionnaire) et la
  taxonomie enrichie de la révision N/O ; les critères du lot :
  tests à horloge fausse (failover, pacer, TTL du cache), l'usage
  sommé de toutes les requêtes du tour (déjà prouvé côté boucle),
  les limites apprises qui survivent à la conversation, un outil
  mutant distant jamais lancé deux fois.
- `:app-android:testDebugUnitTest` 263/0 (vingt rangées exactement)
  ; `:check` complet vert AVEC SDK (tous les modules) et SANS SDK ;
  `checkApi28` vert après la conversion des records en classes ;
  politique de langue : 0 problème (typographie --fix appliquée aux
  entrées nouvelles).

# CHANGES — Lot P : l'UI du chat et l'intégration applicative (groupe 4 de la V2, ADR-É11/060)

Le lot P clôt le groupe 4 « agent v1 » du plan ADR-É10 : l'agent
des lots N et O reçoit sa face. D'abord l'ADR-É11, écrite ce lot
sur la méthode de la série — le design de l'écran de l'agent
(tiroir chat dans l'atelier, cartes d'outils pliables, diff Myers
borné, dialogue de permission sur diff déplié à défaut sûr, cartes
de questions, usage par tour, erreurs actionnables) avec sa
maquette design/agent.html (trois vues : conversation en cours,
permission sur diff, fin de tour), la carte Agent ajoutée à
l'index général et le lien aux navigations des maquettes
existante. Puis l'implémentation : le DIFF RÉEL — DiffMyers
(module agent, pur) : Myers en avant avec trace, coupe commune
préfixe/suffixe, budget d'édition borné (800) et repli honnête
NOMMÉ au-delà (« fichier trop remanié pour un diff fin »), sortie
unifiée à deux lignes de contexte, bornée à 400 lignes /
20 000 caractères, amputation annoncée — éprouvé par
RECONSTRUCTION (appliquer le diff à AVANT redonne APRÈS, fuzz de
200 essais aléatoires) ; l'exécuteur d'outils calcule l'aperçu du
VRAI contenu pour write/create/edit/write_memory par un
LecteurContenu injecté (lecture overlay-preferred de l'espace),
les opérations nommées gardent le résumé d'arguments — la
permission porte sur l'édition réelle, jamais sur un nom de
fichier (la leçon du risque n° 3 de l'ADR-É9) ;
Dialogue.autorisation : corps libre (le diff déplié en mono),
Refuser / Appliquer, et TOUTE annulation (croix, extérieur,
retour) répond REFUSER — le défaut sûr est dans le cadre.
L'ÉTAT PUR DU TIROIR : EtatAgentChat (app-android, JVM) consomme
les EvenementAgent et construit le transcript carte par carte
(message utilisateur, réponse composée en direct, raisonnement
replié à n lignes rouvrable, carte d'outil à trois états avec
résultat borné pliable, fichiers changés, attente décomptée,
compaction chiffrée, ligne d'usage à TROIS pieds + requêtes,
erreur actionnable) ; les QUATORZE genres d'erreur ont leur verbe
(OUVRIR_REGLAGES pour les clés, CHANGER_MODELE pour le 404,
ATTENDRE pour les retentables — jamais une carte sans action) ;
ask_user arrive par la CARTE (options en puces, Autre, Passer,
réponses une par ligne « question : réponse ») ; le transcript est
plafonné à 200 cartes avec éviction annoncée des plus anciennes
terminées ; le journal se REJOUE à l'identique (éprouvé). LE
SERVICE DE PREMIER PLAN : AgentService — le tour vit dans le
service, pas dans l'écran (pattern ADR-043) : notification
silencieuse « l'agent travaille » à bouton Arrêter, journal
ordonné rejouable, session persistée par SessionsAgent à chaque
fin de tour (historique à signatures, compactés PERMANENTS
restaurés par le nouveau constructeur), et les DEUX blocages
honnêtes par futures : la permission et les questions se résolvent
par l'UI quand elle est là, par le DÉFAUT SÛR sans elle ou après
120 s (refuser / passer) — le bouton Arrêter annule le CancelToken
ET débrique les futures ; l'arrêt mi-outils laisse la carte de
l'outil EN COURS (jamais un résultat inventé), la réparation
d'historique apparie les appels interrompus, la conversation
reprend au message suivant. LES CLÉS CHIFFRÉES (D-6, ADR-058) :
ClesChiffrees (pur, testé JVM avec clé forgée) — format
joide.cles/1, AES-GCM à IV aléatoire par écriture (deux poses du
même texte DIFFÈRENT, éprouvé), écriture atomique, un fichier par
fournisseur neutralisé ; KeystoreCles (Android, mince) fabrique ou
retrouve la clé Keystore (alias joide.cles, AES-256 GCM), un
Keystore cassé retombe sur une clé mémoire — l'application
démarre toujours, un fichier d'une autre clé est un absent honnête
(réinstallation). LA SECTION IA DES PARAMÈTRES : quatre rangées
réelles (clé Anthropic, clé OpenAI-compatible, modèle par défaut,
URL de base) avec état affiché (« posée »/« absente » — jamais la
clé en clair), effacement destructeur confirmé, tolérance totale
de lecture (une valeur d'une version future retombe sur son
défaut), bornes d'écriture (modèle jamais vide ≤ 100, URL http(s)
ou vide ≤ 500) ; Reglages gagne fournisseurIa/modeleIa/urlIa ; la
puce du tiroir affiche le modèle, la puce de mode change la
permission par tour (Demander à chaque / Acceptation auto / Plan
seul). LE TIROIR DANS L'ATELIER : glyphe BULLE dans la barre
supérieure avec point d'activité pendant le tour, tiroir poussé
depuis la droite (360 dp) monté sur la racine, le retour le ferme
AVANT les autres surcouches ; VueAgent (vue mince) rend l'état :
cartes, composeur désactivé pendant le tour (l'arrêt est l'action
disponible), préremplissage « Demander à l'IA » (l'entrée du menu
⋮ quote le fichier ACTIF borné à 8 000 caractères — la sélection
précise attend l'API celeris, écart consigné ADR-060 §7 et
backlog). Écarts consignés (ADR-060 §8) : l'amarrage grand écran
du tiroir (v1 pousse aux deux tailles), le sélecteur in-situ, la
preuve appareil de la liaison Keystore, run_task/add_dependency au
défaut dégradé tant que l'atelier n'injecte pas l'exécuteur borné
et le résolveur P4 au service. Acceptation : check vert sans SDK
(politique de langue 0 problème — lint NewApi bloquant : les
String#lines() API 33 remplacés par un comptage manuel), 100 tests
agent 0 échec (15 nouveaux : DiffMyers 10, ApercuPermission 5), 72
tests ide-integration 0 échec, 263 tests app-android 0 échec (36
nouveaux : EtatAgentChat 13, MaquetteAgent 8 — capture contre la
maquette, ClesChiffrees 9, Reglages +6) ; ADR-É11 + maquette +
ADR-060, NOTICE inchangée (aucune dépendance nouvelle) ; arrêt
pour validation du propriétaire — le groupe 4 est complet (jalon
« agent v1 » : discuter avec l'agent, lui voir éditer le projet
avec permission, annuler un tour), la suite est le lot Q
(robustesse et Gemini) puis R (MCP, D-4).

## Ajoutés

| Zone | Fichiers |
|---|---|
| agent `espace/` | `EspaceTravailInjecteur` (le délégué explicite — une signature ajoutée au port casse la compilation) |
| agent | `DiffMyers` (Myers borné, repli honnête nommé, reconstruction éprouvée) |
| app-android `atelier/agent/` | `EtatAgentChat` (transcript par événements, 14 verbes d'erreur, questions, permission, plafond annoncé), `VueAgent` (le tiroir : cartes, composeur/arrêt, puces) |
| app-android racine | `AgentService` (premier plan, journal rejouable, session persistée, deux blocages par futures à défaut sûr) |
| app-android `parametres/` | `ClesChiffrees` (format joide.cles/1, AES-GCM, IV aléatoire — pur), `KeystoreCles` (la clé Keystore, mince) |
| app-android `ui/` | glyphe `BULLE` dans `Icone` ; `Dialogue.autorisation` (fermer = refuser dans le cadre) |
| docs/ecrans | `011-agent-ecran.md` (ADR-É11) + `design/agent.html` (maquette, trois vues) |
| docs/adr | `060-lot-p-ui-chat-et-integration.md` |
| app-android `src/test` | `DiffMyersTest` (10), `ApercuPermissionTest` (5), `EtatAgentChatTest` (13), `MaquetteAgentTest` (8), `ClesChiffreesTest` (9), `ReglagesTest` (+6) |

## Modifiés

- `ExecuteurOutils` : le lecteur de contenu injecté, l'aperçu
  DIFF RÉEL pour write/create/edit/write_memory, les opérations
  nommées gardent le résumé ; `EspaceTravailAgent` porte
  FICHIER_MEMOIRE.
- `HistoriqueAgent` : constructeur (messages, compactes) — les
  décisions de compaction survivent à la relecture de session.
- `EcranParametres`/`EtatParametres` : la section IA (six
  sections, dix-neuf rangées), l'état des clés lu du stockage
  chiffré, les dialogues poser/remplacer/effacer et saisies
  bornées.
- `EcranAtelier` : glyphe agent + point d'activité, tiroir agent,
  liaison du service, dialogue de permission, « Demander à l'IA »
  au menu ⋮, le retour ferme le tiroir d'abord.
- `Reglages` : fournisseurIa, modeleIa, urlIa (bornes et tolérance).
- Manifeste : service AgentService (dataSync) + permission
  FOREGROUND_SERVICE_DATA_SYNC ; chaînes agent fr/en (~50 clés).

# CHANGES — Lots M, N et O : les Paramètres, le noyau agent, les outils (groupes 3 et 4-amorce de la V2)

Le propriétaire a fusionné deux jalons en un GO (« Go pour M et
N/O ») : le groupe 3 (lot M — la v2 est complète) et l'amorce du
groupe 4 (lots N et O — le noyau de l'agent et sa surface d'outils ;
le lot P reste pour clore le jalon « agent v1 »). Lot M (ADR-É8,
ADR-054) : EcranParametres — cinq sections, quinze rangées, RÉGLAGES
RÉELS SEULEMENT (un réglage = une rangée + un consommateur : la
« police de code » et le « thème de syntaxe » de l'ADR-É8 n'ont ni
alternative modifiable ni consommateur sur l'appareil, les rangées
N'EXISTENT PAS — écart consigné, elles naîtront avec leurs
consommateurs) ; Reglages (pur) porte le schéma joide.reglages/1 en
préférences privées avec TOLÉRANCE TOTALE (une valeur absente,
illisible ou hors bornes retombe sur son défaut, jamais une
exception — des préférences d'une version future ne doivent pas
empêcher le démarrage), bornes publiques partagées par les steppers
et les tests (échelle UI 85-130 % au pas de 5, taille du code 10-20
sp au pas de 0,5, fils 1-8) ; le thème (SOMBRE le défaut, CLAIR,
SYSTEME) et l'accent (Nuancier, cinq rôles, Graphite.avecAccent — un
seul jeton réellement appliqué) sont lus par GraphiteVue.palette à
chaque appel, un changement recrée l'écran (jamais une vue
re-stylée à moitié), l'échelle du texte porte la taille ET
l'interligne ; MesuresStockage mesure AU DISQUE les trois valeurs
réelles (projets importés, cache CIDX reconstruit à la prochaine
synchronisation, dépendances m2 re-téléchargées à la demande ;
plafond 100 000 entrées), le vidage supprime le CONTENU jamais le
répertoire (les moteurs le recréent), les deux vidages sont les
seules actions destructrices, chacune avec SA phrase de conséquence
exacte dans les deux langues ; le journal de diagnostic (logcat -d
-t 1000 — depuis Android 4.1 un processus sans READ_LOGS ne voit que
ses propres lignes, le service « :build » partage l'UID) est borné
aux 400 dernières lignes NON VIDES de 2 000 caractères, ellipse
annoncée, un journal vide s'affiche vide, une commande qui échoue
est une erreur nommée ; la rangée Version porte versionName et le
SHA court du commit injecté à la construction (« dev » hors dépôt),
LICENSE et NOTICE sont empaquetés en assets par la tâche
copierLicences (jamais une copie à la main), le lien dépôt dit son
URL en dialogue de repli si aucun navigateur ; le moteur par défaut
et les fils par build DÉMÉNAGENT du menu ⋮ de l'atelier (la case «
en processus » du lot K disparaît, ses chaînes mortes aussi) :
l'atelier relit le moteur à CHAQUE build, le BuildRunner en
processus prend les fils des réglages, le service « :build » les lit
à son prochain démarrage (sa légende le dit — jamais une promesse
cachée) ; les réglages d'éditeur s'appliquent au montage de la
session celeris. Trois composants entrent au kit : Stepper (bornes
posées, bouton hors borne désactivé), Interrupteur (piste 46×27,
pouce animé 180 ms), Nuancier (pastilles 26 dp, la choisie liserée).
Lot N (ADR-É9, ADR-055/056/057/058) : le module agent, PUR, pair de
core-tasks (agent -> core-platform core-deps), testable sans SDK —
le contrôle API 28 s'y applique (ni java.net.http, ni records ni
sealed, ni Files.readString) ; le MODÈLE NEUTRE à signatures
opaques (PartieContenu quatre genres, MessageAgent, RequeteLlm,
EvenementFlux — chaque partie RAISONNEMENT porte la signature de son
fournisseur que le noyau ne fait que transporter, jamais
interpréter : l'aller-retour conversation-fournisseur ne perd rien) ;
la BOUCLE : prompt COUPÉ PAR VOLATILITÉ (l'ancrage stable en préfixe
système écrit une fois, l'état volatile en note de FIN rafraîchie à
chaque itération sans toucher un octet en amont — chez Anthropic la
note vit en second bloc système APRÈS le point de rupture
cache_control, chez OpenAI-compatible en dernier message système),
historique APPEND-ONLY, lecture parallèle / mutation séquentielle,
réparation d'historique, attentes visibles, plafond d'itérations en
passation ; le GARDE-FOU « préfixe stable » (empreinteStable : la
requête N+1 doit contenir la requête N octet pour octet en préfixe —
une dérive silencieuse ne se voit que sur la facture, le test échoue
à sa place) ; la COMPACTION à hystérésis (rien n'est élagué sous
120 000 caractères ; au déclenchement une passe tronque à 4 000
caractères les résultats d'outils plus vieux que les quatre plus
récents, les textes utilisateur et assistant jamais touchés, la
troncature S'ANNONCE, les décisions sont PERMANENTES — un résultat
réélargi casserait le préfixe caché) ; la TAXONOMIE d'erreurs
actionnables (quatorze genres, chacun sait s'il se retente ; les
429 sont désambiguïsés) ; les PERMISSIONS (gate, trois modes) ; les
SESSIONS en double moitié (transcript + historique modèle, écriture
atomique) ; DEUX FOURNISSEURS : Anthropic (Messages API) et
OpenAI-compatible (Chat Completions, URL de base libre — couvre les
passerelles et les serveurs locaux), testés hors ligne par trames
SSE rejouées sur un faux transport ; le TRANSPORT est OkHttp 5.5.1
(décision D-5 du propriétaire, ADR-056 — la seule dépendance tierce
du module, NOTICE et docs/third-party.md à jour ; le transport du
résolveur P4 reste maison), derrière le port TransportHttp
(postSse/post/get), le parseur SSE est MAISON (deltas, usages,
EchecHttp qui porte statut + en-têtes + corps jusqu'à la taxonomie,
jamais jeté) ; le JSON est le parseur maison (ADR-028) complété par
JsonEcrire (écriture compacte, aller-retour garanti par
construction) ; AUCUNE CLÉ dans le dépôt ni dans les tests (les
clés de test sont des constantes visiblement factices), une clé
absente produit CLE_MANQUANTE qui dit où la poser, le port PortCles
est verrouillé par l'ADR-058 (clé dans Android Keystore, fichier
AES-GCM par AtomicFiles, jamais de clés de secours en clair —
l'implémentation est un composant Android, lot P). Lot O (ADR-É9,
ADR-059) : le port EspaceTravailAgent est le SEUL contact entre
l'agent et le projet (lectures overlay-preferred — le texte de
l'éditeur ouvert prime sur le disque, recherche, diagnostics,
écritures, exécution bornée, mémoire, questions), les méthodes que
l'hôte ne sert pas ont un DÉFAUT DÉGRADÉ EspaceNonSupporte que
l'exécuteur rend HONNÊTEMENT au modèle (« non disponible ici ») —
jamais une liste vide qui se lirait « rien trouvé » ;
l'implémentation EspaceTravailIde (ide-integration) sert ce que les
briques servent VRAIMENT : diagnostics et définitions par
MoteursLangage (moteur Java réel), fichiers par java.nio sous la
racine, l'overlay de l'éditeur, l'exécution bornée, la résolution P4
et l'édition du modèle injectées (l'atelier du lot P y branchera ses
propres dépôts) — références, renommage et formatage restent au
défaut dégradé (les vides honnêtes du lot D) ; le CONFINEMENT PAR
CONSTRUCTION (la leçon du risque n° 2 de l'ADR-É9) : tout chemin
d'outil passe par CheminsConfines (relatif, sans .., sans racine ni
~ ni lettre de lecteur, sans séparateur retour, 300 caractères au
plus, segments de contrôle refusés) AVANT d'atteindre le port, un
chemin refusé ne touche JAMAIS le disque (éprouvé par test :
l'arborescence du faux projet reste vide), et l'implémentation
REVÉRIFIE (resolve + normalize + startsWith) — la défense en
profondeur ; la SURFACE V1 : vingt-cinq outils assumés des 42 de
CodeAssist (lecture 6, écriture 6, sémantique 4, exécution 3, plan
1, mémoire 2, web 2, questions 1), neuf mutants, chaque outil porte
son marqueur mutating et ses bornes CÔTÉ OUTIL (read_file fenêtre de
400 lignes / 50 000 caractères, search_text plafonné à 50
occurrences avec annonce, edit_file à occurrence EXACTE unique,
run_program 120 s / run_task 300 s / run_tests 600 s — la QUEUE de
la sortie conservée, l'amputation annoncée), la surface ENTIÈRE
part à chaque requête sous le budget de 16 000 caractères (le
garde-fou du test échoue à la place de la facture) ; les
CHECKPOINTS au premier toucher (EspaceCheckpoint enveloppe l'espace :
instantané du fichier AU PREMIER TOUCHER du tour, plafond 200
fichiers, annulation du plus récent au plus vieux — un fichier créé
disparaît, un modifié revient — et rapport HONNÊTE de ce qui ne se
restaure pas : la dépendance ajoutée n'est pas annulée, le rapport
le dit) ; le contenu web revient TOUJOURS enveloppé
<contenu-non-fiable source=…> (plafond 200 000 caractères, délai
20 s, http(s) seulement), ask_user passe par le port (1 à 4
questions de 2 à 4 options — les cartes arrivent au lot P).
Acceptation : check vert sans SDK (politique de langue 0 problème),
85 tests agent 0 échec (module neuf : boucle 12, compaction 6,
erreurs 12, Anthropic 6, OpenAI-compatible 7, SSE 10, réparation 4,
sessions 4, checkpoints 6, outils 18), 72 tests ide-integration 0
échec (9 nouveaux : l'espace de travail sur les VRAIES briques —
moteur Java réel pour diagnostics et définition, BuildRunner réel
pour la compilation bornée, résolveur P4 réel sur un dépôt local :
lockfile écrit, joide-module.toml réécrit, seconde demande sans
effet), 227 tests app-android 0 échec (39 nouveaux : Reglages 9,
EtatParametres 10, MesuresStockage 7, JournalDiagnostic 6,
MaquetteParametres 7) ; ADR-054 à 059, NOTICE et docs/third-party.md
(OkHttp), graphe et test du graphe (arêtes ide-integration -> agent
et TEST ide-integration -> core-store en jvmApi) ; arrêt pour
validation du propriétaire.

## Ajoutés

| Zone | Fichiers |
|---|---|
| agent (module neuf) | `BoucleAgent`, `HistoriqueAgent`, `CompacteurHistorique`, `ReparationHistorique`, `MessageAgent`/`PartieContenu`/`RequeteLlm`/`EvenementFlux`/`EvenementAgent`/`UsageLlm` (modèle neutre), `ErreursLlm` (quatorze genres), `Permissions`, `SessionsAgent` (double moitié), `FournisseurLlm`, `FournisseurAnthropic`, `FournisseurOpenAiCompatible`, `TransportHttp` (port), `TransportOkHttp`, `ParseurSse`, `SurfaceOutils`, `DescOutil`/`OutilAgent`/`ExecuteurOutils`, espace : `EspaceTravailAgent` (port), `EspaceNonSupporte`, `CheminsConfines`, `EspaceCheckpoint`, `QuestionUtilisateur`, `PositionEspace`/`OccurrenceEspace`/`DiagnosticEspace`/`ResultatExecution`, outils : `OutilsV1`, `OutilsLecture`, `OutilsEcriture`, `OutilsSemantiques`, `OutilsBuild`, `OutilsWeb`, `OutilsDivers`, `OutilSimple`, `Schemas`, `PlanAgent` |
| agent `src/test` | `BoucleAgentTest` (12), `CompacteurHistoriqueTest` (6), `ErreursLlmTest` (12), `FournisseurAnthropicTest` (6), `FournisseurOpenAiCompatibleTest` (7), `ParseurSseTest` (10), `ReparationHistoriqueTest` (4), `SessionsAgentTest` (4), `EspaceCheckpointTest` (6), `OutilsV1Test` (18) ; faux : `FauxTransport`, `FournisseurScripte`, `OutilMemoire`, `EspaceFaux` |
| core-deps `json` | `JsonEcrire` (écriture compacte, aller-retour garanti) |
| ide-integration `agent/` | `EspaceTravailIde` (le port sur les vraies briques : moteurs, overlay, exécution bornée, résolution P4, éditeur de modèle injectés) |
| ide-integration `src/test` | `EspaceTravailIdeTest` (9 — moteur Java réel, BuildRunner réel, résolveur P4 réel) |
| app-android `parametres/` | `Reglages` (schéma, bornes, tolérance), `EtatParametres` (cinq sections, quinze rangées, recherche), `MesuresStockage`, `JournalDiagnostic`, `PreferencesAndroid` (pont), `EcranParametres` |
| app-android `ui/` | `Stepper`, `Interrupteur`, `Nuancier` |
| app-android | entrée Paramètres au menu de l'application (le journal vit dedans — aucune entrée morte), tâche `copierLicences` (LICENSE + NOTICE en assets), SHA du commit en buildConfigField, réglages consommés (palette, session celeris au montage, BuildRunner, service « :build ») |
| docs/adr | `054-lot-m-parametres.md`, `055-lot-n-module-agent.md`, `056-lot-n-transport-okhttp.md`, `057-lot-n-politique-de-couts.md`, `058-lot-n-stockage-chiffre-des-cles.md`, `059-lot-o-outils-et-confinement.md` |

## Modifiés

- `gradle/module-graph.txt` + `settings.gradle.kts` + test du graphe :
  le module `agent` entre entre core-deps et build-jvm ; l'arête
  `ide-integration -> agent` (implémentation du port) et l'arête de
  TEST `ide-integration -> core-store` (le vrai moteur de build
  tourne dans les tests de l'espace de travail) sont autorisées.
- `gradle/libs.versions.toml` : OkHttp 5.5.1 au catalogue (transport
  du module agent, décision D-5).
- `NOTICE` et `docs/third-party.md` : OkHttp + kotlin-stdlib
  (Apache-2.0).
- `Graphite`/`GraphiteVue` : accents et thème lus des réglages à
  chaque appel de palette.
- `EcranAtelier`/`MainActivity` : la case « en processus » du menu ⋮
  déménage dans Paramètres (chaînes mortes retirées), le menu de
  l'application gagne Paramètres.
- Chaînes `values`/`values-fr` : 46 clés nouvelles du lot M en
  parité.

# CHANGES — Lots J, K et L : l'atelier adaptatif, la console, l'aperçu (groupe 2 de la V2)

Le groupe 2 du plan ADR-É10 se complète : éditer dans l'atelier
adaptatif, construire, lire la console, voir l'aperçu — exactement ce
que le propriétaire peut tester au jalon « v2 bêta ». Lot J (ADR-É5,
ADR-051) : EcranAtelier remplace l'empilement programmatique de P8 —
barre supérieure exacte (nom du projet, puce « n modules », pilule
d'état de l'index CIDX avec totaux honnêtes au tap, ⋮ fermer le
projet avec fichiers modifiés comptés — tout est déjà sur le disque,
fermer ne perd rien), onglets multi-fichiers À ÉTAT FAIBLE (le texte
vit sur le disque, écriture fusionnée multi-fichiers par
EcrituresFusionnees — le dernier texte de CHAQUE fichier gagne,
retrait conditionné à la valeur lue : aucune pose concomitante n'est
perdue), plafond 10 avec éviction honnête du plus ancien non modifié
et refus motivé si tout est modifié, point de modification par onglet,
tiroir arborescence poussé à 360 dp (« Nouveau » fichier/dossier dans
le dossier du fichier actif, cible AFFICHÉE, validation NouveauNom :
jamais un chemin déguisé, un refus ne crée rien), entrée active
surlignée, sorties build-joide estompées non dépliables avec leur
raison, affichage plafonné à 400 nœuds ANNOCÉ, FAB Construire qui
devient pilule d'état « build #n — phase » ouvrant la console avec sa
croix d'ARRÊT (DaemonClient.cancel — Frames.write synchronisée :
l'arrêt depuis le fil UI est sûr), retour système qui ferme les
surcouches une à une (console, tiroir — pas en grand écran où
l'arborescence amarrée est un réglage —, aperçu plein écran,
confirmation de fermeture), seuil 720 dp (rail, arborescence amarrée
240 dp, volet d'aperçu côte à côte 320 dp automatique pour une mise
en page active, console amarrée en bas) ; les montages des lots B-F
sont repris tels quels — seul l'habillage change, l'UI reste mince
(EtatAtelier, Arborescence, NouveauNom, EcrituresFusionnees purs et
testés JVM ; composants Onglets, Pilule, Fab, glyphes TERMINAL/
RAFRAICHIR/AGRANDIR). Lot K (ADR-É6, ADR-052) : la console v2 dans le
Dock du lot G — pilule d'état (en cours avec phase, réussi avec
durée, échoué, interrompu : la mort du service est un événement,
jamais un zombie), bande de phases du pipeline déduites des NOMS DE
TÂCHES réels (préparation/compilation/assemblage) avec durées
MESURÉES et filtre au tap (retap retire), filtres Tout / Erreurs (n)
/ Système au compteur exact, transcript en grille deux colonnes
colorée par nature (système accent, phase bleu fonction, tâche
premier plan, erreur rouge — chaque ligne reste sélectionnable),
problèmes en lignes cliquables avec bouton « Ouvrir le fichier à la
ligne » (SceneEditeur.allerALigne : caret au début de ligne bornée +
défilement), plafond 400 lignes ANNONCÉ en légende, résumé final
chiffré « 12 exécutées · 3 du cache · 2 à jour · 6,2 s » — jamais un
succès sans mesures ; ConsoleBuild gagne Nature/Phase/Ligne/
structurer (le texte du lot F reste), EtatConsole est LE point
d'entrée du protocole (surEvenement : l'écran ne formate rien) ; le
PROTOCOLE DU DÉMON reste inchangé et l'écart est documenté : le
pipeline ne transporte pas la localisation des problèmes
(Problem.location perdue dans outcomeOf) — le parseur de cible est
prêt, les problèmes non localisés s'affichent honnêtement sans lien,
aucun faux bouton ; les écarts s'affichent AVANT le lancement
(comportement lot F conservé), « Rien à construire » reste un refus
motivé, la case « en processus » vit transitoirement dans le menu ⋮
(le lot M l'emménagera dans Paramètres). Lot L (ADR-É7, ADR-053) :
le volet d'aperçu — barre de volet (rafraîchir sans délai, plein
écran, fermer), contrôles RÉELS : la densité de rendu devient un
composant du ConfigurationApercu (densiteRendu, DENSITE_CONTEXTE
conserve le comportement du lot E — le DimensionConverter de la
brique prend la densité choisie, dimensions cibles en pixels =
dp × densité, l'écran maintient des dp avec presets
téléphone/grand/tablette), mode nuit (qualificateur -night),
stats MESURÉES de l'écouteur (« n vues · durée ms · largeur ×
hauteur · densité »), surface sur damier neutre où le rendu LayoutLib
— arbre de vues vivant — est posé TEL QUEL dans un perchoir arrondi
24 dp mis à l'échelle du volet, bannières de franchise par gravité :
frappe (dernier rendu valide conservé, le volet ne devient jamais
blanc ni faux), RESSOURCES MANQUANTES détectées pour de vrai
(ReferencesManquantes : le parseur XML MAISON d'ADR-028 extrait les
références directes de l'arbre — jamais une regex sur le document,
risque n° 11 de l'ADR-É9 — et les compare au VRAI résolveur de la
brique : alias suivis jamais signalés, @android:/@id//?attr/ hors
juridiction), MockView COMPTÉES dans l'arbre rendu, échec nominal
(la surface garde le dernier rendu valide), avertissements de
constitution, thème jamais deviné (« repli hôte » affiché et dit) ;
reconfiguration au changement de fichier et à chaque réglage,
debouncé 400 ms conservé. Acceptation : check vert sans SDK
(politique de langue 0 problème — identifiants ASCII, typographie)
et avec SDK (lint NewApi bloquant sans baseline), 188 tests
app-android 0 échec (70 nouveaux : machine à états de l'atelier,
arbre, éviction, écritures fusionnées, validation des noms,
bannières d'aperçu, console — formatage, phases mesurées, filtres,
plafond, cibles —, captures contre les trois maquettes) et 63 tests
ide-integration 0 échec (7 nouveaux : ressources manquantes sur le
vrai résolveur, densité/dimensions de la configuration), ADR-051/
052/053 des décisions nouvelles ; arrêt pour validation du
propriétaire (D-8 : rythme par groupe, jalon « v2 bêta »).

## Ajoutés

| Zone | Fichiers |
|---|---|
| app-android `atelier/` | `EtatAtelier` (onglets, surcouches, retour, 720 dp, pilule), `Arborescence` (arbre, parentsDe, sorties), `NouveauNom` (validation), `EcrituresFusionnees` (écriture multi-fichiers), `EtatApercu` (bannières, contrôles, stats), `VueArbre`, `VueApercu` — purs et vues minces |
| app-android `console/` | `EtatConsole` (transcript plafonné annoncé, phases mesurées, filtres, états, résumé chiffré), `VueConsole` (grille deux colonnes, bande de phases, problème ouvrable) |
| app-android `ecrans/` | `atelier/EcranAtelier` (l'atelier adaptatif, remplace EcranIde) |
| app-android `ui/` | `Onglets`, `Pilule`, `Fab` ; glyphes `TERMINAL`, `RAFRAICHIR`, `AGRANDIR` dans `Icone` |
| ide-integration `apercu/` | `ReferencesManquantes` (détection des replis, parseur maison, package-private) |
| app-android `src/test` | `EtatAtelierTest` (17), `ArborescenceTest` (7), `NouveauNomTest` (2), `EcrituresFusionneesTest` (4), `EtatApercuTest` (8), `MaquetteAtelierTest` (6), `MaquetteApercuTest` (6), `EtatConsoleTest` (10), `MaquetteConsoleTest` (6), `ConsoleBuildTest` (+5) |
| ide-integration `src/test` | `ReferencesManquantesTest` (6, vrai résolveur), `ConfigurationApercuTest` (+1) |
| docs/adr | `051-lot-j-atelier-adaptatif.md`, `052-lot-k-console-de-build.md`, `053-lot-l-apercu-mise-en-page.md` |

## Modifiés

| Zone | Fichiers | Nature du changement |
|---|---|---|
| app-android | `ConsoleBuild` | gagne Nature/Phase/Ligne/structurer/cibleDe/tacheDe — le texte du lot F reste |
| app-android | `SpecsBuild`, `BuildService` | inchangés (le protocole du démon reste intact, critère du lot K) |
| app-android | `MainActivity`, `ecrans/creation/EcranCreation` | ouverts sur `EcranAtelier` (mêmes extras) |
| app-android | `ui/Dock` | `setTitre(CharSequence)`, `setLegende`, `setHauteurContenu` (la console s'y monte) |
| app-android | `ui/Segment` | `setSelection(int)` (l'état revient posé) |
| app-android | `ui/EtatVide` | `setTitre`/`setAide` à texte libre, idempotents (l'atelier y raconte chargement puis erreur) |
| app-android | `ui/Dialogue` | `confirmation` à message libre (le compte de fichiers modifiés) |
| ide-integration `apercu/` | `ConfigurationApercu` | composant `densiteRendu` + `avecDensite`/`avecDimensions` (ADR-053) |
| ide-integration `apercu/` | `ApercuLayouts` | convertisseur à la densité choisie, `ressourcesManquantes(xml)` |
| ide-integration `editeur/` | `SceneEditeur` | `allerALigne(int)` (caret borné + défilement, problème ouvrable) |
| app-android | `AndroidManifest.xml` | `EcranAtelier` déclarée, `EcranIde` retirée |
| app-android `res` | `values/`, `values-fr/` | chaînes des lots J/K/L (parité 175/175) ; chaînes mortes de l'écran P8 retirées |
| Racine | `docs/backlog.md` | entrées du groupe 2 |
| Racine | `ROADMAP.md` | ligne V2 : groupe 2 livré (J+K+L), arrêt pour validation |
| docs/ecrans | `README.md` | statut : groupe 2 complet, arrêt pour validation (D-8) |
| app-android | `README.md` | état des lots J/K/L |

# CHANGES — Lots H et I : l'accueil « Mes projets » et la création de projet (groupe 1 de la V2)

Le groupe 1 du plan ADR-É10 se complète : l'application s'ouvre
désormais sur un accueil moderne, crée un projet, l'importe en
second — exactement ce que le propriétaire peut tester au jalon
« v2 alpha ». Lot H (ADR-É3, ADR-049) : le hub de projets remplace
l'ouverture SAF de P8 comme écran de lancement — carte « Reprendre »
fusionnée avec la première carte (jamais doublée), « Nouveau projet »
primaire et « Importer » tonal côte à côte (l'import reste visible
sans creuser de feuille), section « Projets (n) » au compteur exact,
une seule ligne de méta par carte, ⋮ (renommer l'affichage,
supprimer avec ses deux conséquences NOMMÉES — retirer de la liste /
effacer du stockage), recherche filtrante, état vide de première
classe ; la persistance des projets récents vit dans
un JSON atomique versionné (projets-recents.json, AtomicFiles,
parseur maison ADR-028, tolérance signalée — jamais de réparation
silencieuse), la classification des cartes se lit sur le modèle
déclaratif (Android / console par facette application /
bibliothèque / vide / Gradle en compatibilité avec sa franchise).
Lot I (ADR-É4, ADR-050) : l'assistant en trois étapes — galerie de
quatre modèles déclaratifs TOML (joide.template/1) dans
assets/modeles (un modèle v3 s'ajoute sans toucher l'assistant),
franchise SUR la carte (la limite Android est nommée : JVM sur
l'appareil, APK au bureau « câblage à venir » — jamais une
promesse), formulaire piloté par le modèle (paquet et minSdk
seulement pour Android, segment 28/30 — 24 écarté, ADR-009),
récapitulatif EXACT (l'arborescence affichée est la liste des
fichiers que la génération produira), création atomique tout ou
rien (temporaire voisin puis renommage, chemins hors projet
refusés — zip-slip ADR-032), inscription dans les projets récents
et ouverture de l'atelier sur le fichier d'entrée du modèle.
L'écran IDE de P8 déménage dans EcranIde (extras : racine,
bannière Gradle, fichier d'entrée) ; l'écran d'exercice du kit est
retiré comme le lot G l'annonçait. La « capture » contre les
maquettes suit la méthode ADR-048 : conformité prouvée par tests
lisant les maquettes (MaquetteAccueilTest, MaquetteCreationTest),
validation visuelle par le propriétaire via l'APK du CI (pas
d'émulateur au bac à sable, écart P8 n° 4). Acceptation : check
vert sans SDK et avec SDK (lint NewApi bloquant — quatre appels
hors niveau 28 refusés puis remplacés : readAllBytes de flux,
readString, writeString, Stream.toList), 118 tests app-android
0 échec (71 nouveaux : persistance, état d'accueil, classification,
âges, salutation, analyse des modèles, génération — aller-retour
disque—→ lecteur de workspace sans erreur —, écriture atomique,
machine à états de l'assistant), :app-android:assembleDebug
produit l'APK du groupe 1 (92 Mo, 30 dex, les quatre modèles dans
assets, toutes les classes vérifiées dans les dex), politique de
langue 0 problème (parité values/values-fr, identifiants ASCII,
messages d'exception anglais) ; arrêt pour validation du
propriétaire (D-8 : rythme par groupe).

## Ajoutés

| Zone | Fichiers |
|---|---|
| app-android `accueil/` | `ProjetsRecents` (JSON atomique joide.recents/1), `EtatAccueil` (tri, fusion reprise, compteur, filtre), `ClassifieurProjet`, `TypeProjet`, `AgeRelatif`, `Salutation` — purs, testés JVM |
| app-android `creation/` | `ModeleProjet` (analyse joide.template/1), `GenerateurProjet` (config dérivée par les écrivains canoniques), `EcrireProjet` (atomique tout ou rien), `EtatCreation` (machine à états) — purs, testés JVM |
| app-android `ecrans/` | `ide/EcranIde` (l'écran IDE de P8, déplacé), `creation/EcranCreation` (l'assistant) |
| app-android `ui/` | `Icone` (glyphes au canvas), `Segment` + `ui/choix/ChoixSegment` |
| app-android `res` | chaînes des lots H et I dans `values/` (anglais) et `values-fr/` (français) |
| app-android `assets/modeles/` | `application-android.toml`, `console-jvm.toml`, `bibliotheque-jvm.toml`, `vide.toml` (les quatre modèles v2, ADR-050) |
| app-android `src/test` | `ProjetsRecentsTest` (14), `EtatAccueilTest` (11), `ClassifieurProjetTest` (6), `MaquetteAccueilTest` (5), `ModeleProjetTest` (7), `GenerateurProjetTest` (8), `EcrireProjetTest` (5), `EtatCreationTest` (9), `MaquetteCreationTest` (5) |
| docs/adr | `049-lot-h-projets-recents.md`, `050-lot-i-modeles-de-projet.md` |

## Modifiés

| Zone | Fichiers | Nature du changement |
|---|---|---|
| app-android | `MainActivity` | réécrit : l'accueil « Mes projets » (lanceur), l'import et les ouvertures vivent ici |
| app-android `ui/` | `Carte` | `setTonale` (bord accent 35 %, dégradé 8 % — reprise, modèle choisi) |
| app-android `ui/` | `Champ` | `sansEtiquette` (recherche, renommage) |
| app-android `ui/` | `Dialogue` | `menu`, `saisie`, `destructive` (deux conséquences distinctes), `information` à message libre |
| app-android | `BuildService`, `SpecsBuild` | `lirePort` et `FILS_PAR_DEFAUT` publics (EcranIde vit dans son paquet) |
| app-android | `AndroidManifest.xml` | EcranIde + EcranCreation déclarées, EcranExercice retirée |
| app-android `res` | `values/`, `values-fr/` | chaînes de l'écran d'exercice retirées (écran supprimé au lot H, mandat du lot G) ; chaînes P8 conservées |
| Racine | `docs/backlog.md` | entrées du groupe 1 : exécution sur appareil, outil bureau spec Android, journal, import inconnu |
| Racine | `ROADMAP.md` | ligne V2 : groupe 1 livré (G+H+I), arrêt pour validation |
| docs/ecrans | `README.md` | statut : groupe 1 complet, arrêt pour validation (D-8) |
| app-android | `README.md` | état des lots H/I ; la note pendante « État du lot G » réparée |

# CHANGES — Lot G : le système de design « Graphite » en code

Le GO du propriétaire sur le plan ADR-É10 (2026-11-16 : « je te dis
GO », D-5 tranchée OkHttp — l'implémentation maison écartée) ouvre la
piste UI par le lot G : les jetons ADR-É2 et la palette claire
ADR-048 en constantes pures (Graphite), le kit de composants en vues
du cadre sans AndroidX (boutons, champs, cartes à tuile tonale,
puces, tiroir poussé, dock repliable, dialogues, états vides),
l'écran d'exercice qui les montre dans les deux modes, la parité de
langue values/values-fr activée (anglais par défaut, français
traduit — les chaînes P8 comprises), le thème passé à
Theme.Material.NoActionBar avec couleurs de fenêtre posées en code.
La conformité aux jetons de design/index.html est prouvée par test
(JetonMaquetteTest), les contrastes AA par mathématique (GraphiteTest)
— la validation visuelle sur appareil revient au propriétaire via
l'APK du CI (pas d'émulateur au bac à sable, écart P8 n° 4).

## Ajoutés

| Zone | Fichiers |
|---|---|
| app-android `ui/` | `Graphite` (jetons purs), `GraphiteVue` (colle dp/sp/typographie/fenêtre), `Bouton`, `Champ`, `Carte`, `Puce`, `TiroirPousse`, `Dock`, `Dialogue`, `EtatVide` |
| app-android `ui/choix/` | `ChoixBouton`, `ChoixChamp`, `ChoixPuce`, `ChoixCarte`, `ChoixTexte`, `ChoixMouvement` (purs, testés JVM) |
| app-android `ecrans/exercice/` | `EcranExercice` (écran d'exercice du kit, non exporté) |
| app-android `res` | `values-fr/strings.xml` (français, 113 clés) ; chaînes du kit dans `values/strings.xml` (anglais) |
| app-android `src/test` | `GraphiteTest` (7 cas), `JetonMaquetteTest` (3), `ChoixBoutonTest` (4), `ChoixChampTest` (3), `ChoixPuceTest` (3), `ChoixCarteTest` (3), `ChoixTexteTest` (6), `ChoixMouvementTest` (3) |
| tools-test | fixtures de parité `res/values`+`values-fr` dans `language-clean` (conforme) et `language-violation` (violation) |
| docs/adr | `048-lot-g-graphite-code.md` |

## Modifiés

| Zone | Fichiers | Nature du changement |
|---|---|---|
| app-android | `MainActivity` | couleurs de fenêtre Graphite + entrée discrète « Kit Graphite » (temporaire, retirée au lot H) |
| app-android | `AndroidManifest.xml` | `Theme.Material.NoActionBar` + activité `EcranExercice` non exportée |
| app-android | `res/values/strings.xml` | traduit en anglais (langue par défaut) |
| Racine | `scripts/check-language-policy` | contrôle n° 5 : parité values/values-fr |
| tools-test | `LanguagePolicyScriptTest` | assertions de parité (« parite », « oui », « extra ») |
| Racine | `ROADMAP.md` | ligne V2 dédupliquée ; lot G livré, suite H-R |
| docs/ecrans | `002`, `010`, `README.md` | É2 : note de révision (clair au lot G) ; É10 : Accepted + décisions + renumérotation H 049 / I 050 ; README : GO et lot G |
| docs | `docs/backlog.md` | entrée P0 « parité values/values-fr » fermée |

## Supprimés

Rien.

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
| docs/ecrans | `README.md` (index de la série) ; `001-analyse-codeassist.md` (inventaire complet des écrans de CodeAssist par famille, verdicts, six leçons avec preuves dans le source) ; `002-systeme-design.md` (palette Graphite, typographie à deux familles, composants, implémentation en vues du cadre sans AndroidX, états purs JVM par écran) ; `003-accueil-mes-projets.md` ; `004-creation-projet.md` ; `005-atelier.md` ; `006-console-build.md` ; `007-apercu-mise-en-page.md` ; `008-parametres.md` |
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
| ide-integration | `LanguageModelSync` | `derniereResolution()` : le build réutilise les jars de la synchronisation (additif, lot F) |
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
| import-gradle (fixtures) | cinq projets Gradle échantillons sous `src/test/resources/gradle-samples/` : `jvm-groovy` (Groovy, interpolation), `multi-catalog` (multi-modules Kotlin DSL, catalogue, parfum des deux dialectes, AGP 9), `compose-app` (Kotlin+Compose, BOM, variantes debug), `flavors` (Groovy, parfums, désucrage, sourceSets), `poisoned` (constructions non mappées + poison d'exécution : System.exit, marqueurs, exec — jamais exécutés) |
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
