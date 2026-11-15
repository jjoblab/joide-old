package jo.joide.build.jvm.tasks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import jo.joide.build.jvm.abi.AbiManifest;
import jo.joide.build.jvm.abi.ClassAbi;
import jo.joide.build.jvm.classfile.ClassFileException;
import jo.joide.build.jvm.classfile.ClassFileReader;
import jo.joide.build.jvm.classfile.ClassModel;
import jo.joide.build.jvm.compile.CompileRequest;
import jo.joide.build.jvm.compile.CompileResult;
import jo.joide.build.jvm.compile.CompilerDriver;
import jo.joide.build.jvm.graph.CompileState;
import jo.joide.build.jvm.graph.ImpactAnalysis;
import jo.joide.build.jvm.graph.SourceHeaderScanner;
import jo.joide.platform.AtomicFiles;
import jo.joide.platform.Problem;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.InputDelta;
import jo.joide.tasks.TaskContext;
import jo.joide.tasks.TaskExecution;

/**
 * Cœur de compilation JVM incrémentale, partagé par les tâches Java
 * et Kotlin d'un module. Les sources sont identifiées par leur chemin
 * relatif à la racine nommée — relocalisable par construction.
 *
 * <p>Séquence : chargement de l'état (un cache — toute anomalie vaut
 * recompilation complète), comparaison de la spécification, calcul
 * des changements d'ABI du classpath par manifestes, lots successifs
 * au point fixe (compiler, rescanner les classes produites, propager
 * aux sources dépendantes), repli complet au seuil ou sur suppression
 * de source. L'état et le manifeste sont réécrits après un build
 * réussi seulement : un échec laisse l'état précédent intact.</p>
 */
final class IncrementalJvmCompile {

    private final String moduleName;
    private final String language;
    private final String fileExtension;
    private final List<RootedPath> sourceRoots;
    private final List<ClasspathEntry> classpath;
    private final CompilerDriver driver;
    private final List<RootedPath> javaSourceRootsForResolution;
    private final RootedPath outputDir;
    private final RootedPath abiDir;
    private final RootedPath javaStateFile;
    private final String sourceLevel;
    private final String encoding;
    private final double maxRecompiledFraction;
    private final boolean aggregatingProcessor;

    private Map<String, String> previousAbisSnapshot = new HashMap<>();

    IncrementalJvmCompile(String moduleName, String language,
                          String fileExtension,
                          List<RootedPath> sourceRoots,
                          List<ClasspathEntry> classpath,
                          CompilerDriver driver,
                          List<RootedPath> javaSourceRootsForResolution,
                          RootedPath outputDir, RootedPath abiDir,
                          RootedPath javaStateFile, String sourceLevel,
                          String encoding, double maxRecompiledFraction,
                          boolean aggregatingProcessor) {
        this.moduleName = moduleName;
        this.language = language;
        this.fileExtension = fileExtension;
        this.sourceRoots = List.copyOf(sourceRoots);
        this.classpath = List.copyOf(classpath);
        this.driver = driver;
        this.javaSourceRootsForResolution = List.copyOf(
                javaSourceRootsForResolution);
        this.outputDir = outputDir;
        this.abiDir = abiDir;
        this.javaStateFile = javaStateFile;
        this.sourceLevel = sourceLevel;
        this.encoding = encoding;
        this.maxRecompiledFraction = maxRecompiledFraction;
        this.aggregatingProcessor = aggregatingProcessor;
    }

    /** @return les racines de sources de la tâche */
    List<RootedPath> sourceRoots() {
        return sourceRoots;
    }

    /** @return les entrées de classpath de la tâche */
    List<ClasspathEntry> classpathEntries() {
        return classpath;
    }

    /** @return le répertoire de sortie des classes */
    RootedPath classesOutput() {
        return outputDir;
    }

    /** @return le répertoire des manifestes ABI */
    RootedPath abiOutput() {
        return abiDir;
    }

    /** @return la version de l'outil piloté */
    String toolVersion() {
        return driver.toolVersion();
    }

    /**
     * Exécution en repli complet forcé (raz-de-marée de changements).
     *
     * @param context contexte du moteur
     * @return le résultat de la tâche
     */
    TaskExecution executeForcedFull(TaskContext context) {
        try {
            Map<String, Path> sources = scanSources(context);
            return fullCompile(context, sources, spec());
        } catch (IOException e) {
            return TaskExecution.failed(List.of(Problem.error(
                    "compile-io-error",
                    "module " + moduleName + ": " + e.getMessage())));
        }
    }

    /** @return la spécification canonique (structurelle, jamais les contenus) */
    String spec() {
        StringBuilder out = new StringBuilder();
        out.append(moduleName).append('|').append(language)
                .append('|').append(driver.toolVersion())
                .append('|').append(sourceLevel)
                .append('|').append(encoding)
                .append('|').append(maxRecompiledFraction)
                .append('|').append(aggregatingProcessor);
        for (RootedPath root : sourceRoots) {
            out.append("|src=").append(root);
        }
        for (ClasspathEntry entry : classpath) {
            out.append("|cp=").append(entry.kind).append(':')
                    .append(entry.label).append('@').append(entry.location);
        }
        return out.toString();
    }

    /**
     * Exécute la compilation incrémentale.
     *
     * @param context contexte du moteur
     * @return le résultat de la tâche
     */
    TaskExecution execute(TaskContext context) {
        try {
            return executeInternal(context);
        } catch (IOException e) {
            return TaskExecution.failed(List.of(Problem.error(
                    "compile-io-error",
                    "module " + moduleName + ": " + e.getMessage())));
        }
    }

    private TaskExecution executeInternal(TaskContext context)
            throws IOException {
        Path outDir = context.resolve(outputDir);
        Files.createDirectories(outDir);
        Map<String, Path> sources = scanSources(context);
        CompileState state = loadState(outDir);
        String spec = spec();
        boolean full = state == null || !spec.equals(state.spec);
        if (state == null) {
            state = new CompileState();
        }
        InputDelta delta = context.inputDelta();
        TreeSet<String> added = relativeNames(delta.added());
        TreeSet<String> modified = relativeNames(delta.modified());
        TreeSet<String> removed = relativeNames(delta.removed());
        if (!removed.isEmpty()) {
            full = true; // suppression de source : repli complet
        }

        List<Problem> warnings = new ArrayList<>();
        Map<String, ImpactAnalysis.ChangeKind> classpathChanges =
                new TreeMap<>();
        if (!full) {
            classpathChanges.putAll(classpathChanges(context, state));
        }
        if (!full && "kotlin".equals(language)) {
            applyJavaDirtyApproximation(context, delta, classpathChanges);
        }

        ImpactAnalysis analysis = ImpactAnalysis.of(state,
                new ImpactAnalysis.Policy(maxRecompiledFraction,
                        aggregatingProcessor),
                sources.size());
        TreeSet<String> batch;
        if (full) {
            batch = null;
        } else {
            java.util.Set<String> initial = analysis.initialBatch(
                    added, modified, classpathChanges);
            batch = initial == null ? null : new TreeSet<>(initial);
        }
        if (batch != null && batch.isEmpty()) {
            // rien à compiler : seuls les manifestes consommés bougent
            state.spec = spec;
            updateConsumedManifests(context, state);
            persistIfChanged(context, outDir, state, false);
            return TaskExecution.ok(warnings);
        }
        if (batch == null) {
            warnings.add(Problem.info("full-recompile",
                    "module " + moduleName + " (" + language
                            + "): full recompile (threshold or removal)"));
            return fullCompile(context, sources, spec);
        }
        return incrementalLoop(context, sources, state, analysis, batch,
                spec, warnings, outDir);
    }

    private TaskExecution incrementalLoop(TaskContext context,
                                          Map<String, Path> sources,
                                          CompileState state,
                                          ImpactAnalysis analysis,
                                          TreeSet<String> batch,
                                          String spec,
                                          List<Problem> warnings,
                                          Path outDir) throws IOException {
        while (!batch.isEmpty()) {
            deleteStaleClasses(outDir, state, batch);
            List<Path> batchFiles = new ArrayList<>(batch.size());
            for (String source : batch) {
                Path file = sources.get(source);
                if (file == null) {
                    return TaskExecution.failed(List.of(Problem.error(
                            "source-disappeared",
                            "source vanished during build: " + source)));
                }
                batchFiles.add(file);
            }
            CompileResult result = driver.compile(
                    compileRequest(context, batchFiles, outDir));
            if (!result.success) {
                return TaskExecution.failed(result.diagnostics);
            }
            Map<String, ImpactAnalysis.ChangeKind> produced =
                    recordBatch(outDir, state, batch, sources);
            java.util.Set<String> next = analysis.nextBatch(batch, produced);
            batch = next == null ? null : new TreeSet<>(next);
            if (batch == null) {
                warnings.add(Problem.info("full-recompile",
                        "module " + moduleName + " (" + language
                                + "): full recompile (propagation over"
                                + " threshold)"));
                return fullCompile(context, sources, spec);
            }
        }
        state.spec = spec;
        updateConsumedManifests(context, state);
        persistIfChanged(context, outDir, state, true);
        return TaskExecution.ok(warnings);
    }

    private TaskExecution fullCompile(TaskContext context,
                                      Map<String, Path> sources,
                                      String spec) throws IOException {
        Path outDir = context.resolve(outputDir);
        deleteTree(outDir);
        Files.createDirectories(outDir);
        CompileState state = new CompileState();
        if (!sources.isEmpty()) {
            List<Path> allFiles = new ArrayList<>(sources.values());
            allFiles.sort(null);
            CompileResult result = driver.compile(
                    compileRequest(context, allFiles, outDir));
            if (!result.success) {
                return TaskExecution.failed(result.diagnostics);
            }
        }
        recordBatch(outDir, state, new TreeSet<>(sources.keySet()), sources);
        state.spec = spec;
        updateConsumedManifests(context, state);
        persistIfChanged(context, outDir, state, true);
        return TaskExecution.ok();
    }

    private CompileRequest compileRequest(TaskContext context,
                                          List<Path> batchFiles,
                                          Path outDir) {
        CompileRequest.Builder builder = CompileRequest.builder();
        for (Path file : batchFiles) {
            builder.source(file);
        }
        for (ClasspathEntry entry : classpath) {
            builder.classpath(context.resolve(entry.location));
        }
        for (RootedPath root : javaSourceRootsForResolution) {
            builder.javaSourceRoot(context.resolve(root));
        }
        builder.classpath(outDir);
        builder.outputDir(outDir);
        builder.sourceLevel(sourceLevel);
        return builder.build();
    }

    /**
     * Met à jour l'état pour les sources d'un lot compilé : produits
     * (et super-types), références, ABI ; puis calcule les changements
     * d'ABI des classes produites pour la propagation.
     */
    private Map<String, ImpactAnalysis.ChangeKind> recordBatch(
            Path outDir, CompileState state, TreeSet<String> batch,
            Map<String, Path> sources) throws IOException {
        Map<String, ClassModel> written = scanClasses(outDir);
        Map<String, List<String>> productsByKey = new TreeMap<>();
        for (Map.Entry<String, ClassModel> entry : written.entrySet()) {
            productsByKey.computeIfAbsent(
                    attributionKey(entry.getValue()),
                    k -> new ArrayList<>()).add(entry.getKey());
        }
        Map<String, List<String>> productsOfSource = new TreeMap<>();
        for (String source : batch) {
            CompileState.SourceDeps old = state.sources.get(source);
            if (old != null) {
                for (String produced : old.produced.keySet()) {
                    state.classAbis.remove(produced);
                }
            }
            CompileState.SourceDeps deps = buildDeps(source, sources,
                    productsByKey, written);
            state.sources.put(source, deps);
            productsOfSource.put(source,
                    new ArrayList<>(deps.produced.keySet()));
            // l'ABI de chaque classe produite alimente le manifeste du
            // module : c'est la surface consommée par l'aval
            for (String produced : deps.produced.keySet()) {
                ClassModel model = written.get(produced);
                if (model != null) {
                    state.classAbis.put(produced,
                            ClassAbi.of(model).hash());
                }
            }
        }
        // classes non attribuées (rare) : suivies avec une source
        // synthétique pour rester dans le graphe d'impact
        TreeSet<String> attributed = new TreeSet<>();
        for (List<String> products : productsOfSource.values()) {
            attributed.addAll(products);
        }
        CompileState.SourceDeps orphans = new CompileState.SourceDeps(
                "__orphan__", "");
        boolean hasOrphans = false;
        for (Map.Entry<String, ClassModel> entry : written.entrySet()) {
            if (!attributed.contains(entry.getKey())) {
                hasOrphans = true;
                orphans.produced.put(entry.getKey(),
                        supersOf(entry.getValue()));
                orphans.references.addAll(
                        entry.getValue().referencedClasses);
                state.classAbis.put(entry.getKey(),
                        ClassAbi.of(entry.getValue()).hash());
            }
        }
        if (hasOrphans) {
            state.sources.put(orphans.relativePath, orphans);
        }
        // différences d'ABI des classes du lot pour la propagation
        Map<String, ImpactAnalysis.ChangeKind> changes = new TreeMap<>();
        for (Map.Entry<String, List<String>> products
                : productsOfSource.entrySet()) {
            for (String produced : products.getValue()) {
                String newAbi = state.classAbis.get(produced);
                String oldAbi = previousAbisSnapshot.get(produced);
                if (oldAbi == null) {
                    changes.put(produced,
                            ImpactAnalysis.ChangeKind.ADDED);
                } else if (!oldAbi.equals(newAbi)) {
                    changes.put(produced,
                            ImpactAnalysis.ChangeKind.CHANGED);
                }
            }
        }
        for (Map.Entry<String, String> old : previousAbisSnapshot
                .entrySet()) {
            if (!state.classAbis.containsKey(old.getKey())) {
                changes.put(old.getKey(),
                        ImpactAnalysis.ChangeKind.REMOVED);
            }
        }
        return changes;
    }

    private CompileState loadState(Path outDir) {
        Path file = CompileState.stateFile(outDir);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            CompileState state = CompileState.decode(
                    Files.readAllBytes(file));
            previousAbisSnapshot = new HashMap<>(state.classAbis);
            return state;
        } catch (IOException | RuntimeException e) {
            // état illisible : cache, pas vérité — recompilation complète
            return null;
        }
    }

    private Map<String, Path> scanSources(TaskContext context)
            throws IOException {
        Map<String, Path> found = new TreeMap<>();
        for (RootedPath root : sourceRoots) {
            Path dir = context.resolve(root);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (var stream = Files.walk(dir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString()
                                .endsWith('.' + fileExtension))
                        .forEach(p -> found.put(
                                context.roots().relativize(p).relative(), p));
            }
        }
        return found;
    }

    /** Chemins relatifs des fichiers du delta portant l'extension de la tâche. */
    private TreeSet<String> relativeNames(java.util.Set<RootedPath> paths) {
        TreeSet<String> names = new TreeSet<>();
        for (RootedPath path : paths) {
            if (path.relative().endsWith('.' + fileExtension)) {
                names.add(path.relative());
            }
        }
        return names;
    }

    /**
     * Changements d'ABI par entrée de classpath : manifeste courant
     * contre manifeste consommé (adresses immuables, rejouables).
     */
    private Map<String, ImpactAnalysis.ChangeKind> classpathChanges(
            TaskContext context, CompileState state) {
        Map<String, ImpactAnalysis.ChangeKind> changes = new TreeMap<>();
        for (ClasspathEntry entry : classpath) {
            AbiManifest current = readEntryManifest(context, entry);
            String consumedDigest = state.consumedManifests.get(
                    entry.label);
            if (current == null || current.digest().equals(consumedDigest)) {
                continue;
            }
            AbiManifest previous = null;
            if (consumedDigest != null && entry.manifestDir != null) {
                Path old = context.resolve(entry.manifestDir)
                        .resolve(consumedDigest + ".manifest");
                if (Files.isRegularFile(old)) {
                    try {
                        previous = AbiManifest.read(old);
                    } catch (IOException e) {
                        previous = null;
                    }
                }
            }
            if (previous == null && consumedDigest != null) {
                // manifeste consommé introuvable : sur-invalider tout
                for (String className : current.entries().keySet()) {
                    changes.put(className,
                            ImpactAnalysis.ChangeKind.CHANGED);
                }
                continue;
            }
            for (Map.Entry<String, String> now
                    : current.entries().entrySet()) {
                String oldAbi = previous == null ? null
                        : previous.entries().get(now.getKey());
                if (oldAbi == null) {
                    changes.put(now.getKey(),
                            ImpactAnalysis.ChangeKind.ADDED);
                } else if (!oldAbi.equals(now.getValue())) {
                    changes.put(now.getKey(),
                            ImpactAnalysis.ChangeKind.CHANGED);
                }
            }
            if (previous != null) {
                for (String className : previous.entries().keySet()) {
                    if (!current.entries().containsKey(className)) {
                        changes.put(className,
                                ImpactAnalysis.ChangeKind.REMOVED);
                    }
                }
            }
        }
        return changes;
    }

    private AbiManifest readEntryManifest(TaskContext context,
                                          ClasspathEntry entry) {
        try {
            if (entry.isModule()) {
                Path pointer = context.resolve(entry.manifestInput);
                if (!Files.isRegularFile(pointer)) {
                    return null;
                }
                String digest = Files.readString(pointer,
                        StandardCharsets.UTF_8).trim();
                Path manifest = context.resolve(entry.manifestDir)
                        .resolve(digest + ".manifest");
                if (!Files.isRegularFile(manifest)) {
                    return null;
                }
                return AbiManifest.read(manifest);
            }
            Path manifest = context.resolve(entry.manifestInput);
            if (!Files.isRegularFile(manifest)) {
                return null;
            }
            return AbiManifest.read(manifest);
        } catch (IOException e) {
            return null; // entrée illisible : ignorée, repli sûr au pire
        }
    }

    /**
     * Compilation jointe : un source Java sale du module change la
     * résolution des sources Kotlin qui utilisent ses classes. Les
     * produits enregistrés du source Java sale sont traités comme
     * ABI changée ; un source Java nouveau invalide son paquet.
     */
    private void applyJavaDirtyApproximation(
            TaskContext context, InputDelta delta,
            Map<String, ImpactAnalysis.ChangeKind> changes)
            throws IOException {
        if (javaStateFile == null) {
            return;
        }
        CompileState javaState;
        try {
            javaState = CompileState.decode(Files.readAllBytes(
                    context.resolve(javaStateFile)));
        } catch (IOException | RuntimeException e) {
            javaState = null;
        }
        TreeSet<RootedPath> dirty = new TreeSet<>();
        dirty.addAll(delta.added());
        dirty.addAll(delta.modified());
        for (RootedPath javaSource : dirty) {
            if (!javaSource.relative().endsWith(".java")) {
                continue;
            }
            CompileState.SourceDeps deps = javaState == null ? null
                    : javaState.sources.get(javaSource.relative());
            if (deps != null && !deps.produced.isEmpty()) {
                for (String produced : deps.produced.keySet()) {
                    changes.put(produced,
                            ImpactAnalysis.ChangeKind.CHANGED);
                }
            } else {
                // nouveau source Java : le paquet change pour la
                // résolution des noms simples
                Path file = context.resolve(javaSource);
                String packageName = "";
                if (Files.isRegularFile(file)) {
                    SourceHeaderScanner.Header header =
                            SourceHeaderScanner.scan(file,
                                    Files.readAllBytes(file));
                    packageName = header.packageName;
                }
                String pseudo = packageName.isEmpty()
                        ? javaSource.fileName()
                        : packageName.replace('.', '/') + '/'
                                + javaSource.fileName();
                changes.put(pseudo, ImpactAnalysis.ChangeKind.ADDED);
            }
        }
    }

    private void updateConsumedManifests(TaskContext context,
                                         CompileState state)
            throws IOException {
        for (ClasspathEntry entry : classpath) {
            AbiManifest manifest = readEntryManifest(context, entry);
            if (manifest != null) {
                state.consumedManifests.put(entry.label,
                        manifest.digest());
            } else {
                state.consumedManifests.remove(entry.label);
            }
        }
    }

    /**
     * Écrit l'état, le manifeste du module et son pointeur si le
     * contenu a changé.
     */
    private void persistIfChanged(TaskContext context, Path outDir,
                                  CompileState state, boolean always)
            throws IOException {
        byte[] encoded = state.encode();
        Path stateFile = CompileState.stateFile(outDir);
        if (always || !java.util.Arrays.equals(encoded,
                readIfExists(stateFile))) {
            AtomicFiles.writeBytes(stateFile, encoded);
        }
        AbiManifest manifest = AbiManifest.of(state.classAbis);
        Path abiDirectory = context.resolve(abiDir);
        manifest.writeTo(abiDirectory);
        Path pointer = abiDirectory.resolve("current");
        String digest = manifest.digest();
        if (!digest.equals(readTextIfExists(pointer))) {
            AtomicFiles.writeString(pointer, digest);
        }
    }

    private static byte[] readIfExists(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return Files.readAllBytes(file);
    }

    private static String readTextIfExists(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private void deleteStaleClasses(Path outDir, CompileState state,
                                    TreeSet<String> batch)
            throws IOException {
        for (String source : batch) {
            CompileState.SourceDeps deps = state.sources.get(source);
            if (deps == null) {
                continue;
            }
            for (String produced : deps.produced.keySet()) {
                Files.deleteIfExists(outDir.resolve(produced + ".class"));
            }
        }
    }

    private Map<String, ClassModel> scanClasses(Path outDir)
            throws IOException {
        Map<String, ClassModel> classes = new TreeMap<>();
        if (!Files.isDirectory(outDir)) {
            return classes;
        }
        try (var stream = Files.walk(outDir)) {
            List<Path> files = new ArrayList<>();
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString()
                            .endsWith(".class"))
                    .forEach(files::add);
            files.sort(null);
            for (Path file : files) {
                try {
                    ClassModel model = ClassFileReader.read(
                            Files.readAllBytes(file));
                    classes.put(model.binaryName, model);
                } catch (ClassFileException e) {
                    // classe illisible (écriture concurrente ?) :
                    // ignorée, la relecture du prochain build la couvre
                }
            }
        }
        return classes;
    }

    /** Clé d'attribution d'une classe : paquet + nom de fichier source. */
    private static String attributionKey(ClassModel model) {
        String source = model.sourceFile == null ? "" : model.sourceFile;
        String packageName = model.binaryName.lastIndexOf('/') < 0
                ? "" : model.binaryName.substring(0,
                        model.binaryName.lastIndexOf('/'));
        return packageName + '/' + source;
    }

    private CompileState.SourceDeps buildDeps(String source,
                                              Map<String, Path> sources,
                                              Map<String, List<String>> productsByKey,
                                              Map<String, ClassModel> written)
            throws IOException {
        Path file = sources.get(source);
        SourceHeaderScanner.Header header = file == null
                ? new SourceHeaderScanner.Header("", List.of(), List.of())
                : SourceHeaderScanner.scan(file, Files.readAllBytes(file));
        CompileState.SourceDeps deps = new CompileState.SourceDeps(
                source, header.packageName);
        deps.singleImports.addAll(header.singleImports);
        deps.starImports.addAll(header.starImports);
        String fileName = source.contains("/")
                ? source.substring(source.lastIndexOf('/') + 1) : source;
        String key = header.packageName.replace('.', '/') + '/' + fileName;
        List<String> products = productsByKey.get(key);
        if (products == null) {
            // source sans classe produite (fichier utilitaire, test)
            products = List.of();
        }
        for (String className : products) {
            ClassModel model = written.get(className);
            if (model == null) {
                continue;
            }
            deps.produced.put(model.binaryName, supersOf(model));
            deps.references.addAll(model.referencedClasses);
        }
        for (String single : header.singleImports) {
            deps.references.add(single.replace('.', '/'));
        }
        return deps;
    }

    private static List<String> supersOf(ClassModel model) {
        List<String> supers = new ArrayList<>(model.interfaces.size() + 1);
        if (model.superName != null) {
            supers.add(model.superName);
        }
        supers.addAll(model.interfaces);
        return supers;
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            List<Path> paths = new ArrayList<>();
            stream.forEach(paths::add);
            paths.sort(java.util.Collections.reverseOrder());
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }
}
