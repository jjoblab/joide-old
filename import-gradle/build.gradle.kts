// import-gradle — lecture statique et tolérante des projets Gradle
// (settings, build, catalogues, propriétés) et export vers Gradle ;
// aucun script n'est jamais exécuté (ADR-039).
plugins {
    id("joide.java-library-conventions")
}

dependencies {
    api(project(":core-model"))
    api(project(":core-deps"))
}
