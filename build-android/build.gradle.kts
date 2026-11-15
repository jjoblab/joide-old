// build-android — pipeline Android : variantes, aapt2 (compilation
// incrémentale par fichier, lien, R), dex par scopes avec cache
// partageable entre projets, fusion par buckets, package (zipalign),
// signature, AAB, R8.
//
// Le source set principal reste sous contrôle API 28 : il ne référence
// aucun symbole de bibliothèque tierce. Les adaptations qui parlent aux
// outils Google (D8/R8 en processus, apksig, bundletool) vivent dans le
// source set « tools », hors contrôle API 28, chargées par ServiceLoader
// — même séparation que le pilote SQLite de core-store.
plugins {
    id("joide.java-library-conventions")
}

val tools by sourceSets.creating

dependencies {
    api(project(":build-jvm"))
    api(project(":core-deps"))

    // Adaptations d'outils (bureau et daemon embarqué) : jamais dans le
    // source set principal (contrôle API 28, préfixe com/android interdit).
    "toolsImplementation"(sourceSets.main.get().output)
    "toolsImplementation"(project(":build-jvm"))
    "toolsImplementation"(project(":core-deps"))
    "toolsImplementation"(libs.r8)
    "toolsImplementation"(libs.apksig)
    "toolsImplementation"(libs.bundletool)
    "toolsImplementation"(libs.guava)

    "testImplementation"(project(":build-jvm"))
    "testImplementation"(sourceSets.named("tools").get().output)
    "testImplementation"(libs.r8)
    "testImplementation"(libs.apksig)
    "testImplementation"(libs.bundletool)
    "testImplementation"(libs.kotlin.compiler.embeddable)
    "testImplementation"(libs.kotlin.stdlib)
}

// Le contrôle « check » compile aussi le source set tools.
tasks.named("check") {
    dependsOn(tasks.named("toolsClasses"))
}

// Les adaptateurs d'outils sont exposés aux modules aval qui
// exécutent le pipeline en machine virtuelle séparée : le démon (P6)
// charge D8/R8, apksig et bundletool en processus par ServiceLoader.
// Les jars des outils sont déclarés par le consommateur (même
// catalogue de versions).
val toolsJar = tasks.register<Jar>("toolsJar") {
    archiveBaseName.set("build-android-tools")
    from(sourceSets.named("tools").get().output)
}

val toolsApi by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add("toolsApi", toolsJar)
}
