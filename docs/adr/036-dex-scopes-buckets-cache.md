# ADR-036 — Dex par scopes, buckets multidex, cache partageable entre projets

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

Le dexage final d'une application doit concilier trois exigences : un
travail proportionnel à la modification (un changement de code ne
redexe pas les bibliothèques), un cache de dex partageable entre
projets (deux projets qui dépendent de la même bibliothèque ne la
dexent qu'une fois) et le plafond du format (65 536 méthodes
référencées par fichier dex).

## Décision

Chaque unité de code est un scope dexé séparément : les classes du
module d'application, le jar de chaque module bibliothèque, chaque
jar externe et chaque AAR (classes.jar et libs/*.jar, consommation de
l'extraction vérifiée de P4). L'empreinte d'une tâche de scope ne
dépend que du contenu de ses entrées (chemins ancrés à $M2), de
l'API minimum, de la version de l'outil et des options : deux
projets partageant le même dépôt de bibliothèques et le même cache de
sorties obtiennent la même empreinte. Pour que ce partage marche avec
des stores par projet, l'ouverture du cache de sorties découvre sur
le disque les entrées écrites par d'autres projets (répertoire
partitionné, le nom du dossier est l'empreinte) — le second projet
restaure le dex au lieu de le recalculer. La fusion répartit les dex
de scopes en buckets : le code de l'application d'abord, puis les
premiers buckets qui contiennent encore chaque dex ; un scope seul
au-dessus du plafond reste un bucket et le dépassement est signalé,
jamais caché. La fusion passe par le fork à gros tas (ADR-005).

## Options

| Option | Verdict | Raison |
|---|---|---|
| Monolithe : tout dexer ensemble | écartée | aucune incrémentalité, aucun partage entre projets |
| Dex par classe | écartée | explosion d'entrées et coût de fusion |
| Dex par scope + buckets (retenue) | retenue | proportionnel, partageable, plafond respecté |

## Conséquences

Une bibliothèque très utilisée n'est dexée qu'une fois par machine.
L'éviction du cache de dex suit l'éviction du cache de sorties
(quota, P6). Les méthodes de liaisons générées à la fusion imposent
une marge (512) sous le plafond brut.
