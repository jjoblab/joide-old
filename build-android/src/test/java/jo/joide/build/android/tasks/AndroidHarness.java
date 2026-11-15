package jo.joide.build.android.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jo.joide.build.android.ToolchainFixture;
import jo.joide.build.android.bundle.BundleAssembler;
import jo.joide.build.android.dex.Dexer;
import jo.joide.build.android.sign.ApkSignerService;
import jo.joide.build.android.toolchain.Toolchain;
import jo.joide.build.android.toolchain.ToolchainLocator;
import jo.joide.build.android.variant.AndroidVariant;
import jo.joide.platform.Clock;
import jo.joide.platform.RootName;
import jo.joide.platform.RootSet;
import jo.joide.tasks.BuildEngine;
import jo.joide.tasks.BuildReport;
import jo.joide.tasks.SchedulingMode;
import jo.joide.tasks.TaskGraph;
import jo.joide.tasks.TaskResult;
import jo.joide.tasks.TaskStatus;
import jo.joide.vfs.DirtySet;
import jo.joide.vfs.TreeScanner;
import jo.joide.vfs.VfsSnapshot;

/**
 * Harnais du pipeline Android : projet multi-modules sur disque
 * (application + bibliothèque), fabrique de tâches, moteur complet,
 * outillage localisé (variables d'environnement, classpath ou
 * téléchargement épinglé).
 */
final class AndroidHarness implements AutoCloseable {

    final Path project;
    final Path out;
    final Path m2;
    final Path toolchainDir;
    final Toolchain toolchain;
    final ExecutorService pool;
    private final MemoryBuildStore store;
    private final RootSet roots;
    private final AndroidTaskFactory factory;
    private final Path cacheDir;

    AndroidHarness(Path project, Path out, List<AndroidModuleSpec> modules,
                   List<AndroidVariant> variants, Path keystore)
            throws IOException {
        this(project, out, modules, variants, keystore,
                new MemoryBuildStore());
    }

    AndroidHarness(Path project, Path out, List<AndroidModuleSpec> modules,
                   List<AndroidVariant> variants, Path keystore,
                   MemoryBuildStore sharedStore)
            throws IOException {
        this.project = project;
        this.out = out;
        this.m2 = Files.createDirectories(
                out.resolve("../m2").toAbsolutePath().normalize());
        this.cacheDir = out.resolveSibling("cache");
        this.pool = Executors.newFixedThreadPool(4);
        this.store = sharedStore;
        this.toolchainDir = ToolchainFixture.cacheDir();
        this.toolchain = new ToolchainLocator(toolchainDir).locate();
        Dexer scopeDexer = serviceDexer();
        Dexer mergeDexer = new jo.joide.build.android.dex.ForkDexer(
                toolchain.r8Jar, "1g", 600);
        ApkSignerService signer = serviceSigner();
        BundleAssembler assembler = serviceAssembler();
        Map<RootName, Path> anchors = new LinkedHashMap<>();
        anchors.put(RootName.PROJECT, project);
        anchors.put(RootName.OUT, out);
        anchors.put(RootName.M2, m2);
        anchors.put(RootName.SDK, toolchainDir);
        this.roots = RootSet.of(anchors);
        this.factory = new AndroidTaskFactory(out, m2, toolchainDir,
                toolchain, scopeDexer, mergeDexer, signer, assembler, "17")
                .withSourceAnchors(roots)
                .signingPassword("joidepass".toCharArray());
        for (AndroidModuleSpec spec : modules) {
            if (spec.application) {
                spec.variants.clear();
                spec.variants.addAll(variants);
            }
            factory.module(spec);
        }
    }

    private static Dexer serviceDexer() {
        return java.util.ServiceLoader.load(Dexer.class).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "dexeur en processus absent du classpath"));
    }

    private static ApkSignerService serviceSigner() {
        return java.util.ServiceLoader.load(ApkSignerService.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "signataire absent du classpath"));
    }

    private static BundleAssembler serviceAssembler() {
        return java.util.ServiceLoader.load(BundleAssembler.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "assembleur de bundle absent du classpath"));
    }

    TaskGraph graph() throws IOException {
        return factory.buildGraph();
    }

    RootSet roots() {
        return roots;
    }

    BuildReport build(TaskGraph graph, DirtySet dirty)
            throws IOException {
        BuildEngine engine = BuildEngine.builder()
                .roots(roots)
                .store(store)
                .clock(Clock.system())
                .executor(pool)
                .mode(SchedulingMode.FAIL_FAST)
                .budget(new jo.joide.tasks.ResourceBudget(8, 4096))
                .outputCacheDir(cacheDir)
                .build();
        Map<RootName, VfsSnapshot> snapshots = new LinkedHashMap<>();
        Map<RootName, DirtySet> dirtyMap = new LinkedHashMap<>();
        for (Map.Entry<RootName, Path> anchor : roots.asMap().entrySet()) {
            if (Files.isDirectory(anchor.getValue())) {
                snapshots.put(anchor.getKey(), VfsSnapshot.of(
                        TreeScanner.scan(anchor.getValue(), pool,
                                jo.joide.platform.CancelToken.NEVER)));
                dirtyMap.put(anchor.getKey(),
                        anchor.getKey().equals(RootName.PROJECT)
                                ? dirty : DirtySet.empty());
            }
        }
        return engine.build(graph, dirtyMap, snapshots,
                jo.joide.platform.CancelToken.NEVER);
    }

    /** @return les noms des tâches restaurées du cache de sorties */
    static List<String> cachedTasks(BuildReport report) {
        List<String> names = new ArrayList<>();
        for (TaskResult result : report.results()) {
            if (result.status() == TaskStatus.FROM_CACHE) {
                names.add(result.name());
            }
        }
        return names;
    }

    /** @return les noms des tâches exécutées (pas à jour, pas du cache) */
    static List<String> executedTasks(BuildReport report) {
        List<String> names = new ArrayList<>();
        for (TaskResult result : report.results()) {
            if (result.status() == TaskStatus.SUCCEEDED
                    && result.didWork()) {
                names.add(result.name());
            }
        }
        return names;
    }

    /** @return les noms de toutes les tâches du rapport (avec
     *         diagnostics des échecs) */
    static List<String> allTasks(BuildReport report) {
        List<String> names = new ArrayList<>();
        for (TaskResult result : report.results()) {
            StringBuilder line = new StringBuilder(result.name())
                    .append(':').append(result.status());
            if (!result.diagnostics().isEmpty()) {
                line.append(" <<");
                for (jo.joide.platform.Problem problem
                        : result.diagnostics()) {
                    line.append(' ').append(problem.message());
                }
                line.append(">>");
            }
            names.add(line.toString());
        }
        return names;
    }

    @Override
    public void close() {
        pool.shutdown();
        try {
            pool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
