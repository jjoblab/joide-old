package jo.joide.imports.gradle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jo.joide.model.toml.Toml;
import jo.joide.model.toml.TomlDocument;
import jo.joide.model.toml.TomlProblem;
import jo.joide.model.toml.TomlValue;
import jo.joide.platform.Problem;

/**
 * Lecture d'un catalogue de versions Gradle ({@code
 * gradle/libs.versions.toml}) : versions, bibliothèques, groupes et
 * greffons.
 *
 * <p>Le catalogue est la source des alias {@code libs.foo.bar} des
 * scripts ; la lecture résout les références de version mais ne
 * touche jamais au réseau (ADR-039). Les entrées mal formées sont
 * rapportées, jamais ignorées en silence (ADR-040).</p>
 */
public final class VersionCatalog {

    /** Une bibliothèque du catalogue : groupe, artefact, version. */
    public static final class Library {

        private final String alias;
        private final String group;
        private final String artifact;
        private final String version;

        /**
         * @param alias    alias dans le catalogue
         * @param group    groupe
         * @param artifact artefact
         * @param version  version, ou {@code null} si non épinglée
         */
        public Library(String alias, String group, String artifact,
                       String version) {
            this.alias = alias;
            this.group = group;
            this.artifact = artifact;
            this.version = version;
        }

        /** @return l'alias dans le catalogue */
        public String alias() {
            return alias;
        }

        /** @return le groupe */
        public String group() {
            return group;
        }

        /** @return l'artefact */
        public String artifact() {
            return artifact;
        }

        /** @return la version, ou {@code null} si non épinglée */
        public String version() {
            return version;
        }

        /** @return la coordonnée « groupe:artefact:version », ou null */
        public String coordinates() {
            return version == null ? null : group + ":" + artifact
                    + ":" + version;
        }

        /** @return la référence « groupe:artefact » sans version */
        public String ga() {
            return group + ":" + artifact;
        }
    }

    /** Un greffon du catalogue : identifiant et version. */
    public static final class Plugin {

        private final String alias;
        private final String id;
        private final String version;

        /**
         * @param alias   alias dans le catalogue
         * @param id      identifiant du greffon
         * @param version version, ou {@code null}
         */
        public Plugin(String alias, String id, String version) {
            this.alias = alias;
            this.id = id;
            this.version = version;
        }

        /** @return l'alias dans le catalogue */
        public String alias() {
            return alias;
        }

        /** @return l'identifiant du greffon */
        public String id() {
            return id;
        }

        /** @return la version, ou {@code null} */
        public String version() {
            return version;
        }
    }

    private final Map<String, String> versions;
    private final Map<String, Library> libraries;
    private final Map<String, Plugin> plugins;

    private VersionCatalog(Map<String, String> versions,
                           Map<String, Library> libraries,
                           Map<String, Plugin> plugins) {
        this.versions = versions;
        this.libraries = libraries;
        this.plugins = plugins;
    }

    /** @return un catalogue vide */
    public static VersionCatalog empty() {
        return new VersionCatalog(Map.of(), Map.of(), Map.of());
    }

    /**
     * @param content  contenu du fichier TOML
     * @param problems réceptacle des problèmes
     * @param location localisation lisible, pour les rapports
     * @return le catalogue lu, éventuellement vide
     */
    public static VersionCatalog parse(String content,
                                       List<Problem> problems,
                                       String location) {
        TomlDocument document = Toml.parse(
                content == null ? "" : content);
        for (TomlProblem parseProblem : document.problems()) {
            problems.add(Problem.error("catalog-syntax",
                    location + " : " + parseProblem));
        }
        Map<String, String> versions = new LinkedHashMap<>();
        Map<String, Library> libraries = new LinkedHashMap<>();
        Map<String, Plugin> plugins = new LinkedHashMap<>();
        readVersions(document, versions, problems, location);
        readLibraries(document, versions, libraries, problems, location);
        readPlugins(document, versions, plugins, problems, location);
        return new VersionCatalog(versions, libraries, plugins);
    }

    private static void readVersions(TomlDocument document,
                                     Map<String, String> versions,
                                     List<Problem> problems,
                                     String location) {
        TomlValue table = document.find("versions").orElse(null);
        Map<String, TomlValue> entries =
                table == null ? null : table.asTable();
        if (entries == null) {
            return;
        }
        for (Map.Entry<String, TomlValue> entry : entries.entrySet()) {
            if (entry.getValue().isString()) {
                versions.put(entry.getKey(),
                        entry.getValue().asString());
            } else {
                problems.add(Problem.warn("catalog-version-malformed",
                        "version '" + entry.getKey() + "' not a string in "
                                + location));
            }
        }
    }

    private static void readLibraries(TomlDocument document,
                                      Map<String, String> versions,
                                      Map<String, Library> libraries,
                                      List<Problem> problems,
                                      String location) {
        TomlValue table = document.find("libraries").orElse(null);
        Map<String, TomlValue> entries =
                table == null ? null : table.asTable();
        if (entries == null) {
            return;
        }
        for (Map.Entry<String, TomlValue> entry : entries.entrySet()) {
            readLibrary(entry.getKey(), entry.getValue(), versions,
                    libraries, problems, location);
        }
    }

    private static void readLibrary(String alias, TomlValue value,
                                    Map<String, String> versions,
                                    Map<String, Library> libraries,
                                    List<Problem> problems,
                                    String location) {
        if (value.isString()) {
            String[] parts = value.asString().split(":");
            if (parts.length == 2) {
                libraries.put(alias, new Library(alias, parts[0],
                        parts[1], null));
                problems.add(Problem.warn("catalog-library-unversioned",
                        "library '" + alias + "' has no version in "
                                + location));
                return;
            }
            problems.add(Problem.warn("catalog-library-malformed",
                    "library '" + alias + "' is not group:artifact in "
                            + location));
            return;
        }
        Map<String, TomlValue> table = value.asTable();
        if (table == null) {
            problems.add(Problem.warn("catalog-library-malformed",
                    "library '" + alias + "' unreadable in " + location));
            return;
        }
        String group = table.containsKey("group")
                ? table.get("group").asString() : null;
        String name = table.containsKey("name")
                ? table.get("name").asString() : null;
        String module = table.containsKey("module")
                ? table.get("module").asString() : null;
        if (group == null && module != null && module.contains(":")) {
            String[] parts = module.split(":");
            group = parts[0];
            name = parts[1];
        }
        if (group == null || name == null) {
            problems.add(Problem.warn("catalog-library-malformed",
                    "library '" + alias + "' without group/name in "
                            + location));
            return;
        }
        libraries.put(alias, new Library(alias, group, name,
                readVersionValue(alias, table, versions, location)));
    }

    private static String readVersionValue(String alias,
                                           Map<String, TomlValue> table,
                                           Map<String, String> versions,
                                           String location) {
        TomlValue version = table.get("version");
        if (version == null) {
            return null;
        }
        if (version.isString()) {
            return version.asString();
        }
        Map<String, TomlValue> versionTable = version.asTable();
        if (versionTable == null) {
            return null;
        }
        TomlValue strict = versionTable.get("strictly");
        if (strict != null && strict.isString()) {
            return strict.asString();
        }
        TomlValue require = versionTable.get("require");
        if (require != null && require.isString()) {
            return require.asString();
        }
        TomlValue ref = versionTable.get("ref");
        if (ref != null && ref.isString()) {
            String referenced = versions.get(ref.asString());
            if (referenced != null) {
                return referenced;
            }
            return null;
        }
        TomlValue prefer = versionTable.get("prefer");
        if (prefer != null && prefer.isString()) {
            return prefer.asString();
        }
        return null;
    }

    private static void readPlugins(TomlDocument document,
                                    Map<String, String> versions,
                                    Map<String, Plugin> plugins,
                                    List<Problem> problems,
                                    String location) {
        TomlValue table = document.find("plugins").orElse(null);
        Map<String, TomlValue> entries =
                table == null ? null : table.asTable();
        if (entries == null) {
            return;
        }
        for (Map.Entry<String, TomlValue> entry : entries.entrySet()) {
            TomlValue value = entry.getValue();
            String id = null;
            String version = null;
            if (value.isString()) {
                id = value.asString();
            } else {
                Map<String, TomlValue> pluginTable = value.asTable();
                if (pluginTable != null) {
                    id = pluginTable.containsKey("id")
                            ? pluginTable.get("id").asString() : null;
                    version = readVersionValue(entry.getKey(),
                            pluginTable, versions, location);
                }
            }
            if (id == null) {
                problems.add(Problem.warn("catalog-plugin-malformed",
                        "plugin '" + entry.getKey() + "' unreadable in "
                                + location));
                continue;
            }
            plugins.put(entry.getKey(), new Plugin(entry.getKey(), id,
                    version));
        }
    }

    /**
     * Cherche une bibliothèque par alias de script : « foo.bar »
     * désigne l'alias « foo-bar » (les points des scripts deviennent
     * des tirets dans le catalogue).
     *
     * @param dottedAlias alias tel qu'écrit dans le script
     * @return la bibliothèque, si l'alias existe
     */
    public Optional<Library> library(String dottedAlias) {
        return Optional.ofNullable(libraries.get(
                dottedAlias.replace('.', '-')));
    }

    /**
     * @param dottedAlias alias tel qu'écrit dans le script
     * @return le greffon, si l'alias existe
     */
    public Optional<Plugin> plugin(String dottedAlias) {
        return Optional.ofNullable(plugins.get(
                dottedAlias.replace('.', '-')));
    }

    /**
     * @param name nom de version (« foo » de [versions])
     * @return la version, si elle existe
     */
    public Optional<String> version(String name) {
        return Optional.ofNullable(versions.get(name));
    }

    /** @return les bibliothèques, par alias */
    public Map<String, Library> libraries() {
        return libraries;
    }

    /** @return les greffons, par alias */
    public Map<String, Plugin> plugins() {
        return plugins;
    }

    /** @return les versions, par nom */
    public Map<String, String> versions() {
        return versions;
    }

    /** @return la liste des alias de bibliothèques, triée */
    public List<String> libraryAliases() {
        List<String> aliases = new ArrayList<>(libraries.keySet());
        aliases.sort(String::compareTo);
        return aliases;
    }

    @Override
    public String toString() {
        return "VersionCatalog[" + libraries.size() + " libraries, "
                + plugins.size() + " plugins]";
    }
}
