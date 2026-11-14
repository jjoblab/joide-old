// core-store — l'interface BuildStore (source set principal, API 28
// stricte) et son implémentation de bureau (source set jvm : pilote
// SQLite JDBC, API absentes d'Android). L'implémentation Android du
// même contrat vivra avec le démon.
plugins {
    id("joide.java-library-conventions")
}

val jvm by sourceSets.creating

dependencies {
    api(project(":core-platform"))

    // Implémentation de bureau : pilote SQLite, jamais exécutée sur
    // Android (d'où un source set séparé, hors contrôle API 28).
    "jvmImplementation"(sourceSets.main.get().output)
    "jvmImplementation"(project(":core-platform"))
    "jvmImplementation"(libs.sqlite.jdbc)

    "testImplementation"(sourceSets.named("jvm").get().output)
    "testImplementation"(libs.sqlite.jdbc)
}

// Le contrôle « check » compile aussi le source set jvm.
tasks.named("check") {
    dependsOn(tasks.named("jvmClasses"))
}
