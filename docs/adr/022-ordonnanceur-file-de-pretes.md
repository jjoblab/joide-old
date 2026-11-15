# ADR-022 — Ordonnanceur : file de prêtes par compteurs, ordre relâché, annulation sans orpheline

## Contexte

Le prompt exige un ordonnanceur sans barrière de niveau (une tâche
devient éligible dès que son dernier prédécesseur se termine), avec
priorité par chemin critique restant, admission de ressources,
modes d'échec et annulation coopérative sans tâche orpheline. Le
code doit rester analysable : pas de coroutines, pas de threads
virtuels, exécuteur et horloge injectés.

## Décision

1. **Compteurs, pas de niveaux** : chaque tâche porte un compteur de
   dépendances non terminées ; à zéro, elle entre dans une file de
   prêtes. La priorité est le poids de chemin critique restant
   (durée propre + plus lourd chemin vers une tâche terminale),
   calculée une fois par build à partir des durées historiques du
   store ou des estimations par type. Départage alphabétique pour
   un ordre déterministe.
2. **Admission** : la file est parcourue jusqu'à trouver une tâche
   admise par le budget (une tâche bloquée par le budget n'empêche
   pas une plus petite de passer — pas de blocage de tête de file).
   Une tâche exclusive s'exécute seule ; pendant qu'elle vole, rien
   n'est admis.
3. **Ordre « mustRunAfter » best-effort** : une contrainte d'ordre
   dont les prédécesseurs ne sont pas finis retient la tâche. Si
   RIEN ne vole et que la file ne contient que des tâches retenues
   par l'ordre (cycle d'ordre pur), la contrainte est relâchée une
   fois pour la tâche la plus urgente : jamais d'affamement. Les
   contraintes contraires aux dépendances dures sont rejetées à la
   validation du graphe.
4. **Annulation sans annulation de futur** : l'arrêt passe par le
   jeton d'annulation (et ses écouteurs), pas par
   `Future.cancel(true)`. Une tâche déjà en file d'exécution doit
   démarrer pour constater l'arrêt, libérer sa revendication et
   rendre son compte de vol — sinon la comptabilité ne se referme
   jamais. Toute tâche non terminée est marquée annulée au moment de
   l'arrêt ; une exécution déjà lancée garde son résultat réel et
   remplace le marqueur. Le retour de `run` n'a lieu qu'une fois
   toutes les tâches terminées ET tous les vols achevés.
5. **Modes d'échec** : `FAIL_FAST` déclenche l'arrêt à la première
   tâche échouée ; `KEEP_GOING` marque la fermeture transitive des
   dépendants comme sautée et poursuit les tâches indépendantes.

## Options

- Annuler les futurs à l'arrêt : testé, interblocage — une tâche en
  file annulée avant de démarrer ne libère jamais sa revendication.
- Barrière par niveau avec tri par priorité : rejette l'exigence de
  chemin critique et le critère d'acceptation de la barrière levée.
- Verrou par tâche au lieu d'un verrou d'exécution : plus fin mais
  inutilement complexe pour le nombre de tâches visé.

## Conséquences

- Les événements de début et de fin sont émis sous le verrou interne
  : leurs horodatages sont cohérents avec l'ordre de déblocage (un
  écouteur ne doit jamais bloquer, documenté dans l'API).
- Un bug de comptage se traduit par une exception explicite
  (« stalled with no runnable task ») plutôt qu'une attente infinie.
- Les tests de propriétés rejouent plus de mille exécutions de
  graphes aléatoires sous perturbation des délais, graine affichée
  en cas d'échec.
