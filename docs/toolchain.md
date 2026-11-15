# Chaîne d'outils (P0)

Versions **vérifiées** le 2026-11-15 ; chaque outil d'une phase future
sera vérifié dans SA phase (§4.2 du prompt : jamais d'API inventée).

| Outil | Version | Rôle | Vérification |
|---|---|---|---|
| JDK | Temurin 21.0.13+9 (linux x64) | construction de JoIDE | `java -version` |
| javac | 21.0.13, `--release 17` | compilation (artefacts niveau 17, D2) | `javac -version` |
| Gradle | 9.8.2 (wrapper, distribution épinglée) | build de JoIDE lui-même (D13) | somme de contrôle dans `gradle/wrapper/gradle-wrapper.properties` : `21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607` |
| JUnit Jupiter | 5.14.4 (EPL-2.0) | tests uniquement | `gradle/libs.versions.toml` ; dernière 5.x stable vérifiée sur Maven Central le 2026-11-15 |
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
  `String.repeat`, `SplittableRandom`, `Files.walk`, `computeIfAbsent` ;
  absent — `HexFormat`, `java.lang.Record`, `System.Logger`,
  `LambdaMetafactory`, `StringConcatFactory` (les deux derniers : réécrits
  par D8, autorisés nommément par le scanner).

## Arrivages prévus (vérifiés dans leur phase, pas avant)

| Phase | Outil | Note |
|---|---|---|
| P3 | ecj retenu (ADR-026, même artefact que :ecj-art), compilateur Kotlin embarqué (ADR-027) | fait : versions lues dans l'artefact chargé, diagnostics par API |
| P4 | — (résolveur maison, transport HTTP vérifié TLS) | checksums .sha256/.sha1 |
| P5 | aapt2, D8/R8, apksig, bundletool | exécution aapt2 depuis nativeLibraryDir à valider sur appareil |
| P6 | — (daemon, Binder/AIDL) | |
| P7 | — (lecteur statique Gradle maison) | |
