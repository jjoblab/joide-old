# ADR-070 — Lot VIII : le pack qui charge, le service qui parle, l'APK à 11 Mo

- **Statut** : Accepted (mandat du propriétaire du 2026-11-17 :
  « D'accord pour l'option A (R8 minification (déjà en backlog « P8 »
  du build)) » et le rapport de terrain : « service de build
  injoignable (port non publié en 30 s) — relancez le build / service
  injoignable — le build continue en processus / embedded build tool
  unavailable: jo.joide.build.android.tools.InProcessDexer — jars des
  outils absents et pack différé r8 9.5.32 (r8-9.5.32.dex.zip)
  indisponible (java.lang.reflect.InvocationTargetException) » —
  « vérifie comment CodeAssist a fait »)
- **Date** : 2026-11-17

## Contexte

Le premier build sur l'appareil (APK du lot VII) échouait deux fois
de suite. D'abord le service `:build` ne publiait pas son port en
trente secondes — le repli en processus prenait le relais, mais la
cause du silence restait invisible (ni fichier d'erreur, ni
distinction entre « jamais lancé » et « pas prêt »). Puis la
résolution du dexeur mourait sur l'`InvocationTargetException` de
l'embarqué pendant que la cause RÉELLE du pack se perdait dans
stderr — logcat sur l'appareil, jamais la console.

L'étude de CodeAssist (tyron12233/CodeAssist) éclaire les trois
volets : leur D8 est statiquement lié dans l'app (« sur ART il n'y a
pas de lanceur java à forker »), R8 et le compilateur Kotlin tournent
en VMs FORKÉES à gros tas (`ForkedD8Dexer`, `ForkedR8Shrinker`,
`ForkedKotlinCompiler`, politique `ForkedDexPolicy`), et les
bibliothèques des projets utilisateur arrivent DÉJÀ DEXÉES
(`DexRepository`, layout Maven de dex signés). Point décisif : aucune
classe d'outil ne vit dans leur application — donc aucune OMBRE.

## Décision

### 1. L'ombre des adaptateurs — la panne du pack (VIII/1)

Diagnostic : les adaptateurs des outils packés (`InProcessDexer`,
`BundletoolAssembler`) restaient EMBARQUÉS dans l'APK (le lot VI
croyait que « leur échec de linkage est le signal de repli pack ») —
mais la délégation parent-d'abord du `DexClassLoader` servait la
copie de l'APK, SANS ses classes d'outil, et jamais celle du pack :
la route du pack ne pouvait pas charger, sur AUCUN appareil. Le
correctif suit la manière CodeAssist : `toolsApi` passe en
`compileOnly` (aucune classe d'outil dans l'app, tout arrive déjà
dexé) et un jar DÉDIÉ `signatureApi` garde `ApksigSigner` seul
embarqué (apksig reste dans l'APK, décision du lot VI — trop léger
pour un pack). La tâche `verifierOmbreApk` (finalisée par
`assembleDebug`) LIT l'APK construit et échoue si l'ombre revient
(descripteurs des adaptateurs packés, fichiers de services des
outils) ou si un élément vital disparaît (adaptateur de signature,
pont des packs, SPI du mur de chargeurs, enregistrement du pont par
CONTENU — R8 renomme légitimement les fichiers de services internes).

### 2. Les causes voyagent (VIII/2)

`BuildRunner.outil()` compose désormais son message avec les DEUX
causes — l'embarquée aplatie et celle du pack — et `outilDuPack`
LÈVE au lieu d'avaler vers stderr. Un
`InvocationTargetException` s'aplatit vers sa cible :
« java.lang.reflect.InvocationTargetException » ne dit rien,
« java.lang.NoClassDefFoundError: com/android/tools/r8/Version »
dit tout. La chaîne d'origine reste entière (cause + supprimée).

### 3. Le service qui parle (VIII/3)

`demon-build.vie` : une preuve de vie (pid, instant) posée à la
PREMIÈRE ligne d'`onCreate`, avant tout bloc gardé. Le client qui
attend le port distingue désormais trois silences : « le processus
:build n'a JAMAIS démarré (restriction ROM ou batterie ?) », «
processus démarré, démon pas prêt » et « port publié mais connexion
refusée N fois — mort juste après publication ». Le bind du démon est
vérifié sain (`InetAddress.getLoopbackAddress()` = 127.0.0.1, le
client s'y connecte) ; sans logcat, la panne ROM reste à confirmer
sur le terrain — le détail la nommera désormais.

### 4. R8 minification — le backlog P8, l'option A (VIII/4)

La passe de rétrécissement DÉDIÉE annoncée par le backlog P8 («
jamais de règle approximative ») :

- la variante debug devient NON débogable — le debug EST le livrable
  distribué, et AGP DÉSACTIVE R8 sur les constructions débogables («
  All code optimizations and obfuscation are disabled for debuggable
  builds ») ; `-Pdebuggable=true` le rend débogable localement ;
- `-dontoptimize` documenté : le noyau a TUÉ le démon Gradle (tas
  2,2 Go, puis 2,4 Go) pendant la phase d'optimisation de R8 sur les
  41 Mo de classes — la machine de build a 4 Go ; le
  rétrécissement (retrait du code mort) et l'obfuscation (noms
  courts) portent l'essentiel du gain ; la mémoire directe est
  plafonnée (`MaxDirectMemorySize`) et `lintVital` passe à la
  demande (R8 et lint chargent chacun le modèle complet de l'app) ;
- règles de conservation, chacune avec sa raison : les paquets
  `dex`/`bundle`/`sign` (le mur de chargeurs — les packs chargent
  leurs adaptateurs PAR NOM contre CES interfaces), `ApksigSigner`
  PAR NOM (`Class.forName` de `BuildRunner`), le pont
  `ChargeurDexAndroid`, `lsp4j` (sérialisation Gson par réflexion),
  `kotlinx.serialization` (lookup du serializer compagnon), les
  métadonnées Kotlin (`KotlinFacts` lit `@Metadata`),
  `layoutlib` (rendu réflexif) ; chaque `-dontwarn` nomme sa
  bibliothèque (javac/StAX absents d'ART, le pilote BUREAU du
  compilateur Kotlin exclu depuis le lot VII, l'« actual » manquant
  du métamodule fleet).

### 5. Le résultat

L'APK passe de 35 012 865 o (33,4 Mo, lot VII) à **11 782 666 o
(11,2 Mo)** : −66 % sur le lot, −92 % depuis l'origine (~150 Mo à 4
ABI). UN seul `classes.dex` ; la carte `mapping.txt` accompagne
chaque construction (retrace des piles) ; signature v2 vérifiée ;
composants du manifeste et éléments vitaux présents (garde verte) ;
les packs sont INCHANGÉS — la tâche `dexPacksOutils` re-vérifie les
empreintes épinglées (r8, bundletool, kotlin régénérés à
l'identique).

## Conséquences

- Le premier build sur appareil télécharge r8 + bundletool (~10 Mo)
  puis le compilateur Kotlin à la première compilation (19,1 Mo) —
  le pack CHARGE désormais : l'ombre qui servait la copie cassée de
  l'APK n'existe plus.
- L'APK n'est plus débogable par défaut (c'est le livrable) ; les
  piles sont obfusquées — `mapping.txt` les relit ; le débogage
  local revient par `-Pdebuggable=true`.
- Le bureau garde les adaptateurs sur son classpath (`toolsApi`
  reste `implementation` dans build-daemon) : rien ne change pour le
  démon de bureau ni ses essais (suites build-daemon, build-android,
  build-jvm au vert après le lot).
- Risque résiduel : le parcours complet sur appareil (UI, packs,
  service) ne se prouve que sur le terrain — la garde anti-ombre et
  les vérifications statiques couvrent ce que la machine de build
  peut couvrir ; le détail du service et les causes du pack
  nommeront désormais ce qui resterait à corriger.
