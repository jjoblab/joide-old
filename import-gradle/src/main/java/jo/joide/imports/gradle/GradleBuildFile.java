package jo.joide.imports.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jo.joide.imports.gradle.script.Script;
import jo.joide.imports.gradle.script.ScriptExpr;
import jo.joide.imports.gradle.script.ScriptParser;
import jo.joide.imports.gradle.script.ScriptStmt;
import jo.joide.platform.Problem;

/**
 * Lecture statique d'un fichier {@code build.gradle(.kts)} : greffons,
 * bloc {@code android { }}, dépendances, variables {@code ext} et
 * ensembles de sources.
 *
 * <p>Le lecteur extrait de la structure, pas de la sémantique : les
 * valeurs restent brutes (interpolation non résolue), les formes des
 * deux dialectes sont ramenées aux mêmes enregistrements. Tout ce qui
 * n'est pas mappé est compté pour le SyncReport — jamais ignoré en
 * silence (ADR-040), jamais d'exception, jamais d'exécution
 * (ADR-039).</p>
 */
public final class GradleBuildFile {

    /** Un greffon déclaré, sous l'une des formes des deux dialectes. */
    public static final class PluginDecl {

        private final String id;
        private final String alias;
        private final String version;
        private final boolean applyFalse;
        private final int line;

        /**
         * @param id         identifiant du greffon, ou {@code null}
         *                   si déclaré par alias
         * @param alias      alias de catalogue, ou {@code null}
         * @param version    version déclarée, ou {@code null}
         * @param applyFalse vrai si le greffon n'est pas appliqué
         * @param line       ligne d'origine
         */
        public PluginDecl(String id, String alias, String version,
                          boolean applyFalse, int line) {
            this.id = id;
            this.alias = alias;
            this.version = version;
            this.applyFalse = applyFalse;
            this.line = line;
        }

        /** @return l'identifiant, ou {@code null} si déclaré par alias */
        public String id() {
            return id;
        }

        /** @return l'alias de catalogue, ou {@code null} */
        public String alias() {
            return alias;
        }

        /** @return la version déclarée, ou {@code null} */
        public String version() {
            return version;
        }

        /** @return vrai si le greffon n'est pas appliqué */
        public boolean applyFalse() {
            return applyFalse;
        }

        /** @return la ligne d'origine */
        public int line() {
            return line;
        }

        /** @return l'identifiant, résolu ou brut selon la forme */
        public String declared() {
            return id != null ? id : alias;
        }
    }

    /** Le bloc android : identité, réglages par défaut, types et parfums. */
    public static final class AndroidInfo {

        private final String namespace;
        private final String compileSdk;
        private final String applicationId;
        private final String minSdk;
        private final String targetSdk;
        private final String versionCode;
        private final String versionName;
        private final Map<String, Map<String, String>> buildTypes;
        private final Map<String, Map<String, String>> flavors;
        private final Map<String, String> buildFeatures;
        private final String flavorDimensions;

        /**
         * @param namespace        espace de noms
         * @param compileSdk       niveau de compilation
         * @param applicationId    identifiant d'application
         * @param minSdk           niveau minimum
         * @param targetSdk        niveau cible
         * @param versionCode      code de version
         * @param versionName      nom de version
         * @param buildTypes       types de build, par nom
         * @param flavors          parfums, par nom
         * @param buildFeatures    fonctions activées
         * @param flavorDimensions dimensions déclarées
         */
        public AndroidInfo(String namespace, String compileSdk,
                           String applicationId, String minSdk,
                           String targetSdk, String versionCode,
                           String versionName,
                           Map<String, Map<String, String>> buildTypes,
                           Map<String, Map<String, String>> flavors,
                           Map<String, String> buildFeatures,
                           String flavorDimensions) {
            this.namespace = namespace;
            this.compileSdk = compileSdk;
            this.applicationId = applicationId;
            this.minSdk = minSdk;
            this.targetSdk = targetSdk;
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.buildTypes = buildTypes;
            this.flavors = flavors;
            this.buildFeatures = buildFeatures;
            this.flavorDimensions = flavorDimensions;
        }

        /** @return l'espace de noms */
        public String namespace() {
            return namespace;
        }

        /** @return le niveau de compilation */
        public String compileSdk() {
            return compileSdk;
        }

        /** @return l'identifiant d'application */
        public String applicationId() {
            return applicationId;
        }

        /** @return le niveau minimum */
        public String minSdk() {
            return minSdk;
        }

        /** @return le niveau cible */
        public String targetSdk() {
            return targetSdk;
        }

        /** @return le code de version */
        public String versionCode() {
            return versionCode;
        }

        /** @return le nom de version */
        public String versionName() {
            return versionName;
        }

        /** @return les types de build, par nom */
        public Map<String, Map<String, String>> buildTypes() {
            return buildTypes;
        }

        /** @return les parfums, par nom */
        public Map<String, Map<String, String>> flavors() {
            return flavors;
        }

        /** @return les fonctions activées */
        public Map<String, String> buildFeatures() {
            return buildFeatures;
        }

        /** @return les dimensions déclarées */
        public String flavorDimensions() {
            return flavorDimensions;
        }
    }

    /** Une dépendance déclarée : configuration et première expression. */
    public static final class DependencyDecl {

        private final String configuration;
        private final ScriptExpr notation;
        private final boolean hasBlock;
        private final int line;

        /**
         * @param configuration nom de la configuration
         * @param notation      première expression de la notation
         * @param hasBlock      vrai si une fermeture suit l'appel
         * @param line          ligne d'origine
         */
        public DependencyDecl(String configuration, ScriptExpr notation,
                              boolean hasBlock, int line) {
            this.configuration = configuration;
            this.notation = notation;
            this.hasBlock = hasBlock;
            this.line = line;
        }

        /** @return le nom de la configuration */
        public String configuration() {
            return configuration;
        }

        /** @return la première expression de la notation */
        public ScriptExpr notation() {
            return notation;
        }

        /** @return vrai si une fermeture suit l'appel */
        public boolean hasBlock() {
            return hasBlock;
        }

        /** @return la ligne d'origine */
        public int line() {
            return line;
        }
    }

    /** Une opération de répertoires sur un conteneur d'un ensemble. */
    public static final class SrcDirsOp {

        private final String container;
        private final boolean replace;
        private final List<String> dirs;
        private final int line;

        /**
         * @param container conteneur (java, res, assets…)
         * @param replace   vrai si l'opération remplace les défauts
         * @param dirs      répertoires déclarés
         * @param line      ligne d'origine
         */
        public SrcDirsOp(String container, boolean replace,
                         List<String> dirs, int line) {
            this.container = container;
            this.replace = replace;
            this.dirs = dirs;
            this.line = line;
        }

        /** @return le conteneur */
        public String container() {
            return container;
        }

        /** @return vrai si l'opération remplace les défauts */
        public boolean replace() {
            return replace;
        }

        /** @return les répertoires déclarés */
        public List<String> dirs() {
            return dirs;
        }

        /** @return la ligne d'origine */
        public int line() {
            return line;
        }
    }

    /** Un ensemble de sources déclaré : nom et opérations. */
    public static final class SourceSetDecl {

        private final String name;
        private final List<SrcDirsOp> ops;
        private final int line;

        /**
         * @param name nom de l'ensemble
         * @param ops  opérations de répertoires
         * @param line ligne d'origine
         */
        public SourceSetDecl(String name, List<SrcDirsOp> ops, int line) {
            this.name = name;
            this.ops = ops;
            this.line = line;
        }

        /** @return le nom de l'ensemble */
        public String name() {
            return name;
        }

        /** @return les opérations de répertoires */
        public List<SrcDirsOp> ops() {
            return ops;
        }

        /** @return la ligne d'origine */
        public int line() {
            return line;
        }
    }

    private final List<PluginDecl> plugins;
    private final Optional<AndroidInfo> android;
    private final List<DependencyDecl> dependencies;
    private final Map<String, String> ext;
    private final List<SourceSetDecl> sourceSets;
    private final List<String> unmapped;

    private GradleBuildFile(List<PluginDecl> plugins,
                            Optional<AndroidInfo> android,
                            List<DependencyDecl> dependencies,
                            Map<String, String> ext,
                            List<SourceSetDecl> sourceSets,
                            List<String> unmapped) {
        this.plugins = plugins;
        this.android = android;
        this.dependencies = dependencies;
        this.ext = ext;
        this.sourceSets = sourceSets;
        this.unmapped = unmapped;
    }

    /** @return un fichier de build vide (aucun greffon, aucune dépendance) */
    public static GradleBuildFile empty() {
        return new GradleBuildFile(List.of(), Optional.empty(),
                List.of(), Map.of(), List.of(), List.of());
    }

    /**
     * Lit un fichier de build, sans résolution de versions.
     *
     * @param buildFile chemin de {@code build.gradle(.kts)}
     * @param problems  réceptacle des problèmes
     * @return le fichier lu ; un fichier illisible donne un résultat
     *         vide et une erreur
     */
    public static GradleBuildFile read(Path buildFile,
                                       List<Problem> problems) {
        return read(buildFile, problems, null);
    }

    /**
     * Lit un fichier de build.
     *
     * @param buildFile chemin de {@code build.gradle(.kts)}
     * @param problems  réceptacle des problèmes
     * @param versions  résolution des références {@code
     *                  libs.versions.x} du catalogue (null : aucune)
     * @return le fichier lu ; un fichier illisible donne un résultat
     *         vide et une erreur
     */
    public static GradleBuildFile read(Path buildFile,
                                       List<Problem> problems,
                                       java.util.function.Function<String, String> versions) {
        String content;
        try {
            content = Files.readString(buildFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            problems.add(Problem.error("build-file-unreadable",
                    buildFile + " : " + e.getMessage()));
            return new GradleBuildFile(List.of(), Optional.empty(),
                    List.of(), Map.of(), List.of(), List.of());
        }
        Script script = ScriptParser.parse(content);
        problems.addAll(script.problems());
        List<PluginDecl> plugins = new ArrayList<>();
        readPlugins(script, plugins, problems, buildFile);
        Optional<ScriptStmt> androidBlock = script.findCall("android")
                .filter(stmt -> stmt.block() != null);
        Optional<AndroidInfo> android =
                readAndroid(script, problems, buildFile, versions);
        List<DependencyDecl> dependencies =
                readDependencies(script, problems, buildFile);
        Map<String, String> ext = new LinkedHashMap<>();
        readExt(script, ext);
        List<SourceSetDecl> sourceSets = readSourceSets(script,
                androidBlock, problems, buildFile);
        List<String> unmapped = new ArrayList<>();
        collectUnmapped(script, unmapped);
        return new GradleBuildFile(plugins, android, dependencies, ext,
                sourceSets, unmapped);
    }

    private static void readPlugins(Script script,
                                    List<PluginDecl> plugins,
                                    List<Problem> problems,
                                    Path buildFile) {
        Optional<ScriptStmt> pluginsBlock =
                script.findCall("plugins");
        if (pluginsBlock.isPresent() && pluginsBlock.get().block() != null) {
            PluginDecl last = null;
            for (ScriptStmt stmt
                    : pluginsBlock.get().block().statements()) {
                if (stmt.kind() != ScriptStmt.Kind.CALL) {
                    if (stmt.kind() == ScriptStmt.Kind.UNKNOWN) {
                        problems.add(Problem.info("plugins-stmt-unknown",
                                "plugins statement not understood in "
                                        + buildFile + " (line "
                                        + stmt.line() + ")"));
                    }
                    continue;
                }
                PluginDecl decl = readPluginCall(stmt, problems, buildFile);
                if (decl != null) {
                    plugins.add(decl);
                    last = decl;
                    continue;
                }
                if (last == null) {
                    continue;
                }
                if ("apply".equals(stmt.name())) {
                    boolean isFalse = stmt.args().size() == 1
                            && isFalseWord(stmt.args().get(0));
                    if (isFalse) {
                        int index = plugins.size() - 1;
                        plugins.set(index, new PluginDecl(last.id(),
                                last.alias(), last.version(), true,
                                last.line()));
                        last = plugins.get(index);
                    } else {
                        problems.add(Problem.info(
                                "plugins-apply-argument",
                                "apply argument not mapped in "
                                        + buildFile + " (line "
                                        + stmt.line() + ")"));
                    }
                } else if ("version".equals(stmt.name())
                        && stmt.args().size() == 1) {
                    String version = stringValue(stmt.args().get(0));
                    if (version != null) {
                        int index = plugins.size() - 1;
                        plugins.set(index, new PluginDecl(last.id(),
                                last.alias(), version, last.applyFalse(),
                                last.line()));
                        last = plugins.get(index);
                    }
                }
            }
        }
        for (ScriptStmt stmt : script.statements()) {
            if (stmt.kind() == ScriptStmt.Kind.CALL
                    && "apply".equals(stmt.name())
                    && !stmt.args().isEmpty()
                    && stmt.args().get(0)
                            instanceof ScriptExpr.MapExpr map) {
                for (ScriptExpr.MapExpr.Entry entry : map.entries()) {
                    if (entry.key() instanceof ScriptExpr.Literal key
                            && "plugin".equals(key.raw())
                            && entry.value()
                                    instanceof ScriptExpr.Literal value) {
                        plugins.add(new PluginDecl(value.decoded(), null,
                                null, false, stmt.line()));
                    }
                }
            }
        }
    }

    /** Le mot « false » : référence ou littérale selon le dialecte. */
    private static boolean isFalseWord(ScriptExpr expr) {
        if (expr instanceof ScriptExpr.Reference ref) {
            return ref.chain().size() == 1
                    && "false".equals(ref.chain().get(0));
        }
        return expr instanceof ScriptExpr.Literal literal
                && "false".equals(literal.raw());
    }

    private static PluginDecl readPluginCall(ScriptStmt stmt,
                                             List<Problem> problems,
                                             Path buildFile) {
        String name = stmt.name();
        if ("id".equals(name) && !stmt.args().isEmpty()
                && stmt.args().get(0)
                        instanceof ScriptExpr.Literal literal) {
            return new PluginDecl(literal.decoded(), null, null, false,
                    stmt.line());
        }
        if ("alias".equals(name) && !stmt.args().isEmpty()
                && stmt.args().get(0)
                        instanceof ScriptExpr.Reference ref) {
            return new PluginDecl(null, String.join(".", ref.chain()),
                    null, false, stmt.line());
        }
        if ("kotlin".equals(name) && !stmt.args().isEmpty()
                && stmt.args().get(0)
                        instanceof ScriptExpr.Literal literal) {
            return new PluginDecl(
                    "org.jetbrains.kotlin." + literal.decoded(), null,
                    null, false, stmt.line());
        }
        if (stmt.args().isEmpty() && stmt.block() == null
                && stmt.receiver().isEmpty()) {
            return new PluginDecl(name, null, null, false, stmt.line());
        }
        if ("alias".equals(name) || "id".equals(name)
                || "kotlin".equals(name)) {
            problems.add(Problem.info("plugins-argument-not-literal",
                    name + " argument not a literal in " + buildFile
                            + " (line " + stmt.line() + ")"));
        }
        return null;
    }

    private static Optional<AndroidInfo> readAndroid(Script script,
                                                     List<Problem> problems,
                                                     Path buildFile,
                                                     java.util.function.Function<String, String> versions) {
        Optional<ScriptStmt> block = script.findCall("android");
        if (block.isEmpty() || block.get().block() == null) {
            return Optional.empty();
        }
        Script android = block.get().block();
        Map<String, Map<String, String>> buildTypes = new LinkedHashMap<>();
        Map<String, Map<String, String>> flavors = new LinkedHashMap<>();
        Map<String, String> features = new LinkedHashMap<>();
        Map<String, String> defaults = new LinkedHashMap<>();
        String flavorDimensions = null;
        for (ScriptStmt stmt : android.statements()) {
            if (stmt.kind() != ScriptStmt.Kind.CALL
                    || stmt.block() == null) {
                if (stmt.kind() == ScriptStmt.Kind.ASSIGN
                        && stmt.target() != null) {
                    String value = referenceableValue(stmt.value(),
                            versions);
                    if (value != null) {
                        defaults.put(kebab(stmt.target()), value);
                    } else {
                        problems.add(Problem.info(
                                "android-value-not-literal",
                                stmt.target() + " value not a literal"
                                        + " nor a version reference in "
                                        + buildFile + " (line "
                                        + stmt.line() + ")"));
                    }
                } else if (stmt.kind() == ScriptStmt.Kind.CALL
                        && stmt.block() == null
                        && stmt.args().size() == 1) {
                    String value = referenceableValue(stmt.args().get(0),
                            versions);
                    if (value != null) {
                        defaults.put(kebab(stmt.qualifiedName()), value);
                    }
                } else if (stmt.kind() == ScriptStmt.Kind.UNKNOWN) {
                    problems.add(Problem.info("android-stmt-unknown",
                            "android statement not understood in "
                                    + buildFile + " (line " + stmt.line()
                                    + ")"));
                }
                continue;
            }
            String name = stmt.name();
            if ("defaultConfig".equals(name)) {
                readNamedEntries(stmt.block(), defaults, problems,
                        buildFile, versions);
            } else if ("buildTypes".equals(name)) {
                readBlockMap(stmt.block(), buildTypes, problems, buildFile,
                        versions);
            } else if ("productFlavors".equals(name)) {
                readBlockMap(stmt.block(), flavors, problems, buildFile,
                        versions);
            } else if ("buildFeatures".equals(name)) {
                readNamedEntries(stmt.block(), features, problems,
                        buildFile, versions);
            } else if ("flavorDimensions".equals(name)
                    || "flavorDimensionList".equals(name)) {
                flavorDimensions = firstArgument(stmt);
            } else {
                problems.add(Problem.info("android-block-not-mapped",
                        "android block '" + name + "' not mapped in "
                                + buildFile + " (line " + stmt.line()
                                + ")"));
            }
        }
        String namespace = defaults.remove("namespace");
        String compileSdk = defaults.remove("compile-sdk");
        if (compileSdk == null) {
            compileSdk = defaults.remove("compile-sdk-version");
        }
        String applicationId = defaults.remove("application-id");
        String minSdk = defaults.remove("min-sdk");
        if (minSdk == null) {
            minSdk = defaults.remove("min-sdk-version");
        }
        String targetSdk = defaults.remove("target-sdk");
        if (targetSdk == null) {
            targetSdk = defaults.remove("target-sdk-version");
        }
        String versionCode = defaults.remove("version-code");
        String versionName = defaults.remove("version-name");
        return Optional.of(new AndroidInfo(namespace, compileSdk,
                applicationId, minSdk, targetSdk, versionCode, versionName,
                buildTypes, flavors, features, flavorDimensions));
    }

    /** Lit un bloc d'entrées nommées (defaultConfig, buildFeatures). */
    private static void readNamedEntries(Script block,
                                         Map<String, String> into,
                                         List<Problem> problems,
                                         Path buildFile,
                                         java.util.function.Function<String, String> versions) {
        for (ScriptStmt stmt : block.statements()) {
            if (stmt.kind() == ScriptStmt.Kind.ASSIGN
                    && stmt.target() != null) {
                String value = referenceableValue(stmt.value(), versions);
                if (value != null) {
                    into.put(kebab(normalizeEntryName(stmt.target())),
                            value);
                } else {
                    problems.add(Problem.info("android-value-not-literal",
                            stmt.target() + " value not a literal nor a"
                                    + " version reference in "
                                    + buildFile + " (line " + stmt.line()
                                    + ")"));
                }
            } else if (stmt.kind() == ScriptStmt.Kind.CALL
                    && stmt.block() == null && stmt.args().size() == 1) {
                String value = referenceableValue(stmt.args().get(0),
                        versions);
                if (value != null) {
                    into.put(kebab(normalizeEntryName(stmt.qualifiedName())),
                            value);
                }
            } else if (stmt.kind() == ScriptStmt.Kind.CALL
                    && stmt.block() != null) {
                problems.add(Problem.info("android-nested-block",
                        "nested block '" + stmt.qualifiedName()
                                + "' not mapped in " + buildFile
                                + " (line " + stmt.line() + ")"));
            }
        }
    }

    /** Lit buildTypes / productFlavors : des blocs nommés. */
    private static void readBlockMap(Script block,
                                     Map<String, Map<String, String>> into,
                                     List<Problem> problems,
                                     Path buildFile,
                                     java.util.function.Function<String, String> versions) {
        for (ScriptStmt stmt : block.statements()) {
            if (stmt.kind() != ScriptStmt.Kind.CALL
                    || stmt.block() == null) {
                continue;
            }
            String flavorName;
            if ("create".equals(stmt.name()) || "getByName".equals(
                    stmt.name())) {
                flavorName = firstArgument(stmt);
            } else if (stmt.receiver().isEmpty()) {
                flavorName = stmt.name();
            } else {
                flavorName = null;
            }
            if (flavorName == null) {
                problems.add(Problem.info("android-named-block-unknown",
                        "named block not mapped in " + buildFile
                                + " (line " + stmt.line() + ")"));
                continue;
            }
            Map<String, String> entries = new LinkedHashMap<>();
            readNamedEntries(stmt.block(), entries, problems, buildFile,
                    versions);
            into.put(flavorName, entries);
        }
    }

    private static List<DependencyDecl> readDependencies(Script script,
                                                         List<Problem> problems,
                                                         Path buildFile) {
        List<DependencyDecl> dependencies = new ArrayList<>();
        Optional<ScriptStmt> block = script.findCall("dependencies");
        if (block.isEmpty() || block.get().block() == null) {
            return dependencies;
        }
        for (ScriptStmt stmt : block.get().block().statements()) {
            if (stmt.kind() != ScriptStmt.Kind.CALL) {
                if (stmt.kind() == ScriptStmt.Kind.UNKNOWN) {
                    problems.add(Problem.info("dependencies-stmt-unknown",
                            "dependencies statement not understood in "
                                    + buildFile + " (line " + stmt.line()
                                    + ")"));
                }
                continue;
            }
            if (stmt.args().isEmpty() && stmt.block() == null) {
                problems.add(Problem.info("dependencies-empty-call",
                        "call '" + stmt.qualifiedName() + "' without"
                                + " argument in " + buildFile + " (line "
                                + stmt.line() + ")"));
                continue;
            }
            ScriptExpr notation = stmt.args().isEmpty() ? null
                    : stmt.args().get(0);
            dependencies.add(new DependencyDecl(stmt.qualifiedName(),
                    notation, stmt.block() != null, stmt.line()));
        }
        return dependencies;
    }

    private static void readExt(Script script, Map<String, String> ext) {
        Optional<ScriptStmt> block = script.findCall("ext");
        if (block.isPresent() && block.get().block() != null) {
            flattenExt(block.get().block(), "", ext);
        }
        for (ScriptStmt stmt : script.statements()) {
            if (stmt.kind() == ScriptStmt.Kind.ASSIGN
                    && stmt.target() != null
                    && stmt.target().startsWith("ext.")) {
                String value = stringValue(stmt.value());
                if (value != null) {
                    ext.put(stmt.target().substring(4), value);
                }
            }
        }
    }

    private static void flattenExt(Script block, String prefix,
                                   Map<String, String> ext) {
        for (ScriptStmt stmt : block.statements()) {
            if (stmt.kind() != ScriptStmt.Kind.ASSIGN
                    || stmt.target() == null) {
                continue;
            }
            String key = prefix.isEmpty() ? stmt.target()
                    : prefix + "." + stmt.target();
            if (stmt.value() instanceof ScriptExpr.MapExpr map) {
                Map<String, String> nested = new LinkedHashMap<>();
                for (ScriptExpr.MapExpr.Entry entry : map.entries()) {
                    String entryKey = stringValue(entry.key());
                    String entryValue = stringValue(entry.value());
                    if (entryKey != null && entryValue != null) {
                        nested.put(entryKey, entryValue);
                    }
                }
                flattenExt(nested, key, ext);
            } else {
                String value = stringValue(stmt.value());
                if (value != null) {
                    ext.put(key, value);
                }
            }
        }
    }

    private static void flattenExt(Map<String, String> nested, String prefix,
                                   Map<String, String> ext) {
        for (Map.Entry<String, String> entry : nested.entrySet()) {
            if (entry.getValue() != null) {
                ext.put(prefix + "." + entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Lit les ensembles de sources : au premier niveau et dans le
     * bloc android (les deux emplacements de la DSL AGP) ; les
     * déclarations ultérieures remplacent les premières par nom
     * d'ensemble.
     */
    private static List<SourceSetDecl> readSourceSets(Script script,
                                                      Optional<ScriptStmt> androidBlock,
                                                      List<Problem> problems,
                                                      Path buildFile) {
        Map<String, SourceSetDecl> sets = new LinkedHashMap<>();
        Optional<ScriptStmt> top = script.findCall("sourceSets");
        if (top.isPresent() && top.get().block() != null) {
            readSourceSetsBlock(top.get().block(), sets, problems,
                    buildFile);
        }
        if (androidBlock.isPresent()) {
            Optional<ScriptStmt> inner =
                    androidBlock.get().block().findCall("sourceSets");
            if (inner.isPresent() && inner.get().block() != null) {
                readSourceSetsBlock(inner.get().block(), sets, problems,
                        buildFile);
            }
        }
        return new ArrayList<>(sets.values());
    }

    private static void readSourceSetsBlock(Script block,
                                             Map<String, SourceSetDecl> into,
                                             List<Problem> problems,
                                             Path buildFile) {
        for (ScriptStmt stmt : block.statements()) {
            if (stmt.kind() != ScriptStmt.Kind.CALL
                    || stmt.block() == null) {
                continue;
            }
            String setName;
            if ("getByName".equals(stmt.name())
                    || "create".equals(stmt.name())) {
                setName = firstArgument(stmt);
            } else if (stmt.receiver().isEmpty()) {
                setName = stmt.name();
            } else {
                setName = null;
            }
            if (setName == null) {
                problems.add(Problem.info("sourcesets-block-unknown",
                        "source set block not mapped in " + buildFile
                                + " (line " + stmt.line() + ")"));
                continue;
            }
            List<SrcDirsOp> ops = new ArrayList<>();
            SourceSetDecl existing = into.get(setName);
            if (existing != null) {
                // un ensemble peut etre declare au premier niveau et
                // dans le bloc android : les operations se cumulent
                ops.addAll(existing.ops());
            }
            for (ScriptStmt inner : stmt.block().statements()) {
                collectSrcDirsOp(inner, ops, problems, buildFile);
            }
            into.put(setName, new SourceSetDecl(setName, ops,
                    stmt.line()));
        }
    }

    private static void collectSrcDirsOp(ScriptStmt stmt,
                                         List<SrcDirsOp> ops,
                                         List<Problem> problems,
                                         Path buildFile) {
        if (stmt.kind() == ScriptStmt.Kind.CALL
                && !stmt.args().isEmpty()
                && stmt.block() == null) {
            String container = containerOf(stmt.qualifiedName());
            if (container == null) {
                problems.add(Problem.info("sourcesets-op-not-mapped",
                        "source set operation '" + stmt.qualifiedName()
                                + "' not mapped in " + buildFile
                                + " (line " + stmt.line() + ")"));
                return;
            }
            boolean replace = stmt.qualifiedName().endsWith("setSrcDirs")
                    || "manifest.srcFile".equals(stmt.qualifiedName());
            List<String> dirs = new ArrayList<>();
            for (ScriptExpr arg : stmt.args()) {
                String single = stringValue(arg);
                if (single != null) {
                    dirs.add(single);
                    continue;
                }
                List<String> list = stringList(arg);
                if (list != null) {
                    dirs.addAll(list);
                }
            }
            // une liste vide est licite : setSrcDirs(listOf()) efface
            // les répertoires par défaut du conteneur
            ops.add(new SrcDirsOp(container, replace, dirs,
                    stmt.line()));
            return;
        }
        if (stmt.kind() == ScriptStmt.Kind.ASSIGN
                && stmt.target() != null) {
            String container = containerOf(stmt.target());
            if (container == null) {
                return;
            }
            boolean replace = "=".equals(stmt.operator());
            String single = stringValue(stmt.value());
            if (single != null) {
                ops.add(new SrcDirsOp(container, replace, List.of(single),
                        stmt.line()));
                return;
            }
            List<String> dirs = stringList(stmt.value());
            if (dirs != null) {
                ops.add(new SrcDirsOp(container, replace, dirs,
                        stmt.line()));
            }
        }
    }

    private static String containerOf(String qualified) {
        int dot = qualified.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        String head = qualified.substring(0, dot);
        String tail = qualified.substring(dot + 1);
        if (!tail.equals("srcDirs") && !tail.equals("srcDir")
                && !tail.equals("setSrcDirs") && !tail.equals("srcFile")) {
            return null;
        }
        return head;
    }

    private static void collectUnmapped(Script script,
                                        List<String> unmapped) {
        List<String> known = List.of("plugins", "android", "dependencies",
                "ext", "sourceSets", "apply");
        for (ScriptStmt stmt : script.statements()) {
            if (stmt.kind() == ScriptStmt.Kind.CALL) {
                if (!known.contains(stmt.qualifiedName())) {
                    unmapped.add(stmt.qualifiedName() + " (line "
                            + stmt.line() + ")");
                }
            } else if (stmt.kind() == ScriptStmt.Kind.ASSIGN
                    && stmt.target() != null
                    && !stmt.target().startsWith("ext.")) {
                unmapped.add(stmt.target() + " (line " + stmt.line() + ")");
            } else if (stmt.kind() == ScriptStmt.Kind.UNKNOWN) {
                unmapped.add("unknown statement (line " + stmt.line()
                        + ")");
            }
        }
    }

    /**
     * Valeur textuelle d'une expression android : littérale, ou
     * référence de version du catalogue ({@code libs.versions.x},
     * {@code libs.versions.x.get().toInt()}).
     */
    private static String referenceableValue(ScriptExpr expr,
            java.util.function.Function<String, String> versions) {
        String literal = stringValue(expr);
        if (literal != null || versions == null) {
            return literal;
        }
        List<String> chain = new ArrayList<>();
        if (expr instanceof ScriptExpr.Reference ref) {
            chain.addAll(ref.chain());
        } else if (expr instanceof ScriptExpr.CallExpr call) {
            chain.addAll(call.receiver());
            chain.add(call.name());
        } else {
            return null;
        }
        while (!chain.isEmpty() && CALL_CHAIN_SUFFIXES.contains(
                chain.get(chain.size() - 1))) {
            chain.remove(chain.size() - 1);
        }
        if (chain.size() >= 3 && "libs".equals(chain.get(0))
                && "versions".equals(chain.get(1))) {
            String name = String.join(".",
                    chain.subList(2, chain.size()));
            return versions.apply(name);
        }
        return null;
    }

    private static final List<String> CALL_CHAIN_SUFFIXES =
            List.of("get", "toInt", "toString", "asProvider", "call");

    private static String stringValue(ScriptExpr expr) {
        if (expr instanceof ScriptExpr.Literal literal) {
            return literal.decoded();
        }
        return null;
    }

    private static List<String> stringList(ScriptExpr expr) {
        if (expr instanceof ScriptExpr.ListExpr list) {
            List<String> values = new ArrayList<>();
            for (ScriptExpr item : list.items()) {
                String value = stringValue(item);
                if (value == null) {
                    return null;
                }
                values.add(value);
            }
            return values;
        }
        // le dialecte Kotlin écrit listOf(...) : c'est un appel
        if (expr instanceof ScriptExpr.CallExpr call
                && List.of("listOf", "arrayListOf", "setOf", "mutableListOf")
                        .contains(call.name())
                && call.receiver().isEmpty()) {
            List<String> values = new ArrayList<>();
            for (ScriptExpr item : call.args()) {
                String value = stringValue(item);
                if (value == null) {
                    return null;
                }
                values.add(value);
            }
            return values;
        }
        return null;
    }

    private static String firstArgument(ScriptStmt stmt) {
        return stmt.args().isEmpty() ? null
                : stringValue(stmt.args().get(0));
    }

    /** Les identifiants camelCase des scripts deviennent kebab-case. */
    private static String kebab(String name) {
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

    /** Les propriétés « isX » du dialecte Kotlin se lisent comme « x ». */
    private static String normalizeEntryName(String target) {
        return target.startsWith("is") && target.length() > 2
                && Character.isUpperCase(target.charAt(2))
                ? Character.toLowerCase(target.charAt(2))
                        + target.substring(3)
                : target;
    }

    /** @return les greffons déclarés, dans l'ordre */
    public List<PluginDecl> plugins() {
        return plugins;
    }

    /** @return le bloc android, s'il existe */
    public Optional<AndroidInfo> android() {
        return android;
    }

    /** @return les dépendances déclarées, dans l'ordre */
    public List<DependencyDecl> dependencies() {
        return dependencies;
    }

    /** @return les variables ext, aplaties (les tables en « a.b ») */
    public Map<String, String> ext() {
        return ext;
    }

    /** @return les ensembles de sources déclarés */
    public List<SourceSetDecl> sourceSets() {
        return sourceSets;
    }

    /** @return les appels et affectations de premier niveau non mappés */
    public List<String> unmapped() {
        return unmapped;
    }

    @Override
    public String toString() {
        return "GradleBuildFile[" + plugins.size() + " plugins, "
                + dependencies.size() + " dependencies]";
    }
}
