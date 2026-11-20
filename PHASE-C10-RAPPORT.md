# PHASE C10 — le manifeste porte enfin sa version, le bundle assemblé sans réserve (2026-11-20)

**Mandat du propriétaire, mot pour mot.** « Le build affiche :
:app:bundle[debug] : bundle assembly failed: Version code not found in
manifest. » Le chantier du bouton build avait déjà franchi (C8 : le pack
des classes de l'assembleur ; C9 : les SIX dépendances runtime restantes
du POM, dont aapt2-proto) la barrière des `NoClassDefFoundError` —
l'assemblage atteint ENFIN bundletool lui-même, qui REFUSE le module de
base : son code de version ne se trouve nulle part. Un seul terrain, un
seul correctif — la manière AGP exacte, appliquée au manifeste enrichi du
lot XVII.

## 1. Le diagnostic

bundletool, à l'assemblage d'un bundle (`BuildBundleCommand`), lit
`android:versionCode` dans le manifeste du module de base — la valeur
manque et l'assembleur nous la rend telle quelle : « bundle assembly
failed: Version code not found in manifest » (notre habillage de
l'exception, ligne pour ligne).

Or depuis AGP 7, le versionCode vit dans le `build.gradle` (la
variante), PAS dans le manifeste — et le manifeste GABARIT des projets
créés (`application-android.toml` : `package`, rien de plus) est
fidèle à cette manière. Sur les bancs, aucun test ne pouvait le voir :
le pipeline d'acceptation écrit toujours un manifeste avec
`android:versionCode="7"` (AndroidPipelineTest) — le bug n'existait
pas dans ses fichiers.

La manière AGP face au même problème : le merger SYNTHÉTISE dans le
manifeste mergé les attributs que le build porte (paquet depuis
`namespace` — notre lot XVII le fait déjà ; version et SDK depuis le
`build.gradle`). C'est exactement ce qui manquait au manifeste
enrichi.

## 2. Le correctif — `enrichir` porte la version

`Aapt2LinkTask.enrichir` (lot XVII : paquet synthétisé s'il manque)
injecte maintenant AUSSI, quand ils manquent :

- `android:versionCode` — de la première variante du module
  d'application (défaut 1 : un manifeste sans code de version ferait
  échouer bundletool, jamais un bundle silencieux) ;
- `android:versionName` — de la même variante (défaut « 1.0 », rien
  à écrire si absent) ;
- l'élément `<uses-sdk android:minSdkVersion android:targetSdkVersion>`
  — de la variante, celle-là même qui a taillé le dex (les scopes
  dexent à `variant.minSdk` depuis toujours) : sans lui, l'APK mentait
  sur son API minimum.

Les règles du lot XVII tiennent toutes : la copie s'écrit dans `$OUT`,
JAMAIS dans les sources utilisateur ; chaque synthèse émet un WARN qui
nomme le correctif recommandé ; un manifeste qui porte déjà tout reste
copié SANS modification ; les modules bibliothèque n'injectent RIEN (la
variante est `null` pour eux — pas porteurs de version) ; une balise
auto-fermée reçoit les attributs (`android:versionCode="7"/>` reste
valide) mais jamais l'enfant `uses-sdk` (un manifeste auto-fermé ne
peut pas porter d'enfant). L'injection en queue respecte la
multi-ligne et l'auto-fermeture (`injecterAttribut`), et la lecture
d'attribut écarte les noms suffixés d'un namespace
(`attributDe`, regard arrière).

La fabrique (`AndroidTaskFactory`) passe la première variante ENTIERE
à la tâche de lien (elle ne passait que l'identifiant de renommage) :
paquet, code et nom de version, SDK minimum et cible suivent.

## 3. Les versions sont des entrées du lien

Le manifeste enrichi est une SORTIE de la tâche de lien : les valeurs
de la variante l'affectent, elles sont donc des ENTRÉES — quatre
propriétés d'entrée les portent (`joide.aapt2.version-code`,
`-version-name`, `-min-sdk`, `-target-sdk`) et
`optionsFingerprint` passe de `link-v1` à `link-v2` (paquet, code,
nom, SDK). Une variante qui change rejoue le lien — jamais un bundle
périmé servi du cache.

## 4. La reproduction sur le banc — rouge, puis verte

Le nouveau test `AndroidPipelineTest
.manifesteGabaritSansVersionCodeAssembleLeBundle` écrit le manifeste
du GABARIT (paquet, rien de plus) et exige le build complet :

- SANS correctif (le correctif mis au stash) : le pipeline réussit
  TOUT (process, link, R.jar, compile, dex, package, sign) et échoue
  EXACTEMENT comme l'appareil — « app:bundle[debug]:FAILED &lt;&lt;
  bundle assembly failed: Version code not found in manifest.&gt;&gt; ».
  Le banc voit le terrain au mot près : le diagnostic est prouvé, pas
  déduit.
- AVEC correctif : build complet jusqu'au bundle VALIDÉ par bundletool
  (`ValidateBundleCommand`), et le manifeste enrichi porte
  `android:versionCode="7"`, `android:versionName="1.7"`,
  `android:minSdkVersion="23"`.

Quatre tests unitaires nouveaux (`Aapt2LinkTaskTest`) :
`leCodeDeVersionSeSynthetisePourBundletool` (synthèse complète,
TROIS WARN nommés), `leManifesteCompletResteInchange` (intégrité),
`laBaliseAutoFermeePorteSaVersionSansEnfant` (auto-fermeture,
attributs mais jamais d'enfant), `sansVarianteAucuneVersionNEcrit`
(bibliothèque).

## 5. Vérifications

- `:build-android:test` : 67 tests, 0 échec, 0 ignoré (dont 5
  nouveaux) — la suite entière à chaud.
- `:agent:test` : 184 tests, 0 échec.
- `:app-android:testDebugUnitTest` : 403 tests, 0 échec.
- 654 tests verts au total ; APK C10 livré
  (`:app-android:assembleDebug`).

## 6. Fichiers

- `build-android/src/main/java/jo/joide/build/android/tasks/Aapt2LinkTask.java`
  — le manifeste enrichi porte version et SDK ; empreinte `link-v2` ;
  aides `attributDe` / `injecterAttribut` / `codeDeVersion` /
  `nomDeVersion` / `renommage`.
- `build-android/src/main/java/jo/joide/build/android/tasks/AndroidTaskFactory.java`
  — la première variante entière passe à la tâche de lien.
- `build-android/src/test/java/jo/joide/build/android/tasks/Aapt2LinkTaskTest.java`
  — quatre tests nouveaux, appels à jour.
- `build-android/src/test/java/jo/joide/build/android/tasks/AndroidPipelineTest.java`
  — la reproduction gabarit, rouge sans correctif, verte avec.
- `CHANGES.md` — l'entrée du lot.
