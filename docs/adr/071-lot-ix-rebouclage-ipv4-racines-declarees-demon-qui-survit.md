# ADR-071 — Lot IX : le rebouclage IPv4, les racines déclarées, le démon qui survit

- **Statut** : Accepted (mandat du propriétaire du 2026-11-17 : « La CI
  est rouge. J'ai ouvert l'application puis le bouton build » — build
  #1 : « service de build injoignable (port publié mais connexion
  refusée 6 fois — le démon :build meurt juste après publication) »,
  repli en processus, puis « :core:compileKotlin : échouée … cannot
  scan input directory: $PROJECT/core/src/main/kotlin » ; build #2 :
  « port non publié en 30 s et le processus :build n'a JAMAIS démarré
  (restriction ROM ou batterie ?) »)
- **Date** : 2026-11-17

## Contexte

Le lot VIII avait rendu les silences du service NOMMÉS (ADR-070,
VIII/3) — et ce nommage a permis le diagnostic que les trente secondes
muettes interdisaient. Le rapport de terrain distingue enfin trois
faits :

1. le port est PUBLIÉ (le client le lit six fois), et chaque
   connexion à `127.0.0.1` est REFUSÉE ;
2. aucun fichier `demon-build.err` n'existe (le client aurait montré
   la cause) — donc AUCUN des blocs gardés du service n'a vu d'échec ;
3. au build #2, aucune preuve de vie en trente secondes, alors que le
   port continue d'être republié toutes les cinq secondes.

Pendant ce temps, le repli en processus va jusqu'à la compilation de
`:core` — la chaîne différée du lot VIII fonctionne — et échoue sur un
répertoire source déclaré mais jamais créé.

## Décision

### 1. Le rebouclage IPv4 EXPLICITE — le démon n'était jamais mort (IX/1)

Diagnostic : `InetAddress.getLoopbackAddress()` ne rend PAS
127.0.0.1 sur Android — la documentation le dit (« the IPv4 loopback
address, 127.0.0.1, or the IPv6 loopback address, ::1 ») et libcore
livre `Inet6Address.LOOPBACK`, c'est-à-dire **::1**. Le démon écoutait
donc sur `[::1]:port` pendant que le client composait
`127.0.0.1:port` : connexion refusée À CHAQUE tentative, démon en
parfaite santé. La JVM de bureau, elle, rend 127.0.0.1 — les tests
passaient, l'appareil échouait. Le « démon qui meurt juste après
publication » était une interprétation du client, pas un fait ; le «
processus qui n'a JAMAIS démarré » du build #2 était tout aussi faux :
le service VIVAIT depuis le build #1 (onCreate ne repasse jamais), il
republiait le port sans jamais republier la preuve de vie.

Correctifs :

- `HoteDemonSocket` bind le rebouclage **IPv4 explicite**
  (`InetAddress.getByAddress({127,0,0,1})` — pas de résolution, pas de
  préférence de pile, présent sur tout appareil Android) ; le client
  compose déjà `127.0.0.1` : les deux bouts s'accordent partout ;
  `adresseEcoute()` expose le choix et le test
  `leServeurEcouteEnIpv4Explicite` l'ancre pour toujours ;
- `republierPort()` republie AUSSI la preuve de vie : un service vivant
  qui republie se lit vivant — le faux « JAMAIS démarré » ne peut plus
  se produire pour un démon sain ;
- le message du client ne prétend plus connaître la cause (« rien
  n'écoute sur 127.0.0.1 » au lieu de « meurt juste après
  publication »).

### 2. Les racines déclarées EXISTENT, le moteur TOLÈRE (IX/2)

Deux couches, chacune nécessaire :

- **La création** : le modèle « application-android » déclare
  `core/src/main/kotlin` sans jamais y mettre de fichier — le dossier
  n'existait pas. `GenerateurProjet.Generation` porte désormais ses
  `dossiers` (les racines déclarées de chaque module retenu) et
  `EcrireProjet.ecrire` les crée dans le temporaire atomique : le
  dossier vide se voit dans l'arborescence de l'atelier, l'utilisateur
  peut y créer ses fichiers ;
- **Le moteur** : un répertoire d'entrées ABSENT vaut VIDE — sémantique
  Gradle (`BuildEngine.scanStamps` rend une liste vide sur
  `notExists`) ; un projet IMPORTÉ qui déclarerait une racine jamais
  créée ne meurt plus au premier build. Quand le dossier apparaîtra,
  l'empreinte changera et la tâche repartira. Un FICHIER là où un
  dossier est attendu reste une erreur (`NotDirectoryException`).

Preuves : `lesRacinesDeclareesSansFichierExistentDesLaCreation`,
`lesDossiersDeclaresSontCrees`, `dossierDeclareHorsDuProjetRefuse…`
(app-android) et `declaredButUncreatedSourceRootIsEmptyNotAnError`
(build-jvm, bout en bout : le cas exact de MyApp).

### 3. Le démon SURVIT à ses pannes — sauf à l'injection (IX/3)

L'ancienne boucle ne rattrapait que `IOException` : un `Error` (classe
absente, lien natif cassé) tuait le fil d'acceptation en silence — pas
de fichier d'erreur, socket fermée, et le client ne voyait qu'un «
connexion refusée » sans cause. Désormais :

- chaque requête vit dans SA garde dans `BoucleDemon.servir` : un
  cadre illisible (ou un handler cassé) se RÉPOND (`error`), le canal
  reste debout ;
- `BoucleDemon.build` rattrape `Throwable` : un `Error` coûte LE build
  (répondu `build-finished` en échec), pas le service ;
- `HoteDemonSocket.accepter` rattrape `Throwable` par connexion : la
  connexion tombe, la cause s'ENREGISTRE (`panique()`), la connexion
  suivante reste servie ;
- le chien de garde du service publie la cause enregistrée dans
  `demon-build.err` AVANT de s'arrêter : le client qui attend le port
  montrera la cause réelle d'une mort anormale.

L'injection de panne (« oom », `-Djoide.daemon.faults=1`) garde SA
sémantique de mort : `BoucleDemon.PaniqueFatale` TRAVERSE toutes les
gardes (la boucle, l'hôte) — les preuves de robustesse
(`DaemonDeathResumeTest` : le démon meurt, le client survit, le disque
reprend) restent intactes. Preuve nouvelle :
`uneRequeteCasseeNeTuePasLeCanal`.

### 4. La CI — le cache de configuration refusait la garde du lot VIII (IX/4)

Le run du lot VIII (95cec9e) avait construit les DEUX APK — puis
échoué au STOCKAGE du cache de configuration : la tâche
`verifierOmbreApk` capturait `layout` (service du projet) DANS son
`doLast` : « cannot serialize Gradle script object references ». Le
bac à sable de construction tourne avec `configuration-cache=false`
(la mémoire avant tout) : la faute n’y apparaissait jamais — le
journal du run l’a nommée. Correctif : le chemin de l’APK se résout à
la CONFIGURATION et se capture comme `File` (sérialisable) ; la
commande CI exacte (assembleDebug + assembleRelease, cache actif)
passe et STOCKE son entrée, garde `verifierOmbreApk` verte.

## Conséquences

- Le build sur appareil passe par le démon (plus de repli systématique)
  : le port publié est un port ÉCOUTÉ, et la vie publiée est une vie
  VRAIE à chaque republication.
- Un projet créé depuis un modèle a TOUTES ses racines sources sur
  disque ; un projet importé qui déclare une racine absente construit
  quand même (racine vide), conformément à Gradle.
- Une panne du démon se voit : la cause part dans `demon-build.err` et
  la console la montre pendant l'attente du port.
- Suites au vert après le lot : build-daemon, core-tasks, build-jvm,
  app-android (unitaires) ; politique de langue inchangée (aucune
  faute nouvelle).
- Le dépôt construit désormais avec son cache de configuration
  ACTIF de bout en bout (la faute n'était visible qu'en CI) ; le bac
  à sable local peut garder `configuration-cache=false` pour la
  mémoire — la commande CI reste la référence.
