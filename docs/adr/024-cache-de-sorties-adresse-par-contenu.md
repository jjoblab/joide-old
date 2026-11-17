# ADR-024 — Cache de sorties adressé par contenu

## Contexte

Le prompt exige un cache de sorties adressé par empreinte d'entrées,
plafonné avec éviction « le moins récemment utilisé », pour restaurer
après un nettoyage ou entre variantes. L'index du cache vit déjà
dans le store (P1) ; les fichiers des sorties, eux, doivent vivre
sur disque quelque part.

## Décision

1. **Entrée = copie intégrale des sorties**. Chaque entrée du cache
   est un dossier immuable `cache/<deux premiers caractères de
   l'empreinte>/<empreinte>/files/<racine>/<chemin ancré>`, écrit en
   zone temporaire puis déplacé atomiquement. La clé ne contient
   jamais de chemin absolu : deux emplacements d'un même projet
   partagent le cache.
2. **Index en mémoire, persistance groupée**. L'index (taille et
   dernier usage par empreinte) est tenu en mémoire par le cache et
   synchronisé avec l'index du store dans la transaction de fin de
   build : enregistrement des nouvelles entrées, retouches des
   entrées restaurées, puis éviction au-delà du plafond. Les
   fichiers des entrées évincées sont supprimés au même moment.
3. **Verrou global d'entrée**. Les copies (écriture et restauration)
   sont sérialisées par un verrou : une éviction ne peut pas
   supprimer une entrée en cours de restauration. Le coût est
   acceptable au niveau P2 (les copies sont brèves devant les
   compilations) ; le verrou pourra être affiné par entrée si une
   mesure le justifie.
4. **En cas de doute, sur-invalider**. Une restauration qui échoue
   (entrée illisible) écarte l'entrée et laisse la tâche s'exécuter :
   un raté de cache n'est jamais un échec de build. Une écriture de
   cache qui échoue produit un avertissement, pas une erreur.

## Options

- Tares de fichiers (liens physiques) au lieu de copies : économise
  de l'espace mais lie la durée de vie des sorties à celle du cache ;
  le déplacement atomique de dossiers devient piégeux sur les
  systèmes de fichiers sans liens.
- Une entrée par fichier plutôt que par tâche : restauration plus
  fine mais l'index explose et la cohérence d'un jeu de sorties
  disparaît.

## Conséquences

- L'espace disque du cache est plafonné par configuration (512 Mo
  par défaut sur le bureau) ; l'éviction supprime les fichiers
  immédiatement, pas seulement les lignes d'index.
- Un processus tué entre l'éviction de l'index et la suppression des
  fichiers laisse au pire des fichiers orphelins invisibles à la
  réouverture (l'index fait foi) — une fuite d'espace, jamais une
  incohérence.
- L'interface `BuildStore` a été étendue en P2 : `listCacheEntries`
  pour recharger l'index à froid, et `evictCacheBeyond` retourne
  désormais les entrées évincées (et non plus une somme) pour que
  l'appelant supprime leurs fichiers.
