# ADR-048 — Lot G : le système de design « Graphite » en code

- **Statut** : Accepted
- **Date** : 2026-11-16

## Contexte

Le plan ADR-É10, accepté le 2026-11-16 (GO du propriétaire, D-5 :
OkHttp), s'ouvre sur le lot G : les jetons ADR-É2 en code, le kit de
composants en vues du cadre, le clair/sombre, la parité de langue
`values`/`values-fr`. L'ADR-É2 avait défini la palette sombre et
différé le clair (« avec les mêmes rôles ») ; le plan avance le clair
au lot G. Cinq décisions sont nouvelles et demandent une ADR
numérotée : la palette claire (jamais spécifiée nulle part), la
syntaxe de code claire, l'application du thème (mode nuit), la
convention de langue des ressources, et l'accès à l'écran d'exercice.

## Décision

### 1. Palette claire — mêmes rôles, valeurs claires, AA prouvé par test

| Rôle | Sombre (ADR-É2) | Clair (présente ADR) |
|---|---|---|
| `fond` | `#0E1116` | `#F3F6FA` |
| `surface` | `#151920` | `#FFFFFF` |
| `surfaceHaute` | `#1B212A` | `#E9EEF6` |
| `puits` | `#0B0E12` | `#FFFFFF` |
| `bord` | `#262D38` | `#D9E1EC` |
| `bordFin` (maquette) | `#1D232C` | `#E5EBF3` |
| `texte` | `#E7ECF3` | `#1C2430` |
| `texteSecondaire` | `#9AA6B5` | `#5A6B80` |
| `texteEstompe` | `#64707F` | `#8A97A8` |
| `accent` | `#82AAFF` | `#2E63D6` |
| `reussite` | `#3DDC84` | `#0D7D45` |
| `alerte` | `#E5C07B` | `#8A6A16` |
| `erreur` | `#E06C75` | `#C73540` |
| `violet` (bibliothèque) | `#C678DD` | `#A026AA` |
| `texteSurAccent` (bouton primaire) | `#0B1524` | `#FFFFFF` |
| `texteSurReussite` (FAB) | `#07130C` | `#FFFFFF` |
| `rideau` (tiroir, scrim) | puits à 48 % | `#1C2430` à 24 % |

Syntaxe de code : sombre selon É2 ; clair — mot-clé `#A026AA`, type
`#B45309`, fonction `#2E63D6`, chaîne `#22863A`, nombre `#C25314`,
commentaire `#667385`, défaut `#384556`.

En clair, `puits` et `surface` partagent le blanc : la distinction
passe par les bords (`bordFin`), l'inverse du sombre où le puits
s'enfonce — la métaphore est chromique en sombre, structurelle en
clair. Les statuts (`reussite`, `alerte`, `erreur`, `accent`) sont
assombris jusqu'au AA texte (4,5:1) sur `surface` et `fond` : en
clair ils servent de texte, pas seulement de fond. Le contraste AA
est **prouvé par test** (`GraphiteTest` : mathématique de luminance
sur les deux palettes, les 16 couples texte/support et les 7 rôles
de syntaxe sur le puits) — pas affirmé.

### 2. Thème et mode nuit

`Theme.Material.Light.NoActionBar` (P8) devient
`Theme.Material.NoActionBar`. Chaque activité pose ses couleurs de
fenêtre **en code** depuis les constantes (`barre d'état`, barre de
navigation, fond) — pas de ressources couleurs : les valeurs vivent
une seule fois dans `Graphite` (le principe « un changement de
palette se fait ici, une fois » d'É2). Le mode suit le système :
`NIGHT_NO` → clair ; nuit ou indéfini → sombre (« sombre d'abord »,
É2) — la bascule jour/nuit recrée l'activité et relit le mode.

### 3. Rayons et tuiles

Rayons 16/12/999 (É2 fait autorité) — la maquette affiche 14 sur
tuile et bouton : écart pixel, É2 a priorité (écart consigné). Tuile
tonale : rôle à 12 % sur `surface` (le calcul vit dans `Graphite`,
testé). Le glyphe de tuile est **un seul dossier dessiné** au lot G,
raffiné par écran ensuite (lot H) ; la troncature est une ligne,
coupe par mot puis « … » (`ChoixTexte`, testé JVM).

### 4. Composants du lot G

Le sous-ensemble du plan : boutons (primaire/tonal/texte), champs
(étiquette au-dessus), cartes (tuile tonale, liseré sombre, ombre
douce), puces (dont catégories), tiroir poussé (360 dp, 180 ms,
rideau), dock repliable (poignée), dialogues (AlertDialog du cadre
habillés Graphite), états vides (zone en pointillés). Les autres
composants d'É2 — barre d'onglets, FAB, barre de phases, ligne de
problème, bannière, rangée de réglage, pilules, segment — arrivent
avec leurs écrans (lots J à M), jamais en avance.

### 5. Parité de langue : values/ en anglais, values-fr/ en français

La convention celeris (`android-host` : `values/` anglais par défaut,
`values-fr/` traduites) devient celle de JoIDE : la langue du
propriétaire reste le français sur son appareil (`fr`), tout autre
appareil retombe sur l'anglais. Le contrôle n°5 du script de
politique de langue l'exige : même ensemble de clés dans
`values/strings.xml` et `values-fr/strings.xml`, pour tout module
portant des chaînes (aujourd'hui `app-android` seul) ; fixtures
dédiées dans `tools-test` (une conforme, une en violation) et le
test du script étendu. Les chaînes P8 sont traduites au passage.

### 6. Écran d'exercice et captures

L'écran d'exercice du kit est une activité non exportée du paquet
`ecrans/exercice/`, atteinte par une entrée discrète « Kit Graphite »
en bas de l'écran P8 — entrée **temporaire**, retirée quand le lot H
remplace l'ouverture (aucune navigation n'est câblée au lot G, le
plan l'interdit). Le bac à sable n'a pas d'émulateur (écart P8 n° 4) :
la conformité aux jetons de `design/index.html` est prouvée par test
(`JetonMaquetteTest` lit les variables CSS de la maquette et les
compare aux constantes, jeton par jeton) ; la validation visuelle sur
appareil revient au propriétaire via l'APK du CI, comme en P8.

## Options

| Option | Verdict | Raison |
|---|---|---|
| Palette claire dérivée, AA par test (retenue) | retenue | mêmes rôles qu'É2, preuve mathématique, une seule source (Graphite) |
| Réutiliser les valeurs sombres en clair | écartée | `#82AAFF` sur blanc : ≈ 2,2:1, l'AA texte est impossible sans assombrir |
| Différer le clair (position É2 d'origine) | écartée | le plan accepté par le propriétaire le met au lot G |
| Ressources couleurs (`values/colors.xml`) | écartée | deux sources de vérité ; É2 : les jetons sont des rôles, pas des valeurs câblées — les vues lisent `Graphite` |

## Conséquences

- Les lots H à M héritent des jetons et du kit sans les redéfinir ;
  un changement de palette se fait dans `Graphite` une fois.
- L'ADR « projets récents » (lot H) passe à 049, l'ADR « format
  déclaratif des modèles » (lot I) à 050 — renumérotation consignée
  dans ADR-É10.
- Le backlog P0 « réactiver la parité values/values-fr » est fermé ;
  le contrôle devient permanent (script + fixtures + test).
- Les maquettes restent sombres : pas de maquette claire (les écrans
  se vérifient sur maquette sombre, puis sur appareil dans les deux
  modes par l'écran d'exercice).
- La palette claire et sa syntaxe n'ont pas de maquette de référence :
  leur conformité est celle des rôles (test), pas du pixel.
