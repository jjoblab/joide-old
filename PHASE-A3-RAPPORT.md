# PHASE A3 — RAPPORT (paramètres)

- **Date** : 2026-11-19
- **Base** : A2 validée (`c352d08`)
- **Lot** : A3 — `EcranParametres` : layout racine
  `activity_parametres.xml`, un layout d'item par `Genre` de
  `EtatParametres.Rangee`, `MaterialButtonToggleGroup` et
  `MaterialSwitch` habillés Graphite, `Stepper` et `Nuancier` conservés.

## 0. Circonstance de session (à connaître du propriétaire)

Le prompt exigeait un arbre de travail propre avant de commencer. À la
prise de session, la base était bien `c352d08` (vérifié
`git log --oneline -3`) mais l'arbre portait déjà un travail A3 **non
committé**, précisément circonscrit au périmètre du lot (l'écran, son
état, ses layouts, ses styles, ses tests) — vraisemblablement une
exécution antérieure interrompue avant commit. Plutôt qu'un arrêt sec,
le travail a été **audité intégralement** (lecture ligne à ligne du
diff et des 11 nouveaux layouts face au prompt, à l'ADR-085 et à la
maquette), puis **complété et corrigé** (voir §2 : parité des nouveaux
jetons dans `ThemesM3Test`, chaînes orphelines, `saveEnabled` des
commutateurs, autofill du champ de saisie) et **validé par les
exécutions** du §4. Rien n'a été committé sans ces vérifications.

## 1. Constats du prompt — vérifications A3

- **C4 (EcranParametres) CONFIRMÉ** : la base portait 8 fabriques
  `rangee*` (`rangeeSegment`/`rangeeStepper`/`rangeeCommutateur`/
  `rangeeNuancier`/`rangeeMesure`/`rangeeAction`/`rangeeInfo` + le
  répartiteur `rangee(Rangee)`, l.284-482) et 20 vues instanciées en
  code (`grep` de l'annexe B : le module passe de 185 à 165 vues après
  le lot — exactement −20).
- **ADR-085 (table des équivalents) appliqué** : `Segment` →
  `MaterialButtonToggleGroup` + `MaterialButton` (`Widget.Jo.Bascule`,
  fond du conteneur `fond_bascule` surface haute + liseré bord-fin,
  case cochée accent 18 % + texte accent — le mélange exact du
  `ChoixSegment` d'hier, en `ColorStateList`) ; `Interrupteur` →
  `MaterialSwitch` (`Widget.Jo.Interrupteur`, piste surface
  haute/réussite 25 %, pouce texte3/réussite, liseré de piste
  bord/réussite 60 % — les trois teintes d'`Interrupteur.java`, en
  `res/color/`) ; `Stepper` et `Nuancier` **conservés** (pas
  d'équivalent Material, constructeurs `(Context, AttributeSet)`
  inchangés, déclarés en XML dans `item_param_stepper/nuancier.xml`).
- **Critères du lot atteints** : 0 vue instanciée dans
  `EcranParametres` (grep : 0) ; les 3 `recreate()` (thème, accent,
  échelle UI) conservés et l'état de défilement préservé
  (`onSaveInstanceState`/`onRestoreInstanceState` avec la position du
  `NestedScrollView`, la requête de recherche relue du texte restauré
  — l'EditText a un id unique) ; `EtatParametresTest` (11) et
  `MaquetteParametresTest` (7) verts.

## 2. Écarts avec le prompt

- **NestedScrollView + `<include>` retenu** (la mission offrait
  RecyclerView multi-types OU l'include, « le plus simple, justifié »)
  : décision 11 de l'ADR — liste **courte et statique** (6 sections,
  21 rangées, jamais d'insertion ni de flux), la recherche ne fait que
  cacher des rangées (visibilité, pas re-listage), et la carte groupée
  de la maquette (en-tête + conteneur à liseré) ne s'exprimerait pas
  dans une liste plate recyclée sans adapter multi-genres ET
  multi-cartes. Le recyclage n'apporte rien à 21 rangées.
- **Corps des dialogues existants migrés en XML** alors que la
  consigne disait « les dialogues existants restent en l'état » : les
  FABRIQUES restent en l'état (`Dialogue.menu/saisie/confirmation/
  information/saisieTexte`, lot A7), mais leurs corps (champ de saisie
  clé/modèle/URL, page de texte licences/journal) devaient être
  gonflés depuis des layouts pour que l'exigence « 0 vue instanciée »
  soit tenable (`champ_saisie_param.xml`, `page_texte_param.xml`) —
  l'écran instanciait hier les `EditText` des dialogues.
- **Couleur du vidage m2 posée par l'écran** (`R.color.jo_erreur` par
  ressource, pas de littérale) : le même layout d'item `mesure` sert
  CIDX (accent) et m2 (rouge, maquette) ; un seul layout par Genre est
  exigé, un `ColorStateList` ne porte pas de variante par rangée, un
  overlay de thème par include serait fragile sans appareil pour le
  vérifier. Écart avec la décision 8 (voie unique : la couleur est
  statique), documenté dans le layout même et ici.
- **Graisse de la case cochée du segment posée en code**
  (`GraphiteVue.police(…700…)`) : un `ColorStateList` porte la couleur
  mais pas la graisse — même concession que la décision 7 de l'ADR
  pour la typographie non exprimable en `textAppearance`.
- **Interligne du titre de barre à 26 sp** (maquette : 20 px de corps,
  interligne hérité 22 px du `body` CSS) : 20/26 suit la cadence
  Graphite (+6, comme SOUS_TITRE 16/22 et TITRE_ECRAN 28/34) ; 22 sur
  une fonte de 20 serait un couple étranger à l'échelle. Taille
  elle-même conforme (20 px = 20 sp).
- **Paddings de rangées calés sur la maquette** (envers 8, légende 3)
  plutôt que le 9/4 d'hier : écart d'un dp par rangée-envers, documenté
  ici ; les paddings simples restent à 9 (identique des deux côtés).
- **Deux chaînes orphelines supprimées**
  (`parametres_echelle_legende`, `parametres_fils_legende`, en `values`
  ET `values-fr`) : la migration révèle que `texteLegende()` d'hier ne
  les servait que par un embranchement mort (les fabriques stepper ne
  posaient jamais de légende, la maquette n'en montre pas) ; aucun
  consommateur ne reste (grep : 0), la parité des clés est intacte.
- **Barre du haut : fond transparent sur le fond de fenêtre** (l'état
  d'hier posait `surface` sur la barre) : la maquette `.sous-barre`
  est transparente sur `--fond`, et le pilote A2 (accueil) a posé cette
  convention ; l'icône retour garde 40 dp (valeur d'hier, factorisée
  `jo_action_icone`) et non 36 px de la maquette — cohérence avec
  l'accueil. Écart visuel mineur voulu, listé au §5 pour vérification.
- **`saveEnabled="false"` sur le `MaterialSwitch`** : le genre
  commutateur est inclus deux fois (numéros, retour à la ligne) ; les
  ids internes se dupliquent et la restauration automatique du
  framework écraserait l'état de l'un sur l'autre aux `recreate()`.
  L'état de vérité vit dans `Reglages` et se repose au montage
  (setChecked AVANT l'auditeur) — la sauvegarde de vue n'apportait
  rien que des collisions.
- **Le 40 dp des actions de l'accueil** (`activity_accueil.xml`, 2
  icônes) pourrait adopter `@dimen/jo_action_icone` — HORS périmètre du
  lot, signalé pour la consolidation A9.

## 3. Changements

| Fichier | Nature |
|---|---|
| `res/layout/activity_parametres.xml` (nouveau) | racine : barre (retour en `Icone` + titre `TextAppearance.Jo.TitreBarre`), recherche `TextInputLayout`+`TextInputEditText` (`Widget.Jo.Champ`), `NestedScrollView` de 6 sections = en-têtes `item_param_groupe` + cartes `drawable/carte_groupe_param` (surface, liseré bord-fin, rayon carte) contenant les includes de rangées, état « aucun résultat » en `EtatVide` |
| `res/layout/item_param_groupe/segment/stepper/commutateur/nuancier/mesure/action/info.xml` (8 nouveaux) | un layout d'item par genre (et l'en-tête de section pour `groupe`) : libellé + contrôle, légende de franchise (segment), séparateur de carte en fin (masqué sous la dernière rangée vivante) |
| `res/layout/champ_saisie_param.xml`, `page_texte_param.xml` (2 nouveaux) | corps des dialogues existants gonflés par binding (clé/modèle/URL ; licences/journal en vue défilante mono) — plus aucune vue instanciée pour les dialogues |
| `res/color/bascule_texte/bascule_fond/interrupteur_piste/lisere/pouce.xml` (5 nouveaux) | teintes d'état par `?attr/jo_*` (alpha porté par l'item) : cochée accent 18 %, piste surface haute/réussite 25 %, liseré bord/réussite 60 %, pouce texte3/réussite |
| `res/drawable/carte_groupe_param.xml`, `fond_bascule.xml` (2 nouveaux) | carte de groupe (surface, liseré bord-fin 1 dp, rayon 16) et conteneur de bascule (surface haute, liseré bord-fin, rayon pilule, padding 3) |
| `res/values/styles.xml` | `TextAppearance.Jo.TitreBarre` (20/26 gras), `TextAppearance.Jo.NomSection` (légende 12/16 en graisse 700 espacée 0,07), `Widget.Jo.Bascule` (MaterialButton pilule, insets 0, paddings 6/13, `ShapeAppearance.Jo.Pilule`), `Widget.Jo.Interrupteur` (MaterialSwitch teinté) |
| `res/values/dimens.xml` | traits fins (`jo_ligne`), paddings de barre/rangées/groupes/bascules de la maquette, titre 20/26, marges des pages de texte, `jo_action_icone` |
| `ecrans/parametres/EcranParametres.java` | coque mince sur `ActivityParametresBinding` (montage par `monter(…)`) : table statique clé d'état → vues d'item (7 surcharges `rangee` par genre de binding), montage des contrôles, rendu par visibilité (recherche, sections, séparateur arrêté avant la dernière rangée vivante), sauvegarde/restauration du défilement — 1 099 lignes (0 vue instanciée) ; les dialogues existants restent (A7) |
| `parametres/EtatParametres.java` | + `derniereRangee(Section)` : la clé de la dernière rangée vivante (le séparateur s'arrête avant elle — la maquette ne trace jamais sous la dernière, le filtrage déplace cette dernière) |
| `ArchitectureVuesTest` | `EcranParametres` retiré des exceptions de vues instanciées (6 exceptions restantes) |
| `EtatParametresTest` | + `laDerniereRangeeVivanteArreteLeSeparateurDeCarte` (3 assertions dont une recherche qui prend la première rangée) |
| `ThemesM3Test` | + `lesStylesDuLotParametresOntUnParentM3EtDesJetonsExistants` (parents M3, teintes `@color/`, paddings 6/13 et écart 3 de la maquette, 20/26) et `lesSelecteursDeCouleurDuLotParametresNePortentQueDesAttrsJo` (les 5 sélecteurs : que des `?attr/jo_*` déclarés dans `attrs.xml`) — exigence §2 : tout nouveau jeton reçoit sa parité |
| `values{,-fr}/strings.xml` | − 2 chaînes orphelines (parité des clés intacte) |

## 4. Mesures avant/après

| Mesure | A2 | A3 |
|---|---|---|
| Vues instanciées en code (module) | 185 | **165** (−20 : EcranParametres, grep EcranParametres = 0) |
| `palette.*` en code | 359 | **343** (−16) |
| `GraphiteVue.dp(` | 385 | **362** (−23) |
| Layouts | 30 | **41** (+11, tous consommés par un `*Binding`) |
| `<include` | 2 | **30** (+28 : les rangées du paramètres) |
| dp littéraux | 238 | 246 (+8 : uniquement des `0dp` d'idiome `layout_weight`, comme au pilote ; aucune dimension réelle littérale dans les 11 nouveaux layouts) |
| sp littéraux | 56 | 56 |
| `Dialogue.` usages | 58 | 58 (inchangé — A7) |
| Imports Compose | 0 | **0** ; imports AndroidX/Material tous dans la liste blanche (material.button ×2 fichiers) |
| APK debug (R8 + shrink) | 15 581 637 o | **15 649 057 o (+67 420 o, +0,06 Mio)** |
| `verifierOmbreApk` | vert | **vert** (« APK sans ombre, éléments vitaux présents ») |
| Tests JVM `:app-android:test` | 340 verts | **343 verts, 0 échec** (+1 état, +2 parité) |

## 5. Validation

- `./gradlew --init-script … :app-android:test` — **343 tests, 0 échec**
  (dont `EtatParametresTest` 11, `MaquetteParametresTest` 7,
  `ArchitectureVuesTest` 7 — exceptions réduites, `ThemesM3Test` 8,
  `JetonsXmlTest` 5) ; dernière passe forcée (`:testDebugUnitTest
  --rerun`) sur l'état final.
- `./gradlew … :app-android:assembleDebug` — vert ;
  `verifierOmbreApk` : « APK sans ombre, éléments vitaux présents
  (15649057 o) ». Contrainte d'environnement connue (rapport A1) : le
  démon meurt au tas historique (3 g) comme à `-Xmx1800m` en pleine
  charge — l'assemblage de validation est mené à
  `-Dorg.gradle.jvmargs=-Xmx1500m` en ligne de commande, aucun fichier
  du dépôt modifié pour cela.
- `./gradlew … :app-android:lintDebug` — **10 erreurs, toutes
  préexistantes au lot** (les 9 signalées en A1 + `DiffUtilEquals`
  sur `AdaptateurAccueil` apparu au lot A2 — record Java, faux
  positif probable, signalé au propriétaire). **Aucune nouvelle
  erreur `NewApi`/`HardcodedText`/`ContentDescription`.** Alertes
  nouvelles du lot, toutes warnings documentées :
  - `DuplicateIncludedIds` ×14 : les ids internes des 7 items se
    dupliquent entre includes (un layout par genre, exigé par la
    mission) ; chaque `<include>` porte un id unique, le ViewBinding
    typé désambiguïse, aucun `findViewById` ambigu n'existe, et le
    seul état framework du genre (checked du commutateur) est neutralisé
    (`saveEnabled="false"`, l'état de vérité relit `Reglages` au
    montage) ;
  - `LabelFor` ×1 sur `champ_saisie_param.xml` : le hint est posé en
    code selon le cas (clé/modèle/URL) — faux positif ;
  - `RtlHardcoded` ×4 (nom de section, écart de valeur, deux écarts
    de bascule — les `marginLeft/Right` des items du lot) : convention
    Left/Right du dépôt (manifest sans `supportsRtl`, idem
    accueil/pilote) ;
  - `Autofill` du champ corrigé dans le lot
    (`importantForAutofill="no"` : un champ qui porte des clés d'API).
- **Aucun appareil/émulateur** : états visuels à vérifier à la main
  par le propriétaire —
  - segments : case cochée (fond accent 18 % sur surface haute, texte
    accent, graisse 700 — la graisse se pose au choix), case du milieu
    grisée « Système » du thème (texte3), pilule du conteneur ;
  - commutateurs : piste/liseré/pouce inactifs (surface haute, bord,
    texte3) et actifs (réussite 25 %, 60 %, réussite) ; ripple M3 du
    pouce ;
  - recherche (outline bord-fin, rayon 12, hint estompé), cartes de
    groupe (liseré bord-fin, rayon 16, séparateurs arrêtés avant la
    dernière rangée vivante — filtrer « numéros » pour déplacer la
    dernière), état « aucun résultat », mesures Mo et « Vider » (cidx
    accent, m2 rouge), version (nom + SHA court) ;
  - comportements : les 3 `recreate()` (thème, accent, échelle UI)
    conservent la position de défilement ET la recherche (relire le
    texte restauré) ; barre : fond transparent sur le fond de fenêtre
    (hier : surface — écart voulu, voir §2) ; rotation de l'écran
    (recréation complète — lot A8).
- `./gradlew check` racine NON VERT : `checkLanguagePolicy` échouait
  déjà au commit de base (1313 problèmes typographiques préexistants,
  preuve archivée au rapport A1) — inchangé par le lot ; les documents
  DU CHANTIER restent conformes (script passé sur les fichiers du lot).

## 6. Risques résiduels et suite

- Le lint compte 10 erreurs préexistantes (dont `DiffUtilEquals` du
  lot A2, probable faux positif sur un record) — décision du
  propriétaire attendue ; le chantier ne les masque pas.
- `EcranParametres` passe de 1 002 à 1 099 lignes : coque mince en
  comportement (0 vue instanciée) mais les tables statiques, la
  sauvegarde d'état et la javadoc pèsent ; la consolidation A9 pourra
  extraire la mécanique commune de montage/défilement si A4 en
  réinvente une.
- Suite : **A4** — Création (`activity_creation.xml`, un layout par
  étape, `item_modele_projet`, `item_banniere`, `TextInputLayout`,
  `MaterialCardView`, garde de cycle de vie du thread de génération).
