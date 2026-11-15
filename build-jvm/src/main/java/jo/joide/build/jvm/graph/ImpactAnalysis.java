package jo.joide.build.jvm.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Analyse d'impact d'un changement sur les sources d'un module :
 * construction du lot initial à compiler, puis propagation au point
 * fixe après chaque lot.
 *
 * <p>Règles d'affectation d'une classe dont l'ABI a changé : les
 * sources qui la référencent ; les sources qui référencent une de
 * ses sous-classes du module (un membre ajouté ou retiré change la
 * résolution à travers la hiérarchie) ; et si la classe est ajoutée
 * ou retirée, les sources du même paquet et celles qui importent le
 * paquet à la demande (la résolution d'un nom simple peut changer).
 * Les index sont construits une fois : le coût d'un lot est
 * proportionnel aux classes changées, jamais au carré des sources.</p>
 */
public final class ImpactAnalysis {

    /** Genre de changement d'une classe. */
    public enum ChangeKind {
        /** La classe est nouvelle dans le lot observé. */
        ADDED,
        /** La classe a disparu du lot observé. */
        REMOVED,
        /** La classe existe des deux côtés mais son ABI diffère. */
        CHANGED
    }

    /** Politique d'incrément et de repli. */
    public static final class Policy {
        /** Part maximale des sources recompilées avant repli complet. */
        public final double maxRecompiledFraction;
        /** Vrai si un processeur d'annotations agrégeant est actif. */
        public final boolean aggregatingProcessor;

        /**
         * @param maxRecompiledFraction seuil de repli (ex. 0,30)
         * @param aggregatingProcessor  processeur agrégeant actif
         */
        public Policy(double maxRecompiledFraction,
                      boolean aggregatingProcessor) {
            this.maxRecompiledFraction = maxRecompiledFraction;
            this.aggregatingProcessor = aggregatingProcessor;
        }

        /** Politique par défaut : 30 %, pas de processeur agrégeant. */
        public static Policy standard() {
            return new Policy(0.30, false);
        }
    }

    private final Policy policy;
    private final int totalSources;
    private final Set<String> compiled = new TreeSet<>();
    private final Map<String, Set<String>> referenceIndex;
    private final Map<String, List<String>> subclassIndex;
    private final Map<String, Set<String>> packageIndex;
    private final Map<String, Set<String>> starIndex;

    private ImpactAnalysis(Policy policy, int totalSources,
                           Map<String, Set<String>> referenceIndex,
                           Map<String, List<String>> subclassIndex,
                           Map<String, Set<String>> packageIndex,
                           Map<String, Set<String>> starIndex) {
        this.policy = policy;
        this.totalSources = totalSources;
        this.referenceIndex = referenceIndex;
        this.subclassIndex = subclassIndex;
        this.packageIndex = packageIndex;
        this.starIndex = starIndex;
    }

    /**
     * @param state        état incrémental du module (sources vivantes)
     * @param policy       politique de repli
     * @param totalSources nombre total de sources actuelles du module
     * @return l'analyse prête à produire son premier lot
     */
    public static ImpactAnalysis of(CompileState state, Policy policy,
                                    int totalSources) {
        Map<String, Set<String>> referenceIndex = new HashMap<>();
        Map<String, List<String>> subclassIndex = new HashMap<>();
        Map<String, Set<String>> packageIndex = new HashMap<>();
        Map<String, Set<String>> starIndex = new HashMap<>();
        for (Map.Entry<String, CompileState.SourceDeps> entry
                : state.sources.entrySet()) {
            String source = entry.getKey();
            CompileState.SourceDeps deps = entry.getValue();
            for (String reference : deps.references) {
                referenceIndex.computeIfAbsent(reference,
                        k -> new TreeSet<>()).add(source);
            }
            for (Map.Entry<String, List<String>> produced
                    : deps.produced.entrySet()) {
                for (String superName : produced.getValue()) {
                    subclassIndex.computeIfAbsent(superName,
                            k -> new ArrayList<>()).add(produced.getKey());
                }
            }
            packageIndex.computeIfAbsent(internalForm(deps.packageName),
                    k -> new TreeSet<>()).add(source);
            for (String starImport : deps.starImports) {
                starIndex.computeIfAbsent(internalForm(starImport),
                        k -> new TreeSet<>()).add(source);
            }
        }
        return new ImpactAnalysis(policy, totalSources, referenceIndex,
                subclassIndex, packageIndex, starIndex);
    }

    /**
     * Lot initial : sources sales et sources touchées par les
     * changements d'ABI du classpath.
     *
     * @param added            sources ajoutées
     * @param modified         sources modifiées
     * @param classpathChanges classes du classpath dont l'ABI a changé
     * @return le lot à compiler, ou {@code null} pour un repli complet
     */
    public Set<String> initialBatch(Set<String> added,
                                    Set<String> modified,
                                    Map<String, ChangeKind> classpathChanges) {
        Set<String> batch = new TreeSet<>();
        batch.addAll(added);
        batch.addAll(modified);
        batch.addAll(affectedBy(classpathChanges));
        if (policy.aggregatingProcessor && !batch.isEmpty()) {
            return null; // processeur agrégeant : tout ou rien
        }
        return checkThreshold(batch);
    }

    /**
     * Lot suivant, après compilation d'un lot : les sources touchées
     * par les changements d'ABI des classes que le lot vient de
     * produire.
     *
     * @param batchSources    sources du lot qui vient de compiler
     * @param producedChanges classes produites dont l'ABI a changé
     * @return le prochain lot, vide si point fixe, {@code null} pour
     *         un repli complet
     */
    public Set<String> nextBatch(Set<String> batchSources,
                                 Map<String, ChangeKind> producedChanges) {
        compiled.addAll(batchSources);
        Set<String> affected = affectedBy(producedChanges);
        affected.removeAll(compiled);
        return checkThreshold(affected);
    }

    /** @return les sources déjà compilées par cette analyse */
    public Set<String> compiledSoFar() {
        return new TreeSet<>(compiled);
    }

    /**
     * Applique le seuil : au-delà de la part autorisée, repli complet.
     */
    private Set<String> checkThreshold(Set<String> batch) {
        if (batch.isEmpty()) {
            return batch;
        }
        int cumulative = compiled.size() + batch.size();
        if (totalSources > 0
                && cumulative > policy.maxRecompiledFraction * totalSources) {
            return null;
        }
        return batch;
    }

    /**
     * Sources affectées par un ensemble de changements de classes :
     * références directes, références aux sous-classes du module,
     * paquet touché (sources du paquet et importateurs « * »).
     */
    private Set<String> affectedBy(Map<String, ChangeKind> changes) {
        Set<String> affected = new TreeSet<>();
        for (Map.Entry<String, ChangeKind> change : changes.entrySet()) {
            String className = change.getKey();
            ChangeKind kind = change.getValue();
            Set<String> direct = referenceIndex.get(className);
            if (direct != null) {
                affected.addAll(direct);
            }
            List<String> subclasses = subclassIndex.get(className);
            if (subclasses != null) {
                for (String subclass : subclasses) {
                    Set<String> viaSubclass = referenceIndex.get(subclass);
                    if (viaSubclass != null) {
                        affected.addAll(viaSubclass);
                    }
                }
            }
            if (kind == ChangeKind.CHANGED) {
                continue; // la résolution de nom ne change pas
            }
            String packageName = packageOf(className);
            Set<String> samePackage = packageIndex.get(packageName);
            if (samePackage != null) {
                affected.addAll(samePackage);
            }
            Set<String> starImporters = starIndex.get(packageName);
            if (starImporters != null) {
                affected.addAll(starImporters);
            }
        }
        return affected;
    }

    private static String packageOf(String internalClassName) {
        int slash = internalClassName.lastIndexOf('/');
        return slash < 0 ? "" : internalClassName.substring(0, slash);
    }

    private static String internalForm(String dotted) {
        return dotted.replace('.', '/');
    }

    /** @return un ensemble ordonné vide, pratique des tests */
    public static Set<String> emptySet() {
        return new TreeSet<>();
    }
}
