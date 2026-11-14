package jo.joide.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Comparaison aux baselines commitées (ADR-014, §8 du prompt).
 *
 * <p>Les valeurs déterministes (hash d'arbre, comptages, octets, graine)
 * doivent être exactement égales ; les valeurs temporelles (temps mur,
 * pic de tas) tolèrent ±10 % — plus un plancher absolu de 50 ms sur le
 * temps mur : sous le seuil de la seconde, le bruit de l'ordonnanceur
 * dépasse proportionnellement 10 % (le §8 du prompt vise des budgets de
 * 1,5 s à 30 s, où ce plancher est négligeable). L'environnement (cœurs,
 * JVM) est rapporté mais ne fait pas échouer : les baselines temporelles
 * se régénèrent sur la machine de référence.</p>
 */
public final class BaselineCompare {

    private static final double TOLERANCE = 0.10;

    /** Plancher absolu (ms) pour le temporel : bruit sous la seconde. */
    private static final double WALL_SLACK_MS = 50.0;

    private BaselineCompare() {
    }

    /**
     * @param args {@code <S|M|L|XL> <répertoire des baselines> <répertoire
     *             de travail>}
     */
    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("usage: BaselineCompare <S|M|L|XL>"
                    + " <baselineDir> <scratchDir>");
            System.exit(2);
        }
        try {
            System.exit(compare(TierSpec.parse(args[0]), Path.of(args[1]),
                    Path.of(args[2])));
        } catch (IOException e) {
            System.err.println("baseline comparison failed: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * @param tier        tier à vérifier
     * @param baselineDir répertoire des baselines commitées
     * @param scratchDir  répertoire de travail — MÊME système de fichiers
     *                    que la capture de baseline (mesure comparable) ;
     *                    créé si absent, réécrit à chaque exécution
     * @return 0 si conforme, 1 sinon
     * @throws IOException si la génération ou la lecture échoue
     */
    public static int compare(TierSpec tier, Path baselineDir, Path scratchDir)
            throws IOException {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(baselineDir, "baselineDir");
        Path baselineFile = baselineDir.resolve(
                tier.name().toLowerCase(java.util.Locale.ROOT) + ".toml");
        if (!Files.isRegularFile(baselineFile)) {
            System.err.println("baseline missing: " + baselineFile);
            return 1;
        }
        BenchMetrics baseline = BenchMetrics.parse(baselineFile);
        if (!tier.name().equals(baseline.tier)
                || tier.seed != baseline.seed) {
            System.err.println("baseline tier/seed mismatch for "
                    + tier.name());
            return 1;
        }
        // Hygiène de mesure (ADR-014) : le répertoire de travail vit sur le
        // même système de fichiers que la capture de baseline — la
        // comparaison temporelle ne doit pas mesurer un disque différent.
        Path scratch = scratchDir;
        Files.createDirectories(scratch);
        try {
            // Même protocole que la création de baseline : trois
            // exécutions, moyenne pour le temporel, maximum pour le tas.
            // Chaque exécution revérifie le déterminisme (hash identique).
            ProjectGenerator.Stats stats = null;
            String firstHash = null;
            long wallTotal = 0;
            double cpuTotal = 0;
            int cpuRuns = 0;
            long peak = 0;
            final int runs = 3;
            for (int run = 0; run < runs; run++) {
                BenchMain.deleteRecursively(scratch);
                long start = System.nanoTime();
                long cpuStart = CpuClock.processCpuNanos();
                ProjectGenerator.Stats current;
                long runPeak;
                try (HeapProbe probe = new HeapProbe()) {
                    current = ProjectGenerator.generate(tier, scratch);
                    runPeak = probe.peakUsedBytes();
                }
                wallTotal += (System.nanoTime() - start) / 1_000_000L;
                long cpuEnd = CpuClock.processCpuNanos();
                if (cpuStart >= 0 && cpuEnd >= cpuStart) {
                    cpuTotal += (cpuEnd - cpuStart) / 1_000_000.0;
                    cpuRuns++;
                }
                TreeHasher.Result tree = TreeHasher.hashTree(scratch);
                if (firstHash == null) {
                    firstHash = tree.root;
                    stats = current;
                } else if (!firstHash.equals(tree.root)) {
                    System.err.println("determinism check FAILED on run "
                            + (run + 1) + ": " + tree.root
                            + " != " + firstHash);
                    return 1;
                }
                peak = Math.max(peak, runPeak);
            }
            long wallMs = wallTotal / runs;
            double cpuMs = cpuRuns == 0 ? 0.0 : cpuTotal / cpuRuns;
            List<String> failures = new java.util.ArrayList<>();
            checkExact(failures, "files", stats.files, baseline.files);
            checkExact(failures, "dirs", stats.dirs, baseline.dirs);
            checkExact(failures, "bytes", stats.bytes, baseline.bytes);
            checkExact(failures, "java-files", stats.javaFiles,
                    baseline.javaFiles);
            checkExact(failures, "kotlin-files", stats.kotlinFiles,
                    baseline.kotlinFiles);
            checkExact(failures, "other-files", stats.otherFiles,
                    baseline.otherFiles);
            if (!firstHash.equals(baseline.treeHash)) {
                failures.add("tree-hash: " + firstHash + " != "
                        + baseline.treeHash);
            }
            // Critère temporel : temps CPU (insensible au bridage de
            // l'ordonnanceur) ; le temps mur reste informatif dans la
            // baseline. Si le temps CPU est indisponible, repli sur le
            // temps mur (marge absolue de 50 ms).
            if (cpuMs > 0 && baseline.cpuMsMean > 0) {
                checkTolerance(failures, "cpu-ms", cpuMs,
                        baseline.cpuMsMean, 0.0);
            } else {
                checkTolerance(failures, "wall-ms", wallMs,
                        baseline.wallMsMean, WALL_SLACK_MS);
            }
            checkTolerance(failures, "heap-peak-bytes", peak,
                    baseline.heapPeakBytes, 0.0);
            System.out.println("environnement : " + baseline.cores
                    + " cœurs (baseline), JVM " + baseline.javaVersion
                    + " ; courant : " + Runtime.getRuntime()
                    .availableProcessors() + " cœurs, JVM "
                    + System.getProperty("java.version", "unknown"));
            if (failures.isEmpty()) {
                System.out.println("regressionTest[" + tier.name()
                        + "] : OK (hash " + firstHash.substring(0, 12)
                        + "…)");
                return 0;
            }
            System.err.println("regressionTest[" + tier.name()
                    + "] : ECHEC");
            for (String failure : failures) {
                System.err.println("  - " + failure);
            }
            return 1;
        } finally {
            BenchMain.deleteRecursively(scratch);
        }
    }

    private static void checkExact(List<String> failures, String key,
                                   long actual, long expected) {
        if (actual != expected) {
            failures.add(key + ": " + actual + " != " + expected);
        }
    }

    private static void checkTolerance(List<String> failures, String key,
                                       double actual, double expected,
                                       double absoluteSlack) {
        if (expected <= 0) {
            return; // baseline sans valeur : rien à comparer
        }
        double bound = expected * (1 + TOLERANCE) + absoluteSlack;
        if (actual > bound) {
            failures.add(key + ": " + actual + " > " + bound
                    + " (baseline " + expected + ", tolerance 10 %"
                    + (absoluteSlack > 0 ? " + " + (long) absoluteSlack
                    + " ms de marge" : "") + ")");
        }
    }

}
