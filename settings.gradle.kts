// JoIDE — dépôt unique, modules purs JVM (critère : constructibles et
// testables SANS SDK Android). Les modules Android
// rejoindront le conditionnel SDK : inclusion seulement si ANDROID_HOME (ou
// local.properties) est détecté, refus explicite par JOIDE_SKIP_ANDROID=1.
// Les modules démarrent comme des projets JVM vides ou presque : la règle
// de dépendances (gradle/module-graph.txt) couvre déjà le graphe cible.
pluginManagement {
    // Les conventions vivent dans le build isolé build-logic.
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Briques du propriétaire (§6.8, ADR-044) : publiées en Maven Local
        // depuis les checkouts épinglés (procédure dans docs/dev.md).
        mavenLocal()
        google()
        mavenCentral()
        // Dépendances des moteurs code-lsp issues d'intellij-dependencies
        // (syntax-api, lang-syntax, java-syntax — P-K2 levée le 31/10,
        // DEMANDES.md D-3) : même dépôt que la brique, filtré au groupe
        // org.jetbrains pour ne pas détourner kotlin-stdlib (Maven Central).
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies") {
            content {
                includeGroup("org.jetbrains")
            }
        }
    }
}

rootProject.name = "joide"

include(":core-platform")
include(":core-store")
include(":core-vfs")
include(":core-model")
include(":core-tasks")
include(":core-deps")
include(":agent")
include(":build-jvm")
include(":build-android")
include(":build-daemon")
include(":import-gradle")
include(":tools-bench")
include(":tools-test")

// Conditionnel Android (ADR-015, appliqué en P8) : ide-integration et
// app-android sont des modules AGP (bibliothèque / application). Ils ne
// rejoignent le build que si un SDK est détecté ; sans SDK, le `check`
// des modules purs reste le critère P0 — pas une dégradation.
// Le refus explicite JOIDE_SKIP_ANDROID=1 prime sur toute détection.
val skipAndroid = providers.gradleProperty("JOIDE_SKIP_ANDROID")
        .orElse(providers.environmentVariable("JOIDE_SKIP_ANDROID")).orElse("")
        .get() == "1"
val sdkFromEnv = providers.environmentVariable("ANDROID_HOME")
        .orElse(providers.environmentVariable("ANDROID_SDK_ROOT")
        ).orElse("").get()
val localProps = java.util.Properties().also {
    val f = rootDir.resolve("local.properties")
    if (f.isFile) f.inputStream().use { s -> it.load(s) }
}
val sdkDir = sdkFromEnv.ifBlank { localProps.getProperty("sdk.dir") ?: "" }
val androidOn = !skipAndroid && sdkDir.isNotBlank()
if (androidOn) {
    include(":ide-integration")
    include(":app-android")
    logger.lifecycle("joide : SDK Android détecté ($sdkDir) — modules Android inclus (ide-integration, app-android)")
} else if (skipAndroid) {
    logger.lifecycle("joide : JOIDE_SKIP_ANDROID=1 — modules Android non inclus (refus explicite)")
} else {
    logger.lifecycle("joide : SDK Android absent — modules Android non inclus ; le check des modules purs reste le critère P0")
}
