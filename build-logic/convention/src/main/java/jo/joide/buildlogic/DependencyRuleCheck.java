package jo.joide.buildlogic;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;

/**
 * Application de la règle de dépendances à un projet Gradle (ADR-011).
 *
 * <p>Chaque configuration (main comme test) est parcourue après
 * évaluation du projet : toute dépendance de projet doit figurer dans la
 * liste autorisée de gradle/module-graph.txt. Un module absent du graphe
 * est une erreur (le graphe doit couvrir l'ensemble des modules).</p>
 */
public final class DependencyRuleCheck {

    private DependencyRuleCheck() {
    }

    /**
     * Vérifie le projet ; en cas de violation, lève
     * {@link org.gradle.api.GradleException} avec toutes les arêtes
     * fautives.
     *
     * @param project    le projet (module) vérifié
     * @param graphFile  gradle/module-graph.txt à la racine du dépôt
     */
    public static void enforce(Project project, File graphFile) {
        if (project.getParent() == null) {
            return; // la racine n'est pas un module
        }
        ModuleGraph graph = ModuleGraph.load(graphFile.toPath());
        String module = project.getName();
        if (!graph.knows(module)) {
            throw new org.gradle.api.GradleException(
                    "dependency rule: module '" + module
                            + "' is missing from gradle/module-graph.txt"
                            + " (add it in descending order)");
        }
        Set<String> allowed = new LinkedHashSet<>(graph.dependenciesOf(module));
        Set<String> violations = new LinkedHashSet<>();
        project.getConfigurations().all(configuration -> {
            for (Dependency dep : configuration.getDependencies()) {
                if (dep instanceof ProjectDependency) {
                    String target = dep.getName();
                    if (!allowed.contains(target)) {
                        violations.add(module + " -> :" + target
                                + " (configuration '" + configuration.getName()
                                + "') is not allowed by gradle/module-graph.txt");
                    }
                }
            }
        });
        if (!violations.isEmpty()) {
            StringBuilder message = new StringBuilder(
                    "dependency rule violation(s) in module '" + module + "':");
            for (String violation : violations) {
                message.append("\n  - ").append(violation);
            }
            message.append("\n  Allowed dependencies: ")
                    .append(allowed.isEmpty()
                            ? "(none)" : String.join(", ", allowed));
            throw new org.gradle.api.GradleException(message.toString());
        }
    }
}
