# ADR-023 — Chaîne de décision à jour et passe à chaud sans entrée-sortie

## Contexte

Le prompt exige qu'une tâche dont aucune entrée n'est dans
l'ensemble sale soit déclarée à jour sans aucun accès disque, et
qu'un build sans changement au démon chaud au tier L se décide en
cent millisecondes au plus. En même temps, l'empreinte d'une tâche
doit être f(entrées, version d'outil, options) : changer la version
d'outil doit réexécuter même sans changement de fichier.

## Décision

1. **Une seule chaîne, trois raccourcis**. La décision d'une tâche
   suit toujours : hachage des entrées (paresseux), empreinte,
   comparaison à l'enregistrement de la dernière exécution, puis
   éventuellement vérification des sorties signalées sales,
   restauration du cache, exécution. Les raccourcis ne changent pas
   la sémantique, seulement le coût :
   - un chemin sale (ajouté, modifié, supprimé) ne réutilise jamais
     les hachages des builds passés : seul un hachage calculé pendant
     CE build ou porté par l'instantané courant fait foi ;
   - un chemin propre réutilise le hachage connu (en mémoire au
     chaud, par signaux inchangés dans l'instantané précédent à
     froid — jamais de re-hachage sans changement de taille, date ou
     clé) ;
   - les sorties ne sont vérifiées que si le journal les signale
     sales (détection de falsification), jamais à chaque build.
2. **Passe à chaud sans ordonnancement**. Si aucun chemin n'est
   sale et que chaque tâche retrouve son enregistrement avec la même
   empreinte recalculée en mémoire, le build entier est déclaré à
   jour sans lancer l'ordonnanceur : la décision reste O(1) par
   tâche, sans pool, sans verrou, sans accès au store. Cette passe
   tombe dès qu'un chemin est sale, qu'un enregistrement manque ou
   qu'une empreinte diverge — le chemin normal prend le relais.
3. **Regroupement des écritures**. Toutes les écritures du store
   (enregistrements d'exécution, empreintes, durées, index du cache,
   instantané enrichi des hachages calculés) ont lieu en une seule
   transaction à la fin du build, sur le fil propriétaire du store,
   et seulement s'il y a quelque chose à écrire : un build sans
   changement n'écrit rien. Un build tué en cours d'exécution ne
   laisse aucun état à moitié écrit ; le build suivant rejoue les
   tâches non enregistrées.
4. **Emplacements**. L'état d'exécution vit dans le store par espace
   de travail (copié avec le projet) ; le cache de sorties vit dans
   un dossier séparé, adressé par empreinte (partageable entre
   emplacements d'un même projet). Aucune empreinte ne contient de
   chemin absolu : copier le projet rend tout à jour, nettoyer les
   sorties restaure depuis le cache.

## Options

- Décider à jour avant l'ordonnancement pour toutes les tâches, y
  compris sales : impossible, une tâche sale peut dépendre d'une
  tâche qui doit s'exécuter d'abord.
- Persister les enregistrements tâche par tâche : des centaines de
  transactions par build pour aucune garantie supplémentaire — la
  granularité du build suffit.

## Conséquences

- Le critère « cent millisecondes au tier L sans changement » est
  tenu avec une large marge (la passe mesure quelques dizaines de
  millisecondes sur le bac à sable à deux cœurs).
- L'exactitude repose sur la fraîcheur de l'ensemble sale fourni par
  le système de fichiers virtuel : la responsabilité du démon (P6)
  sera de le tenir à jour, y compris pour les sorties supprimées.
- L'état chaud du moteur vit en mémoire entre builds du même
  démon ; un nouveau moteur rejoue depuis le store (froid).
