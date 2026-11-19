# PHASE A2 — RAPPORT (pilote : rappel MCP + accueil)

- **Date** : 2026-11-19
- **Base** : A1 validée (`61133b2`)
- **Lot** : A2 — pilote des écrans simples (`EcranCallbackMcp`,
  `MainActivity`) : layouts racine + liste recyclée + composants Material
  habillés Graphite.

## 1. Changements

| Fichier | Nature |
|---|---|
| `res/layout/activity_callback_mcp.xml` (nouveau) | racine du rappel OAuth : `MaterialCardView` (surface, liseré bord-fin, rayon du thème) + `MaterialButton` tonal |
| `res/layout/activity_accueil.xml` (nouveau) | racine de l'accueil : barre d'application (titre d'écran, salutation, actions recherche/⋮ en `Icone`), champ de recherche `TextInputLayout`+`TextInputEditText` (`Widget.Jo.Champ`), ligne de progression, `RecyclerView` |
| `res/layout/item_projet_carte.xml` (nouveau) | carte projet : `MaterialCardView` (surface, bord-fin 1 dp, élévation 3 dp) + `TuileProjet` + lignes nom/méta + accessoire ⋮ |
| `res/layout/item_projet_reprise.xml` (nouveau) | carte de reprise : mêmes jetons, chevron d'ouverture ; les couleurs TONALES (dégradé/bord accent) se posent au bind (décision 8 : l'accent du nuancier décide) |
| `res/layout/item_section_entete.xml`, `item_actions_accueil.xml`, `item_etat_vide_accueil.xml`, `item_aucun_resultat.xml` (nouveaux) | en-tête de section (compteur en pilule `@drawable/pilule_compte`), rangée d'actions (`Widget.Jo.Button.Primaire/Tonal`), état vide (`EtatVide` du kit, boutons Material adoptés), « aucun résultat » |
| `res/drawable/pilule_compte.xml` (nouveau) | pilule du compteur : `?attr/jo_surface_haute` + rayon Graphite |
| `styles.xml` | `Widget.Jo.Button.Primaire/Tonal/Texte` (52 dp, retrait 24, corps 15/22, couleurs de `ChoixBouton` : TONAL = surface-haute/texte — M3 ne sait pas le dire seul) + `Widget.Jo.Champ` (`TextInputLayout` OutlinedBox : surface, bord-fin, rayon 12) |
| `dimens.xml` | bouton (52/24), paddings de la barre d'application et du contenu, écarts de listes, pilule — les valeurs du code d'hier |
| `ui/TuileProjet.java` (nouveau) | la tuile tonale extraite du kit `Carte` : déclarable en XML (`jo_tuile_type` dans `attrs.xml`, motif `JoIcone`), glyphe dossier dessiné au `Canvas`, couleurs calculées depuis la palette |
| `ui/EtatVide.java` | adoption des actions déclarées en XML (`onFinishInflate`/`onAttachedToWindow` : la rangée reste en fin de zone, l'ordre du kit est préservé) + `addAction(View)` (les boutons Material) |
| `accueil/ElementAccueil.java` (nouveau) | éléments de liste scellés (records) — la vue pure d'`EtatAccueil` composée en éléments recyclables |
| `accueil/AdaptateurAccueil.java` (nouveau) | `ListAdapter`/`DiffUtil`, view-holders = bindings d'items, hôte étroit `Auditeur` (décision 3) ; la méta de carte se formule ici (texte de présentation) |
| `MainActivity.java` | coque mince : montage par `monter(ActivityAccueilBinding::inflate)`, câblage, `LinearLayoutManager`, navigation typée (`Ecrans.creation/parametres/atelier`) — **0 vue instanciée** ; les dialogues existants restent (A7) |
| `ecrans/mcp/EcranCallbackMcp.java` | coque mince sur `ActivityCallbackMcpBinding` — **0 vue instanciée** |
| `gradle/libs.versions.toml`, `build.gradle.kts` | `androidx.recyclerview:recyclerview` 1.4.0 entre au lot qui l'importe (prévu par l'ADR) |
| `ArchitectureVuesTest` | exceptions des écrans : `MainActivity` et `EcranCallbackMcp` **retirés** (6 restantes) |
| `values{,-fr}/strings.xml` | `accueil_desc_*` (parité bilingue) |
| `JetonsXmlTest` | filtre `jo_tuile_*` (attribut de VUE, même règle que `jo_icone_*` — pas un jeton de thème) |

## 2. Mesures avant/après

| Mesure | A1 | A2 |
|---|---|---|
| Vues instanciées en code | 203 | **185** (−18 : MainActivity 14, EcranCallbackMcp 4) |
| Layouts | 22 | **30** (8 nouveaux, tous consommés par un `*Binding`) |
| `palette.*` en code | 374 | **359** (−15) |
| `GraphiteVue.dp(` | 414 | **385** (−29) |
| APK debug | 15 172 977 o | **15 581 637 o (+408 660 o, +0,39 Mio)** — recyclerview |
| `verifierOmbreApk` | vert | **vert** |
| Tests JVM | 340 verts | **340 verts** (0 échec) |
| Imports Compose | 0 | **0** |

## 3. Validation

- `./gradlew :app-android:test` — 340 tests, 0 échec (dont
  `EtatAccueilTest`, `MaquetteAccueilTest`, `ArchitectureVuesTest` avec la
  liste réduite, `JetonsXmlTest`, `ThemesM3Test`).
- `./gradlew :app-android:assembleDebug` — vert ;
  `verifierOmbreApk` : « APK sans ombre, éléments vitaux présents ».
- Aucun appareil : états visuels à vérifier à la main par le
  propriétaire (le pilote est volontairement l'écran de validation de
  l'habillage Graphite avant généralisation) :
  - accueil : barre d'application, recherche (fond/rounded 12/liseré
    bord-fin, hint estompé), cartes (élévation 3 dp, liseré bord-fin,
    tuile tonale par type), carte de reprise (fond/bord accent —
    aplats du dégradé), pilule du compteur, état vide (boutons Material
    dans la zone en pointillés), « aucun résultat » ;
  - rappel MCP : carte centrée sur voile, bouton tonal.
  - Écarts inévitables documentés (habillage Material) : ripple des
    boutons/cartes cliquables (le kit dessinait un état appuyé uni) ;
    carte de reprise : le DÉGRADÉ accent 8 % → surface devient un APLAT
    accent 8 % (bord accent 35 % exact) ; liseré « élévation » 5 % blanc
    des cartes en sombre non exprimé par `MaterialCardView` ;
    état appuyé des champs (liseré accent au focus M3 vs liseré
    constant du kit) ; `MaterialButton` a des insets verticaux propres
    (hauteur min 52 dp conservée).
  - Comportements : rotation de l'accueil (l'état de liste est
    re-soumis à `recharger`, la position de défilement suit le
    `RecyclerView` — pas encore de sauvegarde explicite, lot A8) ;
    clavier sur le champ de recherche.

## 4. Écarts avec le prompt

- Le prompt nommait `item_etat_vide_accueil` avec boutons du kit : les
  boutons sont des `MaterialButton` (`Widget.Jo.Button.*`), l'état vide
  reste la vue du kit CONSERVÉE (pas d'équivalent Material — table de
  l'ADR) avec adoption des actions XML.
- La « rangée d'actions » vit en item de liste (`item_actions_accueil`),
  recyclable comme les autres éléments — plus une vue reconstruite à
  chaque `rendre()`.

## 5. Risques résiduels et suite

- Le liseré des cartes (sombre) et le dégradé de la reprise sont les
  seuls pixels non fidèles du pilote (listés en §3) — décision du
  propriétaire souhaitée : les accepter comme habillage Material ou
  demander un `MaterialCardView` custom background en consolidation.
- Suite : **A3** — Paramètres (8 fabriques `rangee*` → `RecyclerView`
  multi-genres ou `NestedScrollView`, `MaterialButtonToggleGroup`,
  `MaterialSwitch`, `Stepper`/`Nuancier` conservés).
