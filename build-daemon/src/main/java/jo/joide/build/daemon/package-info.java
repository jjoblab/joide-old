/**
 * Démon de build : protocole IPC par cadres JSON sur les flux
 * standard, client de lancement, exécuteur équivalent en processus.
 *
 * <p>Invariants : le contexte se reconstruit depuis le disque (le
 * store persistant est la mémoire du démon) ; la mort du démon —
 * OOM, kill -9 — est un événement pour le client, jamais la sienne ;
 * les compilateurs et dexeurs restent chauds entre les builds d'un
 * même espace (une instance par démon, publiée par compteur).</p>
 */
package jo.joide.build.daemon;
