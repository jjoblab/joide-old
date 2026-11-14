/**
 * Modèle de projet déclaratif : espace de travail, projets, modules, ensembles de sources, facettes, topologie et classpath déterministe.
 *
 * <p>Invariants : immuabilité stricte (les lecteurs voient des instantanés) ; chargement tolérant (un module corrompu est signalé, le reste charge) ; identifiants de contenu sans aucun chemin absolu ; écritures atomiques par lot.
 */
package jo.joide.model;
