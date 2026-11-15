package jo.joide.tasks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Export du rapport de build en « build scan » local : un fichier
 * JSON détaillé et un résumé lisible.
 *
 * <p>Le JSON est écrit à la main, sans bibliothèque tierce : la
 * structure est simple et les chaînes sont échappées.</p>
 */
public final class BuildScanWriter {

    private BuildScanWriter() {
        // classe utilitaire
    }

    /**
     * Écrit l'export JSON du rapport.
     *
     * @param report rapport à exporter
     * @param target fichier cible, créé avec ses parents
     * @throws IOException si l'écriture échoue
     */
    public static void writeJson(BuildReport report, Path target)
            throws IOException {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(target, "target");
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.writeString(target, toJson(report), StandardCharsets.UTF_8);
    }

    /**
     * Écrit le résumé lisible du rapport.
     *
     * @param report rapport à exporter
     * @param target fichier cible
     * @throws IOException si l'écriture échoue
     */
    public static void writeSummary(BuildReport report, Path target)
            throws IOException {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(target, "target");
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.writeString(target, summary(report), StandardCharsets.UTF_8);
    }

    /**
     * @param report rapport à sérialiser
     * @return la représentation JSON du rapport
     */
    public static String toJson(BuildReport report) {
        Objects.requireNonNull(report, "report");
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"wallStartMillis\": ").append(report.wallStartMillis())
                .append(",\n");
        json.append("  \"wallNanos\": ").append(report.wallNanos()).append(",\n");
        json.append("  \"peakHeapBytes\": ").append(report.peakHeapBytes())
                .append(",\n");
        json.append("  \"tasks\": [\n");
        List<TaskResult> results = report.results();
        for (int i = 0; i < results.size(); i++) {
            TaskResult result = results.get(i);
            json.append("    {\n");
            json.append("      \"name\": \"").append(escape(result.name()))
                    .append("\",\n");
            json.append("      \"type\": \"").append(escape(result.taskType()))
                    .append("\",\n");
            json.append("      \"status\": \"").append(result.status())
                    .append("\",\n");
            json.append("      \"usedHeapBytes\": ").append(result.usedHeapBytes())
                    .append(",\n");
            json.append("      \"timings\": {\n");
            json.append("        \"fingerprintNanos\": ")
                    .append(result.timings().fingerprintNanos()).append(",\n");
            json.append("        \"checkNanos\": ")
                    .append(result.timings().checkNanos()).append(",\n");
            json.append("        \"executionNanos\": ")
                    .append(result.timings().executionNanos()).append(",\n");
            json.append("        \"outputHashNanos\": ")
                    .append(result.timings().outputHashNanos()).append(",\n");
            json.append("        \"totalNanos\": ")
                    .append(result.timings().totalNanos()).append("\n");
            json.append("      },\n");
            json.append("      \"diagnostics\": [");
            for (int d = 0; d < result.diagnostics().size(); d++) {
                jo.joide.platform.Problem problem = result.diagnostics().get(d);
                if (d > 0) {
                    json.append(", ");
                }
                json.append("\n        {\"severity\": \"")
                        .append(problem.severity())
                        .append("\", \"code\": \"").append(escape(problem.code()))
                        .append("\", \"message\": \"")
                        .append(escape(problem.message()))
                        .append("\", \"location\": \"")
                        .append(escape(problem.location())).append("\"}");
            }
            if (!result.diagnostics().isEmpty()) {
                json.append("\n      ");
            }
            json.append("]\n");
            json.append("    }");
            if (i < results.size() - 1) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    /**
     * @param report rapport à résumer
     * @return le résumé lisible, une ligne par information clé
     */
    public static String summary(BuildReport report) {
        Objects.requireNonNull(report, "report");
        StringBuilder text = new StringBuilder();
        text.append("Build : ")
                .append(report.count(TaskStatus.SUCCEEDED)).append(" exécutées, ")
                .append(report.count(TaskStatus.UP_TO_DATE)).append(" à jour, ")
                .append(report.count(TaskStatus.FROM_CACHE)).append(" du cache, ")
                .append(report.count(TaskStatus.FAILED)).append(" échecs, ")
                .append(report.count(TaskStatus.CANCELLED)).append(" annulées, ")
                .append(report.count(TaskStatus.SKIPPED_UPSTREAM))
                .append(" sautées\n");
        text.append("Durée mur : ").append(report.wallNanos() / 1_000_000)
                .append(" ms ; pic de tas : ")
                .append(report.peakHeapBytes() / (1024 * 1024)).append(" Mo\n");
        List<TaskResult> slowest = report.results().stream()
                .filter(result -> result.timings().totalNanos() > 0)
                .sorted((a, b) -> Long.compare(b.timings().totalNanos(),
                        a.timings().totalNanos()))
                .limit(10)
                .toList();
        if (!slowest.isEmpty()) {
            text.append("Tâches les plus longues :\n");
            for (TaskResult result : slowest) {
                text.append("  ").append(result.name()).append(" (")
                        .append(result.status()).append(") : ")
                        .append(result.timings().totalNanos() / 1_000_000)
                        .append(" ms\n");
            }
        }
        if (!report.failures().isEmpty()) {
            text.append("Échecs :\n");
            for (TaskResult failure : report.failures()) {
                for (jo.joide.platform.Problem problem : failure.diagnostics()) {
                    text.append("  ").append(failure.name()).append(" : ")
                            .append(problem.message());
                    if (problem.location() != null && !problem.location()
                            .isEmpty()) {
                        text.append(" (").append(problem.location()).append(')');
                    }
                    text.append('\n');
                }
            }
        }
        return text.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
