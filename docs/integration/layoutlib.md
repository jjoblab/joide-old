# Contrat réel — LayoutLib (aperçu de layouts)

- **Dépôt** : `jjoblab/LayoutLib`, lecture seule, épinglé à `720e85d`
  (tag `v3.7.0`, `version.properties` : `VERSION_NAME=3.7.0`,
  `VERSION_CODE=30700`).
- **Groupe de publication** : `com.github.jjoblab.LayoutLib`.
- **Licence** : **aucun fichier `LICENSE` à la racine** ; la seule
  mention (README § Licence) est « Projet interne — aucun droit accordé
  sans autorisation explicite ». Le §6.8 règle 2a exige l'ajout de
  `LICENSE` (Apache-2.0) + `NOTICE` **avant l'intégration** : voir
  [DEMANDES.md](DEMANDES.md) D-1 — **close pour JoIDE** depuis la
  déclaration du propriétaire du 2026-11-16 (il détient le dépôt et
  autorise explicitement usage et redistribution par JoIDE ; le fichier
  `LICENSE` reste à ajouter côté brique pour l'extérieur).
- **Plateforme** : bytecode Java 17 construit sur JDK 21 ; minSdk 24,
  compileSdk 37.2 ; 9 modules.

## Constats du §6.8, revérifiés sur les sources

| Constat | Verdict |
|---|---|
| Java 17, minSdk 24, paquet `jo.layoutlib`, point d'entrée `RenderService` | exact — deux points d'entrée documentés : `RenderService` (prévisualisation, rappels) et la façade `MiniLayoutLib` ; les deux vivent dans `module-inflater` |
| Modules de rendu indépendants ; l'app dépend de l'ancien éditeur `code-editor` via JitPack | exact : `com.github.jjoblab.code-editor:cel-ui:3.49.2` (JitPack) — raison de plus d'exclure l'app |
| Aucun `LICENSE` à la racine | exact — toujours aucun à `720e85d` |

## Modules

Les **6 modules de rendu** (seuls intégrés par JoIDE) :

| Module | Rôle |
|---|---|
| `module-inflater` | XML → arbre de vues ; `RenderService`, `BridgeInflater`, `MiniLayoutLib` |
| `module-resources` | `@color/@string/@dimen/@array/@plurals` |
| `module-drawables` | `<shape>/<selector>/<vector>/…` |
| `module-themes` | `themes.xml`, `?attr/` |
| `module-attributes` | `<declare-styleable>`, `app:*` (registre framework + Material + AndroidX) |
| `module-layout` | measure/layout + Cassowary (autonome) |

Exclus : `layout-editor-app` (l'application, `com.android.application`,
dépend de `code-editor`), `module-compose` (spike), `validation`
(catalogue de comparaison, hors pipeline). `module-inflater` expose les
5 autres en `api` : dépendre de lui suffit.

## API publique (`jo.layoutlib.inflater.RenderService`)

- `RenderService(Context)` — lit densité/xdpi du Context ; installe un
  `AttributeRegistryImpl` par défaut (framework + Material + AndroidX).
- Configuration : `setTargetDimensions(int, int)` (défaut 1080×1920),
  `setDebounceMs(long)` (défaut 400), `setRenderCallback(RenderCallback)`,
  `setResourceResolver(ResourceResolver)`, `setDimensionConverter(...)`,
  `setDrawableResolver(...)`, `setThemeResolver(...)`,
  `setThemeDuProjet(String)`, `setAttributeRegistry(...)`,
  `setFactory2(LayoutInflater.Factory2)`.
- Rendu : `requestRender(String xml)` (débouncé), `requestImmediateRender(String)`,
  `cancelPending()` ; `isRendering()`, `hasValidRender()`.
- Sortie (`RenderCallback`, **thread principal**) :
  `onRenderSuccess(View rootView, long timeMs, int viewCount, int, int)`
  — un arbre de vues Android **vivant**, mesuré et layouté (pas une
  image) ; `onRenderError(String, Throwable)` ;
  `default onXmlInvalid(String)` (garde le dernier rendu valide).
- Cycle de vie : `release()` idempotent, à appeler depuis
  `Activity.onDestroy()` ; setters ultérieurs = `IllegalStateException` ;
  `requestRender` après release ignoré sans lever.
- Threading : rendu **synchrone sur le thread principal** ; re-entrée
  rejouée (`reRenderRequested`).

## Ce que le rendu exige de l'hôte

- Un `Context` Android ; **aucun `android.jar` ni classpath à fournir**
  (le rendu vit des classes du framework présentes chez l'hôte ;
  AndroidX/Material sont embarqués dans `module-inflater` depuis
  v3.4.9) ; les vues du projet utilisateur se substituent par le hook
  `setFactory2` (sémantique AOSP : une seule factory, retour null →
  création par défaut).
- Les ressources du projet : répertoire `res/`
  (`ResourceResolverImpl(String)`) **ou** table en mémoire
  (`ResourceResolverImpl(ResourceTable)`).
- Le thème : par nom (`setThemeDuProjet` — un thème non résoluble
  retombe au thème hôte, jamais d'échec) + `ThemeResolverImpl`
  (`registerThemesFile/registerStylesFile/setTheme`).
- La densité : `DimensionConverter(density, fontScale, xdpi)` posé sur
  le service ET le résolveur de ressources (pattern canonique).

## Mode de consommation dans JoIDE

Les 6 modules de rendu `com.github.jjoblab.LayoutLib:module-*:3.7.0`
(AAR), construits depuis le checkout épinglé `720e85d`, publiés en
Maven Local. La redistribution des artefacts par JoIDE (APK/AAB) est
autorisée par la déclaration du propriétaire du 2026-11-16 (D-1 close
pour JoIDE — il détient le dépôt ; voir [DEMANDES.md](DEMANDES.md)).
Aucune modification de la brique ; tout écart passe par
[DEMANDES.md](DEMANDES.md).
