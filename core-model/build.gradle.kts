// core-model — modèle de projet déclaratif (workspace, projets,
// modules, ensembles de sources, facettes), parseur TOML tolérant,
// topologie des modules et assemblage déterministe de classpath.
plugins {
    id("joide.java-library-conventions")
}

dependencies {
    api(project(":core-platform"))
    api(project(":core-vfs"))
}
