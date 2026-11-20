# PHASE C12 — la sonde vit dans la tâche, le cache de configuration se stocke enfin (2026-11-20)

**Mandat du propriétaire, mot pour mot.** « Le CI est rouge. » Le
run 108 de « APK signés » (44664904866, commit a4e02ae, le lot C11
venait d'être poussé) tombe à l'étape « Assemblage des APK signés
(debug + release) » — la couleur rouge arrive une minute après le
début du rouge précédent : le lot C11 avait corrigé le terrain de
l'appareil, mais sa manière de l'écrire faisait tomber le pipeline
qui prouve les APK.

## 1. Le diagnostic — ni la signature, ni l'assemblage

Le journal du run dit l'essentiel : `> Task :app-android:assembleDebug`
puis `> Task :app-android:assembleRelease` passent TOUS les DEUX —
les APK sont assemblés et signés. Le rouge vient APRÈS, au moment de
plier le build :

```
Configuration cache problems found in this build.
2 problems were found storing the configuration cache, 1 of which
seems unique.
- Task `:app-android:genererKeystoreDebug` of type
  `org.gradle.api.DefaultTask`: cannot serialize Gradle script object
  references as these are not supported with the configuration cache.
```

La sonde PBES2 du lot C11 (`portePbes2`, la garde d'octets du
keystore embarqué) vivait en FONCTION TOP-LEVEL du script Kotlin. Or
`outputs.upToDateWhen { … !portePbes2(cible) }` et le `doLast` (deux
appels) l'invoquent depuis des lambdas que le cache de configuration
DOIT sérialiser : chaque lambda capture alors L'INSTANCE DU SCRIPT —
et Gradle refuse de la porter. La première panne avait déjà été
rencontrée et consignée par le dépôt : le commentaire de
`verifierOmbreApk` cite le run 95cec9e du 2026-11-17 (« les DEUX APK
étaient construits, le rouge ne venait que de ce stockage ») —
`fetchAapt2` et `verifierOmbreApk` suivent depuis la discipline : ne
capturer QUE des locales sérialisables. Le lot C11, écrit dans la
hâte du terrain, a oublié la discipline : `portePbes2` est la seule
fonction top-level de tout le script.

## 2. Le correctif — le déménagement de la sonde

L'OID (`oidPbes2`, IntArray) et la sonde (fonction anonyme
`(File) -> Boolean`) déménagent DANS le bloc de configuration de la
tâche : des VALEURS LOCALES capturées — Fichier, tableau d'entiers,
fonction anonyme — aussi sérialisables que les `cible` et `marqueur`
de `fetchAapt2`. Zéro changement de comportement : mêmes octets OID
(`06 09 2A 86 48 86 F7 0D 01 05 0D`), mêmes gardes (l'à-jour et la
vérification post-génération au même endroit), même command keytool
avec `keystore.pkcs12.legacy=true`. UN fichier touché
(`app-android/build.gradle.kts`), 30 insertions, 18 suppressions —
aucun code Java, aucun test touché : les 658 tests verts du C11
restent la vérité d'exécution.

## 3. Le banc — rouge puis vert, sur Gradle 9.8.2

La même version que le CI, projet minimal, réplique EXACTE de la
tâche fautive : la panne se reproduit MOT POUR MOT (« 2 problems …
cannot serialize Gradle script object references »). Le patron
corrigé, l'exercice complet :

- stockage : l'entrée se pose SANS problème ;
- rechargement : deuxième run « Reusing configuration cache », tâche
  UP-TO-DATE — la lambda dé-sérialisée vit et juge ;
- garde de re-génération : un PKCS12 MODERNE openssl substitué au
  fichier (PBES2 présent) force la re-génération, le fichier rendu
  n'a plus d'OID PBES2 ;
- lisibilité : `keytool -list` lit le magasin (alias « joide »,
  PrivateKeyEntry).

## 4. La validation sur le dépôt

`gradle :app-android:genererKeystoreDebug` (Gradle 9.8.2, JDK 21
Temurin, SDK 37.2, cache de configuration actif — la configuration
exacte du CI) : premier run, l'entrée se stocke PROPREMENT ; deuxième
run, « Reusing configuration cache » et UP-TO-DATE ; l'asset
`src/main/assets/toolchain/debug.keystore` naît à 2 509 octets, OID
PBES2 ABSENT, `keytool -list` le lit. (Détail du banc local : les
429 en rafale de repo.maven.apache.org imposaient des miroirs de
repli par script d'init — une affaire du banc, jamais du dépôt.)

## 5. Bilan

La correction tient en un déménagement ; le lot C11 garde son
correctif et ses tests, le pipeline redevient démontrable. Le run CI
suivant doit repasser vert — sa couleur fait partie de la livraison,
et l'APK C12 suivra l'artefact du run vert.
