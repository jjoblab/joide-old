// tools-bench — générateur déterministe, harnais de mesure et baselines.
// Module test-only : il ne dépend d'aucun module JoIDE.
plugins {
    id("joide.java-library-conventions")
}

dependencies {
    // Arête de test uniquement : le banc vérifie que ses projets
    // synthétiques sont lisibles par le modèle de projet.
    testImplementation(project(":core-model"))
}

// Hygiène de mesure : tas fixe — la variance temporelle doit
// venir du travail, pas du redimensionnement du tas (XL monte à
// 512 Mo de tas observés). Même réglage pour la création de baseline
// et pour la comparaison.
val benchJvmArgs = listOf("-Xms256m", "-Xmx512m")

// Mesure d'un tier (3 exécutions, déterminisme auto-vérifié par
// BenchMain : échec si les racines Merkle divergent) :
//   ./gradlew :tools-bench:bench -Ptier=S|M|L|XL
// écrit la baseline tools-bench/baselines/<tier>.toml.
val benchTier = providers.gradleProperty("tier").getOrElse("S")
tasks.register<JavaExec>("bench") {
    group = "bench"
    description = "Generates, measures and checks determinism of one tier."
    mainClass.set("jo.joide.bench.BenchMain")
    jvmArgs(benchJvmArgs)
    classpath = sourceSets.main.get().runtimeClasspath
    args(listOf(
        benchTier,
        layout.buildDirectory.dir("bench/$benchTier").get().asFile
            .absolutePath,
        "--baseline",
        project.file("baselines").toPath()
            .resolve("${benchTier.lowercase()}.toml").toFile().absolutePath,
    ))
}

// Variants à tier fixe : la CI locale (racine : ciLocal) lance S et M —
// la mesure inclut la porte de déterminisme (génération reproductible). L/XL restent à la demande (coût documenté au rapport).
listOf("S", "M", "L", "XL").forEach { tier ->
    tasks.register<JavaExec>("bench$tier") {
        group = "bench"
        description = "Measures tier $tier (determinism self-checked)."
        mainClass.set("jo.joide.bench.BenchMain")
        jvmArgs(benchJvmArgs)
        classpath = sourceSets.main.get().runtimeClasspath
        args(listOf(
            tier,
            layout.buildDirectory.dir("bench/$tier").get().asFile
                .absolutePath,
            "--baseline",
            project.file("baselines").toPath()
                .resolve("${tier.lowercase()}.toml").toFile().absolutePath,
        ))
    }
}

// Comparaison aux baselines commitées, par tier : exact sur le
// déterministe (hash, comptages, octets), ±10 % sur le temporel (temps
// CPU du processus ; repli temps mur + 50 ms si indisponible), scratch
// sur le même système de fichiers que la capture. À exécuter sur une
// machine de performance stable (machine de référence) : les
// valeurs temporelles d'un ordonnanceur partagé ne sont pas comparables.
listOf("S", "M", "L", "XL").forEach { tier ->
    tasks.register<JavaExec>("regressionTest$tier") {
        group = "bench"
        description = "Compares tier $tier against the committed baseline."
        mainClass.set("jo.joide.bench.BaselineCompare")
        jvmArgs(benchJvmArgs)
        classpath = sourceSets.main.get().runtimeClasspath
        args(listOf(
            tier,
            project.file("baselines").absolutePath,
            layout.buildDirectory.dir("regression/$tier").get().asFile
                .absolutePath,
        ))
    }
}
