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


## Notes P6

- Le démon de bureau : `jo.joide.build.daemon.desktop.DaemonMain`
  (source set `desktop`, hors contrôle API 28 comme le pilote
  SQLite) — cadres JSON sur les flux standard, store SQLite persistant
  à la racine des sorties (`build.db`). Le client et le protocole
  (source set principal) restent sous contrôle API 28 : l'application
  Android embarquera le client.
- L'injection de pannes (preuve OOM forcé) est armée par
  `-Djoide.daemon.faults=1` : le démon de production répond
  `crash-refused`.
- Les fils de construction du démon : `-Djoide.daemon.threads=N`
  (4 par défaut ; les preuves à cœur unique le posent pour ne pas
  affamer la machine de test).
- Le banc du démon : `./gradlew :build-daemon:daemonBench -Ptier=S|M|L|XL`
  (tas plafond réglable : `-PdaemonHeapMb=700`). Tier L sur la
  machine de bac à sable : génération 0,9 s, build à froid 19 s
  (400 tâches, pic de tas 95 Mo), build sans changement 1,6 s.
- Les revendications des tâches de fork (fusion/R8 : 1 024 Mo)
  comptent au budget d'admission : un build Android avec
  rétrécissement demande une spec à `heapMb` ≥ 1 280 et un démon dont
  le tas couvre ce budget (1 500 en test) — l'usage réel du démon
  reste en dessous pendant que le fork vit dehors.

## Notes P8 — modules Android et briques

### Prérequis supplémentaires

- **JDK 21 complet** (javac — le JRE du bac à sable ne suffit pas ;
  par ex. Temurin 21 via l'API Adoptium) : `JAVA_HOME` pointé dessus.
- **SDK Android** dans `work/android-sdk` (gitignoré) :
  `platforms;android-36` et `platforms;android-37.2`,
  `build-tools;36.0.0` et `37.0.0`, licences acceptées
  (`licenses/android-sdk-license`). Sans SDK (ou `JOIDE_SKIP_ANDROID=1`),
  les modules Android ne rejoignent pas le build : le `check` des
  modules purs reste le critère P0 — pas une dégradation.
- Le SDK se reconstruit à la main (paquets réels de
  `dl.google.com/android/repository/`) :

```bash
mkdir -p work/android-sdk/{platforms,build-tools,licenses}
printf '8933bad161af4178b1185d1a37fbf41ea5269c55\nd56f5187479451eabf01fb78af6dfcb131a6481e\n24333f8a63b6825ea9c5514f83c2829b004d1fee\n' \
  > work/android-sdk/licenses/android-sdk-license
# décompresser platform-36_r02.zip → platforms/android-36, etc.
# (build-tools_r37_linux.zip → build-tools/37.0.0 : renommer le
#  répertoire extrait, son nom (« android-37.0 ») n'est pas la version)
echo "sdk.dir=$(pwd)/work/android-sdk" > local.properties
```

### Publication des briques en Maven Local (ADR-044)

Une fois par montée de version d'une brique — cloner les refs épinglées
et publier :

```bash
# celeris f9001f0 → jo.celeris:*:1.1.0
git clone github.com/jjoblab/celeris && cd celeris && git checkout f9001fc
./gradlew --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1900m" publishToMavenLocal
# code-lsp 657d787 → jo.codeide.lsp.*:1.15.0 (SANS :host)
# :java :kotlin :xml :model :index :ecj-art
git clone github.com/jjoblab/code-lsp && cd code-lsp && git checkout 657d787
./gradlew --max-workers=2 :java:publishToMavenLocal :kotlin:publishToMavenLocal \
  :xml:publishToMavenLocal :model:publishToMavenLocal :index:publishToMavenLocal \
  :ecj-art:publishToMavenLocal
# LayoutLib 720e85d → com.github.jjoblab.LayoutLib:module-*:3.7.0
git clone github.com/jjoblab/LayoutLib && cd LayoutLib && git checkout 720e85d
./gradlew --max-workers=2 :module-inflater:publishToMavenLocal \
  :module-resources:publishToMavenLocal :module-drawables:publishToMavenLocal \
  :module-themes:publishToMavenLocal :module-attributes:publishToMavenLocal \
  :module-layout:publishToMavenLocal
```

### Mémoire du bac à sable (4 Go)

- Les démons Gradle des briques veulent 3 Go (`org.gradle.jvmargs` de
  leurs `gradle.properties`) : les tuer entre les builds
  (`pkill -f GradleDaemon`) et réduire le tas sur la ligne de commande
  (`-Dorg.gradle.jvmargs="-Xmx1900m ..."`), 2 travailleurs maximum.
- `:app-android:assembleDebug` (APK 88 Mo à dexer) : démon Gradle
  RÉDUIT (`-Xmx1024m`) pour laisser le travailleur D8 vivre
  (`--max-workers=1`) — un démon à 3 Go y est tué par le noyau.

## CI — APK signés (GitHub Actions, ADR-047)

Le workflow « APK signés » (`.github/workflows/apk.yml`) produit à
chaque exécution deux APK installables — debug (logcat, diagnostic) et
release — signés par la MÊME clé CI : une nouvelle génération
s'installe sur la précédente, jamais de désinstallation (exigence du
propriétaire du 2026-11-16). Déclencheurs : `workflow_dispatch`
(onglet Actions) et chaque push sur `main`. Les APK sont dans l'onglet
Actions de la dernière exécution (les artefacts précédents sont
supprimés — quota de stockage du dépôt privé).

Secrets du dépôt (posés le 2026-11-16, restauration dans Paramètres →
Secrets and variables → Actions) :

| Nom | Rôle |
|---|---|
| `JOIDE_CI_KEYSTORE_B64` | keystore de signature CI (base64) |
| `JOIDE_CI_KEYSTORE_PASSWORD` | mot de passe du magasin et de la clé (PKCS12 : un seul) |
| `JOIDE_CI_KEY_ALIAS` | alias de la clé (`joide-ci`) |
| `JOIDE_BRIQUES_PAT` | jeton clonant les dépôts privés celeris/code-lsp/LayoutLib (à remplacer un jour par un PAT fin limité aux trois dépôts) |

La copie de secours de la clé (keystore + identifiants) est détenue
par le propriétaire hors du dépôt : sa perte impose un jour la
désinstallation complète de l'application sur chaque appareil — dès le
premier APK installé, la clé est l'identité de l'application. Pour
restaurer les secrets depuis la copie : `base64 -w0 joide-ci.pkcs12`,
puis poser les quatre valeurs ci-dessus.

Le workflow installe le SDK (plateformes 36 et 37.2, build-tools 36.0.0
et 37.0.0), publie les briques en Maven Local aux refs épinglées
(`CELERIS_REF`, `CODELSP_REF`, `LAYOUTLIB_REF` dans le workflow — une
montée de version s'y fait ET dans `gradle/libs.versions.toml`),
met le résultat en cache, assemble, vérifie les signatures puis dépose
les artefacts. Équivalent local de la signature :

```bash
JOIDE_CI_KEYSTORE=<chemin>/joide-ci.pkcs12 \
JOIDE_CI_KEYSTORE_PASSWORD=<mot de passe> \
JOIDE_CI_KEY_ALIAS=joide-ci \
./gradlew :app-android:assembleDebug :app-android:assembleRelease
```

APK produits : `app-android/build/outputs/apk/{debug,release}/` —
vérification : `apksigner verify --print-certs` (empreinte SHA-256
attendue du certificat CI :
`8da507f58a63eed7d4db4aac71e04bc2c01c8eaa02dff1e6f22d7e1d42646d75`).
