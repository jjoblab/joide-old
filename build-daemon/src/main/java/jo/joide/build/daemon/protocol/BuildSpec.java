package jo.joide.build.daemon.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jo.joide.deps.json.JsonValue;

/**
 * Description d'un build à exécuter par le démon : espaces (projet,
 * sorties, bibliothèques), modules (JVM ou Android) et variantes.
 * La description voyage en JSON ; les valeurs sont lues par la
 * fabrique adéquate côté démon.
 */
public final class BuildSpec {

    /** Type de pipeline. */
    public enum Kind { /** Pipeline JVM pur. */ JVM,
                        /** Pipeline Android complet. */ ANDROID }

    /** Type de pipeline. */
    public final Kind kind;
    /** Répertoire racine du projet. */
    public final String project;
    /** Répertoire des sorties (ancre $OUT). */
    public final String out;
    /** Répertoire des bibliothèques (ancre $M2). */
    public final String m2;
    /** Niveau de source (« 17 »). */
    public final String sourceLevel;
    /** Fils de construction plafonnés. */
    public final int threads;
    /** Tas demandé pour l'admission (Mio). */
    public final int heapMb;
    /** Modules à construire, dans l'ordre de déclaration. */
    public final List<ModuleSpec> modules;

    /** Un module du build. */
    public static final class ModuleSpec {
        /** Nom unique. */
        public final String name;
        /** Module d'application Android. */
        public final boolean application;
        /** Racines de sources (chemins relatifs au projet). */
        public final List<String> sources;
        /** Racines de ressources Android. */
        public final List<String> resources;
        /** Répertoire d'actifs. */
        public final String assets;
        /** Manifeste Android (chemin relatif). */
        public final String manifest;
        /** Dépendances de modules (noms). */
        public final List<String> moduleDependencies;
        /** Jars externes (chemins absolus). */
        public final List<String> externalJars;
        /** Répertoires d'AAR extraits (chemins absolus). */
        public final List<String> externalAarDirs;

        /** Construit un module du build.
         *
         * @param name              nom unique
         * @param application       module d'application Android
         * @param sources           racines de sources (chemins relatifs)
         * @param resources         racines de ressources Android
         * @param assets            répertoire d'actifs, ou {@code null}
         * @param manifest          manifeste Android (chemin relatif)
         * @param moduleDependencies dépendances de modules (noms)
         * @param externalJars      jars externes (chemins absolus)
         * @param externalAarDirs   répertoires d'AAR extraits
         */
        public ModuleSpec(String name, boolean application,
                          List<String> sources, List<String> resources,
                          String assets, String manifest,
                          List<String> moduleDependencies,
                          List<String> externalJars,
                          List<String> externalAarDirs) {
            this.name = name;
            this.application = application;
            this.sources = sources;
            this.resources = resources;
            this.assets = assets;
            this.manifest = manifest;
            this.moduleDependencies = moduleDependencies;
            this.externalJars = externalJars;
            this.externalAarDirs = externalAarDirs;
        }
    }

    /** Variantes Android (ignorées en JVM). */
    public final List<Map<String, Object>> variants;

    /** Construit une description de build.
     *
     * @param kind        type de pipeline
     * @param project     répertoire racine du projet
     * @param out         répertoire des sorties (ancre $OUT)
     * @param m2          répertoire des bibliothèques (ancre $M2)
     * @param sourceLevel niveau de source (« 17 »)
     * @param threads     fils de construction plafonnés
     * @param heapMb      tas demandé pour l'admission (Mio)
     * @param modules     modules à construire, dans l'ordre de déclaration
     * @param variants    variantes Android (ignorées en JVM)
     */
    public BuildSpec(Kind kind, String project, String out, String m2,
                     String sourceLevel, int threads, int heapMb,
                     List<ModuleSpec> modules,
                     List<Map<String, Object>> variants) {
        this.kind = kind;
        this.project = project;
        this.out = out;
        this.m2 = m2;
        this.sourceLevel = sourceLevel;
        this.threads = threads;
        this.heapMb = heapMb;
        this.modules = modules;
        this.variants = variants;
    }

    /**
     * @return la description en objet JSON sérialisable
     */
    public Map<String, Object> toJson() {
        List<Object> moduleList = new ArrayList<>();
        for (ModuleSpec module : modules) {
            // un module est un objet imbriqué, pas du texte : la
            // valeur Map est écrite comme objet par l'écrivain JSON
            Map<String, Object> serialized = new java.util.LinkedHashMap<>();
            serialized.put("name", module.name);
            serialized.put("application", module.application);
            serialized.put("sources", module.sources);
            serialized.put("resources", module.resources);
            serialized.put("assets", module.assets);
            serialized.put("manifest", module.manifest);
            serialized.put("deps", module.moduleDependencies);
            serialized.put("externalJars", module.externalJars);
            serialized.put("aarDirs", module.externalAarDirs);
            moduleList.add(serialized);
        }
        Map<String, Object> spec = new java.util.LinkedHashMap<>();
        spec.put("kind", kind == Kind.ANDROID ? "android" : "jvm");
        spec.put("project", project);
        spec.put("out", out);
        spec.put("m2", m2);
        spec.put("sourceLevel", sourceLevel);
        spec.put("threads", threads);
        spec.put("heapMb", heapMb);
        spec.put("modules", moduleList);
        spec.put("variants", variants);
        return spec;
    }

    /**
     * Lit une description depuis un objet JSON analysé.
     *
     * @param spec objet JSON du champ « spec »
     * @return la description
     * @throws IllegalArgumentException si un champ obligatoire manque
     */
    public static BuildSpec fromJson(JsonValue spec) {
        Kind kind = "android".equals(spec.member("kind").asString())
                ? Kind.ANDROID : Kind.JVM;
        List<ModuleSpec> modules = new ArrayList<>();
        for (JsonValue element : spec.member("modules").items()) {
            String name = element.member("name").asString();
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException(
                        "module without name at index " + modules.size());
            }
            modules.add(new ModuleSpec(
                    name,
                    element.member("application").asBoolean() != null
                            && element.member("application").asBoolean(),
                    strings(element.member("sources")),
                    strings(element.member("resources")),
                    nullableText(element.member("assets")),
                    nullableText(element.member("manifest")),
                    strings(element.member("deps")),
                    strings(element.member("externalJars")),
                    strings(element.member("aarDirs"))));
        }
        List<Map<String, Object>> variants = new ArrayList<>();
        for (JsonValue variant : spec.member("variants").items()) {
            Map<String, Object> copy = new java.util.LinkedHashMap<>();
            for (String key : variant.keys()) {
                JsonValue value = variant.member(key);
                copy.put(key, switch (value.type()) {
                    case STRING -> value.asString();
                    case NUMBER -> value.asLong();
                    case BOOLEAN -> value.asBoolean();
                    default -> null;
                });
            }
            variants.add(copy);
        }
        return new BuildSpec(kind, spec.member("project").asString(),
                spec.member("out").asString(),
                spec.member("m2").asString(),
                nullableText(spec.member("sourceLevel")) == null ? "17"
                        : spec.member("sourceLevel").asString(),
                spec.member("threads").asLong() < 0 ? 4
                        : (int) spec.member("threads").asLong(),
                spec.member("heapMb").asLong() < 0 ? 512
                        : (int) spec.member("heapMb").asLong(),
                modules, variants);
    }

    private static String nullableText(JsonValue value) {
        return value.isNull() ? null : value.asString();
    }

    private static List<String> strings(JsonValue array) {
        List<String> values = new ArrayList<>();
        for (JsonValue element : array.items()) {
            if (!element.isNull()) {
                values.add(element.asString());
            }
        }
        return values;
    }
}
