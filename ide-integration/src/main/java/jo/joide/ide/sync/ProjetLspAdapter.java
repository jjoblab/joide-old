package jo.joide.ide.sync;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jo.codeide.lsp.model.ConvertisseurModeleLsp;
import jo.codeide.lsp.model.LecteurStatistiquesSysteme;
import jo.codeide.lsp.model.ProjetLsp;
import jo.codeide.lsp.model.protocole.ClasspathEntry;
import jo.codeide.lsp.model.protocole.ClasspathKind;
import jo.codeide.lsp.model.protocole.ClasspathModule;
import jo.codeide.lsp.model.protocole.ClasspathModuleAndroid;
import jo.codeide.lsp.model.protocole.ClasspathResult;
import jo.joide.model.ContentRole;
import jo.joide.model.DependencyRef;
import jo.joide.model.DependencyScope;
import jo.joide.model.Facet;
import jo.joide.model.Module;
import jo.joide.model.ModuleType;
import jo.joide.model.SourceSet;
import jo.joide.model.Workspace;

/**
 * Adaptateur du modèle JoIDE vers le contrat « ProjetLsp » v11 de la
 * brique code-lsp (§6.8 règle 3, ADR-044) : l'instantané est produit
 * par le convertisseur de la brique — les empreintes, l'ordre canonique,
 * la déduplication « plus récent gagne » et le rejet des cycles sont
 * donc cohérents avec le contrat <em>par construction</em>.
 *
 * <p>Règles portées par l'adaptateur :</p>
 * <ul>
 *   <li>identifiants : chemins plats « :a:b » du modèle, tels quels ;</li>
 *   <li>sources par rôle (principal / test / générés), chemins absolus ;</li>
 *   <li>classpath isolé par module (W5) : dépendances frères en entrées
 *       nominales MODULE, dépendances externes résolues en JAR (AAR
 *       normalisé : jar de classes et {@code libs/*.jar} extraits,
 *       provenance conservée) ; les portées d'exécution seule sont
 *       hors de la vue de compilation ;</li>
 *   <li>{@code android.jar} fourni par la plateforme injectée ; un
 *       module Android sans plateforme reste ANDROID (variante) avec
 *       un avertissement ;</li>
 *   <li>module sans répertoire ou de type inconnu : écarté avec sa
 *       raison (jamais en silence) ;</li>
 *   <li>toute dépendance non résolue est un avertissement, jamais une
 *       omission muette.</li>
 * </ul>
 *
 * <p>Pur : aucun réseau, aucun téléchargement — les artefacts viennent
 * de la résolution injectée.</p>
 */
public final class ProjetLspAdapter {

    /** Version du protocole dont la forme est remplie ici (contrat v8). */
    private static final int PROTOCOL_VERSION = 8;

    /** Identifiant de synchronisation JoIDE (déduplication côté hôte). */
    private static final String SYNC_ID = "joide-sync";

    /** Niveau d'API de compilation par défaut (plateforme épinglée P5). */
    private static final int COMPILE_SDK_DEFAUT = 35;

    private final ModuleDirectories directories;
    private final AndroidPlatform platform;

    /**
     * @param directories localisation des répertoires de modules
     * @param platform    fournisseur de la plateforme Android
     */
    public ProjetLspAdapter(ModuleDirectories directories,
            AndroidPlatform platform) {
        this.directories = directories;
        this.platform = platform;
    }

    /**
     * Adapte un workspace en instantané « ProjetLsp ».
     *
     * @param root      racine du projet sur le disque
     * @param workspace modèle du workspace (core-model)
     * @param externals dépendances externes résolues (core-deps)
     * @return l'instantané immuable, empreintes cohérentes avec le
     *         contrat de la brique
     */
    public ProjetLsp adapt(Path root, Workspace workspace,
            ExternalResolution externals) {
        List<ClasspathModule> modules = new ArrayList<>();
        for (Module module : workspace.allModules().values()) {
            modules.add(convert(root, module, workspace, externals));
        }
        ClasspathResult result = new ClasspathResult(SYNC_ID,
                PROTOCOL_VERSION, root.toString(), modules);
        return ConvertisseurModeleLsp.INSTANCE.depuisClasspathResult(
                result, new LecteurStatistiquesSysteme());
    }

    private ClasspathModule convert(Path root, Module module,
            Workspace workspace, ExternalResolution externals) {
        String path = module.path();
        List<String> warnings = new ArrayList<>(externals.problems());
        boolean android = module.type() == ModuleType.ANDROID_LIBRARY
                || module.type() == ModuleType.ANDROID_APPLICATION;

        if (!android && module.type() != ModuleType.JVM_LIBRARY) {
            return ignored(path, module,
                    "unsupported module type: " + module.type().id());
        }
        if (!directories.exists(path)) {
            return ignored(path, module, "module directory not found: "
                    + directories.directory(path));
        }

        Path moduleDir = directories.directory(path);
        Facet facet = module.facets().get("android");

        java.util.Set<String> modulePaths =
                workspace.allModules().keySet();
        List<String> sourceDirs = absoluteRoots(module, "main",
                ContentRole.SOURCE, moduleDir);
        List<String> testDirs = absoluteRoots(module, "test",
                ContentRole.SOURCE, moduleDir);
        List<String> generatedDirs = generatedRoots(module, moduleDir);

        String androidJar = null;
        String variant = null;
        if (android) {
            variant = variantOf(facet);
            int compileSdk = compileSdkOf(facet);
            Path jar = platform.androidJar(compileSdk);
            if (jar == null) {
                warnings.add("android platform " + compileSdk
                        + " not available: compilation for module " + path
                        + " will lack the framework classes");
            } else {
                androidJar = jar.toString();
            }
        }

        List<ClasspathEntry> entries =
                entriesOf(module, moduleDir, externals, modulePaths,
                        warnings);

        return new ClasspathModule(
                path,
                sourceDirs,
                testDirs.isEmpty() ? null : testDirs,
                entries,
                countKind(entries, ClasspathKind.JAR),
                countKind(entries, ClasspathKind.AAR),
                null,
                variant,
                countKind(entries, ClasspathKind.MODULE),
                generatedDirs.isEmpty() ? null : generatedDirs,
                androidJar,
                false,
                null,
                warnings,
                null,
                null,
                androidContract(module, workspace, facet, moduleDir));
    }

    private ClasspathModule ignored(String path, Module module,
            String reason) {
        return new ClasspathModule(path, List.of(), null, List.of(),
                0, 0, null, null, null, null, null, true, reason,
                List.of(), null, null, null);
    }

    private List<ClasspathEntry> entriesOf(Module module, Path moduleDir,
            ExternalResolution externals,
            java.util.Set<String> modulePaths, List<String> warnings) {
        List<ClasspathEntry> entries = new ArrayList<>();
        for (DependencyRef dep : module.dependencies()) {
            if (dep.isModule()) {
                if (!modulePaths.contains(dep.modulePath())) {
                    warnings.add("unknown sibling module: "
                            + dep.modulePath() + " (module " + module.path()
                            + ")");
                    continue;
                }
                entries.add(new ClasspathEntry(dep.modulePath(),
                        ClasspathKind.MODULE, scopeOf(dep.scope()), null,
                        null));
                continue;
            }
            if (dep.scope() == DependencyScope.RUNTIME_ONLY
                    || dep.scope() == DependencyScope.TEST_RUNTIME_ONLY) {
                continue;
            }
            String[] segments = dep.coordinates().split(":");
            String ga = segments[0] + ":" + segments[1];
            String version = segments.length > 2 ? segments[2] : "?";
            Optional<ExternalResolution.Resolved> resolved =
                    externals.find(ga);
            if (resolved.isEmpty()) {
                warnings.add("unresolved dependency: " + dep.coordinates()
                        + " (module " + module.path() + ")");
                continue;
            }
            ExternalResolution.Resolved artifact = resolved.get();
            if (artifact.aar && artifact.aarContents != null) {
                entries.add(new ClasspathEntry(
                        artifact.aarContents.classesJar.toString(),
                        ClasspathKind.JAR, scopeOf(dep.scope()), null,
                        artifact.file.toString()));
                for (Path lib : artifact.aarContents.libs) {
                    entries.add(new ClasspathEntry(lib.toString(),
                            ClasspathKind.JAR, scopeOf(dep.scope()), null,
                            artifact.file.toString()));
                }
            } else if (artifact.aar) {
                entries.add(new ClasspathEntry(artifact.file.toString(),
                        ClasspathKind.AAR, scopeOf(dep.scope()), null,
                        null));
            } else {
                entries.add(new ClasspathEntry(artifact.file.toString(),
                        ClasspathKind.JAR, scopeOf(dep.scope()), null,
                        null));
            }
        }
        return entries;
    }

    private String scopeOf(DependencyScope scope) {
        return scope.isTest() ? "test" : "compile";
    }

    private Integer countKind(List<ClasspathEntry> entries,
            ClasspathKind kind) {
        int count = 0;
        for (ClasspathEntry entry : entries) {
            if (entry.getKind() == kind) {
                count++;
            }
        }
        return count;
    }

    private ClasspathModuleAndroid androidContract(Module module,
            Workspace workspace, Facet facet, Path moduleDir) {
        if (facet == null) {
            return null;
        }
        Integer minSdk = intEntry(facet, "min-sdk").orElseGet(
                () -> workspace.setting("android.min-sdk")
                        .flatMap(ProjetLspAdapter::parseIntSafe)
                        .orElse(null));
        return new ClasspathModuleAndroid(
                facet.entry("namespace"),
                facet.entry("application-id"),
                minSdk,
                intEntry(facet, "target-sdk").orElse(null),
                intEntry(facet, "compile-sdk")
                        .orElse(COMPILE_SDK_DEFAUT),
                absoluteRoots(module, "main", ContentRole.ANDROID_RES,
                        moduleDir),
                facet.entry("manifest") == null ? null
                        : moduleDir.resolve(facet.entry("manifest"))
                                .normalize().toString(),
                boolEntry(facet, "feature-viewBinding"),
                boolEntry(facet, "feature-dataBinding"),
                boolEntry(facet, "feature-buildConfig"),
                boolEntry(facet, "feature-resValues"),
                null,
                null,
                Map.of(),
                Map.of());
    }

    private String variantOf(Facet facet) {
        String buildType = firstOf(facet, "build-types", "debug");
        String flavor = firstOf(facet, "flavors", null);
        if (flavor == null) {
            return buildType;
        }
        return flavor + Character.toUpperCase(buildType.charAt(0))
                + buildType.substring(1);
    }

    private int compileSdkOf(Facet facet) {
        return intEntry(facet, "compile-sdk").orElse(COMPILE_SDK_DEFAUT);
    }

    private String firstOf(Facet facet, String key, String defaut) {
        String value = facet.entry(key);
        if (value == null || value.isBlank()) {
            return defaut;
        }
        return value.split(",")[0].trim();
    }

    private Optional<Integer> intEntry(Facet facet, String key) {
        String value = facet.entry(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return parseIntSafe(value);
    }

    private static Optional<Integer> parseIntSafe(String value) {
        try {
            return Optional.of(Integer.parseInt(
                    value.trim().split("\\.")[0]));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Boolean boolEntry(Facet facet, String key) {
        String value = facet.entry(key);
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private List<String> absoluteRoots(Module module, String sourceSet,
            ContentRole role, Path moduleDir) {
        SourceSet set = module.sourceSets().get(sourceSet);
        List<String> out = new ArrayList<>();
        if (set == null) {
            return out;
        }
        for (var root : set.rootsOf(role)) {
            out.add(moduleDir.resolve(root.path()).normalize().toString());
        }
        return out;
    }

    private List<String> generatedRoots(Module module, Path moduleDir) {
        Map<String, String> seen = new LinkedHashMap<>();
        for (SourceSet set : module.sourceSets().values()) {
            for (var root : set.rootsOf(ContentRole.GENERATED)) {
                seen.putIfAbsent(root.path(), root.path());
            }
        }
        List<String> out = new ArrayList<>();
        for (String root : seen.values()) {
            out.add(moduleDir.resolve(root).normalize().toString());
        }
        return out;
    }
}
