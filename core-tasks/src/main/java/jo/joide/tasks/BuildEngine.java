package jo.joide.tasks;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jo.joide.platform.CancelToken;
import jo.joide.platform.Clock;
import jo.joide.platform.Problem;
import jo.joide.platform.RootName;
import jo.joide.platform.RootSet;
import jo.joide.platform.RootedPath;
import jo.joide.store.BuildStore;
import jo.joide.store.FileStamp;
import jo.joide.tasks.TaskInput.DirInput;
import jo.joide.tasks.TaskInput.FileInput;
import jo.joide.tasks.TaskInput.PropertyInput;
import jo.joide.vfs.DirtySet;
import jo.joide.vfs.StableHasher;
import jo.joide.vfs.VfsSnapshot;

/**
 * Moteur de build : relie le modèle d'entrées, le store, le système
 * de fichiers virtuel, l'ordonnanceur et le cache de sorties.
 *
 * <p>Le moteur garde en mémoire l'état des dernières exécutions, les
 * empreintes de contenu connues et les instantanés précédents : sur
 * un démon chaud, un build sans changement ne touche ni le disque ni
 * le store. Les écritures du store ont toutes lieu sur le fil
 * appelant {@link #build}, avant et après l'ordonnancement, en une
 * seule transaction de fin de build : un build interrompu ne laisse
 * rien à moitié écrit.</p>
 *
 * <p>Chaîne de décision d'une tâche : empreinte (contenus ancrés,
 * version d'outil, options) puis comparaison à l'enregistrement
 * précédent ; à jour sans sortie touchée sinon vérification des
 * sorties signalées sales ; sinon restauration du cache adressé par
 * contenu ; sinon exécution, hachage des sorties, enregistrement et
 * mise en cache. Un échec de lecture d'une entrée est une erreur
 * explicite, jamais une empreinte vide.</p>
 */
public final class BuildEngine {

    private static final long HASH_SEED = 0x4A4F494445L;
    private static final long DEFAULT_CACHE_MAX_BYTES = 512L * 1024 * 1024;

    private final RootSet roots;
    private final BuildStore store;
    private final Clock clock;
    private final ExecutorService executor;
    private final ScheduledExecutorService samplerExecutor;
    private final long samplerIntervalMillis;
    private final SchedulingMode mode;
    private final BuildEvents events;
    private final OutputCache outputCache;
    private final Map<String, Long> typeDurations;
    private final ResourceBudget budget;
    private final StableHasher hasher = new StableHasher(HASH_SEED);

    private LastBuildState lastState;
    private final Map<String, VfsSnapshot> previousSnapshots =
            new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> hashesByRoot =
            new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> signalsByRoot =
            new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> dirHashesByRoot =
            new ConcurrentHashMap<>();
    private boolean coldLoaded;

    private BuildEngine(Builder builder) {
        this.roots = builder.roots;
        this.store = builder.store;
        this.clock = builder.clock;
        this.executor = builder.executor;
        this.samplerExecutor = builder.samplerExecutor;
        this.samplerIntervalMillis = builder.samplerIntervalMillis;
        this.mode = builder.mode;
        this.events = builder.events;
        this.typeDurations = builder.typeDurations;
        this.budget = builder.budget == null ? defaultBudget() : builder.budget;
        this.outputCache = new OutputCache(builder.outputCacheDir,
                builder.outputCacheMaxBytes);
        this.outputCache.open(store);
    }

    private static ResourceBudget defaultBudget() {
        Runtime runtime = Runtime.getRuntime();
        long cpuUnits = Math.max(1, runtime.availableProcessors() - 1);
        long memoryMb = Math.max(64, runtime.maxMemory() / (4L * 1024 * 1024));
        return new ResourceBudget(cpuUnits, memoryMb);
    }

    /**
     * @return un bâtisseur de moteur
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Exécute un build complet du graphe.
     *
     * @param graph            graphe validé
     * @param dirty            ensembles sales par racine, jamais
     *                         {@code null}
     * @param currentSnapshots instantanés courants par racine, jamais
     *                         {@code null}
     * @param externalCancel   annulation externe, peut être
     *                         {@code null}
     * @return le rapport du build, résultats dans l'ordre du graphe
     */
    public BuildReport build(TaskGraph graph, Map<RootName, DirtySet> dirty,
                             Map<RootName, VfsSnapshot> currentSnapshots,
                             CancelToken externalCancel) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(dirty, "dirty");
        Objects.requireNonNull(currentSnapshots, "currentSnapshots");
        long startNanos = clock.nanoTime();
        long startWall = clock.wallTimeMillis();
        coldLoad(graph);

        BuildSession session = new BuildSession(graph, dirty, currentSnapshots);
        HeapSampler sampler = new HeapSampler(samplerExecutor,
                samplerIntervalMillis);
        sampler.start();
        Map<String, TaskResult> results = tryHotNoChangePass(graph, session,
                externalCancel);
        if (results == null) {
            Map<String, Long> priorities = CriticalPaths.weights(graph,
                    lastState.durations(), typeDurations);
            TaskScheduler scheduler = new TaskScheduler(executor, mode, events);
            results = scheduler.run(graph, priorities, budget, externalCancel,
                    session::step);
        }
        sampler.stop();
        long endNanos = clock.nanoTime();
        List<TaskResult> ordered = new ArrayList<>();
        for (String name : graph.names()) {
            ordered.add(results.get(name));
        }
        long peak = sampler.peakBytes();
        for (TaskResult result : ordered) {
            peak = Math.max(peak, result.usedHeapBytes());
        }
        persist(graph, session, currentSnapshots);
        return new BuildReport(startWall, endNanos - startNanos, ordered, peak);
    }

    private void coldLoad(TaskGraph graph) {
        if (coldLoaded) {
            return;
        }
        List<TaskDefinition> tasks = new ArrayList<>();
        for (String name : graph.names()) {
            tasks.add(graph.task(name));
        }
        lastState = LastBuildState.load(store, tasks);
        for (RootName root : involvedRoots(graph)) {
            List<FileStamp> stamps = store.loadSnapshot(root.id());
            previousSnapshots.put(root.id(), VfsSnapshot.of(stamps));
        }
        coldLoaded = true;
    }

    private static Set<RootName> involvedRoots(TaskGraph graph) {
        Set<RootName> roots = new LinkedHashSet<>();
        for (String name : graph.names()) {
            TaskDefinition task = graph.task(name);
            for (TaskInput input : task.inputs().asList()) {
                if (input instanceof FileInput file) {
                    roots.add(file.path().root());
                } else if (input instanceof DirInput dir) {
                    roots.add(dir.path().root());
                }
            }
            for (RootedPath output : task.outputs().paths()) {
                roots.add(output.root());
            }
        }
        return roots;
    }

    /**
     * Passe à chaud sans changement : si aucun chemin n'est sale et
     * que chaque tâche retrouve son enregistrement avec la même
     * empreinte recalculée en mémoire, le build entier est à jour
     * sans ordonnancer la moindre tâche — la décision reste O(1) par
     * tâche, sans accès disque ni store.
     *
     * @param graph   graphe validé
     * @param session état du build
     * @return les résultats à jour, ou {@code null} si la passe ne
     *         s'applique pas (un chemin sale, un enregistrement
     *         manquant ou une empreinte divergente)
     */
    private Map<String, TaskResult> tryHotNoChangePass(TaskGraph graph,
                                                       BuildSession session,
                                                       CancelToken externalCancel) {
        for (DirtySet dirtySet : session.dirty.values()) {
            if (!dirtySet.isEmpty()) {
                return null;
            }
        }
        if (externalCancellation(externalCancel)) {
            return null;
        }
        Map<String, TaskResult> results = new LinkedHashMap<>();
        long start = clock.nanoTime();
        for (String name : graph.names()) {
            TaskDefinition task = graph.task(name);
            ExecutionRecord record = lastState.find(name);
            if (record == null) {
                return null;
            }
            String fingerprint;
            try {
                fingerprint = TaskFingerprinter.fingerprint(task,
                        session.hashView());
            } catch (IllegalStateException missing) {
                return null;
            }
            if (!record.fingerprint().equals(fingerprint)) {
                return null;
            }
            long now = clock.nanoTime();
            results.put(name, new TaskResult(name, task.taskType(),
                    TaskStatus.UP_TO_DATE, List.of(),
                    new TaskTimings(now - start, 0, 0, 0, now - start),
                    usedHeap()));
            start = now;
        }
        return results;
    }

    private static boolean externalCancellation(CancelToken token) {
        return token != null && token.isCancelled();
    }

    private void persist(TaskGraph graph, BuildSession session,
                         Map<RootName, VfsSnapshot> currentSnapshots) {
        Collection<ExecutionRecord> changed = session.changedRecords;
        // seuls les hachages calculés pendant CE build déclenchent une
        // écriture : au chaud sans changement, rien n'est écrit
        boolean hashesChanged = !session.rootsWithNewHashes.isEmpty();
        if (changed.isEmpty() && !hashesChanged
                && !outputCache.hasPendingIndexChanges()) {
            return;
        }
        Map<String, String> typesById = new HashMap<>();
        for (String name : graph.names()) {
            typesById.put(name, graph.task(name).taskType());
        }
        Set<RootName> roots = involvedRoots(graph);
        store.inTransaction(tx -> {
            lastState.writeWithinTransaction(tx, changed, typesById);
            outputCache.persistWithinTransaction(tx);
            for (RootName root : roots) {
                if (!session.rootsWithNewHashes.contains(root.id())) {
                    continue;
                }
                VfsSnapshot current = currentSnapshots.get(root);
                if (current == null) {
                    continue;
                }
                Map<String, String> hashes = hashesByRoot.get(root.id());
                VfsSnapshot merged = hashes == null
                        ? current : current.withContentHashes(hashes);
                tx.replaceSnapshot(root.id(),
                        new ArrayList<>(merged.entries().values()));
            }
            return null;
        });
    }

    /**
     * État d'un build en cours : vues et accumulateurs partagés entre
     * les fils de tâches.
     */
    private final class BuildSession {

        private final TaskGraph graph;
        private final Map<RootName, DirtySet> dirty;
        private final Map<RootName, VfsSnapshot> currentSnapshots;
        private final Collection<ExecutionRecord> changedRecords =
                new ConcurrentLinkedQueue<>();
        private final Set<String> rootsWithNewHashes =
                ConcurrentHashMap.newKeySet();
        private final OutputHasher outputHasher = new OutputHasher(hasher);
        // hachages calculés pendant CE build : toujours valides, même
        // pour un chemin sale ; les maps du moteur ne gardent que les
        // hachages des builds passés, réutilisables pour les chemins
        // propres uniquement
        private final Map<String, Map<String, String>> freshHashes =
                new ConcurrentHashMap<>();
        private final Map<String, Map<String, String>> freshSignals =
                new ConcurrentHashMap<>();
        private final Map<String, Map<String, String>> freshDirHashes =
                new ConcurrentHashMap<>();

        BuildSession(TaskGraph graph, Map<RootName, DirtySet> dirty,
                     Map<RootName, VfsSnapshot> currentSnapshots) {
            this.graph = graph;
            this.dirty = dirty;
            this.currentSnapshots = currentSnapshots;
        }

        TaskResult step(TaskDefinition task, CancelToken cancellation) {
            long start = clock.nanoTime();
            long heapBefore = usedHeap();
            if (cancellation.isCancelled()) {
                return cancelled(task, TaskTimings.ZERO, heapBefore);
            }
            List<Problem> inputProblems = new ArrayList<>();
            ensureInputHashes(task, cancellation, inputProblems);
            long afterHashes = clock.nanoTime();
            if (!inputProblems.isEmpty()) {
                TaskTimings timings = new TaskTimings(
                        afterHashes - start, 0, 0, 0, afterHashes - start);
                return failed(task, inputProblems, timings, heapBefore);
            }
            String fingerprint;
            try {
                fingerprint = TaskFingerprinter.fingerprint(task, hashView());
            } catch (IllegalStateException missing) {
                List<Problem> problems = List.of(Problem.error(
                        "input-read-error", missing.getMessage())
                        .at(task.name()));
                TaskTimings timings = new TaskTimings(
                        afterHashes - start, 0, 0, 0, clock.nanoTime() - start);
                return failed(task, problems, timings, heapBefore);
            }
            long afterFingerprint = clock.nanoTime();

            ExecutionRecord record = lastState.find(task.name());
            if (record != null && record.fingerprint().equals(fingerprint)) {
                boolean outputsDirty = outputsTouched(task);
                if (!outputsDirty || outputsMatchRecord(task, record)) {
                    long afterCheck = clock.nanoTime();
                    TaskTimings timings = new TaskTimings(
                            afterFingerprint - start, afterCheck - afterFingerprint,
                            0, 0, afterCheck - start);
                    return new TaskResult(task.name(), task.taskType(),
                            TaskStatus.UP_TO_DATE, List.of(), timings,
                            heapBefore);
                }
            }
            long afterCheck = clock.nanoTime();

            if (outputCache.has(fingerprint)) {
                try {
                    outputCache.restore(fingerprint, task.outputs(), roots);
                    OutputHasher.Result hashed = outputHasher.hash(
                            task.outputs(), roots);
                    if (!hashed.problems().isEmpty()) {
                        outputCache.drop(fingerprint);
                    } else {
                        ExecutionRecord restored = new ExecutionRecord(
                                task.name(), fingerprint, task.inputs(),
                                task.outputs(), hashed.hashes(),
                                record == null ? 0 : record.durationMs());
                        lastState.put(restored);
                        changedRecords.add(restored);
                        outputCache.touch(fingerprint);
                        long afterRestore = clock.nanoTime();
                        TaskTimings timings = new TaskTimings(
                                afterFingerprint - start,
                                afterCheck - afterFingerprint, 0,
                                afterRestore - afterCheck, afterRestore - start);
                        return new TaskResult(task.name(), task.taskType(),
                                TaskStatus.FROM_CACHE, List.of(), timings,
                                Math.max(heapBefore, usedHeap()));
                    }
                } catch (IOException unreadable) {
                    outputCache.drop(fingerprint);
                    // une entrée illisible est un raté de cache, pas
                    // un échec de build : la tâche s'exécute
                }
            }

            InputDelta delta = inputDelta(task, record);
            TaskContextImpl context = new TaskContextImpl(cancellation, delta);
            long executionStart = clock.nanoTime();
            TaskExecution outcome;
            try {
                outcome = task.execute(context);
            } catch (InterruptedException stopped) {
                long end = clock.nanoTime();
                TaskTimings timings = new TaskTimings(
                        afterFingerprint - start, afterCheck - afterFingerprint,
                        end - executionStart, 0, end - start);
                return cancelled(task, timings, heapBefore);
            } catch (Throwable crash) {
                long end = clock.nanoTime();
                TaskTimings timings = new TaskTimings(
                        afterFingerprint - start, afterCheck - afterFingerprint,
                        end - executionStart, 0, end - start);
                List<Problem> problems = new ArrayList<>(context.reports);
                problems.add(Problem.error("task-crash",
                        crash.getClass().getSimpleName() + ": "
                                + crash.getMessage()).at(task.name()));
                return failed(task, problems, timings, heapBefore);
            }
            long executionEnd = clock.nanoTime();

            if (outcome instanceof TaskExecution.Failed failure) {
                List<Problem> problems = new ArrayList<>(context.reports);
                problems.addAll(failure.diagnostics());
                TaskTimings timings = new TaskTimings(
                        afterFingerprint - start, afterCheck - afterFingerprint,
                        executionEnd - executionStart, 0, executionEnd - start);
                return failed(task, problems, timings, heapBefore);
            }
            TaskExecution.Ok ok = (TaskExecution.Ok) outcome;
            OutputHasher.Result hashed = outputHasher.hash(task.outputs(), roots);
            long outputsEnd = clock.nanoTime();
            if (!hashed.problems().isEmpty()) {
                List<Problem> problems = new ArrayList<>(context.reports);
                problems.addAll(hashed.problems());
                TaskTimings timings = new TaskTimings(
                        afterFingerprint - start, afterCheck - afterFingerprint,
                        executionEnd - executionStart,
                        outputsEnd - executionEnd, outputsEnd - start);
                return failed(task, problems, timings, heapBefore);
            }
            long durationMs = (executionEnd - executionStart) / 1_000_000;
            ExecutionRecord executed = new ExecutionRecord(task.name(),
                    fingerprint, task.inputs(), task.outputs(), hashed.hashes(),
                    durationMs);
            lastState.put(executed);
            changedRecords.add(executed);
            List<Problem> warnings = new ArrayList<>(context.reports);
            warnings.addAll(ok.warnings());
            try {
                outputCache.store(fingerprint, task.outputs(), hashed.produced());
            } catch (IOException cacheFailure) {
                warnings.add(Problem.warn("cache-write-error",
                        "cannot store outputs in cache: " + cacheFailure)
                        .at(task.name()));
            }
            TaskTimings timings = new TaskTimings(
                    afterFingerprint - start, afterCheck - afterFingerprint,
                    executionEnd - executionStart, outputsEnd - executionEnd,
                    outputsEnd - start);
            return new TaskResult(task.name(), task.taskType(),
                    TaskStatus.SUCCEEDED, warnings, timings,
                    Math.max(heapBefore, usedHeap()));
        }

        private void ensureInputHashes(TaskDefinition task, CancelToken cancel,
                                       List<Problem> problems) {
            for (TaskInput input : task.inputs().asList()) {
                if (cancel.isCancelled()) {
                    return;
                }
                if (input instanceof FileInput file && file.hashContent()) {
                    if (hashView().fileHash(file.path()) == null) {
                        try {
                            String hash = hasher.hashStable(
                                    roots.resolve(file.path())).toHex();
                            freshHashes
                                    .computeIfAbsent(file.path().root().id(),
                                            key -> new ConcurrentHashMap<>())
                                    .put(file.path().relative(), hash);
                            hashesByRoot
                                    .computeIfAbsent(file.path().root().id(),
                                            key -> new ConcurrentHashMap<>())
                                    .put(file.path().relative(), hash);
                            rootsWithNewHashes.add(file.path().root().id());
                        } catch (IOException unreadable) {
                            problems.add(Problem.error("input-read-error",
                                    "cannot read input file: " + file.path()
                                            + ": " + unreadable.getMessage())
                                    .at(file.path().toString()));
                        }
                    }
                } else if (input instanceof FileInput file) {
                    if (hashView().fileSignals(file.path()) == null) {
                        try {
                            Path resolved = roots.resolve(file.path());
                            BasicFileAttributes attributes =
                                    Files.readAttributes(resolved,
                                            BasicFileAttributes.class);
                            String signals = signalsOf(attributes);
                            freshSignals
                                    .computeIfAbsent(file.path().root().id(),
                                            key -> new ConcurrentHashMap<>())
                                    .put(file.path().relative(), signals);
                            signalsByRoot
                                    .computeIfAbsent(file.path().root().id(),
                                            key -> new ConcurrentHashMap<>())
                                    .put(file.path().relative(), signals);
                            rootsWithNewHashes.add(file.path().root().id());
                        } catch (IOException unreadable) {
                            problems.add(Problem.error("input-read-error",
                                    "cannot read input file: " + file.path()
                                            + ": " + unreadable.getMessage())
                                    .at(file.path().toString()));
                        }
                    }
                } else if (input instanceof DirInput dir) {
                    if (hashView().dirHash(dir.path()) == null) {
                        try {
                            List<FileStamp> stamps = scanStamps(
                                    roots.resolve(dir.path()), cancel);
                            String treeHash = VfsSnapshot.of(stamps)
                                    .dirHash("").toHex();
                            freshDirHashes
                                    .computeIfAbsent(dir.path().root().id(),
                                            key -> new ConcurrentHashMap<>())
                                    .put(dir.path().relative(), treeHash);
                            dirHashesByRoot
                                    .computeIfAbsent(dir.path().root().id(),
                                            key -> new ConcurrentHashMap<>())
                                    .put(dir.path().relative(), treeHash);
                            rootsWithNewHashes.add(dir.path().root().id());
                        } catch (IOException unreadable) {
                            problems.add(Problem.error("input-read-error",
                                    "cannot scan input directory: " + dir.path()
                                            + ": " + unreadable.getMessage())
                                    .at(dir.path().toString()));
                        }
                    }
                }
            }
        }

        private String signalsOf(BasicFileAttributes attributes) {
            Object key = attributes.fileKey();
            return attributes.size() + ":" + attributes.lastModifiedTime()
                    .to(TimeUnit.NANOSECONDS) + ":"
                    + (key == null ? "" : key.toString());
        }

        /**
         * Parcours séquentiel équivalent au parcours du système de
         * fichiers virtuel : mêmes états de fichiers (liens
         * symboliques non suivis, dossiers exclus), donc mêmes
         * empreintes d'arbre. Séquentiel pour ne pas risque
         * l'interblocage avec le pool des tâches.
         */
        private List<FileStamp> scanStamps(Path directory,
                                           CancelToken cancellation)
                throws IOException {
            List<FileStamp> stamps = new ArrayList<>();
            scanInto(directory, "", stamps, cancellation);
            stamps.sort(Comparator.comparing(FileStamp::path));
            return stamps;
        }

        private void scanInto(Path directory, String relativeDir,
                              List<FileStamp> stamps, CancelToken cancellation)
                throws IOException {
            cancellation.checkCancelled();
            List<String> subdirs = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path child : stream) {
                    cancellation.checkCancelled();
                    BasicFileAttributes attributes = Files.readAttributes(
                            child, BasicFileAttributes.class,
                            java.nio.file.LinkOption.NOFOLLOW_LINKS);
                    String name = child.getFileName().toString();
                    String relative = relativeDir.isEmpty()
                            ? name : relativeDir + "/" + name;
                    if (attributes.isDirectory()) {
                        subdirs.add(relative);
                        continue;
                    }
                    stamps.add(new FileStamp(relative, attributes.size(),
                            attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS),
                            fileKeyOf(attributes), null));
                }
            }
            for (String subdir : subdirs) {
                scanInto(directory.resolve(subdir), subdir, stamps, cancellation);
            }
        }

        private static String fileKeyOf(BasicFileAttributes attributes) {
            Object key = attributes.fileKey();
            return key == null ? null : key.toString();
        }

        private boolean outputsTouched(TaskDefinition task) {
            for (RootedPath output : task.outputs().paths()) {
                DirtySet dirtySet = dirty.get(output.root());
                if (dirtySet == null || dirtySet.isEmpty()) {
                    continue;
                }
                String relative = output.relative();
                if (dirtySet.added().contains(relative)
                        || dirtySet.modified().contains(relative)
                        || dirtySet.removed().contains(relative)) {
                    return true;
                }
                for (String changed : dirtySet.added()) {
                    if (changed.startsWith(relative + "/")) {
                        return true;
                    }
                }
                for (String changed : dirtySet.modified()) {
                    if (changed.startsWith(relative + "/")) {
                        return true;
                    }
                }
                for (String changed : dirtySet.removed()) {
                    if (changed.startsWith(relative + "/")) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean outputsMatchRecord(TaskDefinition task,
                                           ExecutionRecord record) {
            OutputHasher.Result hashed = outputHasher.hash(task.outputs(), roots);
            if (!hashed.problems().isEmpty()) {
                return false;
            }
            for (Map.Entry<String, String> entry : hashed.hashes().entrySet()) {
                String known = record.outputHashes().get(entry.getKey());
                if (known == null || !known.equals(entry.getValue())) {
                    return false;
                }
            }
            return true;
        }

        private boolean isDirty(RootedPath path) {
            DirtySet dirtySet = dirty.get(path.root());
            if (dirtySet == null) {
                return false;
            }
            String relative = path.relative();
            return dirtySet.added().contains(relative)
                    || dirtySet.modified().contains(relative)
                    || dirtySet.removed().contains(relative);
        }

        private InputDelta inputDelta(TaskDefinition task, ExecutionRecord record) {
            Set<RootedPath> added = new LinkedHashSet<>();
            Set<RootedPath> modified = new LinkedHashSet<>();
            Set<RootedPath> removed = new LinkedHashSet<>();
            Set<RootedPath> currentFiles = new LinkedHashSet<>();
            currentFiles.addAll(task.inputs().filePaths());
            currentFiles.addAll(task.inputs().dirPaths());
            Set<RootedPath> previousFiles = new LinkedHashSet<>();
            if (record != null) {
                previousFiles.addAll(record.inputs().filePaths());
                previousFiles.addAll(record.inputs().dirPaths());
            }
            for (RootedPath path : currentFiles) {
                DirtySet dirtySet = dirty.get(path.root());
                String relative = path.relative();
                if (dirtySet != null) {
                    if (dirtySet.added().contains(relative)) {
                        added.add(path);
                    } else if (dirtySet.modified().contains(relative)) {
                        modified.add(path);
                    } else if (dirtySet.removed().contains(relative)) {
                        removed.add(path);
                    }
                }
                if (!previousFiles.contains(path)) {
                    added.add(path);
                }
            }
            for (RootedPath path : previousFiles) {
                if (!currentFiles.contains(path)) {
                    removed.add(path);
                }
            }
            return InputDelta.of(added, modified, removed);
        }

        private TaskFingerprinter.HashView hashView() {
            return new TaskFingerprinter.HashView() {
                @Override
                public String fileHash(RootedPath path) {
                    // un hachage calculé pendant ce build est toujours
                    // valide ; un chemin sale ne réutilise que lui et
                    // l'instantané courant, jamais les hachages des
                    // builds passés
                    Map<String, String> computed = freshHashes.get(
                            path.root().id());
                    if (computed != null) {
                        String hash = computed.get(path.relative());
                        if (hash != null) {
                            return hash;
                        }
                    }
                    boolean stale = isDirty(path);
                    if (!stale) {
                        Map<String, String> known = hashesByRoot.get(
                                path.root().id());
                        if (known != null) {
                            String hash = known.get(path.relative());
                            if (hash != null) {
                                return hash;
                            }
                        }
                    }
                    VfsSnapshot current = currentSnapshots.get(path.root());
                    if (current != null) {
                        FileStamp stamp = current.entry(path.relative());
                        if (stamp != null && stamp.contentHash() != null) {
                            return stamp.contentHash();
                        }
                    }
                    if (!stale) {
                        VfsSnapshot previous = previousSnapshots.get(
                                path.root().id());
                        if (previous != null && current != null) {
                            FileStamp old = previous.entry(path.relative());
                            FileStamp now = current.entry(path.relative());
                            if (old != null && now != null
                                    && old.size() == now.size()
                                    && old.mtimeNs() == now.mtimeNs()
                                    && Objects.equals(old.fileKey(), now.fileKey())
                                    && old.contentHash() != null) {
                                return old.contentHash();
                            }
                        }
                    }
                    return null;
                }

                @Override
                public String fileSignals(RootedPath path) {
                    Map<String, String> fresh = freshSignals.get(
                            path.root().id());
                    if (fresh != null) {
                        String signals = fresh.get(path.relative());
                        if (signals != null) {
                            return signals;
                        }
                    }
                    Map<String, String> computed = isDirty(path) ? null
                            : signalsByRoot.get(path.root().id());
                    if (computed != null) {
                        String signals = computed.get(path.relative());
                        if (signals != null) {
                            return signals;
                        }
                    }
                    VfsSnapshot current = currentSnapshots.get(path.root());
                    if (current == null) {
                        return null;
                    }
                    FileStamp stamp = current.entry(path.relative());
                    if (stamp == null) {
                        return null;
                    }
                    return stamp.size() + ":" + stamp.mtimeNs() + ":"
                            + (stamp.fileKey() == null ? "" : stamp.fileKey());
                }

                @Override
                public String dirHash(RootedPath path) {
                    Map<String, String> fresh = freshDirHashes.get(
                            path.root().id());
                    if (fresh != null) {
                        String hash = fresh.get(path.relative());
                        if (hash != null) {
                            return hash;
                        }
                    }
                    VfsSnapshot current = currentSnapshots.get(path.root());
                    if (current != null) {
                        jo.joide.platform.Hash128 currentHash =
                                current.dirHash(path.relative());
                        if (currentHash != null) {
                            return currentHash.toHex();
                        }
                    }
                    Map<String, String> computed = isDirty(path) ? null
                            : dirHashesByRoot.get(path.root().id());
                    if (computed != null) {
                        String hash = computed.get(path.relative());
                        if (hash != null) {
                            return hash;
                        }
                    }
                    return null;
                }
            };
        }
    }

    private long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static TaskResult failed(TaskDefinition task, List<Problem> problems,
                                     TaskTimings timings, long heapBytes) {
        return new TaskResult(task.name(), task.taskType(), TaskStatus.FAILED,
                problems, timings, heapBytes);
    }

    private static TaskResult cancelled(TaskDefinition task, TaskTimings timings,
                                        long heapBytes) {
        return new TaskResult(task.name(), task.taskType(), TaskStatus.CANCELLED,
                List.of(), timings, heapBytes);
    }

    /**
     * Contexte d'exécution d'une tâche, fourni par le moteur.
     */
    private final class TaskContextImpl implements TaskContext {

        private final CancelToken cancellation;
        private final InputDelta delta;
        private final List<Problem> reports = new CopyOnWriteArrayList<>();

        TaskContextImpl(CancelToken cancellation, InputDelta delta) {
            this.cancellation = Objects.requireNonNull(cancellation,
                    "cancellation");
            this.delta = Objects.requireNonNull(delta, "delta");
        }

        @Override
        public CancelToken cancellation() {
            return cancellation;
        }

        @Override
        public Clock clock() {
            return clock;
        }

        @Override
        public InputDelta inputDelta() {
            return delta;
        }

        @Override
        public Path resolve(RootedPath path) {
            return roots.resolve(path);
        }

        @Override
        public RootSet roots() {
            return roots;
        }

        @Override
        public void report(Problem problem) {
            reports.add(Objects.requireNonNull(problem, "problem"));
        }
    }

    /**
     * Bâtisseur de moteur de build.
     */
    public static final class Builder {

        private RootSet roots;
        private BuildStore store;
        private Clock clock;
        private ExecutorService executor;
        private ScheduledExecutorService samplerExecutor;
        private long samplerIntervalMillis = 50;
        private SchedulingMode mode = SchedulingMode.FAIL_FAST;
        private BuildEvents events = BuildEvents.NOOP;
        private Path outputCacheDir;
        private long outputCacheMaxBytes = DEFAULT_CACHE_MAX_BYTES;
        private Map<String, Long> typeDurations =
                CriticalPaths.defaultTypeDurations();
        private ResourceBudget budget;

        /**
         * @param roots racines de l'installation
         * @return ce bâtisseur
         */
        public Builder roots(RootSet roots) {
            this.roots = Objects.requireNonNull(roots, "roots");
            return this;
        }

        /**
         * @param store store possédé par le fil qui appelle les builds
         * @return ce bâtisseur
         */
        public Builder store(BuildStore store) {
            this.store = Objects.requireNonNull(store, "store");
            return this;
        }

        /**
         * @param clock horloge injectée
         * @return ce bâtisseur
         */
        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        /**
         * @param executor exécuteur borné des tâches, injecté
         * @return ce bâtisseur
         */
        public Builder executor(ExecutorService executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        /**
         * @param executor exécuteur optionnel de l'échantillonneur de
         *                 tas, injecté ; sans exécuteur, seul le pic
         *                 par tâche est mesuré
         * @return ce bâtisseur
         */
        public Builder samplerExecutor(ScheduledExecutorService executor) {
            this.samplerExecutor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        /**
         * @param intervalMillis cadence d'échantillonnage du tas
         * @return ce bâtisseur
         */
        public Builder samplerIntervalMillis(long intervalMillis) {
            if (intervalMillis <= 0) {
                throw new IllegalArgumentException(
                        "sampler interval must be positive: " + intervalMillis);
            }
            this.samplerIntervalMillis = intervalMillis;
            return this;
        }

        /**
         * @param mode mode de traitement des échecs
         * @return ce bâtisseur
         */
        public Builder mode(SchedulingMode mode) {
            this.mode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        /**
         * @param events écouteur d'événements de build
         * @return ce bâtisseur
         */
        public Builder events(BuildEvents events) {
            this.events = Objects.requireNonNull(events, "events");
            return this;
        }

        /**
         * @param dir dossier racine du cache de sorties
         * @return ce bâtisseur
         */
        public Builder outputCacheDir(Path dir) {
            this.outputCacheDir = Objects.requireNonNull(dir, "dir");
            return this;
        }

        /**
         * @param maxBytes plafond de la taille du cache de sorties
         * @return ce bâtisseur
         */
        public Builder outputCacheMaxBytes(long maxBytes) {
            if (maxBytes <= 0) {
                throw new IllegalArgumentException(
                        "cache max bytes must be positive: " + maxBytes);
            }
            this.outputCacheMaxBytes = maxBytes;
            return this;
        }

        /**
         * @param durations durées estimées par type de tâche
         * @return ce bâtisseur
         */
        public Builder typeDurations(Map<String, Long> durations) {
            this.typeDurations = Map.copyOf(
                    Objects.requireNonNull(durations, "durations"));
            return this;
        }

        /**
         * @param budget budget d'admission, dérivé de l'environnement
         *               si absent
         * @return ce bâtisseur
         */
        public Builder budget(ResourceBudget budget) {
            this.budget = Objects.requireNonNull(budget, "budget");
            return this;
        }

        /**
         * @return le moteur construit ; le cache de sorties est ouvert
         * @throws IllegalStateException si un paramètre requis manque
         */
        public BuildEngine build() {
            List<String> missing = new ArrayList<>();
            if (roots == null) {
                missing.add("roots");
            }
            if (store == null) {
                missing.add("store");
            }
            if (clock == null) {
                missing.add("clock");
            }
            if (executor == null) {
                missing.add("executor");
            }
            if (outputCacheDir == null) {
                missing.add("outputCacheDir");
            }
            if (!missing.isEmpty()) {
                throw new IllegalStateException(
                        "missing required parameters: " + String.join(", ",
                                missing));
            }
            return new BuildEngine(this);
        }
    }
}
