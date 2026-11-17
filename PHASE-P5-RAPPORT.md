# PHASE P5 — RAPPORT

- **Date** : 2026-11-15
- **Base** : P4 validée (`c69aaa4`), feuille de route poussée (`4c852c7`)
- **Module rempli** : `build-android` (paquets `jo.joide.build.android`) + évolution de `core-tasks`

## 1. Ce qui est fait

Le pipeline Android complet, de la ressource à l'artefact installable :

- **Outillage** — localisation par variables d'environnement, classpath
  ou téléchargement épinglé (URL + SHA-256 fixes, transport HTTPS
  vérifié du résolveur, extraction protégée zip-slip, idempotence par
  marqueur) ; aapt2 9.5.1 (binaire natif, processus externe),
  plateforme android-35, D8/R8 9.5.32 ;
- **aapt2** — compilation incrémentale par fichier (seul le fichier
  touché est recompilé, les suppressions retirent le plat
  correspondant), lien binaire (archive liée, R.java, R.txt) et lien
  proto (bundle), diagnostics structurés avec la sortie d'aapt2 ;
- **R** — les sources R.java générées sont compilées par ecj en jar
  déterministe, avec manifeste ABI stable écrit par la tâche : la
  compilation aval dépend du R par empreinte de contenu, pas de
  l'heure ;
- **Variantes** — type de build, identifiant d'application, versions,
  minSdk/targetSdk, rétrécissement R8, schémas de signature v1/v2/v3 ;
- **Dex par scopes** — classes de l'application, jar de chaque module
  bibliothèque, chaque jar externe et chaque AAR (classes.jar +
  libs/*.jar) forment des scopes séparés ; **cache de dex
  partageable entre projets** : l'empreinte d'un scope externe ne
  dépend que du contenu, de l'API minimum et de l'outil — et le cache
  de sorties découvre désormais sur le disque les entrées écrites par
  d'autres projets (évolution core-tasks, testée) ;
- **Fusion par buckets** — répartition déterministe sous le plafond
  de 65 536 méthodes référencées (marge de 512), le code de
  l'application au bucket principal, un scope seul au-dessus du
  plafond est signalé, jamais caché ; fusion par fork à gros tas ;
- **D8/R8 en processus** (source set tools, ServiceLoader) pour les
  scopes — compilateurs chauds pour le démon — et par fork pour la
  fusion et le rétrécissement (pic de mémoire isolé de la machine
  virtuelle du build) ;
- **Package** — écriture zip maison déterministe et alignée (le
  remplissage d'alignement vit dans le champ « extra », comme
  zipalign ; resources.arsc et dex non compressés alignés sur
  quatre octets, bibliothèques natives sur seize) ;
- **Signature** — apksig, schémas explicites par variante,
  revérification systématique après signature (v1 exigée pour les
  minSdk sous 24, v2 vérifiée au niveau de la variante) ;
- **AAB** — charge du module de base écrite à la main (manifeste et
  table proto du lien aapt2, dex, ressources proto extraites),
  assemblage par bundletool, **validation par bundletool validate** ;
- **R8** — rétrécissement de la variante avec règles de conservation
  et mapping de renommage.

## 2. Critères d'acceptation P5, un par un

| # | Critère (invariants du module) | Statut | Preuve |
|---|---|---|---|
| 1 | Travail proportionnel à la modification | ✅ | `AndroidPipelineTest.incrementalBuildIsProportionalToChange` : après le changement d'une chaîne, seuls `app:processAndroidResources` (1 fichier recompilé), `app:linkAndroidResources`, package, signature et bundle se réexécutent ; aucune tâche `lib:` ne tourne ; le R.jar reste à jour (les identifiants n'ont pas changé) ; le dex de la bibliothèque n'est pas recalculé |
| 2 | Cache de dex partageable entre projets | ✅ | `SharedDexCacheTest.secondProjectRestoresExternalDexFromSharedCache` : deux projets distincts (stores distincts), même dépôt de bibliothèques et même cache de sorties — le premier dexe la bibliothèque externe, le second la **restaure du cache** (`FROM_CACHE`, aucun redexage, le dex est sur le disque) ; découverte disque prouvée dans `OutputCacheTest.foreignEntryIsDiscoveredFromDisk` |
| 3 | Incrémental ≡ propre | ✅ | `AndroidPipelineTest.incrementalMatchesCleanBuildByteForByte` : après modification d'une ressource et d'une source, l'APK non signé du build incrémental et celui d'un build propre du projet modifié sont **identiques octet par octet** |
| 4 | Pipeline complet jusqu'à l'artefact | ✅ | `AndroidPipelineTest.fullBuildProducesSignedVerifiedApkAndValidBundle` : application + bibliothèque (ressources et sources), APK signé v1+v2 **vérifié au minSdk de la variante**, resources.arsc non compressée alignée, classes.dex présent, AAB **validé par bundletool** |
| 5 | Les dex et la fusion consomment l'extraction AAR vérifiée de P4 | ✅ | `AndroidTaskFactory` : chaque AAR contribue classes.jar et libs/*.jar au classpath de compilation et comme scopes de dex ; l'extraction zip-slip de l'outillage suit la même politique que l'extracteur d'AAR (`SafeZipTest`, sept formes refusées) |
| 6 | Le pic de mémoire de la fusion n'atteint pas le build | ✅ | Fusion et rétrécissement par fork (ADR-005) : `ForkDexer` lance une machine virtuelle fille à plafond propre ; preuve d'exécution dans `DexerToolTest.mergeCombinesDexFiles` et `shrinkRemovesDeadCode` |
| 7 | Le désucrage D8 des enregistrements est vérifié | ✅ | `DexerToolTest.recordsAreDesugaredByD8` : un enregistrement Java 17 est compilé puis désucré par D8 en dex valide (ferme l'item backlog P3/P5 pour les records ; interfaces scellées au backlog) |
| 8 | `check` vert sans SDK Android | ✅ | `./gradlew check` — 306 tests, 0 échec (28 pour build-android) ; l'outillage est téléchargé au premier passage, les tests d'outillage se sautent explicitement sans réseau ni cache |
| 9 | Aucun symbole hors niveau 28 dans le source set principal | ✅ | `checkApi28` vert sur build-android : le source set principal ne référence ni `com/android/` ni de java.* hors niveau 28 ; les adaptateurs vivent dans le source set `tools` (ADR-034) |

Vérification globale : `./gradlew check` — **BUILD SUCCESSFUL**,
306 tests, 0 échec.

## 3. Détail des tests ajoutés en P5 (29 cas)

| Suite | Cas |
|---|---|
| `SafeZipTest` (3) | sept formes de zip-slip refusées une par une, extraction heureuse idempotente par marqueur, raisons de la politique |
| `DexBucketsTest` (7) | petits dex regroupés, gros scope isolé avec bucket suivant, dépassement du plafond réel signalé, déterminisme de la répartition, bucket principal vide préservé, lecture d'en-tête dex (comptages), magie invalide rejetée |
| `ApkWriterTest` (4) | deux écritures identiques octet par octet, entrées non compressées alignées (vérifié par balayage des en-têtes locaux), lecture par le lecteur zip du JDK, entrées triées |
| `Aapt2ToolTest` (4) | compilation par fichier (nommage plat) et par répertoire, lien avec R.java et R.txt portant les symboles, manifeste cassé → diagnostic nommant la ressource manquante, lien proto (manifeste + resources.pb) |
| `DexerToolTest` (6) | dex en processus et par fork (en-têtes valides, comptages), fusion de deux dex par fork, rétrécissement R8 (classe morte éliminée, mapping), désucrage des enregistrements |
| `AndroidPipelineTest` (3) | **les preuves 1, 3 et 4** (voir §2) |
| `SharedDexCacheTest` (1) | **la preuve 2** (voir §2) |
| `OutputCacheTest` (core-tasks, +1) | entrée étrangère découverte sur le disque et restaurée par un store vierge |

## 4. Écarts par rapport au prompt

| Écart | Raison | Statut |
|---|---|---|
| **Fusion de manifestes hors périmètre** | le manifeste de l'application est lié tel quel ; les bibliothèques contribuent leurs ressources mais pas leurs entrées de manifeste | Backlog P6/P7 : la sémantique AGP (merge, overlays, placeholders) se décide avec l'import Gradle |
| **R non transitifs par module, identifiants par lien** | chaque module génère son R depuis son propre lien ; le lien de l'application porte la table finale — un code d'application référençant les identifiants d'une bibliothèque compile contre le R de cette bibliothèque (identifiants locaux) | Documenté (ADR-036) ; la cohérence des identifiants à l'exécution sur appareil relève du périmètre P8 |
| **Règles R8 au paquet près** | la règle de conservation porte le paquet de l'application entier | Backlog P5/P8 : lecture des points d'entrée dans le manifeste |
| **Suffixe d'identifiant de debug et ressources par variante** | le lien est unique par module | Backlog P5/P8 |
| **Bibliothèques natives des AAR** | le rédacteur les aligne (seize octets) mais le câblage jni/*.so des AAR vers l'APK n'est pas fait | Backlog P5/P8 |
| **aapt2 hors API 28** | binaire natif invoqué en processus externe : aucun symbole Java à contrôler | Conforme par construction (ADR-034) |

## 5. Mesures de performance

Bac à sable 2 cœurs partagés — indicatif ; le verdissement sur la
machine de référence reste pour les phases concernées :

| Mesure | Obtenu |
|---|---|
| `:build-android:test` complet (28 cas, outillage réel) | 41 s dont `AndroidPipelineTest` 12,8 s (trois builds complets), `DexerToolTest` 7,8 s (forks D8/R8), `SharedDexCacheTest` 5,3 s |
| Build complet application + bibliothèque (16 tâches) | ~4 s par build dans le test d'acceptation (aapt2, ecj, D8 en processus, fork de fusion, signature, bundle, validation) |
| Réexécution après changement d'une chaîne | 5 tâches sur 16 (aapt2 du fichier touché, lien, package, signature, bundle) |
| `./gradlew check` complet | **306 tests, 0 échec** (278 en P4) |

## 6. Dette et risques ouverts

- Fusion de manifestes, règles R8 par manifeste, natives des AAR,
  suffixe de debug, V3/V4 : voir backlog P5.
- Le lien proto d'aapt2 écrit les ressources compilées sous `res/`
  dans l'archive proto : l'extraction vers la charge de module est
  faite, la table des symboles proto (resources.pb) n'est pas
  réinterprétée (pas besoin exprimé à ce jour).
- Le dexage en processus charge D8 dans la machine virtuelle du
  build (désiré : compilateurs chauds P6) ; le plafond mémoire du
  daemon (ADR-005) tranchera la politique scope/fork par appareil.
- L'éviction du cache de dex suit celle du cache de sorties : à
  câbler avec le quota d'espace du démon (P6).

## 7. Questions pour le propriétaire

1. La fusion de manifestes (backlog) se traite avec l'import Gradle
   (P7 connaît la sémantique AGP) : confirmez-vous ce report, ou
   souhaitez-vous une fusion minimale (éléments, attributs outils)
   dès P6 ?
2. Le lien actuel génère le R de chaque module depuis ses propres
   ressources (non transitifs) et le lien d'application porte la
   table finale : convenez-vous que la cohérence des identifiants
   entre R de bibliothèque et table finale relève de P8 (contexte
   d'exécution sur appareil) ?
3. Les tests d'outillage téléchargent 63 Mio au premier passage
   (cache local partagé ensuite) : convient-il de documenter ce
   premier passage dans le guide de prise en main de P8, ou
   préférez-vous un paquet d'outillage versionné dans le dépôt ?

## 8. ADR ajoutés

- **ADR-034** — adaptateurs d'outils Android derrière des
  interfaces, source set `tools` : API 28 respectée, D8/R8 en
  processus pour les compilateurs chauds, fork pour les grosses
  fusions, ServiceLoader pour le câblage.
- **ADR-035** — outillage épinglé : variables d'environnement,
  classpath, puis téléchargement URL + SHA-256 par le transport
  vérifié ; extraction protégée ; plateforme jamais redistribuée.
- **ADR-036** — dex par scopes, buckets multidex, cache partageable :
  empreintes ancrées, découverte disque du cache de sorties,
  dépassement de plafond signalé.
- **ADR-037** — package déterministe aligné, schémas de signature
  explicites revérifiés, AAB par charge de module validée.

## 9. Reproduction

```bash
export JAVA_HOME=<jdk-21>
./gradlew check                    # 306 tests, 0 échec
./gradlew :build-android:test      # 28 cas, outillage réel
```

Aucun SDK Android n'est requis ; le premier passage télécharge
l'outillage épinglé (aapt2, plateforme 35, D8/R8) dans
`~/.cache/joide/toolchain` — voir `docs/dev.md`, notes P5.
