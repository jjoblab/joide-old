/**
 * Snapshot persistant de l'arbre de fichiers, journal de changements fusionné (éditeur, surveilleur, rattrapage) et empreintes d'arbre par dossier.
 *
 * <p>Invariants : chemins toujours relatifs à une racine nommée ; empreinte de contenu paresseuse, calculée seulement quand les signaux changent ; sur débordement, re-scan exigé plutôt que fusion incertaine.
 */
package jo.joide.vfs;
