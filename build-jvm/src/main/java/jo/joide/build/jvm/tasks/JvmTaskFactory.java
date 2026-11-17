package jo.joide.build.jvm.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jo.joide.build.jvm.abi.AbiCache;
import jo.joide.build.jvm.abi.AbiManifest;
import jo.joide.build.jvm.compile.CompilerDriver;
import jo.joide.build.jvm.compile.EcjCompilerDriver;
import jo.joide.build.jvm.compile.KotlinCompilerDriver;
import jo.joide.platform.Problem;
import jo.joide.platform.RootName;
import jo.joide.platform.RootSet;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.TaskGraph;

/**
 * Fabrique du graphe de tâches JVM d'un espace de modules : pour
 * chaque module, compileKotlin puis compileJava (compilation jointe :
 * le compilateur Kotlin lit les sources Java, le compilateur Java
 * compile contre les sorties Kotlin), processResources,
 * generateSources et jar ; les compilations aval dépendent des
 * compilations amont dont elles consomment les manifestes ABI.
 *
 * <p>La fabrique est pure : elle construit des définitions, n'exécute
 * rien. Les chemins de sortie sont ancrés à la racine nommée
 * {@code $OUT} — le graphe entier est relocalisable.</p>
 */
public final class JvmTaskFactory {

    private final RootName outRoot;
    private final Path buildDir;
    private final Map<String, ModuleSpec> modules = new LinkedHashMap<>();
    private final AbiCache abiCache;
    private final CompilerDriver javaDriver;
    // lot VII : injectable (withKotlinDriver) — embarqué ou VM forkée
    private CompilerDriver kotlinDriver;
    private final String sourceLevel;
    private final Map<String, Boolean> kotlinPresence = new LinkedHashMap<>();
    private RootSet sourceAnchors;
    private final Map<String, Boolean> forcedKotlin = new LinkedHashMap<>();

    /** Description d'un module à construire. */
    public static final class ModuleSpec {
        /** Nom unique du module. */
        public final String name;
        /** Racines de sources (Java et Kotlin mélangés, scannés par
         *  extension). */
        public final List<RootedPath> sourceRoots = new ArrayList<>();
        /** Racines de ressources. */
        public final List<RootedPath> resourceRoots = new ArrayList<>();
        /** Noms des modules dont celui-ci dépend (ordre de classpath). */
        public final List<String> moduleDependencies = new ArrayList<>();
        /** Jars externes (bibliothèques), chemins absolus. */
        public final List<Path> externalJars = new ArrayList<>();
        /** Générateurs de sources injectés. */
        public final List<SourceGenerator> generators = new ArrayList<>();
        /** Classe principale (tâche run), ou {@code null}. */
        public String mainClass;

        /** @param name nom unique du module */
        public ModuleSpec(String name) {
            this.name = name;
        }
    }

    private final RootName m2Root = RootName.M2;
    private final Path m2Dir;

    /**
     * @param buildDir    répertoire racine des sorties (ancre $OUT)
     * @param m2Dir       répertoire des bibliothèques externes (ancre
     *                    $M2) — les jars y sont copiés par la
     *                    résolution de dépendances (P4) ou le câbleur
     * @param sourceLevel niveau de source/cible (« 17 »)
     */
    public JvmTaskFactory(Path buildDir, Path m2Dir, String sourceLevel) {
        this.outRoot = RootName.OUT;
        this.buildDir = buildDir.toAbsolutePath().normalize();
        this.m2Dir = m2Dir.toAbsolutePath().normalize();
        this.abiCache = new AbiCache(buildDir.resolve("jvm/abi-cache"));
        this.javaDriver = new EcjCompilerDriver();
        // lot VII : le pilote Kotlin s'INJECTE (BuildRunner : embarqué
        // d'abord, VM forkée ensuite) — l'instanciation paresseuse ne
        // sert qu'aux usages de bureau où le jar est sur le classpath
        this.sourceLevel = sourceLevel;
    }

    /**
     * @param kotlinDriver le pilote Kotlin à utiliser (embarqué ou
     *                     VM forkée — lot VII)
     * @return cette fabrique
     */
    public JvmTaskFactory withKotlinDriver(CompilerDriver kotlinDriver) {
        this.kotlinDriver = kotlinDriver;
        return this;
    }

    /** Le pilote Kotlin, résolu paresseusement (embarqué). */
    private CompilerDriver kotlinDriver() {
        if (kotlinDriver == null) {
            kotlinDriver = new KotlinCompilerDriver();
        }
        return kotlinDriver;
    }

    /** @return le nom de la racine des sorties */
    public RootName outRootName() {
        return outRoot;
    }

    /**
     * @param spec module à ajouter
     * @return cette fabrique
     */
    public JvmTaskFactory module(ModuleSpec spec) {
        modules.put(spec.name, spec);
        return this;
    }

    /** Ancres ($OUT, $M2) pour construire le RootSet du build. */
    public Map<RootName, Path> anchors() {
        return Map.of(outRoot, buildDir, m2Root, m2Dir);
    }

    /**
     * Fournit les ancres des racines sources ($PROJECT…) : sans
     * elles, la présence de Kotlin est décidée par le câbleur.
     *
     * @param roots ancres des racines du build
     * @return cette fabrique
     */
    public JvmTaskFactory withSourceAnchors(RootSet roots) {
        this.sourceAnchors = roots;
        return this;
    }

    /**
     * Force la présence (ou l'absence) de sources Kotlin d'un module.
     *
     * @param module    nom du module
     * @param hasKotlin vrai si le module contient des sources Kotlin
     * @return cette fabrique
     */
    public JvmTaskFactory forceKotlin(String module, boolean hasKotlin) {
        forcedKotlin.put(module, hasKotlin);
        return this;
    }

    /**
     * Construit le graphe de tâches complet.
     *
     * @return le graphe enregistré
     * @throws IOException si un manifeste externe ne peut être calculé
     */
    public TaskGraph buildGraph() throws IOException {
        abiCache.load();
        TaskGraph.Builder graph = TaskGraph.builder();
        for (ModuleSpec spec : modules.values()) {
            registerModule(graph, spec);
        }
        return graph.build();
    }

    private void registerModule(TaskGraph.Builder graph, ModuleSpec spec)
            throws IOException {
        RootedPath moduleDir = RootedPath.of(outRoot, "jvm/" + spec.name);
        RootedPath classesJava = moduleDir.resolve("classes-java");
        RootedPath classesKotlin = moduleDir.resolve("classes-kotlin");
        RootedPath abiJava = moduleDir.resolve("abi-java");
        RootedPath abiKotlin = moduleDir.resolve("abi-kotlin");
        RootedPath resources = moduleDir.resolve("resources");
        RootedPath jarFile = moduleDir.resolve("libs")
                .resolve(spec.name + ".jar");

        boolean hasKotlin = kotlinPresence.computeIfAbsent(spec.name,
                name -> detectKotlin(spec));
        String compileJava = spec.name + ":compileJava";
        String compileKotlin = spec.name + ":compileKotlin";
        String jarTask = spec.name + ":jar";

        List<ClasspathEntry> upstreamJava = new ArrayList<>();
        List<ClasspathEntry> upstreamKotlin = new ArrayList<>();
        for (String dependency : spec.moduleDependencies) {
            ModuleSpec dep = modules.get(dependency);
            if (dep == null) {
                continue;
            }
            RootedPath depDir = RootedPath.of(outRoot,
                    "jvm/" + dependency);
            boolean depKotlin = kotlinPresence.computeIfAbsent(dependency,
                    name -> detectKotlin(dep));
            for (List<ClasspathEntry> target
                    : List.of(upstreamJava, upstreamKotlin)) {
                target.add(new ClasspathEntry(ClasspathEntry.Kind.MODULE_JAVA,
                        dependency + ":java",
                        depDir.resolve("classes-java"),
                        depDir.resolve("abi-java").resolve("current"),
                        depDir.resolve("abi-java")));
                if (depKotlin) {
                    target.add(new ClasspathEntry(
                            ClasspathEntry.Kind.MODULE_KOTLIN,
                            dependency + ":kotlin",
                            depDir.resolve("classes-kotlin"),
                            depDir.resolve("abi-kotlin").resolve("current"),
                            depDir.resolve("abi-kotlin")));
                }
            }
            graph.dependsOn(compileJava, dependency + ":compileJava");
            if (depKotlin) {
                graph.dependsOn(compileJava,
                        dependency + ":compileKotlin");
            }
            if (hasKotlin) {
                graph.dependsOn(compileKotlin,
                        dependency + ":compileJava");
                if (depKotlin) {
                    graph.dependsOn(compileKotlin,
                            dependency + ":compileKotlin");
                }
            }
        }
        for (Path jar : spec.externalJars) {
            AbiManifest manifest = abiCache.manifestOf(jar);
            String label = "ext:" + jar.getFileName();
            RootedPath manifestFile = RootedPath.of(outRoot, "jvm")
                    .resolve("abi-cache")
                    .resolve(manifest.digest() + ".manifest");
            ClasspathEntry entry = new ClasspathEntry(
                    ClasspathEntry.Kind.EXTERNAL, label, anchored(jar),
                    manifestFile, null);
            upstreamJava.add(entry);
            upstreamKotlin.add(entry);
        }

        RootedPath javaStateFile = RootedPath.of(outRoot, "jvm")
                .resolve(spec.name).resolve("classes-java")
                .resolve("compile-state.bin");
        if (hasKotlin) {
            List<ClasspathEntry> javaWithOwnKotlin = new ArrayList<>(
                    upstreamJava);
            javaWithOwnKotlin.add(new ClasspathEntry(
                    ClasspathEntry.Kind.MODULE_KOTLIN,
                    spec.name + ":kotlin", classesKotlin,
                    abiKotlin.resolve("current"), abiKotlin));
            // bureau : le JDK réel fournit les racines java.* —
            // PAS de -no-jdk (la manière CodeAssist ne concerne que
            // la compilation Android, lot XIV)
            graph.register(JvmCompileTask.create(spec.name, "kotlin",
                    spec.sourceRoots, upstreamKotlin, kotlinDriver(),
                    spec.sourceRoots, classesKotlin, abiKotlin,
                    javaStateFile, sourceLevel, false));
            graph.register(JvmCompileTask.create(spec.name, "java",
                    spec.sourceRoots, javaWithOwnKotlin, javaDriver,
                    List.of(), classesJava, abiJava, null, sourceLevel,
                    false));
            graph.dependsOn(compileJava, compileKotlin);
        } else {
            graph.register(JvmCompileTask.create(spec.name, "java",
                    spec.sourceRoots, upstreamJava, javaDriver,
                    List.of(), classesJava, abiJava, null, sourceLevel,
                    false));
        }
        if (!spec.generators.isEmpty()) {
            graph.register(new GenerateSourcesTask(spec.name,
                    spec.generators,
                    moduleDir.resolve("generated")));
            graph.dependsOn(compileJava, spec.name + ":generateSources");
            if (hasKotlin) {
                graph.dependsOn(compileKotlin,
                        spec.name + ":generateSources");
            }
        }
        List<RootedPath> jarClasses = new ArrayList<>();
        jarClasses.add(classesJava);
        if (hasKotlin) {
            jarClasses.add(classesKotlin);
        }
        List<RootedPath> jarResources = List.of();
        if (!spec.resourceRoots.isEmpty()) {
            jarResources = List.of(resources);
            graph.register(new ProcessResourcesTask(spec.name,
                    spec.resourceRoots, resources));
            graph.dependsOn(jarTask, spec.name + ":processResources");
        }
        graph.register(new JarTask(spec.name, jarClasses, jarResources,
                jarFile));
        graph.dependsOn(jarTask, compileJava);
        if (hasKotlin) {
            graph.dependsOn(jarTask, compileKotlin);
        }
        if (spec.mainClass != null) {
            List<RootedPath> runClasspath = new ArrayList<>(jarClasses);
            for (String dependency : spec.moduleDependencies) {
                ModuleSpec dep = modules.get(dependency);
                if (dep == null) {
                    continue;
                }
                RootedPath depDir = RootedPath.of(outRoot,
                        "jvm/" + dependency);
                runClasspath.add(depDir.resolve("classes-java"));
                if (kotlinPresence.computeIfAbsent(dependency,
                        name -> detectKotlin(dep))) {
                    runClasspath.add(depDir.resolve("classes-kotlin"));
                }
            }
            for (Path jar : spec.externalJars) {
                runClasspath.add(anchored(jar));
            }
            graph.register(new JvmRunTask(spec.name, spec.mainClass,
                    List.of(), runClasspath,
                    moduleDir.resolve("run.log")));
        }
    }

    /**
     * Présence de sources Kotlin : valeur forcée par le câbleur, ou
     * balayage des racines lorsque les ancres sources sont fournies.
     */
    private boolean detectKotlin(ModuleSpec spec) {
        Boolean forced = forcedKotlin.get(spec.name);
        if (forced != null) {
            return forced;
        }
        if (sourceAnchors == null) {
            return false;
        }
        for (RootedPath root : spec.sourceRoots) {
            Path dir;
            try {
                dir = sourceAnchors.resolve(root);
            } catch (RuntimeException e) {
                continue;
            }
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (var stream = Files.walk(dir)) {
                if (stream.anyMatch(p -> p.getFileName().toString()
                        .endsWith(".kt"))) {
                    return true;
                }
            } catch (IOException e) {
                // balayage impossible : absence retenue (sur-approx
                // impossible ; le câbleur peut forcer la valeur)
            }
        }
        return false;
    }

    /**
     * Traduit un chemin absolu en chemin ancré : $OUT pour les
     * sorties du build, $M2 pour les bibliothèques externes.
     */
    private RootedPath anchored(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(buildDir)) {
            return RootedPath.of(outRoot,
                    buildDir.relativize(normalized).toString());
        }
        if (normalized.startsWith(m2Dir)) {
            return RootedPath.of(m2Root,
                    m2Dir.relativize(normalized).toString());
        }
        throw new IllegalArgumentException(
                "path outside build roots: " + path
                        + " (expected under " + buildDir + " or "
                        + m2Dir + ")");
    }

    /** @return les problèmes de câblage détectés (modules inconnus) */
    public List<Problem> wiringProblems() {
        List<Problem> problems = new ArrayList<>();
        for (ModuleSpec spec : modules.values()) {
            for (String dependency : spec.moduleDependencies) {
                if (!modules.containsKey(dependency)) {
                    problems.add(Problem.error("unknown-module",
                            "module " + spec.name + " depends on unknown "
                                    + dependency));
                }
            }
        }
        return problems;
    }
}
