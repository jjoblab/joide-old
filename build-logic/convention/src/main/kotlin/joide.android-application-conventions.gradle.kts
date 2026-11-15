// Convention de l'application Android JoIDE (P8, ADR-015/045) : même
// discipline que la bibliothèque (javac strict, 17/JDK 21, minSdk 28,
// compileSdk 37.2, lint bloquant, contrôle API 28, JUnit 5, règle de
// dépendances) sur com.android.application. L'identifiant d'application
// et la version vivent dans le build.gradle.kts du module.

import com.android.build.api.dsl.ApplicationExtension
import jo.joide.buildlogic.DependencyRuleCheck
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("com.android.application")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure(ApplicationExtension::class.java) {
    namespace = "jo.joide." + project.name.replace('-', '.')
    compileSdk = 37
    compileSdkMinor = 2
    defaultConfig {
        minSdk = 28 // ADR-009
        targetSdk = 28 // le produit vise le niveau 28 (D4) ; pas au-dessus
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
            isReturnDefaultValues = true
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

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

project.afterEvaluate {
    DependencyRuleCheck.enforce(project, rootProject.file("gradle/module-graph.txt"))
}

// Contrôle API 28 des modules Android : lint NewApi bloquant sans
// baseline (ADR-013) — il couvre android.* ET java.* contre minSdk 28.
// Le scan de pool de constantes reste l'outil des modules purs (leur
// fichier de signatures couvre la surface java/javax du niveau 28) ;
// il n'est pas appliqué ici : les références android.* y seraient
// faussement interdites (aucune classe de plateforme dans le fichier).
