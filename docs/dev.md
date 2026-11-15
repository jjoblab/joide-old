# Guide de développement

## Prérequis

- JDK 21 (javac requis — par ex. Temurin 21) ; `JAVA_HOME` pointé
  dessus.
- Python 3 (script de politique de langue et génération de signatures,
  stdlib uniquement).
- Réseau au premier lancement (téléchargement de la distribution Gradle
  9.8.2, somme de contrôle vérifiée par le wrapper).
- **Aucun SDK Android requis en P0** : les 14 modules sont purs ; le
  critère « check vert sans SDK » (§7 P0) l'exige.

## Commandes

```bash
export JAVA_HOME=<jdk-21>
./gradlew check                      # tous les modules : tests, règle de
                                     # dépendances, politique de langue, API 28
./gradlew ciLocal                    # check + bench S/M contre les baselines
./gradlew :tools-bench:bench -Ptier=S|M|L|XL   # génère + mesure (3 runs,
                                                # déterminisme auto-vérifié)
./gradlew :tools-bench:benchS                  # variante à tier fixe
./gradlew :tools-bench:regressionTestS         # comparaison à la baseline S
```

`regressionTest*` compare l'exact (hash, comptages, octets) et le temporel (temps CPU, ±10 %). Le temporel n'a de sens que sur une machine de performance stable : exécuter ces tâches sur la machine de référence, pas sur un ordonnanceur partagé (ADR-014).

## Notes P1

- Les tests du store incluent le harnais de pannes (deux cents meurtres
  de machines virtuelles filles) : comptez environ deux minutes sur
  `:core-store:check` ; ils sont reproductibles (graine fixe, ADR-020).
- Le test de parcours à froid crée cent mille fichiers dans un
  répertoire temporaire (quelques dizaines de secondes de préparation,
  hors mesure).
- `core-store` sépare interface (source set principal, API 28) et
  implémentation de bureau (source set `jvm`, pilote SQLite JDBC) ;
  le contrôle API 28 ne voit que le principal (ADR-019).

## Notes P2

- Les tests de l'ordonnanceur exécutent plus de mille builds de
  graphes aléatoires sous perturbation des délais (exécuteur
  turbulent à graine fixe) : comptez environ une minute sur
  `:core-tasks:check`.
- Le test de build à chaud au tier L crée 3 600 fichiers
  d'entrée et 1 200 sorties dans un dossier temporaire (quelques
  secondes de préparation, hors mesure).
- `core-store` porte deux implémentations de `BuildStore` : SQLite
  (défaut) et le journal à ajouts (prototype de référence,
  ADR-021) ; `StoreBenchmarkTest` affiche les mesures comparatives
  sans les vérifier.
- L'interface `BuildStore` a évolué en P2 (`listCacheEntries`,
  éviction retournant les entrées) : toute nouvelle implémentation
  doit suivre le contrat mis à jour.

## Régénérer les signatures API 28

```bash
curl -O https://dl.google.com/android/repository/platform-28_r06.zip
unzip platform-28_r06.zip android-9/android.jar
python3 scripts/generate_api28_signatures.py \
    android-9/android.jar gradle/api28-signatures.txt.gz
```

Le sha256 du android.jar et les comptages attendus sont consignés dans
`docs/toolchain.md` ; une divergence est une alerte, pas un échec
silencieux.

## Régénérer les baselines

```bash
./gradlew :tools-bench:bench -Ptier=S   # écrit baselines/s.toml (3 runs)
./gradlew :tools-bench:bench -Ptier=M   # écrit baselines/m.toml
```

Les baselines temporelles dépendent de la machine : régénérer sur la
machine de référence après un changement de matériel, jamais pour
« faire passer » une régression.

## Conventions

- Commits : `feat(scope):`, `fix(scope):`, `chore(scope):`, `docs:` en
  français, un commit par changement, tout correctif accompagné d'un
  test qui échoue avant.
- Aucun secret, aucun jeton, aucun keystore dans le dépôt.


## Notes P3

- `build-jvm` est pur JVM : `./gradlew :build-jvm:test` n'exige aucun SDK
  Android ; le stdlib Kotlin est sur le classpath d'exécution des tests
  (le test différentiel et `InlineBodyAbiTest` le localisent depuis
  `java.class.path`).
- Bac à sable sans JDK complet sur `/usr/lib/jvm` : exporter `JAVA_HOME`
  vers un JDK 21 complet (javac requis par la chaîne d'outillage) ;
  `~/.gradle/gradle.properties` pointe
  `org.gradle.java.installations.paths` vers ce JDK.
- Le test différentiel (200 séquences, ~2 min) est déterministe : graine
  affichée dans le message d'échec ; tout échec se reproduit à l'identique.
- TestKit (`tools-test`) peut rencontrer un 429 transitoire de
  repo.maven.apache.org lors d'une résolution de classpath de compilateur :
  réexécuter (retries configurés dans `gradle.properties`).


## Notes P4

- `core-deps` est pur JVM : `./gradlew :core-deps:test` n'exige aucun
  SDK Android. Le transport HTTPS est prouvé dans le harnais par un
  serveur local au certificat auto-signé (rejet = vérification
  active) ; `keytool` du JDK est requis pour ce test.
- Les tests du résolveur passent par un dépôt `Remote` factice
  (`FakeTransport` sert la fixture sur disque) : le comptage
  `stats.requests` et la concurrence mesurée sont réels, sans socket.
- Sans `JAVA_HOME` complet, exporter vers le JDK 21 (voir notes P3).
- Le lockfile se lit à la main : une entrée par artefact, une arête
  indentée par dépendance ; réordonné/trié par le résolveur, jamais
  fusionné à la main.


## Notes P5

- `build-android` reste pur JVM : le source set principal est sous
  contrôle API 28 ; les adaptateurs d'outils (D8/R8 en processus,
  apksig, bundletool) vivent dans le source set `tools`, chargés par
  ServiceLoader.
- Les tests d'outillage téléchargent une seule fois, dans
  `~/.cache/joide/toolchain` (ou `JOIDE_TOOLCHAIN_DIR`) : aapt2
  (2,4 Mio), la plateforme android-35 (61 Mio, 11 162 entrées) et le
  jar de D8/R8 s'il n'est pas déjà sur le classpath. Sans réseau ni
  cache, ces tests se sautent (hypothèse), jamais en erreur.
- Variables d'environnement pour l'outillage local :
  `JOIDE_AAPT2`, `JOIDE_ANDROID_JAR`, `JOIDE_R8_JAR` (chemins
  explicites prioritaires sur le cache).
- Le test différentiel incrémental ≡ propre compare les APK non
  signés octet par octet : l'écriture zip est déterministe (entrées
  triées, horodatages fixes), le lien aapt2 et D8 le sont aussi.
- La fusion et le rétrécissement passent par un fork
  (`java -cp r8.jar com.android.tools.r8.D8`, plafond 1 Gio) : les
  tests les exécutent réellement — comptez quelques secondes de
  démarrage de machine virtuelle par fusion.
