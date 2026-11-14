# ADR-003 — Infrastructure incrémentale : ordonnanceur, journal, store, empreintes

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

Les constats 1-3 et 6-7 de la revue (exécution par niveaux avec barrière, scan intégral à chaque build, cache de digests en mémoire seulement, empreintes non relocalisables) font du moteur analysé un moteur O(taille du projet).

## Décision

1. Ordonnanceur à file de tâches prêtes (compteurs de dépendances), priorité au chemin critique pondéré par les durées historiques, admission mémoire/CPU par déclaration de coût. 2. Journal de changements fusionné (éditeur, watcher, scan de rattrapage) produisant un dirty set ; build sans changement en O(Δ). 3. Store unique SQLite WAL derrière l'interface BuildStore (empreintes, snapshot VFS, durées, état incrémental). 4. Empreintes relocalisables (chemins relatifs à des racines nommées), hash rapide 128 bits pour le changement, SHA-256 pour la confiance. 5. Entrées de tâche fichier par fichier avec delta ajouté/modifié/supprimé.

## Options

| Option | Verdict | Raison |
|---|---|---|
| a. Schéma scan + .fp par tâche | écartée | O(taille), fichiers milliers de petits fichiers, collisions de noms |
| b. Retenue | retenue | proportionnelle à la modification, relocalisable, testable |
| c. Watcher seul | écartée | limites inotify, débordements ; le rattrapage reste nécessaire |

## Conséquences

Plus complexe à tester : tests de propriétés et de pannes (kill -9, OOM) obligatoires dès P1/P2 ; l'invariant « incrémental ≡ propre » devient le critère d'acceptation central (P2/P3/P5).
