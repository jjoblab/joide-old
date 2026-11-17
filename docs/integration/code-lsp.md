# Contrat réel — code-lsp (serveurs de langage)

- **Dépôt** : `jjoblab/code-lsp`, lecture seule, épinglé à `657d787`
  (« ci: baseline parité re-signée »).
- **Version** : 1.15.0 (publiée le 2026-11-13 ; source unique
  `build.gradle.kts:445`).
- **Licence** : Apache-2.0 (`LICENSE` racine). `NOTICE` (7 items) :
  ecj 3.40.0 **EPL-2.0 modifié** (patcheur `:ecj-art`, rejouable par
  `./gradlew :ecj-art:patcherEcj`, description des modifications
  embarquée `META-INF/ECJ-ART-MODIFICATIONS.txt`) ; java-syntax
  JetBrains 0.3.381 (Apache-2.0 sources ; binaires
  intellij-dependencies sous « JetBrains Open-Source Build Terms ») ;
  lsp4j 0.21.2 EPL-2.0 ; ASM 9.7 BSD-3-Clause (build-time uniquement) ;
  grammaire Kotlin vendorée v2.3.0 Apache-2.0 (notices embarquées).
- **Langue** : écrit en Kotlin à 100 % ; artefacts Java 17.

## Constats du §6.8, revérifiés sur les sources

| Constat | Verdict |
|---|---|
| LSP Java, Kotlin (livré, non branché), XML | **périmé** : les trois moteurs sont EN SERVICE à 1.15.0 — Java (11 versions publiées), Kotlin (bascule ADR 0136 étape A exécutée le 03/11, 26 capacités annoncées et servies), XML (migration `:host`/`:xml` du 13/11/2026) ; le README racine est antérieur à ces migrations — le dépôt a raison |
| Socle `:index` (CIDX), `:model` (ProjetLsp v11), `:ecj-art` ; hôte `lsp-host` (AAR, minSdk 26) ; Kotlin | exact (lsp-host : minSdk 26, compileSdk 37.2) |

## Décision d'intégration (critique)

L'AAR hôte `jo.codeide.lsp.host:jo-codeide-lsp-host` expose dans son
API publique `jo.codeide.tooling.protocol.ClasspathResult` et dépend
de `com.github.jjoblab.codeide-tooling:protocol:1.2.0` (licence non
clarifiée — **interdit pour JoIDE**, §6.8 règle 2b) et de
`com.github.jjoblab.code-editor:cel-lsp:3.74.0` (l'ancien éditeur,
via JitPack).

**JoIDE ne consomme pas `:host`.** Il consomme les moteurs et le
socle, qui n'ont **aucune** dépendance vers codeide-tooling :

- `jo.codeide.lsp.java:jo-codeide-lsp-java:1.15.0` (moteur Java)
  + compagnon `jo-codeide-lsp-java-ecj-art:1.15.0` (jar ecj patché,
  EPL-2.0, classpath **runtime** uniquement) ;
- `jo.codeide.lsp.kotlin:jo-codeide-lsp-kotlin:1.15.0` (moteur Kotlin) ;
- `jo.codeide.lsp.xml:jo-codeide-lsp-xml:1.15.0` (moteur XML) ;
- `jo.codeide.lsp.model:jo-codeide-lsp-model:1.15.0` (contrat
  `ProjetLsp` v11, empreintes, convertisseur) ;
- `jo.codeide.lsp.index:jo-codeide-lsp-index:1.15.0` (index disque
  CIDX).

La clarification de codeide-tooling (§6.8 règle 2b) devient **inutile
pour JoIDE** : le socle `:model` embarque sa propre réécriture
clean-room du protocole (`jo.codeide.lsp.model.protocole`), et
l'adaptateur JoIDE traduit `core-model` → `ProjetLsp` directement.

## Le contrat `ProjetLsp` v11 (`socle/model`)

Fichier : `socle/model/src/main/kotlin/jo/codeide/lsp/model/ProjetLsp.kt`.

- `ProjetLsp(cheminRacine, modules triés par nom, empreinte)` :
  snapshot **immuable** ; le remplacement se fait par publication d'un
  NOUVEAU `ProjetLsp` (`DepotModeleLsp.instantane: StateFlow`),
  jamais par mutation.
- `ModuleLsp` : `nom` (chemin Gradle plat `:a:b` — identifiant
  canonique), `type` (`ANDROID`/`JVM`), `variante`, `sources`
  (racines par rôle : principal/test/générés), `classpath`
  (`ClasspathInstantane` : entrées ordonnées, dédupliquées,
  empreintées), `dependancesModules`, `androidJar` (null pour JVM),
  `ignore` + `raisonIgnore` (**jamais ignoré en silence**),
  `avertissements`, `compatibiliteSource`/`compatibiliteCible`
  (niveaux Java normalisés), `android` (`ModuleAndroidLsp` v11 :
  namespace, minSdk/targetSdk/compileSdk, dossiersRes, manifest,
  buildFeatures…), `empreinte` (SHA-256).
- Empreintes : par entrée selon la provenance (maven/transform/partiel/
  dossier/module) ; empreinte du module = SHA-256 d'une chaîne composite
  (classpath, racine, androidjar, variante, niveaux, android) — changer
  une dépendance change l'empreinte du SEUL module concerné ; empreinte
  du projet = SHA-256 des modules.
- `AssociationFichier.associer(projet, chemin)` : route fichier →
  module (racine de source la plus profonde).

## Les moteurs (points d'entrée publics)

- **Java** : `ServeurLangageJava(environnement: EnvironnementCompilation,
  rappelArret)` — « l'hôte nourrit l'environnement, le serveur route ».
  L'hôte pose `environnement.remplacerContexteModule(nom, racinesSources
  + frères, jars + androidJar, compatibiliteSource)` et
  `environnement.routeurFichierModule` (relu à chaque requête) ;
  remplacement effectif par double-tampon + époques (aucune réponse
  périmée : rejouée) ; ordonnanceur pool 2 workers (1..3), 3 voies
  priorisées (frappe/navigation/confort), exclusion par fichier ;
  diagnostics publiés asynchrones, débouncés 50 ms par URI.
- **Kotlin** : SPI `ServiceMoteurLangageKotlin` (`creerSession()`,
  `definirClasspath(classpath)`) — session moteur née à `initialize`,
  nourrie du classpath courant à chaque sync.
- **XML** : `ServiceMoteurLangageXml.unique()` + `VueNomsXml`.

## L'index CIDX (`socle/index`)

`ServiceIndexDisque(repertoire, scope, dispatcheurIo, parallelisme,
budgetCacheOctets)` — **budget mémoire dur configurable** (défaut
30 Mo, `max(30 Mo, 512 Kio/jar)` en auto) ; vues immédiatement
interrogables ; construction en arrière-plan annulable (états honnêtes
INITIAL/CONSTRUCTION/PRET/PARTIEL/ERREUR) ; auto-guérison d'un segment
corrompu (crc) ; écriture atomique `.tmp` ; stockage **partagé entre
projets**, mutualisé par empreinte de contenu. Règle du §6.8.5 :
**réutiliser CIDX avant d'écrire un index** (aucun index JoIDE tant
qu'aucune mesure ne montre que CIDX ne tient pas le budget à l'échelle
XL).

## Licences à vérifier par le propriétaire

- java-syntax 0.3.381 (binaires intellij-dependencies, « JetBrains
  Open-Source Build Terms ») : redistribution par JoIDE du moteur Java
  à confirmer — voir [DEMANDES.md](DEMANDES.md) D-3. (P-K2 du
  31/10/2026 a acté Apache-2.0 pour syntax-api/lang-syntax ;
  java-syntax reste sous Build Terms.)
- ecj 3.40.0 EPL-2.0 : obligations satisfaites **par la brique**
  (modifications décrites, rejouables, sources amont Maven Central) ;
  JoIDE redistribue le compagnon tel quel et le consigne dans son
  `NOTICE`.
- lsp4j 0.21.2 EPL-2.0 : consommée comme bibliothèque, consignée dans
  le `NOTICE` de JoIDE.

## Mode de consommation dans JoIDE

Artefacts `jo.codeide.lsp.*:1.15.0` construits depuis le checkout
épinglé `657d787` (publication Maven Local), **sans `:host`** ; le
compagnon ecj-art reste au classpath runtime uniquement (séparation
de licences Apache-2.0/EPL-2.0, comme dans la brique). Aucune
modification de la brique ; tout écart passe par
[DEMANDES.md](DEMANDES.md).
