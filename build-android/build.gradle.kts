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
    // Lot VII : l'ouvrier de la VM forkée — le test bout-en-bout du
    // protocole le lance en sous-processus (java -cp … KotlinWorkerMain)
    "testImplementation"(project(mapOf("path" to ":build-jvm",
            "configuration" to "kotlinWorkerApi")))
    "testImplementation"(libs.r8)
    "testImplementation"(libs.apksig)
    "testImplementation"(libs.bundletool)
    "testImplementation"(libs.kotlin.compiler.embeddable)
    "testImplementation"(libs.kotlin.metadata.jvm)
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

// Lot VIII (ADR-070) : le jar de la SIGNATURE seul. ApksigSigner
// reste EMBARQUÉ dans l'APK (apksig trop léger pour un pack,
// décision du lot VI) mais les adaptateurs des outils PACKÉS
// (InProcessDexer, BundletoolAssembler) n'y vivent PLUS : une copie
// embarquée empoisonnait le pack — la délégation parent-d'abord du
// DexClassLoader servait la copie de l'APK (sans ses classes
// d'outil) et JAMAIS celle du pack : « pack différé r8 indisponible
// (java.lang.reflect.InvocationTargetException) », rapport du
// 2026-11-17. L'ombre part, le pack charge (la manière CodeAssist :
// aucune classe d'outil dans l'app, tout arrive déjà dexé).
val signatureJar = tasks.register<Jar>("signatureJar") {
    archiveBaseName.set("build-android-signature")
    from(sourceSets.named("tools").get().output) {
        include("jo/joide/build/android/tools/ApksigSigner*")
    }
}

val signatureApi by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add("toolsApi", toolsJar)
    add("signatureApi", signatureJar)
}

// Lot VII (VM forkée) : RELAIS de l'ouvrier Kotlin (source set
// workerKotlin de build-jvm) vers le consommateur du pack —
// app-android ne dépend jamais de build-jvm directement (ADR-011),
// l'ouvrier part au dexage du pack kotlin-compiler.dex.zip par
// cette configuration, comme les adaptateurs par toolsApi.
val kotlinWorkerApi by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

// Lot XI : le PATCHEUR ART et ses shims transitent pareillement
// (le patcheur et sa fermeture ASM, les shims ART dexés dans le pack)
val packArtApi by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}
val shimsArtApi by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

dependencies {
    kotlinWorkerApi(project(mapOf("path" to ":build-jvm",
            "configuration" to "kotlinWorkerApi")))
    packArtApi(project(mapOf("path" to ":build-jvm",
            "configuration" to "packArtApi")))
    packArtApi(libs.asm)
    packArtApi(libs.asm.tree)
    packArtApi(libs.asm.commons)
    shimsArtApi(project(mapOf("path" to ":build-jvm",
            "configuration" to "shimsArtApi")))
}
