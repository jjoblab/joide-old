# PHASE A8 — RAPPORT (cycle de vie et configuration)

- **Date** : 2026-11-19
- **Base** : A7 validée (`170a311`), arbre de travail **portant un travail
  A8 NON committé** à la prise de session (circonstance au §0).
- **Lot** : A8 — **décision 10 de l'ADR-085** : le changement de
  configuration devient **non destructeur**. `configChanges` de
  **géométrie seule** (`orientation|screenSize|smallestScreenSize|
  screenLayout`) sur `EcranAtelier` ; `onConfigurationChanged` ferme
  les popups, **ré-inflamme la racine seule** (compact ↔
  `layout-w720dp`) **sans `fermerIde()`** — le puits (scène celeris)
  se détache avant, se rattache après, jamais reconstruit ; l'état
  d'UI minimal survit aux recréations RESTANTES (thème, langue,
  taille de police) par `onSaveInstanceState` (export pur de
  l'`EtatAtelier` + brouillons de `EtatPopups` + assistant
  `EtatCreation`) ; la décision vit dans une classe **pure JVM**
  (`DecisionConfiguration`, testée) ; la garde `isDestroyed()`/`
  isFinishing()` couvre **tous** les callbacks `runOnUiThread` du
  module (revue I8) et l'exécuteur de l'atelier.

## 0. Circonstance de session (à connaître du propriétaire)

Le brief exigeait la base `170a311` (lot A7) et un arbre propre ; la
prise de session a trouvé la base **conforme** mais l'arbre portant
**22 fichiers non committés** (20 modifiés + 2 nouveaux), précisément
circonscrits au périmètre du lot (le manifest, la coque et la vue
d'écran de l'atelier, les états purs et leurs tests, l'assistant de
création, les gardes I8 des quatre autres écrans, l'amendement ADR) —
un agent précédent a épuisé ses tours **avant validation et commit**.

L'audit (le brief l'exige : lecture intégrale face au présent ordre,
critères d'acceptation d'abord, compilation tôt — la compilation
passait dès la prise de session, preuve que le prédécesseur avait
compilé) a couvert : la lecture ligne à ligne de `DecisionConfiguration`
et de ses 4 tests (la grille des classes, le critère §6.10 asserté
partout) ; l'attribut du manifest face à la liste EXACTE du §5 et le
filet `ArchitectureVuesTest` qui le verrouille ; la **ré-inflation de
la racine** de `VueAtelier` vérifiée membre à membre face aux DEUX
layouts (guards `colonneArbre`/`tiroir`/`tiroirFichiers`/
`boutonRailConsole`/`voletDroit` nuls selon la variante, ré-amarrage de
l'arbre au conteneur de la disposition, de la console dans la feuille
neuve, du tiroir agent au RANG du stub neuf, re-câblage complet par
`cabler()`, re-pose par `majOnglets`/`majVolets`/`majFab`/
`majConsole`/`reposerFeuille` — toutes les références de binding
confrontées aux deux variantes de `activity_atelier.xml`) ; les
exports/imports de `EtatAtelier`, `EtatPopups`, `EtatCreation` et
leurs tests d'aller-retour ; les racines `NestedScrollView` de
l'assistant et leurs ids portés par les `<include>` du ViewFlipper ;
la revue **un par un** des 14 sites `runOnUiThread` du module ; puis
la validation re-exécutée par mes propres lancés (`--rerun` forcé sur
les tests, `assembleDebug`, `verifierOmbreApk`, `lintDebug` — les
tâches étaient UP-TO-DATE à la prise de session : les exécutions du
prédécesseur n'étaient ni prouvées ni documentées).

**Corrections portées par l'audit** (le travail hérité était
structurellement complet et compilait ; trois fautes de robustesse
sans test les couvrant) :
- `EcranCreation.depuis(Bundle)` : l'ordinal d'étape et le nom de
  catégorie lus du Bundle se **bornent** désormais (un Bundle écrit
  par une AUTRE version de l'application — étape réordonnée, catégorie
  renommée — faisait planter le réveil : `values()[ordinal]` hors
  bornes, `Categorie.valueOf` inconnu ; l'état dégradé vaut mieux que
  l'écran mort) ;
- `VueAtelier.restaurer(Bundle)` : idem pour l'ordinal de section de
  feuille (borné à CONSOLE) ;
- `VueAtelier.restaurer` : `poserSection` **déplie** à mi-hauteur une
  feuille repliée (patron « le choix déplie », lot A6) — la détente
  exportée se re-pose aussitôt (`poserDeplie(consoleDepliee)`) : une
  feuille repliée avant recréation reste repliée après, au lieu de
  se rouvrir en flash jusqu'au premier `majVolets` du chargement.

Compléments posés par l'agent de reprise : ce rapport, l'entrée
`CHANGES.md`, le commit (l'amendement ADR « Évaluations du lot A8 »
était déjà posé par le prédécesseur et vérifié exact).

## 1. Constats du prompt — vérifications A8

- **C3 (cycle de vie destructeur) CONFIRMÉ puis RÉSOLU**. À la base :
  `grandEcran` lu UNE fois à la création, aucune politique
  `configChanges` (grep : 0), aucun `onSaveInstanceState` (grep : 0
  hors le nôtre), toute rotation/pliage/multi-fenêtre recréait →
  `onDestroy` → `fermerIde()` (scène, aperçu, moteurs LSP), l'état
  d'onglets perdu, les popups perdus. Après le lot :
  - le manifest déclare la **géométrie seule** sur `EcranAtelier`
    (`orientation|screenSize|smallestScreenSize|screenLayout`) — et
    RIEN d'autre : thème, langue, densité et police restent des
    recréations (le filet `ArchitectureVuesTest` verrouille la liste
    EXACTE et que **seul** l'atelier déclare : compter ==
    `android:configChanges=` vaut 1) ;
  - `onConfigurationChanged` déroule la décision PURE
    `DecisionConfiguration.surGeometrie(avant, après)` : les popups se
    ferment TOUJOURS (avant tout — jamais une carte vivante sur une
    géométrie dépassée), la racine se ré-influe SEULEMENT si la
    CLASSE change (compact ↔ grand — **économie documentée** : même
    classe, même layout, un re-câblage entier ne dirait rien de
    plus), l'IDE ne se ferme JAMAIS — **le champ `fermerIde` existe
    dans la réaction pour que le test JVM l'asserte faux dans toute
    la grille** (critère §6.10) ;
  - la ré-inflation détache le puits (les enfants de `vue_editeur` :
    la vue de la scène + sa couche de popups) AVANT, gonfle la racine
    neuve (`ActivityAtelierBinding.inflate` suit la configuration déjà
    commutée), `setContentView`, rattache la scène au puits neuf,
    ré-amarre les zones montées (arbre au conteneur de la disposition
    — colonne amarrée en grand écran, cadre du tiroir en compact ;
    console dans la feuille neuve ; tiroir agent au RANG du stub
    neuf, mêmes LayoutParams), re-pose le voile du porte-popups
    (`PortePopups.poserVoile` — le registre survit, vide : les popups
    se sont fermés avant), re-câble (`cabler()` : auditeurs, insets
    `WindowInsetsCompat`, fond calculé de la bannière) et re-pose
    l'état (`reposerEtatSurNouvelleRacine` : nom de projet et puce
    modules, état vide du puits, tiroir compact, couleur du rail,
    onglets et fil d'Ariane, pilule d'index, arbre, volets et FAB,
    console et feuille — `ControleurEdition.reposerFeuille` re-déroule
    problèmes/journal/logcat de leurs états purs). **`SessionAtelier`
    n'est pas touché** : moteurs LSP, sync, aperçu et écritures
    fusionnées survivent — la scène n'est jamais reconstruite.
- **I8 (concurrence UI, « garde `isDestroyed()`/vues nulles après
  `onDestroy` à vérifier ») CONFIRMÉ puis RÉSOLU par la revue exigée** :
  le module porte **14 sites d'appel** `runOnUiThread` (15 occurrences
  grep, dont 1 mention en javadoc) — `MainActivity` 5,
  `EcranParametres` 6, `EcranCreation` 1, `EcranCallbackMcp` 1,
  `VueAtelier` 1. **Chacun est gardé** (lu un par un) : les 5 de
  l'accueil, 1 du rappel MCP (le contrôle `isFinishing` du fil du
  service ne suffisait pas — l'écran peut fermer ENTRE le contrôle et
  l'exécution) et 1 des paramètres (la relance de mesure) ont reçu
  `isDestroyed()` au lot A8 ; les 5 autres des paramètres, celui de
  la création (`isDestroyed() || ecran == null`, binding MUET après
  `onDestroy`) et les 4 gardes d'`EcranCreation` d'A4 étaient déjà
  posés. L'atelier concentre ses callbacks asynchrones (exécuteur de
  fond, relais logcat, démon de build, service de l'agent, session)
  dans **`HoteAtelier.executerSurFilUi`**, qui pose la garde UNE fois
  — avant le post ET au moment d'exécuter (`isFinishing() ||
  isDestroyed()`) : l'écran détruit n'exécute plus rien, jamais une
  vue morte touchée, jamais un popup montré sur une fenêtre
  disparue.
- **« A3 : rotation de l'accueil = position suit le RecyclerView, pas
  encore de sauvegarde explicite »** : CONFIRMÉ couvert —
  `liste_accueil` (RecyclerView) et
  `defilement_parametres` (NestedScrollView) portent leurs `@+id` : le
  cadre restaure le défilement seul ; aucun état d'UI propre à
  sauvegarder n'existe sur ces écrans (la vérité = projets récents /
  `Reglages`, relus au `onCreate`).
- **« A4 : saveEnabled=false posé sur certains champs ; A8 fera la
  sauvegarde »** : FAIT, la sauvegarde par l'ÉTAT PUR — et
  `saveEnabled=false` **RESTE** (écart motivé au §2) : la vérité est
  unique (`EtatCreation` exporté/importé, `Reglages` re-posé), la
  restauration du cadre écrirait un deuxième avis (et marquerait le
  paquet « touché » sans motif).

## 2. Écarts avec le prompt (et choix tranchés)

- **ε (`ViewModel`) ÉVALUÉE puis ÉCARTÉE** (le brief demandait
  l'évaluation motivée ; consigné à l'ADR-085 « Évaluations du lot
  A8 ») : β couvre la géométrie sans recréation, l'état minimal
  couvre les recréations restantes — un `ViewModel` n'apporterait
  rien que le Bundle ne donne déjà (l'export est minuscule, sans
  commune mesure avec le plafond transactionnel). Y DÉMÉNAGER la
  session d'IDE est exclu : `SceneEditeur`/`ApercuLayouts` sont
  construits sur le `Context` de l'activité (thème, ressources,
  fenêtre) — les y retenir violerait « jamais de vue ni de `Context`
  d'activité retenus » et exigerait un protocole de re-attach au
  moins aussi lourd que la ré-inflation de β. **Aucun changement de
  propriété de la session n'a lieu : rien à consigner de plus.** La
  clause « À revisiter » (γ/ε si le coût de rechargement mesuré reste
  pénalisant) demeure ; `androidx.lifecycle` n'est PAS importé (aucune
  dépendance nouvelle).
- **`saveEnabled=false` NON levés** (le brief : « y compris la levée
  des saveEnabled=false d'A3/A4 **s'ils bloquent la restauration** ») :
  ils ne bloquent RIEN — la saisie se restaure depuis l'état pur
  (l'aller-retour est testé), et la restauration du cadre écraserait
  un deuxième avis sur une vérité déjà re-posée (paquet « touché »
  sans motif, coche de catégorie en double avec l'état). Le
  DÉFILEMENT, lui, se restaure par le cadre : les racines d'étape de
  l'assistant deviennent des `NestedScrollView` (le `ScrollView` du
  cadre ne sauve pas sa position), id porté par les `<include>` du
  ViewFlipper. Consigné à l'ADR.
- **Les popups se ferment même SANS ré-inflation** (même classe de
  géométrie) : la décision 12 dit « fermeture en
  `onConfigurationChanged` », TOUT changement de configuration —
  l'économie porte sur la racine seule, jamais sur les fenêtres
  ouvertes (une carte ancrée vivrait sur une géométrie dépassée).
  Documenté (classe + ADR).
- **L'état du BUILD ne s'exporte pas** : la pilule du FAB suit les
  événements du service (`ControleurBuild`), pas un état d'UI — après
  une recréation restante en plein build, la pilule reprendra à
  l'événement suivant. Résiduel documenté (§6), cohérent avec « la
  mort du service est un événement de flux » (§005).
- **L'accueil et les paramètres n'ont pas d'`onSaveInstanceState`
  dédié** : rien d'exportable n'y vit qui ne soit déjà relu au
  `onCreate` (projets récents, `Reglages`) ; le défilement se
  restaure par le cadre (ids §1). L'atelier et l'assistant, seuls
  porteurs d'état d'UI, l'ont.
- **Les voies d'erreur de l'atelier (`racine` absente, bannière de
  compatibilité) ne restaurent pas l'état minimal** (retour anticipé
  avant `demarrerSession`) : il n'y a alors AUCUNE session dont
  reprendre les onglets — l'état exporté serait un mensonge.
  Documenté ici (pas un écart : la décision 10 ne parle que de
  l'atelier monté).

## 3. Changements

| Fichier | Nature |
|---|---|
| `atelier/DecisionConfiguration.java` (nouveau, 72 l.) | LA décision pure (décision 10) : donnée la géométrie avant/après, la `Reaction` — ré-infler la racine (si la classe change), fermer les popups (toujours), fermer l'IDE (JAMAIS — le champ existe pour que le test l'asserte) |
| `AndroidManifest.xml` | `android:configChanges="orientation\|screenSize\|smallestScreenSize\|screenLayout"` sur `EcranAtelier` SEUL (+ le commentaire de motif) — rien d'autre ne déclare |
| `ecrans/atelier/EcranAtelier.java` | 194 → 230 l. : `onConfigurationChanged` délègue à la vue (la décision pure décide) ; `onSaveInstanceState` exporte l'état minimal ; le `onCreate` restaure AVANT `demarrerSession` (l'ACTIF restauré devient le fichier initial : rebranchement, pas reconstruction, ADR-É5) |
| `ecrans/atelier/VueAtelier.java` | 1 443 → 1 782 l. : `surChangementConfiguration` (fermeture des popups, décision pure, ré-inflation si la classe change), `reinflerRacine` (détacher/regonfler/rattacher/re-câbler/re-poser — le puits jamais reconstruit, les zones ré-amarrées), `reposerEtatSurNouvelleRacine`, `sauverEtat`/`restaurer` (le Bundle traduit l'`EtatAtelier` + les brouillons ; bornage d'ordinal et re-pose de détente par l'audit), l'UI volatile re-posable (bannière, état vide) ; `executerSurFilUi` porte la garde de cycle de vie UNE fois |
| `ecrans/atelier/ControleurEdition.java` | + `reposerFeuille()` : re-pose problèmes/journal/logcat et leurs verdicts sur la feuille NEUVE depuis les états purs (aucun événement ne re-arrive pour le faire) |
| `atelier/EtatAtelier.java` | + `Export` (record pur Bundle-compatible : onglets, actif, points de modification, surcouches, section de feuille), `exporter`/`importer` (aller-retour testé ; l'amaigri se normalise), `poserGrandEcran` (la géométrie se RE-POSE, ne s'importe pas) ; `grandEcran` cesse d'être final |
| `atelier/EtatPopups.java` | + `exporterBrouillons`/`importerBrouillons` (le même passage que l'`EtatAtelier` : la saisie survit, le popup n'est jamais restauré) |
| `creation/EtatCreation.java` | + `Export` + `exporter`/`importer` (A4 : « A8 fera la sauvegarde » — l'assistant reprend EXACTEMENT où il était ; normalisations testées) |
| `ecrans/creation/EcranCreation.java` | 746 → 842 l. : `onSaveInstanceState`/`depuis(Bundle)` (traduction coque, bornage d'audit), import AVANT le montage, la recherche restaurée se re-pose en silence (`poseEnCours`) |
| `res/layout/creation_etape_{modele,identite,recapitulatif}.xml` | les racines deviennent `NestedScrollView` (le cadre restaure le DÉFILEMENT ; id porté par l'`<include>` ; les commentaires consignent le choix `saveEnabled=false`) |
| `MainActivity.java`, `ecrans/mcp/EcranCallbackMcp.java`, `ecrans/parametres/EcranParametres.java` | les gardes `isDestroyed()` des callbacks `runOnUiThread` non gardés (5 accueil, 1 rappel MCP, 1 relance de mesure) |
| `ui/PortePopups.java` | + `poserVoile(View)` : la vue-voile se re-lier à la racine ré-inflée (le registre survit, vide) |
| `docs/adr/085-architecture-vues-ecrans.md` | décision 10 précisée (posée au lot A8, détacher AVANT/rattacher APRÈS) ; option β « POSÉE » ; ε ÉCARTÉE (motivée) ; section « Évaluations du lot A8 » : la liste exacte du manifest, la classe pure, l'économie, la ré-inflation, l'état minimal, `saveEnabled=false` |
| `atelier/DecisionConfigurationTest.java` (nouveau) | 4 tests JVM : la grille des classes (même classe = pas de ré-inflation, popups fermés quand même ; classe changée = ré-inflation), le critère §6.10 (`fermerIde` faux) asserté dans TOUTE la grille |
| `atelier/EtatAtelierTest.java` | +3 : l'aller-retour export→import→export ÉGAL (onglets et ordre, actif, points, surcouches), l'import amaigri, la géométrie se re-pose (ne s'importe pas, l'état d'UI reste) |
| `atelier/EtatPopupsTest.java` | +2 : l'aller-retour des brouillons (l'ordre de frappe compris), l'import amaigri/nul |
| `creation/EtatCreationTest.java` | +2 : l'aller-retour de l'assistant (étape, recherche, catégorie, modèle, identité — `continuerPossible` reste actif), l'import amaigri (minSdk hors bornes écarté) |
| `ArchitectureVuesTest.java` | +1 : le manifest porte les changements de GÉOMÉTRIE sur l'atelier SEUL, liste EXACTE verrouillée, compte `android:configChanges=` = 1 |

## 4. Mesures avant/après (Annexe B, re-mesurées par l'agent)

| Mesure | A7 (`170a311`) | A8 |
|---|---|---|
| `onSaveInstanceState` (module, coques) | 0 | **3** (atelier, assistant — les deux seuls porteurs d'état d'UI) |
| `configChanges` au manifest | 0 | **1** (l'atelier, géométrie seule — filet) |
| `runOnUiThread` (occurrences grep) | 15 (14 sites, 7 non gardés) | **15 (14 sites, 14 gardés)** — la revue exigée |
| Sites non gardés | 7 (accueil ×5, MCP ×1, paramètres ×1) | **0** |
| Tests JVM `:app-android:test` | 371 verts | **383 verts, 0 échec** (+4 décision, +3 atelier, +2 brouillons, +2 assistant, +1 manifeste) |
| Vues instanciées en code (module) | 22 (kit `ui/` seul) | **22 inchangé** (0 dans les écrans) |
| `palette.*` / `GraphiteVue.dp(` | 205 / 152 | **209 / 152** (+4 : les re-poses de la ré-inflation — bannière, rail) |
| Layouts | 87 + 1 `w720dp` | **87 + 1 `w720dp` inchangés** |
| dp / sp littéraux | 283 / 54 | **283 / 54 inchangés** |
| `<merge>`/`<include>`/`<ViewStub>` | 9 / 45 / 6 | **9 / 45 / 6** inchangés (le grep brut dit 48 includes : 3 mentions dans les COMMENTES du lot) |
| Dialogues (grep Annexe B, main) | 0 | **0** |
| Compose | 0 | **0** |
| Lignes | `EcranAtelier` 194, `VueAtelier` 1 443, `EcranCreation` 746, `MainActivity` 531, `EcranParametres` 1 102, `EcranCallbackMcp` 124 | **230 / 1 782 / 842 / 560 / 1 110 / 130** (`EcranAtelier` reste < 500) |
| APK debug (R8 + shrink) | 15 844 349 o | **15 853 241 o (+8 892 o)** — aucune dépendance nouvelle : une classe pure, les exports, les gardes |

## 5. Validation

Reprise d'audit : les exécutions ci-dessous sont LES MIENNES, sur
l'état final (corrections §0 comprises) ; les tests ont été forcés
(`--rerun`), l'empaquetage de l'APK forcé. Le démon Gradle n'est PAS
mort (leçons A4-A7 armées :
`GRADLE_OPTS=-Dorg.gradle.internal.repository.max.threads=2`).

- `./gradlew --init-script … :app-android:testDebugUnitTest --rerun`
  — **383 tests, 0 échec, 0 erreur, 0 ignoré** (52 fichiers de
  résultats ; dont `DecisionConfigurationTest` 4, `EtatAtelierTest`
  19, `EtatPopupsTest` 8, `EtatCreationTest` 11,
  `ArchitectureVuesTest` 9 — l'ordre du retour système INCHANGÉ
  reste testé) ; puis `:app-android:test` — vert.
- `./gradlew … :app-android:assembleDebug :app-android:verifierOmbreApk
  --rerun` — **vert** : « APK sans ombre, éléments vitaux présents
  (15853241 o) ».
- `./gradlew … :app-android:lintDebug` — **11 erreurs, 135
  avertissements** : les 11 d'A7 EXACTEMENT (8 `NewApi` préexistantes :
  `Path#of` ×3 (`ControleurFichiers`), `toList` (`EtatProblemes`),
  `readString` (`PreparationChaine`), `readAllBytes` ×2
  (`ProprietesConstruction`), `transferTo` (`SpecsBuild`) ; 3
  `DiffUtilEquals` faux positifs connus A2/A5) — **aucune NOUVELLE
  erreur** ; aucun avertissement NOUVEAU ne pointe un fichier du lot
  (le `SetTextI18n` `EcranCreation:567` est le patron d'hier déplacé
  de 16 lignes ; le `RtlHardcoded` `creation_etape_identite:192`
  préexistait à la base — lignes vérifiées par `git show`) ; les
  `DuplicateIncludedIds` ×14 restent TOUS dans
  `activity_parametres.xml`, lot A3 préexistant.
- Parité `values/` ↔ `values-fr/` par script : **391 = 391 clés**, 0
  seulement-d'un-côté — aucune chaîne nouvelle n'a été nécessaire.
- `./gradlew check` racine NON VERT — inchangé de la base
  (`checkLanguagePolicy` : 1 313 problèmes typographiques
  préexistants, preuve archivée au rapport A1) ; le lot n'ajoute
  rien.
- **Aucun appareil/émulateur** : les comportements de géométrie ne
  peuvent pas être PROUVÉS ici — voir §6.

## 6. Risques résiduels et suite

- **À vérifier sur appareil par le propriétaire** (sans émulateur, la
  ré-inflation ne peut être PROUVÉE) : la **rotation** et le
  **pliage** (compact ↔ grand écran) — la session survit (pas de
  rechargement du projet), la scène garde son texte et son curseur,
  la feuille/sa section, le tiroir, les onglets reviennent, les
  popups se ferment ; la **multi-fenêtre** (le redimensionnement à
  classe constante ne ré-influe pas — économie : le cadre re-agence
  seul, les popups se ferment quand même) ; le **changement de
  thème/langue/taille de police** (recréation : onglets, actif,
  surcouches, section et brouillons reviennent — l'actif se
  re-monte par rebranchement celeris ; le curseur précis et le
  défilement de l'éditeur ne sont pas exportés — état faible
  ADR-É5) ; la **mort du processus en arrière-plan** (même Bundle).
- **Résiduels acceptés et documentés** : l'état du build ne s'exporte
  pas (la pilule suit le service — §2) ; le point d'activité de
  l'agent et la pilule d'index ne se re-posent pas à la
  ré-inflation (ils suivent le prochain événement du service/
  `majPiluleIndex` de la re-pose) ; le curseur/défilement de l'éditeur
  ne survivent ni à la ré-inflation ni à la recréation (état faible
  par choix, ADR-É5 — le texte vit sur le disque) ; les voies d'erreur
  de l'atelier ne restaurent pas (§2).
- La ré-inflation de la racine est la pièce la plus neuve du chantier
  : elle n'est éprouvée que par la lecture et le filet (aucun test
  Android n'existe dans le dépôt) — la vérification sur appareil des
  points ci-dessus est LA sortie attendue du lot.
- Suite : **A9** — consolidation (une seule voie de thémage, `<merge>`,
  kit `ui/` remplacé, ADR-085 → Accepted, exceptions vides).
