package jo.joide.model;

import java.util.List;
import java.util.Optional;
import jo.joide.platform.Problem;

/**
 * Résultat d'un chargement d'espace de travail : l'espace chargé (ou
 * rien si la racine elle-même est illisible) et les problèmes
 * rencontrés, module par module.
 */
public final class WorkspaceLoad {

    private final Workspace workspace;
    private final List<Problem> problems;

    /**
     * @param workspace espace chargé, ou {@code null}
     * @param problems  problèmes rencontrés
     */
    public WorkspaceLoad(Workspace workspace, List<Problem> problems) {
        this.workspace = workspace;
        this.problems = List.copyOf(problems);
    }

    /**
     * @return l'espace de travail, ou vide si la racine était illisible
     */
    public Optional<Workspace> workspace() {
        return Optional.ofNullable(workspace);
    }

    /** @return tous les problèmes rencontrés */
    public List<Problem> problems() {
        return problems;
    }

    /** @return les problèmes de niveau erreur */
    public List<Problem> errors() {
        List<Problem> errors = new java.util.ArrayList<>();
        for (Problem problem : problems) {
            if (problem.isError()) {
                errors.add(problem);
            }
        }
        return errors;
    }

    /** @return vrai si aucune erreur bloquante n'a été rencontrée */
    public boolean isUsable() {
        return workspace != null && errors().isEmpty();
    }
}
