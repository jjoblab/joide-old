# PHASE A7 — RAPPORT (popups : fin des dialogues, D3)

- **Date** : 2026-11-19
- **Base** : A6 validée (`14ce1a2`), arbre de travail **portant un travail
  A7 NON committé** à la prise de session (circonstance au §0).
- **Lot** : A7 — **plus aucun dialogue** (D3) : chaque cas modal devient
  un popup `popup_*.xml` gonflé par binding ; `Dialogue.java` **supprimée** ;
  `PopupAncre` **généralisée** + gabarit **`PopupCentre`** (centré avec
  voile) ; **découpage de `ControleurAgent`** en
  `ControleurModelesAgent`/`ControleurUsagesAgent`/
  `ControleurHistoriqueAgent`/`ControleurConnexionsMcp` ; règles de retour
  système (popup EN PREMIER, testé JVM), clavier, fermeture au cycle de
  vie, accessibilité (décision 12) ; contrats `ActivityResult` typés
  `Ecrans.CHOISIR_IMAGE`/`PRENDRE_PHOTO` (décision 13 — les codes
  47101/47102 disparaissent).

## 0. Circonstance de session (à connaître du propriétaire)

Le brief exigeait la base `14ce1a2` (lot A6) et un arbre propre ; la
prise de session a trouvé la base **conforme** mais l'arbre portant
**65 fichiers non committés**, précisément circonscrits au périmètre du
lot (`Dialogue.java` supprimé, 12 `popup_*.xml` + 12 `item_*.xml` +
6 drawables, `ui/PopupCentre.java`, `ui/PortePopups.java`,
`atelier/EtatPopups.java`, le découpage des contrôleurs, les adaptations
des trois écrans porteurs, les tests du filet) — un agent précédent a
épuisé ses tours **avant validation et commit**.

L'audit orienté preuve (le brief A7-fin interdit la relecture exhaustive
des 65 fichiers : vérifier par les critères d'acceptation et la
compilation) a couvert : l'inventaire `git status --short` (65 fichiers,
**tous dans le périmètre du lot**, rien hors du dépôt) ; la lecture
ciblée de `PopupCentre`, `PortePopups`, `PopupAncre` modifiée,
`EtatPopups` + `EtatPopupsTest` (l'ordre du retour système : popup avant
feuille/tiroirs, JVM) ; du montage dans `EcranAtelier`/`HoteAtelier`/
`VueAtelier` (fermeture `onPause`/`onDestroy`/`onConfigurationChanged`,
contrats `registerForActivityResult` posés AVANT `onCreate`) ; des
popups `popup_confirmation`/`popup_saisie`/`popup_menu_noeud`/
`popup_permission_diff`/`popup_usages`/`popup_modeles` + items et
drawables (voile uniformisé, couleurs par `?attr` uniquement, aucun
`android:text` littéral, cibles ≥ 48 dp, `contentDescription` des
icônes) ; des cinq contrôleurs découpés (0 vue du cadre instanciée ;
les 3 `EditText` de `ControleurFichiers` disparus ; l'ordre du retour ;
les contrats `ActivityResult`) ; les greps d'acceptation (Annexe B) ;
**puis la validation re-exécutée par mes propres lancés** (`--rerun`
forcé sur les tests, empaquetage de l'APK forcé, `verifierOmbreApk`,
`lintDebug`).

Particularité mesurée : les tâches Gradle se présentaient **UP-TO-DATE**
à la prise de session — le prédécesseur avait donc compilé et exécuté
les tests avant de s'interrompre ; ses exécutions n'étaient **ni prouvées
ni documentées** (pas de rapport, pas de `CHANGES.md`, pas de section ADR,
pas de commit). Conformément au brief (« ne commit jamais sur la seule
foi du travail hérité »), tout a été rejoué et mesuré à neuf par
l'agent de reprise.

**Corrections portées par l'audit** (le travail hérité était
structurellement complet et compilait ; les fautes étaient des
coquilles de commentaires/javadoc, 8 en tout) :
- 7 × « CONTRATS TYÉ(S) » / « CONTRAT TYÉ » → « TYPÉ(S) »/« TYPÉ »
  (`EcranAtelier`, `ControleurAgent` ×4, `VueAtelier` ×2) ;
- 1 × « les compteurs se montrent PEINs » → « PLEINS »
  (`ControleurUsagesAgent`).

Compléments posés par l'agent de reprise (ce que le prédécesseur n'avait
pas fini) : la section « Évaluations du lot A7 » de l'ADR-085 (le choix
du voile que la décision 12 demandait de consigner), ce rapport,
l'entrée `CHANGES.md`, le commit.

## 1. Constats du prompt — vérifications A7

- **I5 (dialogues et popups construits en code) CONFIRMÉ puis RÉSOLU** :
  à la base, `ui/Dialogue.java` (9 fabriques `AlertDialog`) portait
  58 usages `Dialogue.` (neuf fichiers) et `ControleurAgent` (1 843 l.)
  fabriquait ses popups en code (48 vues) aux l.263, 356, 520, 628,
  1278, 1422, 1646, 1689… Après le lot : `Dialogue.java` **supprimée**
  (`dialog_*.xml` : 0 — il n'en existait pas), grep Annexe B
  (`AlertDialog|MaterialAlertDialogBuilder|android\.app\.Dialog|new
  Dialog\(|DialogFragment|BottomSheetDialog|DatePickerDialog|
  TimePickerDialog|ProgressDialog|Dialogue\.`) = **0 résultat** sur
  `src/main/java`. Sur `src/` ENTIER, les seules occurrences du grep
  (15) sont **les littéraux du filet lui-même**
  (`ArchitectureVuesTest` nomme les classes interdites dans ses motifs
  et sa javadoc pour les vérifier — état identique depuis le lot A0) ;
  le filet vérifie les sources **principales** et les listes
  d'exceptions sont **VIDES** (§1 dernier point).
- **Décision 12, gabarit ANCRÉ — `PopupAncre` généralisée** : la classe
  ne fabrique **plus aucune vue** (plus de `GradientDrawable` posé en
  code : la carte porte le fond par le drawable `carte_popup` du
  `popup_*.xml`), les dimensions se lisent des ressources
  (`jo_pad_popup`, `jo_ecart_popup_ancre`, parité `ThemesM3Test`) ;
  `focusable` (le retour système ferme le popup en premier — la fenêtre
  prend le focus), fond TRANSPARENT (le tap extérieur ferme : la règle
  des menus), chute sous l'ancre ou montée au-dessus, hauteur bornée à
  la place qui reste.
- **Décision 12, gabarit CENTRÉ AVEC VOILE — `PopupCentre`** (nouveau) :
  `PopupWindow` focusable, largeur 360 bornée à l'écran − 32, hauteur
  bornée à l'écran − 48 (le corps `ScrollView` défile dedans), élévation
  12, `setOutsideTouchable(false)` (une confirmation ne se ferme pas du
  bout du doigt), `setAccessibilityPaneTitle` (pane TalkBack),
  `INPUT_METHOD_NEEDED` + `SOFT_INPUT_ADJUST_RESIZE` pour les saisies
  (focus au premier champ, parcours borné).
- **Le VOILE — la question ouverte de la décision 12, tranchée et
  consignée à l'ADR-085** : **vue-voile du layout racine**, pas
  `FLAG_DIM_BEHIND` — `PopupWindow` **n'expose pas sa fenêtre** (aucun
  `getWindow()` public, aucun drapeau posable) et ce drapeau s'applique
  à une fenêtre de DIALOGUE, interdite par D3. Uniformisé : une vue
  `voile_popup`, **dernier enfant** du `FrameLayout` racine des QUATRE
  écrans porteurs (`activity_accueil`, `activity_atelier`,
  `layout-w720dp/activity_atelier`, `activity_parametres`),
  `?attr/jo_rideau`, **cliquable** (retient le toucher hors de la
  carte), `gone` au repos ; `PopupCentre` montre/cache le voile, la
  carte vit dans sa PROPRE fenêtre au-dessus.
- **`PortePopups`** (nouveau) : LA voie d'appel uniforme d'un écran —
  le **registre** des fenêtres ouvertes (au plus une centrée + un menu
  ancré), les corps communs gonflés de leurs `popup_*.xml` par binding
  (menu ancré à `item_ligne_menu` — le tap FERME PUIS agit —,
  information, confirmation Annuler tonal/Valider plein, destructive
  retirer/effacer, saisie à `champ_saisie_param` inclus, écran texte),
  `fermer()` idempotent (cycle de vie), `ouvert()` (l'état pur se
  recale).
- **L'ordre du retour système, testé en JVM** (`EtatPopups` +
  `EtatPopupsTest`, 6 tests) : un popup ouvert se ferme **EN PREMIER**
  (avant même la demande de fermeture et la sortie), puis le tiroir
  agent, puis les surcouches d'`EtatAtelier` **inchangées** (feuille,
  tiroir, aperçu, confirmation, sortie). Mécanique double et
  complémentaire : le popup `focusable` absorbe le retour (fenêtre
  focalisée — le cadre le ferme avant que l'écran ne voie la touche),
  ET le `OnBackPressedCallback` porte `FERMER_POPUP` (ceinture et
  bretelles — le filet d'hier `EtatAtelierTest` reste vert, l'ordre
  d'ADR-É5 inchangé).
- **Clavier** (décision 12) : toutes les saisies passent par
  `centrerSaisie` → `INPUT_METHOD_NEEDED` + `SOFT_INPUT_ADJUST_RESIZE`,
  focus au premier champ (`PopupCentre.premierChamp`).
- **Cycle de vie** (décision 12) : fermeture explicite **`onPause`** et
  **`onDestroy`** sur les TROIS écrans porteurs (accueil, paramètres,
  atelier — `VueAtelier.fermer()` ferme aussi les popups à
  l'`onDestroy`), **`onConfigurationChanged`** armé sur l'atelier
  (frontière A8 : le manifest ne porte pas encore `configChanges` —
  une recréation passe par `onDestroy` qui ferme déjà tout ; l'override
  devient porteur le jour où A8 déclare les changements retenus). Un
  popup n'est **jamais restauré** : l'état de saisie vit dans
  `EtatPopups` (brouillons par clé — `fichiers.nouveau`,
  `fichiers.renomme`, `agent.modele`, `agent.url` — testés JVM).
- **Accessibilité** (décision 12) : `contentDescription` sur les icônes
  cliquables des popups (`btn_fermer_modeles`, `rafraichir_usages`,
  `fermer_usages`…), cibles ≥ 48 dp (`jo_hauteur_cible` 48 pour lignes
  de menu et icônes ; boutons Material à `jo_bouton_hauteur` 52),
  `setAccessibilityPaneTitle` (API 26 ≤ minSdk 28), focus initial des
  saisies.
- **Découpage de `ControleurAgent`** (1 843 → **590 l.**, le service, le
  tour, les pièces jointes, la permission sur diff déplié) :
  `ControleurModelesAgent` (**597** — sélecteur ancré, favoris/récents,
  modèle personnalisé à brouillons), `ControleurUsagesAgent` (**151**),
  `ControleurHistoriqueAgent` (**200**), `ControleurConnexionsMcp`
  (**241**) — **tous < 600 l.** ✓ (les autres contrôleurs restent
  conformes : `ControleurBuild` 387, `ControleurEdition` 457,
  `ControleurFichiers` 399). `VueAtelier` monte les quatre contrôleurs
  et les passe au contrôleur de l'agent.
- **0 vue du cadre instanciée dans les contrôleurs** : le grep §6.1 du
  prompt sur `MainActivity` + `ecrans/` = **0** ; les 3 `new EditText(`
  de `ControleurFichiers` d'hier : **0** (le champ vit dans
  `champ_saisie_param` inclus par les popups) ; module entier : **22**
  instanciations restantes, TOUTES dans le kit `ui/` racine (A9/I9 —
  le filet l'exclut explicitement).
- **Contrats `ActivityResult` (décision 13, passation A6)** :
  `Ecrans.CHOISIR_IMAGE` (`GetContent`, l'entrée le type MIME, la
  sortie l'URI) et `Ecrans.PRENDRE_PHOTO` (`TakePicture`, l'URI de
  sortie, la sortie « prise ? ») — enregistrés **avant `onCreate`** dans
  `EcranAtelier`, délégués au tiroir agent par
  `HoteAtelier.choisirImageAgent()`/`prendrePhotoAgent()` (l'ancienne
  méthode `ouvrirEcranPourResultat` dépréciée est retirée de l'hôte) ;
  les codes `47101`/`47102` et `onActivityResult`/
  `startActivityForResult` : **0** (grep). Les URI annulées n'attachent
  rien en silence (`surImageChoisie(null)`).
- **Chaînes — parité `values/` ↔ `values-fr/` : 391 = 391 clés**, 0
  seulement-d'un-côté (script) : les popups ne consomment QUE des
  chaînes existantes (compilation + lint `HardcodedText` : 0).
- **`ArchitectureVuesTest`** : les listes d'exceptions **VIDES**
  (`ECRANS_EXCUSSES_VUES` — `ControleurAgent`/`ControleurFichiers`
  sortent ; `ZONES_EXCUSSEES_VUES` — `VueAgent` sort ; les 11 fichiers
  de `FICHIERS_EXCUSSES_MAISON` sortent ; `FICHIERS_EXCUSSES_DIALOGUES`
  vide depuis la suppression de `Dialogue.java`) + l'assertion
  « `Dialogue.java` doit rester SUPPRIMÉE » — toute régression échoue.

## 2. Écarts avec le prompt (et corrections d'audit)

- **Le voile : vue-voile, pas `FLAG_DIM_BEHIND`** (choix demandé par la
  décision 12) — justification d'impossibilité ci-dessus §1 ;
  consigné à l'ADR-085 (section « Évaluations du lot A7 », ajoutée par
  la reprise : le prédécesseur n'avait pas amendé l'ADR).
- **Les PUCES du kit restent posées par code dans les popups**
  (rangée des fournisseurs du sélecteur de modèles, options des
  questions) : vues MAISON déclarables en XML (`Puce` du kit `ui/`,
  hors filet — I9 les remplacera en A9 par des `Chip` si la maquette le
  permet) ; la COQUE et les lignes de liste, elles, vivent en XML
  (`popup_modeles`, `item_modele`, `item_entete_section`).
- **Les listes des popups par inflation binding, pas `RecyclerView`**
  (entrées de menu, conversations de l'historique, serveurs de
  connexions, assises des usages) : listes **courtes et bornées par
  construction** (plafond `CONVERSATIONS_MAX`, configurations MCP
  bornées à 64 Kio) — clause « une liste courte et statique peut rester
  dans le layout » de la décision 11, motivée au rapport A3/A4 (motif
  des contentions).
- **`SetTextI18n` transporté** (`ControleurHistoriqueAgent:89`) : la
  méta-ligne « active · échanges · date heure » est le patron
  D'HIR de `ControleurAgent:1761` (base A6) transporté à l'identique —
  pas une nouvelle alerte ; de même `MainActivity:172` (légende
  d'accueil) et `VueAgent:586`/`VueApercu:163` (patrons d'hier).
- **`onConfigurationChanged` armé sur l'atelier seul** (accueil et
  paramètres n'ont pas de politique de configuration — leur recréation
  passe par `onDestroy`, qui ferme ; A8 pose la décision 10 complète) ;
  l'accueil et les paramètres ferment en `onPause`/`onDestroy`.
- **`EcranAtelier` 180 → 194 lignes** (contrats typés + fermeture au
  cycle de vie) — reste largement < 500 ; `MainActivity` 531 l.,
  `EcranParametres` 1 102 l. (A9 la réduira, hors périmètre A7).
- **Chute marquée des mesures `palette.*` et `GraphiteVue.dp(`**
  (218 → 205, 245 → 152) : réelle — les 48 vues du contrôleur et les
  10 de `VueAgent` se posaient par palette/dp en code, les popups
  portent couleurs et dimensions par `?attr`/`@dimen` ; restent au bind
  par nature (teinte de bande d'usage calculée par ligne, décision 8 ;
  états pointés des puces).
- **Corrections d'audit** : les 8 coquilles §0 (aucun comportement
  touché — commentaires/javadoc seuls).

## 3. Changements

| Fichier | Nature |
|---|---|
| `ui/Dialogue.java` | **SUPPRIMÉE** (D3 : les 9 fabriques `AlertDialog` — 58 usages sur neuf fichiers — deviennent des popups) |
| `ui/PopupCentre.java` (nouveau) | le gabarit CENTRÉ AVEC VOILE : fenêtre focusable bornée, clavier des saisies, pane TalkBack, focus initial — la mécanique seulement, aucune vue |
| `ui/PortePopups.java` (nouveau) | le PORTE-POPUPS d'un écran : registre des fenêtres, corps communs gonflés (menu, information, confirmation, destructive, saisie, écran texte), `fermer()` idempotent, `ouvert()` pour l'état |
| `ui/PopupAncre.java` | GÉNÉRALISÉE : plus aucune vue fabriquée (le fond vit dans `carte_popup`), dimensions par ressources, rappel de fermeture (le registre se retire) |
| `atelier/EtatPopups.java` (nouveau) | l'état pur : l'ordre du retour (popup EN PREMIER), les BROUILLONS de saisie (le popup n'est jamais restauré) |
| `ecrans/atelier/ControleurAgent.java` | 1 843 → **590 l.** : le service, le tour, les pièces jointes, la permission sur diff (`popup_permission_diff` centré, fermer = REFUSER), les menus ancrés, les contrats typés |
| `ecrans/atelier/ControleurModelesAgent.java` (nouveau, 597 l.) | le sélecteur ANCRÉ (`popup_modeles` : fournisseurs, état de clé, recherche, favoris/récents/suggestions, coche) + le modèle personnalisé (`popup_saisie_modele`, brouillons `EtatPopups`) |
| `ecrans/atelier/ControleurUsagesAgent.java` (nouveau, 151 l.) | les USAGES centrés (`popup_usages` : compteurs à bande colorée, pieds de conversation, banc) |
| `ecrans/atelier/ControleurHistoriqueAgent.java` (nouveau, 200 l.) | l'HISTORIQUE centré (`popup_historique` : conversations, courante marquée, suppression confirmée) + le journal rejoué |
| `ecrans/atelier/ControleurConnexionsMcp.java` (nouveau, 241 l.) | les CONNEXIONS MCP centrées (`popup_connexions_mcp` : serveurs, approbation ADR-063, OAuth) + `lireMcpJson`/`cleApprobationMcp` (transférés d'hier) |
| `ecrans/atelier/EcranAtelier.java` | 180 → 194 l. : contrats `CHOISIR_IMAGE`/`PRENDRE_PHOTO` AVANT `onCreate`, `OnBackPressedCallback` porte `FERMER_POPUP` en tête, `onPause`/`onConfigurationChanged` ferment les popups |
| `ecrans/atelier/HoteAtelier.java` | `ouvrirEcranPourResultat` déprécié RETIRÉ ; `fenetre()` retiré (l'ancre du menu d'arbre n'a plus besoin du decor) ; `etatPopups()` et `popups()` ajoutés |
| `ecrans/atelier/VueAtelier.java` | porte le porte-popups (voile du layout racine) et `EtatPopups` ; `ouvrirMenu()`/`demanderFermeture()`/`confirmerVidage()` deviennent des popups ; `surRetourSysteme()` consulte `EtatPopups` ; les menus de l'aperçu/de l'arbre passent par le registre partagé (`poserPopups`) |
| `ecrans/atelier/SessionAtelier.java`, `ControleurEdition.java`, `ControleurFichiers.java`, `MainActivity.java`, `EcranParametres.java`, `atelier/VueArbre.java`, `atelier/VueApercu.java`, `atelier/agent/VueAgent.java`, `accueil/AdaptateurAccueil.java` | les `Dialogue.*` → `popups.*` (menus ancrés à la vue appelante, cartes centrées sur voile) ; `menuProjet(ancre)` ; le transcript de l'agent migre ses vues en items XML (état vide, borne, lignes tonales, usages, erreurs, questions) |
| `res/layout/popup_confirmation.xml`, `popup_destructive.xml`, `popup_information.xml`, `popup_saisie.xml`, `popup_page_texte.xml` (nouveaux) | les cartes CENTRÉES communes (MaterialCardView surface/liseré bord-fin/rayon carte, Annuler tonal / Valider plein, la destructive deux actions) — `page_texte_param.xml` SUPPRIMÉ (remplacé) |
| `res/layout/popup_menu_noeud.xml` (nouveau), `item_ligne_menu.xml` (nouveau) | LE gabarit des menus ANCRÉS (titre optionnel, lignes à cible 48 dp, drawable `carte_popup` + `ligne_menu`) — généralise le « menu de nœud » à tous les menus |
| `res/layout/popup_modeles.xml` | le sélecteur de modèle devient un CORPS XML complet (titre + fermer 48 dp `contentDescription`, rangée défilante, état de clé, recherche, liste, personnalisé) |
| `res/layout/popup_permission_diff.xml`, `popup_usages.xml`, `popup_historique.xml`, `popup_connexions_mcp.xml` (nouveaux) | les cartes centrées à contenu (diff borné 320 dp, compteurs à bandes, conversations, serveurs) |
| `res/layout/item_modele.xml`, `item_entete_section.xml`, `item_conversation_historique.xml`, `item_serveur_connexions.xml`, `item_assise_usages.xml`, `item_compteur_usages.xml`, `item_ligne_tonale_agent.xml`, `item_usage_agent.xml`, `item_vide_agent.xml`, `item_borne_agent.xml`, `item_question_ligne.xml` (nouveaux) | les lignes gonflables des listes des popups et du transcript de l'agent (coche/étoile, courante marquée, bande d'usage à poids/teinte, état vide, questions) |
| `res/drawable/carte_popup.xml`, `ligne_menu.xml`, `ligne_popup.xml`, `ligne_popup_active.xml`, `piste_bande_usage.xml`, `remplissage_bande_usage.xml` (nouveaux) | la carte des menus (surface/bord-fin/rayon — l'ancien `GradientDrawable` en code), l'appui marqué des lignes, la piste et le remplissage des bandes d'occupation |
| `res/layout/champ_saisie_param.xml` | réorienté : LE champ de saisie des POPUPS (inclus par `popup_saisie`/`popup_saisie_modele`), plus « corps de dialogue » |
| `res/layout/activity_accueil.xml`, `activity_atelier.xml`, `layout-w720dp/activity_atelier.xml`, `activity_parametres.xml` | + la vue-voile `voile_popup` (dernier enfant, `?attr/jo_rideau`, cliquable, `gone`) — l'accueil passe sa colonne sous un `FrameLayout` racine |
| `res/values/dimens.xml`, `res/values/styles.xml` | +21 jetons du lot (retraits popup 24/20, carte ancrée 8/4, ligne de menu 14/11, CIBLE 48, bande d'usage 6/3, diff 320, lignes tonales 2…) et `Widget.Jo.Button.Reussite` (l'« Appliquer » de la permission) — parité `ThemesM3Test.lesJetonsDuLotA7OntLeursDimens` |
| `docs/adr/085-architecture-vues-ecrans.md` | + section « Évaluations du lot A7 » : le voile (vue-voile retenue, `FLAG_DIM_BEHIND` impraticable), le porte-popups, la carte porte le fond, les listes courtes |
| `ArchitectureVuesTest` | les quatre listes d'exceptions VIDES + l'assertion « `Dialogue.java` reste supprimée » |
| `ThemesM3Test` | + `lesJetonsDuLotA7OntLeursDimens` (21 jetons, parité) |
| `atelier/EtatPopupsTest.java` (nouveau) | 6 tests JVM : l'ordre du retour (popup avant tout, tiroir agent, ordre d'hier inchangé, avant la demande de fermeture et la sortie) + les brouillons (survie, normalisation du null, oubli) |

## 4. Mesures avant/après (Annexe B, re-mesurées par l'agent)

| Mesure | A6 (`14ce1a2`) | A7 |
|---|---|---|
| Usages de dialogues (grep Annexe B, main) | 58 | **0** (`Dialogue.java` supprimée, `dialog_*.xml` : 0) |
| Vues instanciées en code (module) | 84 | **22** (le kit `ui/` racine seul : A9/I9) |
| dont `ControleurAgent` / `VueAgent` / `ControleurFichiers` | 48 / 10 / 3 | **0 / 0 / 0** |
| `palette.*` (grep Annexe B) | 218 | **205** (+24 `palette().` de l'hôte) — la dé-injection est réelle : couleurs des popups par `?attr` |
| `GraphiteVue.dp(` | 245 | **152** |
| Layouts | 65 + 1 `w720dp` | **87** + 1 `w720dp` (+11 popups, +12 items, −1 `page_texte_param`) |
| dp littéraux dans les layouts | 263 | **283** (+20 : idiomes `0dp` de poids et retraits ponctuels des items d'agent — valeurs du code d'hier ; A9 consolide) |
| sp littéraux | 56 | **54** |
| `<merge` / `<include` / `<ViewStub` | 3 / 38 / 6 | **9 / 45 / 6** (+6 `<merge>` items, +7 `<include>` champ/compteur) |
| Lignes `ControleurAgent` | 1 843 | **590** (+ Modeles **597**, Usages **151**, Historique **200**, Connexions **241** — tous < 600) |
| `EcranAtelier` | 180 | **194** (< 500) ; `MainActivity` 531 ; `EcranParametres` 1 102 |
| Imports AndroidX/Material (liste blanche) | 0 Compose | **0 Compose** (+`androidx.activity.result` ×5 — contrats, listé) |
| APK debug (R8 + shrink) | 15 798 411 o | **15 844 349 o (+45 938 o, +0,04 Mio)** — aucune dépendance nouvelle : layouts, items, drawables, classes de popups |
| Tests JVM `:app-android:test` | 364 verts | **371 verts, 0 échec** (+6 `EtatPopupsTest`, +1 parité dimens A7) |

## 5. Validation

Reprise d'audit : les exécutions ci-dessous sont LES MIENNES, sur
l'état final (corrections §0 comprises) ; les tests ont été forcés
(`--rerun`), l'empaquetage de l'APK forcé (`:app-android:packageDebug
--rerun` — même octets à la re-paration). Le démon Gradle n'est PAS mort
 cette fois (les leçons A4/A5/A6 restaient armées :
`GRADLE_OPTS=-Dorg.gradle.internal.repository.max.threads=2`).

- `./gradlew --init-script … :app-android:testDebugUnitTest --rerun` —
  **371 tests, 0 échec, 0 erreur, 0 ignoré** (dont `EtatPopupsTest` 6,
  `ArchitectureVuesTest` 8 — exceptions VIDES, `ThemesM3Test` 12 —
  parité A7, `EtatAgentChatTest` 14, `EtatAtelierTest` 16,
  `MaquetteAtelierTest` 6, `EtatAccueilTest`, `EtatParametresTest`,
  `EtatCreationTest`…) ; puis `:app-android:test` — vert.
- `./gradlew … :app-android:packageDebug --rerun` puis
  `:app-android:verifierOmbreApk --rerun` — **vert** : « APK sans
  ombre, éléments vitaux présents (15844349 o) ».
- `./gradlew … :app-android:lintDebug` — **11 erreurs, 135
  avertissements** : les 11 d'A6 EXACTEMENT (8 `NewApi` préexistantes :
  `Path#of` ×3, `toList`, `readString`, `readAllBytes` ×2, `transferTo`
  — hors lot ; 3 `DiffUtilEquals` faux positifs connus A2/A5) —
  **aucune NOUVELLE erreur** `NewApi`/`HardcodedText`/
  `ContentDescription` ; aucun avertissement NOUVEAU ne pointe un
  fichier du lot (le `SetTextI18n` de `ControleurHistoriqueAgent:89`
  est le patron d'hier `ControleurAgent:1761` transporté à l'identique ;
  les `DuplicateIncludedIds` ×14 restent TOUS dans
  `activity_parametres.xml`, lot A3 préexistant).
- Parité `values/` ↔ `values-fr/` par script : **391 = 391 clés**, 0
  seulement-d'un-côté — aucune chaîne nouvelle n'a été nécessaire.
- `./gradlew check` racine NON VERT — inchangé de la base
  (`checkLanguagePolicy` : 1 313 problèmes typographiques préexistants,
  preuve archivée au rapport A1) ; le lot n'ajoute rien.
- Manifest non touché (A8 y posera `configChanges`).
- **Aucun appareil/émulateur** : états à vérifier à la main par le
  propriétaire — voir §6.

## 6. Risques résiduels et suite

- **Comportements de popup à vérifier sur appareil** (sans émulateur,
  ils ne peuvent être PROUVÉS) : le retour ferme le popup ouvert en
  premier (fenêtre focusable) avant feuille/tiroirs ; le clavier
  accompagnant les saisies centrées (`INPUT_METHOD_NEEDED` +
  `ADJUST_RESIZE`, focus au champ — la fiabilité API 28/29 reste à
  valider, constat C5 du prompt) ; la rotation (aujourd'hui :
  recréation → `onDestroy` ferme tout, le brouillon survit dans
  `EtatPopups` — mémoire de l'activité — jusqu'à la fermeture du
  processus) ; le voile couvre TOUT hors carte (le tap dehors ne ferme
  pas une confirmation) ; l'écran texte défile dans sa carte bornée.
- **`SOFT_INPUT_ADJUST_RESIZE` est déprécié à l'API 35** : la décision
  12 de l'ADR-085 le retient sous `targetSdk` 28 (la voie fiable sur
  API 28/29) — à revisiter si `targetSdk` monte un jour (jamais dans ce
  chantier).
- Les 22 vues instanciées restantes vivent dans le kit `ui/` racine
  (`Onglets`, `Nuancier`, `EtatVide`, `TiroirPousse`, `Stepper`,
  `Segment`, `Pilule`, `Fab`, `Champ`, `Carte`) : I9/A9 les remplacera
  par des composants Material — les PUCES des popups suivront.
- `EcranParametres` (1 102 l.) et `VueAtelier` (~1 470 l. après le
  lot) restent des états de transition documentés : A8 (cycle de vie)
  et A9 (consolidation) les réduisent ; `HoteAtelier` rétrécira
  membre à membre.
- `DiffUtilEquals` ×3 : décision du propriétaire attendue (le chantier
  ne masque pas).
- dp littéraux +20 dans les layouts du lot (retraits ponctuels des
  items d'agent, valeurs du code d'hier) : A9 factorise les répétitions
  ≥ 3 fois en `@dimen/`.
- Suite : **A8** — `configChanges` de géométrie, `onConfigurationChanged`
  (ré-inflation de la racine SANS `fermerIde()` + fermeture des popups —
  l'override est déjà armé), sauvegarde d'état, garde `isDestroyed()`
  des callbacks ; **A9** — consolidation, ADR-085 → Accepted.
