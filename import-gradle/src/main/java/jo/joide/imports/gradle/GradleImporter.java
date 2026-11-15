package jo.joide.imports.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import jo.joide.model.ContentRole;
import jo.joide.model.ContentRoot;
import jo.joide.model.DependencyRef;
import jo.joide.model.DependencyScope;
import jo.joide.model.Facet;
import jo.joide.model.Module;
import jo.joide.model.ModuleType;
import jo.joide.model.Project;
import jo.joide.model.SourceSet;
import jo.joide.model.Workspace;
import jo.joide.imports.gradle.script.ScriptExpr;
import jo.joide.platform.Problem;

/**
 * Import statique d'un projet Gradle : réglages, propriétés, catalogue
 * et fichiers de build deviennent un espace de travail JoIDE, et tout
 * ce qui n'est pas résolu devient une entrée du SyncReport.
 *
 * <p>L'import n'exécute jamais un seul script (ADR-039), ne touche
 * jamais au réseau et ne lève jamais : un projet illisible produit un
 * rapport explicite (ADR-040).</p>
 */
public final class GradleImporter {

    /** Nom du projet implicite qui regroupe les modules importés. */
    static final String MAIN_PROJECT = "main";

    /** Résultat d'un import : l'espace de travail lu et le rapport. */
    public static final class ImportResult {

        private final Workspace workspace;
        private final SyncReport report;

        /**
         * @param workspace espace de travail lu (éventuellement vide)
         * @param report    rapport de synchronisation
         */
        public ImportResult(Workspace workspace, SyncReport report) {
            this.workspace = workspace;
            this.report = report;
        }

        /** @return l'espace de travail lu */
        public Workspace workspace() {
            return workspace;
        }

        /** @return le rapport de synchronisation */
        public SyncReport report() {
            return report;
        }
    }

    private GradleImporter() {
    }

    /**
     * Importe un projet Gradle depuis son répertoire racine.
     *
     * @param projectDir répertoire contenant settings.gradle(.kts)
     * @return l'espace de travail (éventuellement vide) et le rapport
     */
    public static ImportResult importProject(Path projectDir) {
        List<Problem> problems = new ArrayList<>();
        SettingsFiles settingsFiles = findSettingsFiles(projectDir);
        GradleSettings settings = settingsFiles.settings();
        problems.addAll(settingsFiles.problems());
        GradleProperties properties = readProperties(projectDir);
        VersionCatalog catalog = readCatalog(projectDir, problems);
        java.util.function.Function<String, String> catalogVersions =
                name -> catalog.version(name).orElse(null);
        GradleBuildFile rootBuild = readRootBuild(projectDir, problems,
                !settings.includes().isEmpty(), catalogVersions);
        Function<String, String> scope = Interpolation.chain(
                Interpolation.scopeOf(rootBuild.ext()),
                name -> properties.get(name).orElse(null),
                name -> catalog.version(name).orElse(null));
        String workspaceName = settings.rootName()
                .orElse(sanitizeName(projectDir));
        Map<String, List<Problem>> moduleProblems = new LinkedHashMap<>();
        Map<String, Module> modules = new TreeMap<>();
        List<String> includes = settings.includes();
        boolean rootBuildExists = firstExisting(projectDir,
                "build.gradle.kts", "build.gradle") != null;
        if (includes.isEmpty() && rootBuildExists) {
            // le projet racine est le seul module : son répertoire est
            // la racine elle-même (ADR-040)
            problems.add(Problem.info("import-root-as-module",
                    "no included module : the root project itself is"
                            + " imported as a single module"));
            importModuleAt(projectDir, ":" + sanitizeName(projectDir),
                    catalog, scope, modules, moduleProblems);
        } else {
            for (String include : includes) {
                Path moduleDir = projectDir;
                for (String segment : include.substring(1).split(":")) {
                    moduleDir = moduleDir.resolve(segment);
                }
                importModuleAt(moduleDir, include, catalog, scope,
                        modules, moduleProblems);
            }
        }
        Map<String, String> workspaceSettings = new LinkedHashMap<>();
        String minSdk = minimalAndroidSdk(modules);
        if (minSdk != null) {
            workspaceSettings.put("android.min-sdk", minSdk);
        }
        Project project = new Project(MAIN_PROJECT, modules);
        Workspace workspace = new Workspace(workspaceName,
                Map.of(MAIN_PROJECT, project), workspaceSettings);
        SyncReport report = SyncReport.of(problems, moduleProblems);
        return new ImportResult(workspace, report);
    }

    private static void importModuleAt(Path moduleDir, String include,
                                       VersionCatalog catalog,
                                       Function<String, String> scope,
                                       Map<String, Module> modules,
                                       Map<String, List<Problem>> moduleProblems) {
        List<Problem> problems = new ArrayList<>();
        String location = include.substring(1);
        Path buildFile = firstExisting(moduleDir, "build.gradle.kts",
                "build.gradle");
        GradleBuildFile build;
        if (buildFile == null) {
            problems.add(Problem.warn("module-build-file-missing",
                    "no build.gradle(.kts) in " + location
                            + " : empty module"));
            build = GradleBuildFile.empty();
        } else {
            build = GradleBuildFile.read(buildFile, problems,
                    name -> catalog.version(name).orElse(null));
        }
        String relativeFile = location + "/"
                + (buildFile == null ? "(no build file)"
                        : buildFile.getFileName().toString());
        ModuleType type = resolveType(build, catalog, location, problems);
        boolean android = type.id().startsWith("android-");
        boolean kotlin = hasKotlin(build, catalog)
                || (android && agpMajorAtLeast(build, catalog, 9));
        Map<String, SourceSet> sourceSets = resolveSourceSets(build, type,
                kotlin, location, problems);
        List<DependencyRef> dependencies = resolveDependencies(build,
                catalog, scope, location, problems);
        Map<String, Facet> facets = resolveFacets(build, type, kotlin,
                catalog, location, problems);
        Module module = new Module(include, lastSegment(include), type,
                sourceSets, dependencies, facets);
        modules.put(module.path(), module);
        moduleProblems.put(include, relocate(problems, relativeFile));
    }

    /** Avertit quand le build racine s'applique à un projet racine
     * qui n'est pas importable comme module (des modules inclus
     * existent déjà). */
    private static void warnRootModule(GradleBuildFile rootBuild,
                                       boolean hasIncludes,
                                       List<Problem> problems) {
        if (!hasIncludes || rootBuild.plugins().isEmpty()) {
            return;
        }
        boolean rootOnly = rootBuild.plugins().stream()
                .allMatch(GradleBuildFile.PluginDecl::applyFalse);
        if (!rootOnly) {
            problems.add(Problem.warn("root-module-not-mappable",
                    "root build file applies plugins : the root project"
                            + " itself is not mappable to a JoIDE module"
                            + " (JoIDE modules live in included"
                            + " directories); its ext variables still"
                            + " feed the interpolation"));
        }
    }

    private static ModuleType resolveType(GradleBuildFile build,
                                          VersionCatalog catalog,
                                          String location,
                                          List<Problem> problems) {
        boolean application = false;
        boolean library = false;
        boolean android = false;
        boolean kotlinMultiplatform = false;
        boolean jvmApplication = false;
        for (GradleBuildFile.PluginDecl plugin : build.plugins()) {
            if (plugin.applyFalse()) {
                continue;
            }
            String id = plugin.id();
            if (id == null && plugin.alias() != null
                    && plugin.alias().startsWith("libs.plugins.")) {
                id = catalog.plugin(plugin.alias().substring(
                        "libs.plugins.".length()))
                        .map(VersionCatalog.Plugin::id).orElse(null);
            }
            if (id == null) {
                if (plugin.alias() != null) {
                    problems.add(Problem.warn("plugin-alias-unresolved",
                            "plugin alias '" + plugin.alias()
                                    + "' not resolvable in " + location));
                }
                continue;
            }
            switch (id) {
                case "com.android.application" -> {
                    application = true;
                    android = true;
                }
                case "com.android.library", "com.android.dynamic-feature" -> {
                    library = true;
                    android = true;
                }
                case "org.jetbrains.kotlin.multiplatform",
                        "org.jetbrains.kotlin.kmp" -> {
                    kotlinMultiplatform = true;
                }
                case "java", "java-library", "application",
                        "org.jetbrains.kotlin.jvm",
                        "org.gradle.kotlin.kotlin-dsl" -> {
                    if (id.equals("application")) {
                        jvmApplication = true;
                    }
                }
                default -> {
                    // greffons inconnus : ignorés ici, signalés par le
                    // lecteur via les appels non mappés
                }
            }
        }
        if (android && application) {
            return ModuleType.ANDROID_APPLICATION;
        }
        if (android && library) {
            return ModuleType.ANDROID_LIBRARY;
        }
        if (kotlinMultiplatform) {
            problems.add(Problem.warn("module-type-kmp",
                    "Kotlin multiplatform is not buildable by the"
                            + " pipeline : module kept as exotic type in "
                            + location));
            return ModuleType.of("kotlin-multiplatform");
        }
        if (jvmApplication) {
            return ModuleType.of("jvm-application");
        }
        return ModuleType.JVM_LIBRARY;
    }

    /**
     * Vrai si un greffon com.android.* est appliqué avec une version
     * majeure au moins {@code major} (AGP 9 intègre le support
     * Kotlin, ADR-041).
     */
    private static boolean agpMajorAtLeast(GradleBuildFile build,
                                           VersionCatalog catalog,
                                           int major) {
        for (GradleBuildFile.PluginDecl plugin : build.plugins()) {
            if (plugin.applyFalse()) {
                continue;
            }
            String id = plugin.id();
            String version = plugin.version();
            if (id == null && plugin.alias() != null
                    && plugin.alias().startsWith("libs.plugins.")) {
                Optional<VersionCatalog.Plugin> resolved = catalog.plugin(
                        plugin.alias().substring("libs.plugins.".length()));
                id = resolved.map(VersionCatalog.Plugin::id).orElse(null);
                version = version != null ? version
                        : resolved.map(VersionCatalog.Plugin::version)
                                .orElse(null);
            }
            if (version == null || !(id != null
                    && (id.equals("com.android.application")
                            || id.equals("com.android.library")))) {
                continue;
            }
            try {
                int declared = Integer.parseInt(
                        version.split("[.-]")[0]);
                if (declared >= major) {
                    return true;
                }
            } catch (NumberFormatException e) {
                // version illisible : non décisive
            }
        }
        return false;
    }

    private static boolean hasKotlin(GradleBuildFile build,
                                      VersionCatalog catalog) {
        for (GradleBuildFile.PluginDecl plugin : build.plugins()) {
            if (plugin.applyFalse()) {
                continue;
            }
            String id = plugin.id();
            if (id == null && plugin.alias() != null
                    && plugin.alias().startsWith("libs.plugins.")) {
                id = catalog.plugin(plugin.alias().substring(
                        "libs.plugins.".length()))
                        .map(VersionCatalog.Plugin::id).orElse(null);
            }
            if (id != null && (id.equals("org.jetbrains.kotlin.android")
                    || id.equals("org.jetbrains.kotlin.jvm"))) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, SourceSet> resolveSourceSets(
            GradleBuildFile build, ModuleType type, boolean kotlin,
            String location, List<Problem> problems) {
        Map<String, SourceSet> sourceSets = new LinkedHashMap<>();
        boolean android = type == ModuleType.ANDROID_APPLICATION
                || type == ModuleType.ANDROID_LIBRARY;
        Map<String, Map<String, List<String>>> declared =
                new LinkedHashMap<>();
        for (GradleBuildFile.SourceSetDecl set : build.sourceSets()) {
            Map<String, List<String>> containers =
                    declared.computeIfAbsent(set.name(),
                            k -> new LinkedHashMap<>());
            for (GradleBuildFile.SrcDirsOp op : set.ops()) {
                String role = roleOfContainer(op.container(), problems,
                        location);
                if (role == null) {
                    continue;
                }
                List<String> current = containers.get(role);
                if (current == null || op.replace()) {
                    containers.put(role, new ArrayList<>(op.dirs()));
                } else {
                    current.addAll(op.dirs());
                }
            }
        }
        for (String setName : List.of("main", "test")) {
            Map<String, List<String>> defaults = defaultRoots(setName,
                    android, kotlin);
            Map<String, List<String>> overrides = declared.get(setName);
            List<ContentRoot> roots = new ArrayList<>();
            Map<String, List<String>> effective = new LinkedHashMap<>();
            if (overrides != null && !overrides.isEmpty()) {
                for (Map.Entry<String, List<String>> def
                        : defaults.entrySet()) {
                    effective.put(def.getKey(), new ArrayList<>(
                            def.getValue()));
                }
                effective.putAll(overrides);
            } else {
                effective.putAll(defaults);
            }
            for (Map.Entry<String, List<String>> entry
                    : effective.entrySet()) {
                ContentRole role = roleById(entry.getKey());
                for (String dir : entry.getValue()) {
                    try {
                        roots.add(new ContentRoot(dir, role));
                    } catch (IllegalArgumentException e) {
                        problems.add(Problem.warn("source-path-invalid",
                                "source path invalid in " + location
                                        + " : " + dir));
                    }
                }
            }
            if (!roots.isEmpty()) {
                try {
                    sourceSets.put(setName, new SourceSet(setName, roots));
                } catch (IllegalArgumentException e) {
                    problems.add(Problem.warn("source-set-invalid",
                            "source set invalid in " + location + " : "
                                    + setName));
                }
            }
        }
        return sourceSets;
    }

    private static Map<String, List<String>> defaultRoots(String setName,
                                                          boolean android,
                                                          boolean kotlin) {
        Map<String, List<String>> roots = new LinkedHashMap<>();
        List<String> sources = new ArrayList<>();
        sources.add("src/" + setName + "/java");
        if (kotlin) {
            sources.add("src/" + setName + "/kotlin");
        }
        roots.put("source", sources);
        if (android) {
            roots.put("resources", List.of("src/" + setName + "/resources"));
            roots.put("android-res", List.of("src/" + setName + "/res"));
            roots.put("assets", List.of("src/" + setName + "/assets"));
            roots.put("aidl", List.of("src/" + setName + "/aidl"));
            roots.put("jniLibs", List.of("src/" + setName + "/jniLibs"));
        } else {
            roots.put("resources", List.of("src/" + setName + "/resources"));
        }
        return roots;
    }

    private static String roleOfContainer(String container,
                                          List<Problem> problems,
                                          String location) {
        return switch (container) {
            case "java", "kotlin", "groovy" -> "source";
            case "resources" -> "resources";
            case "res" -> "android-res";
            case "assets" -> "assets";
            case "aidl" -> "aidl";
            case "jniLibs" -> "jniLibs";
            case "manifest" -> null;
            default -> {
                problems.add(Problem.info("sourceset-container-unknown",
                        "source set container '" + container
                                + "' not mapped in " + location));
                yield null;
            }
        };
    }

    private static ContentRole roleById(String id) {
        return switch (id) {
            case "source" -> ContentRole.SOURCE;
            case "resources" -> ContentRole.RESOURCES;
            case "android-res" -> ContentRole.ANDROID_RES;
            case "assets" -> ContentRole.ASSETS;
            case "aidl" -> ContentRole.AIDL;
            case "jniLibs" -> ContentRole.JNI_LIBS;
            case "generated" -> ContentRole.GENERATED;
            case "excluded" -> ContentRole.EXCLUDED;
            default -> ContentRole.of(id);
        };
    }

    private static List<DependencyRef> resolveDependencies(
            GradleBuildFile build, VersionCatalog catalog,
            Function<String, String> scope, String location,
            List<Problem> problems) {
        List<DependencyRef> dependencies = new ArrayList<>();
        Set<String> flavors = build.android()
                .map(info -> info.flavors().keySet())
                .orElse(Set.of());
        for (GradleBuildFile.DependencyDecl decl
                : build.dependencies()) {
            if ("coreLibraryDesugaring".equals(decl.configuration())) {
                recordDesugaring(decl, scope, problems, location);
                continue;
            }
            if (decl.configuration().startsWith("testFixtures")) {
                problems.add(Problem.warn("dependency-test-fixtures",
                        "testFixtures() dependency not mapped in "
                                + location + " (line " + decl.line() + ")"));
                continue;
            }
            if ("constraints".equals(decl.configuration())
                    || "rootProject".equals(decl.configuration())) {
                continue;
            }
            ScopeMapping mapping = ScopeMapping.parse(
                    decl.configuration(), flavors);
            if (mapping == null) {
                problems.add(Problem.warn("dependency-scope-unknown",
                        "configuration '" + decl.configuration()
                                + "' not mapped in " + location
                                + " (line " + decl.line() + ")"));
                continue;
            }
            if (decl.hasBlock()) {
                problems.add(Problem.info("dependency-block-skipped",
                        "configuration block of '"
                                + decl.configuration() + "' not mapped"
                                + " in " + location + " (line "
                                + decl.line() + ")"));
            }
            Optional<DependencyRef> dependency = resolveNotation(decl,
                    mapping, catalog, scope, problems, location);
            dependency.ifPresent(dependencies::add);
        }
        return dependencies;
    }

    private static void recordDesugaring(GradleBuildFile.DependencyDecl decl,
                                         Function<String, String> scope,
                                         List<Problem> problems,
                                         String location) {
        String coordinates = literalCoordinates(decl.notation(), scope);
        if (coordinates == null) {
            problems.add(Problem.warn("desugaring-not-literal",
                    "coreLibraryDesugaring notation not a literal in "
                            + location + " (line " + decl.line() + ")"));
        }
    }

    private static Optional<DependencyRef> resolveNotation(
            GradleBuildFile.DependencyDecl decl, ScopeMapping mapping,
            VersionCatalog catalog, Function<String, String> scope,
            List<Problem> problems, String location) {
        ScriptExpr notation = decl.notation();
        if (notation == null) {
            problems.add(Problem.warn("dependency-without-notation",
                    "dependency without notation in " + location
                            + " (line " + decl.line() + ")"));
            return Optional.empty();
        }
        if (notation instanceof ScriptExpr.MapExpr map) {
            return fromMapNotation(map, mapping, problems, location,
                    decl.line());
        }
        if (notation instanceof ScriptExpr.CallExpr call) {
            return fromCallNotation(decl, call, mapping, catalog, scope,
                    problems, location);
        }
        if (notation instanceof ScriptExpr.Reference reference
                && reference.chain().get(0).equals("libs")) {
            return fromCatalog(decl, reference, mapping, catalog,
                    problems, location);
        }
        if (notation instanceof ScriptExpr.Reference reference
                && reference.chain().size() == 1) {
            problems.add(Problem.warn("dependency-bare-identifier",
                    "bare identifier '" + reference.chain().get(0)
                            + "' not mapped in " + location + " (line "
                            + decl.line() + ")"));
            return Optional.empty();
        }
        String coordinates = literalCoordinates(notation, scope);
        if (coordinates != null) {
            return fromCoordinates(coordinates, mapping, problems,
                    location, decl.line());
        }
        problems.add(Problem.warn("dependency-notation-unknown",
                "notation not mapped in " + location + " (line "
                        + decl.line() + ")"));
        return Optional.empty();
    }

    private static Optional<DependencyRef> fromMapNotation(
            ScriptExpr.MapExpr map, ScopeMapping mapping,
            List<Problem> problems, String location, int line) {
        String group = null;
        String name = null;
        String version = null;
        for (ScriptExpr.MapExpr.Entry entry : map.entries()) {
            String key = ScriptStmts.stringValue(entry.key());
            String value = ScriptStmts.stringValue(entry.value());
            if (key == null || value == null) {
                continue;
            }
            switch (key) {
                case "group" -> group = value;
                case "name" -> name = value;
                case "version" -> version = value;
                default -> {
                }
            }
        }
        if (group != null && name != null && version != null) {
            return fromCoordinates(group + ":" + name + ":" + version,
                    mapping, problems, location, line);
        }
        problems.add(Problem.warn("dependency-map-incomplete",
                "map notation without group/name/version in "
                        + location + " (line " + line + ")"));
        return Optional.empty();
    }

    private static Optional<DependencyRef> fromCallNotation(
            GradleBuildFile.DependencyDecl decl, ScriptExpr.CallExpr call,
            ScopeMapping mapping, VersionCatalog catalog,
            Function<String, String> scope, List<Problem> problems,
            String location) {
        if ("project".equals(call.name())) {
            String path = ScriptStmts.stringValue(call.args().isEmpty()
                    ? null : call.args().get(0));
            if (path == null) {
                problems.add(Problem.warn("dependency-project-arg",
                        "project() without literal path in " + location
                                + " (line " + decl.line() + ")"));
                return Optional.empty();
            }
            String normalized = path.startsWith(":") ? path
                    : ":" + path;
            try {
                return Optional.of(DependencyRef.toModule(normalized,
                        mapping.scope(), mapping.variants()));
            } catch (IllegalArgumentException e) {
                problems.add(Problem.warn("dependency-project-invalid",
                        "project path invalid in " + location + " : "
                                + path));
                return Optional.empty();
            }
        }
        if ("platform".equals(call.name())
                || "enforcePlatform".equals(call.name())) {
            problems.add(Problem.warn("dependency-platform",
                    "platform()/BOM import not mapped in " + location
                            + " (line " + decl.line() + ")"));
            return Optional.empty();
        }
        if ("files".equals(call.name()) || "fileTree".equals(call.name())
                || "layout".equals(call.receiver().isEmpty() ? ""
                        : call.receiver().get(0))) {
            problems.add(Problem.warn("dependency-local-files",
                    "local file dependency not mapped in " + location
                            + " (line " + decl.line() + ")"));
            return Optional.empty();
        }
        if ("create".equals(call.name())) {
            String coordinates = call.args().isEmpty() ? null
                    : ScriptStmts.stringValue(call.args().get(0));
            if (coordinates != null) {
                return fromCoordinates(
                        Interpolation.resolve(coordinates, scope,
                                problems, location),
                        mapping, problems, location, decl.line());
            }
        }
        problems.add(Problem.warn("dependency-call-unknown",
                "call '" + call.name() + "' not mapped in " + location
                        + " (line " + decl.line() + ")"));
        return Optional.empty();
    }

    private static Optional<DependencyRef> fromCatalog(
            GradleBuildFile.DependencyDecl decl,
            ScriptExpr.Reference reference, ScopeMapping mapping,
            VersionCatalog catalog, List<Problem> problems,
            String location) {
        String alias = String.join(".",
                reference.chain().subList(1, reference.chain().size()));
        Optional<VersionCatalog.Library> library =
                catalog.library(alias);
        if (library.isEmpty()) {
            problems.add(Problem.warn("dependency-catalog-alias-unknown",
                    "catalog alias 'libs." + alias + "' not found in "
                            + location + " (line " + decl.line() + ")"));
            return Optional.empty();
        }
        String coordinates = library.get().coordinates();
        if (coordinates == null) {
            problems.add(Problem.warn(
                    "dependency-catalog-unversioned",
                    "catalog alias 'libs." + alias + "' has no version"
                            + " in " + location + " (line "
                            + decl.line() + ")"));
            return Optional.empty();
        }
        return fromCoordinates(coordinates, mapping, problems, location,
                decl.line());
    }

    private static Optional<DependencyRef> fromCoordinates(
            String coordinates, ScopeMapping mapping,
            List<Problem> problems, String location, int line) {
        String[] parts = coordinates.split(":");
        if (parts.length == 2) {
            problems.add(Problem.warn("dependency-unversioned",
                    "coordinates without version not mapped : "
                            + coordinates + " in " + location + " (line "
                            + line + ")"));
            return Optional.empty();
        }
        if (parts.length > 3) {
            problems.add(Problem.warn("dependency-classifier",
                    "classifier not carried by the model : "
                            + coordinates + " in " + location + " (line "
                            + line + ")"));
            return Optional.empty();
        }
        if (parts.length != 3 || coordinates.contains("$")) {
            problems.add(Problem.warn("dependency-unresolved",
                    "unresolved coordinates : " + coordinates + " in "
                            + location + " (line " + line + ")"));
            return Optional.empty();
        }
        try {
            return Optional.of(DependencyRef.external(coordinates,
                    mapping.scope(), mapping.variants()));
        } catch (IllegalArgumentException e) {
            problems.add(Problem.warn("dependency-invalid",
                    "invalid coordinates : " + coordinates + " in "
                            + location + " (line " + line + ")"));
            return Optional.empty();
        }
    }

    private static String literalCoordinates(ScriptExpr notation,
                                             Function<String, String> scope) {
        if (!(notation instanceof ScriptExpr.Literal literal)) {
            return null;
        }
        String decoded = literal.decoded();
        return decoded.contains("$")
                ? Interpolation.resolve(decoded, scope, List.of(), "")
                : decoded;
    }

    private static Map<String, Facet> resolveFacets(
            GradleBuildFile build, ModuleType type, boolean kotlin,
            VersionCatalog catalog, String location,
            List<Problem> problems) {
        Map<String, Facet> facets = new LinkedHashMap<>();
        if (kotlin) {
            facets.put("kotlin", new Facet("kotlin", Map.of()));
        }
        Map<String, String> pluginVersions = new LinkedHashMap<>();
        for (GradleBuildFile.PluginDecl plugin : build.plugins()) {
            if (plugin.applyFalse()) {
                continue;
            }
            String id = plugin.id();
            String version = plugin.version();
            if (id == null && plugin.alias() != null
                    && plugin.alias().startsWith("libs.plugins.")) {
                Optional<VersionCatalog.Plugin> resolved = catalog.plugin(
                        plugin.alias().substring("libs.plugins.".length()));
                id = resolved.map(VersionCatalog.Plugin::id).orElse(null);
                version = version != null ? version
                        : resolved.map(VersionCatalog.Plugin::version)
                                .orElse(null);
            }
            if (id != null && version != null) {
                pluginVersions.put("plugin-" + id, version);
            }
        }
        if (!pluginVersions.isEmpty()) {
            facets.put("gradle", new Facet("gradle", pluginVersions));
        }
        boolean android = type == ModuleType.ANDROID_APPLICATION
                || type == ModuleType.ANDROID_LIBRARY;
        if (android) {
            Map<String, String> entries = new LinkedHashMap<>();
            GradleBuildFile.AndroidInfo info = build.android()
                    .orElse(null);
            if (info == null) {
                problems.add(Problem.error("android-block-missing",
                        "android module without android block in "
                                + location));
            } else {
                String manifest = manifestOverride(build);
                putIfPresent(entries, "manifest",
                        manifest != null ? manifest
                                : "src/main/AndroidManifest.xml");
                putIfPresent(entries, "namespace", info.namespace());
                putIfPresent(entries, "compile-sdk", info.compileSdk());
                putIfPresent(entries, "application-id",
                        info.applicationId());
                putIfPresent(entries, "min-sdk", info.minSdk());
                putIfPresent(entries, "target-sdk", info.targetSdk());
                putIfPresent(entries, "version-code", info.versionCode());
                putIfPresent(entries, "version-name", info.versionName());
                if (!info.buildTypes().isEmpty()) {
                    entries.put("build-types", String.join(",",
                            info.buildTypes().keySet()));
                }
                for (Map.Entry<String, Map<String, String>> buildType
                        : info.buildTypes().entrySet()) {
                    String minify = buildType.getValue().get(
                            "minify-enabled");
                    if ("true".equals(minify)) {
                        entries.put("minify-" + buildType.getKey(),
                                "true");
                    }
                }
                if (!info.flavors().isEmpty()) {
                    entries.put("flavors", String.join(",",
                            info.flavors().keySet()));
                    for (Map.Entry<String, Map<String, String>> flavor
                            : info.flavors().entrySet()) {
                        String dimension = flavor.getValue().get(
                                "dimension");
                        if (dimension != null) {
                            entries.put("dimension-" + flavor.getKey(),
                                    dimension);
                        }
                    }
                }
                if (info.flavorDimensions() != null) {
                    entries.put("flavor-dimensions",
                            info.flavorDimensions());
                }
                String compose = info.buildFeatures().get("compose");
                if ("true".equals(compose)) {
                    entries.put("compose", "true");
                    if (!pluginVersions.containsKey(
                            "plugin-org.jetbrains.kotlin.plugin.compose")) {
                        problems.add(Problem.warn(
                                "compose-compiler-plugin-missing",
                                "compose is enabled without the Compose"
                                        + " Compiler Gradle plugin (required"
                                        + " with Kotlin 2.0 and later) in "
                                        + location));
                    }
                }
                for (Map.Entry<String, String> feature
                        : info.buildFeatures().entrySet()) {
                    if (!feature.getKey().equals("compose")) {
                        entries.put("feature-" + feature.getKey(),
                                feature.getValue());
                    }
                }
                for (String desugar : desugaringCoordinates(build)) {
                    entries.put("desugar-libraries", desugar);
                }
            }
            facets.put("android", new Facet("android", entries));
        }
        for (String unmapped : build.unmapped()) {
            problems.add(Problem.info("build-toplevel-skipped",
                    "top level statement not mapped in " + location
                            + " : " + unmapped));
        }
        return facets;
    }

    private static List<String> desugaringCoordinates(
            GradleBuildFile build) {
        List<String> coordinates = new ArrayList<>();
        for (GradleBuildFile.DependencyDecl decl
                : build.dependencies()) {
            if ("coreLibraryDesugaring".equals(decl.configuration())
                    && decl.notation()
                            instanceof ScriptExpr.Literal literal) {
                coordinates.add(literal.decoded());
            }
        }
        return coordinates;
    }

    /** Manifeste personnalisé déclaré dans les ensembles de sources. */
    private static String manifestOverride(GradleBuildFile build) {
        for (GradleBuildFile.SourceSetDecl set : build.sourceSets()) {
            if (!"main".equals(set.name())) {
                continue;
            }
            for (GradleBuildFile.SrcDirsOp op : set.ops()) {
                if ("manifest".equals(op.container())
                        && !op.dirs().isEmpty()) {
                    return op.dirs().get(0);
                }
            }
        }
        return null;
    }

    private static void putIfPresent(Map<String, String> entries,
                                     String key, String value) {
        if (value != null && !value.isEmpty()) {
            entries.put(key, value);
        }
    }

    private static String minimalAndroidSdk(Map<String, Module> modules) {
        String minimal = null;
        for (Module module : modules.values()) {
            Facet facet = module.facets().get("android");
            if (facet == null) {
                continue;
            }
            String minSdk = facet.entry("min-sdk");
            if (minSdk == null) {
                continue;
            }
            if (minimal == null
                    || Integer.compareUnsigned(
                            Integer.parseInt(minSdk),
                            Integer.parseInt(minimal)) < 0) {
                minimal = minSdk;
            }
        }
        return minimal;
    }

    private static List<Problem> relocate(List<Problem> problems,
                                          String relativeFile) {
        List<Problem> relocated = new ArrayList<>(problems.size());
        for (Problem problem : problems) {
            relocated.add(problem.location() == null
                    ? problem.at(relativeFile) : problem);
        }
        return relocated;
    }

    private static String lastSegment(String include) {
        int last = include.lastIndexOf(':');
        return last < 0 ? include.substring(1)
                : include.substring(last + 1);
    }

    private static String sanitizeName(Path projectDir) {
        String name = projectDir.getFileName() == null
                ? "projet" : projectDir.getFileName().toString();
        String sanitized = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("^-+|-+$", "");
        if (sanitized.isEmpty() || !sanitized.matches(
                "[a-z][a-z0-9-]{0,30}")) {
            sanitized = "projet-" + sanitized;
        }
        return sanitized;
    }

    private static SettingsFiles findSettingsFiles(Path projectDir) {
        List<Problem> problems = new ArrayList<>();
        for (String candidate : List.of("settings.gradle.kts",
                "settings.gradle")) {
            Path file = projectDir.resolve(candidate);
            if (Files.isRegularFile(file)) {
                return new SettingsFiles(
                        GradleSettings.read(file, problems), problems);
            }
        }
        problems.add(Problem.warn("settings-file-missing",
                "no settings.gradle(.kts) in " + projectDir));
        return new SettingsFiles(
                GradleSettings.empty(), problems);
    }

    private static GradleProperties readProperties(Path projectDir) {
        Path file = projectDir.resolve("gradle.properties");
        if (!Files.isRegularFile(file)) {
            return GradleProperties.parse("");
        }
        try {
            return GradleProperties.parse(Files.readString(file,
                    StandardCharsets.UTF_8));
        } catch (IOException e) {
            return GradleProperties.parse("");
        }
    }

    private static VersionCatalog readCatalog(Path projectDir,
                                              List<Problem> problems) {
        Path file = projectDir.resolve("gradle")
                .resolve("libs.versions.toml");
        if (!Files.isRegularFile(file)) {
            return VersionCatalog.empty();
        }
        try {
            return VersionCatalog.parse(Files.readString(file,
                    StandardCharsets.UTF_8), problems,
                    "gradle/libs.versions.toml");
        } catch (IOException e) {
            problems.add(Problem.error("catalog-unreadable",
                    "gradle/libs.versions.toml : " + e.getMessage()));
            return VersionCatalog.empty();
        }
    }

    private static GradleBuildFile readRootBuild(Path projectDir,
                                                 List<Problem> problems,
                                                 boolean hasIncludes,
                                                 java.util.function.Function<String, String> versions) {
        Path file = firstExisting(projectDir, "build.gradle.kts",
                "build.gradle");
        if (file == null) {
            return GradleBuildFile.empty();
        }
        List<Problem> readProblems = new ArrayList<>();
        GradleBuildFile build = GradleBuildFile.read(file, readProblems,
                versions);
        problems.addAll(readProblems);
        warnRootModule(build, hasIncludes, problems);
        if (hasIncludes) {
            // quand la racine n'est pas un module, ses instructions non
            // mappées sont rapportées au niveau global ; sinon le module
            // racine les rapportera lui-même
            for (String unmapped : build.unmapped()) {
                problems.add(Problem.info("root-toplevel-skipped",
                        "root statement not mapped : " + unmapped));
            }
        }
        return build;
    }

    private static Path firstExisting(Path dir, String... names) {
        for (String name : names) {
            Path candidate = dir.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** Fichiers de réglages trouvés, et leurs problèmes. */
    private static final class SettingsFiles {

        private final GradleSettings settings;
        private final List<Problem> problems;

        SettingsFiles(GradleSettings settings, List<Problem> problems) {
            this.settings = settings;
            this.problems = problems;
        }

        GradleSettings settings() {
            return settings;
        }

        List<Problem> problems() {
            return problems;
        }
    }

    /** Portée et variantes d'une configuration de dépendance Gradle. */
    static final class ScopeMapping {

        private final DependencyScope scope;
        private final Set<String> variants;

        private ScopeMapping(DependencyScope scope, Set<String> variants) {
            this.scope = scope;
            this.variants = variants;
        }

        DependencyScope scope() {
            return scope;
        }

        Set<String> variants() {
            return variants;
        }

        /**
         * Analyse un nom de configuration : portées standard, portées
         * de test, préfixes debug/release et parfums connus.
         *
         * @param configuration nom de la configuration
         * @param flavors       parfums déclarés du module
         * @return la correspondance, ou {@code null} si inconnue
         */
        static ScopeMapping parse(String configuration,
                                  Set<String> flavors) {
            Set<String> variants = new LinkedHashSet<>();
            String rest = configuration;
            boolean test = false;
            if (rest.startsWith("test")) {
                test = true;
                rest = rest.substring(4);
            }
            // ordre AGP : parfum avant type de build (freeDebug…)
            for (String flavor : flavors) {
                String stripped = stripPrefix(rest, flavor);
                if (stripped != null) {
                    variants.add(flavor);
                    rest = stripped;
                    break;
                }
            }
            for (String variant : List.of("debug", "release")) {
                String stripped = stripPrefix(rest, variant);
                if (stripped != null) {
                    variants.add(variant);
                    rest = stripped;
                    break;
                }
            }
            String scopeName = (test ? "test_" : "")
                    + camelToKebab(rest).replace('-', '_');
            DependencyScope scope;
            try {
                scope = DependencyScope.parse(scopeName);
            } catch (IllegalArgumentException e) {
                return null;
            }
            return new ScopeMapping(scope, variants);
        }

        /** Retire un préfixe insensible à la casse, en exigeant une
         * suite en majuscule (« testDebug… » → « Debug… »). */
        private static String stripPrefix(String rest, String prefix) {
            String capitalized = Character.toUpperCase(prefix.charAt(0))
                    + prefix.substring(1);
            if (rest.startsWith(prefix)) {
                return rest.substring(prefix.length());
            }
            if (rest.startsWith(capitalized)) {
                return rest.substring(capitalized.length());
            }
            return null;
        }

        /** « compileOnly » devient « compile-only ». */
        private static String camelToKebab(String name) {
            StringBuilder out = new StringBuilder(name.length() + 4);
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (Character.isUpperCase(c)) {
                    if (i > 0) {
                        out.append('-');
                    }
                    out.append(Character.toLowerCase(c));
                } else {
                    out.append(c);
                }
            }
            return out.toString();
        }
    }

    /** Lectures d'expressions utiles au module (valeur textuelle). */
    static final class ScriptStmts {

        private ScriptStmts() {
        }

        /**
         * @param expr expression éventuellement nulle
         * @return la valeur textuelle si l'expression est une littérale
         */
        static String stringValue(ScriptExpr expr) {
            return expr instanceof ScriptExpr.Literal literal
                    ? literal.decoded() : null;
        }
    }
}
