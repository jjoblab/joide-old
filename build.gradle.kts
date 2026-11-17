// Racine JoIDE : agrégation des contrôles. Aucune logique de module
// ici — les réglages communs vivent dans build-logic.

import org.gradle.api.tasks.Exec

// Politique de langue : script Python sans dépendance,
// adapté de celeris (voir NOTICE). Échoue sur toute violation.
val checkLanguagePolicy = tasks.register<Exec>("checkLanguagePolicy") {
    group = "verification"
    description = "Language policy: identifiers, Javadoc, exception messages, typography."
    commandLine("python3", "scripts/check-language-policy", ".")
}

// « check » racine : tous les modules + politique de langue.
tasks.register("check") {
    group = "verification"
    description = "Every module check plus the language policy."
    dependsOn(checkLanguagePolicy)
    subprojects.forEach { dependsOn("${it.path}:check") }
}

// CI locale : check complet + bench S et M, dont la porte de
// déterminisme intégrée (BenchMain échoue si les exécutions d'un même
// tier divergent). La comparaison temporelle aux baselines
// (regressionTest<S|M>) est une tâche séparée, à exécuter sur une
// machine de performance stable. Aucun SDK Android requis.
tasks.register("ciLocal") {
    group = "verification"
    description = "Local acceptance: checks plus S and M bench runs."
    dependsOn(":check", ":tools-bench:benchS", ":tools-bench:benchM")
}
