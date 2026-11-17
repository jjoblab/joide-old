// Init script de VÉRIFICATION de l'export uniquement (P7) — pas une
// donnée de l'export. La machine de développement était bridée par
// Maven Central (erreur 429)  ; cache-redirector.jetbrains.com
// sert Central directement (sans redirection). L'export lui-même reste fidèle :
// ses fichiers déclarent mavenCentral()/google().
//
// Usage : -Djoide.export.init.script=scripts/export-verification-init.gradle.kts

settingsEvaluated {
    pluginManagement.repositories.all {
        if (this is org.gradle.api.artifacts.repositories.MavenArtifactRepository
                && url.toString().contains("repo.maven.apache.org")) {
            url = uri("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
        }
    }
    dependencyResolutionManagement.repositories.all {
        if (this is org.gradle.api.artifacts.repositories.MavenArtifactRepository
                && url.toString().contains("repo.maven.apache.org")) {
            url = uri("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
        }
    }
}

allprojects {
    buildscript.repositories.all {
        if (this is org.gradle.api.artifacts.repositories.MavenArtifactRepository
                && url.toString().contains("repo.maven.apache.org")) {
            url = uri("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
        }
    }
    repositories.all {
        if (this is org.gradle.api.artifacts.repositories.MavenArtifactRepository
                && url.toString().contains("repo.maven.apache.org")) {
            url = uri("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
        }
    }
}
