# ADR-020 — Harnais de pannes : meurtres de processus fille et arêtes de test

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

Le critère P1 exige : tuer le processus par signal pendant mille transactions du store et pendant la mise à jour du snapshot, au moins deux cents itérations, et vérifier une reprise cohérente sans corruption. Un test ne peut pas tuer sa propre machine virtuelle : il faut des processus filles. Par ailleurs, la validation croisée « le modèle lit les projets synthétiques » (risque R2 du plan P0) nécessite que le banc d'essai et le lecteur de modèle partagent un test, alors que la règle de dépendances interdit les arêtes remontantes.

## Décision

**Harnais de pannes** (dans `core-store`, source set de test) : une machine virtuelle fille (`StoreCrashChild`) ouvre le store et écrit sans fin des transactions connues — empreintes pour un mode, remplacements complets de snapshot pour l'autre — en imprimant une ligne par transaction validée. Le parent la tue par signal (`destroyForcibly`) après un nombre aléatoire de transactions vues (entre 50 et 1000, graine fixe et affichée), rouvre le store et vérifie l'invariant : les empreintes forment un préfixe contigu exact, le snapshot est exactement une génération complète — jamais un mélange — puis le store réouvert accepte de nouvelles écritures. Deux cents meurtres au total (cent par mode). Le classpath d'exécution des tests est transmis aux filles par une propriété système posée à l'exécution (compatible cache de configuration).

**Arêtes de test dans le graphe** : le harnais vit dans le module qu'il éprouve (pas de dépendance nouvelle) ; la validation croisée vit dans tools-bench sous la forme d'une arête descendante `tools-bench -> core-model` (test uniquement, commentée comme telle dans `gradle/module-graph.txt`) — le banc vérifie que ses projets générés sont lisibles par le modèle ; le code principal du banc ne dépend toujours d'aucun module.

## Options

| Option | Verdict | Raison |
|---|---|---|
| Filles JVM tuées par signal, invariants de préfixe (retenue) | retenue | la seule façon de prouver la résilience au kill -9 ; reproductible par graine |
| Corruption artificielle de fichiers de base | écartée | ne prouve pas la résilience du journal écriture d'avance, seulement la détection |
| Harnais mutualisé dans tools-test dès P1 | écartée | un seul consommateur : l'arête de graphe n'apporte rien ; promotion prévue quand un deuxième module l'utilisera |

## Conséquences

Les tests de pannes durent environ une minute et demie (démarrages de machines virtuelles) ; ils sont reproductibles (graine affichée en cas d'échec, §10). Toute corruption non couverte par le journal (fichier de base lui-même détruit) est couverte séparément par le test de reconstruction après corruption dans `SqliteBuildStoreTest`.
