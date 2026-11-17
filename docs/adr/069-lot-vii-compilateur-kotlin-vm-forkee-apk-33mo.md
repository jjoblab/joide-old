# ADR-069 — Lot VII : le compilateur Kotlin en VM forkée, l'APK à 33 Mo

- **Statut** : Accepted (mandat du propriétaire du 2026-11-17 :
  « Continue avec le prochain poste d'amaigrissement » — le poste
  désigné par l'ADR-067 : le compilateur Kotlin, 43,6 Mo de dex)
- **Date** : 2026-11-17

## Contexte

L'ADR-067 laissait le compilateur Kotlin comme prochain poste
d'amaigrissement et tranchait la voie : (a) un adaptateur réflexif
chargé du pack ou (b) la VM FORKÉE — `/apex/com.android.art/bin/
dalvikvm64 -Xmx… -cp kotlin.dex.zip <WorkerMain>`, une VM non-zygote
dont le tas dépasse le plafond `largeHeap` de l'app (la manière
CodeAssist `ForkedKotlinCompiler` : l'ouvrière persiste, réutilisée
entre les compilations). La voie (b) se recommandait : elle résout
AUSSI le budget mémoire des grosses compilations — un build qui
lève `OutOfMemoryError` dans le processus app tue l'app entière ;
dans l'ouvrière, il tue un processus qu'on relance.

Le lot VI avait livré un APK « de 96,7 Mo » — un chiffre faux, on
le verra : il portait 58,3 Mo d'entrées ORPHELINES (voir ci-dessous).

## Décision

### L'ouvrière : `KotlinWorkerMain`, auto-contenu

Un source set dédié `workerKotlin` de build-jvm porte
`jo.joide.build.jvm.worker.KotlinWorkerMain` : boucle sur l'entrée
standard (nombre d'arguments, puis un argument par ligne), appel du
`K2JVMCompiler` avec les MÊMES arguments que le pilote embarqué,
réponse ligne (statut `OK|ERR`, compte, puis un diagnostic par
ligne `SÉVÉRITÉ\tcode\tchemin:ligne:colonne\tmessage-échappé` —
l'antislash, la tabulation et les sauts de ligne échappés : une
ligne transporte toujours quatre champs). L'ouvrière ne référence
AUCUNE classe `jo.joide.*` : son chargeur ne porte que le pack, sur
ART comme sur la JVM de bureau (où les essais bout-en-bout la
lancent par `java -cp … KotlinWorkerMain`). La fermeture de
l'entrée termine l'ouvrière : le parent meurt, l'ouvrière meurt.

### Le pilote : `KotlinForkedDriver`, le mur étanche

`jo.joide.build.android.toolchain.KotlinForkedDriver` (build-android)
implémente le SPI `CompilerDriver` SANS AUCUNE classe du
compilateur : il forge la VM (`dalvikvm64` de l'APEX ART, 1 Gio de
tas), lui passe le pack en classpath, sérialise la requête et lit
la réponse. Les arguments viennent de `KotlinArgs` — la fabrique
COMMUNE extraite du pilote embarqué (aucun type Kotlin dedans) — et
les classes écrites se détectent par différence d'inventaire du
répertoire de sortie (`CompileInventory`, partagé), côté appelant :
l'ouvrière ne sait même pas où sont les sorties. L'échange est
sérialisé (protocole mono-serveur) et la VM RÉUTILISÉE entre les
lots : un démarrage de compilateur ne se paie qu'une fois. Une
morte d'ouvrière se détecte (EOF sur la réponse) : le lot échoue
avec sa cause, la prochaine compilation re-fork. Les surcharges
système `joide.kotlinVm` et `joide.kotlinWorkerCp` branchent les
essais du bureau sans toucher au pack.

### La résolution : une SONDE, pas un constructeur

`BuildRunner.kotlinDriver()` mémorise le pilote effectif : sonde
`Class.forName("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")`
d'abord — le jar présent (démon de bureau) donne l'embarqué, chaud
comme avant ; l'absence (APK allégé) donne `KotlinForkedDriver`.
La sonde regarde la classe du COMPILATEUR, pas la stdlib :
kotlin-stdlib et kotlin-metadata-jvm restent embarqués (l'ABI
`KotlinFacts` les lit), le constructeur du pilote embarqué ne
lèverait donc PAS sur l'APK allégé — seul `compile()` échouerait,
trop tard. Les fabriques (`AndroidTaskFactory`, `JvmTaskFactory`)
reçoivent le pilote par `withKotlinDriver(…)` : plus d'instanciation
au champ ni au constructeur, qui auraient levé à la construction du
graphe sur l'appareil.

### Le pack : la fermeture transitive, les ressources, le dexage par lots

`kotlin-compiler-2.3.0.dex.zip` (19 065 892 o,
`ccc17bcd1952be03d3574b571e57d58705d332c0c63e2f3d94652f9fef767b28`,
épinglé dans `PacksOutils.KOTLIN`) porte :
l'ouvrière, le compilateur et sa FERMETURE RUNTIME — les dépendances
de son POM (kotlin-stdlib, kotlin-script-runtime, kotlin-reflect,
kotlin-daemon-embeddable, kotlinx-coroutines-core-jvm) résolues
TRANSITIVEMENT par la configuration `jarsKotlin` : l'essai
bout-en-bout a prouvé que reflect manque sinon (le parseur
d'arguments du compilateur le lit) ; les RESSOURCES non-class des
jars (les `.kotlin_module` notamment), que le dexage seul ne
transporte pas — le chargeur de la VM ouvrière les lit dans
l'archive du pack.

Le dexage se fait PAR LOTS : D8 sur 60 Mo de bytecode dépasse la
mémoire d'une machine modeste (tué par le noyau à -Xmx2600m sur 4
Go). Chaque lot se dexé seul — 6000 classes triées par nom pour le
gros jar, les petits jars entiers — (min-api 28 : aucun désucrage
inter-lots), les dex produits se renumérotent en séquence
(`classes.dex`, `classes2.dex`, …). La composition des lots est
déterministe : régénérer reproduit l'empreinte épinglée, vérifié
par la tâche `:app-android:dexPacksOutils` (une divergence échoue
la tâche, jamais un pack non vérifié).

`PacksOutils.fichierAssure(pack)` télécharge-vérifie une fois et
retourne le FICHIER : le pilote forké n'a pas besoin du chargeur
de classes du pont Android, seulement du chemin.

### L'APK : exclusion chirurgicale, vérité du poids

`app-android` exclut `org.jetbrains.kotlin:kotlin-compiler-embeddable`
de sa dépendance `ide-integration` (même schéma que l'exclusion ecj
— l'unique route du compilateur vers l'APK) ; stdlib et
kotlin-metadata-jvm restent. L'ouvrier voyage au consommateur du
pack par un RELAIS `kotlinWorkerApi` de build-android vers
build-jvm : l'app ne dépend jamais de build-jvm directement
(ADR-011, `gradle/module-graph.txt`).

**La découverte du lot** : l'empaquetage incrémental d'AGP laisse
le contenu RETIRÉ comme entrées orphelines — des octets morts entre
les entrées vivantes, invisibles au répertoire central. L'APK du
lot VI (« 96,7 Mo ») portait 58,3 Mo de dex morts (les outils du
lot VI + le compilateur d'avant). Un build propre dit la vérité :
le contenu réel du lot VI était ~38 Mo, et le lot VII livre un APK
PROPRE de **33,4 Mo** (35 012 865 o) — contre 134,3 Mo au lot V et
~150 Mo à l'origine à 4 ABI. Les LIVRABLES (release, APK distribués)
se construisent désormais après `clean` — c'est écrit dans le kit.

## Conséquences

- L'APK arm64 passe de 134,3 Mo (lot V) à **33,4 Mo** : 4× plus
  léger que le point de départ ; l'installation télécharge UNE fois
  la plateforme (~61 Mo) et les packs (r8 4,8 + bundletool 5,0 +
  kotlin 19,1 Mo), la première compilation Kotlin déclenchant
  seule le téléchargement du pack compilateur.
- Le tas de l'ouvrière (1 Gio) dépasse le plafond `largeHeap` :
  une grosse compilation ne tue plus l'app, elle tue une VM qu'on
  relance ; le bureau garde l'embarqué chaud (démon, essais).
- Le compilateur sur ART suit le précédent CodeAssist (dex du
  compilateur en classpath de `dalvikvm64`) ; les bavardages du
  compilateur vont au journal du parent (stderr hérité — logcat sur
  l'appareil), jamais dans le protocole (stdout).
- L'API 28 tient : l'ouvrière et le pilote n'utilisent que du JDK
  présent au niveau 28 (tampons, `ProcessBuilder`, `Path`).
- La réutilisation de l'ouvrière sérialise les compilations Kotlin
  d'un même BuildRunner (verrou) : acceptable pour des projets
  d'appareil ; un pool d'ouvrières reste possible plus tard.
- Risque résiduel : le compilateur écrit parfois sur sa sortie
  standard (jamais constaté avec un `MessageCollector` — le
  protocole ne serait plus lisible) ; la réponse inattendue fait
  échouer le lot avec la ligne fautive, l'ouvrière se re-fork.
