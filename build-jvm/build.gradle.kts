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

val workerKotlin by sourceSets.creating
val packArt by sourceSets.creating

// Lot XIX : le jar du plugin compilateur Compose copié en RESSOURCE
// du jar build-jvm (PluginsKotlin.jarCompose l'extrait du classpath
// pour le pilote embarqué du bureau ; l'appareil le prend du pack).
// Copié au montage depuis le catalogue — jamais committé.
val composePlugin by configurations.creating {
    isCanBeConsumed = false
    // le jar du plugin SEUL — ses transitifs (le compilateur, déjà
    // là) renommés produiraient une entrée dupliquée
    isTransitive = false
}
dependencies {
    composePlugin(libs.compose.compiler.plugin)
}
val copierPluginCompose = tasks.register<Copy>("copierPluginCompose") {
    from(composePlugin)
    rename { "kotlin-compose-compiler-plugin.jar" }
    into(layout.buildDirectory.dir("ressources-compose"))
}
sourceSets.named("main") {
    resources.srcDir(copierPluginCompose)
}

dependencies {
    api(project(":core-tasks"))
    // ecj : la COMPILATION prend l'artefact Central ; le RUNTIME de
    // l'APK ne le dexé PAS — app-android l'exclut et prend le jar
    // ecj-art de la brique code-lsp (le MÊME artefact, resources
    // comprises : bundles messages, tables .rsc du parseur), la
    // doctrine éprouvée anti-doublons (art-compat : « shipping both
    // a stock Eclipse jar and its ART copy is a duplicate-class
    // failure at dex time »).
    implementation(libs.ecj)
    implementation(libs.kotlin.compiler.embeddable)
    implementation(libs.kotlin.metadata.jvm)

    // Lot VII (VM forkée) : l'OUVRIER du compilateur Kotlin —
    // auto-contenu (aucune classe jo.joide.*), il vit dans le pack de
    // dex différé kotlin-compiler.dex.zip et tourne dans une VM non
    // zygote sur l'appareil ; sur le bureau il sert aux essais du
    // protocole (java -cp … KotlinWorkerMain).
    "workerKotlinImplementation"(libs.kotlin.compiler.embeddable)
    "workerKotlinImplementation"(libs.kotlin.metadata.jvm)
    "workerKotlinImplementation"(libs.kotlin.stdlib)

    // Lot XI (la manière éprouvée, passes kotlinc-art) : le
    // PATCHEUR ART du pack — réécrit les classes du compilateur
    // hostiles à ART avant le dexage (management absent, Unsafe,
    // ClassValue, fastutil dispatch, Introspector, EDT, PathUtil) et
    // fournit les shims ART qu'elles appellent. Le compilateur
    // (relocalisé) sert de classpath de compilation pour les types
    // que le patcheur touche ; ASM réécrit le bytecode.
    "packArtImplementation"(libs.kotlin.compiler.embeddable)
    "packArtImplementation"(libs.asm)
    "packArtImplementation"(libs.asm.tree)
    "packArtImplementation"(libs.asm.commons)

    // les essais du patcheur relisent les classes réécrites (ASM) et
    // touchent les classes réelles du compilateur (classpath de test)
    "testImplementation"(sourceSets.named("packArt").get().output)
    "testImplementation"(libs.asm)
    "testImplementation"(libs.asm.tree)
    "testImplementation"(libs.asm.commons)
}

// Le contrôle « check » compile aussi l'ouvrier et le patcheur.
tasks.named("check") {
    dependsOn(tasks.named("workerKotlinClasses"))
    dependsOn(tasks.named("packArtClasses"))
}

val workerKotlinJar = tasks.register<Jar>("workerKotlinJar") {
    archiveBaseName.set("build-jvm-worker-kotlin")
    from(sourceSets.named("workerKotlin").get().output)
}

val packArtJar = tasks.register<Jar>("packArtJar") {
    archiveBaseName.set("build-jvm-pack-art")
    from(sourceSets.named("packArt").get().output)
}

// Les shims ART seuls (jo.joide.build.jvm.artcompat.*) : dexés DANS le
// pack kotlin à côté du compilateur patché qui les appelle — jamais
// dans l'APK ni sur le classpath du bureau.
val shimsArtJar = tasks.register<Jar>("shimsArtJar") {
    archiveBaseName.set("build-jvm-artcompat-shims")
    from(sourceSets.named("packArt").get().output) {
        include("jo/joide/build/jvm/artcompat/*")
    }
}

// Le jar de l'ouvrier est exposé au consommateur du pack (app-android,
// tâche dexPacksOutils) — même schéma que toolsApi de build-android.
val kotlinWorkerApi by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}
val packArtApi by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}
val shimsArtApi by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add("kotlinWorkerApi", workerKotlinJar)
    add("packArtApi", packArtJar)
    add("shimsArtApi", shimsArtJar)
}
