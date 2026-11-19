# ADR-067 — Lot V : le build qui part, le fournisseur qui persiste, l'APK qui maigrit

- **Statut** : Accepted (mandat du propriétaire du 2026-11-17 :
  « le build ne fonctionne pas, j'ai des erreurs, peut-être que je
  n'utilise pas la bonne approche et qu'il manque d'autres outils.
  Peu importe le provider choisi, la carte erreur affiche requête
  échoué no anthropic key. Pour l'abi, je veux seulement arm64.
  L'apk mesure à peu près 150mb, j'aimerais savoir qui gonfle
  l'apk ainsi et étudier un moyen pour le rendre plus petit, avec
  téléchargement des besoins une seule fois à la première
  installation »)
- **Date** : 2026-11-17

## Contexte

Le lot U avait embarqué la chaîne Android complète, mais trois
ruptures tenaient le parcours : le démon `:build` ne connaissait
NI son répertoire d'outillage NI son aapt2 embarqué (les
propriétés système ne traversent pas les processus — seules
celles de l'atelier étaient posées, et `user.home` n'existe pas
sur ART : `Path.of(null,…)` tuait la localisation) ; l'aapt2
jniLibs n'était PAS extrait (AGP garde les .so mappés dans l'APK
quand `useLegacyPackaging` reste par défaut — le binaire
n'existait donc jamais dans `nativeLibraryDir`, seul répertoire
où ART exécute) ; et la variante debug restait NON SIGNÉE (la
tâche de signature ne s'enregistre que sur un keystore posé —
l'APK produit était ininstallable). S'y ajoutaient le résolveur
d'app limité à Maven Central (AndroidX non résoluble) et, côté
agent, un choix de fournisseur qui ne PERSISTAIT pas au tap de
puce : le sélecteur bougeait, les réglages restaient sur
ANTHROPIC — la requête partait chez Anthropic avec une clé
absente, quel que soit le fournisseur choisi.

L'APK mesure ~150 Mo (134 Mo après arm64 seul) ; l'attribution
du poids (classpath d'exécution, 139,7 Mo de jars/aars,
~145 Mo de dex non compressés) dit : compilateur Kotlin
embarqué 55 Mo (43,6 Mo de dex), r8 20,2 Mo, bundletool 12,8 Mo,
stdlib 6,7 Mo, code-lsp 6,4 Mo, kotlin-reflect 5,9 Mo, celeris
4,3 Mo — la chaîne de build et le compilateur pèsent plus que
l'IDE lui-même.

## Décision

### 1. Le démon connaît son outillage (V/1)

`BuildService.onCreate()` pose `joide.toolchain.dir` et
`joide.native.dir` DANS le processus `:build` — la localisation
de l'outillage (`ToolchainLocator.defaultCacheDir`,
`aapt2Embarque`) vit dans le démon, pas seulement dans l'UI.

### 2. L'aapt2 s'extrait (V/2)

`jniLibs.useLegacyPackaging = true` : l'installateur extrait
`libaapt2.so` vers `nativeLibraryDir` (le choix de CodeAssist,
même cause même remède). `abiFilters` se limite à `arm64-v8a`
(demande du propriétaire) et `fetchAapt2` purge les dossiers
d'ABI retirés d'un jniLibs local ancien.

### 3. La variante debug se signe (V/3)

Un keystore de débogage PKCS12 (alias `joide`, mot de passe
`joidepass`, celui du `BuildRunner`) est généré À LA
CONSTRUCTION par `genererKeystoreDebug` (keytool du JDK,
JAMAIS commis — convention docs/dev.md), embarqué en asset
`toolchain/debug.keystore`, copié une fois vers
`filesDir/toolchain` par `PreparationChaine` ; la variante
debug de `SpecsBuild.android()` le porte (`keystoreFile` +
`keystoreAlias`) — la tâche `:sign[debug]` s'enregistre, l'APK
utilisateur s'installe.

### 4. Le résolveur sert Google Maven (V/4)

`EcranAtelier.monterIde()` ajoute `dl.google.com/android/maven2`
à côté de Maven Central — AndroidX et `com.android.*` se
résolvent (CodeAssist sert central + google par défaut).

### 5. Le fournisseur persiste au tap de puce (V/5)

`choisirFournisseur()` persiste le fournisseur à l'instant du
choix, résout un modèle qui lui APPARTIENT (posé pour lui,
partagé si le garde l'accepte, sinon première suggestion du
catalogue — jamais « claude-sonnet-4-5 » chez OpenRouter), pousse
au binder. L'état de clé du fournisseur pointé se VOIT sous la
rangée (`agent_cle_posee`/`agent_cle_absente` enfin servies), un
tap ouvre les réglages. Le titre du dialogue de saisie couvre
Gemini ; l'URL libre du fournisseur openai entre dans la
signature de la bascule (une URL changée ne laisse plus
l'ancienne passerelle en place) ; un message parti pendant le
bind asynchrone se retient et part à la liaison.

### 6. La chaîne différée — l'APK allégé (V/6)

ART ne charge pas des jars `.class` : D8/R8 et bundletool
partent en PACKS DE DEX pré-construits (la manière CodeAssist :
`r8.dex.zip`). `PacksOutils` (build-android, pur) épingle
fichier + taille + SHA-256, télécharge UNE fois (transport HTTPS
du résolveur, vérification AVANT posage atomique) et charge par
un pont `ChargeurClassesOutil` (implémenté dans app-android par
`DexClassLoader`, découvert par ServiceLoader — les modules purs
restent sous contrôle API 28). `BuildRunner.outil()` tente
l'embarqué d'abord, le pack ensuite — le mur de chargeurs reste
étanche : l'adaptateur du pack n'expose que l'interface SPI du
parent, chargé PAR NOM (jamais ServiceLoader sur le pack : le
parent porte aussi les fichiers de services des adaptateurs
embarqués). La tâche `:app-android:dexPacksOutils` produit les
packs DÉTERMINISTES (dex D8 + horodatages d'archive fixes) et
ÉCHOUE sur divergence avec les constantes épinglées — jamais un
pack non vérifié. Les packs r8 (4,8 Mo) et bundletool+guava
(5,0 Mo) sont générés ; leur publication dans une release GitHub
`toolchain-v1` et le retrait des trois `implementation` de jars
(voir le commentaire du build.gradle.kts d'app-android) allègent
l'APK de ~25 Mo ; le compilateur Kotlin (43,6 Mo de dex) reste
le prochain poste (design en fin d'ADR).

## Conséquences

- Le build Android via le service (moteur par défaut) localise
  enfin l'outillage sur l'appareil ; le repli en processus reste
  le filet de sécurité connu.
- L'APK arm64 seul mesure 134 Mo (contre ~150 Mo à 4 ABI) ; les
  packs différés enlèvent ~25 Mo de plus quand la release est
  publiée ; l'installation télécharge alors UNE fois : la
  plateforme android.jar (~61 Mo, comportement existant) et les
  packs d'outils (~10 Mo).
- La signature debug des projets utilisateurs demande la
  régénération du keystore à chaque machine de build (comme
  `~/.android/debug.keystore` d'Android Studio) ; la convention
  « aucun keystore dans le dépôt » tient (asset généré, ignoré).

## Le prochain poste : le compilateur Kotlin (non décidé)

Le compilateur embarqué (55 Mo de jars, 43,6 Mo de dex) exige un
mur plus épais que le SPI dexeur : son API est typée Kotlin
(`K2JVMCompiler`). Deux voies, la manière CodeAssist montrant la
seconde : (a) un adaptateur réflexif chargé du pack (tout en
`Object`, aucun type Kotlin côté app) ; (b) la VM FORKÉE —
`/apex/com.android.art/bin/dalvikvm64 -Xmx… -cp kotlin.dex.zip
<WorkerMain>`, une VM non-zygote dont le tas dépasse le plafond
`largeHeap` de l'app (le point clé de CodeAssist :
`ForkedKotlinCompiler` persiste une VM ouvrière réutilisée,
l'APK de l'app lui sert de classpath). La voie (b) résout AUSSI
le budget mémoire des grosses compilations — elle se recommande,
le lot W la décidera.
