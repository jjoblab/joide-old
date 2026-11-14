package jo.joide.bench;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Métriques de bench et codage TOML des baselines (ADR-014).
 *
 * <p>Le format est un sous-ensemble plat — sections entre crochets,
 * scalaires uniquement — rendu et relu par cette seule classe. Aucun
 * horodatage : une baseline ne doit différer que si les mesures ou la
 * graine changent (§8.5 du plan P0).</p>
 */
public final class BenchMetrics {

    /** Clé de schéma du fichier de baseline. */
    public static final String SCHEMA = "joide.baseline/1";

    /** Valeurs déterministes (comparaison exacte). */
    public final String treeHash;
    public final int files;
    public final int dirs;
    public final long bytes;
    public final int javaFiles;
    public final int kotlinFiles;
    public final int otherFiles;
    /** Valeurs temporelles (temps mur informatif ; temps CPU = critère ±10 %). */
    public final double wallMsMean;
    public final double wallMsMin;
    public final double wallMsMax;
    public final double cpuMsMean;
    public final long heapPeakBytes;
    /** Contexte. */
    public final String tier;
    public final long seed;
    public final int runs;
    public final int cores;
    public final String javaVersion;

    /**
     * @param tier       nom du tier (S, M, L, XL)
     * @param seed       graine maîtresse
     * @param runs       nombre d'exécutions mesurées
     * @param stats      statistiques déterministes
     * @param treeHash   racine Merkle de l'arbre généré
     * @param wallMsMean temps mur moyen (ms, informatif)
     * @param wallMsMin  temps mur minimal (ms)
     * @param wallMsMax  temps mur maximal (ms)
     * @param cpuMsMean  temps CPU moyen du processus (ms, critère)
     * @param heapPeak   pic de tas observé (octets)
     */
    public BenchMetrics(String tier, long seed, int runs,
                        ProjectGenerator.Stats stats, String treeHash,
                        double wallMsMean, double wallMsMin, double wallMsMax,
                        double cpuMsMean, long heapPeak) {
        this.tier = Objects.requireNonNull(tier, "tier");
        this.seed = seed;
        this.runs = runs;
        this.treeHash = Objects.requireNonNull(treeHash, "treeHash");
        this.files = stats.files;
        this.dirs = stats.dirs;
        this.bytes = stats.bytes;
        this.javaFiles = stats.javaFiles;
        this.kotlinFiles = stats.kotlinFiles;
        this.otherFiles = stats.otherFiles;
        this.wallMsMean = wallMsMean;
        this.wallMsMin = wallMsMin;
        this.wallMsMax = wallMsMax;
        this.cpuMsMean = cpuMsMean;
        this.heapPeakBytes = heapPeak;
        this.cores = Runtime.getRuntime().availableProcessors();
        this.javaVersion = System.getProperty("java.version", "unknown");
    }

    private BenchMetrics(Map<String, String> values) {
        this.tier = values.get("tier");
        this.seed = Long.parseLong(values.get("seed"));
        this.runs = Integer.parseInt(values.get("runs"));
        this.treeHash = values.get("deterministic.tree-hash");
        this.files = Integer.parseInt(values.get("deterministic.files"));
        this.dirs = Integer.parseInt(values.get("deterministic.dirs"));
        this.bytes = Long.parseLong(values.get("deterministic.bytes"));
        this.javaFiles = Integer.parseInt(
                values.get("deterministic.java-files"));
        this.kotlinFiles = Integer.parseInt(
                values.get("deterministic.kotlin-files"));
        this.otherFiles = Integer.parseInt(
                values.get("deterministic.other-files"));
        this.wallMsMean = Double.parseDouble(values.get("temporal.wall-ms-mean"));
        this.wallMsMin = Double.parseDouble(values.get("temporal.wall-ms-min"));
        this.wallMsMax = Double.parseDouble(values.get("temporal.wall-ms-max"));
        this.cpuMsMean = Double.parseDouble(values.get("temporal.cpu-ms-mean"));
        this.heapPeakBytes = Long.parseLong(
                values.get("temporal.heap-peak-bytes"));
        this.cores = Integer.parseInt(values.get("environment.cores"));
        this.javaVersion = values.get("environment.java");
    }

    /** @return le rendu TOML complet des métriques */
    public String render() {
        StringBuilder out = new StringBuilder(512);
        out.append("# Baseline tools-bench (ADR-014) — regenerable : ")
                .append("./gradlew :tools-bench:bench -Ptier=")
                .append(tier).append("\n");
        out.append("schema = \"").append(SCHEMA).append("\"\n");
        out.append("tier = \"").append(tier).append("\"\n");
        out.append("seed = ").append(seed).append("\n");
        out.append("runs = ").append(runs).append("\n\n");
        out.append("[deterministic]\n");
        out.append("files = ").append(files).append("\n");
        out.append("dirs = ").append(dirs).append("\n");
        out.append("bytes = ").append(bytes).append("\n");
        out.append("java-files = ").append(javaFiles).append("\n");
        out.append("kotlin-files = ").append(kotlinFiles).append("\n");
        out.append("other-files = ").append(otherFiles).append("\n");
        out.append("tree-hash = \"").append(treeHash).append("\"\n\n");
        out.append("[temporal]\n");
        out.append("wall-ms-mean = ")
                .append(String.format(Locale.ROOT, "%.1f", wallMsMean))
                .append("\n");
        out.append("wall-ms-min = ")
                .append(String.format(Locale.ROOT, "%.1f", wallMsMin))
                .append("\n");
        out.append("wall-ms-max = ")
                .append(String.format(Locale.ROOT, "%.1f", wallMsMax))
                .append("\n");
        out.append("cpu-ms-mean = ")
                .append(String.format(Locale.ROOT, "%.1f", cpuMsMean))
                .append("\n");
        out.append("heap-peak-bytes = ").append(heapPeakBytes).append("\n\n");
        out.append("[environment]\n");
        out.append("cores = ").append(cores).append("\n");
        out.append("java = \"").append(javaVersion).append("\"\n");
        return out.toString();
    }

    /**
     * @param file fichier de baseline
     * @return les métriques lues
     * @throws IOException si le fichier est illisible ou incohérent
     */
    public static BenchMetrics parse(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        Map<String, String> values = new LinkedHashMap<>();
        String section = "";
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1);
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                throw new IOException("baseline line without '=': " + raw);
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"")
                    && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(section.isEmpty()
                    ? key : section + "." + key, value);
        }
        if (!SCHEMA.equals(values.get("schema"))) {
            throw new IOException("unexpected baseline schema: "
                    + values.get("schema"));
        }
        try {
            return new BenchMetrics(values);
        } catch (RuntimeException e) {
            throw new IOException("incomplete baseline: " + file, e);
        }
    }

    /** @throws IOException si une valeur attendue manque */
    public void requireComplete() throws IOException {
        try {
            Objects.requireNonNull(tier, "tier");
            Objects.requireNonNull(treeHash, "treeHash");
            Objects.requireNonNull(javaVersion, "javaVersion");
        } catch (NullPointerException e) {
            throw new IOException("incomplete metrics", e);
        }
    }
}
