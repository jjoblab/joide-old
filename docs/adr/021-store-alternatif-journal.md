# ADR-021 — Store alternatif en journal à ajouts : mesuré, écarté pour l'instant

## Contexte

L'interface `BuildStore` (P1) existe précisément pour permettre de
benchmarker une alternative au store SQLite et d'en décider par la
mesure. Le backlog P2 porte cette décision. Le moteur de tâches P2
exerce le store avec des charges typiques : empreintes et durées par
tâche, index du cache de sorties, instantané de fichiers complet.

## Décision

**SQLite reste le store par défaut.** Un prototype complet de store
en journal à ajouts (`JournalBuildStore`, source set `jvm` de
core-store) a été écrit et mesuré sur les charges du moteur
(`StoreBenchmarkTest`, tier L de tâches, instantané de 30 000
fichiers) :

| Charge (bac à sable 2 cœurs, chiffres indicatifs) | Journal | SQLite |
|---|---|---|
| 1 200 écritures de tâches (empreinte + durée) | 14 ms | 89 ms |
| 1 200 lectures d'empreintes | < 1 ms | 9 ms |
| 6 000 entrées d'index de cache | 6 ms | 80 ms |
| Instantané complet de 30 000 fichiers | 200 ms | 98 ms |
| Lecture de l'instantané | 40 ms | 53 ms |
| Réouverture à froid | 176 ms | 7 ms |

Le journal gagne nettement sur les petites transactions répétées (pas
de coût par requête SQL), mais perd sur la réouverture à froid —
critique pour un démon qui redémarre souvent — et sur les gros blocs.
Aucune de ces charges n'est un goulot du build : le build sans
changement à chaud ne touche pas le store du tout (ADR-023), et les
écarts absolus (dizaines de millisecondes) sont marginaux devant les
budgets de build à froid.

Le journal reste dans le dépôt comme implémentation de référence du
contrat (elle passe les mêmes tests de sémantique, y compris
transaction abandonnée et journal tronqué) et comme garde-fou contre
une régression de l'interface.

**Déclencheur de réévaluation** : une mesure sur la machine de
référence montrant le store au-delà de 10 % du temps de build à froid
au tier L, ou une réouverture SQLite qui dégrade le budget de
démarrage du démon (P6).

## Options

- **SQLite seul, pas de prototype** : la décision aurait reposé sur
  des hypothèses, pas des chiffres — contraire à l'objet de
  l'interface.
- **Adopter le journal par défaut** : réouverture 25 fois plus lente,
  code neuf non éprouvé par le harnais de pannes de P1 ; le gain
  (75 ms par build L à froid) ne le justifie pas aujourd'hui.
- **Retirer le prototype après mesure** : il coûte peu à maintenir et
  documente le contrat mieux qu'un texte.

## Conséquences

- Deux implémentations de `BuildStore` coexistent ; les deux passent
  les tests de sémantique du contrat (le harnais kill -9 de P1 reste
  spécifique à SQLite).
- Toute évolution de l'interface doit rester implémentable par le
  journal — c'est la contrainte qui a fait rejeter un accès page par
  page au profit d'opérations de haut niveau.
- La décision est rapportée dans le rapport P2 avec les chiffres
  complets.
