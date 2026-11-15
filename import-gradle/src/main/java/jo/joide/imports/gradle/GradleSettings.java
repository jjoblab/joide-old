package jo.joide.imports.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jo.joide.imports.gradle.script.Script;
import jo.joide.imports.gradle.script.ScriptExpr;
import jo.joide.imports.gradle.script.ScriptParser;
import jo.joide.imports.gradle.script.ScriptStmt;
import jo.joide.platform.Problem;

/**
 * Lecture statique d'un fichier {@code settings.gradle(.kts)} : les
 * modules inclus et le nom du projet racine.
 *
 * <p>Les blocs qui ne portent pas de structure de projet (gestion des
 * greffons, des dépôts, buildscript) sont rapportés comme
 * information, jamais ignorés en silence (ADR-040). Aucune
 * exécution, aucune exception (ADR-039).</p>
 */
public final class GradleSettings {

    private final List<String> includes;
    private final Optional<String> rootName;

    private GradleSettings(List<String> includes,
                           Optional<String> rootName) {
        this.includes = includes;
        this.rootName = rootName;
    }

    /** @return des réglages vides (aucun module inclus) */
    public static GradleSettings empty() {
        return new GradleSettings(List.of(), Optional.empty());
    }

    /**
     * Lit le fichier de réglages d'un projet Gradle.
     *
     * @param settingsFile chemin de {@code settings.gradle(.kts)}
     * @param problems     réceptacle des problèmes
     * @return les réglages lus, éventuellement vides
     */
    public static GradleSettings read(Path settingsFile,
                                      List<Problem> problems) {
        String content;
        try {
            content = Files.readString(settingsFile,
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            problems.add(Problem.error("settings-unreadable",
                    settingsFile + " : " + e.getMessage()));
            return new GradleSettings(List.of(), Optional.empty());
        }
        Script script = ScriptParser.parse(content);
        problems.addAll(script.problems());
        List<String> includes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Optional<String> rootName = Optional.empty();
        for (ScriptStmt stmt : script.statements()) {
            if (stmt.kind() == ScriptStmt.Kind.UNKNOWN) {
                problems.add(Problem.info("settings-stmt-unknown",
                        "statement not understood in " + settingsFile
                                + " (line " + stmt.line() + ")"));
                continue;
            }
            if (stmt.isCall("include")) {
                collectIncludes(stmt, includes, seen, problems,
                        settingsFile);
            } else if (stmt.isCall("includeFlat")) {
                problems.add(Problem.warn("settings-include-flat",
                        "includeFlat is not mapped (flat layout) in "
                                + settingsFile));
            } else if (stmt.kind() == ScriptStmt.Kind.ASSIGN
                    && "rootProject.name".equals(stmt.target())) {
                rootName = ScriptStmt.asString(stmt.value());
                if (rootName.isEmpty()) {
                    problems.add(Problem.warn(
                            "settings-root-name-not-literal",
                            "rootProject.name is not a literal in "
                                    + settingsFile));
                }
            } else if (stmt.kind() == ScriptStmt.Kind.CALL
                    && "rootProject".equals(stmt.name())
                    && stmt.block() != null) {
                rootName = stmt.block().findString("name");
            } else if (stmt.kind() == ScriptStmt.Kind.CALL) {
                reportSettingsCall(stmt, settingsFile, problems);
            } else {
                problems.add(Problem.info("settings-stmt-skipped",
                        "statement not mapped in " + settingsFile
                                + " (line " + stmt.line() + ")"));
            }
        }
        return new GradleSettings(includes, rootName);
    }

    private static void collectIncludes(ScriptStmt stmt,
                                        List<String> includes,
                                        Set<String> seen,
                                        List<Problem> problems,
                                        Path settingsFile) {
        for (ScriptExpr arg : stmt.args()) {
            String path = null;
            if (arg instanceof ScriptExpr.Literal literal) {
                path = literal.decoded();
            } else if (arg instanceof ScriptExpr.ListExpr list
                    && !list.items().isEmpty()) {
                for (ScriptExpr item : list.items()) {
                    if (item instanceof ScriptExpr.Literal literal) {
                        addInclude(literal.decoded(), includes, seen,
                                problems, settingsFile);
                    }
                }
                continue;
            }
            if (path == null) {
                problems.add(Problem.warn("settings-include-not-literal",
                        "include argument is not a literal in "
                                + settingsFile + " (line " + stmt.line()
                                + ")"));
                continue;
            }
            addInclude(path, includes, seen, problems, settingsFile);
        }
    }

    private static void addInclude(String raw, List<String> includes,
                                   Set<String> seen,
                                   List<Problem> problems,
                                   Path settingsFile) {
        String path = raw.startsWith(":") ? raw : ":" + raw;
        if (path.length() < 2 || !path.matches("(:[A-Za-z0-9_-]+)+")) {
            problems.add(Problem.warn("settings-include-invalid",
                    "include path not mappable : '" + raw + "' in "
                            + settingsFile));
            return;
        }
        if (seen.add(path)) {
            includes.add(path);
        }
    }

    private static void reportSettingsCall(ScriptStmt stmt,
                                           Path settingsFile,
                                           List<Problem> problems) {
        String name = stmt.qualifiedName();
        switch (name) {
            case "pluginManagement", "dependencyResolutionManagement",
                    "buildscript", "plugins", "repositoryHandler",
                    "enableFeaturePreview", "storage" ->
                    problems.add(Problem.info("settings-block-not-mapped",
                            name + " block not mapped in " + settingsFile
                                    + " (repositories and plugin"
                                    + " management are not part of the"
                                    + " project structure)"));
            default ->
                    problems.add(Problem.info("settings-call-skipped",
                            "call '" + name + "' not mapped in "
                                    + settingsFile + " (line "
                                    + stmt.line() + ")"));
        }
    }

    /** @return les chemins de modules inclus, dans l'ordre */
    public List<String> includes() {
        return includes;
    }

    /** @return le nom du projet racine, s'il est lisible */
    public Optional<String> rootName() {
        return rootName;
    }

    @Override
    public String toString() {
        return "GradleSettings[" + includes.size() + " includes]";
    }
}
