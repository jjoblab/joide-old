# PHASE A1 — RAPPORT (fondations AndroidX + Material 3)

- **Date** : 2026-11-19
- **Base** : A0 validée (`fb1c3b6`)
- **Lot** : A1 — socle commun, en TROIS commits (l'ADR AVANT toute
  dépendance, discipline du prompt) : `6f5f704` (ADR-085 + ADR-083 annoté),
  `f9ee3da` (dépendances, thèmes M3, jetons, tests, navigation),
  `b8f2306` (correctif C5 — commit séparé, exigé par le prompt).

## 1. Constats du prompt — vérifications A1

- **C6 (c) INFIRMÉ définitivement** : `verifierOmbreApk` (sondes du mur de
  chargeurs dans l'APK) et `MurEtancheTest` (surface `-keep` du jeu
  `tools`) ne portent AUCUN interdit AndroidX/Material — **aucun
  amendement nécessaire**, ils gardent leur rôle tel quel, preuves dans
  `PHASE-A0-RAPPORT.md`.
- **C6 (b) INFIRMÉ** : `compileSdk = 37` + `compileSdkMinor = 2` suffit
  pour appcompat 1.8.0 / core 1.19.1 / activity 1.13.0 / material 1.14.0
  (versions stables relevées sur Google Maven le 2026-11-19) — aucun
  relèvement, `targetSdk` reste 28.
- **Substitution des widgets (C6-e)** : les 22 layouts portent
  `LinearLayout`/`FrameLayout` (non substitués) et des `TextView` (→
  `MaterialTextView` sous la coque AppCompat : même rendu, couleurs et
  tailles explicites partout). Aucun `Button`/`EditText` déclaré. La vue
  du kit `Puce` (seule à étendre `TextView`) passe à
  `AppCompatTextView` — même rendu, l'alerte `AppCompatCustomView`
  disparaît.
- **API 30 résiduelle** : la lecture post-correctif ne trouve PLUS aucun
  `WindowInsets.Type|getInsets(` direct dans le module.

## 2. Changements

| Fichier | Nature |
|---|---|
| `docs/adr/085-architecture-vues-ecrans.md` | ADR complet : directives D1-D3, décisions 1-15, options, équivalents du kit, gabarit cible, compromis, plan A0-A9 |
| `docs/adr/083-…md` | annoté « Partiellement remplacé par ADR-085 » (§2) |
| `gradle/libs.versions.toml` | versions épinglées (règle §10) : androidxAppcompat 1.8.0, androidxCore 1.19.1, androidxActivity 1.13.0, material 1.14.0 |
| `app-android/build.gradle.kts` | `implementation` des quatre artefacts — exactement ceux que le code importe |
| `res/values/themes.xml` | parents `Theme.Material3.Dark/Light.NoActionBar` ; rôles M3 ← jetons Graphite existants ; formes/typographie branchées ; tous les `?attr/jo_*` conservés (`JetonsXmlTest` vert) |
| `res/values/dimens.xml` (nouveau) | rayons, grille, marges, gouttière, tuile, échelle typographique complète (en sp) |
| `res/values/styles.xml` (nouveau) | `TextAppearance.Jo.*` (héritent de `TextAppearance.Material3.*`, valeurs par `@dimen`) + `ShapeAppearance.Jo.*` |
| `res/values/bools.xml`, `res/values-w720dp/bools.xml` (nouveaux) | `est_grand_ecran` — parité `w720dp` ↔ `EtatAtelier.SEUIL_GRAND_ECRAN_DP` |
| `ui/ActiviteJoide.java` | `extends AppCompatActivity` + `monter(Function)` — l'ordre thème → inflation reste l'invariant |
| `ecrans/Ecrans.java` (nouveau) | fabriques d'`Intent`, extras, contrats typés (`OpenDocumentTree`, `GetContent`, `TakePicture`) |
| `MainActivity.java` | import SAF par contrat (`Ecrans.IMPORTER_DOSSIER`), `PICK_IMPORT=41` et `onActivityResult` supprimés |
| `ecrans/atelier/ControleurAgent.java` | les deux appels restants (47101/47102) annotés `@SuppressWarnings("deprecation")` avec renvoi à A7 (contrats prêts dans `Ecrans`) |
| `ecrans/atelier/EcranAtelier.java` | correctif C5 : `ViewCompat` + `WindowInsetsCompat` (plus d'API 30 directe) |
| `ui/Puce.java` | étend `AppCompatTextView` (rendu identique) |
| `ui/ThemesM3Test.java` (nouveau) | 6 tests JVM : parents M3, rôles définis et résolus vers des couleurs existantes, égalité Graphite ↔ dimens, styles (parents, `@dimen`, familles), rayons, seuil w720dp |
| `NOTICE`, `docs/third-party.md` | quatre artefacts tracés (Apache-2.0) |

## 3. Mesures avant/après

| Mesure | A0 | A1 |
|---|---|---|
| APK debug (R8 + shrink, arm64) | 14 567 869 o | **15 172 977 o (+605 108 o, +0,58 Mio, +4,2 %)** |
| Tests JVM `:app-android:test` | 334 verts | **340 verts** (7 `ArchitectureVuesTest` + 6 `ThemesM3Test`) |
| `verifierOmbreApk` | vert | **vert** (« APK sans ombre, éléments vitaux présents ») |
| Imports AndroidX/Material | 0 | 8 (appcompat, core, activity — tous dans la liste blanche ADR-085, `ArchitectureVuesTest` vert) |
| `dialogues_cadre` / `Dialogue.` | 24 / 58 | inchangés (A7) |

## 4. Validation

- `./gradlew :app-android:test` — 340 tests, 0 échec (dont `JetonsXmlTest`,
  `ThemesM3Test`, `ArchitectureVuesTest`, `Maquette*`, `Etat*`).
- `./gradlew :app-android:assembleDebug` — vert (2ᵉ tentative sous
  limitation de débit Maven Central) ; `verifierOmbreApk` vert.
- `./gradlew :app-android:lintDebug` — **9 erreurs, toutes préexistantes
  au commit de base** (`MissingSuperCall` sur `onBackPressed` de
  l'atelier, `NewApi` sur `Path#of`×3, `Stream#toList`,
  `transferTo`, `readAllBytes`×2, `Files#readString`) — signalées au
  propriétaire, hors périmètre du chantier (moteur/build, §8) ;
  **aucune nouvelle alerte** du chantier : l'erreur
  `AppCompatCustomView` née du socle a été corrigée dans le même lot
  (`Puce`), et l'API 30 d'hier a disparu.
- **`./gradlew check` racine : ROUGE DÉJÀ AU COMMIT DE BASE** — le
  contrôle `checkLanguagePolicy` signale **1313 problèmes typographiques
  préexistants** (preuve : worktree sur `0e15c16`, sortie complète
  archivée) ; les documents DU CHANTIER sont eux conformes (espaces
  insécables posés, zéro flag). Le lot ne peut pas rendre vert un check
  déjà rouge sans toucher 446 lignes de `CHANGES.md` et 12 ADR — hors
  périmètre (§8), décision laissée au propriétaire.
- **Contrainte d'environnement** : la machine d'exécution (4 Go partagés)
  voit R8 tuer le démon Gradle avec le tas historique de 3 g — les
  assemblages de validation ont été menés à `-Xmx1800m` (aucun fichier du
  dépôt modifié pour cela, les valeurs d'`org.gradle.jvmargs` restent
  celles du dépôt).

## 5. Ce qui n'a PAS pu être exécuté

- Aucun appareil/émulateur : états visuels à vérifier à la main —
  - rendu des 22 layouts existants sous `MaterialTextView` (rendu attendu
    identique : couleurs/tailles explicites) ;
  - clavier → barre de symboles + FAB sur API 28/29 (le Compat tombe sur
    `adjustResize`, comportement fiabilité à valider, prompt §2) ;
  - rotation/pliage de l'atelier — inchangé en A1 (C3 reste ouvert, lot A8).
- `lintRelease` non lancé (coût mémoire de l'analyse complète).

## 6. Écarts avec le prompt

- `Ecrans` arrive SANS câblage du retour système sur les écrans restants :
  le `OnBackPressedDispatcher` est disponible dès A1 (hérité
  d'`AppCompatActivity`), mais les `onBackPressed` existants (atelier,
  seul écran concerné) ne migrent qu'aux lots A6/A8 — le prompt plaçait
  l'infrastructure en A1, l'usage suit les écrans.
- Les contrats `GetContent`/`TakePicture` sont posés mais pas encore
  câblés (les codes 47101/47102 ne disparaissent qu'au lot A7, avec la
  refonte de `ControleurAgent`) — les appels survivants sont annotés et
  documentés dans le code même.
- Aucun `Widget.Jo.*` encore : aucun composant Material n'est consommé
  avant le pilote A2 (styles prêts à naître avec lui).

## 7. Risques résiduels et suite

- Les 9 erreurs lint préexistantes (dont `Path#of` API 34) attendent une
  décision du propriétaire — le chantier ne les masque pas.
- `colorOnError` ← `jo_fond` : couple faible-contraste accepté pour un
  rôle d'erreur rarement rempli (les composants D2 des lots suivants
  l'évalueront à l'usage).
- **Suite** : A2 — pilote `EcranCallbackMcp` + `MainActivity` (layouts
  racine, `MaterialCardView`, `MaterialButton`, `TextInputLayout`,
  `RecyclerView` des projets récents, `Widget.Jo.*`).
