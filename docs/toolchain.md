# Chaîne d'outils (P0)

Versions **vérifiées** le 2026-11-15 ; chaque outil d'une phase future
sera vérifié dans SA phase (§4.2 du prompt : jamais d'API inventée).

| Outil | Version | Rôle | Vérification |
|---|---|---|---|
| JDK | Temurin 21.0.13+9 (linux x64) | construction de JoIDE | `java -version` |
| javac | 21.0.13, `--release 17` | compilation (artefacts niveau 17, D2) | `javac -version` |
| Gradle | 9.8.2 (wrapper, distribution épinglée) | build de JoIDE lui-même (D13) | somme de contrôle dans `gradle/wrapper/gradle-wrapper.properties` : `21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607` |
| JUnit Jupiter | 5.14.4 (EPL-2.0) | tests uniquement | `gradle/libs.versions.toml` ; dernière 5.x stable vérifiée sur Maven Central le 2026-11-15 |
| Python | 3.12 (stdlib uniquement) | scripts de contrôle (langue, signatures) | `python3 --version` |

## Donnée API 28

- Source : `platform-28_r06.zip`
  (https://dl.google.com/android/repository/platform-28_r06.zip),
  entrée `android-9/android.jar`, sha256
  `d20853d289a5161d9cf1c1268d7923076487d4fa68c970b0733e2d5a0ccfc3c5`.
- Génération : `python3 scripts/generate_api28_signatures.py <android.jar>
  gradle/api28-signatures.txt.gz` — javap -s -p (JDK requis) + fermeture
  d'héritage ; 63 020 entrées (1 339 classes, 49 478 membres, 12 203
  champs).
- Constats de génération (vérifiés sur les données, ADR-013) : présent —
  `String.repeat`, `SplittableRandom`, `Files.walk`, `computeIfAbsent` ;
  absent — `HexFormat`, `java.lang.Record`, `System.Logger`,
  `LambdaMetafactory`, `StringConcatFactory` (les deux derniers : réécrits
  par D8, autorisés nommément par le scanner).

## Arrivages prévus (vérifiés dans leur phase, pas avant)

| Phase | Outil | Note |
|---|---|---|
| P3 | ecj retenu (ADR-026, même artefact que :ecj-art), compilateur Kotlin embarqué (ADR-027) | fait : versions lues dans l'artefact chargé, diagnostics par API |
| P4 | fait : résolveur maison (ADR-029/033), transport `HttpsURLConnection` vérifié TLS (ADR-031) | checksums .sha256/.sha1, GMM incorporées |
| P5 | aapt2, D8/R8, apksig, bundletool | exécution aapt2 depuis nativeLibraryDir à valider sur appareil |
| P6 | — (daemon, Binder/AIDL) | |
| P7 | — (lecteur statique Gradle maison) | |

## Fabrication des packs différés (lot XXI/b, 2026-11-18)

Les packs dex (`*.dex.zip` de la release `toolchain-v1`) sont
AUTOSUFFISANTS : l'adaptateur du pack charge PAR NOM à travers le
chargeur parent (le mur de chargeurs, ADR-067) — une dépendance qui
voyage « à côté » n'existe pas pour le pack. Le terrain a tranché :
le pack bundletool d'origine ne portait ni protobuf ni guava
(descripteurs référencés, définitions absentes) et
`bundle[debug]` mourait de `NoClassDefFoundError:
Lcom/google/protobuf/GeneratedMessageV3;` sur l'appareil.

Procédure (script : `scripts/fabrique-pack-bundletool.sh`) :

1. Purifier le jar de bundletool (D8 refuse une archive mixte — le
   jar embarque des dex de fixtures de test sous
   `com/android/tools/build/bundletool/archive/dex/*/`).
2. `d8 --release --min-api 28 --lib android.jar` sur le jar purifié
   + TOUTES les dépendances runtime du POM (bundletool 1.18.3 :
   guava 32.0.1-jre, protobuf-java 3.22.3, protobuf-java-util 3.22.3,
   failureaccess, listenablefuture, jsr305, error_prone_annotations,
   checker-qual — versions exactes de l'ère guava-32, du cache gradle).
3. **Vérifier les DÉFINITIONS** (jamais les références) : `dexdump -f`
   doit montrer `Class descriptor` pour `GeneratedMessageV3`,
   `BuildBundleCommand`, `ValidateBundleCommand`, `ImmutableList`,
   `InternalFutureFailureAccess`. Un marqueur qui n'est qu'un
   descripteur référencé = l'omission qui a coûté le lot.
4. Archive déterministe (entrées triées, horodatages fixes), taille
   et SHA-256 épinglés dans `PacksOutils`.
5. Publier comme NOUVEAU fichier versionné (règle dorée : un artefact
   publié ne change jamais) — `bundletool-1.18.3.1.dex.zip`, l'ancien
   reste en place.

Note d'exécution : `set -o pipefail` + `grep -q` tue dexdump par
SIGPIPE dès le premier match (141) et transforme un succès en échec —
le vérificateur lit avec `grep -c` jusqu'à la fin.


## Le pack bundletool porte AUSSI son adaptateur (lot XXII, 2026-11-22)

Le terrain du 2026-11-22 : le pack 1.18.3.1 du lot XXI/b portait
bundletool + toute sa fermeture runtime mais PAS l'adaptateur
`jo/joide/build/android/tools/BundletoolAssembler` — le lot VIII
(ADR-070) l'a retiré de l'APK précisément parce que le pack doit le
porter (« aucune classe d'outil dans l'app, tout arrive déjà dexé ») :
le bouton build mourrait de `IllegalStateException: embedded build
tool unavailable: BundletoolAssembler — ClassNotFoundException` (le
ServiceLoader de l'APK ne voit pas l'adaptateur, `Class.forName` sur
l'APK non plus, et le chargeur du pack ne le définit pas non plus).

Procédure inchangée en apparence, le vrai changement est le DÉXAGE
ENSEMBLE de l'adaptateur avec la fermeture runtime — le miroir exact
de la tâche `:app-android:dexPacksOutils`, qui reproduit l'artefact
octet pour octet (D8 du jar r8 du catalogue, jamais le d8 du SDK) :

1. Extraire du jar tools (`:build-android:toolsJar`) SEULES les
   classes du préfixe `jo/joide/build/android/tools/
   BundletoolAssembler` (le mur de chargeurs reste étanche : l'API
   `BundleAssembler` et `packaging.ApkWriter` restent DANS l'APK,
   chargées par nom à travers le chargeur parent).
2. Assainir chacun des neuf jars (dex embarqué de bundletool,
   module-info, variantes multi-release de guava hors) — entrées
   triées, ordre canonique fixe : bundletool, guava, protobuf-java,
   protobuf-java-util, failureaccess, listenablefuture, jsr305,
   error_prone_annotations, checker-qual.
3. `java -cp r8-<version>.jar com.android.tools.r8.D8 --release
   --min-api 28 --lib android.jar` sur les neuf jars assainis +
   l'adaptateur, D'UN SEUL COUP, l'ordre canonique prime.
4. **Vérifier les définitions** — le marqueur du lot XXII est
   `Ljo/joide/build/android/tools/BundletoolAssembler;` en plus des
   cinq du lot XXI/b.
5. Publier comme NOUVEAU fichier versionné (règle dorée tenue) :
   `bundletool-1.18.3.2.dex.zip`, les .0 et .1 restent en place ;
   taille et SHA-256 épinglés dans `PacksOutils` (5 655 184 octets,
   `f5b09784…d9d5300a`).
