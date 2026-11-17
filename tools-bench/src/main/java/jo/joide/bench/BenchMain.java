package jo.joide.bench;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Point d'entrée du bench : génère N exécutions du tier,
 * vérifie que toutes produisent la même racine Merkle (preuve de
 * déterminisme), mesure (temps mur, pic de tas) et optionnellement
 * écrit la baseline.
 *
 * <p>Usage : {@code BenchMain <S|M|L|XL> <outDir> [--runs N]
 * [--baseline <fichier>]}. Le répertoire de sortie est réécrit à chaque
 * exécution (suppression récursive puis génération).</p>
 */
public final class BenchMain {

    private BenchMain() {
    }

    /**
     * @param args voir la Javadoc de classe
     * @throws IOException si une écriture échoue
     */
    public static void main(String[] args) throws IOException {
        System.exit(run(args));
    }

    /**
     * @param args voir la Javadoc de classe
     * @return 0 si toutes les exécutions concordent, 1 sinon
     * @throws IOException si une écriture échoue
     */
    static int run(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: BenchMain <S|M|L|XL> <outDir>"
                    + " [--runs N] [--baseline <file>]");
            return 2;
        }
        TierSpec tier = TierSpec.parse(args[0]);
        Path outDir = Path.of(args[1]);
        int runs = 3;
        Path baselineFile = null;
        for (int i = 2; i < args.length; i++) {
            if ("--runs".equals(args[i]) && i + 1 < args.length) {
                runs = Integer.parseInt(args[++i]);
            } else if ("--baseline".equals(args[i]) && i + 1 < args.length) {
                baselineFile = Path.of(args[++i]);
            }
        }
        runs = Math.max(1, runs);

        List<Double> walls = new ArrayList<>();
        List<Double> cpus = new ArrayList<>();
        ProjectGenerator.Stats stats = null;
        String firstHash = null;
        long peak = 0;
        for (int run = 0; run < runs; run++) {
            deleteRecursively(outDir);
            long start = System.nanoTime();
            long cpuStart = CpuClock.processCpuNanos();
            ProjectGenerator.Stats current;
            long runPeak;
            try (HeapProbe probe = new HeapProbe()) {
                current = ProjectGenerator.generate(tier, outDir);
                runPeak = probe.peakUsedBytes();
            }
            long wallMs = (System.nanoTime() - start) / 1_000_000L;
            long cpuEnd = CpuClock.processCpuNanos();
            walls.add((double) wallMs);
            if (cpuStart >= 0 && cpuEnd >= cpuStart) {
                cpus.add((cpuEnd - cpuStart) / 1_000_000.0);
            }
            TreeHasher.Result tree = TreeHasher.hashTree(outDir);
            if (firstHash == null) {
                firstHash = tree.root;
                stats = current;
            } else if (!firstHash.equals(tree.root)) {
                System.err.println("determinism check FAILED on run "
                        + (run + 1) + ": " + tree.root
                        + " != " + firstHash);
                return 1;
            }
            if (tree.files != tier.totalFiles) {
                System.err.println("file count check FAILED: "
                        + tree.files + " != " + tier.totalFiles);
                return 1;
            }
            peak = Math.max(peak, runPeak);
        }
        double mean = average(walls);
        double cpuMean = cpus.isEmpty() ? 0.0 : average(cpus);
        BenchMetrics metrics = new BenchMetrics(
                tier.name(), tier.seed, runs, stats, firstHash,
                mean, min(walls), max(walls), cpuMean, peak);
        System.out.print(metrics.render());
        if (baselineFile != null) {
            Files.createDirectories(baselineFile.toAbsolutePath().getParent());
            Files.write(baselineFile,
                    metrics.render().getBytes(StandardCharsets.UTF_8));
            System.out.println("baseline ecrite : " + baselineFile);
        }
        return 0;
    }

    private static double average(List<Double> values) {
        double total = 0;
        for (double value : values) {
            total += value;
        }
        return total / values.size();
    }

    private static double min(List<Double> values) {
        double result = Double.MAX_VALUE;
        for (double value : values) {
            result = Math.min(result, value);
        }
        return result;
    }

    private static double max(List<Double> values) {
        double result = Double.MIN_VALUE;
        for (double value : values) {
            result = Math.max(result, value);
        }
        return result;
    }

    /** Suppression récursive : enfants avant parents (ordre inverse). */
    static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        List<Path> paths = new ArrayList<>();
        collectAll(root, paths);
        paths.sort(Comparator.comparing(
                path -> path.toString().length()).reversed());
        for (Path path : paths) {
            Files.delete(path);
        }
    }

    private static void collectAll(Path dir, List<Path> paths)
            throws IOException {
        paths.add(dir);
        List<Path> children = new ArrayList<>();
        try (java.nio.file.DirectoryStream<Path> stream =
                     Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                children.add(child);
            }
        }
        for (Path child : children) {
            if (Files.isDirectory(child)) {
                collectAll(child, paths);
            } else {
                paths.add(child);
            }
        }
    }
}
