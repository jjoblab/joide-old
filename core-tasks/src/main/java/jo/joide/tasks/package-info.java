/**
 * Moteur de tâches : graphe validé (dépendances dures, ordre,
 * inférence par préfixe), ordonnanceur à file de prêtes sans
 * barrière de niveau, admission de ressources, empreintes
 * relocalisables, cache de sorties adressé par contenu et build
 * scan.
 *
 * <p>Invariants : déterminisme à graphe et entrées égaux, les
 * résultats ne dépendent pas de l'ordre d'exécution ; en cas de
 * doute, sur-invalider ; aucune empreinte ne contient de chemin
 * absolu ; un build sans changement ne touche ni le disque ni le
 * store au chaud.</p>
 */
package jo.joide.tasks;
