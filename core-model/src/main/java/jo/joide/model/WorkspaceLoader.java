package jo.joide.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import jo.joide.model.toml.Toml;
import jo.joide.model.toml.TomlDocument;
import jo.joide.model.toml.TomlProblem;
import jo.joide.model.toml.TomlValue;
import jo.joide.platform.Problem;

/**
 * Chargement tolérant d'un espace de travail : le fichier racine
 * définit les modules, chaque module est chargé indépendamment — un
 * fichier illisible est signalé, le reste charge.
 *
 * <p>Aucun fichier n'est jamais réécrit à la lecture : un module
 * corrompu est rapporté et ignoré, jamais réparé en silence.</p>
 */
public final class WorkspaceLoader {

    /** Schéma attendu du fichier d'espace de travail. */
    public static final String WORKSPACE_SCHEMA = "joide.workspace/1";

    /** Schéma attendu des fichiers de module. */
    public static final String MODULE_SCHEMA = "joide.module/1";

    /** Nom du fichier d'espace de travail à la racine. */
    public static final String WORKSPACE_FILE = "joide.toml";

    /** Nom du fichier de module dans chaque répertoire inclus. */
    public static final String MODULE_FILE = "joide-module.toml";

    /** Projet implicite qui regroupe les modules déclarés à plat. */
    static final String MAIN_PROJECT = "main";

    /**
     * Charge un espace de travail depuis un répertoire racine.
     *
     * @param rootDir répertoire contenant joide.toml
     * @return le résultat du chargement, jamais d'exception de lecture
     */
    public static WorkspaceLoad load(Path rootDir) {
        List<Problem> problems = new ArrayList<>();
        Path workspaceFile = rootDir.resolve(WORKSPACE_FILE);
        if (!Files.isRegularFile(workspaceFile)) {
            problems.add(Problem.error("workspace-missing",
                    "fichier " + WORKSPACE_FILE + " absent").at(rootDir.toString()));
            return new WorkspaceLoad(null, problems);
        }
        TomlDocument document;
        try {
            document = Toml.parse(Files.readString(workspaceFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
            problems.add(Problem.error("workspace-unreadable",
                    WORKSPACE_FILE + " illisible : " + e.getMessage()));
            return new WorkspaceLoad(null, problems);
        }
        collectParseProblems(document, WORKSPACE_FILE, problems);
        Optional<String> schema = document.findString("schema");
        if (schema.isEmpty()) {
            problems.add(Problem.error("workspace-schema-missing",
                    "champ schema absent de " + WORKSPACE_FILE));
            return new WorkspaceLoad(null, problems);
        }
        if (!WORKSPACE_SCHEMA.equals(schema.get())) {
            problems.add(Problem.error("workspace-schema-unknown",
                    "schema non reconnu : " + schema.get()));
            return new WorkspaceLoad(null, problems);
        }
        Optional<String> name = document.findString("name");
        if (name.isEmpty()) {
            problems.add(Problem.error("workspace-name-missing",
                    "champ name absent de " + WORKSPACE_FILE));
            return new WorkspaceLoad(null, problems);
        }
        Map<String, String> settings = new LinkedHashMap<>();
        document.findInteger("android", "min-sdk")
                .ifPresent(v -> settings.put("android.min-sdk", Long.toString(v)));
        List<String> includes = document.findStringArray("modules", "include");
        if (includes.isEmpty()) {
            problems.add(Problem.warn("workspace-empty",
                    "aucun module declare dans " + WORKSPACE_FILE));
        }
        Map<String, Module> modules = new TreeMap<>();
        for (String include : includes) {
            if (!include.matches("[a-z][a-z0-9-]{0,40}")) {
                problems.add(Problem.error("module-name-invalid",
                        "nom de module invalide : " + include));
                continue;
            }
            loadModule(rootDir, include, modules, problems);
        }
        Project project = new Project(MAIN_PROJECT, modules);
        Workspace workspace =
                new Workspace(name.get(), Map.of(MAIN_PROJECT, project), settings);
        reportUnknownWorkspaceKeys(document, problems);
        return new WorkspaceLoad(workspace, problems);
    }

    /** Charge un module : tout échec est signalé, le module est sauté. */
    private static void loadModule(Path rootDir, String include,
                                   Map<String, Module> modules,
                                   List<Problem> problems) {
        String location = include + "/" + MODULE_FILE;
        Path moduleFile = rootDir.resolve(include).resolve(MODULE_FILE);
        if (!Files.isRegularFile(moduleFile)) {
            problems.add(Problem.error("module-file-missing",
                    "fichier " + location + " absent"));
            return;
        }
        TomlDocument document;
        try {
            document = Toml.parse(Files.readString(moduleFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
            problems.add(Problem.error("module-unreadable",
                    location + " illisible : " + e.getMessage()));
            return;
        }
        collectParseProblems(document, location, problems);
        Optional<String> schema = document.findString("schema");
        if (schema.isEmpty() || !MODULE_SCHEMA.equals(schema.get())) {
            problems.add(Problem.error("module-schema-unknown",
                    "schema absent ou non reconnu dans " + location));
            return;
        }
        Optional<String> name = document.findString("name");
        Optional<String> type = document.findString("type");
        if (name.isEmpty() || type.isEmpty()) {
            problems.add(Problem.error("module-incomplete",
                    "champs name et type obligatoires dans " + location));
            return;
        }
        ModuleType moduleType;
        try {
            moduleType = ModuleType.of(type.get());
        } catch (IllegalArgumentException e) {
            problems.add(Problem.error("module-type-invalid",
                    "type de module invalide dans " + location + " : " + type.get()));
            return;
        }
        Map<String, SourceSet> sourceSets = readSourceSets(document, location, problems);
        List<DependencyRef> dependencies =
                readDependencies(document, location, problems);
        Map<String, Facet> facets = readFacets(document, problems);
        Module module = new Module(":" + include, name.get(), moduleType,
                sourceSets, dependencies, facets);
        modules.put(module.path(), module);
        reportUnknownModuleKeys(document, location, problems);
    }

    /** Ensembles de sources : [[sources]] avec nom et rôles → chemins. */
    private static Map<String, SourceSet> readSourceSets(TomlDocument document,
                                                         String location,
                                                         List<Problem> problems) {
        Map<String, SourceSet> sourceSets = new LinkedHashMap<>();
        TomlValue series = document.find("sources").orElse(null);
        if (series == null) {
            return sourceSets;
        }
        List<TomlValue> entries = series.asArray();
        if (entries == null) {
            problems.add(Problem.error("sources-malformed",
                    "sources doit etre une serie de tables dans " + location));
            return sourceSets;
        }
        for (TomlValue entry : entries) {
            Map<String, TomlValue> table = entry.asTable();
            if (table == null) {
                problems.add(Problem.error("sources-entry-malformed",
                        "element de sources invalide dans " + location));
                continue;
            }
            TomlValue nameValue = table.get("name");
            String setName = nameValue == null ? null : nameValue.asString();
            if (setName == null) {
                problems.add(Problem.error("sources-name-missing",
                        "ensemble de sources sans nom dans " + location));
                continue;
            }
            List<ContentRoot> roots = new ArrayList<>();
            for (Map.Entry<String, TomlValue> key : table.entrySet()) {
                if (key.getKey().equals("name")) {
                    continue;
                }
                ContentRole role;
                try {
                    role = ContentRole.of(key.getKey());
                } catch (IllegalArgumentException e) {
                    problems.add(Problem.info("sources-role-unknown",
                            "role de contenu inconnu dans " + location + " : "
                                    + key.getKey()));
                    continue;
                }
                List<TomlValue> paths = key.getValue().asArray();
                if (paths == null) {
                    problems.add(Problem.error("sources-paths-malformed",
                            "les chemins du role " + key.getKey()
                                    + " doivent etre un tableau dans " + location));
                    continue;
                }
                for (TomlValue path : paths) {
                    String text = path.asString();
                    if (text == null) {
                        problems.add(Problem.error("sources-path-malformed",
                                "chemin non textuel dans " + location));
                        continue;
                    }
                    try {
                        roots.add(new ContentRoot(text, role));
                    } catch (IllegalArgumentException e) {
                        problems.add(Problem.error("sources-path-invalid",
                                "chemin invalide dans " + location + " : " + text));
                    }
                }
            }
            try {
                sourceSets.put(setName, new SourceSet(setName, roots));
            } catch (IllegalArgumentException e) {
                problems.add(Problem.error("sources-name-invalid",
                        "nom d'ensemble invalide dans " + location + " : " + setName));
            }
        }
        return sourceSets;
    }

    /** Dépendances : [[dependencies]] avec path ou coordinates, scope, variants. */
    private static List<DependencyRef> readDependencies(TomlDocument document,
                                                        String location,
                                                        List<Problem> problems) {
        List<DependencyRef> dependencies = new ArrayList<>();
        TomlValue series = document.find("dependencies").orElse(null);
        if (series == null) {
            return dependencies;
        }
        List<TomlValue> entries = series.asArray();
        if (entries == null) {
            problems.add(Problem.error("dependencies-malformed",
                    "dependencies doit etre une serie de tables dans " + location));
            return dependencies;
        }
        for (TomlValue entry : entries) {
            Map<String, TomlValue> table = entry.asTable();
            if (table == null) {
                problems.add(Problem.error("dependency-malformed",
                        "dependance invalide dans " + location));
                continue;
            }
            String path = stringValue(table.get("path"));
            String coordinates = stringValue(table.get("coordinates"));
            String scopeText = stringValue(table.get("scope"));
            if (scopeText == null) {
                scopeText = "implementation";
            }
            DependencyScope scope;
            try {
                scope = DependencyScope.parse(scopeText);
            } catch (IllegalArgumentException e) {
                problems.add(Problem.error("dependency-scope-unknown",
                        "portee inconnue dans " + location + " : " + scopeText));
                continue;
            }
            Set<String> variants = new LinkedHashSet<>();
            TomlValue variantsValue = table.get("variants");
            if (variantsValue != null && variantsValue.asArray() != null) {
                for (TomlValue variant : variantsValue.asArray()) {
                    String text = variant.asString();
                    if (text != null) {
                        variants.add(text);
                    }
                }
            }
            try {
                if (path != null && coordinates == null) {
                    dependencies.add(DependencyRef.toModule(path, scope, variants));
                } else if (coordinates != null && path == null) {
                    dependencies.add(DependencyRef.external(coordinates, scope, variants));
                } else {
                    problems.add(Problem.error("dependency-ambiguous",
                            "une dependance est soit path, soit coordinates : "
                                    + location));
                }
            } catch (IllegalArgumentException e) {
                problems.add(Problem.error("dependency-invalid",
                        "dependance invalide dans " + location + " : " + e.getMessage()));
            }
        }
        return dependencies;
    }

    /** Facettes : [facets.<domaine>] en entrées texte. */
    private static Map<String, Facet> readFacets(TomlDocument document,
                                                 List<Problem> problems) {
        Map<String, Facet> facets = new LinkedHashMap<>();
        TomlValue all = document.find("facets").orElse(null);
        Map<String, TomlValue> tables = all == null ? null : all.asTable();
        if (tables == null) {
            return facets;
        }
        for (Map.Entry<String, TomlValue> entry : tables.entrySet()) {
            Map<String, TomlValue> table = entry.getValue().asTable();
            if (table == null) {
                continue;
            }
            Map<String, String> entries = new LinkedHashMap<>();
            for (Map.Entry<String, TomlValue> config : table.entrySet()) {
                String text = stringValue(config.getValue());
                if (text != null) {
                    entries.put(config.getKey(), text);
                } else {
                    problems.add(Problem.info("facet-value-skipped",
                            "valeur de facette non textuelle ignoree : "
                                    + entry.getKey() + "." + config.getKey()));
                }
            }
            try {
                facets.put(entry.getKey(), new Facet(entry.getKey(), entries));
            } catch (IllegalArgumentException e) {
                problems.add(Problem.error("facet-type-invalid",
                        "domaine de facette invalide : " + entry.getKey()));
            }
        }
        return facets;
    }

    /** Les problèmes d'analyse du parseur deviennent des problèmes de chargement. */
    private static void collectParseProblems(TomlDocument document, String location,
                                             List<Problem> problems) {
        for (TomlProblem parseProblem : document.problems()) {
            problems.add(Problem.error("toml-syntax",
                    location + " : " + parseProblem));
        }
    }

    /** Signale les clés de premier niveau inconnues du fichier racine. */
    private static void reportUnknownWorkspaceKeys(TomlDocument document,
                                                   List<Problem> problems) {
        for (String key : document.root().entries().keySet()) {
            if (!List.of("schema", "name", "modules", "android").contains(key)) {
                problems.add(Problem.info("workspace-key-unknown",
                        "cle inconnue ignoree dans " + WORKSPACE_FILE + " : " + key));
            }
        }
    }

    /** Signale les clés de premier niveau inconnues d'un fichier de module. */
    private static void reportUnknownModuleKeys(TomlDocument document, String location,
                                                List<Problem> problems) {
        for (String key : document.root().entries().keySet()) {
            if (!List.of("schema", "name", "type", "sources", "dependencies",
                    "facets").contains(key)) {
                problems.add(Problem.info("module-key-unknown",
                        "cle inconnue ignoree dans " + location + " : " + key));
            }
        }
    }

    private static String stringValue(TomlValue value) {
        return value == null ? null : value.asString();
    }
}
