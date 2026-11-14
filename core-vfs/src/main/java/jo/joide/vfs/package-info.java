/**
 * Snapshot persistant de l'arbre de fichiers et journal de changements fusionné (éditeur, watcher, rattrapage).
 *
 * <p>Invariants : chemins toujours relatifs à une racine nommée ; hash paresseux ; sur doute, re-scan.
 *
 * <p>Phase de remplissage : P1 (squelette en P0, ADR-011).</p>
 */
package jo.joide.vfs;
