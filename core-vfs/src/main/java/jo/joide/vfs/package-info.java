/**
 * Snapshot persistant de l'arbre de fichiers et journal de changements fusionné (éditeur, watcher, rattrapage).
 *
 * <p>Invariants : chemins toujours relatifs à une racine nommée ; hash paresseux ; sur doute, re-scan.
 */
package jo.joide.vfs;
