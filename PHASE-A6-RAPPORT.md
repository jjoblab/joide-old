# PHASE A6 — RAPPORT (racine de l'atelier)

- **Date** : 2026-11-19
- **Base** : A5 validée (`d30d56c`), arbre de travail **portant un travail
  A6 NON committé** à la prise de session (circonstance au §0).
- **Lot** : A6 — `EcranAtelier` devient une **coque mince** :
  `activity_atelier.xml` + `layout-w720dp/activity_atelier.xml`
  (`<include>`/composants par zone, **`FrameLayout puits`** pour la scène
  éditeur, `ViewStub` pour les zones rares — console, aperçu, tiroir
  agent), évaluation §4.4 de `CoordinatorLayout`/`BottomSheetBehavior`,
  `DrawerLayout`, `MaterialToolbar`, `TabLayout`/`RecyclerView`,
  suppression de `construireUi()`/`ContexteUi`, extraction du montage de
  session, **interface `HoteAtelier`**, FAB en `dimens`, `BarreOnglets`
  recyclée.

## 0. Circonstance de session (à connaître du propriétaire)

Le brief exigeait la base `d30d56c` et un arbre propre. À la prise de
session, la base était bien `d30d56c` (vérifié `git log --oneline -3`)
mais l'arbre portait déjà un travail A6 **non committé**, précisément
circonscrit au périmètre du lot (la racine XML, la vue d'écran, la
session, l'hôte, les quatre contrôleurs migrés, la bande d'onglets
recyclée, l'histogramme de la feuille, les layouts de zones
gonflables, l'ADR amendé, les tests du filet). Plutôt qu'un arrêt sec,
ce travail a été **audité intégralement** : lecture ligne à ligne des
différents diffs des quatre contrôleurs et des ~1 900 lignes des
nouveaux fichiers face au prompt, à l'ADR-085, aux maquettes
(`atelier.html`, `atelier-v2.html`) et aux spécifications (§005),
vérification de chaque mapping `atelier.xxx` → `hote.xxx()` face au
champ d'hier (l'union des 685 accès relevée à la base, les constantes
délocalisées retrouvées une à une : `ATTENTE_PORT_MS` et
`REDEMANDE_SERVICE_MS` dans `ControleurBuild`, `numerosBuild`/
`clientDemon` à LUI seul, `REQUETE_IMAGE`/`REQUETE_PHOTO` dans
`ControleurAgent`), mesures Annexe B **re-mesurées** sur la base ET sur
l'état hérité (les chiffres « avant » ci-dessous sont les miens), puis
**corrections portées** (§2 : la console différée montée à la première
ouverture de la feuille, le retour système sur le
`OnBackPressedDispatcher`, les imports qualifiés nettoyés, un commentaire
de tiroir erroné) et **validation re-exécutée par mes propres lancés**
(§5 : 364 tests verts forcés, assembleDebug, `verifierOmbreApk`, lint).
Rien n'a été committé sur la seule foi du travail hérité.

## 1. Constats du prompt — vérifications A6

- **C1 (pas de layout racine) CONFIRMÉ puis RÉSOLU** : à la base,
  `EcranAtelier.construireUi()` (l. 314-749, ~435 l.) assemblait la
  racine en code et la classe interne `ContexteUi` (`rangeeGrandEcran`,
  `cadrePuits`, `puitsCompact`) le grand écran. Après le lot :
  `activity_atelier.xml` (compact) + `layout-w720dp/activity_atelier.xml`
  portent TOUTE la composition — une colonne (barre haute 52, bannière de
  compatibilité, bande d'onglets 40, fil d'Ariane 28, CORPS à poids,
  barre de symboles 42, feuille basse), le grand écran en rangée amarrée
  (rail 48, colonne d'arbre 240, puits au poids, volet d'aperçu 320,
  §005 « tout amarré »). `construireUi()` et `ContexteUi` ont
  disparu ; `grep construireUi` : 0.
- **C2 (orchestrateur incomplet, 685 accès) CONFIRMÉ puis RÉSOLU** :
  `EcranAtelier` 1 526 → **180 lignes** (critère < 500 ✓ ; reste : cycle
  de vie, intents, extras, retour système, menu ⋮). Le câblage et les
  mises à jour vivent dans `VueAtelier` (1 318 l., la collaboratrice de
  la coque — **0 vue instanciée** : elle câble le binding, elle ne
  construit pas), le montage de session dans `SessionAtelier` (254 l. :
  `charger`/`monterIde`/`monterVues`/`ecrireEnAttente`/`fermerIde`,
  extraction fidèle, mêmes commentaires), et les quatre contrôleurs
  dépendent de **`HoteAtelier`** (333 l.) : `grep EcranAtelier` dans les
  contrôleurs : **0** (685 → 0).
- **La hauteur utile de la feuille est revalidée (le bug `6f2e899`)** :
  le câblage du lot L3 (`FeuilleBasse.onAttachedToWindow` : hauteur
  utile = la feuille + ses voisins À POIDS) vit désormais sur le parent
  **du layout racine** — la feuille est une voisine `wrap_content` du
  CORPS (`0dp`, poids 1) dans les DEUX variantes : leur somme est
  constante à toute détente, la feuille reçoit ENFIN la hauteur de son
  parent par le layout (et non plus par une hiérarchie posée en code) ;
  le `stub_console` est un enfant du `racine` FrameLayout, HORS de la
  colonne : il ne perturbe pas la mesure de la feuille.
- **Le `puits` (scène éditeur celeris)** : `FrameLayout` déclaré dans
  les deux layouts (`vue_editeur` dedans, `etat_vide_puits` par-dessus),
  la scène s'y attache au montage (`poserSceneEditeur`) et s'en détache
  à la fermeture (`viderEditeur`) — le conteneur n'est JAMAIS reconstruit
  (la ré-inflation de la racine au lot A8 le réattachera).
- **Les `ViewStub` (3 par variante)** : `stub_console` (zone coûteuse :
  3 listes recyclées + carte — montée à la première maj de console OU à
  la première ouverture de la feuille, JAMAIS pendant le chargement de
  session), `stub_apercu` (dans le volet plein écran — bascule plein
  écran ou volet droit du grand écran ; le dernier texte de mise en page
  est rejoué au montage : un rendu arrivé avant n'est pas perdu),
  `stub_tiroir_agent` (première ouverture — le service d'agent ne
  démarre qu'avec lui). Les contrôleurs ne référencent JAMAIS une zone
  non montée : `vueConsole`/`vueApercu` n'étaient pas atteints par les
  contrôleurs à la base (maj* uniquement), les accès à `vueAgent` sont
  tous postérieurs à l'ouverture (le service n'existe qu'après) ou
  gardés (`vueAgent() == null`).
- **Les évaluations §4.4 (évaluer n'est pas imposer)** — les quatre
  conclusions sont consignées dans l'ADR-085 (section « Évaluations du
  lot A6 ») :
  - **Onglets → `RecyclerView` horizontal RETENU** : l'onglet FERMABLE
    (× sur l'actif, appui long sur un autre) et l'indicateur accent 2 dp
    de la maquette (`.onglet.actif { border-bottom: 2px solid
    var(--accent) }`) exigent la vue d'onglet personnalisée
    (`item_onglet` du lot L2) — `TabLayout` garderait SA mécanique de
    sélection (indicateur/ripple) qui doublerait l'indicateur maison ;
    et la bande recycle (décision 11) ;
  - **Feuille basse → `FeuilleBasse` CONSERVÉE** (clause « si les
    détentes ne s'expriment pas ») : la spécification veut une VOISINE
    À POIDS qui resserre le corps, trois détentes FRACTIONS de la
    hauteur utile, ancrage au relâchement, tap qui cycle —
    `BottomSheetBehavior` est une feuille DANS un `CoordinatorLayout`
    qui recouvre sans jamais redimensionner un frère à poids ;
    l'exprimer exigerait un `Behavior` maison. `EtatFeuille` reste la
    source de vérité ;
  - **Tiroirs → `TiroirPousse` CONSERVÉ** : rideau teinté `jo_rideau`
    et fermeture au rideau (`.tiroir-rang` de la maquette), discipline
    PIEGE-31, mouvement 180 ms — `DrawerLayout` imposerait son scrim,
    ses gestes de bord et ses tiroirs enfants directs ; le tiroir agent
    est une zone RARE au-dessus de tout aux DEUX tailles ;
  - **Barre haute → `BarreHaut` CONSERVÉE** : pilule d'état de l'index,
    puce modules, point d'activité, bouton tiroir — pas de titre de
    fenêtre ni menu à trois points au sens M3, aucun défilement
    spécifié (pas d'`AppBarLayout`).
- **Critères du lot atteints** : `EcranAtelier` **180 l. < 500** ;
  **aucun contrôleur ne référence `EcranAtelier`** (grep : 0) ; **0 vue
  instanciée dans `EcranAtelier`** (et dans `VueAtelier`/
  `SessionAtelier` — le filet `ArchitectureVuesTest` les compte comme
  écrans) ; `EtatAtelierTest` (16), `EtatFeuilleTest` (9),
  `MaquetteAtelierTest` (6) verts ; **ordre du retour système
  inchangé** (le tiroir agent d'abord, puis feuille, tiroir, aperçu,
  confirmation, sortie — le `OnBackPressedCallback` porte le switch
  d'hier à l'identique) ; hauteur utile de la feuille revalidée (ci
  -dessus) ; `Dialogue.` **58 inchangé** (3 dans le triptyque
  écran/vue/session, comme hier dans l'écran) ; manifest **non touché**
  (A8).

## 2. Écarts avec le prompt (et corrections d'audit)

- **Console différée : la feuille ne s'ouvre plus sur un vide muet
  (CORRECTIF d'audit)** : le travail hérité montait la console au
  premier événement de build ou à l'ouverture pilotée par l'état — mais
  PAS à l'ouverture par GESTE interne (poignée/rail de la feuille,
  détente posée par `etatFeuille` interne, pas par `EtatAtelier`) : à
  froid, un tap sur la poignée montrait un corps VIDE là où hier la
  `VueConsole` (posée à la construction) montrait son en-tête et son
  état vide. Correction : `FeuilleBasse.Auditeur` gagne
  `surOuverture()` — `majCorps()` la demande AVANT de montrer un corps
  dont le contenu est null (idempotent : le montage est gardé, pas de
  boucle — le contenu devient non null). Le comportement d'hier est
  restauré, la déferlation conservée.
- **Retour système : `OnBackPressedDispatcher` (décision 13, motif des
  lots A2-A4)** : l'override déprécié `onBackPressed` de l'écran devient
  un `OnBackPressedCallback` toujours armé, enregistré AVANT tout
  retour anticipé de `onCreate` (l'état d'erreur garde son retour
  système, l'ordre `surRetour()` est celui d'hier). Bonus mesurable :
  l'erreur lint `MissingSuperCall` de l'écran disparaît (12 → 11).
- **`onActivityResult`/`startActivityForResult` restent dépréciés**
  (écart assumé, borné à A7) : les deux flux SAF/caméra vivent DANS
  `ControleurAgent` (48 vues, popups d'ajout — c'est LE découpage
  d'A7) ; les codes `47_101`/`47_102` ont été délocalisés de l'écran
  vers `ControleurAgent` et l'hôte expose
  `ouvrirEcranPourResultat(Intent, int)` avec le commentaire de
  passation — les contrats typés `Ecrans.CHOISIR_IMAGE`/`PRENDRE_PHOTO`
  prendront le relais en A7 (décision 13 : les codes disparaissent
  alors). Le brief borne A6 à l'interface hôte pour les contrôleurs ;
  aucun `Dialogue.*` n'a bougé.
- **Noms qualifiés nettoyés (reprise du motif A5)** : ~15 références
  qualifiées introduites par la migration ont repris leurs imports
  normaux (`EtatAtelier`, `EtatProblemes`, `EtatLogcat`,
  `Pilule`…`Fab`, `Pilule`, `Puce`, `FilAriane`, `ListeProblemes`,
  `EcrituresFusionnees`, `PackageManager`, `Reglages`) — la base les
  avait en imports. Restent tel quel : les formes qualifiées PRÉEXistantes
  (`java.nio.file.*` de `ControleurFichiers`,
  `android.view.inputmethod.*`, `GradientDrawable`, `KeystoreCles` —
  extraction mécanique, identité d'hier) et les `new EditText(` de
  `ControleurFichiers` VOLONTAIREMENT non qualifiés : le filet
  `ArchitectureVuesTest` les COMPTE (exceptions A7 — une forme
  qualifiée serait une évasion du filet, pas une correction).
- **Le tiroir fichiers en XML direct** : la zone est déclarée EN BALISE
  FILLE du `TiroirPousse` (`tiroir_agent_pousse.xml` aussi) —
  `TiroirPousse.onFinishInflate()` adopte ses enfants XML dans le
  panneau (le rideau et la barrière PIEGE-31 ne bougent pas) ; c'est
  l'équivalent `setPanneau()` d'hier, déclaré. ADR-085 décision 1 :
  « `<include>` OU par composant » — les deux voies sont couvertes.
- **VueAtelier n'est NI une activité NI une vue** : classe pure de
  câblage (1 318 l. — les corps d'hier, état + auditeurs + mises à
  jour + zones différées + services d'écran délégués). Le critère du
  prompt (« activité coque mince, contrôleurs découplés ») est tenu ;
  la taille de la collaboratrice est un état de transition documenté :
  A7 (popups + découpage de `ControleurAgent`, le plus gros
  consommateur de l'hôte) et A9 (consolidation) la réduisent —
  `HoteAtelier` porte l'union EXACTE de ce que les contrôleurs
  atteignaient hier (60 membres : lecture ET pose — le montage de
  session vit hors de l'écran), elle rétrécira pareillement.
- **Mesure Annexe B « palette.* » à lire avec précaution** : le grep
  mesure 290 → 218, MAIS 105 des accès d'hier (`atelier.palette.X`)
  sont devenus `hote.palette().X` (63 comptés par `palette().`) ou des
  captures locales — la mesure ne les voit plus, c'est un artefact du
  renommage, pas une dé-injection. La dé-injection RÉELLE : l'écran
  20 → 0 (bannière en layout + mélange calculé 12 % porté par
  `VueAtelier`, décision 8), `FeuilleBasse` 12 → 10 (histogramme au
  Canvas), `BarreOnglets` 4 → 4 (transportées à l'adaptateur). Restent
  au bind par nature (rôle/ligne, décision 8 documentée en A5).
- **`stub_console` différé AUSSI en écran compact** : l'ADR-085 dit
  « console GRAND ÉCRAN » — le lot applique la déferlation aux deux
  variantes (même zone, même coût : 3 listes recyclées + carte ;
  montée au premier besoin dans les deux cas). Écart de lecture assumé,
  cohérent avec « zones rares ou coûteuses ».
- **Détails d'audit** : commentaire du tiroir fichiers corrigé
  (« 300 dp max » → 360 dp au plus, ADR-É2 — le code d'hier n'a jamais
  posé d'autre largeur) ; accès `cadreArbre()` (l'accesseur public) au
  lieu du champ `binding` brut dans `monterVueArbre`.

## 3. Changements

| Fichier | Nature |
|---|---|
| `res/layout/activity_atelier.xml` (nouveau) | LA RACINE compacte : colonne (barre haute, bannière compat, onglets, fil d'Ariane, CORPS à poids — puits + volet plein écran portant son stub —, barre de symboles, FEUILLE voisine à poids), tiroir fichiers (`TiroirPousse` adoptant `TiroirFichiers`), `ViewStub` tiroir agent, FAB (marges `@dimen/jo_fab_*`) ; couleurs par `?attr/jo_*` uniquement, dimensions par `@dimen` (0 dp d'idiome poids seuls) |
| `res/layout-w720dp/activity_atelier.xml` (nouveau) | LA RACINE grand écran (**`w720dp`**, en parité avec `SEUIL_GRAND_ECRAN_DP` — pas `sw720dp`) : même colonne haute, corps en RANGÉE AMARRÉE — rail 48 (2 icônes : arbre, console), colonne d'arbre 240, puits AU POIDS, volet d'aperçu 320 — + volet plein écran/stub ; tiroir fichiers ABSENT (l'arbre est amarré) ; feuille et FAB communs |
| `res/layout/vue_console_zone.xml`, `vue_apercu_zone.xml`, `vue_arbre_zone.xml`, `tiroir_agent_pousse.xml` (4 nouveaux) | les layouts GONFLABLES des zones : vues déclarées pures (la classe gonfle elle-même son `<merge>` du lot A5), portées par les `ViewStub` / gonflées une fois (`vue_arbre_zone` : UNE instance de l'arbre, posée dans le tiroir compact OU la colonne amarrée — jamais deux) |
| `ecrans/atelier/EcranAtelier.java` | 1 526 → **180 lignes** : coque mince (thème, binding monté par `monter()`, extras par `Ecrans`, retour système par `OnBackPressedDispatcher`, menu ⋮, `onDestroy` → `vue.fermer()`) ; `construireUi()`/`ContexteUi` supprimés |
| `ecrans/atelier/VueAtelier.java` (nouveau) | la vue d'écran : câblage des zones (auditeurs d'hier inchangés), mises à jour (volets, FAB, verdicts, console, aperçu), zones différées par stub (montage à la demande + rejou du texte de mise en page), l'hôte implémenté (services d'écran délégués, états, session lue/posée) — 0 vue instanciée |
| `ecrans/atelier/SessionAtelier.java` (nouveau) | le montage de session extrait (~250 l. d'hier) : `demarrer`/`charger`/`monterIde`/`monterVues`/`ecrireEnAttente`/`fermerIde` — la scène s'attache au PUITS du layout racine, jamais reconstruit ; garde `isFinishing()/isDestroyed()` d'hier conservée (A8 la généralise) |
| `ecrans/atelier/HoteAtelier.java` (nouveau) | l'interface hôte : l'union exacte de ce que les quatre contrôleurs atteignaient (services d'activité, états, session, zones, mises à jour, montage différé) — testable avec un faux hôte, rétrécit en A7/A8/A9 |
| `ControleurBuild`, `ControleurAgent`, `ControleurEdition`, `ControleurFichiers` | `EcranAtelier atelier` → `HoteAtelier hote` (extraction mécanique, mêmes corps/commentaires) ; `ATTENTE_PORT_MS`, `REDEMANDE_SERVICE_MS`, `numerosBuild`, `clientDemon` deviennent à `ControleurBuild` ; `REQUETE_IMAGE`/`REQUETE_PHOTO` à `ControleurAgent` (contrats typés en A7) ; **0 référence à l'écran** |
| `ui/TiroirPousse.java` | `onFinishInflate()` : l'adoption des enfants XML déclarés (équivalent déclaré de `setPanneau()`) — le tiroir se déclare en layout racine |
| `ui/atelier/BarreOnglets.java` | 170 → 128 l. : la bande RECYCLÉE — `RecyclerView` horizontal + `AdaptateurOnglets` (ListAdapter/DiffUtil), plus de `removeAllViews()`/ré-inflation à chaque maj (constat I4 appliqué à la bande, signalé au rapport A5) ; le RAPPEL DE COMMIT du diff porte le défilement minimal vers l'actif (leçon A5) |
| `ui/atelier/AdaptateurOnglets.java`, `OngletVu.java` (nouveaux) | l'onglet fermable recyclé : la valeur de rendu immuable (record pur — le diff court en tâche de fond), TOUT re-posé au bind, l'appui long ferme un autre onglet (§3.2) — rendu identité pour identité au `item_onglet` d'hier |
| `ui/atelier/FeuilleBasse.java` | les TROIS LISTES de section vivent dans SON layout (adoptées à la construction — l'écran n'instancie plus rien) ; l'histogramme du débit logcat se DESSINE au Canvas (`HistogrammeLogcat`) — dernière vue instanciée de la feuille, sortie des exceptions du filet ; `Auditeur.surOuverture()` (le correctif d'audit §2) |
| `ui/atelier/HistogrammeLogcat.java` (nouveau) | les 9 barres 3 dp (écart 2, rayon 2, barre erreur en couleur erreur) dessinées — vue déclarable en XML, constructeur `(Context, AttributeSet)` |
| `res/layout/atelier_feuille.xml`, `feuille_entete.xml` | les trois listes déclarées dans le contenu de la feuille ; l'histogramme en vue Canvas (remplace la rangée de vues posée une à une) |
| `res/layout/atelier_onglets.xml` | la bande : `HorizontalScrollView`+`LinearLayout` → `RecyclerView` horizontal (padding 0 8 conservé, `clipToPadding="false"`) |
| `res/values/dimens.xml` | +13 jetons du lot (hauteurs 52/40/28/42, rail 48/10, arbre 240, volet 320, bannière 14/7, **FAB 84/20** — les valeurs du code d'hier) |
| `docs/adr/085-architecture-vues-ecrans.md` | + section « Évaluations du lot A6 » : les quatre évaluations §4.4 et leurs conclusions (ci-dessus §1) |
| `ArchitectureVuesTest` | l'écran atelier sort des exceptions de vues ; `VueAtelier`/`SessionAtelier` comptent comme écrans (0 vue) ; exceptions de vues de zone réduites à `VueAgent` (A7) ; `VueAtelier`/`SessionAtelier` dans la liste `Dialogue.` (3 usages transportés) |
| `ThemesM3Test` | + `lesJetonsDuLotA6OntLeursDimens` (13 jetons, parité) |
| `ui/atelier/OngletVuTest.java` (nouveau) | 7 tests JVM : identité (chemin), rendu (drapeau/badge/points/nom), et la MESURE du coût par maj (activer un autre onglet = 2 re-liés, 0 gonflé ; ouvrir = 1 insertion + 1 re-lier ; fermer = 1 retrait + 1 re-lier) |

## 4. Mesures avant/après (Annexe B, re-mesurées par l'agent)

| Mesure | A5 (`d30d56c`) | A6 |
|---|---|---|
| Lignes `EcranAtelier` | 1 526 | **180** (< 500 ✓) |
| Accès contrôleurs → champs d'écran (`atelier.xxx`) | 685 | **0** (grep `EcranAtelier` : 0) |
| Vues instanciées en code (module) | 94 | **84** (écran 11 → 0 ; feuille 1 → 0 (Canvas) ; `ControleurFichiers` 1 → 3 : les `new EditText(` qualifiés d'hier désormais COMPTÉS — voulus, A7 les sortira) |
| dont `ControleurAgent` / `VueAgent` | 48 / 10 | 48 / 10 (inchangés — A7) |
| `palette.*` (grep Annexe B) | 290 | **218** (artefact §2 : 105 accès devenus `hote.palette().`… ; dé-injection réelle : écran 20 → 0, feuille 12 → 10) |
| `GraphiteVue.dp(` | 261 | **245** (−16 : écran −17, feuille −4, bande −1 ; histogramme +5, adaptateur +1) |
| Layouts | 60 | **65** (+5 racine + 4 zones gonflables) + **1 `layout-w720dp`** — premier qualificatif de TAILLE du module (`w720dp`, parité `SEUIL_GRAND_ECRAN_DP`) |
| `ViewStub` (balises réelles) | 0 | **6** (3 par variante : console, aperçu, tiroir agent) |
| dp littéraux dans les layouts | 262 | **263** (+3, tous des `0dp` d'idiome `layout_weight` ; aucune dimension réelle) |
| sp littéraux | 56 | 56 |
| `<merge` / `<include` | 3 / 38 | 3 / 38 (les zones se déclarent EN COMPOSANT, décision 1 : « `<include>` ou par composant ») |
| `Dialogue.` usages | 58 | **58** inchangés (écran 3 → écran 2 + vue 1 ; session 1 transportée d'hier ; aucun nouveau dialogue, D3) |
| Imports Compose / hors liste blanche | 0 | **0** (`androidx.recyclerview` 30 → 34, `core` 2, `activity` 1 — tous listés ADR-085) |
| APK debug (R8 + shrink) | 15 775 929 o | **15 798 411 o (+22 482 o, +0,02 Mio)** — aucune dépendance nouvelle : layouts, `dimens`, classes d'écran et adaptateur |
| `verifierOmbreApk` | vert | **vert** (« APK sans ombre, éléments vitaux présents (15798411 o) ») |
| Tests JVM `:app-android:test` | 356 verts | **364 verts, 0 échec** (+1 parité dimens, +7 `OngletVuTest` ; passe forcée `--rerun` sur l'état final) |

## 5. Validation

**Reprise d'audit** : les exécutions ci-dessous sont LES MIENNES, sur
l'état final (corrections §2 comprises) ; la passe de tests a été
forcée (`--rerun`). Le démon Gradle est mort UNE fois en pleine charge
R8 d'`assembleDebug` (contrainte d'environnement documentée aux
rapports A1/A3/A4/A5) : le re-jeu immédiat, à
`-Dorg.gradle.jvmargs=-Xmx1500m` comme le prescrit le brief, est
vert — aucun fichier du dépôt modifié pour cela.

- `./gradlew --init-script … :app-android:testDebugUnitTest --rerun` —
  **364 tests, 0 échec, 0 erreur** (dont `OngletVuTest` 7,
  `EtatAtelierTest` 16, `EtatFeuilleTest` 9, `MaquetteAtelierTest` 6,
  `ArchitectureVuesTest` 8, `ThemesM3Test` 11,
  `RecyclageListesAtelierTest` 9, `JetonsXmlTest` 5) ; puis
  `:app-android:test` — vert.
- `./gradlew … :app-android:assembleDebug` — vert ;
  `verifierOmbreApk` : « APK sans ombre, éléments vitaux présents
  (15798411 o) ».
- `./gradlew … :app-android:lintDebug` — **11 erreurs** = les 12 du
  rapport A5 MOINS `MissingSuperCall EcranAtelier:1174` (l'override
  `onBackPressed` déprécié a disparu avec la décision 13) : les 8
  `NewApi` préexistantes (`Path#of` ×3, `toList`, `readString`,
  `readAllBytes` ×2, `transferTo` — hors lot, préexistantes au lot L)
  et les 3 `DiffUtilEquals` (`AdaptateurAccueil` A2, `AdaptateurArbre`,
  `AdaptateurProblemes` A5 — records derrière interfaces scellées,
  faux positifs probables, décision du propriétaire attendue)
  EXACTEMENT. **Aucune nouvelle erreur
  NewApi/HardcodedText/ContentDescription.** `AdaptateurOnglets` n'est
  pas signalé (le diff passe par `memeOnglet`/`memeRendu` explicites).
  Avertissements : les `DuplicateIncludedIds` ×14 sont TOUS dans
  `activity_parametres.xml` (lot A3, préexistant) ; aucun
  avertissement ne pointe un fichier du lot.
- `./gradlew check` racine NON VERT — `checkLanguagePolicy` échouait
  déjà au commit de base (1 313 problèmes typographiques préexistants,
  preuve archivée au rapport A1) et `lintDebug` porte les 11 erreurs
  ci-dessus : inchangé par le lot (les documents du chantier restent
  conformes).
- Parité `values/` ↔ `values-fr/` vérifiée par script : **391 = 391
  clés**, 0 seulement-d'un-côté (aucune chaîne nouvelle — les layouts
  n'utilisent que des chaînes existantes, dont
  `atelier_desc_fichiers`/`atelier_desc_console`).
- Manifest non touché (A8 y posera `configChanges`).
- **Aucun appareil/émulateur** : états visuels à vérifier à la main par
  le propriétaire —
  - racine compacte : l'ordre colonne (barre, bannière, onglets, fil,
    corps, symboles, feuille), la feuille VOISINE du corps (sa
    dépliage resserre le puits — hauteur utile mesurée par le câblage
    L3 SUR le layout), le tiroir fichiers (rideau teinté, fermeture au
    rideau, 360 dp), le FAB 84/20 qui disparaît quand la feuille
    dépasse ;
  - grand écran (≥ 720 dp) : la rangée amarrée (rail, arbre 240, puits
    au poids, volet 320 fermable), la bascule multi-fenêtre/rotation —
    **ATTENTION : A8 n'est pas fait** (pas de `configChanges`, pas de
    sauvegarde d'état — la recréation détruit la session comme à la
    base, constat C3 inchangé, frontière du lot) ;
  - zones différées : la console vide d'état visible à la PREMIÈRE
    ouverture de feuille (correctif d'audit), l'aperçu plein
    écran ↔ volet droit (déplacement du volet entre parents), le
    tiroir agent (première ouverture : service démarré, journal
    rejoué) ;
  - bande d'onglets : fermeture par × (actif seul), appui long sur un
    autre onglet, défilement minimal vers l'actif, coût par maj borné ;
  - retour système : tiroir agent → feuille → tiroir → aperçu →
    confirmation → accueil, à la main (l'ordre est testé en logique
    d'état, le dispatcheur porte le switch d'hier à l'identique).

## 6. Risques résiduels et suite

- `VueAtelier` (1 318 l.) et `HoteAtelier` (60 membres) sont des états
  de transition assumés : ils portent l'union exacte d'hier — A7
  (popups + découpage de `ControleurAgent`, le plus gros consommateur)
  puis A8/A9 les réduisent membre à membre (documenté dans leurs
  javadocs).
- `DiffUtilEquals` ×3 (A2 + A5 ×2) : décision du propriétaire attendue
  (suppression, annotation ou re-formulation des DIFF) ; le chantier ne
  les masque pas.
- Le rail du grand écran porte 2 icônes (arbre, console — §005 et le
  code d'hier) ; la maquette `atelier.html` en montre 3 (dossier, loupe,
  horloge) : écart PRÉEXISTANT du lot J (le grand écran d'hier avait
  déjà 2), conservé — à arbitrer par le propriétaire.
- La déferlation de la console/aperçu s'appuie sur le comportement du
  `ViewStub` (LayoutParams du stub transférés à la vue gonflée) : validé
  par lecture et par les tests d'état, à confirmer sur appareil.
- Suite : **A7** — popups (fin des dialogues, D3 : `Dialogue` et les
  58 usages), découpage de `ControleurAgent`, contrats typés
  `Ecrans.CHOISIR_IMAGE`/`PRENDRE_PHOTO` ; **A8** — `configChanges`,
  `onConfigurationChanged` (ré-inflation de la racine, réattachement du
  puits, fermeture des popups), sauvegarde d'état.
