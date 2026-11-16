# ADR-É1 — Analyse des écrans de CodeAssist (tyron12233) : ce que JoIDE retient

- **Statut** : Accepted (design — implémentation en lots futurs)
- **Date** : 2026-11-16

## Contexte

Premier test du propriétaire sur l'APK du CI (2026-11-16) : l'UI de P8
est fonctionnelle mais minimale — un bouton « Ouvrir un projet » qui
lance le sélecteur SAF système, un empilement programmatique de vues
du cadre, aucun écran de création, une console brute. Le propriétaire
exige un design moderne et professionnel, et mandate l'analyse de
CodeAssist de tyron12233 comme référence : « Je veux que tu consultes
CodeAssist de tyron12233 bien que dans son cas c'est compose, analyse
ces différents écrans (tous ces écrans) pour voir comment il faut
faire les nôtres. »

CodeAssist est la référence naturelle : un IDE complet qui tourne sur
appareil, avec création de projet, atelier adaptatif et build local —
le même problème produit que JoIDE, trois ans d'avance sur l'UI.

## Méthode

Analyse sur les sources du dépôt public (branche `main`), pas sur les
captures : l'arborescence complète (`app/ide-ui-*`, `app/ide-android`)
puis lecture des écrans clés — `HomeScreen`, `ProjectsHomeScreen`,
`CreateProjectScreen`, `EditorScreen`, `RunScreen`, `SettingsHubScreen`,
`Theme.kt`, `ScreenTransition.kt`. Le code est plus précis qu'une
capture : il porte les commentaires de conception, les leçons
corrigées et les mesures (typographie, seuils d'adaptation). GPL-3.0 :
analyse seule, lecture — aucun code n'est repris dans JoIDE
(clean-room, ADR-008 ; seule la structure et les leçons sont retenues,
pas une ligne).

## Inventaire complet, par famille, avec verdict pour JoIDE

| Famille CodeAssist | Écrans | Verdict JoIDE v2 |
|---|---|---|
| Navigation et thème | `ScreenTransition` (transitions différenciées mobile/bureau), `Theme`/`AppThemeScheme`/`AppThemes` (rôles Material, thèmes contribué par greffons), séries de tests `*Snapshot` | adapter — transitions et jetons, sans les greffons de thème (palette fixe v2) |
| Accueil et projets | `HomeScreen` (onglets + barre basse / barre latérale ≥ 840 dp), `ProjectsHomeScreen` (cartes, reprise, import, état vide), `YouScreen` (compte) | adopter l'essentiel — voir leçons 1 et 2 ; onglets Store/Apprentissage/Défis écartés (plateforme communautaire hors produit) |
| Création | `CreateProjectScreen` (galerie de modèles + formulaire piloté par le modèle), `AppRenameDialog` | adopter — leçon 3 |
| Atelier | `EditorScreen` (+`Support`), `BlockOutlineScreen` (édition par blocs) | adopter l'adaptatif — leçon 4 ; l'édition par blocs écartée (celeris n'a pas ce modèle) |
| Build et exécution | `RunScreen` (console, phases, problèmes), `RunConflictDialog`, `LogsScreen` | adopter — leçon 5 ; `LogsScreen` en backlog diagnostic |
| Réglages | `SettingsHubScreen`/`SettingsScreen`, `ColorSchemeScreen`, `CodeStyleScreen` | adopter le hub ; éditeurs de schéma et de style de code en backlog |
| Outils spécialisés | `SdkManagerScreen`, `KeystoreManagerScreen`, `IconManagerScreen`, `AppIconStudioScreen`, `DependenciesScreen`, `ModuleConfigScreen`, `LanguageServersScreen`, `PluginsScreen`, `SearchScreen` | écarter de v2 — JoIDE n'a ni SDK ni keystore gérés dans l'app, pas de greffons, pas de serveurs configurables (moteurs embarqués) ; `SearchScreen` et `DependenciesScreen` au backlog ; `ModuleConfigScreen` écarté (le `joide.toml` s'édite dans l'éditeur de texte) |
| Communauté et distribution | `ProjectsStoreScreen`, `StoreItemScreen`, `StoreSearchScreen`, `ExploreFeedScreen`, `SubmitProjectScreen`, `ProjectSharingScreens`, `PublisherProfileScreen`, `PublishingGuideScreen`, `ModerationScreen`, `Learn*` (4), `Challenge*` (2), `DailyChallengeScreen`, `FalseErrorReportDialog`, `ScreenshotViewer` | écarter — plateforme communautaire et pédagogie : pas le produit JoIDE (construire, pas animer) |
| Git | `AccountsScreen`, `BranchesScreen`, `CloneScreen`, `DiffScreen`, `GitHubScreen`, `HistoryScreen`, `StashesScreen` (vcs-ui) | écarter de v2 — backlog lointain |
| IA | `AgentPermissionDialog` (services/agent-ui) | révisé — analysé en profondeur sur le code (ADR-É9) et planifié en lots N-R (ADR-É10) après réouverture du sujet par le propriétaire |
| Dialogues de franchise | `CloseProjectDialog`, `UnsavedCloseDialog`, `ErrorDialog`, `PermissionDialog`, `IndexStatusDialog`, `NewFileDialog`, `FileOperationsDialogs` | adopter le principe — leçon 5 |

## Leçons retenues (avec leurs preuves dans le code source)

1. **L'accueil est un hub de projets ; l'import est secondaire.**
   `ProjectsHomeScreen` salue (« greeting() » par heure), trie par
   dernière ouverture, met en avant une carte « Reprendre » et un
   bouton « Nouveau projet » ; l'import d'un dossier existant est une
   ligne tonale discrète à côté. Le commentaire de `ImportCard` est
   la leçon exactement inverse de notre état actuel : « enfouir
   l'import dans une feuille rendait invisible le deuxième moyen le
   plus courant de commencer » — JoIDE aujourd'hui n'a que ça, au
   premier plan, derrière le sélecteur système. La création passe
   première, l'import SAF devient l'action secondaire.

2. **La carte projet : une ligne de méta, tout le reste dans un
   menu.** `LocalProjectCard` porte un commentaire d'aveu : la carte
   affichait le chemin, deux puces, l'âge, un chevron ET deux
   boutons — « le problème "lequel je tape ?" signalé par les
   utilisateurs » ; la carte retenue fait le nom (taille moyenne), la
   tuile tonale 46 dp avec glyphe par type, une seule ligne
   « genre · modules · âge », et un unique bouton ⋮ pour renommer,
   exporter, supprimer. Taper la carte ouvre le projet. Le tap sur la
   rangée ne fait jamais deux choses.

3. **La création est une galerie de modèles, puis un formulaire
   piloté par le modèle.** `CreateProjectScreen` : étape 1, galerie
   (recherche, puces de catégorie, cartes groupées par famille) ;
   étape 2, « Configure » dont le formulaire est entièrement dérivé
   des `UiProjectParam` du modèle choisi — « un nouveau modèle
   apporte ses propres champs sans changement de l'UI ». Les valeurs
   par défaut viennent du modèle (`defaultName`, `defaultValue`).
   JoIDE garde le principe : les champs de l'étape Identité sont
   déclarés par le modèle, pas codés dans l'écran.

4. **L'atelier est adaptatif, avec un seuil net.**
   `COMPACT_BREAKPOINT = 720.dp` : en dessous, un volet d'édition
   unique, l'arborescence en tiroir poussé depuis la gauche, la
   console en dock bas ; au-dessus, rail latéral · arborescence ·
   éditeur · console amarrée. Le retour système ferme d'abord les
   surcouches (dialogues, palette, feuilles, console, tiroir) —
   jamais un état zombie. JoIDE reprend le seuil 720 dp, le tiroir
   poussé et le dock ; le rail latéral reste en option large
   (backlog) car JoIDE vise d'abord le téléphone.

5. **La franchise est un motif d'UI, pas une note bas de page.**
   `CloseProjectDialog` demande avant de quitter (fichiers modifiés,
   build en cours) ; les sorties de build s'affichent estompées dans
   l'arborescence pendant qu'elles se construisent ; une pilule
   flottante ramène à un build en tâche de fond ; le statut de l'index
   est une puce dans la barre supérieure qui ouvre son dialogue dédié.
   C'est déjà la doctrine JoIDE (« jamais d'omission muette », lot F) :
   elle devient visible dans l'UI.

6. **Le système porte l'ensemble.** Typographie dédiée au code
   (`code` 13,5 sp / 22 d'interligne, famille monospace séparée de
   l'UI), jetons de mouvement (`Motion.BASE`, `FAST`, courbes), seuil
   d'adaptation unique, états d'écran purs testables hors backend
   (séries `*Snapshot` : les écrans rendent avec des données seules).
   JoIDE transpose : jetons dans l'ADR-É2, et chaque écran gardera un
   état pur testable JVM — la doctrine P8 « l'UI reste mince »
   (`MainActivity` : la logique testable vit dans les classes pures)
   est conservée ; le design ne déplace pas la logique dans les vues.

## Ce que JoIDE ne prend pas, et pourquoi

Compose, d'abord : la pile JoIDE est en vues du cadre (celeris est une
bibliothèque de vues personnalisées ; l'application n'a aucune
dépendance UI AndroidX) ; basculer l'application sur Compose pour
imiter la technique serait un changement de pile, pas de design. Ce
qui est retenu de CodeAssist est structurel — hub, cartes, galerie,
adaptatif, dock, franchise — et se dessine aussi bien en vues.
Ensuite les surfaces produit : Store, Apprentissage, Défis, IA, Git,
gestion de SDK/keystore/icônes : hors périmètre, listées pour que
l'architecture des écrans v2 n'en dépende pas (l'ADR-É8 garde des
sections extensibles, jamais des onglets réservés). L'IA, écartée ici
sur la seule vue des écrans, est réouverte par le propriétaire
(2026-11-16) : l'ADR-É9 analyse le code de l'agent et l'ADR-É10 en
planifie les lots — l'écran de chat sera conçu en ADR-É11 au lot P,
sur la méthode de la présente série.

## Conséquences

- Les ADR-É3 à É8 spécifient chaque écran retenu ; l'ADR-É2 fixe les
  jetons et la stratégie d'implémentation (vues du cadre, zéro
  AndroidX, composants dessinés, états purs JVM).
- Les maquettes `design/*.html` illustrent chaque écran ; elles sont
  contractuelles pour la structure (sections, états, franchise), pas
  pour les valeurs exactes de pixels.
- L'implémentation viendra en lots futurs (à planifier avec le
  propriétaire) ; le code P8 n'est pas touché par cette série.
