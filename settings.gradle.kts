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
        google()
        mavenCentral()
    }
}

rootProject.name = "joide"

include(":core-platform")
include(":core-store")
include(":core-vfs")
include(":core-model")
include(":core-tasks")
include(":core-deps")
include(":build-jvm")
include(":build-android")
include(":build-daemon")
include(":import-gradle")
include(":tools-bench")
include(":tools-test")
include(":app-android")
include(":ide-integration")
