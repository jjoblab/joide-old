package jo.joide.tasks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Poids de chemin critique restant de chaque tâche : la priorité de
 * l'ordonnanceur.
 *
 * <p>Le poids d'une tâche est sa durée estimée augmentée du plus
 * lourd chemin qui la sépare d'une tâche terminale, en suivant les
 * dépendances inversées. Les durées viennent de l'historique du
 * store ; en leur absence, une estimation par type de tâche est
 * utilisée. Plus le poids est grand, plus la tâche est urgente :
 * lancer en premier ce qui débloque la chaîne la plus longue
 * minimise le temps mur du build.</p>
 */
public final class CriticalPaths {

    private CriticalPaths() {
        // classe utilitaire
    }

    /**
     * Calcule le poids de chaque tâche.
     *
     * @param graph      graphe validé
     * @param durations  durée estimée en millisecondes par nom de
     *                   tâche ; une entrée absente est estimée par
     *                   le fournisseur par défaut
     * @param defaults   durée par défaut par type de tâche, pour les
     *                   tâches sans historique
     * @return le poids par nom de tâche, jamais {@code null}
     */
    public static Map<String, Long> weights(TaskGraph graph,
                                             Map<String, Long> durations,
                                             Map<String, Long> defaults) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(durations, "durations");
        Objects.requireNonNull(defaults, "defaults");
        Map<String, Long> weight = new HashMap<>();
        List<String> order = graph.topologicalOrder();
        // ordre topologique : les dépendances précèdent les
        // dépendants ; en le parcourant à l'envers, le poids d'une
        // tâche est connu avant que ses dépendantes ne le calculent
        for (int i = order.size() - 1; i >= 0; i--) {
            String name = order.get(i);
            long own = durationOf(graph.task(name), durations, defaults);
            long heaviestDependent = 0;
            for (String dependent : graph.dependentsOf(name)) {
                Long dependentWeight = weight.get(dependent);
                if (dependentWeight != null
                        && dependentWeight > heaviestDependent) {
                    heaviestDependent = dependentWeight;
                }
            }
            weight.put(name, own + heaviestDependent);
        }
        return weight;
    }

    private static long durationOf(TaskDefinition task,
                                   Map<String, Long> durations,
                                   Map<String, Long> defaults) {
        Long known = durations.get(task.name());
        if (known != null) {
            return known;
        }
        Long byType = defaults.get(task.taskType());
        if (byType != null) {
            return byType;
        }
        return 500;
    }

    /**
     * Estimations de durée par défaut des types courants, en
     * millisecondes. Affinées par l'historique dès la deuxième
     * exécution.
     *
     * @return la table type vers millisecondes, non modifiable
     */
    public static Map<String, Long> defaultTypeDurations() {
        Map<String, Long> defaults = new HashMap<>();
        defaults.put("compile", 1500L);
        defaults.put("kotlin", 2000L);
        defaults.put("dex", 2000L);
        defaults.put("merge", 800L);
        defaults.put("package", 1200L);
        defaults.put("resources", 400L);
        defaults.put("check", 300L);
        defaults.put("noop", 1L);
        return java.util.Collections.unmodifiableMap(defaults);
    }
}
