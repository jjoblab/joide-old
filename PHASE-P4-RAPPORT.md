# PHASE P4 — RAPPORT

- **Date** : 2026-11-15
- **Base** : P3 validée (`4fd16ee`)
- **Module rempli** : `core-deps` (paquet `jo.joide.deps`)

## 1. Ce qui est fait

Le résolveur de dépendances complet, du coordinate au fichier vérifié
au cache :

- **Coordonnées, versions, exigences** — `Coordinate` (forme Maven
  canonique, ordre total), `Version` (ordre maison documenté et testé
  cas par cas, ADR-029), `VersionRange` (bornes incluses/exclues,
  pincement `[a]`, infinis), `VersionRequirement` (littéral, plage,
  `1.0+`, `+`, `latest.release`, `latest.integration`) ;
- **Métadonnées** — parseurs XML et JSON maison (ADR-028 :
  `org.w3c.dom` est absent de la liste des signatures du niveau 28) ;
  POM avec chaîne de parents suivie, propriétés héritées interpolées,
  gestion de dépendances fusionnée avec importation de nomenclatures,
  exclusions, portées, packaging ; GMM (`.module`) avec variantes,
  attributs, checksums incorporés, `available-at` suivi (jusqu'à trois
  sauts, relatif au répertoire de version), version objet
  (`strictly`/`requires`/`prefers`) ; maven-metadata.xml (versions,
  latest/release, état d'instantané avec `snapshotVersions`) ;
- **Dépôts et transport** — disposition Maven 2 (`Layout`),
  dépôts locaux et distants, transport `HttpsURLConnection` (ADR-031)
  : https seul, TLS vérifié jamais désactivé (preuve par rejet d'un
  certificat auto-signé), redirections sans rétrogradation, plafonds
  de temps et de taille, 404 = introuvable ;
- **Cache disque partagé** (ADR-030) — adressé par contenu
  (`files/xx/<sha256>`), écritures atomiques, relecture revérifiée
  (une divergence retire le fichier, jamais un contenu faux servi) ;
- **Lockfile `joide.lock` v1** (ADR-033) — format déterministe trié,
  portant le graphe (arêtes `api`/`runtime`/`compile`), gel des
  instantanés et sélecteurs dynamiques, relecture à zéro requête ;
- **Résolveur** — deux phases : couverture par lockfile (zéro réseau),
  sinon résolution complète : point fixe des versions (« la plus
  récente gagne », exigence stricte s'impose), fermeture avec
  exclusions par sous-arbre, téléchargements parallèles plafonnés,
  vérification des checksums publiés (GMM incorporée, `.sha256`,
  `.sha1` — la première source disponible fait foi), extraction AAR
  protégée zip-slip (ADR-032), écriture du lockfile (inchangé octet
  pour octet si le contenu est le même) ;
- **Hors-ligne strict** — flag du résolveur : aucune requête tentée,
  échec explicite listant ce qui manque.

## 2. Critères d'acceptation P4, un par un

| # | Critère (ADR-007, invariants du module) | Statut | Preuve |
|---|---|---|---|
| 1 | Une résolution avec lockfile valide ne fait AUCUNE requête réseau | ✅ | `LockfileDeterminismTest.validLockfileMeansZeroNetworkRequests` : transport rendu hostile (toute requête échouerait) puis résolution chaude — succès, `stats.requests == 0`, aucune requête au-delà de la base ; `OfflineStrictTest.offlineWithValidLockfileStillWorks` le confirme en mode hors-ligne strict |
| 2 | Checksum divergent = échec explicite | ✅ | `ChecksumDivergenceTest` : `.sha256` publié inversé → problème `deps.checksum` (avec les deux empreintes affichées), rien n'entre au cache ; GMM incorporée fausse → échec ; `.sha1` seul concordant → accepté ; aucune source publiée → accepté avec l'empreinte calculée, puis zéro requête au chaud (le lockfile tient sans source publiée) |
| 3 | Lockfile identique octet pour octet sur deux résolutions | ✅ | `LockfileDeterminismTest.twoIndependentResolutionsGiveIdenticalLockfiles` : deux caches distincts, deux lockfiles distincts, comparaison `Arrays.equals` — identiques ; la re-sérialisation après relecture est identique (`LockfileTest`) |
| 4 | Versions dynamiques et SNAPSHOT résolues une fois puis figées | ✅ | `SnapshotFreezeTest` : instantané littéral résolu en forme concrète (`1.0-20260115.142030-7` au lockfile) ; le dépôt avance (nouvel horodatage) — la lecture chaude reste sur l'ancienne forme, zéro requête ; sans lockfile, la nouvelle forme est prise (le gel n'est jamais un piège) ; plage dynamique racine : idem |
| 5 | Résolveur Maven + Gradle Module Metadata (parents, nomenclatures, exclusions, strictly, variantes AndroidX/KMP) | ✅ | `ResolverTest` : parents et propriétés héritées (`PomParserTest`), nomenclatures importées (`pom` + `type pom` + `scope import`), exclusions par sous-arbre (l'exclu n'entre que par aucun chemin), conflits « la plus récente gagne », `strictly` GMM s'impose sur une exigence plus récente, variantes `available-at` avec sélection `java-api`/`java-runtime` sur `platform.type androidjvm`, packaging `aar` extrait |
| 6 | Mode hors-ligne strict | ✅ | `OfflineStrictTest` : sans rien au cache → problème `hors-ligne strict` listant les demandes, aucune requête tentée ; avec lockfile valide → fonctionne ; lockfile + cache vidé → échec explicite, aucune requête |
| 7 | Téléchargements parallèles plafonnés | ✅ | `ParallelCapTest` : huit téléchargements, plafond 2 — concurrence réelle mesurée au transport (jauge atomique) jamais au-dessus, et au moins 2 (sinon le test ne prouverait rien) |
| 8 | Extraction AAR protégée zip-slip | ✅ | `AarExtractorTest` : sept formes d'attaque refusées une par une (`../`, absolu, `a/../../`, `C:\\`, `a/./`, `a//b`, `//réseau`) ; politique pure testée sans zip ; extraction idempotente par marqueur d'empreinte |
| 9 | Cache disque partagé entre projets | ✅ | Adressage par contenu (ADR-030) : `DependencyCacheTest` — écriture idempotente, douze écritures concurrentes du même contenu d'accord sur un seul fichier, corruption retirée à la relecture |
| 10 | Transport HTTP vérifié TLS | ✅ | `HttpTransportTest` : le clair refusé d'entrée ; certificat auto-signé rejeté (serveur TLS local — la vérification est active, jamais désactivée) ; redirections 301/302/303/307/308 suivies sans rétrogradation, 200/404/500 non |
| 11 | `check` vert sans SDK Android, aucun symbole hors niveau 28 | ✅ | `./gradlew check` — 278 tests, 0 échec ; `checkApi28` vert sur les 32 classes principales de `core-deps` (l'absence d'`org.w3c.dom` dans la liste a motivé l'ADR-28) |
| 12 | `HexFormat` hors niveau 28 respecté | ✅ | Conversion hexadécimale maison dans `Checksums` (le contrôle `checkApi28` le vérifie, cf. constats de génération `docs/toolchain.md`) |

Vérification globale : `./gradlew check` — **BUILD SUCCESSFUL**,
278 tests (72 pour `core-deps`), 0 échec.

## 3. Détail des tests ajoutés en P4 (72 cas)

| Suite | Cas |
|---|---|
| `VersionTest` (4) | série croissante représentative (20 inégalités), égalité des zéros de fin et alias (`1.0` = `1.0.0` = `1.0-ga`, `a` = `alpha`), drapeau d'instantané, rejet des formes vides |
| `VersionRangeTest` (5) | bornes incluses/exclues, infinis, pincement `[1.2.3]`, formes invalides |
| `CoordinateTest` (5) | analyse des formes Maven, canonique, `ga`/`key`, ordre, rejets |
| `XmlTest` (5) | éléments/attributs/textes, entités et CDATA, commentaires et instructions, documents brisés, filtrage par nom |
| `JsonTest` (5) | fichier `.module` complet, échappements, virgules finales, documents brisés, membre d'un non-objet |
| `PomParserTest` (6) | parents + propriétés héritées + gestion héritée, nomenclatures importées sur place, exclusions/optionnel/portées, interpolation des construits, parent introuvable (modèle refusé), échec de lecture en problème |
| `GmmParserTest` (3) | variantes et données de sélection (dont `available-at`), documents brisés, `formatVersion` |
| `MavenMetadataParserTest` (3) | métadonnées d'artefact, métadonnées de version (instantané concret par extension/classifieur), documents brisés |
| `ResolverTest` (8) | fermeture par portées POM (api/runtime/provided ; optionnelle et test jamais), variantes GMM avec `available-at` (AAR extrait, genres d'arête), conflit « la plus récente gagne », nomenclature épingle les versions sans version, `strictly` s'impose, nomenclature en racine refusée, introuvable explicite, exclusions par sous-arbre |
| `LockfileDeterminismTest` (2) | **les preuves 1 et 3** (voir §2) |
| `ChecksumDivergenceTest` (4) | **la preuve 2** et ses variantes |
| `SnapshotFreezeTest` (2) | **la preuve 4** (instantané concret, puis plage dynamique) |
| `OfflineStrictTest` (3) | **la preuve 6** |
| `ParallelCapTest` (1) | **la preuve 7** |
| `LockfileTest` (5) | aller-retour sérialisation/lecture, classifieurs, doublons rejetés, lignes brisées en problèmes, `sha256` mal formé rejeté |
| `DependencyCacheTest` (4) | aller-retour, corruption retirée, douze écritures concurrentes, zone d'attente et AAR |
| `AarExtractorTest` (3) | disposition extraite, zip-slip refusé (sept formes), politique pure |
| `HttpTransportTest` (3) | **la preuve 10** |

## 4. Écarts par rapport au prompt

| Écart | Raison | Statut |
|---|---|---|
| **`org.w3c.dom` remplacé par un parseur XML maison** | absent de la liste des signatures API 28 (vérifié dans `api28-signatures.txt.gz`) : le DOM standard aurait été refusé par `checkApi28` | Consigné ADR-028 ; même philosophie que le TOML maison de P1 |
| **Question 1 du rapport P3 (exécution pipeline M/L) non reprise ici** | le propriétaire a validé P3 sans demander d'addendum (« Je valide, Go P4 ») ; interprétation donnée dans la réponse du 2026-11-15 : report à P9 avec le reste du passage à l'échelle | **Signalé** ; l'exécution différentielle de P3 (200 séquences) reste la preuve de granularité à ce jour |
| Union de plages multiples (`[1,2),[3,4)`) rejetée | aucune occurrence dans les projets synthétiques ni dans les ponts prévus (P7 traduira `strictly`/`prefers` Gradle un à un) | Backlog |
| Authentification des dépôts privés (jetons) | aucune exigence du prompt à P4 ; le transport n'envoie aucun identifiant — état sûr par défaut | Backlog |
| Unions de chemins d'exclusions : la cible est réétendue seulement vers un ensemble d'exclusions **strictement plus petit** | la fermeture est l'union des chemins non exclus ; l'approximation documentée (ADR-033) est exacte pour les exclusions d'un niveau, conservative au-delà | Testée sur un niveau ; backlog pour un garde-fou supplémentaire |

## 5. Mesures de performance

Sandbox 2 cœurs partagés — indicatif ; le verdissement sur la machine
de référence est pour les phases concernées :

| Mesure | Cible | Obtenu |
|---|---|---|
| `:core-deps:test` complet (72 cas) | — | 4,8 s ; `ParallelCapTest` 2,9 s (délais volontaires du plafond : 8 × 120 ms), `HttpTransportTest` 1,3 s (keytool + serveur TLS local) |
| Résolution complète à froid (5 artefacts, 2 niveaux) | — | < 0,2 s par résolution (hors délais de transport factices) |
| Relecture chaude par lockfile (5 artefacts) | zéro requête | zéro requête, un hachage par artefact (`verifyInCache`) |
| `./gradlew check` complet | — | **278 tests, 0 échec** (206 en P3) |

## 6. Dette et risques ouverts

- **Câblage aval** : `ResolvedArtifact.file` et `aarContents` attendent
  leur consommateur (P5 : dex des `classes.jar`/`libs/*.jar`) ; la
  surface est livrée, le câblage vient avec `build-android`.
- Éviction du cache (quota d'espace) : avec la gestion de l'espace du
  daemon (P6), même travail que l'ADR-024.
- Classifieurs (`sources`, `javadoc`) : format prêt, aucune demande ne
  les produit (P7 en décidera via les ponts Gradle).
- `HttpTransport` contre un miroir public réel : hors harnais (la
  politique est prouvée localement) ; à exécuter sur la machine de
  référence.
- Coût de `verifyInCache` (un hachage par artefact à la relecture
  chaude) : à benchmarker sur un graphe de centaines de dépendances
  (P9) ; mémoïsable par signaux comme le VFS si le coût gêne.

## 7. Questions pour le propriétaire

1. Le gel des instantanés et sélecteurs dynamiques est attaché au
   lockfile (le supprimer re-résout au prochain passage) : convenez-
   vous qu'un `joide.lock` versionné dans les projets utilisateur est
   la pratique à documenter en P8 ?
2. L'authentification des dépôts privés (jetons personnels) est
   reportée (backlog) : faut-il la traiter en P7 avec l'import Gradle
   (qui lit les `repositories {}` des projets importés), ou plus tôt ?
3. L'exécution M/L du pipeline (question 1 du rapport P3, reportée à
   P9 par interprétation de votre validation) : confirmez-vous le
   report, ou souhaitez-vous un addendum avant la fin de P5 ?

## 8. ADR ajoutés

- **ADR-028** — parseurs XML et JSON maison : `org.w3c.dom` absent du
  niveau 28 ; tolérance symétrique au TOML de P1.
- **ADR-029** — ordre des versions : spécification maison documentée,
  20 cas de test ; inconnu au-dessus de `sp` ; fin de liste = 0
  (numérique) / publication (qualificatif).
- **ADR-030** — cache de dépendances partagé, adressé par contenu :
  relecture revérifiée, jamais un contenu faux servi.
- **ADR-031** — transport `HttpsURLConnection` : https seul, TLS
  vérifié jamais désactivé (preuve par rejet du certificat
  auto-signé), redirections sans rétrogradation.
- **ADR-032** — extraction AAR : zip-slip bloqué par politique pure
  (sept formes), plafonds d'entrées et de volume.
- **ADR-033** — lockfile `joide.lock` v1 : gel, zéro réseau,
  exclusions résolues dans le graphe ; deux phases dans le résolveur.

## 9. Reproduction

```bash
export JAVA_HOME=<jdk-21>
./gradlew check                 # 278 tests, 0 échec (dont 72 pour core-deps)
./gradlew :core-deps:test       # le détail de la phase
```

Aucun SDK Android n'est requis ; `keytool` (JDK) sert au test TLS du
transport.
