package jo.joide.imports.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import jo.joide.model.ContentRole;
import jo.joide.model.DependencyRef;
import jo.joide.model.DependencyScope;
import jo.joide.model.Facet;
import jo.joide.model.Module;
import jo.joide.model.SourceSet;
import jo.joide.model.Workspace;

/**
 * Export d'un espace de travail JoIDE vers un projet Gradle :
 * {@code settings.gradle.kts}, un {@code build.gradle.kts} par module,
 * {@code gradle.properties}, et {@code EXPORT-NOTES.md} pour ce qui
 * n'a pas d'équivalent fidèle.
 *
 * <p>L'export est déterministe : deux exports du même modèle
 * produisent des octets identiques (ADR-041). L'aller-retour
 * import(export(modèle)) ≡ modèle tient sur le sous-ensemble
 * représentable ; les limites sont listées dans EXPORT-NOTES.md.</p>
 */
public final class GradleExporter {

    /** Résultat d'un export : fichiers par chemin relatif. */
    public static final class ExportResult {

        private final Map<String, String> files;

        /**
         * @param files fichiers produits, par chemin relatif
         */
        public ExportResult(Map<String, String> files) {
            this.files = files;
        }

        /** @return les fichiers produits, par chemin relatif */
        public Map<String, String> files() {
            return files;
        }

        /** @return le contenu d'EXPORT-NOTES.md, ou une chaîne vide */
        public String notes() {
            return files.getOrDefault("EXPORT-NOTES.md", "");
        }
    }

    private GradleExporter() {
    }

    /**
     * Exporte un espace de travail en mémoire.
     *
     * @param workspace espace de travail JoIDE
     * @return les fichiers produits, par chemin relatif à la racine
     */
    public static ExportResult export(Workspace workspace) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("settings.gradle.kts", settingsFile(workspace));
        files.put("gradle.properties", propertiesFile(workspace));
        boolean useAndroidX = workspace.allModules().values().stream()
                .anyMatch(module -> module.type().id().startsWith(
                        "android-"));
        if (useAndroidX) {
            files.put("gradle.properties",
                    files.get("gradle.properties")
                            + "android.useAndroidX=true\n");
        }
        List<String> notes = new ArrayList<>();
        for (Module module : workspace.allModules().values()) {
            String relative = module.path().substring(1).replace(':',
                    '/') + "/build.gradle.kts";
            files.put(relative, moduleFile(module, workspace, notes));
        }
        files.put("EXPORT-NOTES.md", notesFile(workspace, notes));
        return new ExportResult(files);
    }

    /**
     * Écrit un export sur le disque.
     *
     * @param result    export produit par {@link #export}
     * @param targetDir répertoire racine cible (créé s'il faut)
     * @throws IOException si l'écriture échoue
     */
    public static void write(ExportResult result, Path targetDir)
            throws IOException {
        for (Map.Entry<String, String> file : result.files().entrySet()) {
            Path target = targetDir.resolve(file.getKey());
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, file.getValue(),
                    StandardCharsets.UTF_8);
        }
    }

    /**
     * Vrai si un greffon com.android.* est déclaré avec une version
     * majeure au moins {@code major} dans la facette gradle.
     */
    private static boolean agpMajorAtLeast(Facet gradleFacet, int major) {
        for (String id : List.of("com.android.application",
                "com.android.library")) {
            String version = gradleFacet == null ? null
                    : gradleFacet.entry("plugin-" + id);
            if (version == null) {
                continue;
            }
            try {
                if (Integer.parseInt(version.split("[.-]")[0])
                        >= major) {
                    return true;
                }
            } catch (NumberFormatException e) {
                // version illisible : non décisive
            }
        }
        return false;
    }

    private static String settingsFile(Workspace workspace) {
        boolean android = workspace.allModules().values().stream()
                .anyMatch(module -> module.type().id().startsWith(
                        "android-"));
        StringBuilder out = new StringBuilder(256);
        out.append("// Genere par l'export JoIDE — ne pas editer.\n");
        if (android) {
            out.append("pluginManagement {\n    repositories {\n"
                    + "        google()\n        mavenCentral()\n"
                    + "        gradlePluginPortal()\n    }\n}\n\n");
        }
        out.append("rootProject.name = \"")
                .append(escape(workspace.name())).append("\"\n\n");
        out.append("dependencyResolutionManagement {\n"
                + "    repositories {\n");
        if (android) {
            out.append("        google()\n");
        }
        out.append("        mavenCentral()\n    }\n}\n");
        if (!workspace.allModules().isEmpty()) {
            out.append('\n');
            List<String> includes = new ArrayList<>(
                    workspace.allModules().keySet());
            out.append("include(");
            for (int i = 0; i < includes.size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append("\"").append(includes.get(i)).append("\"");
            }
            out.append(")\n");
        }
        return out.toString();
    }

    private static String propertiesFile(Workspace workspace) {
        return "# Genere par l'export JoIDE — ne pas editer.\n";
    }

    private static String moduleFile(Module module, Workspace workspace,
                                     List<String> notes) {
        StringBuilder out = new StringBuilder(512);
        out.append("// Genere par l'export JoIDE — ne pas editer.\n");
        writePlugins(module, out);
        boolean android = module.type().id().startsWith("android-");
        Facet androidFacet = module.facets().get("android");
        if (android && androidFacet != null) {
            writeAndroidBlock(androidFacet, workspace, out, notes);
        }
        writeSourceSets(module, out, notes);
        for (Map.Entry<String, Facet> facet
                : module.facets().entrySet()) {
            if (!facet.getKey().equals("android")
                    && !facet.getKey().equals("kotlin")) {
                notes.add("module " + module.path() + " : facette '"
                        + facet.getKey() + "' sans equivalent Gradle"
                        + " (entrees : " + facet.getValue().entries()
                        + ")");
            }
        }
        writeDependencies(module, androidFacet, out);
        return out.toString();
    }

    private static void writePlugins(Module module, StringBuilder out) {
        Facet gradleFacet = module.facets().get("gradle");
        out.append("plugins {\n");
        List<String> ids = new ArrayList<>();
        switch (module.type().id()) {
            case "android-application" ->
                    ids.add("com.android.application");
            case "android-library" -> ids.add("com.android.library");
            case "jvm-application" -> ids.add("application");
            case "kotlin-multiplatform" ->
                    ids.add("org.jetbrains.kotlin.multiplatform");
            default -> ids.add("java-library");
        }
        if (module.facets().containsKey("kotlin")
                && !(module.type().id().startsWith("android-")
                        && agpMajorAtLeast(gradleFacet, 9))) {
            // AGP 9 intègre le support Kotlin et refuse le greffon
            // org.jetbrains.kotlin.android (ADR-041)
            ids.add(module.type().id().startsWith("android-")
                    ? "org.jetbrains.kotlin.android"
                    : "org.jetbrains.kotlin.jvm");
        }
        for (String id : ids) {
            out.append("    id(\"").append(id).append("\")");
            String version = gradleFacet == null ? null
                    : gradleFacet.entry("plugin-" + id);
            if (version != null) {
                out.append(" version \"").append(escape(version))
                        .append("\"");
            }
            out.append('\n');
        }
        if (gradleFacet != null) {
            // les autres greffons portés par le modèle (compilateur
            // Compose, sérialisation…) suivent, avec leur version
            for (Map.Entry<String, String> plugin
                    : gradleFacet.entries().entrySet()) {
                if (!plugin.getKey().startsWith("plugin-")) {
                    continue;
                }
                String id = plugin.getKey().substring("plugin-".length());
                if (ids.contains(id)) {
                    continue;
                }
                out.append("    id(\"").append(escape(id)).append("\")")
                        .append(" version \"")
                        .append(escape(plugin.getValue())).append("\"\n");
            }
        }
        out.append("}\n\n");
    }

    private static void writeAndroidBlock(Facet facet, Workspace workspace,
                                          StringBuilder out,
                                          List<String> notes) {
        out.append("android {\n");
        appendProperty(out, "    namespace", facet.entry("namespace"));
        appendProperty(out, "    compileSdk", facet.entry("compile-sdk"));
        String defaultMinSdk = facet.entry("min-sdk");
        if (defaultMinSdk == null) {
            defaultMinSdk = workspace.setting("android.min-sdk")
                    .orElse(null);
            if (defaultMinSdk != null) {
                notes.add("module min-sdk absent : la valeur de l'espace"
                        + " (android.min-sdk=" + defaultMinSdk
                        + ") est distribuee au module");
            }
        }
        boolean hasDefaults = facet.entry("application-id") != null
                || defaultMinSdk != null
                || facet.entry("target-sdk") != null
                || facet.entry("version-code") != null
                || facet.entry("version-name") != null;
        if (hasDefaults) {
            out.append("    defaultConfig {\n");
            appendProperty(out, "        applicationId",
                    facet.entry("application-id"));
            appendProperty(out, "        minSdk", defaultMinSdk);
            appendProperty(out, "        targetSdk",
                    facet.entry("target-sdk"));
            appendProperty(out, "        versionCode",
                    facet.entry("version-code"));
            appendProperty(out, "        versionName",
                    facet.entry("version-name"));
            out.append("    }\n");
        }
        String buildTypes = facet.entry("build-types");
        if (buildTypes != null) {
            out.append("    buildTypes {\n");
            for (String type : buildTypes.split(",")) {
                out.append("        ").append(type).append(" {\n");
                if ("true".equals(facet.entry("minify-" + type))) {
                    out.append("            isMinifyEnabled = true\n");
                }
                out.append("        }\n");
            }
            out.append("    }\n");
        }
        String flavors = facet.entry("flavors");
        if (flavors != null) {
            out.append("    productFlavors {\n");
            for (String flavor : flavors.split(",")) {
                out.append("        create(\"").append(flavor)
                        .append("\") {\n");
                String dimension = facet.entry("dimension-" + flavor);
                if (dimension != null) {
                    out.append("            dimension = \"")
                            .append(escape(dimension)).append("\"\n");
                }
                out.append("        }\n");
            }
            out.append("    }\n");
        }
        if ("true".equals(facet.entry("compose"))) {
            out.append("    buildFeatures {\n        compose = true\n"
                    + "    }\n");
        }
        String manifest = facet.entry("manifest");
        if (manifest != null
                && !"src/main/AndroidManifest.xml".equals(manifest)) {
            out.append("    sourceSets {\n        main {\n"
                    + "            manifest.srcFile(\"")
                    .append(escape(manifest)).append("\")\n"
                    + "        }\n    }\n");
        }
        out.append("}\n\n");
    }

    private static void writeSourceSets(Module module, StringBuilder out,
                                       List<String> notes) {
        boolean kotlin = module.facets().containsKey("kotlin");
        boolean android = module.type().id().startsWith("android-");
        StringBuilder sets = new StringBuilder();
        for (Map.Entry<String, SourceSet> entry
                : module.sourceSets().entrySet()) {
            Map<String, List<String>> byRole = new TreeMap<>();
            for (var root : entry.getValue().roots()) {
                byRole.computeIfAbsent(root.role().id(),
                        k -> new ArrayList<>()).add(root.path());
            }
            Map<String, List<String>> defaults =
                    defaultRoots(entry.getKey(), android, kotlin);
            if (byRole.equals(defaults)) {
                continue;
            }
            sets.append("    ").append(entry.getKey()).append(" {\n");
            Map<String, List<String>> all = new TreeMap<>(defaults);
            all.putAll(byRole);
            for (Map.Entry<String, List<String>> role
                    : all.entrySet()) {
                String container = containerOfRole(role.getKey());
                if (container == null) {
                    if (byRole.containsKey(role.getKey())) {
                        notes.add("module " + module.path() + " : role de"
                                + " contenu sans equivalent Gradle : "
                                + role.getKey());
                    }
                    continue;
                }
                List<String> dirs = byRole.getOrDefault(role.getKey(),
                        List.of());
                sets.append("        ").append(container)
                        .append(".setSrcDirs(");
                if (dirs.isEmpty()) {
                    // listOf() vide : le type doit être explicite en
                    // Kotlin, sinon l'inférence échoue
                    sets.append("listOf<String>()");
                } else {
                    sets.append("listOf(");
                    for (int i = 0; i < dirs.size(); i++) {
                        if (i > 0) {
                            sets.append(", ");
                        }
                        sets.append("\"").append(escape(dirs.get(i)))
                                .append("\"");
                    }
                    sets.append(")");
                }
                sets.append(")\n");
            }
            sets.append("    }\n");
        }
        if (!sets.isEmpty()) {
            out.append("sourceSets {\n").append(sets).append("}\n\n");
        }
    }

    private static Map<String, List<String>> defaultRoots(String setName,
                                                          boolean android,
                                                          boolean kotlin) {
        Map<String, List<String>> roots = new TreeMap<>();
        List<String> sources = new ArrayList<>();
        sources.add("src/" + setName + "/java");
        if (kotlin) {
            sources.add("src/" + setName + "/kotlin");
        }
        roots.put("source", sources);
        roots.put("resources", List.of("src/" + setName + "/resources"));
        if (android) {
            roots.put("android-res", List.of("src/" + setName + "/res"));
            roots.put("assets", List.of("src/" + setName + "/assets"));
            roots.put("aidl", List.of("src/" + setName + "/aidl"));
            roots.put("jniLibs", List.of("src/" + setName + "/jniLibs"));
        }
        return roots;
    }

    private static String containerOfRole(String role) {
        return switch (role) {
            case "source" -> "java";
            case "resources" -> "resources";
            case "android-res" -> "res";
            case "assets" -> "assets";
            case "aidl" -> "aidl";
            case "jniLibs" -> "jniLibs";
            default -> null;
        };
    }

    private static void writeDependencies(Module module, Facet androidFacet,
                                          StringBuilder out) {
        List<DependencyRef> dependencies = new ArrayList<>(
                module.dependencies());
        String desugar = androidFacet == null ? null
                : androidFacet.entry("desugar-libraries");
        List<String> lines = new ArrayList<>();
        for (DependencyRef dependency : dependencies) {
            lines.add("    " + configurationOf(dependency, androidFacet)
                    + "(" + notationOf(dependency) + ")");
        }
        if (desugar != null) {
            lines.add("    coreLibraryDesugaring(\"" + escape(desugar)
                    + "\")");
        }
        if (!lines.isEmpty()) {
            // l'ordre du modèle est conservé : il fait partie de
            // l'identifiant de contenu et de l'aller-retour
            out.append("dependencies {\n");
            for (String line : lines) {
                out.append(line).append('\n');
            }
            out.append("}\n");
        }
    }

    private static String configurationOf(DependencyRef dependency,
                                          Facet androidFacet) {
        List<String> parts = new ArrayList<>();
        if (dependency.scope().isTest()) {
            parts.add("test");
        }
        List<String> flavors = new ArrayList<>();
        String flavorList = androidFacet == null ? null
                : androidFacet.entry("flavors");
        if (flavorList != null) {
            flavors.addAll(List.of(flavorList.split(",")));
        }
        for (String variant : dependency.variants()) {
            if (flavors.contains(variant)) {
                parts.add(variant);
            }
        }
        for (String variant : dependency.variants()) {
            if (variant.equals("debug") || variant.equals("release")) {
                parts.add(variant);
            }
        }
        String word = scopeWord(dependency.scope());
        if (parts.isEmpty()) {
            return Character.toLowerCase(word.charAt(0))
                    + word.substring(1);
        }
        StringBuilder out = new StringBuilder(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            out.append(Character.toUpperCase(parts.get(i).charAt(0)))
                    .append(parts.get(i).substring(1));
        }
        return out.append(word).toString();
    }

    private static String scopeWord(DependencyScope scope) {
        return switch (scope) {
            case API -> "Api";
            case IMPLEMENTATION -> "Implementation";
            case COMPILE_ONLY -> "CompileOnly";
            case RUNTIME_ONLY -> "RuntimeOnly";
            case TEST_API -> "Api";
            case TEST_IMPLEMENTATION -> "Implementation";
            case TEST_COMPILE_ONLY -> "CompileOnly";
            case TEST_RUNTIME_ONLY -> "RuntimeOnly";
        };
    }

    private static String notationOf(DependencyRef dependency) {
        String target = dependency.isModule()
                ? "project(\"" + dependency.modulePath() + "\")"
                : "\"" + escape(dependency.coordinates()) + "\"";
        return target;
    }

    private static String notesFile(Workspace workspace,
                                    List<String> notes) {
        StringBuilder out = new StringBuilder(256);
        out.append("# EXPORT-NOTES\n\n");
        out.append("Ce que l'export de `").append(workspace.name())
                .append("` ne peut pas representer fidelement"
                        + " dans un projet Gradle :\n\n");
        String minSdk = workspace.setting("android.min-sdk").orElse(null);
        if (minSdk != null) {
            out.append("- Le reglage d'espace `android.min-sdk = ")
                    .append(minSdk).append("` n'a pas d'equivalent"
                            + " racine en Gradle : il est distribue aux")
                    .append(" modules android qui ne declarent pas leur")
                    .append(" propre minSdk.\n");
        }
        for (String note : notes) {
            out.append("- ").append(note).append('\n');
        }
        if (notes.isEmpty() && minSdk == null) {
            out.append("- Rien : toutes les donnees du modele ont un")
                    .append(" equivalent Gradle.\n");
        }
        return out.toString();
    }

    private static void appendProperty(StringBuilder out, String name,
                                       String value) {
        if (value != null && !value.isEmpty()) {
            out.append(name).append(" = ")
                    .append(numberOrString(value)).append('\n');
        }
    }

    private static String numberOrString(String value) {
        return value.matches("[0-9]+") ? value
                : "\"" + escape(value) + "\"";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("$", "\\$");
    }
}
