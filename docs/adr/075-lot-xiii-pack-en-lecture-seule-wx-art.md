# ADR-075 — Lot XIII : le pack en lecture seule — la W^X d'ART,
# la vraie cause racine du 134

- **Statut** : Accepted (le logcat du terrain, fourni par le
  propriétaire le 2026-11-17, nomme enfin la cause racine)
- **Date** : 2026-11-17

## Contexte

Le terrain envoie le « Fatal signal » que le stderr vide du lot X
ne pouvait pas montrer :

```
SecurityException: Writable dex file
'/data/user/0/jo.joide.app/files/toolchain/packs/kotlin-compiler-2.3.0.dex.zip'
is not allowed.
  at dalvik.system.DexFile.openDexFileNative(...)
  at dalvik.system.DexPathList.makeDexElements(...)
  at dalvik.system.BaseDexClassLoader.<init>(...)
java.lang.ExceptionInInitializerError
  at java.lang.ClassLoader.getSystemClassLoader()(ClassLoader.java:1915)
Check failed: system_class_loader != nullptr (abort 134)
```

La VM ouvrière forkée aborte au DÉMARRAGE, en construisant son
chargeur de classes système depuis le classpath : le pack
`kotlin-compiler-2.3.0.dex.zip` est MODIFIABLE par l'application, et
la politique W^X d'Android (10+, renforcée sur 14+) refuse de charger
un dex/jar modifiable. Le `Check failed: system_class_loader !=
nullptr` est l'abort consécutif — la sortie 134 sans stderr de TOUS
les rapports depuis le lot X. Aucun rapport au JIT (#1748, ≤ 30),
aucun à l'OOM mid-run : l'échelle de tas du lot X/XII travaillait sur
une cause orthogonale (elle reste utile et correcte, mais elle ne
guérissait pas celui-ci).

La manière CodeAssist (les deux sites de référence) :

- `ArtKotlinPluginLoader.kt` : `tmp.toFile().setReadOnly()` AVANT le
  move atomique — « Android 14+ won't load a writable dex/jar through
  a class loader (W^X) » ;
- `ForkedKotlincArtSpike.kt` : `dest.setReadOnly()` à l'extraction de
  chaque dex — « ART rejects a writable dex on a VM classpath ».

joide a le setReadOnly au CHARGEUR en processus depuis le lot VIII
(`ChargeurDexAndroid`) — mais le FICHIER du pack passe à la VM forkée
par `PacksOutils.fichierAssure`, qui ne l'imposait pas ; et surtout,
un pack déjà sur le disque (posé par une version antérieure, sain en
taille + SHA-256, donc JAMAIS re-téléchargé) restait modifiable pour
toujours — l'état exact de l'appareil du propriétaire.

## Décision

- `fichierAssure` impose la LECTURE SEULE à CHAQUE résolution
  (`imposerLectureSeule`, idempotent, « au mieux » : jamais un échec
  dur si le système de fichiers refuse — la même tolérance que
  `ChargeurDexAndroid`). C'est le point d'étranglement de tous les
  consommateurs : le chargeur en processus ET le classpath de la VM
  forkée. La réparation au chargement couvre les packs déjà sur le
  disque SANS re-téléchargement.
- `telechargePinned` fait naître le fichier en lecture seule : le
  staging passe en lecture seule AVANT le move atomique (le patron
  exact d'`ArtKotlinPluginLoader`) ; une cible déjà posée se SUPPRIME
  avant le move (un pack corrompu en lecture seule doit pouvoir se
  ré-écrire — le fichier se remplace, il ne se ré-ouvre pas en
  écriture) ; le staging résiduel se nettoie en `finally`.
- NOT : déplacer les packs hors du répertoire de l'application — la
  W^X ne réclame que la non-modifiabilité, pas le lieu. NOT : changer
  `ChargeurDexAndroid` — il fait déjà le setReadOnly depuis le lot
  VIII ; `fichierAssure` le rend redondant, pas faux.

## Conséquences

- Le prochain terrain doit CHARGER : le pack modifiable passe en
  lecture seule à la première résolution, la VM ouvrière démarre, la
  compilation repart — sans re-téléchargement ni nettoyage manuel.
- Les essais desktop prouvent le chemin exact du terrain : un pack
  posé MODIFIABLE (comme les versions antérieures le posaient), sain
  pour son épinglage, passe en lecture seule au premier
  `fichierAssure`, reste sain, idempotent au second passage, et son
  contenu ne bouge pas.
- La classe des 134 « sans stderr » est CLOSE : toute future sortie
  128+n sur la VM ouvrière a maintenant trois narratifs nommés dans
  la console (tas essayés, remontée plafonnée, cause stderr) et la
  vraie cause au logcat — le propriétaire sait où chercher.
