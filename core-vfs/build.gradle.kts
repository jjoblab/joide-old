// core-vfs — snapshot persistant de l'arbre de fichiers, journal de
// changements fusionné (éditeur, surveilleur, rattrapage), empreintes
// d'arbre par dossier. Les chemins sont relatifs à une racine nommée.
plugins {
    id("joide.java-library-conventions")
}

dependencies {
    api(project(":core-platform"))
    api(project(":core-store"))
}
