// Convention de base des modules JoIDE : javac strict — le compilateur
// est le SEUL outil de qualité (ni Checkstyle, ni PMD,
// ni analyse statique tierce, ni seuil de couverture) —, artefacts 17
// construits au JDK 21, UTF-8, plateforme de test JUnit 5, règle de
// dépendances et contrôle API 28.

import jo.joide.buildlogic.Api28ScanTask
import jo.joide.buildlogic.DependencyRuleCheck
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    java
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

// JDK qui exécute Gradle = JDK 21 ; les artefacts visent le niveau 17
// (options.release : la surface d'API 17 est garantie par javac).
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}

// Tests : JUnit 5 uniquement, aucune dépendance de test tierce.
dependencies {
    "testImplementation"(libs.findLibrary("junit-jupiter").get())
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Règle de dépendances : vérifiée à la configuration de chaque
// build — une arête hors gradle/module-graph.txt échoue immédiatement.
project.afterEvaluate {
    DependencyRuleCheck.enforce(project, rootProject.file("gradle/module-graph.txt"))
}

// Contrôle API 28 : lecture du pool de constantes des
// classes produites, comparaison aux signatures du platform 28
// (gradle/api28-signatures.txt.gz, généré par scripts/generate_api28_
// signatures.py). Les références aux constructions réécrites par D8
// (lambdas, concaténation de chaînes) sont explicitement autorisées.
// Exemption documentée : tools-bench et tools-test sont des modules
// « test-only », jamais exécutés sur Android — le contrôle API 28 ne
// les concerne pas ; le harnais de mesure y utilise les API de la JVM
// de bureau (temps CPU du processus), absentes du niveau 28.
val api28Exempt = project.name == "tools-bench" || project.name == "tools-test"
if (!api28Exempt) {
    val checkApi28 = tasks.register<Api28ScanTask>("checkApi28") {
        group = "verification"
        description = "Rejects main-class references outside the Android 28 API."
        classesDirs.from(sourceSets.main.get().output.classesDirs)
        signaturesFile.set(rootProject.file("gradle/api28-signatures.txt.gz"))
        dependsOn(tasks.named("classes"))
    }
    tasks.named("check") {
        dependsOn(checkApi28)
    }
}
