package jo.joide.ide.sync;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import jo.joide.deps.DependencyRequest;
import jo.joide.deps.Exposure;
import jo.joide.deps.VersionRequirement;
import jo.joide.deps.resolve.Resolver;
import jo.joide.deps.resolve.ResolverConfig;
import jo.joide.deps.resolve.ResolutionResult;
import jo.joide.model.DependencyRef;
import jo.joide.model.DependencyScope;
import jo.joide.model.Module;
import jo.joide.model.Workspace;

/**
 * Stratégies de résolution des dépendances externes d'un workspace,
 * pour la synchronisation du modèle de langage.
 */
public final class Resolutions {

    private Resolutions() {
    }

    /**
     * Résolution par le résolveur P4 : collecte les références
     * externes du workspace (une demande par exigence distincte),
     * résout, indexe.
     *
     * <p>Une exigence de version illisible (projet importé) est un
     * problème transporté, jamais un échec silencieux ni une
     * exception.</p>
     *
     * @param config configuration du résolveur (dépôts, cache, lockfile)
     * @return la fonction de résolution workspace → externes
     */
    public static Function<Workspace, ExternalResolution> byResolver(
            ResolverConfig config) {
        return workspace -> {
            List<DependencyRequest> requests = new ArrayList<>();
            List<String> problems = new ArrayList<>();
            Map<String, DependencyRequest> distinct = new LinkedHashMap<>();
            for (Module module : workspace.allModules().values()) {
                for (DependencyRef dep : module.dependencies()) {
                    if (dep.isModule()) {
                        continue;
                    }
                    String[] segments = dep.coordinates().split(":");
                    if (segments.length != 3) {
                        problems.add("invalid coordinates: "
                                + dep.coordinates() + " (module "
                                + module.path() + ")");
                        continue;
                    }
                    VersionRequirement requirement;
                    try {
                        requirement = VersionRequirement.parse(segments[2]);
                    } catch (RuntimeException e) {
                        problems.add("invalid version requirement: "
                                + dep.coordinates() + " (module "
                                + module.path() + ")");
                        continue;
                    }
                    String key = dep.coordinates() + ":"
                            + requirement + ":" + exposureOf(dep.scope());
                    distinct.putIfAbsent(key, new DependencyRequest(
                            segments[0], segments[1],
                            requirement, exposureOf(dep.scope())));
                }
            }
            requests.addAll(distinct.values());
            ResolutionResult result =
                    new Resolver(config).resolve(requests);
            ExternalResolution resolution = ExternalResolution.of(result);
            return resolution.withExtraProblems(problems);
        };
    }

    /**
     * @param externals résolution fixée (tests, repli hors ligne)
     * @return la fonction constante qui répond {@code externals}
     */
    public static Function<Workspace, ExternalResolution> fixed(
            ExternalResolution externals) {
        return workspace -> externals;
    }

    private static Exposure exposureOf(DependencyScope scope) {
        return switch (scope) {
            case API -> Exposure.API;
            case COMPILE_ONLY -> Exposure.COMPILE_ONLY;
            case RUNTIME_ONLY -> Exposure.RUNTIME_ONLY;
            default -> Exposure.IMPLEMENTATION;
        };
    }
}
