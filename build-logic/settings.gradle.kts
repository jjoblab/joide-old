// build-logic — build isolé qui compile les plugins de convention JoIDE.
// Le DSL Kotlin reste confiné ici et dans les fichiers de build des
// modules : aucun Kotlin ne fuit dans les artefacts publiés — le
// produit reste Java exclusivement.
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            // Catalogue unique : celui du dépôt racine.
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"

include(":convention")
