# PHASE A5 — RAPPORT (vues d'atelier restantes)

- **Date** : 2026-11-19
- **Base** : A4 validée (`ef2288a`), arbre de travail **portant un travail
  A5 NON committé** à la prise de session (circonstance au §0).
- **Lot** : A5 — `VueArbre`, `VueConsole`, `VueApercu` + les listes de la
  feuille : `vue_arbre.xml`, `vue_console.xml` (+ `item_phase`,
  `item_ligne_console`, `carte_probleme_console`),
  `vue_apercu.xml` ; `RecyclerView + ListAdapter/DiffUtil` pour
  Logcat/Journal/Problèmes/Console/Arbre (décision 11) ; coût par mise à
  jour BORNÉ — le cœur du lot (constat I4).

## 0. Circonstance de session (à connaître du propriétaire)

Le prompt exigeait la base `ef2288a` et un arbre propre. À la prise de
session, la base était bien `ef2288a` (vérifié `git log --oneline -3`)
mais l'arbre portait déjà un travail A5 **non committé**, précisément
circonscrit au périmètre du lot (les trois vues, leurs adaptateurs,
l'état console, les listes de la feuille, les layouts/dimens/chaînes,
les tests). Plutôt qu'un arrêt sec, ce travail a été **audité
intégralement** : lecture ligne à ligne du diff et des 10 nouveaux
layouts face au prompt, à l'ADR-085, aux maquettes (`console.html`,
`apercu.html`, `atelier.html`) et au code d'hier (identité visuelle des
lots K/L/L3/L4 : paddings, teintes, retraits, plafonds — chaque valeur
relevée et comparée), mesures Annexe B **re-mesurées** sur HEAD (les
chiffres « avant » ci-dessous sont les miens), puis **corrections
portées** (§2 : imports qualifiés, course du scroll-vers-le-bas du
transcript et du logcat) et **validation re-exécutée par mes propres
lancés** (§5 : 356 tests verts forcés, assembleDebug, APK re-mesuré).
Rien n'a été committé sur la seule foi du travail hérité.

## 1. Constats du prompt — vérifications A5

- **I4 (listes sans recyclage) CONFIRMÉ** : la base portait
  `removeAllViews()` + ré-inflation de TOUTES les lignes à chaque maj —
  `VueConsole.java` (l.284 `zoneProbleme`, l.326 `bandePhases`, l.373
  `transcript` : 17 vues instanciées), `VueArbre.java` (l.120
  `colonne` : 6 vues), `VueApercu.java` (l.264 `bannieres` : 9 vues),
  `ListeLogcat.java:44`, `ListeJournal.java:43`, `ListeProblemes.java:56`
  (`rangee`). Le flux logcat notifie à chaque lot déposé, la console à
  chaque événement du démon P6, les problèmes à chaque diagnostic LSP.
- **I6 (vues d'atelier non migrées) CONFIRMÉ** : `VueConsole` 513 l.,
  `VueApercu` 389 l., `VueArbre` 318 l. à la base — 513→247, 389→266,
  318→137 après le lot (hiérarchie en XML, `<merge>` : la classe EST le
  conteneur, un niveau aplati — précision I3 du brief appliquée aux
  trois vues, qui étendaient LinearLayout et construisaient en code).
- **BarreOnglets (« si en cause ») : HORS lot** — la mission A5 liste
  Logcat/Journal/Problèmes/Console/Arbre ; les onglets fermables sont
  le partage A6 (Annexe A : `TabLayout` OU `RecyclerView` horizontal,
  « l'onglet fermable de l'atelier tranche »). `BarreOnglets.java:72`
  garde son `removeAllViews()` — non touché, signalé à A6.
- **Critères du lot atteints** : 0 vue instanciée dans
  `VueConsole`/`VueArbre`/`VueApercu`/`Liste*`/adaptateurs (grep : 0 ;
  module 131 → 94 vues, −37 = exactement les vues retirées des trois
  vues + listes) ; `EtatConsoleTest` (11), `EtatApercuTest` (8),
  `ArborescenceTest` (7) verts ; coût par maj BORNÉ (§4bis) ; tests du
  module verts ; assembleDebug + `verifierOmbreApk` verts.
- **D3/D1-D2 vérifiés** : aucun nouveau dialogue (les deux
  `Dialogue.menu` d'hier restent les seuls usages des vues migrées —
  58 usages inchangés dans le module, A7 les supprimera) ; les imports
  nouveaux (`androidx.recyclerview` ×26, `material.chip`) sont dans la
  liste blanche de l'ADR-085 ; 0 import Compose.

## 2. Écarts avec le prompt (et corrections d'audit)

- **Bannières de l'aperçu : dérogation ASSUMÉE à la décision 11**
  (liste non recyclée) : liste TRÈS COURTE et BORNÉE (≤ 6 : au plus 1
  erreur + 1 ressources + 1 MockView + n avertissements + 1 frappe + 1
  thème), gonflée par binding à chaque maj — le motif A4 des contentions
  bornées ; un RecyclerView par bannière serait un sur-outil. Justifiée
  dans le javadoc de `VueApercu` et dans `item_banniere_apercu.xml`.
  Les autres listes du lot (console, phases, transcript, arbre,
  logcat, journal, problèmes) sont TOUTES recyclées.
- **Pilule d'état de la console : pas un `Chip`** (la table d'équivalents
  de l'ADR note « Pilule → Chip ») : le point 6 dp teinté PAR RÔLE
  (accent/réussite/alerte/erreur/neutre) + libellé à la couleur du rôle
  ne s'exprime pas en Chip sans un drawable par rôle ; la pilule reste
  donc un petit composite XML (`pilule_etat_console` drawable + point
  teint au bind) — l'IDENTITÉ EXACTE du kit `Pilule` d'hier (surface
  haute, rayon puce, point/libellé LEGENDE, padding 8/3/6), la classe du
  kit restant pour ses autres consommateurs (barre haute, poignée
  feuille — A6/A9). Écart consigné ici (justification exigée par la
  décision 6 de l'ADR).
- **Couleurs par NATURE/gravité/rôle/niveau posées au bind depuis la
  palette** (transcript console, problème, bannières, pastilles d'arbre,
  lignes logcat/journal) : un `ColorStateList` ne porte pas de variante
  PAR LIGNE (même écart que le lot A3 pour la couleur du vidage m2,
  documenté alors) ; décision 8 assumée : la nature de la ligne décide.
  Jamais de littérale — tout part des jetons.
- **En-tête de groupe des problèmes : 12 sp d'hier → `TextAppearance.Jo.Note`
  (13/18) + `sans-serif-medium`** : la maquette (.grp) montre 12 px
  semi-gras, le code d'hier posait 12 sp gras ; le couple 13/18 suit la
  cadence Graphite (précédent A3 du titre de barre 20/26), la graisse
  rapprochée par sans-serif-medium (concession A3/A4, décision 7).
  Écart d'un sp, documenté.
- **Titre de la carte problème : `singleLine` + ellipse médiane AJOUTÉS**
  (hier : le titre se repliait sur plusieurs lignes pour une cible
  longue) — cible « fichier:ligne » ellipse au milieu, plus lisible ;
  écart voulu, à valider visuellement par le propriétaire (§5).
- **Course du scroll-vers-le-bas CORRIGÉE en reprise d'audit** : le
  travail hérité posait le suivi du flux (« le transcript suit la fin »)
  par `post()` juste après `submitList` — le diff court en tâche de fond,
  le `post()` passait AVANT le commit et ratait la dernière ligne ;
  le RAPPEL DE COMMIT de `submitList(liste, callback)` porte désormais
  le scroll (`VueConsole.maj`, `ListeLogcat.maj`) — l'identité du lot K
  (« le transcript suit la fin ») est tenue.
- **Trois imports qualifiés nettoyés** (`com.google.android.material.chip.Chip`
  dans `VueConsole`, `ConsoleBuild.Phase` dans `AdaptateurPhases`,
  `R.string` dans `AdaptateurProblemes`) : le travail hérité les avait
  en noms pleins en pleine signature — imports normaux, whitelist
  inchangée.
- **Identité des paddings/teintes conservée valeur par valeur** (relevé
  à l'audit) : console (retrait 14, aération 7, nature 64 + écart 10,
  rayon 8 des lignes de problème, carte surface + liseré erreur + rayon
  12 + pad 14, bouton Ouvrir écart 10), arbre (en-tête 16, notes retrait
  20 + dessous 10/8, pastille rayon 6, ligne active rayon 10), aperçu
  (barre 12/6, icônes 32 écart 4, perchoir rayon 24 — la maquette
  `.perchoir` 24 px = 24 dp —, bannière pad 10 rayon 9, damier 8).
  Le `dp(8)` de marge droite de la ligne d'arbre reste en code
  (identité L4, une seule occurrence, valeur dynamique par profondeur à
  côté).
- **`clipToOutline` du perchoir posé en code** (attribut XML API 31,
  minSdk 28) — la voie du lot L, commentée dans le layout.
- **Le plafond 400 lignes est conservé PARTOUT** (console
  `ConsoleBuild.PLAFOND_LIGNES`, logcat `EtatLogcat.PLAFOND`, arbre
  `VueArbre.PLAFOND_NOEUDS`) et la troncature de l'arbre se DIT
  (`item_note_tronquee`, élément recyclé).

## 3. Changements

| Fichier | Nature |
|---|---|
| `res/layout/vue_console.xml` (nouveau) | la console (lot K, ADR-É6) en `<merge>` dans `VueConsole` : en-tête (titre + projet, pilule d'état point + libellé teintés au bind, arrêter `Icone` (Canvas) + vider `MaterialButton` Texte), bande de PHASES recyclée (RecyclerView horizontal), filtres `Chip` (`Widget.Jo.Puce`, cochage posé par l'état), TRANSCRIPT recyclé (grille deux colonnes, sélectionnable), plafond annoncé, carte du problème en `<include>` unique à visibilité pilotée |
| `res/layout/item_ligne_console.xml`, `item_phase.xml` (nouveaux) | la ligne du transcript (nature en marge fixe + texte, `TextAppearance.Jo.CodeCompact`, problème ouvrable = fond erreur 10 % par `res/color`) ; la pilule de phase (« Compilation · 4,1 s », non cochable, tap = filtre) |
| `res/layout/carte_probleme_console.xml` (nouveau) | la carte du premier problème localisé (surface + liseré erreur + rayon composant en drawable, titre semi-gras, bouton Ouvrir Tonal) — gonflée UNE fois |
| `res/drawable/pilule_etat_console.xml`, `rond_etat_console.xml`, `ligne_probleme_console.xml`, `carte_probleme_console.xml`, `perchoir_apercu.xml` (5 nouveaux) | les GradientDrawables d'hier en drawables (jetons `?attr/jo_*`, rayons `@dimen`) |
| `res/color/fond_ligne_probleme.xml` (nouveau) | erreur à 10 % par alpha d'item (le mélange du lot K — le puits transparaît) |
| `console/VueConsole.java` | 513 → 247 lignes : coque mince sur `VueConsoleBinding`, transcript/phases par `submitList`, carte unique à visibilité, libellés partagés dans `LibellesConsole` — 0 vue instanciée |
| `console/AdaptateurConsole.java`, `AdaptateurPhases.java`, `LibellesConsole.java` (nouveaux) | le transcript (DiffUtil par valeur de record, couleur par nature au bind, ligne de problème ouvrable) ; la bande de phases (durée formulée au bind) ; les libellés localisés partagés |
| `atelier/VueArbre.java` | 318 → 137 lignes : coque sur `VueArbreBinding`, arbre recyclé, composition PURE déléguée à `ElementArbre` — 0 vue instanciée (le `Dialogue.menu` « Nouveau » d'hier reste, A7) |
| `atelier/AdaptateurArbre.java`, `ElementArbre.java` (nouveaux) | l'arbre en `ListAdapter` multi-types (ligne / note de franchise / troncature), identité par CHEMIN ; le rendu du lot L4 transporté au bind AVEC les réparations du recyclage (alpha et fond actif RE-POSÉS à chaque bind — hier des vues neuves ne s'annulaient jamais) |
| `atelier/VueApercu.java` | 389 → 266 lignes : coque sur `VueApercuBinding`, PERCHOIR UNIQUE (`conteneurRendu()` — hier : un perchoir PAR rendu s'accumulait), densité `MaterialButtonToggleGroup`, nuit/dimensions `Chip`, bannières par binding — 0 vue instanciée |
| `atelier/DamierApercu.java` (nouveau) | le damier du lot L extrait en vue DECLARE EN XML (Canvas : le code reste la voie des vues dessinées) |
| `atelier/AdaptateurLogcat/Journal/Problemes.java`, `ElementProblemes.java` (nouveaux) | les trois sections de la feuille en `ListAdapter` (logcat/journal par valeur de record ; problèmes multi-types en-tête/ligne, groupement pur dans `ElementProblemes`) |
| `ui/atelier/ListeLogcat.java`, `ListeJournal.java`, `ListeProblemes.java` | deviennent des `RecyclerView` (l'API `maj`/`setAuditeur` ne change pas — `EcranAtelier` inchangé) : `submitList` à la place du `removeAllViews` |
| `res/layout/item_noeud.xml` | +1 : la rangée intérieure porte un id (`rangee_noeud`) — le binding typé sert le retrait par profondeur à l'adaptateur qui recycle (avant : `getChildAt(0)` casté) |
| `res/layout/vue_arbre.xml`, `vue_apercu.xml`, `item_note_sorties.xml`, `item_note_tronquee.xml`, `item_banniere_apercu.xml`, `item_entete_problemes.xml` (6 nouveaux) | le tiroir (en-tête nom + `MaterialButton` Tonal « Nouveau », arbre recyclé) ; le volet d'aperçu (barre + `Icone` Canvas, contrôles réels, damier + perchoir, stats, bannières) ; les éléments recyclés des notes et bannières |
| `console/EtatConsole.java` | + `dureeDernierBuildMs()` : la durée de la pilule « réussi · 6,2 s » extraite PURE de la vue d'hier (bilan chiffré, somme des phases sinon — jamais devinée) |
| `res/values/dimens.xml` | +30 jetons du lot (console/aperçu/arbre : les valeurs du code d'hier, cf. §2) |
| `values{,-fr}/strings.xml` | + `apercu_densite_1/2/3` (parité bilingue, 391 = 391 clés) |
| `ArchitectureVuesTest` | + `lesVuesDatelierNInstancientPasDeVuesSaufExceptionsDecroissantes` : hors écrans et hors kit racine, les vues de zone n'instancient plus (exceptions décroissantes : `VueAgent`, `FeuilleBasse` — A6/A7) |
| `atelier/RecyclageListesAtelierTest` (nouveau) | LE cœur du lot : la mesure du coût par maj (§4bis) + la composition pure (plafond, racine masquée, note de franchise, groupement, premier groupe sans séparateur) |
| `EtatConsoleTest` | + `laDureeDuDernierBuildSeLitDansLeBilanSinonLaSommeDesPhases` |
| `ThemesM3Test` | + `lesJetonsDuLotA5OntLeursDimensEtLeurSelecteur` (30 dimens + le sélecteur erreur 10 %) ; le test des sélecteurs passe à 12 |

## 4. Mesures avant/après (Annexe B, re-mesurées par l'agent)

| Mesure | A4 (`ef2288a`) | A5 |
|---|---|---|
| Vues instanciées en code (module) | 131 | **94** (−37 : les 17 de VueConsole, 9 de VueApercu, 6 de VueArbre, 1+1+3 des trois listes — grep des classes migrées : 0) |
| `palette.*` en code | 307 | **290** (−17 ; restent au bind : couleurs calculées par nature/gravité/rôle, décision 8) |
| `GraphiteVue.dp(` | 297 | **261** (−36 ; restent le retrait par profondeur de l'arbre + la marge 8 de la rangée, identité L4) |
| Layouts | 50 | **60** (+10, tous consommés par un `*Binding`) |
| `<merge` | 0 | **3 balises** (les trois vues — +3 mentions en commentaires) |
| `<include` | 37 | **38** (+1 : la carte problème de la console) |
| dp littéraux | 255 | **262** (+7 : uniquement des `0dp` d'idiome `layout_weight`, aucune dimension réelle littérale dans les 10 nouveaux layouts) |
| sp littéraux | 56 | 56 |
| `Dialogue.` usages | 58 | 58 (inchangé — aucun nouveau dialogue, D3) |
| Imports Compose | 0 | **0** ; `androidx.recyclerview` 4 → 30, `material.chip/button` inchangés — tous dans la liste blanche |
| Lignes des vues | 513/389/318/64 | **247/266/137/59** (VueConsole/VueApercu/VueArbre/ListeLogcat) |
| APK debug (R8 + shrink) | 15 743 828 o | **15 775 929 o (+32 101 o, +0,03 Mio)** — aucune dépendance nouvelle : layouts/dimens/chaînes + classes d'adaptateurs |
| `verifierOmbreApk` | vert | **vert** (« APK sans ombre, éléments vitaux présents (15775929 o) ») |
| Tests JVM `:app-android:test` | 344 verts | **356 verts, 0 échec** (+9 recyclage, +1 état console, +1 parité, +1 architecture — passe forcée `--rerun` sur l'état final) |

## 4bis. Le coût par mise à jour est BORNÉ (le cœur du lot)

- **Avant** (constat I4, confirmé) : chaque maj du flux = `removeAllViews()`
  + ré-inflation de TOUTES les lignes — jusqu'à 400 gonflages par lot
  logcat déposé, par événement du démon P6 (console), par diagnostic LSP
  (problèmes), par dépliage (arbre).
- **Après** : `submitList` + DiffUtil — le `RecyclageListesAtelierTest`
  (9 tests) MESURE les opérations dispatchées entre deux états
  consécutifs : une ligne de console/journal/logcat de plus = **1
  insertion, 0 changement, 0 re-lier** (les lignes inchangées ne
  traversent aucun gonflage — le coût d'une maj est fonction de la
  DIFF, pas de la taille du plafond) ; replier un dossier = les retraits
  du sous-arbre seul ; re-diagnostiquer un fichier = son groupe seul
  (l'en-tête re-lié, les lignes de B inchangées PAR VALEUR — testé) ;
  le plafond 400 est conservé et se DIT (arbre : note de troncature
  recyclée ; console : légende du plafond).
- Les compositions mesurées sont PURES (`ElementArbre.composer`,
  `ElementProblemes.composer`) : testées JVM, la vue ne décide rien.
- Réparations du recyclage (vues qui « traînent ») : l'atténuation des
  sorties et le fond actif de l'arbre se RE-POSENT à chaque bind — le
  code d'hier gonflait des vues neuves, un état posé ne s'y annulait
  jamais ; le padding/fond de la ligne de problème de la console se
  re-posent symétriquement.

## 5. Validation

**Reprise d'audit** : les exécutions ci-dessous sont LES MIENNES, sur
l'état final (corrections §2 comprises) ; la passe de tests a été
forcée (`--rerun`). Le démon Gradle est mort UNE fois en pleine charge
R8 d'assembleDebug (contrainte d'environnement documentée aux rapports
A1/A3/A4) : le re-jeu immédiat, à lignes de commande inchangées, est
vert — aucun fichier du dépôt modifié pour cela.

- `./gradlew --init-script … :app-android:test` — **356 tests, 0 échec**
  (dont `RecyclageListesAtelierTest` 9, `EtatConsoleTest` 11,
  `EtatApercuTest` 8, `ArborescenceTest` 7, `ArchitectureVuesTest` 8 —
  exceptions de vues de zone réduites à `VueAgent` + `FeuilleBasse`,
  `ThemesM3Test` 10, `MaquetteConsoleTest` 6, `MaquetteApercuTest` 6,
  `MaquetteAtelierTest` 6, `JetonsXmlTest` 5) ; puis
  `:app-android:testDebugUnitTest --rerun` : 356/0/0.
- `./gradlew … :app-android:assembleDebug` — vert ;
  `verifierOmbreApk` : « APK sans ombre, éléments vitaux présents
  (15775929 o) ».
- `./gradlew … :app-android:lintDebug` — **12 erreurs** : les 10
  préexistantes du rapport A4 EXACTEMENT (MissingSuperCall
  `EcranAtelier:1174`, 8 NewApi `Path#of`/`toList`/`transferTo`/
  `readAllBytes`/`readString` — hors lot, préexistantes au lot L —,
  `DiffUtilEquals` `AdaptateurAccueil` du lot A2) + **2 nouvelles
  `DiffUtilEquals`** (`AdaptateurArbre:77`, `AdaptateurProblemes:64`)
  : même famille que celle d'A2 — les éléments sont des interfaces
  scellées dont TOUS les impléments sont des RECORDS (equals par valeur
  — l'exécution dispatche vers l'equals du record) ; lint inspecte le
  type déclaré. Faux positif probable, signalé au propriétaire (décision
  A2 reconduite, rien de masqué). **Aucune nouvelle erreur
  NewApi/HardcodedText/ContentDescription.** Alertes nouvelles du lot,
  toutes warnings documentés : `DefaultLocale` ×1 (`AdaptateurArbre:212`
  — le `toLowerCase()` d'hier transporté), `SetTextI18n` ×3
  (`AdaptateurLogcat:83`, `AdaptateurProblemes:122`, `VueApercu:152` —
  les concaténations techniques d'hier « D/Tag message », « nom · n »,
  « 360 × 640 », identité des lots L/L3), `RtlHardcoded` ×2
  (`vue_apercu.xml` — convention Left/Right du dépôt, idem A2-A4).
  Pas de nouveau `DuplicateIncludedIds` (l'include de la carte problème
  porte un id unique).
- Parité `values/` ↔ `values-fr/` vérifiée par script : **391 = 391
  clés**, 0 seulement-d'un-côté.
- `./gradlew check` racine NON VERT : `checkLanguagePolicy` échouait
  déjà au commit de base (1313 problèmes typographiques préexistants,
  preuve archivée au rapport A1) — inchangé par le lot ; les documents
  du chantier restent conformes.
- **Aucun appareil/émulateur** : états visuels à vérifier à la main par
  le propriétaire —
  - console : pilule d'état (point + libellé teintés par rôle — pas de
    pulsation animée : l'identité du lot K, la maquette en montre une),
    phases (coche réussite, « — » à venir, tap = filtre), filtres
    (cochage accent 16 % par l'état), transcript (nature accent/
    fonction/texte2/erreur/alerte, lignes de problème ouvrables sur
    fond erreur 10 % rayon 8), plafond annoncé, carte problème (liseré
    erreur, titre ellipse médiane — écart §2), bouton Ouvrir tonal ;
    le transcript COLLE À LA FIN à chaque événement (correctif du
    commit callback — comportement à vérifier en flux rapide) ;
  - arbre : retrait par profondeur, pastilles de type (J/K/‹›/⚙), entrée
    active (fond accent 14 % + barre 3 dp), sorties estompées + puce
    « build #n… » pendant le build, note de franchise, troncature à 400 ;
    dépliage/repliage rapide (les états posés se RE-posed au recyclage) ;
  - aperçu : damier 8 dp, perchoir unique (rayon 24, rendu clippé),
    densité segmentée (case cochée accent 18 %), puce nuit, puce
    dimensions, stats mesurées, bannières par gravité (info alerte) ;
  - comportements : la console dans la feuille (sections basculantes),
    l'aperçu plein écran/côte à côte (déplacement du volet entre
    parents — API inchangée), retour système (inchangé, lot A8).

## 6. Risques résiduels et suite

- `DiffUtilEquals` compte désormais 3 faux positifs probables (A2 + A5
  ×2) — décision du propriétaire attendue (suppression, annotation, ou
  re-formulation des DIFF) ; le chantier ne les masque pas.
- `VueAgent` (10 vues) et `FeuilleBasse` (1) restent les exceptions des
  vues de zone — A6 (racine de l'atelier, `HoteAtelier`) et A7
  (popups/découpage de `ControleurAgent`) les sortent ; `BarreOnglets`
  (`removeAllViews` à chaque maj, onglets fermables) tranche à A6
  (TabLayout OU RecyclerView, Annexe A).
- Le scroll-vers-la-fin de la console/logcat suit maintenant le
  COMMIT du diff : au rythme maximal (plusieurs lots par frame), le
  commit callback s'exécute après chaque submitList — comportement
  validé par relecture, à confirmer en flux réel sur appareil.
- Suite : **A6** — racine de l'atelier (`activity_atelier.xml` +
  `layout-w720dp`, `<include>` des zones, `HoteAtelier`, suppression de
  `construireUi()`/`ContexteUi`, feuille/tiroirs/onglets §4.4).
