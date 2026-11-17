package jo.joide.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Assemblage déterministe du classpath d'un module : ordre stable,
 * déduplication, promotion de portée et règle de conflit de versions.
 *
 * <p>L'ordre d'entrée des dépendances ne change jamais le résultat :
 * les modules viennent dans l'ordre des niveaux topologiques puis par
 * nom, les externes par coordonnée. Une même cible vue plusieurs fois
 * n'apparaît qu'une fois, à la portée la plus forte. Les conflits de
 * versions externes se résolvent selon la règle choisie : la plus
 * récente gagne, ou la contrainte stricte, ou l'échec explicite.</p>
 */
public final class ClasspathBuilder {

    /** Résolution des conflits de versions externes. */
    public enum ConflictRule {
        /** La version la plus récente gagne, silencieusement. */
        NEWEST_WINS,

        /** Toute version déjà retenue est un invariant : conflit = échec. */
        STRICTLY,

        /** Tout conflit est une erreur, quelle que soit la version. */
        FAIL_ON_CONFLICT
    }

    private static final Comparator<String> VERSION_ORDER = ClasspathBuilder::compareVersions;

    private final Workspace workspace;
    private final ModuleTopology topology;
    private final ConflictRule rule;

    /**
     * @param workspace espace de travail
     * @param topology  topologie des modules
     * @param rule      règle de conflit de versions
     */
    public ClasspathBuilder(Workspace workspace, ModuleTopology topology,
                            ConflictRule rule) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.topology = Objects.requireNonNull(topology, "topology");
        this.rule = Objects.requireNonNull(rule, "rule");
    }

    /**
     * Assemble le classpath d'exécution d'un module : ses dépendances
     * directes, puis les dépendances transitives visibles (portée
     * d'API).
     *
     * @param module module dont on assemble le classpath
     * @return le classpath assemblé, ordonné et dédupliqué
     * @throws ModelException si la règle de conflit rejette une version
     */
    public Classpath build(Module module) {
        Objects.requireNonNull(module, "module");
        Map<String, ClasspathEntry> merged = new LinkedHashMap<>();
        // propres dépendances du module
        for (DependencyRef dependency : module.dependencies()) {
            absorb(dependency, merged);
        }
        // dépendances transitives des dépendances de module, en ordre
        // topologique : un module de niveau inférieur avant son dépendant
        List<String> order = topologicalOrder();
        for (String path : order) {
            ClasspathEntry entry = merged.get("m:" + path);
            if (entry == null) {
                continue;
            }
            Module direct = workspace.module(path);
            for (DependencyRef dependency : direct.dependencies()) {
                if (dependency.isModule() && dependency.scope() == DependencyScope.API) {
                    absorbTransitive(dependency, merged);
                } else if (dependency.isModule()
                        && dependency.scope() == DependencyScope.IMPLEMENTATION) {
                    // invisible des dépendants : absent du classpath transitif
                } else if (!dependency.isModule()
                        && dependency.scope() == DependencyScope.API) {
                    absorbTransitive(dependency, merged);
                }
            }
        }
        // ordre final : modules par niveau puis nom, puis externes par coordonnée
        List<ClasspathEntry> modules = new ArrayList<>();
        List<ClasspathEntry> externals = new ArrayList<>();
        for (ClasspathEntry entry : merged.values()) {
            if (entry.modulePath() != null) {
                modules.add(entry);
            } else {
                externals.add(entry);
            }
        }
        modules.sort(Comparator
                .comparingInt((ClasspathEntry e) -> topology.levelOf(e.modulePath()))
                .thenComparing(ClasspathEntry::modulePath));
        externals.sort(Comparator.comparing(ClasspathEntry::coordinates));
        List<ClasspathEntry> all = new ArrayList<>(modules);
        all.addAll(externals);
        return new Classpath(all);
    }

    private List<String> topologicalOrder() {
        List<String> order = new ArrayList<>();
        for (List<String> level : topology.levelOrder()) {
            order.addAll(level);
        }
        return order;
    }

    private void absorb(DependencyRef dependency, Map<String, ClasspathEntry> merged) {
        absorbInternal(dependency, merged, false);
    }

    private void absorbTransitive(DependencyRef dependency,
                                  Map<String, ClasspathEntry> merged) {
        absorbInternal(dependency, merged, true);
    }

    private void absorbInternal(DependencyRef dependency,
                                Map<String, ClasspathEntry> merged,
                                boolean transitive) {
        if (dependency.scope().isTest() || dependency.scope() == DependencyScope.COMPILE_ONLY
                || dependency.scope() == DependencyScope.RUNTIME_ONLY) {
            return;
        }
        if (dependency.isModule()) {
            Module target = workspace.module(dependency.modulePath());
            if (target == null) {
                throw new ModelException("unknown module dependency: "
                        + dependency.modulePath());
            }
            ClasspathEntry entry = ClasspathEntry.ofModule(target, dependency.scope());
            mergeEntry(entry, merged, transitive);
            return;
        }
        String[] parts = dependency.coordinates().split(":");
        String coordinates = parts[0] + ":" + parts[1];
        String version = parts[2];
        ClasspathEntry candidate =
                ClasspathEntry.external(coordinates, version, dependency.scope());
        ClasspathEntry existing = merged.get(candidate.identityKey());
        if (existing == null) {
            merged.put(candidate.identityKey(), candidate);
            return;
        }
        int comparison = VERSION_ORDER.compare(version, existing.version());
        if (comparison == 0) {
            mergeEntry(candidate, merged, transitive);
            return;
        }
        switch (rule) {
            case NEWEST_WINS:
                if (comparison > 0) {
                    merged.put(candidate.identityKey(), candidate);
                }
                break;
            case STRICTLY:
                throw new ModelException("strict version conflict for "
                        + coordinates + ": " + existing.version() + " vs " + version);
            case FAIL_ON_CONFLICT:
                throw new ModelException("version conflict for "
                        + coordinates + ": " + existing.version() + " vs " + version);
            default:
                throw new IllegalStateException("unknown conflict rule");
        }
    }

    /**
     * Fusionne une entrée avec l'existante : première position
     * conservée, portée la plus forte retenue.
     */
    private void mergeEntry(ClasspathEntry entry, Map<String, ClasspathEntry> merged,
                            boolean transitive) {
        ClasspathEntry existing = merged.get(entry.identityKey());
        if (existing == null) {
            merged.put(entry.identityKey(), entry);
            return;
        }
        DependencyScope strongest = strongest(existing.scope(), entry.scope());
        if (strongest != existing.scope()) {
            ClasspathEntry promoted = existing.modulePath() != null
                    ? ClasspathEntry.ofModule(
                            workspace.module(existing.modulePath()), strongest)
                    : ClasspathEntry.external(existing.coordinates(),
                            existing.version(), strongest);
            merged.put(existing.identityKey(), promoted);
        }
    }

    private static DependencyScope strongest(DependencyScope first,
                                             DependencyScope second) {
        return first == DependencyScope.API || second == DependencyScope.API
                ? DependencyScope.API
                : DependencyScope.IMPLEMENTATION;
    }

    /**
     * Ordre de versions : numéros séparés par des points, le plus
     * récent en dernier ; les suffixes après un tiret ordonnent
     * alphabétiquement.
     *
     * @param left  première version
     * @param right seconde version
     * @return négatif si {@code left} est plus ancienne
     */
    static int compareVersions(String left, String right) {
        String[] leftParts = left.split("[-.]", -1);
        String[] rightParts = right.split("[-.]", -1);
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            String a = i < leftParts.length ? leftParts[i] : "";
            String b = i < rightParts.length ? rightParts[i] : "";
            boolean aNumeric = a.chars().allMatch(Character::isDigit) && !a.isEmpty();
            boolean bNumeric = b.chars().allMatch(Character::isDigit) && !b.isEmpty();
            if (aNumeric && bNumeric) {
                int comparison = Long.compare(Long.parseLong(a), Long.parseLong(b));
                if (comparison != 0) {
                    return comparison;
                }
            } else {
                int comparison = a.compareTo(b);
                if (comparison != 0) {
                    return comparison;
                }
            }
        }
        return 0;
    }
}
