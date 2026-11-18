# Prompt agent — Refonte de l'atelier JoIDE (v2)

Tu travailles dans `jjoblab/joide`, module `app-android`. Ta mission : reproduire **à l'identique** la maquette `docs/ecrans/design/atelier-v2.html` (ouvre-la dans un navigateur, largeur utile 370 dp, **1 px de la maquette = 1 dp**), en remplaçant l'UI construite en code par de **vrais layouts XML + ViewBinding**. La maquette fait autorité sur la structure, les dimensions, les couleurs et les textes. Les valeurs de ce document sont celles de la maquette.

## 0. Règles non négociables

1. **Pas de Compose, pas de Material Components, pas d'AndroidX UI** (RecyclerView, ConstraintLayout, CoordinatorLayout, DrawerLayout, Fragment, BottomSheetBehavior : interdits). Vues du cadre uniquement (FrameLayout, LinearLayout, ScrollView, vues du kit `ui/`). Seule exception à faire valider : la dépendance d'exécution `androidx.databinding:viewbinding` que `viewBinding = true` ajoute. **Étape 1 obligatoire : écrire l'ADR-067** qui amende l'ADR-É2 (« zéro AndroidX ») pour cette seule dépendance (Apache-2.0, à ajouter à `NOTICE` et `docs/third-party.md`). Sans ADR accepté, ne pas activer ViewBinding : arrête-toi et demande.
2. **Clean-room (ADR-008)** : CodeAssist est GPL-3.0. Tu n'en copies aucune ligne ; seules les structures décrites ici sont retenues.
3. **L'UI reste mince** : les états purs (`EtatAtelier`, `EtatConsole`, `EtatApercu`, `EtatAgentChat`, `Arborescence`) ne bougent pas ; les vues les lisent. Tout nouveau comportement testable va dans une classe pure testée JVM.
4. **Franchise** : jamais d'onglet ou de bouton réservé qui ne fait rien ; jamais de couleur codée en dur (tout vient des jetons Graphite).
5. Chaque lot compile, passe `./gradlew check` et reste livrable par la CI.

## 1. Jetons et thème

- `Graphite.java` reste la source de vérité. Créer `res/values/colors.xml` (sombre), `res/values/attrs.xml` (attributs de thème `jo_fond`, `jo_surface`, `jo_surface_haute`, `jo_puits`, `jo_bord`, `jo_bord_fin`, `jo_texte`, `jo_texte2`, `jo_texte3`, `jo_accent`, `jo_reussite`, `jo_alerte`, `jo_erreur`, `jo_violet`, + 7 couleurs de syntaxe), `themes.xml` (`Theme.JoIDE.Sombre`, `Theme.JoIDE.Clair`, parent `android:Theme.Material.NoActionBar`). Les layouts n'utilisent que `?attr/jo_*`. Appliquer le thème par `setTheme()` avant l'inflation, selon `Reglages` (sombre/clair/système, ADR-048).
- Test JVM `JetonsXmlTest` : parse `colors.xml` et vérifie l'égalité avec `Graphite` (dans l'esprit des tests de conformité existants).
- Valeurs (sombre) : fond `#0E1116`, surface `#151920`, surfaceHaute `#1B212A`, puits `#0B0E12`, bord `#262D38`, bordFin `#1D232C`, texte `#E7ECF3`, texte2 `#9AA6B5`, texte3 `#64707F`, accent `#82AAFF`, réussite `#3DDC84`, alerte `#E5C07B`, erreur `#E06C75`, violet `#C678DD`, fonction `#61AFEF`, chaîne `#98C379`, nombre `#D19A66`, commentaire `#7C8695`, défaut `#D6DAE3`. Tonal = couleur à 12 % (14 % pour erreur/alerte) sur le fond de la surface. Texte sur réussite `#06210F`, sur accent `#0B1426`.
- Rayons : 22 (feuille), 18 (composeur), 16 (FAB), 14 (cartes), 12 (boutons, tuiles), 10 (lignes d'arbre, onglets), 999 (puces). Grille 4 dp. Mouvement : 240 ms tiroirs, 260 ms feuille, `PathInterpolator(.2f,.8f,.2f,1f)`.
- Typo : UI sans système ; code monospace (`code` 12,5/22 dans l'éditeur, 12/19 dans la console). Tailles : titre 16 semi-gras, corps 14, note 13, légende 12.
- Icônes : `VectorDrawable` dans `res/drawable/ic_*.xml` (framework, sans AndroidX), teintées par attribut.

## 2. Architecture des fichiers

`EcranAtelier.java` (3 430 lignes) devient un orchestrateur (< 500 lignes : cycle de vie, montage, retour système). Extraire :

- `atelier/ControleurBuild` (méthodes `construire*`, `arreterBuild`, service `:build`) ;
- `atelier/agent/ControleurAgent`, `ControleurModelesAgent`, `ControleurUsagesAgent`, `ControleurHistoriqueAgent` (tout le bloc agent, MCP, modèles, usages, historique) ;
- vues liées : `BarreHaut`, `BarreOnglets`, `FilAriane`, `BarreSymboles`, `FeuilleBasse` (vue personnalisée), `SectionConsole`, `SectionProblemes`, `SectionJournal`, `SectionLogcat`, `TiroirFichiers`, `TiroirAgent`, `PopupModeles`.

Chaque vue reçoit son `Binding` généré. Les vues personnalisées du kit (`Dock`→`FeuilleBasse`, `TiroirPousse`, `Onglets`, `Puce`, `Pilule`, `Fab`, `PopupAncre`) sont déclarées dans le XML : constructeur `(Context, AttributeSet)` obligatoire, attributs dans `attrs.xml`.

Layouts (`res/layout/`, snake_case français, un `<include>` par zone) : `atelier`, `atelier_barre_haut`, `atelier_onglets` + `item_onglet`, `atelier_fil_ariane`, `atelier_barre_symboles` + `item_touche`, `atelier_feuille` (+ `feuille_entete` (rail + zone vivante) + `entete_console`, `entete_problemes`, `entete_journal`, `entete_logcat`, `feuille_corps`, `feuille_console`, `feuille_problemes`, `feuille_journal`, `feuille_logcat`, `item_phase`, `item_probleme`, `item_ligne_journal`, `item_ligne_logcat`), `tiroir_fichiers` + `item_noeud`, `tiroir_agent` + `item_msg_utilisateur`, `item_msg_agent`, `item_raisonnement`, `item_outil`, `item_permission`, `item_question`, `item_erreur_agent`, `composeur_agent`, `popup_modeles`, `popup_menu_noeud`. Aucun `new LinearLayout(` ne doit subsister dans les écrans migrés (les lignes de liste se gonflent depuis un `item_*.xml`).

## 3. Spécifications écran par écran

### 3.1 Barre supérieure (52 dp, padding 0 10 dp, espacement 8 dp)
☰ 40×40 (rayon 12, texte2) ouvre le tiroir fichiers ; **« retour accueil / fermer le projet » passe dans ⋮** (confirmation conservée, fichiers modifiés comptés). Au centre : nom du projet 16/20 semi-gras ; dessous (12/16) puce `n modules` (neutre : fond surfaceHaute, texte2) + pilule d'index (états : *Index prêt* réussite, *Indexation n %* accent, *Partiel* alerte, *Erreur* erreur ; tap → section Journal filtrée INDEX ; le détail honnête existant s'y affiche). Bouton agent 40×40, rayon 12, fond accent 12 %, glyphe accent ; **point d'activité** 8 dp réussite (anneau 2 dp couleur fond) affiché **uniquement pendant un tour d'agent**. ⋮ 40×40.

### 3.2 Onglets (bande 40 dp, padding 0 8 dp, espacement 3 dp)
Onglet : hauteur 34, padding 0 10, rayon haut 10, texte 13. Actif : fond puits, texte, **indicateur 2 dp accent** inset 10 dp aux côtés, collé en bas. Pastille de type 16×16 rayon 5, tonale : Java `J` alerte, Kotlin `K` violet, XML `‹›` réussite, config `⚙` accent. Après le nom : point 7 dp (erreur si diagnostic erreur, sinon alerte si modifié). Fermeture : × sur l'onglet actif ; appui long sur un autre → menu (Fermer / Fermer les autres). Bannière de compatibilité Gradle (existante) : fond alerte 12 %, texte alerte 13, au-dessus de la bande.

### 3.3 Fil d'Ariane (28 dp)
Fond puits, bord bas 1 dp bordFin, padding 0 14, 12 sp texte3 ; segments texte2 (500) séparés par `›` ; **dernier segment = symbole sous le curseur**, couleur fonction `#61AFEF`. Données : chemin du fichier + symbole englobant fournis par le moteur de langage.

### 3.4 Éditeur (vue celeris, fond puits)
Padding vertical 6 dp. Gouttière 42 dp : numéros 11,5 sp texte3 alignés à droite (padding droit 12), ligne courante en texte2. Ligne courante : fond accent 7 %. Ligne avec erreur : point 6 dp erreur dans la gouttière (x = 14 dp, y = 8 dp) + soulignement ondulé erreur sur le symbole. Curseur 2 dp accent, clignotement 1 s. Syntaxe : jetons §1. Ces valeurs se posent sur le thème celeris existant, pas dans l'état.

### 3.5 Barre de symboles (42 dp, **visible seulement quand le clavier est affiché**)
Fond surfaceHaute, bord haut 1 dp bord, padding 5/6, espacement 4. Touches : hauteur 32, rayon 9, fond surface, mono 14 semi-gras. Première touche **Tab** (poids 1,6, accent, 12 sp), puis `{ } ( ) ; " =` et `⋯`. Comportements : toucher = insère via l'insertion intelligente (fermeture auto des paires) ; **flick haut** = variante (`(` → `)`) ; **appui long puis glisser** = trackpad du curseur ; `⋯` ouvre une rangée d'actions (commenter, déplacer/dupliquer la ligne, problème suivant). Aucune touche ne prend le focus (le clavier ne doit pas se fermer). Quand le clavier est visible, **feuille et FAB sont masqués**.

### 3.6 Feuille basse (`FeuilleBasse`, remplace `Dock`)
- Fond `surface` **opaque** (la maquette suggère du verre ; pas de flou d'arrière-plan en vues du cadre sous API 31), rayon haut 22, liseré intérieur haut 1 dp blanc 6 %, élévation 10 dp.
- **Trois détentes** : *replié* 72 dp (poignée 20 + en-tête 52) · *mi-hauteur* 55 % de la hauteur utile · *plein* 90 %. Poignée 38×4 dp rayon 4, texte3 à 70 %, zone tactile = toute la ligne de 20 dp. Toucher la poignée : replié → mi → plein → replié. Glisser : suit le doigt (bornes 72 dp → 90 %) puis s'ancre à la détente la plus proche ; **fling > 1 200 dp/s** va à la détente suivante dans le sens du geste. Animation 260 ms. Défilement imbriqué avec les `ScrollView` des sections : tirer vers le haut agrandit d'abord la feuille avant de faire défiler ; tirer vers le bas au sommet du contenu la replie (API de défilement imbriqué du cadre, `View.startNestedScroll` / `ViewParent.onNestedPreScroll`).
- Logique pure dans `EtatFeuille` (détente, section active, bornes, calcul d'ancrage, fling) + `EtatFeuilleTest`.
- **Principe : une information, un seul propriétaire, un seul en-tête.** Il n'y a **ni barre de navigation repliée distincte, ni rangée de puces de section** (c'étaient des doublons). La feuille a **un seul en-tête de 52 dp, identique replié et déplié, qui change selon la section active**. Hauteur repliée = poignée 20 + en-tête 52 = **72 dp**. Le FAB se place à 84 dp du bas.
- **Structure de l'en-tête** (padding 0 10, espacement 8) : à gauche le **rail de sections** — pilule fond `fond`, rayon 14, padding 3, 4 boutons 32×32 rayon 11 (icône 15 sp ; actif = fond surfaceHaute + liseré haut 5 % blanc + icône accent ; badge erreur LSP en haut à droite de ⚠ : fond erreur, texte blanc 10 sp gras, rayon 9) : `▤ Console`, `⚠ Problèmes`, `≣ Journal`, `▥ Logcat` (chacun avec `contentDescription`). À droite, la **zone vivante** (poids 1) propre à la section : ligne 1 en 13/17 semi-gras, ligne 2 en mono 11,5/15 texte2, ellipsis. **Toucher la zone vivante** : barre ↔ mi-hauteur. **Glisser horizontalement** la zone (> 40 dp) : section suivante/précédente (boucle), contenu qui entre en glissant de 14 dp + fondu, 220 ms. Un geste vertical sur la poignée ou l'en-tête déplace la feuille.
- **Zone vivante par section** :

| Section | Ligne 1 | Ligne 2 | Élément de droite |
|---|---|---|---|
| Console | verdict : `✕ Build #4 échoué · 6,2 s` (erreur) · `✓ Build #4 réussi · 6,2 s` (réussite) · `● Compilation :app · 4,1 s` (accent, en cours) · `Interrompu` (alerte) · `Aucun build` (texte2) | première erreur `Facture.java:19 · symbole introuvable` (tap = ouvre le fichier) ; en cours : tâche courante ; réussi : `12 exécutées · 3 du cache` | **pipeline** de 3 segments 14×5 rayon 3 (résolution · compilation · assemblage : réussite / erreur / bord `bord` = non atteint, accent pulsant = en cours) — **visible replié seulement** (déplié, les cartes de phase le remplacent) |
| Problèmes | `✕ 2  ▲ 1` (erreur, alerte) ou `✓ Aucun problème` ou `Analyse… 62 %` | première erreur **du fichier actif** `Facture.java:19 · numero introuvable` (tap = ouvre à la ligne) | bouton ↓ 34×34 « problème suivant » : saute à l'erreur suivante dans l'éditeur **sans déplier** |
| Journal | `● INDEX · prêt` (point réussite) · `● INDEX · 62 %` (accent) · `● MCP · injoignable` (alerte) | `10:41:02 · 1 284 fichiers · 3 modules` : heure + chiffres du dernier événement | — |
| Logcat | `● En direct · pid 4821` · `○ Application non lancée` (texte3) · `■ Processus terminé (code n)` (alerte) | dernière ligne reçue, colorée par niveau (`E/AndroidRuntime NullPointerException` en erreur) | **histogramme du débit** : 9 barres 3 dp (hauteur max 22, texte3 ; la barre des lignes de niveau E en erreur), fenêtre glissante des dernières secondes |

- **Corps (mi/plein)** : sous l'en-tête, uniquement la **barre d'outils de la section** puis son contenu (padding 0 14 12, espacement 10). **Aucun chiffre ni statut répété** entre l'en-tête et la barre d'outils.

| Section | Responsabilité | Source | Barre d'outils |
|---|---|---|---|
| **Console** | uniquement le **build** | `ConsoleBuild` / `EtatConsole` | filtres `Tout / Erreurs n / Système` · Vider · Copier |
| **Problèmes** | uniquement les diagnostics du **moteur de langage (LSP)** | `jo.joide.ide.langages` (diagnostics) | bascule *Fichier / Projet* · filtres de gravité *Erreurs / Avert.* (sans chiffres) |
| **Journal** | le journal de **l'IDE lui-même** | `JournalDiagnostic` (borné) + événements INDEX, SYNC, AGENT, MCP, BUILD (service) | filtre de niveau (Tout / Avert. / Erreurs) · Vider · Copier |
| **Logcat** | la sortie de **l'application de l'utilisateur** | relais de journal (voir ci-dessous) | puce processus (`jo.compta · pid 4821 ▾`) · puce niveau (`Verbose ▾`) · Pause · Vider · Copier · champ de filtre (tag/texte) |

- **Console** : filtres `Tout / Erreurs n / Système` (n = erreurs **de build**, seul compteur de la barre d'outils) ; bande de 3 cartes de phase (poids égal, fond surfaceHaute, rayon 12, padding 6/10 ; nom 13, valeur 12 texte2 ; terminée = bord intérieur 1 dp réussite, en échec = bord erreur, non atteinte = alpha 50 % « — ignoré » ; tap = filtre de phase) ; transcript (fond puits, rayon 14, padding 10/12, mono 12/19, sélectionnable) coloré : phase accent, tâche texte, avertissement alerte, erreur erreur, succès réussite, système texte2 ; plafond 400 lignes **annoncé**. Lignes d'erreur cliquables → ouvre le fichier à la ligne. **Aucun bouton Construire/Arrêter ici** : l'action vit dans le FAB (qui devient « Arrêter » pendant un build).
- **Problèmes** : lignes groupées par fichier (en-tête 12 semi-gras texte2 « Facture.java · 2 ») ; glyphe ✕/▲, message 13/18 texte, sous-ligne « ligne 19 » mono 11,5 texte2 ; séparateur bordFin ; tap → ouvre le fichier à la ligne et **replie la feuille à la barre**. **Jamais de message de build ici** (il n'est visible qu'en Console). État vide : « Aucun problème » ; index en cours : « Analyse en cours… » (franchise, pas de liste vide muette).
- **Journal** : lignes `HH:MM:SS` (texte3) + étiquette de source (accent : INDEX, SYNC, AGENT, BUILD ; MCP en alerte si échec) + message ; mono 12/19. Le tap sur la **pilule d'index** de la barre haute ouvre cette section en filtrant INDEX.
- **Logcat** : lignes `D/Tag  message` colorées par niveau (D réussite, I défaut, W alerte, E erreur, traces d'exception en erreur). **Mécanisme (à trancher dans l'ADR)** : l'application de l'utilisateur est un autre UID, l'IDE ne peut pas lire son logcat sans `READ_LOGS`. Option recommandée : en build *debug*, JoIDE ajoute un petit relais de journal à l'APK (fournisseur de contenu ou socket local) qui renvoie les lignes à l'IDE ; option de repli : `READ_LOGS` accordé une fois par `adb`. État vide annoncé : « Application non lancée » ; processus terminé : « Processus terminé (code n) » en alerte.
- **Aperçu** (section conditionnelle) : n'apparaît que si l'onglet actif est une mise en page ; remplace l'aperçu plein écran du téléphone (lot ultérieur, derrière le même `EtatApercu`).
- États de la Console : vide (« aucun build dans cette session »), en cours, réussi, échoué (première erreur en tête + bouton « Ouvrir le fichier »), interrompu (« Build interrompu : l'IDE survit »).

### 3.7 FAB Construire
Hauteur 48, rayon 16, padding 0 18 0 14, fond réussite, texte `#06210F` 14 gras, icône ▶ + libellé « Construire », ombre réussite 25 % (0/8/22) + noire 40 % (0/2/6). Position : fin 16 dp, **bas = 84 dp** (feuille repliée 72 + 12). Disparaît (alpha 0, 200 ms) dès que la feuille dépasse la barre ou que le clavier est visible. Pendant un build : pilule accent « build #n — phase » ; **elle porte l'arrêt** (comportement existant `surArreter`) et un tap ouvre la feuille sur Console. C'est le seul endroit qui lance ou arrête un build.

### 3.8 Tiroir fichiers (`TiroirFichiers`, 300 dp, poussé depuis la gauche)
L'éditeur entier se translate de 300 dp (240 ms) ; voile noir 50 % sur le contenu poussé, tap = fermer. Fond surface, padding haut = inset de barre d'état. Ouverture : bouton ☰, balayage vers la droite **seulement si l'éditeur est déjà au début horizontal** (`canScrollHorizontally(-1) == false` pour tout le geste ; jamais de prise de bord qui vole le défilement de l'éditeur).
- **Sélecteur** (fond fond, rayon 12, padding 3) : items de poids égal, 13 sp, actif = fond surfaceHaute rayon 9. **Règle de franchise : n'afficher le sélecteur que si ≥ 2 sections sont réellement implémentées** ; la maquette montre Fichiers · Structure · Recherche pour illustrer, mais tant que seul « Fichiers » existe, afficher un simple titre « Fichiers ».
- **Pas de rangée projet** (le nom et « n modules » sont déjà dans la barre supérieure : aucun doublon). Actions 34×34 (zone tactile ≥ 48 dp via `TouchDelegate`) : nouveau fichier, nouveau dossier, tout réduire, localiser le fichier actif. Champ de filtre : 36 dp, rayon 12, fond fond, 13 sp texte3.
- Arbre : lignes 38 dp, rayon 10, retrait 16 dp par niveau, chevron 14 dp, pastille de type 20×20 rayon 6 (mêmes teintes que les onglets ; dossier = glyphe texte2), nom 14. **Ligne active** : fond accent 12 % + barre 3 dp accent (inset 9 dp). **Sorties de build** : texte3 + puce `build #n…` tant qu'elles se construisent, jamais présentées comme sources. Plafond 400 nœuds annoncé (existant). Appui long → `PopupAncre` (Nouveau ▸, Renommer, Dupliquer, Supprimer en erreur).

### 3.9 Tiroir agent (`TiroirAgent`, 340 dp, depuis la droite, au-dessus de tout)
Fond surface, bord gauche 1 dp bord, padding haut = inset. Fermeture : ✕, voile, retour système.
- **En-tête** (padding 6/10/10, bord bas 1 dp bordFin) : « Agent » 16 semi-gras ; puce modèle (accent tonal, `▾`) → `PopupModeles` ancré (recherche, sections Favoris / Récents / Tout, étoile qui bascule sans fermer) ; puce mode (neutre : *Demander / Auto / Plan*, persisté par projet) ; historique ⟲ ; ✕.
- **Transcript** (padding 12, espacement 10) : message utilisateur aligné à droite, max 84 %, fond surfaceHaute, rayon 16/16/4/16, 14 sp ; **Réflexion** : ligne 12,5 texte2 `▸ Réflexion · 4 s`, ouverte pendant le flux, **pliée à la fin**, rouvrable ; **carte d'outil** (fond fond, bord 1 dp bordFin, rayon 14, en-tête 9/12) : glyphe d'état (✓ réussite, ● accent pulsant, ✕ erreur), nom mono 12,5 semi-gras, cible, libellé droit (« 14 lignes ▸ » pliée par défaut / « à approuver ») ; **permission inline** : bloc diff (fond puits, mono 11,5/18 ; ajout réussite 10 %, retrait erreur 10 %, **borné à 12 lignes visibles** + « Voir tout » → dialogue plein écran ADR-É11), rangée de 2 boutons 36 dp rayon 11 : *Refuser* (surfaceHaute) / *Appliquer* (accent, texte `#0B1426`) ; **fermer ou ignorer = refuser** (défaut sûr conservé) ; texte de l'assistant 14/21 sans bulle ; cartes `question` (puces tapables, « Autre », « Passer ») et `erreur` actionnable (un verbe par genre, ADR-É11 §7) conservées.
- **Ligne d'activité** au-dessus du composeur : point 8 dp accent qui pulse (1,2 s) + libellé du pas (« Réflexion… », « Exécution de <outil>… », « Écriture… », « Attente… ») + écoulé en secondes. **Ligne d'usage** mono 11,5 texte3 : « 14 120 in · 903 out · 11 240 cached ». Bande d'usage ≥ 80 % d'une limite (tap → écran Usages existant).
- **Composeur** : marge 0/10/12, fond fond, bord 1 dp bord, rayon 18, padding 10/12 ; puces de pièces jointes (fichier courant, images) ; zone de saisie 14 sp texte3 en placeholder « Demander à l'agent… » ; rangée basse : bouton ＋ (menu d'ajouts existant) 32 dp, **envoyer** 36 dp rayon 12 fond accent (devient ■ Arrêter pendant un tour).

### 3.10 Retour système (ordre exact)
dialogue/popup → tiroir agent → feuille (plein → mi → barre) → tiroir fichiers → aperçu plein écran → confirmation de fermeture du projet → accueil. Jamais de saut d'étape ni d'état zombie.

### 3.11 Grand écran (≥ 720 dp)
Hors de la maquette : conserver le comportement actuel (rail, arbre amarré 240 dp, aperçu côte à côte, console amarrée) en le re-câblant sur les nouveaux layouts `layout-w720dp/atelier.xml` ; la feuille devient un panneau amarré avec les mêmes sections (en-tête sur une ligne, puces dans l'en-tête).

## 4. Lots (un commit par lot, `./gradlew check` vert)

1. **L1** : ADR-067, ViewBinding, jetons XML + thème + `JetonsXmlTest`, icônes vectorielles.
2. **L2** : barre supérieure, onglets, fil d'Ariane, barre de symboles, gouttière/ligne courante (layouts + bindings).
3. **L3** : `FeuilleBasse` + `EtatFeuille` + sections Console (build), Problèmes (LSP), Journal (IDE), Logcat (appli) ; relais de journal pour Logcat ; FAB réancré.
4. **L4** : tiroir fichiers (arbre, actions, filtre, menu contextuel, balayage conditionnel).
5. **L5** : tiroir agent (layouts d'items, `PopupModeles`, composeur) ; extraction des contrôleurs agent.
6. **L6** : extraction de `ControleurBuild`, `EcranAtelier` < 500 lignes, retrait de `Dock` devenu inutile, grand écran re-câblé.

## 5. Critères d'acceptation

- Capture de chaque état à 370 dp de large comparée à la maquette : mêmes dimensions, couleurs, textes ; aucun écart non justifié écrit dans le rapport de lot.
- `grep -R "new LinearLayout(\|new FrameLayout(" ` sur les écrans migrés : 0 résultat hors vues personnalisées.
- Tests JVM verts : états existants inchangés, `EtatFeuilleTest` (cycle de détentes, ancrage, fling), `JetonsXmlTest`.
- Aucune information n'apparaît à deux endroits avec le même rôle : en-tête de section ≠ barre d'outils (§3.6) ; un seul en-tête, pas de barre de navigation repliée ni de puces de section.
- `EtatFeuille` porte aussi la section active ; test du bouclage du glissement d'en-tête (4 sections) et du saut « problème suivant ».
- Zones tactiles ≥ 48 dp, `contentDescription` sur chaque bouton-icône, contrastes AA conservés (tests Graphite existants).
- Aucun nouveau composant AndroidX hors `androidx.databinding:viewbinding`.

## 6. Hors périmètre

Mode clair détaillé (mêmes rôles, `Graphite.CLAIR`, validé par les tests existants), sections Structure et Recherche du tiroir, édition par blocs, plugins, Git, Store.
