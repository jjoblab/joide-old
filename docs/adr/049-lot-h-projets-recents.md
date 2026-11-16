# ADR-049 — Lot H : les projets récents — lieu, format, classification

- **Statut** : Accepted
- **Date** : 2026-11-16

## Contexte

Le lot H (ADR-É10) livre l'accueil « Mes projets » (ADR-É3) : le hub
de projets, écran de lancement. Il exige la persistance des
**projets récents** — le nom affiché, le type, le dossier, la
dernière ouverture — que P8 n'avait pas : l'ouverture SAF ne
laissait aucune trace, chaque session repartait de zéro. Le plan
demandait une ADR pour « lieu et format des projets récents
(fichier JSON atomique par AtomicFiles, proposition) ». Une seconde
décision s'y ajoute : la **classification** d'un projet importé
pour sa carte (tuile tonale, libellé de méta) — l'ADR-É3 dit « tuile
tonale par type » sans dire d'où vient le type.

## Décision

### 1. Lieu : un fichier privé JSON à la racine des fichiers internes

`filesDir/projets-recents.json`, lu et écrit par la classe pure
`ProjetsRecents` (package `jo.joide.app.android.accueil`). Écriture
par `AtomicFiles.writeString` (P1 : temporaire puis renommage
atomique — jamais de moitié de liste). Aucune base : la liste est
petite (des dizaines d'entrées au plus), lue à chaque `onResume` de
l'accueil ; le store système reste au démon de build.

La liste est la **source de vérité de l'accueil** : les dossiers
présents sous `filesDir/projets/` mais absents de la liste (les
imports de P8, antérieurs au lot) n'apparaissent pas — aucun
balayage silencieux du stockage ; l'utilisateur les réimporte
s'il les veut. Réciproquement, une entrée dont le dossier a
disparu est **retirée en le disant** (dialogue nominé), jamais une
carte morte.

### 2. Format : JSON versionné, analyse au parseur maison

```json
{"schema":"joide.recents/1","projets":[
  {"id":"mon-application","nom":"mon-application","type":"android",
   "dossier":"mon-application","ouvert":1731750000000,"modules":3}]}
```

- `schema` versionné, comme tous les fichiers du dépôt
  (`joide.workspace/1`, `joide.module/1`) ; un schéma inconnu
  refuse le document avec un problème.
- Analyse par le parseur JSON de `core-deps` (ADR-028), sérialisation
  canonique locale (clés en ordre fixe, chaînes échappées, saut de
  ligne final) — `JsonValue.toString()` n'échappe pas, il ne sert
  donc jamais à écrire.
- Tolérance honnête : une entrée invalide ou en doublon est
  **signalée et sautée** (`Chargement(entrees, problemes)`), jamais
  réparée en silence ; les problèmes vont au journal (l'accueil n'a
  pas de console).
- `id` = `dossier` = le nom du répertoire sous `projets/`,
  assaini (convention P8 : `[^A-Za-z0-9._-]` → `_`) et rendu unique
  par suffixe (`-2`, `-3`…) ; `nom` est libre (renommable, JSON
  échappé) ; `type` est l'un des cinq types ci-dessous ; `ouvert`
  en millisecondes d'époque ; `modules` est le compte au moment de
  l'inscription (0 pour Gradle).

### 3. Les cinq types et leur classification

`TypeProjet` : `ANDROID`, `CONSOLE`, `BIBLIOTHEQUE`, `VIDE`,
`GRADLE`. Les quatre premiers prennent la tuile du kit (É2) ;
`GRADLE` prend la tuile grise et porte la franchise de
compatibilité dans sa ligne de méta — jamais un déguisement.

Classification (`ClassifieurProjet`, pure) **sur le modèle
déclaratif, jamais en devinant les sources** :

| Ordre | Règle | Type |
|---|---|---|
| 1 | détection Gradle (lot A) | `GRADLE` |
| 2 | un module `android-*` | `ANDROID` |
| 3 | un module portant la facette `application` | `CONSOLE` |
| 4 | au moins un module | `BIBLIOTHEQUE` |
| 5 | sinon | `VIDE` |

La facette `application` (avec `main-class`) est la convention
d'entrée des consoles : écrite par les modèles du lot I (ADR-050),
lue par la classification — l'import d'un projet JVM sans point
d'entrée déclaré est classé bibliothèque, ce qu'il est
honnêtement : la classification se lit sur ce que le projet
DÉCLARE.

### 4. Les deux suppressions, nommées

ADR-É3 exige « retirer de la liste / effacer du stockage — deux
actions distinctes, deux libellés distincts » : le dialogue
`Dialogue.destructive` porte le titre « Supprimer "x" de la
liste ? », le message nomme les deux conséquences (le dossier reste
/ le dossier importé est effacé, l'original n'est jamais touché),
trois boutons (Annuler, Retirer de la liste, Effacer du stockage).
L'effacement du stockage part sur le fil de fond ; un échec
d'effacement se dit (dialogue), il ne passe pas sous silence.

### 5. Ouverture et import

- Ouvrir = mettre à jour `ouvert` (tri décroissant, la reprise est
  la première carte, fusionnée — jamais doublée), persister, ouvrir
  l'écran IDE (extras : racine, bannière Gradle le cas échéant,
  fichier d'entrée au lot I).
- Importer = détecter (lot A), refuser l'inconnu (dialogue), copier
  vers `projets/<id unique>` (ADR-046), classifier, inscrire,
  persister, ouvrir. Le Gradle importé s'ouvre sur la bannière de
  compatibilité (P7 : import statique au bureau, échappatoire
  export) — rien n'est monté sur l'appareil, c'est dit.

### 6. Le menu ⋮ de l'accueil

ADR-É3 prévoit « à propos, paramètres, journal » ; seul « À propos »
existe (version, une phrase honnête sur ce qui tourne où). Les
paramètres arrivent au lot M, le journal est au backlog : **aucune
entrée morte** n'est affichée en attendant.

## Conséquences

- `MainActivity` devient l'accueil (lanceur) ; l'écran IDE de P8
  déménage dans `ecrans.ide.EcranIde` sans changement de logique
  (la racine et la bannière arrivent par extras) ; l'écran
  d'exercice du kit est retiré comme le lot G l'annonçait — les
  composants restent, prouvés par leurs tests.
- `BuildService.lirePort` et `SpecsBuild.FILS_PAR_DEFAUT` deviennent
  publics : l'écran IDE vit désormais dans son propre paquet.
- Les âges relatifs (`AgeRelatif`), la salutation (`Salutation`),
  le tri, la fusion et le compteur (`EtatAccueil`) sont purs et
  testés JVM — la vue ne fait que dérouler.
- La « capture » contre `design/accueil-projets.html` suit la
  méthode ADR-048 : conformité prouvée par test lisant la maquette
  (textes, structure, compteur, fusion, tuiles), validation visuelle
  par le propriétaire via l'APK du CI (pas d'émulateur au bac à
  sable).
