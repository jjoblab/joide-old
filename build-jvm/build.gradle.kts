// build-jvm — pipeline JVM : compilation Java/Kotlin pilotée (ecj, kotlinc),
// ABI par classe, graphe classe-classe, manifestes ABI, jar déterministe,
// ressources, génération de sources (SPI), exécution JVM.
//
// ecj (EPL-2.0) et le compilateur Kotlin embarqué (Apache-2.0) sont des
// dépendances séparées : obligations consignées dans NOTICE et
// docs/third-party.md. Le pilote n'utilise aucune API JDK absente du
// niveau 28 (contrôle checkApi28).
plugins {
    id("joide.java-library-conventions")
}

dependencies {
    api(project(":core-tasks"))
    implementation(libs.ecj)
    implementation(libs.kotlin.compiler.embeddable)
    implementation(libs.kotlin.metadata.jvm)
}
