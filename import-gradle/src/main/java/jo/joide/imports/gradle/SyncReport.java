package jo.joide.imports.gradle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import jo.joide.platform.Problem;
import jo.joide.platform.Severity;

/**
 * Le rapport de synchronisation d'un import : tous les problèmes
 * rencontrés, la confiance par module, et un rendu lisible.
 *
 * <p>Le contrat est l'ADR-040 : rien n'est jamais ignoré en silence ;
 * chaque point non résolu de l'import figure ici, avec sa gravité, son
 * code stable et sa localisation. Le rapport est immuable.</p>
 */
public final class SyncReport {

    private final List<Problem> problems;
    private final Map<String, ModuleConfidence> confidence;
    private final Map<String, int[]> counts;

    private SyncReport(List<Problem> problems,
                       Map<String, ModuleConfidence> confidence,
                       Map<String, int[]> counts) {
        this.problems = List.copyOf(problems);
        this.confidence = Map.copyOf(confidence);
        this.counts = new TreeMap<>(counts);
    }

    /**
     * Assemble un rapport à partir des problèmes attribués par module.
     *
     * @param problems problèmes globaux (hors modules), avec
     *                localisation éventuelle
     * @param moduleProblems problèmes par chemin de module, dans
     *                l'ordre d'import
     * @return le rapport assemblé
     */
    public static SyncReport of(List<Problem> problems,
                                Map<String, List<Problem>> moduleProblems) {
        List<Problem> all = new ArrayList<>(problems);
        Map<String, ModuleConfidence> confidence = new LinkedHashMap<>();
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (Map.Entry<String, List<Problem>> entry
                : moduleProblems.entrySet()) {
            List<Problem> moduleProblemsList = entry.getValue();
            all.addAll(moduleProblemsList);
            int errors = 0;
            int warns = 0;
            int infos = 0;
            for (Problem problem : moduleProblemsList) {
                switch (problem.severity()) {
                    case ERROR -> errors++;
                    case WARN -> warns++;
                    case INFO -> infos++;
                    default -> {
                    }
                }
            }
            confidence.put(entry.getKey(),
                    ModuleConfidence.of(errors, warns));
            counts.put(entry.getKey(),
                    new int[]{errors, warns, infos});
        }
        return new SyncReport(all, confidence, counts);
    }

    /** @return tous les problèmes, dans l'ordre d'ajout */
    public List<Problem> problems() {
        return problems;
    }

    /** @return la confiance par chemin de module */
    public Map<String, ModuleConfidence> confidenceByModule() {
        return confidence;
    }

    /** @return le nombre de problèmes d'erreur */
    public int errorCount() {
        return count(Severity.ERROR);
    }

    /** @return le nombre de problèmes d'avertissement */
    public int warnCount() {
        return count(Severity.WARN);
    }

    /** @return le nombre de problèmes d'information */
    public int infoCount() {
        return count(Severity.INFO);
    }

    private int count(Severity severity) {
        int total = 0;
        for (Problem problem : problems) {
            if (problem.severity() == severity) {
                total++;
            }
        }
        return total;
    }

    /** @return vrai si aucune erreur ni aucun avertissement */
    public boolean isClean() {
        return errorCount() == 0 && warnCount() == 0;
    }

    /**
     * @return un rendu texte du rapport, pour la console et les
     *         rapports de phase
     */
    public String render() {
        StringBuilder out = new StringBuilder(512);
        out.append("SyncReport : ").append(problems.size())
                .append(" problems (").append(errorCount())
                .append(" errors, ").append(warnCount())
                .append(" warns, ").append(infoCount())
                .append(" infos)\n");
        for (Map.Entry<String, ModuleConfidence> entry
                : confidence.entrySet()) {
            int[] moduleCounts = counts.get(entry.getKey());
            out.append("  module ").append(entry.getKey())
                    .append(" : ").append(entry.getValue())
                    .append(" (").append(moduleCounts[0]).append("E/")
                    .append(moduleCounts[1]).append("W/")
                    .append(moduleCounts[2]).append("I)\n");
        }
        for (Problem problem : problems) {
            out.append("  [").append(problem.severity()).append("] ")
                    .append(problem.code());
            if (problem.location() != null && !problem.location().isEmpty()) {
                out.append(" at ").append(problem.location());
            }
            out.append(" : ").append(problem.message()).append('\n');
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return "SyncReport[" + problems.size() + " problems, "
                + confidence.size() + " modules]";
    }
}
