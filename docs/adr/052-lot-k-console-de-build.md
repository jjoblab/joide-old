# ADR-052 — Lot K : la console de build — lignes structurées, phases, cibles de problèmes

- **Statut** : Accepted
- **Date** : 2026-11-16

## Contexte

Le lot K (ADR-É10) livre la console d'ADR-É6 : dock repliable dans
l'atelier, transcript coloré par nature en grille deux colonnes,
phases cliquables, problèmes ouvrables jusqu'à la ligne, écarts AVANT
lancement, plafond annoncé. Le critère est double : « tests JVM du
formatage et des liens ligne » et « **le protocole du démon reste
inchangé** » — le plan interdit par ailleurs de modifier le démon ou
les pipelines (Hors périmètre d'ADR-É10).

## Décision

### 1. `ConsoleBuild` gagne la structure, la vue ne gagne rien

Le formatage pur du lot F (`ligne(type, id, corps)`) reste — son
texte est celui de la ligne v2. Il gagne :

- `Nature` (SYSTEME, PHASE, TACHE, ERREUR, AVERTISSEMENT) — la
  couleur et le filtre du transcript ;
- `Phase` (PREPARATION, COMPILATION, ASSEMBLAGE) déduite du **nom de
  tâche** du pipeline (`module:generateSources`/`processResources` →
  préparation, `compileKotlin`/`compileJava` → compilation, `jar` →
  assemblage) — les vraies phases du démon, pas celles d'une
  maquette ;
- `structurer(type, id, corps)` : un événement → des lignes
  structurées ; le bilan final porte ses problèmes en lignes d'erreur
  séparées, une tâche FAILED est une erreur qui garde sa phase.

### 2. `EtatConsole` : l'état du panneau, pur et testé

Transcript plafonné à 400 lignes avec **plafond annoncé** (le
booléen `plafonne` porte la légende « jamais muet »), compte
d'erreurs exact, bande de phases avec durées **mesurées** (la phase
s'ouvre à sa première tâche STARTED, se ferme à la phase suivante ou
au bilan), filtres par nature ET par phase (le tap sur une pilule de
phase filtre ; retap = retire), états VIDÉ / EN COURS / RÉUSSI /
ÉCHOUÉ / INTERROMPU, résumé final chiffré (« 12 exécutées · 3 du
cache · 2 à jour · 6,2 s » — jamais un succès sans mesures), et
`surEvenement` : LE point d'entrée du protocole — l'écran ne formate
rien. Le vidage (`vider`) vide le transcript mais **conserve l'état
du build en cours** (numéro, phase) : le build continue d'écrire, la
confirmation le dit.

### 3. Cibles de problèmes : le lien prêt, l'absence honnête

`cibleDe(problème)` extrait « fichier:ligne » d'un problème transporté
(par ex. `…/A.java:12:3 : syntax error`) ; une ligne avec cible est
cliquable et le bouton « Ouvrir le fichier à la ligne » ouvre l'onglet
puis positionne le caret (`SceneEditeur.allerALigne`, lot K côté
façade — `Document.lineStart` de celeris est borné, `requestRectangleOnScreen`
amène le défilement ; le chemin absolu d'ECJ est relativisé par
l'écran).

**Écart documenté** : le pipeline ECJ/Kotlin met la localisation dans
`Problem.location` mais `BuildRunner.outcomeOf` ne transporte que
`problem.message()` — les problèmes du protocole actuel ne portent
PAS de localisation, donc **aucun faux lien** : les problèmes non
localisés s'affichent sans cible, le bouton n'apparaît que pour une
cible réelle. Le parseur est prêt ; le lien s'activera le jour où le
pipeline transportera la localisation (backlog — modifier le démon
est hors périmètre de ce plan).

### 4. Le montage dans l'atelier

La console vit dans le `Dock` du lot G (poignée « Console — état »,
légende chiffrée à droite, contenu 300 dp) ; le FAB/pilule du lot J
y mène ; l'arrêt du build est le même (`cancel`). L'éditeur ne se
masque plus jamais pour la console (P8 basculait des zones
exclusives) : le dock se replie sur place. La case « en processus »
du lot F vit dans le menu ⋮ de l'atelier en attendant le lot M.

## Conséquences

- Le protocole du démon est **inchangé** : mêmes événements, mêmes
  champs ; seul le client change de formatage.
- Tests JVM : extension de `ConsoleBuildTest` (+5), `EtatConsoleTest`
  (11), `MaquetteConsoleTest` (6) — la capture contre
  `design/console.html` suit la méthode ADR-048.
- Le libellé des phases et natures du transcript est en français dans
  le formatage pur (continuité du lot F) ; les libellés d'interface
  (filtres, actions, plafond) vivent en parité `values`/`values-fr`.
