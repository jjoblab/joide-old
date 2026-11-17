# ADR-068 — Lot VI : la chaîne différée activée, le store qui se soigne, le service qui parle

- **Statut** : Accepted (mandat du propriétaire du 2026-11-17 :
  « Je t'autorise à faire tout les travaux nécessaire, retirer les
  trois implementation de jars d'outils, etc... Essayé aussi de
  corriger en parallèle, cette erreur quand j'appuie sur build :
  service de build injoignable (port non publié en 30 s) —
  relancez le build / cannot create store schema »)
- **Date** : 2026-11-17

## Contexte

La release GitHub `toolchain-v1` publie les packs d'outils différés
(r8-9.5.32.dex.zip, bundletool-1.18.3.dex.zip — épinglés taille et
SHA-256 dans `PacksOutils.java`, vérifiés octet par octet contre la
régénération) : l'étape 2 du design ADR-067 (retirer les trois
`implementation` de jars) peut s'exécuter.

Sur l'appareil, deux erreurs tenaient le bouton Construire :
« service de build injoignable (port non publié en 30 s) » puis, dans
le repli en processus, « cannot create store schema ». La lecture du
code les relie : `StoreAndroid.open()` rattache TOUT échec de
schéma — y compris une base corrompue — sans dire ni le fichier ni la
cause SQLite (la console ne voit que le message du `StoreException`,
l'exception réelle se perd dans la cause) ; et `BuildService` ne
gardait que `demarrer()` — une faute AVANT (notification, politique de
service de premier plan) ou autour tuait le processus `:build` en
SILENCE : ni port ni fichier d'erreur, le client comptait ses trente
secondes pour n'en tirer que « port non publié ». Une base tuée en
pleine écriture (un `:build` mort violemment laisse un WAL à moitié
écrit) échouait ensuite à CHAQUE build — le store est un cache, mais
il se comportait en point de rupture permanent.

## Décision

### 1. La chaîne différée est ACTIVÉE (VI/1)

Les trois `implementation(libs.r8)`, `libs.bundletool`, `libs.guava`
partent du `build.gradle.kts` d'app-android — apksig reste (trop léger
pour un pack) et `toolsApi` garde les ADAPTATEURS dans l'APK : sans
leurs classes d'outil, leur linkage échoue (NoClassDefFoundError) et
`BuildRunner.outil()` replie sur le pack téléchargé une fois — c'est
le chemin prévu (ADR-067). L'APK maigrit de 134,3 Mo à 96,7 Mo
(−37,7 Mo : 33,7 Mo de dex d'outils et 4 Mo de trous de packaging
incrémental — un build PROPRE compacte).

### 2. Le store se soigne (VI/2)

`StoreAndroid.open()` devient auto-réparant et bavard :

- le PRAGMA WAL est une OPTIMISATION : son échec (verrou d'un autre
  processus, politique du système de fichiers) se note sur l'erreur
  standard et le store continue en journal par défaut — jamais un
  build tué par un PRAGMA ;
- une base EXISTANTE qui rate son initialisation part de côté UNE fois
  (`build.db.cassé-<horodatage>`, avec ses compagnons `-wal` et
  `-shm` — le diagnostic reste sur le disque) et une base neuve prend
  le relais : le store est un CACHE (empreintes, instantanés,
  durées), sa perte ne coûte qu'un build à froid, jamais un échec
  permanent ;
- les messages portent le fichier ET la cause brute (« cannot create
  store schema (/…/build.db) : SQLiteException: … ») — la console
  montre la vraie raison, la cause ne se perd plus dans l'exception.

### 3. Le service de build parle (VI/3)

Le corps ENTIER de `BuildService.onCreate()` et `onStartCommand()`
est gardé (`catch (Throwable)`) : notification refusée, politique de
service de premier plan, canal absent — TOUT échec écrit SA cause
dans le canal d'erreur (`demon-build.err`), s'arrête proprement, et
le client la montre pendant son attente au lieu de courir trente
secondes vers un « port non publié » muet. Le repli en processus
(lot U/6) reste le filet : la cause est montrée, le build continue.

## Conséquences

- L'APK arm64 seul mesure 96,7 Mo (contre 134,3 Mo embarqué, ~150 Mo
  à 4 ABI) ; la première construction sur appareil télécharge UNE
  fois ~10 Mo de packs (plus la plateforme android.jar ~61 Mo,
  comportement existant) — vérifiés par taille et SHA-256 avant tout
  posage atomique.
- Les packs régénérés par `:app-android:dexPacksOutils` restent
  identiques octet par octet à ceux de la release — la tâche échoue
  sur divergence, la release et le code ne peuvent pas désynchroniser
  en silence.
- Une base corrompue coûte un build à froid, plus jamais un échec
  répété ; le diagnostic reste sur le disque pour l'inspection.
- Le compilateur Kotlin (43,6 Mo de dex) reste le prochain poste
  (design de fin d'ADR-067).
