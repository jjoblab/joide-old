// core-tasks — moteur de tâches : graphe validé (dépendances dures,
// ordre, inférence par préfixe), ordonnanceur à file de prêtes sans
// barrière de niveau, admission de ressources, empreintes
// relocalisables, cache de sorties adressé par contenu et build scan.
plugins {
    id("joide.java-library-conventions")
}

dependencies {
    api(project(":core-store"))
    api(project(":core-vfs"))
    api(project(":core-model"))
}
