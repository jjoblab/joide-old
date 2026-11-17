# Contrat réel — celeris (éditeur)

- **Dépôt** : `jjoblab/celeris`, lecture seule, épinglé à `f9001fc`
  (« le vert des quatorze modules se referme », 2026-11-14).
- **Version** : 1.1.0 (tag `v1.1.0` du 2026-11-12 ; la 1.2.0 est en
  chantier — non consommée).
- **Licence** : Apache-2.0 (`LICENSE` à la racine, 203 lignes).
  `NOTICE` (166 lignes) : dépendances runtime des artefacts publiés =
  joni 2.2.9 + jcodings 1.0.64 (MIT, `core-syntax-grammars`), lsp4j
  1.0.0 + jsonrpc 1.0.0 (EPL-2.0 OU BSD-3-Clause) et Gson 2.10.1
  (Apache-2.0) dans `lsp-protocole` et `serveur-lsp-java` ; outillage
  de build non embarqué ; grammaires TextMate MIT et police Fira Code
  (OFL 1.1) hors artefacts (test/démo).
- **Plateforme** : artefacts Java 17 construits sur JDK 21 ; rendu
  Canvas ; modules Android minSdk 26, compileSdk 36.

## Constats du §6.8, revérifiés sur les sources

| Constat | Verdict |
|---|---|
| Apache-2.0 ; Java 17 (JDK 21) ; rendu Canvas | exact |
| Modules purs et Android ; SPI android-host ; minSdk 26 | exact — le constat omettait `serveur-lsp-java` : le dépôt compte **14 modules**, 12 publiés (9 JAR + 3 AAR) ; `cli` et `demo` sont des applications, jamais publiées |
| Publication `publishToMavenLocal publicationChecksums` | exact : artefacts `jo.celeris:<module>:1.1.0` (JAR purs, AAR Android), avec sources, Javadoc, SBOM CycloneDX et sommes SHA-256 |

## Artefacts consommés par JoIDE

`jo.celeris:1.1.0` : `core-text`, `core-syntax`, `core-language`,
`core-lsp`, `lsp-protocole` (JAR) ; `android-view`, `android-widgets`,
`android-host` (AAR). La construction des AAR exige le SDK Android
**chez celeris** (inclusion conditionnelle : `CELERIS_SKIP_ANDROID=1`
> `ANDROID_HOME` > `ANDROID_SDK_ROOT` > `local.properties`) ; JoIDE
consomme des binaires déjà construits.

## API publique utilisée par JoIDE (P8)

### Le SPI que JoIDE implémente (`jo.celeris.android.host.spi`)

| Interface | Méthodes (signature exacte) |
|---|---|
| `SystemeFichiersVirtuel` | `FichierVirtuel ouvrir(CheminDoc)` lève `FichierAbsentException` ; `void ecrire(CheminDoc, String)` ; `List<CheminDoc> lister(CheminDoc)` ; `boolean estFichier(CheminDoc)` ; `default boolean estBinaire(CheminDoc)` ; `default String apercuBinaire(CheminDoc)` |
| `IntegrationVcs` | `EtatLignesVcs etatLignes(CheminDoc)` ; `List<AnnotationBlame> blame(CheminDoc)` ; `List<RevisionVcs> historique(CheminDoc)` — synchrone, sans état côté éditeur |
| `IntegrationExecution` | `Lancement lancer(CheminDoc, boolean test)` ; `EtatExecution etat(CheminDoc)` ; `List<CheminDoc> etats()` ; `void ajouterAuditeur(AuditeurExecution)` |
| `IntegrationDebogueur` | `poserArret/retirerArret/basculerArret(CheminDoc, int)` ; `List<Arret> arrets()` ; `LigneArret ligneCourante()` ; `List<CadrePile> pileCourante()` ; `String evaluerAuSurvol(CheminDoc, int, int, String)` ; `List<ValeurEnLigne> valeursEnLigne(int)` |
| `ArbreDocuments` | `List<EntreeDocument> enfants(String idDossier)` ; `String lire(String idFichier)` ; `byte[] lireOctets(String)` ; `void remplacer(String, String)` ; `String creer(String idParent, String nom, boolean dossier)` ; `boolean estFichier(String)` |
| `CoachPopups` (étage popups) | `List<String> motsReserves()` ; `List<Candidat> candidats(List<String> bruts)` ; `CompletionDocModel docDe(String)` ; `SignatureAideModel signatureDe(Document, int)` ; `static CoachPopups muet()` |
| `LanguageSession` (`AutoCloseable`) | ouvrir/synchroniser/signature/survol/completion, diagnostics, couverture (plis, formatage, surlignages, renommage, correctifs, indices, jetons, références), `close()` |

Adaptateurs de production fournis par la brique (utilisables tels
quels) : `ArbreDocumentsSaf(Context, Uri)` (DocumentsContract),
`AdaptateurStockageDocuments(ArbreDocuments, String idRacine)`,
`HistoriqueLocal(SystemeFichiersVirtuel, LongSupplier)`. Faux de banc :
`MemoireSystemeFichiers`, `FauxVcs`, `FauxExecution`, `FauxDebogueur`.

L'éditeur consomme le SPI par **injection explicite** (jamais
ServiceLoader) : le sens des dépendances est `android-host` →
`android-view` ; la vue ne référence jamais `jo.celeris.android.host.spi`.

### La vue (`jo.celeris.android.view.EditorView`)

`EditorView(Context)` ; `setSession(TextSession)` (« la vue ne crée
jamais la session ») ; `setLanguage(LanguageSupport)` ;
`setTheme(EditorTheme)` ; `setFont(FontSpec)` ; `reglages()` ;
`intelligence()` ; `marquesGouttiere()` (dont
`setIntegration(MarquesIntegration)` pour les marques VCS/débogueur) ;
`setValeursEnLigne` / `setIndicesInlay` / `setFantome` ; retention
d'analyse réglable (`setRetenueAnalyseMs`, 250 ms par défaut).

### Le modèle de texte (`jo.celeris.core.text`)

`TextSession.of(String, LongSupplier)` ; `Document` immuable
(`replace` rend un nouveau document) ; `Transaction` /
`TextEdit` ; `MultiSelection` ; pile d'annulation. Toute mutation
passe par la transaction unique (ADR-003 celeris).

### La frontière LSP (`jo.celeris.lsp.protocole`)

- `LspSession.sur(InputStream, OutputStream, String nomClient)` :
  `initialiser()` (borné 30 s), correspondances documents
  (`ouvrir/changer/fermer`), requêtes en `CompletableFuture`
  ordonnancées et annulables (`$/cancelRequest`), diagnostics
  (`diagnosticsDe`, `ajouterAuditeurDiagnostics`), `close()` (jamais
  `exit`).
- `ServeurEmbarque.monter(Documents, String)` (montage en processus,
  tubes en mémoire, `TubesMemoire.creer()`) ; `session()` ; `close()`.
- Aucun type de la bibliothèque (lsp4j) ne paraît dans l'API du module.

## Threading et cycle de vie imposés à l'hôte

- Écrivain unique sur le fil UI ; lecteurs sur instantanés immuables
  (ADR-007 celeris). Aucun verrou interne ; pas de `static` mutable.
- L'analyse vit hors fil UI (fil démon « celeris-analyse » ou
  exécuteur injecté) ; **ne jamais bloquer le fil UI** : les contrats
  rendent des `CompletableFuture`.
- Exécuteurs et horloge injectés par l'hôte ; piscines du protocole en
  fils démons.
- Fermer ce qu'on monte : `LspSession`, `ServeurEmbarque`,
  `TubesMemoire.Direction` sont `AutoCloseable`.
- Positions LSP = offsets UTF-16.

## Référence vivante

La démo (`demo/src/main/java/jo/celeris/demo/`, non publiée) est
l'implémentation de référence du SPI : `EditorActivity` (montage
`TextSession` + `PopupsEditeur` + `AdaptateurMarques`),
`ServeurLangage.demarrer()` (`ServeurEmbarque.monter(new
ServeurReference, "celeris-demo")`), `DeclaredServers` (serveurs
déclarés, repli). JoIDE s'en sert comme gabarit, sans la dépendre.

## Mode de consommation dans JoIDE

Dépendance binaire `jo.celeris:*:1.1.0` depuis le checkout épinglé
`f9001fc` (publication Maven Local avec sommes de contrôle,
`publicationChecksums`). Aucune modification de la brique ; tout
écart passe par [DEMANDES.md](DEMANDES.md).
