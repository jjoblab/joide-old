# ADR-083 — Atelier v2 : vrais layouts XML + ViewBinding, la seule dépendance AndroidX tolérée

**Statut :** Partiellement remplacé par [ADR-085](085-architecture-vues-ecrans.md)
(§2 : l'interdit AndroidX/Material est levé par les directives D1-D3 du
propriétaire ; AppCompat, Material 3, RecyclerView, ConstraintLayout,
CoordinatorLayout et DrawerLayout sont autorisés ; seul Compose reste
exclu). Le reste de la décision (ViewBinding, `Graphite` source de vérité,
`JetonsXmlTest`, clean-room) demeure.

Date : 2026-11-18 — lot L1 du chantier « Refonte de l'atelier (v2) »
(maquette contractuelle `docs/ecrans/design/atelier-v2.html`,
prompt `prompt-agent-atelier-v2.md` — l'« ADR-067 » du prompt
renuméroté ici, l'ADR-067 du dépôt étant déjà pris par la chaîne
différée).

## Contexte

L'atelier (`EcranAtelier.java`, 3 430 lignes) construit toute son UI
en code : chaque vue, padding et couleur passe par `GraphiteVue.*`.
La refonte v2 exige de **vrais layouts XML** pour toute l'application
(§2 du prompt : « Aucun `new LinearLayout(` ne doit subsister dans
les écrans migrés ») et des bindings typés — le câblage
`findViewById`/cast étant aussi error-prone que l'UI en code.

Or la règle « vues du cadre uniquement, zéro AndroidX »
(`docs/ecrans/002-systeme-design.md`, « Implémentation — vues du
cadre uniquement, zéro AndroidX » ; ADR-É2 du prompt) interdit tout
AndroidX UI : pas de Compose, pas de Material Components, pas de
RecyclerView/ConstraintLayout/Fragment. La décision tient : elle
protège le design Graphite contre les styles imposés et l'APK contre
le poids.

## Décision

1. **`androidx.databinding:viewbinding` devient la SEULE dépendance
   AndroidX tolérée** — ajoutée automatiquement par
   `buildFeatures { viewBinding = true }` (AGP), licence Apache-2.0,
   inscrite au `NOTICE` et à `docs/third-party.md`. C'est une
   bibliothèque de LIAISON, pas d'UI : elle n'apporte aucun composant
   visuel, aucune vue, aucun style — les classes `*Binding` générées
   sont du code À NOUS (générées depuis nos layouts), la partie
   runtime est minuscule (`ViewBinding` l'interface + `getBinding`
   utilitaires). Elle ne crée aucun précédent pour AndroidX UI :
   RecyclerView, ConstraintLayout, Compose et Fragment restent
   interdits ; toute nouvelle dérogation exige un ADR dédié.
2. **Les vues du cadre restent la loi** : FrameLayout, LinearLayout,
   ScrollView, et les vues personnalisées du kit `ui/` (constructeur
   `(Context, AttributeSet)` obligatoire, attributs dans `attrs.xml`)
   déclarées dans le XML.
3. **`Graphite.java` reste la source de vérité des jetons** (§1 du
   prompt). Les couleurs vivent désormais AUSSI dans
   `res/values/colors.xml` (sombre + clair), exposées aux layouts par
   des attributs de thème (`res/values/attrs.xml` : `jo_fond`,
   `jo_surface`, `jo_surface_haute`, `jo_puits`, `jo_bord`,
   `jo_bord_fin`, `jo_texte`, `jo_texte2`, `jo_texte3`, `jo_accent`,
   `jo_reussite`, `jo_alerte`, `jo_erreur`, `jo_violet`, les 7
   couleurs de syntaxe, les tonaux). Les layouts n'utilisent QUE
   `?attr/jo_*` — jamais une couleur codée en dur (franchise §0.4).
   `JetonsXmlTest` (JVM) vérifie l'égalité `colors.xml` ↔ `Graphite` :
   les deux représentations ne peuvent pas diverger.
   Divergence constatée et tranchée : le prompt v2 cite
   « texte sur réussite #06210F, sur accent #0B1426 » alors que
   `Graphite` porte `#07130C` / `#0B1424` — Graphite l'emporte
   (source de vérité, §1 du prompt) et le test l'ancre.
4. **Les thèmes** `Theme.JoIDE.Sombre` et `Theme.JoIDE.Clair`
   (`res/values/themes.xml`, parent `android:Theme.Material.NoActionBar`
   — le thème du cadre, pas Material Components) posent les
   `?attr/jo_*` ; l'application se fait par `setTheme()` avant
   l'inflation, selon `Reglages` (ADR-048). Le mode clair détaillé
   reste hors périmètre (§6 du prompt) mais ses jetons existent déjà
   dans `Graphite.CLAIR` et sont posés par le thème clair — aucun
   jeton inventé.
5. **Clean-room maintenu (ADR-008)** : CodeAssist est GPL-3.0 ; rien
   de son code n'entre ici. Les structures décrites par le prompt v2
   (layouts par zone, bindings par vue) sont les nôtres.

## Conséquences

- `viewBinding = true` est actif dans `app-android` dès ce lot ; les
  classes `*Binding` apparaîtront au fil des lots L2-L6 (aucun layout
  encore : aucune classe générée, aucun risque).
- Le contrôle « zéro AndroidX UI » passe de « aucun import androidx »
  à « aucun import androidx SAUF androidx.databinding/viewbinding » —
  le garde-fou s'ajuste dans le lot suivant si un contrôle de
  politique existe.
- `JetonsXmlTest` doit rester vert : modifier `Graphite` sans toucher
  `colors.xml` (ou l'inverse) échoue la suite.
- Les captures d'écran de conformité (§5 du prompt) comparent chaque
  état à la maquette à 370 dp — 1 px maquette = 1 dp.
