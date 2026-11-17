# ADR-053 — Lot L : l'aperçu de mise en page — densité réelle, ressources manquantes, bannières

- **Statut** : Accepted
- **Date** : 2026-11-16

## Contexte

Le lot L (ADR-É10) livre le volet d'aperçu d'ADR-É7 : « rendu
LayoutLib brut sur damier, stats de l'écouteur, bannières de franchise
(dernier rendu valide pendant la frappe, repli natif annoncé,
MockView), panneau amarré dans l'atelier ». ADR-É7 liste des
« réglages réels du `ConfigurationApercu` du lot E, rien d'inventé » —
mais le lot E n'exposait NI densité ni dimensions pilotables (la
densité venait du contexte, les dimensions cibles étaient fixes en
pixels). Et la brique LayoutLib fait ses replis de ressources
**silencieusement** : rien ne permettait d'afficher la bannière
exigée. Deux extensions de façade étaient nécessaires — cette ADR les
consigne.

## Décision

### 1. La densité de rendu devient un réglage réel de la configuration

`ConfigurationApercu` (record, ide-integration) gagne un composant
`densiteRendu` (float ; `DENSITE_CONTEXTE` = 0 conserve le
comportement du lot E : la densité du contexte hôte) et une variante
`avecDensite(float)` (+ `avecDimensions(l, h)` pour la netteté).
`ApercuLayouts.configurer` construit le `DimensionConverter` de la
brique avec la densité **choisie** — c'est elle qui traduit les dp du
XML en pixels ; `RenderService.setDimensionConverter` existe dans la
brique, aucun contournement. Les dimensions cibles restent en
**pixels** à la façade : l'écran maintient des **dp** (360 × 640 par
défaut, presets téléphone/grand téléphone/tablette) et convertit
`dp × densité` — un rendu « 2× » d'un layout 360 dp fait 720 px,
cohérent de bout en bout.

### 2. Les ressources manquantes : la façade dit ce que la brique tait

`ApercuLayouts.ressourcesManquantes(xml)` rend les références
DIRECTES du XML de mise en page que le résolveur du projet ne résout
pas — ce sont elles qui passent au repli natif pendant le rendu.
L'analyse vit dans `ReferencesManquantes` (package-private,
ide-integration) :

- le XML est analysé par le **parseur maison** (`core-deps/xml`,
  ADR-028) — les références viennent des attributs de l'arbre, jamais
  d'une regex sur le document (risque n° 11 de l'ADR-É9) ;
- seuls les types que le résolveur sait interroger sont vérifiés
  (`color`, `string`, `dimen`, `drawable`, `integer`, `bool`) ; les
  alias suivis ne sont jamais signalés (testé sur le vrai résolveur —
  faux positif interdit) ; `@android:…`, `@id/`, `@+id/` et `?attr/`
  ne relèvent pas du résolveur ;
- sans dossier `res/` de projet (repli natif global) ou sans document
  analysable (XML en frappe), la liste est vide — ces états sont
  portés par leurs propres bannières.

### 3. MockView : comptée dans l'arbre rendu

La brique dégrade une classe inconnue en MockView pointillé **sans
échec** ; l'écouteur `onSucces` rend l'arbre de vues vivant —
l'écran le parcourt (garde 2 000 visites) et compte les vues dont le
nom de classe simple est `MockView` : la bannière « n classe(s)
inconnue(s) — MockView, pas un échec » repose sur une mesure, pas sur
une supposition.

### 4. `EtatApercu` : les bannières traduisent, ne décident pas

`EtatApercu` (pur, app-android) porte les contrôles réels (densité
segmentée 1×/2×/3×, mode nuit, dimensions dp), les stats MESURÉES de
`onSucces` (vues, durée, dimensions) et compose les bannières par
gravité (ERREUR > ALERTE > INFO) : frappe (dernier rendu valide
conservé, le volet ne devient jamais blanc ni faux), ressources
manquantes (repli annoncé, reste tant que ça manque), MockView, échec
de rendu nominal (la surface garde le dernier rendu valide),
avertissements de constitution. Le thème n'est jamais deviné (lot E) :
`nomTheme` null affiche « repli hôte » et le dit. Le debouncé 400 ms
du lot E est conservé tel quel ; « rendre sans délai » reste l'action
manuelle explicite de la barre de volet.

### 5. Le montage

Le volet vit dans l'atelier du lot J : côte à côte (320 dp, grand
écran, automatique pour une mise en page active) ou plein écran
(téléphone, bouton dans la barre d'onglets, retour système). La
surface est un damier neutre (cases 8 dp, deux gris) ; le rendu —
arbre LayoutLib vivant — est posé **tel quel** dans un perchoir
arrondi 24 dp, mis à l'échelle du volet (`scale` du cadre, jamais
retouché, jamais embellie) ; le changement de densité/nuit/dimensions
reconfigure la façade (`configurer` repose les résolveurs) et
relance le rendu sans délai.

## Conséquences

- La façade ide-integration évolue sans rupture pour ses seuls deux
  consommateurs (l'atelier, les tests) : le composant `densiteRendu`
  s'ajoute au record, les fabriques et variantes propagent.
- Tests JVM : `ReferencesManquantesTest` (6, sur le VRAI résolveur de
  la brique — le pattern du lot E), `ConfigurationApercuTest` étendu
  (+1), `EtatApercuTest` (8), `MaquetteApercuTest` (5) — capture
  contre `design/apercu.html` (méthode ADR-048).
- Le rendu réel exige un Context Android : prouvé par l'APK (lot F),
  pas sur JVM — la logique testable est intégralement pur.
- Backlog : les replis de ressources signalés par la brique elle-même
  (aujourd'hui détectés côté JoIDE sur les références directes) ;
  `@layout/` et les références transitives ne sont pas vérifiées.
