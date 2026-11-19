# PHASE A4 — RAPPORT (création)

- **Date** : 2026-11-19
- **Base** : A3 validée (`8835a90`), arbre PROPRE à la prise de session
  (`git status --short` vide, `git log --oneline -3` = `8835a90`).
- **Lot** : A4 — `EcranCreation` : layout racine `activity_creation.xml`,
  un layout D'ÉTAPE par `EtatCreation.Etape` (MODELE, IDENTITE,
  RECAPITULATIF), `item_modele_projet`, `item_banniere`, `TextInputLayout`
  (identité), `MaterialCardView` (modèles), `Chip` (catégories et modules),
  thread de génération sous l'exécuteur de l'écran avec garde de cycle de
  vie.

## 1. Constats du prompt — vérifications A4

- **C4 (EcranCreation entièrement programmatique) CONFIRMÉ** : la base
  portait 34 vues instanciées en code (grep de l'annexe B :
  `etapeBarre`, `carteModele`, `banniere` — `corpsModele`/`corpsIdentite`/
  `corpsRecap` reconstruisaient le corps à chaque `rendre()`), 879 lignes.
  Après le lot : **0 vue instanciée** (grep : 0), 746 lignes.
- **I8 (concurrence UI) CONFIRMÉ et corrigé pour cet écran** : le thread
  de génération (~l.819 de la base) partait en `new Thread("joide-creation")`
  avec un `runOnUiThread` SANS garde ; il court désormais sous l'exécuteur
  `fond` de l'écran (fil démon `joide-creation`, motif A3), le retour au
  fil UI vérifie `isDestroyed() || ecran == null` et `onDestroy()` fait
  `shutdownNow()` + met le binding à MUET (`ecran = null`).
- **ADR-085 (table des équivalents) appliqué** : `Bouton` →
  `MaterialButton` (`Widget.Jo.Button.Primaire/Texte` A2), `Carte` →
  `MaterialCardView` (cartes de modèle : surface, liseré bord-fin 1 dp,
  élévation 3, rayon du thème ; la carte tonale du modèle choisi pose
  `ChoixCarte.tonaleHaut/tonaleBord` au bind — motif A2, le dégradé du
  kit devient un APLAT), `Champ` → `TextInputLayout`+`TextInputEditText`
  (`Widget.Jo.Champ` A2 ; étiquette 12 demi-grasse au-dessus —
  `TextAppearance.Jo.Etiquette`), `Puce` → **`Chip`/`ChipGroup`**
  (`Widget.Jo.Puce`, teintes de `ChoixPuce` en `res/color` par
  `?attr/jo_*` : cochée = accent 16 % + texte accent + bord accent 45 %,
  coche Material masqué — la maquette ne le montre pas), `Segment` →
  `MaterialButtonToggleGroup`+`MaterialButton` (`Widget.Jo.Bascule` A3
  pour le minSdk 28/30). `Icone` (retour, coche) et `TuileProjet` restent
  du kit (Canvas, sans équivalent Material — ADR-085).

## 2. Écarts avec le prompt (et bugs latents corrigés)

- **`ViewFlipper` retenu** (la mission offrait ViewFlipper OU
  `<include>`) : les trois layouts d'étape sont INCLUS dans le flipper de
  la racine — l'ordinal d'`EtatCreation.Etape` EST l'indice affiché
  (`setDisplayedChild(ordinal)`), exactement un corps visible PAR
  CONSTRUCTION (aucune visibilité à maintenir, aucune désynchro
  possible) ; chaque étape garde son propre layout
  (`creation_etape_*.xml`) lisible isolément. Les `<include>` seuls
  auraient exigé une gestion de visibilité manuelle des trois corps.
- **Galerie SANS RecyclerView** : liste très courte et BORNÉE par les
  assets (4 modèles livrés), la recherche et la catégorie ne font que
  changer les membres — les cartes se gonflent par binding
  (`item_modele_projet`) dans une colonne (motif A3 des corps de
  dialogues) ; un RecyclerView SOUS ScrollView serait un anti-patron et
  le comportement d'hier (re-gonfler la zone) est conservé. Les lignes de
  fiche (`item_ligne_recap`), d'arborescence (`item_ligne_arbo`) et les
  puces de module (`item_puce_module`) suivent le même motif (≤ 5 lignes,
  bornées, l'étape 3 est une photographie).
- **Retraits latéraux du corps RESTAURÉS (.corps 4/20/8 de la maquette)** :
  le code d'hier ne posait AUCUN padding horizontal sur `corpsEtape` —
  recherche, puces et cartes touchaient les bords de l'écran (la maquette
  qui fait foi insette de 20 px). Écart consigné : la maquette prime.
- **La recherche survit aux changements de catégorie** : le code d'hier
  RECONSTRUISAIT le champ de recherche vide à chaque clic de catégorie
  (l'état gardait pourtant le filtre — le champ perdu montrait une
  galerie filtrée « sans raison »). Le champ, immobile dans le layout,
  ne perd plus ni texte ni focus ; seul la galerie se re-gonfle
  (`majModeles`, comme `majListeModeles` d'hier).
- **Le paquet suit le nom sous les yeux de l'utilisateur** :
  `EtatCreation.changerNom` re-défiait déjà le paquet tant qu'il n'était
  pas touché, mais le champ ne se re-pose jamais — affichage périmé.
  Nouveau getter pur `EtatCreation.suitPaquet()` (testé JVM) ; l'écran
  re-pose le champ en silence (garde `poseEnCours` contre les écouteurs
  de saisie). Comportement d'hier : bloqué sur la première valeur.
- **Coche du modèle choisi RÉPARÉ** : `addAccessoire(new Icone(this))`
  donnait au coche un `wrap_content` de vue SANS taille (0×0 — invisible
  depuis le lot I) ; le layout lui rend sa taille de maquette (20 dp,
  glyphe coche accent, `INVISIBLE` hors sélection).
- **`onBackPressed` déprécié → `OnBackPressedDispatcher`** (décision 13) :
  la garde remonte d'une étape, se désarme sur la première et laisse le
  cadre fermer l'assistant ; l'`Intent` vers l'atelier devient
  `Ecrans.atelier(...)` (navigation typée).
- **Puces de catégorie en `selectionRequired="false"`** : re-cliquer la
  catégorie choisie ramène à « Toutes » (comportement du kit d'hier ;
  l'état `null` re-coche « Tous » au rendu).
- **Écarts visuels assumés (identité = code d'hier, documentés)** : le
  bouton « Créer le projet » reste PRIMAIRE accent (la maquette le
  montre vert — décision du lot I conservée) ; « Retour » reste
  `Widget.Jo.Button.Texte` (texte accent, la variante TEXTE du kit — la
  maquette montre un bouton fantôme texte2+bord) ; les bannières n'ont
  pas d'icône (le code d'hier n'en posait pas ; mélanges exacts
  alerte/accent 10 % sur fond, liseré 35 %, posés au bind) ; les puces du
  kit gardent NOTE 13/18 et paddings 14/7 (la maquette montre 11.5 px et
  5/9) ; pas de comptes sur les puces de catégorie (la spéc ADR-É4 ne
  les demande pas, le code d'hier ne les posait pas) ; recherche et
  champs à `Widget.Jo.Champ` (rayon 12 uni au lieu du 14 de cette
  maquette, hauteur M3 — convention A2/A3) ; segment minSdk = motif A3
  (`fond_bascule`, rayon pilule/padding 3 au lieu de 12/4 de cette
  maquette — composant unifié) ; graisse 600 des étiquettes, des
  puces cochées et du libellé d'étape active rapprochée par
  `sans-serif-medium`/gras du cadre ou posée au choix (concessions
  documentées A3, décision 7) ; valeurs de fiche en gras (700 ≈ 600
  d'hier) ; écarts M3 inévitables : ripple des cartes/puces/boutons,
  état désactivé Material, élévation tonale nulle de la fiche.
- **`saveEnabled="false"` partout dans l'assistant** (champs, puces,
  segment) : l'état de vérité vit dans `EtatCreation`, la rotation
  RÉINITIALISE l'assistant exactement comme aujourd'hui (le layout
  pose des ids : sans cette neutralisation, le framework restaurerait
  des textes/sélections sur un état neuf et incohérent — A8 apportera
  la vraie sauvegarde).

## 3. Changements

| Fichier | Nature |
|---|---|
| `res/layout/activity_creation.xml` (nouveau) | racine : sous-barre (retour 36 dp + glyphe 20, titre 17 gras, compte « 1/3 » en `pilule_compte`), barre d'étapes (3 ronds + 2 jalons, états faite/active/à venir par `res/color` + `rond_etape` sélecteur), **ViewFlipper** des trois étapes, ligne d'erreur, pied (Retour fantôme + Continuer/Créer) |
| `res/layout/creation_etape_modele.xml` (nouveau) | étape 1 : recherche `TextInputLayout` (`Widget.Jo.Champ`), catégories en `ChipGroup` singleSelection (4 `Chip` `Widget.Jo.Puce`), colonne des cartes + « aucun modèle » |
| `res/layout/creation_etape_identite.xml` (nouveau) | étape 2 : étiquettes + champs Material (nom corps / paquet monospace), modules en `ChipGroup`, minSdk en `MaterialButtonToggleGroup` (`Widget.Jo.Bascule`), note D4, `include` de la bannière de stockage ; blocs modules/paquet/minSdk à visibilité |
| `res/layout/creation_etape_recapitulatif.xml` (nouveau) | étape 3 : fiche `MaterialCardView` (surface, liseré bord-fin, élévation 0) + colonne des lignes, étiquette, arborescence (carte puits), `include` de la bannière de franchise |
| `res/layout/item_modele_projet.xml` (nouveau) | carte de modèle : `MaterialCardView` + `TuileProjet` + nom/description/franchise (échelles du kit, une ligne ellipse) + coche accent 20 dp |
| `res/layout/item_banniere.xml` (nouveau) | bannière du bas d'étape (fond mélangé posé au bind — décision 8) ; incluse deux fois (ids distincts) |
| `res/layout/item_ligne_recap.xml`, `item_ligne_arbo.xml`, `item_puce_module.xml` (3 nouveaux) | ligne de fiche (séparateur + clé/valeur), ligne d'arborescence monospace, puce de module `Chip` |
| `res/color/puce_fond/texte/bord.xml`, `etape_num_texte.xml`, `etape_libelle.xml`, `etape_jalon.xml` (6 nouveaux) | teintes d'état par `?attr/jo_*` (alpha porté par l'item, motif A3) : puce cochée accent 16 %/45 %, numéro d'étape (sur-réussite/sur-accent/texte3), libellé (texte/texte2/texte3), jalon (réussite/bord) |
| `res/drawable/rond_etape.xml` + `rond_etape_faite/active/todo.xml` (4 nouveaux) | le rond du numéro d'étape suit l'état (oval plein réussite/accent ou transparent liseré bord) |
| `res/drawable/carte_arbo_creation.xml` (nouveau) | carte puits de l'arborescence (liseré bord-fin, rayon 14) |
| `res/values/styles.xml` | `TextAppearance.Jo.Etiquette` (légende + sans-serif-medium), `TextAppearance.Jo.NumEtape` (11/16 gras), `Widget.Jo.Puce` (Chip.Filter teinté `ChoixPuce`, paddings 14/7, coche masqué) |
| `res/values/dimens.xml` | jetons du lot A4 (barre 12/10/16/6, étapes 20/7/2/8, corps 4/20/8, écarts d'hier 6/8/10/12, fiche 14/4, arbo rayon 14 + padding 12, bannière 12/10, coche 20, puces 14/7/6) |
| `ecrans/creation/EcranCreation.java` | coque mince sur `ActivityCreationBinding` (montage par `monter(…)`, sous-bindings d'étapes) : câblage unique, rendu par re-pose (ViewFlipper à l'ordinal, barre d'étapes par états activé/sélectionné), galerie/puces/fiche/arborescence GONFLÉES par binding, retour système par `OnBackPressedDispatcher`, génération sur l'exécuteur `fond` avec `isDestroyed()`/binding muet après `onDestroy` (I8) — **0 vue instanciée** ; 746 lignes (879 avant) |
| `creation/EtatCreation.java` | + `suitPaquet()` : le paquet suit-il encore le nom (getter pur, la vue s'en sert pour re-poser le champ à chaque frappe) |
| `ArchitectureVuesTest` | `EcranCreation` retiré des exceptions de vues instanciées (3 restantes : atelier + 2 contrôleurs) |
| `EtatCreationTest` | `defautsUniquesEtDerivesDuModele` étendu : `suitPaquet()` vrai à la sélection et après renommage, faux après saisie directe du paquet (le paquet touché ne suit plus) |
| `ThemesM3Test` | + `lesStylesDuLotCreationOntUnParentM3EtDesJetonsExistants` (parents M3, teintes `@color/`, paddings 14/7, 11/16, rayon arbo 14) ; le test des sélecteurs devient `lesSelecteursDeCouleurNePortentQueDesAttrsJo` (11 sélecteurs : 5 du lot A3 + 6 du lot A4) |
| `values{,-fr}/strings.xml` | + `creation_desc_retour` (parité bilingue) |

## 4. Mesures avant/après

| Mesure | A3 | A4 |
|---|---|---|
| Vues instanciées en code (module) | 165 | **131** (−34 : EcranCreation, grep EcranCreation = 0) |
| `palette.*` en code | 343 | **307** (−36 ; 11 restent dans l'écran : bannières, arborescence, carte tonale — couleurs calculées, décision 8) |
| `GraphiteVue.dp(` | 362 | **297** (−65 ; 2 restent : liseré 1 dp et rayon 12 de la bannière) |
| Layouts | 41 | **50** (+9, tous consommés par un `*Binding`) |
| `<include` | 30 | **37** (+5 balises réelles : 3 étapes + 2 bannières ; +2 occurrences du mot dans des commentaires) |
| dp littéraux | 246 | 258 (+12 : 11 idiomes `0dp` de `layout_weight`/élévation nulle + l'élévation 3 dp des cartes, convention A2) |
| sp littéraux | 56 | 56 |
| `Dialogue.` usages | 58 | 58 (inchangé — A7 ; aucun nouveau dialogue, D3) |
| Imports Compose | 0 | **0** ; imports AndroidX/Material dans la liste blanche (`androidx.activity`, `material.chip/button`, `material.button`) |
| Lignes d'écran | 879 | 746 |
| APK debug (R8 + shrink) | 15 649 057 o | **15 743 732 o (+94 675 o, +0,09 Mio)** — `Chip`/`ChipGroup` entrent à l'APK (R8 en garde les classes) |
| `verifierOmbreApk` | vert | **vert** (« APK sans ombre, éléments vitaux présents (15743732 o) ») |
| Tests JVM `:app-android:test` | 343 verts | **344 verts, 0 échec** (+1 parité du lot A4) |

## 5. Validation

- `./gradlew --init-script … :app-android:test` — **344 tests, 0 échec**
  (dont `EtatCreationTest` 9, `MaquetteCreationTest` 5 — la capture
  contre `creation-projet.html`, `ArchitectureVuesTest` 7 — exceptions
  réduites, `ThemesM3Test` 9, `JetonsXmlTest` 5) ; dernière passe forcée
  (`:app-android:testDebugUnitTest --rerun`) sur l'état final :
  344/0/0.
- `./gradlew … :app-android:assembleDebug` — vert ;
  `verifierOmbreApk` : « APK sans ombre, éléments vitaux présents
  (15743732 o) ». Contrainte d'environnement connue (rapports A1/A3) :
  le démon Gradle meurt au tas historique — l'assemblage de validation
  (et lint) sont menés à `-Dorg.gradle.jvmargs=-Xmx1500m` en ligne de
  commande, aucun fichier du dépôt modifié pour cela.
- `./gradlew … :app-android:lintDebug` — **10 erreurs, toutes
  préexistantes au lot** (la liste du rapport A3 exactement : 9 de A1 +
  `DiffUtilEquals` du lot A2). **Aucune nouvelle erreur
  `NewApi`/`HardcodedText`/`ContentDescription`.** Nouvelles alertes du
  lot, toutes warnings documentés :
  - `RtlHardcoded` ×13 (marges/paddings Left/Right des layouts A4) :
    convention Left/Right du dépôt (manifest sans `supportsRtl`, idem
    accueil A2 et paramètres A3) ;
  - `SetTextI18n` ×1 (`EcranCreation.java:471` : l'indentation
    `"  ".repeat(profondeur)` de l'arborescence — concaténation
    d'espaces d'indentation, exactement le code d'hier) ;
  - pas de `DuplicateIncludedIds` nouveau : les deux includes de
    `item_banniere` portent des ids distincts et le binding typé
    désambiguïse (les 14 des Paramètres restent seuls).
- **Aucun appareil/émulateur** : états visuels à vérifier à la main par
  le propriétaire —
  - étape 1 : barre (titre 17 gras, pilule « 1/3 »), barre d'étapes
    (rond accent + libellé gras sur l'étape active, ronds réussite + ✓
    sur les faites, jalons réussite/bord), recherche (outline bord-fin,
    rayon 12, hint estompé), puces de catégorie (surface haute + bord
    fin ; cochée : accent 16 %, bord accent 45 %, texte accent — sans
    coche Material), cartes de modèle (élévation 3, tuile tonale par
    type, franchise en alerte / note en estompé, coche accent 20 dp
    réparé), carte choisie (APLAT accent 8 % + bord accent 35 %),
    « aucun modèle » ;
  - étape 2 : étiquettes demi-grasses, champs (nom corps, paquet
    monospace), puces de module (bascule, refus motivé en ligne rouge),
    segment minSdk (case cochée accent 18 %), bannière d'information
    (accent 10 %, liseré 35 %), erreurs en ligne, bouton Continuer
    désactivé sans validité ; le paquet suit le nom tant qu'il n'est pas
    touché ;
  - étape 3 : fiche (lignes clé/valeur, séparateurs bord-fin),
    arborescence (puits, racine texte, annotations en gris de
    commentaire), bannière d'alerte (franchise répétée), bouton « Créer
    le projet » ;
  - comportements : retour système remonte d'une étape / quitte depuis
    l'étape 1 (dispatcher) ; la recherche survit aux clics de catégorie ;
    rotation = assistant réinitialisé (comme aujourd'hui — A8) ;
    création atomique : l'échec reste sur l'étape 3 avec l'erreur
    nominale ; double-clic sur « Créer » pendant l'écriture (voir §6) ;
    ripple/états Material (écarts inévitables, listés en §2).
- `./gradlew check` racine NON VERT : `checkLanguagePolicy` échouait déjà
  au commit de base (1313 problèmes typographiques préexistants, preuve
  archivée au rapport A1) — inchangé par le lot ; les documents du
  chantier restent conformes.

## 6. Risques résiduels et suite

- **Double-clic sur « Créer le projet » pendant l'écriture** : le
  comportement d'hier est conservé (le bouton reste actif pendant la
  génération) — l'exécuteur à fil unique sérialise les écritures, mais
  deux clics rapprochés créent deux projets (ids uniques, l'échec le cas
  échéant s'affiche). La désactivation du bouton pendant le vol serait
  un changement de comportement : laissé à la décision du propriétaire
  (une ligne, signalé ici).
- `EcranCreation` reste à 746 lignes : coque mince en comportement
  (0 vue instanciée, logique dans `EtatCreation`), mais le gonflage des
  quatre contentions (cartes, puces, fiche, arborescence) + la javadoc
  pèsent ; la consolidation A9 pourra extraire la mécanique de
  gonflage-par-binding si A5/A6 en réinventent une.
- `ViewFlipper` mesure les trois étapes à chaque passe (contenus très
  courts, coût borné) ; acceptable pour un assistant de trois étapes.
- Le kit `Puce`/`Champ`/`Carte`/`Segment`/`Bouton` restent utilisés par
  l'atelier/les autres vues (A5-A7) — leur retrait vient avec leurs lots.
- Suite : **A5** — vues d'atelier restantes (`VueArbre`, `VueConsole`,
  `VueApercu`, listes en `RecyclerView`).
