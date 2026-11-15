# ADR-032 — Extraction des AAR : zip-slip bloqué par politique pure

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

L'invariant du module (fiche README P0, constat 9 de la revue) :
« extraction sûre des AAR ». Un AAR est un zip ; une entrée malveillante
(`../`, chemin absolu, lecteur Windows) peut écrire hors de la cible —
le zip-slip. Les descripteurs AAR servis par un dépôt compromis sont
une surface d'attaque de la chaîne d'approvisionnement.

## Décision

`jo.joide.deps.aar.AarExtractor` :

- la politique `slipReason(String)` est **pure et testée** : nom vide,
  barre oblique inverse, chemin réseau (`//`), chemin absolu (`/`, `~`),
  lecteur Windows (`C:`), traversée (`..`), répertoire courant (`.`),
  segment vide (`a//b`) — chacun refuse l'entrée ; la barre finale des
  entrées de répertoire est admise ;
- l'extraction se fait en zone d'attente puis déplacement atomique
  vers `aars/xx/<sha256>/` ; un marqueur `.joide-extracted` contenant
  l'empreinte rend l'opération idempotente ;
- plafonds : 4096 entrées, 512 Mio au total (refus explicite, pas
  d'échec en sortie de bornes) ;
- tout refus supprime la zone d'attente et se rend en problème
  `deps.aar.rejected` — jamais de fichier écrit hors cible.

## Options

| Option | Verdict | Raison |
|---|---|---|
| Normaliser par `Path.normalize()` et comparer les préfixes | écartée | subtile (liens symboliques, `..` résiduels) ; testée difficilement ; la politique explicite est auditable ligne à ligne |
| Refuser tout zip comportant une entrée suspecte (tout ou rien) | écartée | un seul fichier hostile tuerait des AAR honnêtes ; le refus par entrée suffit et se teste |
| Politique par entrée, pure (retenue) | retenue | lisible, testable sans zip (`slipPolicyIsPureAndTestable`), coût nul en dehors du hot path |

## Conséquences

- Le dex et la fusion (P5) consomment l'extraction déjà vérifiée :
  aucune nouvelle surface zip en aval.
- Les AAR publiés avec des entrées de répertoire explicites (`res/`)
  passent ; les « . » et segments vides, tolérés par certains outils,
  sont rejetés — signalé au SyncReport par le résolveur amont.
- Le désucrage et le contenu dex de `classes.jar` ne sont pas ouverts
  en P4 : le hachage suffit au lockfile.
