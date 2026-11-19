# Rapport du lot A9 — Consolidation (chantier « Architecture des vues d'app-android — AndroidX + Material 3 + popups »)

**Base :** commit `aed6ab6` (lot A8). **ADR-085 → Accepted.**
**Critère du lot (§5) :** liste d'exceptions d'`ArchitectureVuesTest` VIDE ; rapport final chiffré (Annexe B) et delta de taille d'APK.

## 1. Constats confirmés / infirmés

- **CONFIRMÉ** : les couleurs des items de l'agent vivaient DEUX FOIS — le `?attr/jo_*` posé en XML dès A7, PLUS une ré-injection identique en code (10 sites : état vide, borne d'éviction, messages, réponse, raisonnement, cible/résumé/résultat d'outil, usage). Preuve : `grep -n "palette\." VueAgent.java` à la base — les 10 lignes supprimées correspondaient aux attributs déjà présents dans `item_*.xml`.
- **CONFIRMÉ** : la dernière exception du filet (`feuille_entete.xml`, note « à remplacer par un jeton dans un lot de consolidation » au A0) était la pastille du compteur `#FFFFFF` sur `@drawable/badge_erreur` (`?attr/jo_erreur`).
- **CONFIRMÉ (I3)** : `BarreHaut`, `BarreSymboles`, `TiroirFichiers` étendaient `FrameLayout` en gonflant un layout racine `LinearLayout` — un conteneur superflu par zone. `BarreSymboles` était le cas extrême : racine VIDE auto-fermée (les touches se posent par code).
- **CONFIRMÉ (I9)** : six vues du kit n'avaient plus AUCUN consommateur (imports réels vérifiés) : `Bouton` (2 restants en XML du composeur), `Carte`, `Champ` (import mort dans `VueAgent`), `Segment`, `Interrupteur`, `Onglets`, plus `TuileProjet` — ce dernier avec consommateurs vivants (conservé, voir §2).

## 2. Écarts avec le prompt et décisions (consignées dans l'ADR-085, « Évaluations du lot A9 »)

- **`Fab` CONSERVÉ** (la table §4.4 propose `FloatingActionButton`) : le FAB Construire est un composant à DOUBLE ÉTAT — rond « réussite » 56 dp avec triangle lecture DESSINÉ au Canvas au repos ; PILULE « build #n — phase » avec CROIX D'ARRÊT intégrée pendant le build ; trois actions distinctes. FAB/ExtendedFAB ne savent exprimer ni la métamorphose ni la croix d'arrêt intégrée ; couleurs Graphite calculées (texte sur réussite). La maquette `atelier.html` fait foi.
- **`Pilule`/`Puce` CONSERVÉES** (la table propose `Chip`) : rôles colorés DYNAMIQUES (`Pilule.Role` lu à l'exécution — index, build) et sélection dans l'état pur `ChoixPuce` (fond accent 16 %, bord 45 %, demi-gras : calculs, décision 8) ; le Chip M3 imposerait ripple, états et cibles stock, contraires à la maquette et à PIEGE-31. L'évaluation A6 de la barre haute demeure.
- **`TuileProjet` CONSERVÉE** : tuile DESSINÉE au Canvas, fond et glyphe calculés depuis `ChoixCarte` (décision 8 : couleurs calculées dynamiquement — jamais en XML).
- **`tiroir_agent.xml` garde sa racine** (pas de merge) : un `<include>` du composeur vit dedans.
- **Pastille du compteur** : `?attr/colorOnError` (rôle M3 déjà branché Graphite et TESTÉ par `ThemesM3Test`, décision 5) — remplace le littéral. En Sombre le texte passe de blanc à `jo_fond` sur `jo_erreur` : contraste nettement meilleur (≈ 8:1 vs ≈ 2,4:1), identité pastille rouge inchangée ; écart visuel délibéré, consigné.
- **`addAction(Bouton)` supprimé** : overload SANS appelant (la version `View` du A2 sert — les boutons Material de `item_etat_vide_accueil.xml`).

## 3. Changements

**Supprimés (kit remplacé par Material, zéro consommateur)** : `ui/Bouton.java`, `ui/Carte.java`, `ui/Champ.java`, `ui/Segment.java`, `ui/Interrupteur.java`, `ui/Onglets.java`, `ui/choix/ChoixBouton.java`, `ui/choix/ChoixChamp.java`, `ui/choix/ChoixTexte.java`, `ui/choix/ChoixSegment.java` ; tests `ChoixBoutonTest`, `ChoixChampTest`, `ChoixTexteTest` (les classes n'existent plus). `Carte.dessinerDossier` (Canvas) déménage dans `Icone.dessinerDossier` (public static — appelants : `Icone`, `EtatVide`, `TuileProjet`).

**Migrés vers Material (D2)** : `composeur_agent.xml` — les deux `<ui.Bouton>` deviennent `<MaterialButton>` `Widget.Jo.Button.Tonal` (fond `?attr/jo_surface_haute`, texte `?attr/jo_texte` : l'exact TONAL du kit, apparence inchangée) ; `VueAgent` porte `MaterialButton envoyer/arreter`.

**`<merge>` (I3)** : `atelier_barre_haut.xml` et `tiroir_fichiers.xml` racine `<merge>` — `BarreHaut`/`TiroirFichiers` étendent `LinearLayout` (orientation/gravité/retrait posés en classe, la hauteur 52 dp vit dans le parent) ; **`BarreSymboles` dissoute** : le layout racine était un LinearLayout VIDE — fichier et `AtelierBarreSymbolesBinding` supprimés, la classe se configure elle-même (orientation, gravité, retrait 6/5, fond, visibilité initiale, description). Les bindings merge suivent le précédent `VueConsole` (`inflate(gonfleur, this)`).

**Une seule voie de thémage** : 10 injections `palette.*` redondantes supprimées de `VueAgent` (couleurs déjà en XML) ; glyphe/couleur/description des icônes menu/fermeture/ajout posés en XML (`app:jo_icone_*` sur `tiroir_agent.xml`/`composeur_agent.xml`) ; saisie du composeur `?attr/jo_texte`/`?attr/jo_texte3` ; pastille `?attr/colorOnError`.

**Filet** : `ArchitectureVuesTest` — `LAYOUTS_EXCUSSES_COULEUR` VIDE (la sixième et dernière liste) ; javadoc à jour.

**Contrat maquette** : `MaquetteCreationTest` — le bloc couleurs du segment lit désormais le ColorStateList de la bascule (`res/color/bascule_fond.xml` alpha 0,18 `?attr/jo_accent` état cochée / `?attr/jo_surface_haute` ; `bascule_texte.xml` `?attr/jo_accent`/`?attr/jo_texte3`) — `ChoixSegment` d'hier a disparu avec le kit.

**Documentation** : ADR-085 → **Accepted** + section « Évaluations du lot A9 » (conservations motivées Fab/Pilule/Puce/TuileProjet, merges, thémage, badge) ; `docs/ecrans/README.md` — livraisons A0-A9 ; entrée `CHANGES.md`.

## 4. Mesures (Annexe B — base A8 → A9)

| Mesure | A8 | A9 |
|---|---|---|
| Vues instanciées en code (module, kit compris) | 22 | **12** (kit conservé : Canvas/composés) |
| … dans les écrans | 0 | **0** |
| Dialogues | 0 | **0** |
| Couleurs injectées (`palette.*`) | 205 | **154** (−51) |
| Conversions (`GraphiteVue.dp(`) | 152 | **109** (−43) |
| Couleurs littérales dans les layouts | 1 (excusée) | **0** |
| Layouts | 87 + 1 w720dp | **86 + 1 w720dp** (−1 dissous) ; 5 racines `<merge>` |
| `EcranAtelier` | 194 l. | 194 l. (inchangé, < 500) |
| Contrôleurs agent | < 600 l. | 590/597/151/200/241 l. (< 600) |
| Tests | 383 | **370** (−13 : tests de classes supprimées ; 0 échec) |
| APK debug | 15 853 241 o | **15 848 929 o (−4 312 o)** |
| Imports AndroidX/Material | liste blanche | **liste blanche** (Compose 0, DialogFragment 0) |

Chantier complet (A0 → A9) : vues instanciées **203 → 12**, dialogues **58+24 → 0**, `palette.*` **374 → 154**, `dp(` **414 → 109**, layouts racines **0 → 5 écrans (+ w720dp)**.

## 5. Validation

- `./gradlew … :app-android:test` — **370 tests, 0 échec, 0 erreur, 0 ignoré** (49 fichiers de résultats) ; `ArchitectureVuesTest` 9 tests verts à exceptions TOUTES VIDES.
- `./gradlew … :app-android:assembleDebug :app-android:verifierOmbreApk --rerun` — **verts** (APK 15 848 929 o).
- `./gradlew … :app-android:lintDebug` — **11 erreurs, 135 avertissements : les 11 d'A7/A8 EXACTEMENT** (8 `NewApi` préexistantes, 3 `DiffUtilEquals` faux positifs connus) — **aucune NOUVELLE erreur**.
- Parité `values/` ↔ `values-fr/` : **391 = 391 clés**, 0 seulement d'un côté.
- **Aucun appareil/émulateur** : la pastille (contraste), le composeur (habillage MaterialButton tonal), la barre haute/tiroir fichiers (un niveau de hiérarchie de moins — mise en page attendue identique), la barre de symboles (dissoute — mêmes touches) restent à vérifier visuellement par le propriétaire.

## 6. Risques résiduels et suite

- Comportements visuels des composants Material dans le composeur (états pressé/disabled du `MaterialButton` tonal : ripple M3) — à vérifier sur appareil.
- Les cibles < 48 dp des rails Graphite (32-40 dp, densité voulue de la maquette) demeurent : choix de design, signalé ici (aucune cible interactive sans `contentDescription` ne subsiste dans les fichiers touchés).
- La pastille en Clair : `colorOnError` = `jo_fond_clair` sur `jo_erreur_clair` — à vérifier sur appareil.
- Le chantier est clos ; suites possibles hors périmètre : γ (session statique) et re-mesure du coût de rechargement (clause « À revisiter » de l'ADR).
