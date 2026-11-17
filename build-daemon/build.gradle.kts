// build-daemon — démon de build : protocole IPC (cadres JSON sur
// flux), client de lancement, exécuteur équivalent en processus, et
// point d'entrée de bureau (store SQLite persistant). Le source set
// principal reste sous contrôle API 28 (le client de l'IDE Android
// l'embarquera) ; le point d'entrée de bureau vit dans le source set
// « desktop », hors contrôle API 28 comme le pilote SQLite ; le banc
// de mesure (preuves de tas et de latence) vit dans « bench ».
plugins {
    id("joide.java-library-conventions")
}

val desktop by sourceSets.creating
val bench by sourceSets.creating

dependencies {
    api(project(":build-android"))

    // Point d'entrée de bureau : store persistant (pilote SQLite du
    // core-store), adaptateurs d'outils du pipeline Android (D8/R8,
    // apksig, bundletool — jars déclarés ici, même catalogue)
    "desktopImplementation"(sourceSets.main.get().output)
    "desktopImplementation"(project(":build-android"))
    "desktopImplementation"(project(":core-store"))
    "desktopImplementation"(
        project(mapOf("path" to ":core-store",
            "configuration" to "jvmApi")))
    "desktopImplementation"(
        project(mapOf("path" to ":build-android",
            "configuration" to "toolsApi")))
    "desktopImplementation"(libs.r8)
    "desktopImplementation"(libs.apksig)
    "desktopImplementation"(libs.bundletool)
    "desktopImplementation"(libs.guava)
    "desktopImplementation"(libs.sqlite.jdbc)
    "desktopImplementation"(libs.ecj)
    "desktopImplementation"(libs.kotlin.compiler.embeddable)

    "testImplementation"(project(":build-android"))
    "testImplementation"(sourceSets.named("desktop").get().output)
    "testImplementation"(
        project(mapOf("path" to ":core-store",
            "configuration" to "jvmApi")))
    "testImplementation"(
        project(mapOf("path" to ":build-android",
            "configuration" to "toolsApi")))
    "testImplementation"(libs.sqlite.jdbc)
    "testImplementation"(libs.r8)
    "testImplementation"(libs.apksig)
    "testImplementation"(libs.bundletool)
    "testImplementation"(libs.guava)
    "testImplementation"(libs.kotlin.compiler.embeddable)
    "testImplementation"(libs.kotlin.stdlib)
    "testImplementation"(libs.ecj)

    // Banc de mesure : le client du démon et le protocole (le banc
    // lance un démon réel ; l'exécution emporte le classpath desktop
    // complet — voir la tâche daemonBench)
    "benchImplementation"(sourceSets.main.get().output)
    "benchImplementation"(project(":build-android"))
}

// Le contrôle « check » compile aussi les source sets desktop et
// bench (le banc lui-même ne s'exécute pas au contrôle : le tier L
// coûte des minutes, sa place est à la demande comme les bancs
// tools-bench).
tasks.named("check") {
    dependsOn(tasks.named("desktopClasses"))
    dependsOn(tasks.named("benchClasses"))
}

// Preuves du démon par tier (S/M/L/XL) :
//   ./gradlew :build-daemon:daemonBench -Ptier=L
// génère le projet au gabarit du tier, le construit dans un démon au
// tas plafonné (700 Mo par défaut : le critère du tier L), mesure
// salutation, aller-retours de ping, build à froid (temps mur, pic
// de tas) et build sans changement à chaud ; échoue si le pic de tas
// dépasse le plafond.
val benchTier = providers.gradleProperty("tier").getOrElse("S")
val daemonHeapMb = providers.gradleProperty("daemonHeapMb")
    .getOrElse("700")
tasks.register<JavaExec>("daemonBench") {
    group = "bench"
    description = "Memory and latency proofs of the build daemon at one tier."
    mainClass.set("jo.joide.build.daemon.bench.DaemonBench")
    // le classpath du banc ET des machines virtuelles filles qu'il
    // lance : les classes du banc puis le classpath d'exécution
    // complet du démon de bureau (sorties desktop comprises)
    classpath = sourceSets.named("bench").get().output
        .plus(sourceSets.named("desktop").get().runtimeClasspath)
    args(listOf(
        benchTier,
        layout.buildDirectory.dir("daemon-bench/$benchTier").get()
            .asFile.absolutePath,
        daemonHeapMb,
    ))
}

