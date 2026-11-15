package jo.joide.build.android.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jo.joide.build.android.dex.DexBuckets;
import jo.joide.build.android.dex.Dexer;
import jo.joide.build.android.dex.DexInfo;
import jo.joide.platform.Problem;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.ResourceClaim;
import jo.joide.tasks.TaskContext;
import jo.joide.tasks.TaskDefinition;
import jo.joide.tasks.TaskExecution;
import jo.joide.tasks.TaskInputs;
import jo.joide.tasks.TaskOutputs;

/**
 * Tâche de fusion des dex : les dex de scopes sont répartis en
 * buckets (plafond de méthodes référencées par fichier dex), puis
 * chaque bucket est fusionné en un classes.dex final. La fusion passe
 * par le dexeur à gros tas (fork) — le pic de mémoire de la fusion ne
 * revient jamais à la machine virtuelle du build. La répartition est
 * recalculée à chaque exécution : déterministe, elle ne dépend que
 * des comptages des dex de scopes.
 */
public final class MergeDexTask implements TaskDefinition {

    private final String taskName;
    private final List<RootedPath> scopeDirs;
    private final List<String> scopeLabels;
    private final int minApi;
    private final RootedPath outputDir;
    private final Dexer dexer;
    private final List<RootedPath> classpath;

    /**
     * @param moduleName  nom du module
     * @param scopeDirs   répertoires de dex de scopes (ordre stable)
     * @param scopeLabels libellés des scopes (même ordre)
     * @param minApi      niveau d'API minimum
     * @param classpath   classpath de résolution
     * @param outputDir   répertoire des classes.dex fusionnés
     * @param dexer       service de fusion (gros tas)
     */
    public MergeDexTask(String taskName, List<RootedPath> scopeDirs,
                        List<String> scopeLabels, int minApi,
                        List<RootedPath> classpath, RootedPath outputDir,
                        Dexer dexer) {
        this.taskName = Objects.requireNonNull(taskName);
        this.scopeDirs = List.copyOf(scopeDirs);
        this.scopeLabels = List.copyOf(scopeLabels);
        this.minApi = minApi;
        this.classpath = List.copyOf(classpath);
        this.outputDir = Objects.requireNonNull(outputDir);
        this.dexer = Objects.requireNonNull(dexer);
    }

    @Override
    public String name() {
        return taskName;
    }

    @Override
    public String taskType() {
        return "dex";
    }

    @Override
    public TaskInputs inputs() {
        TaskInputs.Builder builder = TaskInputs.builder();
        for (RootedPath dir : scopeDirs) {
            builder.dir(dir);
        }
        builder.property("joide.dex.minApi", Integer.toString(minApi));
        return builder.build();
    }

    @Override
    public TaskOutputs outputs() {
        return TaskOutputs.builder().dir(outputDir).build();
    }

    @Override
    public String toolVersion() {
        return dexer.toolVersion();
    }

    @Override
    public String optionsFingerprint() {
        return "merge-dex-v1";
    }

    @Override
    public ResourceClaim resourceClaim() {
        return new ResourceClaim(2, 1024, false);
    }

    @Override
    public TaskExecution execute(TaskContext context)
            throws InterruptedException {
        Path out = context.resolve(outputDir);
        try {
            deleteTree(out);
            Files.createDirectories(out);
            List<DexBuckets.Item> items = new ArrayList<>();
            for (int i = 0; i < scopeDirs.size(); i++) {
                Path dir = context.resolve(scopeDirs.get(i));
                try (var stream = Files.list(dir)) {
                    List<Path> dexes = new ArrayList<>();
                    stream.filter(p -> p.getFileName() != null
                            && p.getFileName().toString().endsWith(".dex"))
                            .sorted()
                            .forEach(dexes::add);
                    for (Path dex : dexes) {
                        items.add(new DexBuckets.Item(scopeLabels.get(i),
                                dex.toString(), DexInfo.read(dex)));
                    }
                }
            }
            DexBuckets buckets = DexBuckets.standard();
            List<DexBuckets.Bucket> plan = buckets.plan(items);
            if (!DexBuckets.withinLimits(plan)) {
                return TaskExecution.failed(List.of(Problem.error(
                        "dex.merge.overflow",
                        "a scope exceeds the method limit alone")));
            }
            List<Path> classpathPaths = new ArrayList<>();
            for (RootedPath entry : classpath) {
                classpathPaths.add(context.resolve(entry));
            }
            for (DexBuckets.Bucket bucket : plan) {
                List<Path> inputs = new ArrayList<>();
                for (DexBuckets.Item item : bucket.items) {
                    inputs.add(Path.of(item.dexFile));
                }
                Path bucketDir = out.resolve("bucket-" + bucket.index);
                Files.createDirectories(bucketDir);
                dexer.merge(new Dexer.MergeRequest(inputs, classpathPaths,
                        minApi, bucketDir));
                Path merged = bucketDir.resolve("classes.dex");
                Path target = out.resolve(bucket.dexName());
                Files.move(merged, target);
                deleteTree(bucketDir);
            }
            return TaskExecution.ok(List.of(Problem.info("dex.merge",
                    plan.size() + " bucket(s), " + items.size()
                            + " scope dex(es) merged")));
        } catch (IOException e) {
            return TaskExecution.failed(List.of(Problem.error("dex.merge",
                    e.getMessage())));
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
