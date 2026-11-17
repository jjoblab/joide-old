# ADR-033 — Lockfile joide.lock v1 : gel, zéro réseau, exclusions dans le graphe

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

ADR-007 : « une résolution avec lockfile valide ne fait AUCUNE requête
réseau ; versions dynamiques et SNAPSHOT résolues une fois puis figées ;
lockfile identique octet pour octet sur deux résolutions ». Le lockfile
doit aussi porter le **graphe** (les exclusions y sont résolues) : sans
lui, la relecture chaude devrait réouvrir les POM/GMM — du réseau.

## Décision

`jo.joide.deps.lock.Lockfile` + le moteur (ADR de phase) :

**Format v1** (LF, UTF-8, trié) :

```
joide.lock v1
g:a:v[:c]@ext sha256=<64 hex> size=<n> repo=<id>
  -> g:a:v[:c]@ext api
  -> g:a:v[:c]@ext runtime
```

Entrées triées par coordonnée ; arêtes par cible puis genre
(`api` compile-visible, `runtime` exécution seulement, `compile`
non transmise — l'équivalent Maven de `provided`).

**Deux phases dans le résolveur** :

1. **Couverture** — le lockfile lisible couvre chaque demande (une
   exigence exacte d'instantané littéral accepte sa forme concrète de
   même base : c'est le **gel**), chaque artefact est relu **et
   revérifié** au cache (empreinte + taille), les arêtes `compile`
   entrent sans être étendues → résultat sans une seule requête, le
   lockfile n'est pas touché ;
2. **Résolution complète** (repli) — point fixe des versions :
   l'existant satisfait l'exigence sinon `latest`/plage par les
   métadonnées ; l'arbitrage des conflits est **« la plus récente
   gagne »**, une exigence stricte (`strictly` GMM, gel) s'impose ;
   fermeture avec **exclusions par sous-arbre** : une exclusion
   ampute le sous-graphe de la dépendance qui la porte, la fermeture
   est l'union des chemins non exclus (une cible réétendue ne l'est
   que vers un ensemble d'exclusions strictement plus petit) ;
   téléchargements en parallèle plafonné, checksums publiés vérifiés
   (GMM incorporée d'abord, puis `.sha256`, puis `.sha1` — la
   première source disponible fait foi, divergence = échec explicite,
   absence de source = accepté avec l'empreinte calculée) ; puis
   écriture du lockfile, **inchangé octet pour octet** si la
   résolution redonne le même contenu.

**Vérification** : `LockfileDeterminismTest` (deux résolutions
indépendantes → fichiers identiques ; transport hostile → zéro
requête), `SnapshotFreezeTest` (le dépôt avance, la lecture chaude ne
bouge pas ; sans lockfile, la nouvelle forme est prise).

## Options

| Option | Verdict | Raison |
|---|---|---|
| Lockfile plat (ensemble de coordonnées, sans arêtes) | écartée | la relecture chaude devrait réouvrir les métadonnées : du réseau, contraire à ADR-007 |
| Graphe complet dans le lockfile (retenue) | retenue | la relecture chaude est fermée sur le fichier ; les exclusions y sont déjà résolues (pas réévaluées) |
| Deux fichiers (artefacts + graphe) | écartée | double écriture atomique, double désynchronisation possible |

## Conséquences

- Le lockfile est la seule persistance de P4 : le store (core-store)
  n'est pas utilisé, le graphe des modules le permet (`core-deps ->
  core-platform` seul, le store restant disponible pour les phases
  suivantes).
- Une exigence qui change (nouvelle dépendance, plage élargie)
  sort de la couverture → repli complet → lockfile réécrit : le gel
  n'est jamais un piège.
- `size` dans l'entrée lie aussi la taille : un artefact remplacé par
  un contenu de même empreinte mais taille différente est impossible
  (l'empreinte détermine la taille) — le champ reste comme garde
  croisée bon marché.
- Les classifieurs (`sources`, etc.) ne sont pas résolus en P4 : les
  entrées de classifieur existent dans le format, aucune demande ne
  les produit (backlog).
