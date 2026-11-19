# ADR-085 — Architecture des vues d'écran : AndroidX, Material 3, popups

**Statut :** Proposed (→ Accepted au lot A9)
**Date :** 2026-11-19 (chantier lancé le 2026-11-18 par les directives du propriétaire)
**Deciders :** Olson (propriétaire, directives D1-D3) ; agent d'ingénierie (instruction et avis motivé)
**Remplace partiellement :** [ADR-083](083-atelier-v2-layouts-viewbinding.md) §2 (« zéro AndroidX UI », viewbinding seule tolérée) ; la section « Implémentation — vues du cadre uniquement, zéro AndroidX » de `docs/ecrans/002-systeme-design.md` est caduque pour l'implémentation (le document spéculatif n'est pas modifié — maquettes et jetons demeurent contractuels).

## Contexte

Les lots L1 à L6 (ADR-083) ont migré des **zones** de l'atelier en XML +
ViewBinding (22 layouts, 22 `*Binding`, tous consommés), mais l'**écran** —
la racine, la composition, la configuration, le cycle de vie — est resté
programmatique. Le lot A0 (`PHASE-A0-RAPPORT.md`, commit `fb1c3b6`) a mesuré
l'état au dépôt `0e15c16` :

- **0 layout racine** : chaque activité fait `setContentView` sur une
  hiérarchie assemblée en code (cause racine du bug de hauteur de la
  feuille, commit `6f2e899`) ;
- **203 vues instanciées en code** dans les écrans et les contrôleurs
  (ControleurAgent 48, EcranCreation 34, EcranParametres 20,
  MainActivity 14, EcranAtelier 11…) ;
- **685 accès** des quatre contrôleurs aux champs d'`EcranAtelier` ;
- **58 usages** de la classe maison `Dialogue` (9 fabriques
  `AlertDialog.Builder`) dans 9 fichiers ; **24** références de classes de
  dialogue du cadre, toutes dans `Dialogue.java` ;
- **374 couleurs** (`palette.*`) et **414 dimensions** (`GraphiteVue.dp(`)
  injectées en code — double voie de thémage avec `?attr/jo_*` ;
- **aucune** ressource qualifiée (`layout-*`, `values-w*`), aucun
  `onSaveInstanceState`, aucun `configChanges` : une rotation détruit la
  session d'IDE (C3) ;
- **C5 confirmé** : `EcranAtelier.java:547-549` appelle
  `WindowInsets.Type.ime()` / `getInsets(int)` (API 30) sous `minSdk` 28
  sans garde.

ADR-083 écartait AppCompat, Material, RecyclerView et ConstraintLayout pour
garder un APK minimal. Le propriétaire a **levé cet interdit** (directives du
2026-11-18) : l'UI doit s'appuyer sur AndroidX et Material 3 habillés
Graphite, et ne plus utiliser de dialogues. Vérifications du lot A0 sur le
socle de build :

- `compileSdk = 37` (+ `compileSdkMinor = 2`) dans
  `joide.android-application-conventions.gradle.kts` : **suffisant** pour
  les versions stables d'AppCompat/Core/Activity/Material retenues — aucun
  relèvement nécessaire ;
- `verifierOmbreApk` (sondes `jo.joide.build.android.*` dans l'APK) et
  `MurEtancheTest` (surface `-keep` du jeu `tools`) ne portent **aucun**
  interdit AndroidX/Material — **rien à amender** : ils continuent de
  garder le mur de chargeurs, inchangés ;
- `minSdk`/`targetSdk` restent 28 (ADR-009/013) ; R8 + shrinkResources
  actifs sur debug et release (ADR-070) : les bibliothèques fournissent
  leurs règles de conservation, le delta d'APK se mesure à chaque lot.

## Directives du propriétaire (exigences, non négociables)

- **D1 — Vues AndroidX.** L'interdit « zéro AndroidX UI » d'ADR-083 est
  levé. Autorisés : `androidx.appcompat`, `androidx.core`,
  `androidx.activity`, `androidx.constraintlayout`,
  `androidx.recyclerview`, `androidx.coordinatorlayout`,
  `androidx.drawerlayout`, `androidx.fragment`, `androidx.lifecycle` (et
  tout artefact de vues justifié ici). Seul **Jetpack Compose** reste
  **exclu** : l'application est en Java. `Fragment` et
  `ViewModel`/Lifecycle sont autorisés mais non imposés ; un état retenu
  par un `ViewModel` ne référence jamais vue ni `Context` d'activité ;
  `DialogFragment` interdit (D3).
- **D2 — Material 3.** `com.google.android.material`, thèmes
  `Theme.Material3.*`. Les composants Material 3 remplacent les vues du
  kit `ui/` et les vues du cadre **partout où il existe un équivalent**,
  habillés aux jetons Graphite (couleurs, formes, typographie) : l'identité
  visuelle ne change pas. Pas de Dynamic Color.
- **D3 — Popups à la place des dialogues.** Aucun `AlertDialog`,
  `MaterialAlertDialogBuilder`, `android.app.Dialog`, `DialogFragment`,
  `BottomSheetDialog`, `DatePickerDialog`/`TimePickerDialog`,
  `ProgressDialog` dans `app-android`. Tout ce qui était un dialogue devient
  un **popup** (`PopupWindow` et dérivés) gonflé depuis un `popup_*.xml`.
  Un `BottomSheetBehavior` **persistant** (feuille de l'atelier), `Toast`
  et `Snackbar` restent permis.

## Décision

1. **Un layout racine par écran** + binding d'écran :
   `activity_accueil`, `activity_creation`, `activity_atelier`
   (+ `layout-w720dp/activity_atelier`), `activity_parametres`,
   `activity_callback_mcp`. Les zones s'y composent par `<include>` ou par
   composant ; les zones rares ou coûteuses (aperçu, console grand écran,
   tiroir agent) par `<ViewStub>`.
2. **Activité mince**, dérivée d'`AppCompatActivity` via `ActiviteJoide` :
   `onCreate` = (thème) → binding → câblage → démarrage. L'utilitaire
   `monter(...)` garantit l'ordre thème → inflation. Ni hiérarchie de vues,
   ni logique métier dans l'activité.
3. **Contrôleurs découplés** : ils dépendent d'une **interface hôte étroite**
   (ex. `HoteAtelier`) et non plus des champs package-private
   d'`EcranAtelier` ; testables avec un faux hôte.
4. **Dépendances AndroidX/Material épinglées au catalogue** (règle §10 :
   jamais de version en dur dans un module), uniquement celles dont le
   code importe une classe, en versions **stables** :
   | Artefact | Rôle | Lot d'entrée |
   |---|---|---|
   | `androidx.appcompat:appcompat` | `AppCompatActivity` (`ActiviteJoide`) | A1 |
   | `androidx.core:core` | `WindowInsetsCompat` (correctif C5), `ViewCompat` | A1 |
   | `androidx.activity:activity` | `OnBackPressedDispatcher`, Activity Result | A1 |
   | `com.google.android.material:material` | thèmes `Theme.Material3.*`, composants D2 | A1 |
   | `androidx.recyclerview:recyclerview` | listes recyclées | A2 |
   | `androidx.constraintlayout:constraintlayout` | racine grand écran | A6 |
   | `androidx.coordinatorlayout:coordinatorlayout` | feuille/`BottomSheetBehavior` | A6 |
   | `androidx.drawerlayout:drawerlayout` | tiroirs | A6 |
   | `androidx.fragment`/`androidx.lifecycle` | si adoptés (option C/ε) | A8 |

   Liste blanche d'imports (garantie par `ArchitectureVuesTest`) :
   `androidx.appcompat.`, `androidx.core.`, `androidx.activity.`,
   `androidx.constraintlayout.`, `androidx.recyclerview.`,
   `androidx.coordinatorlayout.`, `androidx.drawerlayout.`,
   `androidx.fragment.`, `androidx.lifecycle.`, `androidx.viewbinding.`,
   `com.google.android.material.` ; interdits absolus : `androidx.compose.`
   et `androidx.fragment.app.DialogFragment`.
5. **Thème Material 3 habillé Graphite (D2)** : `Sombre` et `Clair`
   descendent de `Theme.Material3.Dark.NoActionBar` /
   `Theme.Material3.Light.NoActionBar`. Les rôles M3 pointent vers les
   jetons existants de `colors.xml` — `colorPrimary`/`colorOnPrimary` ←
   accent/texte-sur-accent, `colorPrimaryContainer`/`colorOnPrimaryContainer`
   ← accent-tonal/texte, `colorSecondary`/`colorOnSecondary` ←
   violet/texte, `colorSurface`/`colorOnSurface` ← surface/texte,
   `colorSurfaceVariant`/`colorOnSurfaceVariant` ← surface-haute/texte,
   `colorOnBackground` ← texte, `colorOutline` ← bord,
   `colorOutlineVariant` ← bord-fin, `colorError`/`colorOnError` ←
   erreur/fond ; `shapeAppearance*` ← rayons Graphite (`@dimen/jo_rayon_*`) ;
   `textAppearance*` ← échelle typographique Graphite (`TextAppearance.Jo.*`).
   Les attributs `?attr/jo_*` sans rôle M3 restent posés par les thèmes.
   Pas de Dynamic Color. `ThemesM3Test` (JVM) vérifie : parent M3 des deux
   thèmes, rôles requis définis, chaque rôle résolu vers une couleur
   existante, l'égalité des dimenstyles avec `Graphite`.
6. **Composants Material à la place du kit** selon la table « Équivalents »
   ci-dessous ; une vue du kit n'est conservée que **sans équivalent** ou si
   l'équivalent ne sait pas exprimer la maquette (justification consignée).
7. **Ressources de jetons complètes** : `dimens.xml` (rayons, grille,
   marges, gouttière, tuile — parité testée avec `Graphite`) et `styles.xml`
   (`TextAppearance.Jo.*` héritant de `TextAppearance.Material3.*`,
   `Widget.Jo.*` héritant de `Widget.Material3.*`). Là où un `StyleTexte`
   n'est pas exprimable en `textAppearance` (échelle de texte des réglages,
   police pondérée), `GraphiteVue.typographie` reste et c'est documenté.
8. **Une seule voie de thémage** : en XML, rôles M3 puis `?attr/jo_*` ; en
   Java, la palette ne sert qu'aux vues dessinées au `Canvas` et aux
   couleurs calculées dynamiquement (nuancier d'accent).
9. **Configuration par ressources qualifiées** : `values-w720dp/bools.xml`
   (`est_grand_ecran`) et `layout-w720dp/` — **`w720dp`** (largeur
   disponible, suit rotation et multi-fenêtre), en parité avec
   `EtatAtelier.SEUIL_GRAND_ECRAN_DP` (720) ; **pas `sw720dp`**.
10. **Politique de cycle de vie de l'atelier** : déclarer
    `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout"`
    sur `EcranAtelier` (**posé au lot A8** — la géométrie SEULE : thème,
    langue, densité et taille de police restent des recréations) ;
    `onConfigurationChanged` ré-inflamme **seulement la
    racine** (le `puits` portant la scène éditeur est détaché AVANT et
    rattaché APRÈS, jamais reconstruit, la session d'IDE n'est pas fermée),
    **ferme les popups
    ouverts**, et l'état d'UI minimal vit dans `onSaveInstanceState` pour
    les recréations restantes (thème, langue, taille de police). L'ordre du
    retour système actuel (popup → feuille → tiroirs → sortie) est
    inchangé et testé.
11. **Listes : `RecyclerView`** + `ListAdapter`/`DiffUtil`, view-holder =
    `*Binding`, pour tout flux (Logcat, Journal, Console, Problèmes, Arbre,
    projets récents) et toute liste > ~50 lignes ; plafond conservé (400
    lignes), mise à jour par `submitList`. Une liste courte et statique
    peut rester dans le layout (justifiée dans le rapport du lot).
12. **Popups à la place des dialogues (D3)** : deux gabarits — (a) popup
    **ancré** (`PopupWindow`, `ListPopupWindow`, `PopupMenu`) pour menus,
    sélecteurs, détails courts ; (b) popup **centré avec voile** pour ce
    qui exige une réponse (confirmation destructive, saisie, diff de
    permission, usages, historique, connexions MCP). `PopupAncre` est
    conservée et généralisée ; un gabarit centré (`PopupCentre`) s'y
    ajoute. `Dialogue` est supprimée en A7. Corps dans `popup_*.xml` +
    binding ; retour système : un popup ouvert est fermé en premier
    (`OnBackPressedCallback` ou `focusable`) ; clavier :
    `INPUT_METHOD_NEEDED` + `SOFT_INPUT_ADJUST_RESIZE` ; cycle de vie :
    fermeture explicite en `onPause`/`onDestroy`/`onConfigurationChanged`,
    jamais restauré (l'état de saisie vit dans l'`Etat*`) ; accessibilité :
    `contentDescription`, `setAccessibilityPaneTitle` (API 28), focus
    initial, cibles ≥ 48 dp. Le voile : `FLAG_DIM_BEHIND` sur la fenêtre du
    popup **ou** vue-voile du layout racine — uniformisé par `PopupCentre`.
13. **Navigation typée** : la classe `Ecrans` porte les fabriques
    d'`Intent`, les extras et les **contrats** `ActivityResultContract` ;
    `registerForActivityResult` remplace `onActivityResult` (les codes
    `47101`, `47102`, `41` disparaissent) ; `OnBackPressedDispatcher`
    remplace `onBackPressed`.
14. **Insets (C5)** : `ViewCompat.setOnApplyWindowInsetsListener` +
    `WindowInsetsCompat.Type.ime()` (`androidx.core`) — plus aucune API 30
    directe ; le clavier sur API 28/29 reste à valider sur appareil
    (`adjustResize`).
15. **Garde-fous automatiques** : `ArchitectureVuesTest` (posé en A0) —
    écrans sans vue instanciée, classes de dialogue interdites, usages
    `Dialogue.`, imports dans la liste blanche (Compose/`DialogFragment`
    interdits), chaque layout consommé, aucune couleur ni texte littéraux ;
    **listes d'exceptions décroissantes** (une entrée sans faute échoue — la
    liste ne peut que décroître, vide en A9). `verifierOmbreApk` et
    `MurEtancheTest` gardent le mur de chargeurs, **inchangés** (vérifié :
    ils n'encodent aucun interdit AndroidX).

## Options considérées

**Boîte à outils UI** — T1 « vues du cadre seules » (statu quo ADR-083) :
écartée (contredit D1/D2 ; maintenance maison des listes, thème, popups).
**T2 « AndroidX + Material 3 »** : retenue (D1/D2 ; composants testés,
accessibilité et états fournis, `RecyclerView`, `WindowInsetsCompat`,
`OnBackPressedDispatcher` ; coût : habillage Graphite à écrire, delta d'APK
mesuré par lot, `compileSdk` déjà suffisant). T3 « Jetpack Compose » :
écartée (exige Kotlin ; application Java ; réécriture complète).

**Structure des écrans** — A « statu quo + finir les zones » : écartée
(laisse racines en code, config destructrice). **B « layout racine + coque
mince + ressources qualifiées »** : retenue (résout C1-C4, incrémentale, un
écran par lot). C « `Fragment` par zone » : autorisée, non retenue par
défaut (cycle de vie plus lourd, gain faible face à B ; jamais de
`DialogFragment` — D3) ; à réévaluer si une zone (tiroir agent) gagne à un
cycle de vie propre. D « Compose » : écartée.

**Dialogues et popups** — P1 `MaterialAlertDialogBuilder` : écartée (D3).
**P2 `PopupWindow` ancé/cintré gonflé depuis `popup_*.xml`** : retenue
(conforme D3, contrôle total du rendu Graphite ; modalité, clavier, retour
et fuites gérés à la main — décision 12). P3 `BottomSheetDialog` : écartée
(reste un dialogue).

**Configuration de l'atelier** — α « laisser recréer » : écartée (détruit
scène, aperçu, moteurs LSP et état à chaque rotation). **β
« `configChanges` de géométrie + ré-inflation de la racine + état minimal +
popups fermés »** : retenue (décision 10, **POSÉE au lot A8**). γ « conteneur de session statique
indexé par racine » : à évaluer en suite (robuste aux recréations, état
global à gérer). δ `onRetainNonConfigurationInstance` : écartée (dépréciée
depuis l'API 28). ε `ViewModel` : **autorisée, évaluée en A8 — ÉCARTÉE**
(la décision motivée vit à « Évaluations du lot A8 » : β couvre la
géométrie, l'état minimal couvre les recréations restantes ; la session
d'IDE y déménagerait avec un `Context` d'activité — interdit — aucun
changement de propriété n'a donc lieu à consigner).

## Équivalents proposés pour le kit `ui/`

| Vue actuelle | Remplaçant | Remarque |
|---|---|---|
| `Bouton` | `MaterialButton` (`Widget.Jo.Button.*` : plein, tonal, contour, texte) | |
| `Carte` | `MaterialCardView` | formes et élévation tonale habillées |
| `Champ` | `TextInputLayout` + `TextInputEditText` | |
| `Fab` | `FloatingActionButton` / `ExtendedFloatingActionButton` | positions en `dimens` (A6) |
| `Interrupteur` | `MaterialSwitch` | |
| `Segment` | `MaterialButtonToggleGroup` | |
| `Puce`, `Pilule` | `Chip` / `ChipGroup` | |
| `Onglets`, `BarreOnglets` | `TabLayout` ou `RecyclerView` horizontal | l'onglet fermable de l'atelier tranche (A6) |
| `BarreHaut` | `MaterialToolbar` (dans `AppBarLayout` si défilement) | |
| Feuille basse (`atelier_feuille`) | `BottomSheetBehavior` persistant dans un `CoordinatorLayout`, `EtatFeuille` source de vérité | si les détentes spécifiées ne s'expriment pas, la feuille actuelle est conservée et consignée ici |
| `TiroirPousse`, `TiroirFichiers`, tiroir agent | `DrawerLayout` (ou panneau permanent en `layout-w720dp`) | `DrawerLayout` RECOUVRE au lieu de pousser : vérifier le comportement spécifié (A6) |
| Écran grand écran | `ConstraintLayout` / `LinearLayout` à poids, éventuellement `NavigationRailView` | |
| `Nuancier`, `Stepper`, `EtatVide`, `Icone` (si dessinée au `Canvas`) | **conservées** | pas d'équivalent Material direct |

### Évaluations du lot A6 (atelier — « évaluer n'est pas imposer »)

Les quatre composants de la table ont été évalués sur le comportement
spécifié (§005 + maquettes `atelier.html`/`atelier-v2.html`, qui font
foi) ; la conclusion de chaque évaluation :

- **Onglets → `RecyclerView` horizontal** (retenu) : l'onglet
  FERMABLE (× sur l'actif, appui long sur un autre) et l'indicateur
  accent 2 dp exigent une vue d'onglet personnalisée — `TabLayout`
  garderait sa propre mécanique de sélection (indicateur/ripple) qui
  doublerait l'indicateur maison ; la bande recycle en plus
  (ListAdapter/DiffUtil, décision 11).
- **Feuille basse → `FeuilleBasse` conservée** (la clause « si les
  détentes ne s'expriment pas » s'applique) : la spécification veut
  que la feuille soit une VOISINE À POIDS qui RESSERRE le corps (le
  puits perd sa hauteur quand elle se déplie — hauteur utile = feuille
  + voisins à poids, mesurée par le câblage du lot L3), avec trois
  détentes FRACTIONS de cette hauteur utile, l'ancrage au relâchement
  et le tap qui cycle — `BottomSheetBehavior` est une feuille DANS un
  `CoordinatorLayout` qui recouvre son parent sans jamais retenir la
  taille des voisins (pas de comportement d'appui stock pour
  redimensionner un frère à poids) ; l'exprimer exigerait un
  `Behavior` maison, contredisant le gain cherché. `EtatFeuille`
  reste la source de vérité (décision inchangée).
- **Tiroirs → `TiroirPousse` conservé** : la maquette spécifie le
  panneau glissant du bord avec RIDEAU teinté `jo_rideau` et fermeture
  au rideau (`.tiroir-rang` de `atelier.html`), discipline PIEGE-31
  (le rideau fermé ne porte jamais la cliquabilité) et mouvement de
  composant 180 ms — `DrawerLayout` saurait glisser, mais il impose
  son propre scrim, ses gestes de bord et ses tiroirs enfants directs
  ; le tiroir agent est une zone RARE montée par `ViewStub` au-dessus
  de tout aux DEUX tailles (il n'est pas un tiroir de navigation), et
  le grand écran n'a qu'un seul tiroir — le conteneur `DrawerLayout`
  n'apporterait qu'un niveau de plus. Aucun changement de comportement
  voulu.
- **Barre haute → `BarreHaut` conservée** : la barre (52 dp, lot L2)
  porte des zones sans équivalent `MaterialToolbar` — pilule d'état de
  l'index (point + libellé teintés, tap = détail), puce « n modules »,
  point d'activité de l'agent, bouton tiroir — l'équivalent menu/entrée
  de `MaterialToolbar` changerait l'identité de la maquette (pas de
  titre de fenêtre, pas de menu à trois points au sens M3) et aucun
  défilement n'est spécifié (pas d'`AppBarLayout`). À rediscuter en A9
  si le kit décroît.

### Évaluations du lot A7 (le voile de la décision 12 — « choisir, justifier, uniformiser »)

La décision 12 laissait le voile ouvert entre
`FLAG_DIM_BEHIND` sur la fenêtre du popup **ou** vue-voile du layout
racine. Le lot tranche et consigne :

- **Vue-voile du layout racine, RETENUE** (l'autre voie est
  **impraticable**) : `PopupWindow` n'expose PAS sa fenêtre — aucun
  `getWindow()` public, aucun drapeau posable ; `FLAG_DIM_BEHIND`
  s'applique à une fenêtre de DIALOGUE, interdite par D3. Le voile est
  donc une vue `voile_popup` déclarée DERNIER enfant du layout racine
  de chaque écran porteur (accueil, atelier compact, atelier
  `w720dp`, paramètres) : `?attr/jo_rideau`, cliquable (elle RETIENT le
  toucher hors de la carte — pas de fermeture au dehors pour ce qui
  exige une réponse), `gone` au repos ; `PopupCentre` la montre et la
  cache. La carte centrée vit dans sa PROPRE fenêtre au-dessus du
  voile (`PopupWindow` focusable — le retour la ferme en premier).
- **Un seul porte-popups par écran** (`PortePopups`) : le registre des
  fenêtres ouvertes (ancrées et centrées), les corps communs gonflés
  de `popup_*.xml` par binding (menu ancré, information,
  confirmation, destructive, saisie, écran texte) — l'ordre du retour
  (popup AVANT feuille/tiroirs) se lit du registre et vit dans
  l'état pur `EtatPopups` (testé JVM) ; la fermeture de cycle de vie
  (`onPause`/`onDestroy`/`onConfigurationChanged`) est UN appel
  idempotent.
- **La carte porte le fond, le cadre reste transparent** : cartes
  centrées en `MaterialCardView` (surface, liseré bord-fin, rayon
  carte — `popup_confirmation`, `popup_saisie`, …), cartes ancrées en
  drawable `carte_popup` (`popup_menu_noeud`) : le `GradientDrawable`
  fabriqué en code par `PopupAncre` d'hier vit désormais en XML
  (décision « le voile/la carte portent le fond » du prompt §4.4).
- Les listes courtes et bornées des popups (entrées de menu, lignes
  de conversation, serveurs) restent des inflations par binding au
  lieu d'un `RecyclerView` (décision 11 : « une liste courte et
  statique peut rester dans le layout », le plafond des menus et des
  conversations est déjà borné par construction).

### Évaluations du lot A8 (cycle de vie et configuration — décision 10)

La décision 10 est posée ; les choix qu'elle laissait ouverts sont tranchés
et consignés ici :

- **`configChanges` de GÉOMÉTRIE seul, sur `EcranAtelier` seul**
  (`orientation|screenSize|smallestScreenSize|screenLayout`) — le filet
  `ArchitectureVuesTest` verrouille la liste EXACTE du manifest et qu'aucune
  autre activité ne déclare de changements. Thème, langue, densité et
  police restent des recréations : l'état d'UI minimal les couvre par
  `onSaveInstanceState`.
- **La décision est une classe PUR JVM** (`DecisionConfiguration`) : donnée
  la classe de géométrie avant/après, elle répond ré-infler/fermer les
  popups/ne JAMAIS fermer l'IDE — le critère §6.10 (un changement de
  géométrie n'appelle pas `fermerIde()`) y est testé dans toute la grille.
- **Même classe de géométrie → PAS de ré-inflation** (économie documentée)
  : un layout neuf de la même classe ne dit rien de plus, le re-câblage
  entier ne servirait à rien — mais les popups se ferment quand même
  (décision 12 : TOUT changement de configuration les ferme).
- **La ré-inflation de la racine** détache le `puits` AVANT (la scène
  celeris et sa couche de popups, jamais reconstruites) et le rattache
  APRÈS ; les zones montées (arborescence, console, aperçu, tiroir agent)
  suivent — instances conservées, ré-amarrées ; les vues neuves se
  re-câblent et se re-posent depuis les états purs (`SessionAtelier`
  n'est pas touché : moteurs LSP, sync, aperçu et écritures survivent).
- **L'état d'UI minimal vit dans `onSaveInstanceState`** : l'export de
  l'`EtatAtelier` (onglets ouverts, actif, points de modification,
  surcouches, section de feuille — aller-retour export/import testé JVM)
  et les BROUILLONS de `EtatPopups` (le même passage : la saisie vit dans
  l'état, le popup n'est jamais restauré). Le build ne s'exporte pas : la
  pilule suit les événements du service (résiduel documenté au rapport).
- **ε (ViewModel) ÉCARTÉE, évaluation motivée** : β couvre la géométrie et
  l'état minimal couvre les recréations restantes — un `ViewModel`
  n'apporterait rien que le Bundle ne donne déjà ; y DÉMÉNAGER la session
  d'IDE (scène, moteurs) est exclu : `SceneEditeur`/`ApercuLayouts` sont
  construits sur le `Context` de l'activité (thème, ressources, fenêtre) —
  les y retenir violerait « jamais de `Context` d'activité retenu » et
  exigerait un protocole de re-attach au moins aussi lourd que la
  ré-inflation de β. La clause « À revisiter » (γ/ε si le coût de
  rechargement mesuré reste pénalisant) demeure.
- **`saveEnabled=false` reste sur les saisies de l'assistant (A4)** : la
  sauvegarde de l'état passe par l'export/import PUR de l'`EtatCreation`
  (A4 : « A8 fera la sauvegarde ») — la vérité reste unique, la
  restauration du framework écrirait un deuxième avis (et marquerait le
  paquet « touché » sans motif) ; les commutateurs des paramètres (A3)
  restent neutralisés pour la même raison d'unicité, leurs ids étant
  dupliqués PAR CONSTRUCTION entre `<include>` (la vérité = `Reglages`,
  re-posée au montage). Le DÉFILEMENT, lui, se restaure par le cadre : les
  racines d'étape de l'assistant deviennent des `NestedScrollView` (le
  `ScrollView` du cadre ne sauve pas sa position ; l'id est porté par
  l'`<include>` du `ViewFlipper`).

## Gabarit cible

```
res/layout/activity_<ecran>.xml          racine de l'écran, <include> par zone
res/layout-w720dp/activity_atelier.xml   variante grand écran (remplace rangeeGrandEcran()/ContexteUi)
res/layout/popup_*.xml                   corps des popups (D3)
res/values/dimens.xml · styles.xml       jetons de dimension et de texte (parité testée)
res/values/themes.xml                    Sombre/Clair : parents Theme.Material3.*, rôles M3 ← Graphite
res/values-w720dp/bools.xml              est_grand_ecran (parité avec EtatAtelier.SEUIL_GRAND_ECRAN_DP)

ui/<zone>/<Zone>.java                    vue mince : binding (+ <merge>), méthodes d'affichage, pas de logique
ui/popup/PopupAncre.java · PopupCentre   gabarits de popup (remplacent Dialogue)
ecrans/<ecran>/Ecran<X>.java             Activity mince (AppCompatActivity) : thème, binding, retour, résultats
ecrans/<ecran>/Controleur<Y>.java        orchestration ; dépend de l'interface Hote<X>, jamais de l'Activity
<domaine>/Etat*.java                     état pur testé JVM (inchangé)
ecrans/Ecrans.java                       navigation typée (Intent, extras, contrats de résultat)
```

Montage d'un binding d'écran (l'ordre thème → inflation est l'invariant
protégé depuis le crash du 2026-11-18) :

```java
// ActiviteJoide extends AppCompatActivity — le type B vient de la
// bibliothèque viewbinding (ADR-083), le reste est à nous.
protected final <B extends ViewBinding> B monter(Function<LayoutInflater, B> inflateur) {
    B binding = inflateur.apply(getLayoutInflater());
    setContentView(binding.getRoot());
    return binding;
}
```

Popup centré gonflé depuis un layout (D3 — `PopupWindow` n'est pas une
vue : aucune vue instanciée en code) :

```java
PopupConfirmationBinding b = PopupConfirmationBinding.inflate(getLayoutInflater());
PopupWindow popup = new PopupWindow(b.getRoot(), largeurPopupPx,
        WindowManager.LayoutParams.WRAP_CONTENT, true); // focusable : le retour le ferme
popup.setBackgroundDrawable(null);      // le fond est porté par le layout (MaterialCardView)
popup.setOutsideTouchable(false);      // confirmation : pas de fermeture au toucher extérieur
popup.showAtLocation(racine, Gravity.CENTER, 0, 0);
// voile : FLAG_DIM_BEHIND sur la fenêtre du popup, ou vue-voile du layout racine
// (le choix est uniformisé par PopupCentre) ; fermeture en onPause/onDestroy.
```

## Analyse des compromis

- **Plus facile** : prévisualiser un écran dans l'éditeur, corriger la mise
  en page sans toucher au Java, tester les contrôleurs avec un faux hôte,
  adapter les tailles par ressources qualifiées, garder la session d'IDE à
  la rotation, bénéficier d'états/accessibilité/listes recyclées fournis,
  supprimer les API dépréciées de navigation.
- **Plus difficile** : habiller Material 3 pour reproduire les maquettes
  Graphite ; `onConfigurationChanged` propre (ré-inflation +
  ré-attachement du `puits` + fermeture des popups) ; revalider la hauteur
  utile de la feuille (bug `6f2e899`) ; modalité, clavier, retour système
  et fuites des popups gérés à la main ; `configChanges` n'attrape pas le
  thème ni la langue ; APK plus gros (delta mesuré à chaque lot).
- **Substitution automatique des widgets** (constat A0) : les 22 layouts
  portent 16 `LinearLayout`, 4 `TextView`, 2 `FrameLayout` ; sous
  `AppCompatActivity` + thème M3, les `TextView` deviennent
  `MaterialTextView` (rendu identique : couleurs et tailles explicites) ;
  `LinearLayout`/`FrameLayout` ne sont pas substitués. Écarts visuels
  inévitables (ripple, élévation tonale, états pressés des composants
  Material) : listés dans les rapports de lots, vérifiés par le propriétaire.
- **À revisiter** : options γ/ε si le coût de rechargement mesuré reste
  pénalisant ; `BottomSheetBehavior` si la feuille actuelle est conservée.

## Conséquences et actions

- ADR-083 passe « Partiellement remplacé par ADR-085 » (§2) ; le kit `ui/`
  décroît au fil des lots ; `Dialogue.java` est supprimée en A7.
- Les lots : **A0** mesurer/verrouiller (fait — `fb1c3b6`) ; **A1** socle
  (cet ADR, dépendances, thèmes M3, `dimens`/`styles`, `ThemesM3Test`,
  `ActiviteJoide`/`monter()`, `Ecrans`, correctif C5 en commit séparé) ;
  **A2** pilote (CallbackMcp, Accueil) ; **A3** Paramètres ; **A4**
  Création ; **A5** vues d'atelier restantes + `RecyclerView` ; **A6**
  racine de l'atelier (`HoteAtelier`, < 500 lignes) ; **A7** popups (fin
  des dialogues, D3) ; **A8** cycle de vie et configuration (FAIT) ; **A9**
  consolidation (une seule voie de thémage, `<merge>`, ADR-085 → Accepted,
  exceptions vides).
- Chaque lot : un commit, `./gradlew check` vert, liste d'exceptions
  réduite, rapport `PHASE-A<n>-RAPPORT.md`, delta d'APK rapporté. Sans
  SDK Android (ADR-015), la validation se limite aux tests JVM, greps
  d'architecture et relecture — jamais de validation simulée.
