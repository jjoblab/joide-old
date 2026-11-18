package jo.joide.build.android.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jo.joide.build.android.bundle.BundleAssembler;
import jo.joide.build.android.dex.Dexer;
import jo.joide.build.android.sign.ApkSignerService;
import jo.joide.build.android.toolchain.Toolchain;
import jo.joide.build.jvm.abi.AbiCache;
import jo.joide.build.jvm.abi.AbiManifest;
import jo.joide.build.jvm.compile.CompilerDriver;
import jo.joide.build.jvm.compile.EcjCompilerDriver;
import jo.joide.build.jvm.compile.KotlinCompilerDriver;
import jo.joide.build.jvm.abi.AbiCache;
import jo.joide.build.jvm.tasks.ClasspathEntry;
import jo.joide.build.jvm.tasks.JvmCompileTask;
import jo.joide.platform.RootName;
import jo.joide.platform.RootSet;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.TaskGraph;

/**
 * Fabrique du graphe de tâches Android d'un espace de modules :
 * pour chaque module, compilation aapt2 proportionnelle à la
 * modification, lien, jar des classes R, puis compilation Java/Kotlin
 * incrémentale par ABI (le classpath porte android.jar, le R.jar du
 * module, les sorties des modules amont et les bibliothèques) ; pour
 * le module d'application, par variante : dex par scopes (le dex d'un
 * jar externe est une entrée de cache partageable entre projets),
 * fusion par buckets, rétrécissement R8 si demandé, package, signature
 * et bundle.
 *
 * <p>La fabrique est pure : elle construit des définitions, n'exécute
 * rien. Les chemins de sortie sont ancrés à la racine nommée
 * {@code $OUT}, les bibliothèques à {@code $M2}, l'outillage et la
 * plateforme à {@code $SDK} — le graphe entier est relocalisable.</p>
 */
public final class AndroidTaskFactory {

    private final Path buildDir;
    private final Path m2Dir;
    private final Path toolchainDir;
    private final Toolchain toolchain;
    private final Dexer scopeDexer;
    private final Dexer mergeDexer;
    private final ApkSignerService signer;
    private final BundleAssembler assembler;
    private final String sourceLevel;
    private final Map<String, AndroidModuleSpec> modules =
            new LinkedHashMap<>();
    private final AbiCache abiCache;
    private final CompilerDriver javaDriver = new EcjCompilerDriver();
    // lot VII : plus d'instanciation au CHAMP — le compilateur
    // embarqué peut manquer (APK allégé) ; l'appelant injecte le
    // pilote résolu (BuildRunner : embarqué d'abord, VM forkée
    // ensuite), la résolution paresseuse ne sert qu'aux usages de
    // bureau (tests, démon direct) où le jar est sur le classpath
    private CompilerDriver kotlinDriver;
    private final Map<String, Boolean> kotlinPresence =
            new LinkedHashMap<>();
    private RootSet sourceAnchors;
    private final Map<String, Boolean> forcedKotlin = new LinkedHashMap<>();
    /** Les modules DOTÉS d'un manifeste (donc d'un R.jar) — les
     *  frères JVM du graphe n'ont ni aapt2 ni R : leurs dépendances
     *  « generateRJar » n'existent pas et ne doivent pas être
     *  déclarées (lot U/6, rapport du 2026-11-17). */
    private final java.util.Set<String> modulesAvecR =
            new java.util.LinkedHashSet<>();
    private char[] signingPassword = "joidepass".toCharArray();

    /**
     * @param buildDir     répertoire racine des sorties (ancre $OUT)
     * @param m2Dir        répertoire des bibliothèques (ancre $M2)
     * @param toolchainDir répertoire racine de l'outillage (ancre
     *                     $SDK) : android.jar et binaire aapt2 y
     *                     vivent (ou en dessous)
     * @param toolchain    outillage localisé
     * @param scopeDexer   dexeur des scopes (préférer en processus)
     * @param mergeDexer   dexeur des fusions (fork à gros tas)
     * @param signer       service de signature
     * @param assembler    service d'assemblage de bundle
     * @param sourceLevel  niveau de source/cible (« 17 »)
     */
    public AndroidTaskFactory(Path buildDir, Path m2Dir, Path toolchainDir,
                              Toolchain toolchain, Dexer scopeDexer,
                              Dexer mergeDexer, ApkSignerService signer,
                              BundleAssembler assembler, String sourceLevel) {
        this.buildDir = buildDir.toAbsolutePath().normalize();
        this.m2Dir = m2Dir.toAbsolutePath().normalize();
        this.toolchainDir = toolchainDir.toAbsolutePath().normalize();
        this.toolchain = toolchain;
        this.scopeDexer = scopeDexer;
        this.mergeDexer = mergeDexer;
        this.signer = signer;
        this.assembler = assembler;
        this.sourceLevel = sourceLevel;
        this.abiCache = new AbiCache(
                this.buildDir.resolve("jvm/abi-cache"));
    }

    /**
     * @param kotlinDriver le pilote Kotlin à utiliser (embarqué ou
     *                     VM forkée — lot VII)
     * @return cette fabrique
     */
    public AndroidTaskFactory withKotlinDriver(
            jo.joide.build.jvm.compile.CompilerDriver kotlinDriver) {
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

    /**
     * @param spec module à ajouter
     * @return cette fabrique
     */
    public AndroidTaskFactory module(AndroidModuleSpec spec) {
        modules.put(spec.name, spec);
        return this;
    }

    /** Ancres ($OUT, $M2, $SDK) pour construire le RootSet du build. */
    public Map<RootName, Path> anchors() {
        return Map.of(RootName.OUT, buildDir, RootName.M2, m2Dir,
                RootName.SDK, toolchainDir);
    }

    /**
     * Fournit les ancres des racines sources ($PROJECT…) : sans
     * elles, la présence de Kotlin est décidée par le câbleur.
     *
     * @param roots ancres des racines du build
     * @return cette fabrique
     */
    public AndroidTaskFactory withSourceAnchors(RootSet roots) {
        this.sourceAnchors = roots;
        return this;
    }

    /**
     * Fixe le mot de passe du fichier de clés et des clés de
     * signature (le même pour les deux, pratique keytool).
     *
     * @param password mot de passe
     * @return cette fabrique
     */
    public AndroidTaskFactory signingPassword(char[] password) {
        this.signingPassword = password == null
                ? "joidepass".toCharArray() : password.clone();
        return this;
    }

    /**
     * Force la présence (ou l'absence) de sources Kotlin d'un module.
     *
     * @param module    nom du module
     * @param hasKotlin vrai si le module contient des sources Kotlin
     * @return cette fabrique
     */
    public AndroidTaskFactory forceKotlin(String module, boolean hasKotlin) {
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
        for (AndroidModuleSpec spec : modules.values()) {
            registerResourceTasks(graph, spec);
        }
        for (AndroidModuleSpec spec : modules.values()) {
            registerCompileTasks(graph, spec);
        }
        AndroidModuleSpec app = findApp();
        if (app != null) {
            for (int v = 0; v < app.variants.size(); v++) {
                registerVariantTasks(graph, app, app.variants.get(v));
            }
        }
        return graph.build();
    }

    private AndroidModuleSpec findApp() {
        for (AndroidModuleSpec spec : modules.values()) {
            if (spec.application) {
                return spec;
            }
        }
        return null;
    }

    private void registerResourceTasks(TaskGraph.Builder graph,
                                       AndroidModuleSpec spec) {
        if (spec.manifest == null) {
            // lot U/6 : un module SANS manifeste (frère JVM du graphe
            // Android, bibliothèque sans facette) ne porte ni aapt2 ni
            // R.jar — l'ancien code NPEtait sur anchored(null)
            // (Path.isAbsolute(), rapporté le 2026-11-17) ; des
            // ressources SANS manifeste restent une erreur NOMINALE
            if (!spec.resourceDirs.isEmpty()) {
                throw new IllegalArgumentException(spec.name
                        + " : ressources Android sans manifeste — "
                        + "la liaison aapt2 exige un manifeste");
            }
            return;
        }
        modulesAvecR.add(spec.name);
        RootedPath androidDir = RootedPath.of(RootName.OUT,
                "android/" + spec.name);
        List<RootedPath> resourceRoots = new ArrayList<>();
        for (Path dir : spec.resourceDirs) {
            resourceRoots.add(anchored(dir));
        }
        String compileTask = spec.name + ":processAndroidResources";
        if (!resourceRoots.isEmpty()) {
            graph.register(new Aapt2CompileTask(spec.name, resourceRoots,
                    androidDir.resolve("flat"),
                    toolchain.aapt2.toAbsolutePath().normalize()));
        }
        List<RootedPath> flatDirs = new ArrayList<>();
        if (!resourceRoots.isEmpty()) {
            flatDirs.add(androidDir.resolve("flat"));
        }
        List<RootedPath> libFlats = new ArrayList<>();
        for (String dependency : allTransitiveModules(spec)) {
            AndroidModuleSpec dep = modules.get(dependency);
            if (dep != null && !dep.resourceDirs.isEmpty()) {
                libFlats.add(RootedPath.of(RootName.OUT,
                        "android/" + dependency + "/flat"));
            }
        }
        if (spec.application) {
            flatDirs.addAll(libFlats);
        }
        graph.register(new Aapt2LinkTask(spec.name,
                anchored(spec.manifest),
                androidDir.resolve("manifest-enrichi.xml"),
                flatDirs,
                androidDir.resolve("linked.apk"),
                androidDir.resolve("gen"),
                androidDir.resolve("R.txt"),
                androidDir.resolve("proto"),
                toolchain.aapt2.toAbsolutePath().normalize(),
                toolchain.platformJar.toAbsolutePath().normalize(),
                spec.application && !spec.variants.isEmpty()
                        ? spec.variants.get(0).applicationId : null,
                spec.assetsDir == null ? null : anchored(spec.assetsDir)));
        graph.register(new RJarTask(spec.name, androidDir.resolve("gen"),
                androidDir.resolve("r-classes"),
                androidDir.resolve("r.jar"),
                androidDir.resolve("r.abi.manifest")));
        graph.dependsOn(spec.name + ":generateRJar",
                spec.name + ":linkAndroidResources");
        if (!resourceRoots.isEmpty()) {
            graph.dependsOn(spec.name + ":linkAndroidResources", compileTask);
        }
    }

    private void registerCompileTasks(TaskGraph.Builder graph,
                                      AndroidModuleSpec spec)
            throws IOException {
        RootedPath moduleDir = RootedPath.of(RootName.OUT,
                "jvm/" + spec.name);
        RootedPath classesJava = moduleDir.resolve("classes-java");
        RootedPath classesKotlin = moduleDir.resolve("classes-kotlin");
        RootedPath abiJava = moduleDir.resolve("abi-java");
        RootedPath abiKotlin = moduleDir.resolve("abi-kotlin");
        RootedPath jarFile = moduleDir.resolve("libs")
                .resolve(spec.name + ".jar");
        boolean hasKotlin = kotlinPresence.computeIfAbsent(spec.name,
                name -> detectKotlin(spec));
        String compileJava = spec.name + ":compileJava";
        String compileKotlin = spec.name + ":compileKotlin";

        List<ClasspathEntry> upstreamJava = new ArrayList<>();
        List<ClasspathEntry> upstreamKotlin = new ArrayList<>();
        // android.jar : bibliothèque de plateforme
        addExternalJar(graph, upstreamJava, upstreamKotlin,
                toolchain.platformJar.toAbsolutePath().normalize(),
                "platform:android.jar");
        // Les stubs d'invokedynamic du compile-time (StringConcatFactory
        // pour la concaténation, LambdaMetafactory pour les lambdas) :
        // à compliance ≥ 9, ECJ émet un invokedynamic dont la bootstrap
        // doit être résolvable — android.jar les OMET et le plateau 35
        // n'a que LambdaMetafactory dans son jar system-modules ; AGP
        // pose core-lambda-stubs.jar du build-tools, la même doctrine
        // CodeAssist (le localisateur embarque et extrait la pièce ;
        // jamais dexé, D8 désugare l'indy au dexage).
        if (toolchain.lambdaStubsJar != null) {
            addExternalJar(graph, upstreamJava, upstreamKotlin,
                    toolchain.lambdaStubsJar.toAbsolutePath().normalize(),
                    "platform:core-lambda-stubs.jar");
        }
        // R.jar du module : son manifeste ABI est écrit par la tâche
        // de génération (le jar n'existe pas encore à la construction
        // du graphe) — chemin stable, contenu haché par le moteur ;
        // les modules SANS manifeste n'ont pas de R.jar (lot U/6)
        if (modulesAvecR.contains(spec.name)) {
            Path rJar = buildDir.resolve("android").resolve(spec.name)
                    .resolve("r.jar");
            ClasspathEntry rEntry = new ClasspathEntry(
                    ClasspathEntry.Kind.EXTERNAL, spec.name + ":R.jar",
                    anchored(rJar),
                    RootedPath.of(RootName.OUT,
                            "android/" + spec.name + "/r.abi.manifest"),
                    null);
            upstreamJava.add(rEntry);
            upstreamKotlin.add(rEntry);
        }
        // modules amont
        for (String dependency : spec.moduleDependencies) {
            AndroidModuleSpec dep = modules.get(dependency);
            if (dep == null) {
                continue;
            }
            RootedPath depDir = RootedPath.of(RootName.OUT,
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
                graph.dependsOn(compileJava, dependency + ":compileKotlin");
            }
            if (hasKotlin) {
                graph.dependsOn(compileKotlin, dependency + ":compileJava");
                if (depKotlin) {
                    graph.dependsOn(compileKotlin,
                            dependency + ":compileKotlin");
                }
            }
            // les ressources du module amont doivent être liées avant
            // la compilation aval (le R du module amont existe déjà) —
            // un amont SANS manifeste n'a pas de generateRJar (lot U/6)
            if (modulesAvecR.contains(dependency)) {
                graph.dependsOn(compileJava, dependency + ":generateRJar");
                if (hasKotlin) {
                    graph.dependsOn(compileKotlin,
                            dependency + ":generateRJar");
                }
            }
        }
        // jars externes et AAR
        for (Path jar : spec.externalJars) {
            addExternalJar(graph, upstreamJava, upstreamKotlin, jar,
                    "ext:" + jar.getFileName());
        }
        for (Path aarDir : spec.externalAarDirs) {
            Path classesJar = aarDir.resolve("classes.jar");
            if (Files.isRegularFile(classesJar)) {
                addExternalJar(graph, upstreamJava, upstreamKotlin,
                        classesJar, "aar:" + aarDir.getFileName());
            }
            Path libs = aarDir.resolve("libs");
            if (Files.isDirectory(libs)) {
                try (var stream = Files.list(libs)) {
                    List<Path> inner = new ArrayList<>();
                    stream.filter(p -> p.getFileName() != null
                            && p.getFileName().toString().endsWith(".jar"))
                            .sorted()
                            .forEach(inner::add);
                    for (Path jar : inner) {
                        addExternalJar(graph, upstreamJava, upstreamKotlin,
                                jar, "aar:" + aarDir.getFileName()
                                        + ":" + jar.getFileName());
                    }
                }
            }
        }
        if (modulesAvecR.contains(spec.name)) {
            graph.dependsOn(compileJava, spec.name + ":generateRJar");
            // lot XVIII : la MÊME arête pour Kotlin — l'entrée R.jar
            // (avec r.abi.manifest en FileInput) sert aux DEUX tâches
            // de compilation (upstreamKotlin) ; sans l'arête, le
            // scheduler parallèle démarrait compileKotlin avant
            // generateRJar : « cannot read input file: r.abi.manifest »
            // (terrain du 2026-11-17)
            if (hasKotlin) {
                graph.dependsOn(compileKotlin,
                        spec.name + ":generateRJar");
            }
        }
        RootedPath javaStateFile = RootedPath.of(RootName.OUT, "jvm")
                .resolve(spec.name).resolve("classes-java")
                .resolve("compile-state.bin");
        if (hasKotlin) {
            List<ClasspathEntry> javaWithOwnKotlin = new ArrayList<>(
                    upstreamJava);
            javaWithOwnKotlin.add(new ClasspathEntry(
                    ClasspathEntry.Kind.MODULE_KOTLIN,
                    spec.name + ":kotlin", classesKotlin,
                    abiKotlin.resolve("current"), abiKotlin));
            // la manière CodeAssist (lot XIV) : -no-jdk — java.*
            // se résout depuis android.jar DU CLASSPATH (posé plus
            // haut, « bibliothèque de plateforme ») ; sans cela la
            // détection JDK consulte java.home (/apex/com.android.art
            // sur l'appareil) et échoue en « No class roots are found
            // in the JDK path »
            graph.register(JvmCompileTask.create(spec.name, "kotlin",
                    sourceRoots(spec), upstreamKotlin, kotlinDriver(),
                    sourceRoots(spec), classesKotlin, abiKotlin,
                    javaStateFile, sourceLevel, true));
            graph.register(JvmCompileTask.create(spec.name, "java",
                    sourceRoots(spec), javaWithOwnKotlin, javaDriver,
                    List.of(), classesJava, abiJava, null, sourceLevel,
                    false));
            graph.dependsOn(compileJava, compileKotlin);
        } else {
            graph.register(JvmCompileTask.create(spec.name, "java",
                    sourceRoots(spec), upstreamJava, javaDriver,
                    List.of(), classesJava, abiJava, null, sourceLevel,
                    false));
        }
        List<RootedPath> jarClasses = new ArrayList<>();
        jarClasses.add(classesJava);
        if (hasKotlin) {
            jarClasses.add(classesKotlin);
        }
        graph.register(new jo.joide.build.jvm.tasks.JarTask(spec.name,
                jarClasses, List.of(), jarFile));
        graph.dependsOn(spec.name + ":jar", compileJava);
        if (hasKotlin) {
            graph.dependsOn(spec.name + ":jar", compileKotlin);
        }
    }

    private void registerVariantTasks(TaskGraph.Builder graph,
                                      AndroidModuleSpec app,
                                      jo.joide.build.android.variant.AndroidVariant variant) {
        String suffix = variant.buildType;
        RootedPath androidDir = RootedPath.of(RootName.OUT,
                "android/" + app.name + "/variants/" + suffix);
        RootedPath dexDir = androidDir.resolve("dex");
        RootedPath mergedDir = androidDir.resolve("merged-dex");
        RootedPath packageApk = androidDir.resolve("unsigned.apk");
        RootedPath signedApk = androidDir.resolve("app-" + suffix + ".apk");
        RootedPath aab = androidDir.resolve("app-" + suffix + ".aab");
        RootedPath mapping = androidDir.resolve("mapping.txt");
        RootedPath shrunkDir = androidDir.resolve("shrunk-dex");

        List<RootedPath> scopeDirs = new ArrayList<>();
        List<String> scopeLabels = new ArrayList<>();
        List<RootedPath> dexClasspath = new ArrayList<>();
        dexClasspath.add(anchored(
                toolchain.platformJar.toAbsolutePath().normalize()));
        // scope 0 : classes du module d'application
        List<RootedPath> appProgram = new ArrayList<>();
        appProgram.add(RootedPath.of(RootName.OUT,
                "jvm/" + app.name + "/classes-java"));
        if (Boolean.TRUE.equals(kotlinPresence.get(app.name))) {
            appProgram.add(RootedPath.of(RootName.OUT,
                    "jvm/" + app.name + "/classes-kotlin"));
        }
        RootedPath appDex = androidDir.resolve("dex-app");
        graph.register(new DexScopeTask(app.name, "app",
                appProgram, dexClasspath, variant.minSdk, appDex,
                scopeDexer));
        scopeDirs.add(appDex);
        scopeLabels.add("app");
        graph.dependsOn(app.name + ":dexScope[app]", app.name + ":compileJava");
        // scopes : jars des modules bibliothèques
        for (String dependency : allTransitiveModules(app)) {
            AndroidModuleSpec dep = modules.get(dependency);
            if (dep == null) {
                continue;
            }
            RootedPath jar = RootedPath.of(RootName.OUT,
                    "jvm/" + dependency + "/libs/" + dependency + ".jar");
            RootedPath dir = androidDir.resolve("dex-lib-" + dependency);
            graph.register(new DexScopeTask(app.name, dependency,
                    List.of(jar), dexClasspath, variant.minSdk, dir,
                    scopeDexer));
            scopeDirs.add(dir);
            scopeLabels.add(dependency);
            graph.dependsOn(app.name + ":dexScope[" + dependency + "]",
                    dependency + ":jar");
        }
        // scopes : jars externes et AAR
        for (Path jar : externalJarsOf(app)) {
            String label = "ext:" + jar.getFileName();
            // le nom du répertoire de sortie ne doit pas porter
            // d'extension d'archive : D8 l'interpréterait comme un
            // fichier zip à produire
            String safeName = jar.getFileName().toString()
                    .replaceAll("\\.(jar|aar)$", "");
            RootedPath dir = androidDir.resolve("dex-ext-" + safeName);
            graph.register(new DexScopeTask(app.name, label,
                    List.of(anchored(jar)), dexClasspath, variant.minSdk,
                    dir, scopeDexer));
            scopeDirs.add(dir);
            scopeLabels.add(label);
        }

        String mergeTask = app.name + ":mergeDex[" + suffix + "]";
        String packageTask = app.name + ":package[" + suffix + "]";
        String signTask = app.name + ":sign[" + suffix + "]";
        String aabTask = app.name + ":bundle[" + suffix + "]";
        List<RootedPath> dexInputs = new ArrayList<>(scopeDirs);
        List<String> dexInputLabels = new ArrayList<>(scopeLabels);

        if (variant.minifyEnabled) {
            List<RootedPath> program = new ArrayList<>();
            // R8 n'accepte pas les répertoires comme entrées de
            // programme : le jar du module (même contenu que les
            // répertoires de classes) est l'entrée de fichier
            program.add(RootedPath.of(RootName.OUT,
                    "jvm/" + app.name + "/libs/" + app.name + ".jar"));
            for (String dependency : allTransitiveModules(app)) {
                AndroidModuleSpec dep = modules.get(dependency);
                if (dep == null) {
                    continue;
                }
                program.add(RootedPath.of(RootName.OUT,
                        "jvm/" + dependency + "/libs/" + dependency + ".jar"));
            }
            for (Path jar : externalJarsOf(app)) {
                program.add(anchored(jar));
            }
            graph.register(new R8Task(app.name, suffix, program,
                    dexClasspath, keepRules(app, variant), variant.minSdk,
                    shrunkDir, mapping,
                    toolchain.platformJar.toAbsolutePath().normalize(),
                    mergeDexer));
            graph.dependsOn(app.name + ":minify[" + suffix + "]",
                    app.name + ":jar");
            graph.register(new PackageTask(app.name, suffix,
                    RootedPath.of(RootName.OUT,
                            "android/" + app.name + "/linked.apk"),
                    shrunkDir, null, packageApk));
            graph.dependsOn(packageTask, app.name + ":minify[" + suffix + "]");
            for (String dependency : allTransitiveModules(app)) {
                AndroidModuleSpec dep = modules.get(dependency);
                if (dep != null) {
                    graph.dependsOn(app.name + ":minify[" + suffix + "]",
                            dependency + ":jar");
                }
            }
            graph.dependsOn(app.name + ":minify[" + suffix + "]",
                    app.name + ":compileJava");
        } else {
            graph.register(new MergeDexTask(mergeTask, dexInputs,
                    dexInputLabels, variant.minSdk, dexClasspath, mergedDir,
                    mergeDexer));
            graph.register(new PackageTask(app.name, suffix,
                    RootedPath.of(RootName.OUT,
                            "android/" + app.name + "/linked.apk"),
                    mergedDir, null, packageApk));
            graph.dependsOn(packageTask, mergeTask);
            for (int i = 0; i < scopeDirs.size(); i++) {
                graph.dependsOn(mergeTask, app.name + ":dexScope["
                        + scopeLabels.get(i) + "]");
            }
        }
        graph.dependsOn(packageTask, app.name + ":linkAndroidResources");

        if (variant.keystoreFile != null) {
            graph.register(new SignTask(app.name, suffix, packageApk,
                    signedApk, Path.of(variant.keystoreFile),
                    variant.keystoreAlias, signingPassword.clone(),
                    signingPassword.clone(), schemesOf(variant),
                    variant.minSdk, signer));
            graph.dependsOn(signTask, packageTask);
        }
        graph.register(new AabTask(app.name, suffix,
                RootedPath.of(RootName.OUT,
                        "android/" + app.name + "/proto.zip"),
                variant.minifyEnabled ? shrunkDir : mergedDir, aab,
                assembler));
        graph.dependsOn(aabTask, packageTask);
    }

    private static List<String> schemesOf(
            jo.joide.build.android.variant.AndroidVariant variant) {
        List<String> schemes = new ArrayList<>();
        if (variant.v1Signing) {
            schemes.add("v1");
        }
        if (variant.v2Signing) {
            schemes.add("v2");
        }
        if (variant.v3Signing) {
            schemes.add("v3");
        }
        return schemes;
    }

    private List<String> keepRules(AndroidModuleSpec app,
            jo.joide.build.android.variant.AndroidVariant variant) {
        List<String> rules = new ArrayList<>();
        // point d'entrée : le paquet de l'application est conservé
        // (le manifeste référence ses classes) ; les règles fines
        // viendront de la lecture du manifeste
        String appPackage = variant.applicationId;
        rules.add("-keep class " + appPackage + ".** { *; }");
        return rules;
    }

    private List<Path> externalJarsOf(AndroidModuleSpec app) {
        List<Path> jars = new ArrayList<>(app.externalJars);
        for (Path aarDir : app.externalAarDirs) {
            Path classesJar = aarDir.resolve("classes.jar");
            if (Files.isRegularFile(classesJar)) {
                jars.add(classesJar);
            }
            Path libs = aarDir.resolve("libs");
            if (Files.isDirectory(libs)) {
                try (var stream = Files.list(libs)) {
                    stream.filter(p -> p.getFileName() != null
                            && p.getFileName().toString().endsWith(".jar"))
                            .sorted()
                            .forEach(jars::add);
                } catch (IOException e) {
                    // listage impossible : ignoré, la compilation
                    // signalera l'absence
                }
            }
        }
        return jars;
    }

    private List<String> allTransitiveModules(AndroidModuleSpec spec) {
        List<String> order = new ArrayList<>();
        collectModules(spec, order, new java.util.HashSet<>());
        order.remove(spec.name);
        return order;
    }

    private void collectModules(AndroidModuleSpec spec, List<String> order,
                                java.util.Set<String> seen) {
        if (!seen.add(spec.name)) {
            return;
        }
        for (String dependency : spec.moduleDependencies) {
            AndroidModuleSpec dep = modules.get(dependency);
            if (dep != null) {
                collectModules(dep, order, seen);
            }
        }
        order.add(spec.name);
    }

    private List<RootedPath> sourceRoots(AndroidModuleSpec spec) {
        List<RootedPath> roots = new ArrayList<>();
        for (Path root : spec.sourceRoots) {
            roots.add(anchored(root));
        }
        return roots;
    }

    private void addExternalJar(TaskGraph.Builder graph,
                                List<ClasspathEntry> java,
                                List<ClasspathEntry> kotlin, Path jar,
                                String label) throws IOException {
        AbiManifest manifest = abiCache.manifestOf(jar);
        RootedPath manifestFile = RootedPath.of(RootName.OUT, "jvm")
                .resolve("abi-cache")
                .resolve(manifest.digest() + ".manifest");
        ClasspathEntry entry = new ClasspathEntry(
                ClasspathEntry.Kind.EXTERNAL, label, anchored(jar),
                manifestFile, null);
        java.add(entry);
        kotlin.add(entry);
    }

    private boolean detectKotlin(AndroidModuleSpec spec) {
        Boolean forced = forcedKotlin.get(spec.name);
        if (forced != null) {
            return forced;
        }
        if (sourceAnchors == null) {
            return false;
        }
        for (RootedPath root : sourceRoots(spec)) {
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
                // balayage impossible : absence retenue
            }
        }
        return false;
    }

    private RootedPath anchored(Path path) {
        if (!path.isAbsolute()) {
            // chemin relatif : racine du projet
            return RootedPath.of(RootName.PROJECT,
                    path.normalize().toString().replace('\\', '/'));
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(buildDir)) {
            return RootedPath.of(RootName.OUT,
                    buildDir.relativize(normalized).toString());
        }
        if (normalized.startsWith(m2Dir)) {
            return RootedPath.of(RootName.M2,
                    m2Dir.relativize(normalized).toString());
        }
        if (normalized.startsWith(toolchainDir)) {
            return RootedPath.of(RootName.SDK,
                    toolchainDir.relativize(normalized).toString());
        }
        throw new IllegalArgumentException(
                "path outside build roots: " + path
                        + " (expected under " + buildDir + ", " + m2Dir
                        + " or " + toolchainDir + ")");
    }
}
