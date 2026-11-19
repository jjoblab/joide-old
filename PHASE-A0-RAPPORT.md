# PHASE A0 — RAPPORT (filet de sécurité)

- **Date** : 2026-11-19
- **Base** : `0e15c16` (main) — le prompt v2 annonce « dernier commit analysé : `6f2e899` » ; le dépôt a avancé d'un commit (JitPack pour les briques) sans toucher `app-android`.
- **Chantier** : ADR-085 — architecture des vues (AndroidX + Material 3, popups, directives D1-D3 du propriétaire).
- **Lot** : A0 — mesurer et verrouiller **sans rien changer au code de l'app**.

## 1. Ce qui est fait

Deux livrables, aucune modification de comportement :

- **`app-android/src/test/java/jo/joide/app/android/ArchitectureVuesTest.java`** — sept tests JVM (lecture de fichiers uniquement) qui verrouillent l'état actuel avec des **listes d'exceptions décroissantes** :
  1. écrans (`MainActivity` + `ecrans/**`) sans vue instanciée en code — 7 fichiers excusés aujourd'hui ;
  2. aucune classe de dialogue interdite (D3) — `ui/Dialogue.java` seule excusée ;
  3. usages de la maison `Dialogue.` — 9 fichiers excusés (58 usages) ;
  4. imports AndroidX/Material dans la liste blanche ADR-085 (Compose et `DialogFragment` interdits absolus) — aucune exception ;
  5. chaque layout a un consommateur `*Binding` — aucune exception ;
  6. aucune couleur littérale en layout — `feuille_entete.xml` excusé (constat nouveau, voir §3) ;
  7. aucun `android:text` littéral — aucune exception.

  Discipline : une exception qui ne correspond plus à aucune faute **échoue** (« retire-la ») — la liste ne peut que décroître, vide en A9.
- **`scripts/mesures-architecture-vues.sh`** — l'inventaire chiffré de l'Annexe B, reproduit fidèlement (grep des mêmes motifs, mêmes chemins), avec en plus la répartition par fichier (nourrit les listes d'exceptions).

## 2. Mesures de base (Annexe B, reproduites)

| Mesure | Valeur |
|---|---|
| Vues instanciées en code (`new ($VUES)(`) | **203** (97 TextView, 73 LinearLayout, 11 FrameLayout, 10 View, 9 ScrollView, 2 HorizontalScrollView, 1 EditText) |
| Répartition écrans/contrôleurs | ControleurAgent 48, EcranCreation 34, EcranParametres 20, MainActivity 14, EcranAtelier 11, VueAgent 10, VueApercu 9, VueArbre 6, Onglets 5, EcranCallbackMcp 4, ControleurFichiers 1… |
| Dialogues du cadre (`AlertDialog`…) | **24 occurrences, toutes dans `ui/Dialogue.java`** (9 fabriques `AlertDialog.Builder`) |
| Usages `Dialogue.` (maison) | **58** dans 9 fichiers (ControleurAgent 12, EcranParametres 16, ControleurFichiers 11, MainActivity 9, EcranAtelier 4, ControleurEdition 3, VueApercu/VueArbre/VueAgent 1 chacun) |
| Imports AndroidX/Material | **0** (dont 0 Compose) — confirmé |
| `palette.*` en code | **374** |
| `GraphiteVue.dp(` en code | **414** |
| Couplage contrôleurs → `atelier.*` | **685** (Agent 348, Edition 140, Fichiers 113, Build 84) — confirmé |
| Taille des écrans | MainActivity 749, EcranAtelier 1 522, EcranParametres 1 002, EcranCreation 879, EcranCallbackMcp 171 — confirmé |
| Layouts | 22, tous consommés par un `*Binding` ; 0 `dialog_*.xml` (les dialogues de `Dialogue.java` sont entièrement programmatiques — rien à supprimer côté XML) |
| Littéraux | 220 `dp`, 56 `sp`, 2 `<include>`, 0 `<merge>`, 0 `<ViewStub>`, 0 styles, 0 `dimens.xml` |
| Qualificatifs (`layout-*`, `values-w*`) | **aucun** — confirmé |
| `onSaveInstanceState` / `onConfigurationChanged` / `configChanges` | **0 / 0 / 0** — C3 confirmé en lecture |
| `findViewById` résiduels | 3 |
| `runOnUiThread` | 38 |
| `contentDescription` | 15 pour 105 `@+id` |
| APK debug (R8 + shrink, arm64) | **14 567 869 octets** — `verifierOmbreApk` vert |

## 3. Constats du prompt : confirmés / infirmés / nuancés

| # | Verdict | Preuve |
|---|---|---|
| C1 | **confirmé** | 0 `activity_*.xml` dans `res/layout/` (22 layouts listés, aucun racine d'écran) |
| C2 | **confirmé** | 685 accès `atelier.*` des 4 contrôleurs ; `EcranAtelier` 1 522 l. |
| C3 | **confirmé en lecture** | `grep onSaveInstanceState\|onConfigurationChanged\|configChanges` → 0 ; `grandEcran` lu une fois (l. 289) ; vérification sur appareil laissée au propriétaire |
| C4 | **confirmé** | MainActivity/EcranParametres/EcranCreation/EcranCallbackMcp entièrement programmatiques (14/20/34/4 vues instanciées) |
| C5 | **confirmé** | `EcranAtelier.java:547-549` : `insets.getInsets(android.view.WindowInsets.Type.ime())` — API 30, `minSdk` 28, sans garde ; `lintDebug` non exécuté ici (voir §5), la lecture suffit à confirmer le risque |
| C6 | **nuancé** | (a) **confirmé** : 0 dépendance AndroidX/Material hors viewbinding ; (b) **infirmé** : `compileSdk = 37` (+`compileSdkMinor = 2`) dans les conventions — **largement suffisant** pour AppCompat/Material 1.12+ (aucun relèvement nécessaire à ce stade, à réévaluer à l'épinglage) ; (c) **infirmé** : ni `verifierOmbreApk` (sondes `jo.joide.build.android.*` uniquement) ni `MurEtancheTest` (surface `-keep` du jeu tools) n'interdisent `androidx`/`material` — **rien à amender dans ces garde-fous** (l'interdit ne vit que dans ADR-083 §2, à amender en A1) ; (d) **confirmé** : `Sombre`/`Clair` parent `android:Theme.Material.NoActionBar` (framework) ; (e) risque réel de substitution des widgets — à traiter en A1 (revue des 22 layouts). |
| O2 | **confirmé** | `ActiviteJoide extends android.app.Activity` (7 lignes ; `setTheme` avant `super.onCreate`) |
| O3 | 3 `findViewById` | à inspecter aux lots concernés (A2/A6) |
| Nouveau | `feuille_entete.xml:66` porte `#FFFFFF` (textColor de la pastille compteur) — **violation existante** d'ADR-083 §3 passée inaperçue ; excusée dans `ArchitectureVuesTest`, à corriger en consolidation (A9) |

Directives D1-D3 : mise en œuvre **non encore entamée** (c'est l'objet d'A1) ; faisabilité vérifiée — le socle build n'oppose aucun verrou (voir C6-c infirmé), `compileSdk` suffit, le thème posé avant inflation (`ActiviteJoide`) rend l'inflation M3 sûre dès que les parents deviennent `Theme.Material3.*`.

## 4. Écarts avec le prompt

- Le relevé des usages de `Dialogue.*` est **9 fichiers / 58 usages** (le prompt annonçait « à relever ») : la liste d'excusés du filet est calée dessus.
- `dialog_*.xml` : le prompt en annonçait la suppression en A7 — **il n'en existe aucun** (les dialogues vivent entièrement en code dans `Dialogue.java`) ; la suppression porte donc sur la seule classe.
- Le filet ajoute deux règles que le prompt n'énumérait pas explicitement en A0 mais qui servent sa décision 15 : texte littéral nul en layout, consommateur obligatoire par layout.

## 5. Validation

- `./gradlew :app-android:test` — **334 tests, 0 échec, 0 erreur** (dont `ArchitectureVuesTest`, 7/7).
- `./gradlew :app-android:assembleDebug` — vert ; **APK 14 567 869 o** ; `verifierOmbreApk` vert (« APK sans ombre, éléments vitaux présents »).
- `lintDebug` **non exécuté** (coût d'analyse du modèle complet sur une machine à 4 Go déjà occupée par R8 ; la lecture confirme C5, et lint devient vital aux lots qui suivent) — C5 confirmé par lecture directe.
- Aucun appareil : comportements (rotation, clavier, popups) restent à vérifier par le propriétaire.

## 6. Risques résiduels et suite

- Le filet ne mesure pas encore `palette.*`/`GraphiteVue.dp(` (objectifs I1/A9 : les injections doivent décroître) — le script de mesure les suit ; un verrou JVM sera posé en consolidation.
- Suite : **A1** — ADR-085, dépendances épinglées, thèmes `Theme.Material3.*` rôles ← Graphite (`ThemesM3Test`), `ActiviteJoide extends AppCompatActivity` + `monter()`, `Ecrans` (contrats), correctif C5 (`WindowInsetsCompat`) en commit séparé.
