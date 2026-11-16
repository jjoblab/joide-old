# ADR-É6 — Écran de console de build

- **Statut** : Accepted (design — implémentation en lots futurs)
- **Date** : 2026-11-16
- **Maquette** : `design/console.html`

## Contexte

La console P8 est une `TextView` monospace à qui l'on ajoute des
lignes, plafonnée à N lignes — fonctionnelle, illisible : aucune
hiérarchie, aucune action. CodeAssist (ADR-É1, leçon 5) montre la
console comme un écran de première classe : phases cliquables,
problèmes ouvrables, transcript coloré par nature.

## Décision

La console est un panneau de l'atelier : dock repliable en bas
(≥ 720 dp) ou plein écran sur téléphone, avec la barre supérieure de
l'atelier conservée. Le modèle `ConsoleBuild` (formatage pur, lot F)
reste la source — seule la présentation change.

### Structure

1. **En-tête** : « Console de build » + nom du projet, pilule d'état
   à droite (« réussi · 6,2 s » verte, « en cours » accent animée,
   « interrompu » alerte — la mort du service est un événement, jamais
   un zombie), actions : arrêter (pendant), vider (confirmation si
   un build tourne).
2. **Bande de phases** : une pilule par phase du pipeline JoIDE
   (résolution, compilation, assemblage), état et durée ; tap →
   filtre le transcript sur la phase.
3. **Filtres** : puces « Tout / Erreurs (n) / Système » — le compte
   d'erreurs est exact.
4. **Transcript** (`code compact`, `puits`) coloré par nature
   d'événement — système en secondaire, phase en accent, tâche au
   premier plan, erreur en `erreur` : lisible d'un coup d'œil sans
   lecture intégrale. Chaque ligne reste du texte sélectionnable/copiable.
5. **Lignes de problème cliquables** : « fichier:ligne — message »,
   tap → ouvre le fichier dans l'atelier, curseur sur la ligne ;
   rangée d'action sous le transcript quand une erreur est visible
   (bouton « Ouvrir le fichier »).

### Franchise (conservation intégrale du lot F)

- Les **écarts s'affichent AVANT le lancement** (modules Android non
  construits sur l'appareil, dépendances non résolues, AAR) — jamais
  d'omission muette ; « Rien à construire » reste un refus motivé.
- La case « en processus » devient un paramètre (ADR-É8) ; quand il
  est actif, la console l'annonce à chaque lancement (comportement
  lot F conservé).
- Le résumé final est chiffré : « 12 exécutées · 3 du cache · 2 à
  jour » (champs `Outcome` existants) — jamais un « succès » sans
  mesures.
- La mort du service : « Build interrompu : l'IDE survit ; le
  prochain build le relance » — l'UI ne meurt pas avec le service.

## États

Vide (« aucun build dans cette session »), en cours (phases
animées), réussi, échoué (première erreur mise en avant, bouton
Ouvrir), interrompu. Le plafond de lignes est conservé (comportement
lot F) mais la suppression est annoncée en légende (« affichage
plafonné à 400 lignes ») — un plafond n'est pas un mensonge.

## Conséquences

- `ConsoleBuild` (pur) gagne au plus des traductions de nature
  d'événement ; aucune logique ne migre dans la vue.
- La console est le seul endroit où l'état du service `:build` est
  narré : notification persistante Android inchangée (ADR-043).
