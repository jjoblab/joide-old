// Convention des modules Android JoIDE (P8, ADR-015/045) : bibliothèque
// AGP — javac strict, artefacts 17 construits au JDK 21 (compileOptions),
// minSdk 28 (ADR-009), compileSdk 37.2 (couvre celeris 36 et LayoutLib
// 37.2), lint bloquant sans baseline (ADR-013 : NewApi pour les modules
// Android), contrôle API 28 sur les classes compilées de la variante
// release (même lecteur de pool de constantes que les modules purs),
// JUnit 5, règle de dépendances. Aucun outil d'analyse statique (§10).
// Le plugin `java` n'est JAMAIS appliqué ici (conflit avec AGP) : les
// parties utiles de joide.java-conventions sont répliquées.

import com.android.build.api.dsl.LibraryExtension
import jo.joide.buildlogic.DependencyRuleCheck
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("com.android.library")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure(LibraryExtension::class.java) {
    namespace = "jo.joide." + project.name.replace('-', '.')
    compileSdk = 37
    compileSdkMinor = 2
    defaultConfig {
        minSdk = 28 // ADR-009 ; les briques sont à minSdk 24/26, toutes ≤ 28
    }
    compileOptions {
        sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
        targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
    }
    lint {
        // ADR-013 : lint bloquant pour les modules Android, zéro baseline.
        abortOnError = true
    }
    testOptions {
        unitTests {
            // Les tests unitaires restent JVM : le android.jar factice
            // rend les valeurs par défaut (jamais d'AndroidX de test).
            isReturnDefaultValues = true
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

// Tests : JUnit 5 uniquement (§10), plateforme Jupiter, échecs affichés.
dependencies {
    "testImplementation"(libs.findLibrary("junit-jupiter").get())
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

// Règle de dépendances : vérifiée à la configuration de chaque build —
// une arête hors gradle/module-graph.txt échoue immédiatement.
project.afterEvaluate {
    DependencyRuleCheck.enforce(project, rootProject.file("gradle/module-graph.txt"))
}

// Contrôle API 28 des modules Android : lint NewApi bloquant sans
// baseline (ADR-013) — il couvre android.* ET java.* contre minSdk 28.
// Le scan de pool de constantes reste l'outil des modules purs (leur
// fichier de signatures couvre la surface java/javax du niveau 28) ;
// il n'est pas appliqué ici : les références android.* y seraient
// faussement interdites (aucune classe de plateforme dans le fichier).
