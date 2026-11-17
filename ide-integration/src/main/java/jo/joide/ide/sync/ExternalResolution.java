package jo.joide.ide.sync;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jo.joide.deps.aar.AarContents;
import jo.joide.deps.resolve.ResolvedArtifact;
import jo.joide.deps.resolve.ResolutionResult;

/**
 * Dépendances externes résolues d'un workspace : pour chaque
 * « groupe:artefact », l'artefact retenu (fichier, nature AAR,
 * contenu extrait) — le résultat du résolveur P4, indexé pour
 * l'adaptation.
 *
 * <p>Immuable ; les problèmes de résolution sont transportés
 * (jamais ignorés en silence), la version retenue par « groupe:artefact »
 * est celle du point fixe du résolveur.</p>
 */
public final class ExternalResolution {

    private final Map<String, Resolved> byGa;
    private final List<String> problems;

    private ExternalResolution(Map<String, Resolved> byGa,
            List<String> problems) {
        this.byGa = Map.copyOf(byGa);
        this.problems = List.copyOf(problems);
    }

    /**
     * @param result résultat du résolveur P4
     * @return la résolution indexée par « groupe:artefact »
     */
    public static ExternalResolution of(ResolutionResult result) {
        Map<String, Resolved> byGa = new LinkedHashMap<>();
        for (ResolvedArtifact artifact : result.artifacts) {
            byGa.put(artifact.ga(), new Resolved(
                    artifact.file, artifact.aar, artifact.aarContents));
        }
        List<String> problems = new ArrayList<>();
        result.problems.forEach(p -> problems.add(p.toString()));
        return new ExternalResolution(byGa, problems);
    }

    /** @return une résolution vide (aucune dépendance résolue) */
    public static ExternalResolution empty() {
        return new ExternalResolution(Map.of(), List.of());
    }

    /** @return les problèmes de résolution, jamais nuls */
    public List<String> problems() {
        return problems;
    }

    /**
     * @param ga « groupe:artefact »
     * @return l'artefact résolu, ou vide si la dépendance n'a pas été
     *         résolue (l'appelant signale, jamais d'entrée muette)
     */
    public Optional<Resolved> find(String ga) {
        return Optional.ofNullable(byGa.get(ga));
    }

    /** @return les « groupe:artefact » résolus */
    public List<String> resolvedGas() {
        return List.copyOf(byGa.keySet());
    }

    /**
     * @param extra problèmes additionnels à transporter
     * @return une copie avec ces problèmes en plus (jamais de mutation)
     */
    public ExternalResolution withExtraProblems(List<String> extra) {
        List<String> merged = new ArrayList<>(problems);
        merged.addAll(extra);
        return new ExternalResolution(byGa, merged);
    }

    /** Une dépendance externe résolue, avec son contenu AAR extrait. */
    public static final class Resolved {

        /** Fichier de l'artefact (jar ou AAR), jamais nul. */
        public final Path file;
        /** Vrai si l'artefact est un AAR. */
        public final boolean aar;
        /** Contenu extrait de l'AAR, ou {@code null} (jar ou AAR brut). */
        public final AarContents aarContents;

        Resolved(Path file, boolean aar, AarContents aarContents) {
            this.file = file;
            this.aar = aar;
            this.aarContents = aarContents;
        }
    }

    /** Montage de résolution pour les tests et les faux. */
    public static final class Builder {

        private final Map<String, Resolved> byGa = new LinkedHashMap<>();
        private final List<String> problems = new ArrayList<>();

        /**
         * @param ga      « groupe:artefact »
         * @param file    fichier de l'artefact
         * @param aar     vrai si AAR
         * @param contenu contenu extrait (ou {@code null})
         * @return ce montage
         */
        public Builder add(String ga, Path file, boolean aar,
                AarContents contenu) {
            byGa.put(ga, new Resolved(file, aar, contenu));
            return this;
        }

        /**
         * @param problem problème à transporter
         * @return ce montage
         */
        public Builder problem(String problem) {
            problems.add(problem);
            return this;
        }

        /** @return la résolution montée */
        public ExternalResolution build() {
            return new ExternalResolution(byGa, problems);
        }
    }
}
