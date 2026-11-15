package jo.joide.tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jo.joide.platform.RootedPath;
import jo.joide.tasks.TaskOutput.DirOutput;
import jo.joide.tasks.TaskOutput.FileOutput;

/**
 * Ensemble ordonné et dédupliqué des sorties d'une tâche.
 */
public final class TaskOutputs {

    private final List<TaskOutput> outputs;

    private TaskOutputs(List<TaskOutput> outputs) {
        this.outputs = outputs;
    }

    /**
     * @return un bâtisseur de sorties
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return les sorties dans l'ordre d'ajout, non modifiables
     */
    public List<TaskOutput> asList() {
        return outputs;
    }

    /**
     * @return les chemins ancrés des sorties, dans l'ordre d'ajout
     */
    public List<RootedPath> paths() {
        List<RootedPath> paths = new ArrayList<>();
        for (TaskOutput output : outputs) {
            if (output instanceof FileOutput file) {
                paths.add(file.path());
            } else if (output instanceof DirOutput dir) {
                paths.add(dir.path());
            }
        }
        return paths;
    }

    /**
     * @return vrai si la tâche ne déclare aucune sortie
     */
    public boolean isEmpty() {
        return outputs.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TaskOutputs)) {
            return false;
        }
        return outputs.equals(((TaskOutputs) other).outputs);
    }

    @Override
    public int hashCode() {
        return outputs.hashCode();
    }

    @Override
    public String toString() {
        return "TaskOutputs" + outputs;
    }

    /**
     * Bâtisseur de sorties de tâche.
     */
    public static final class Builder {

        private final List<TaskOutput> outputs = new ArrayList<>();

        /**
         * Déclare un fichier produit.
         *
         * @param path chemin ancré du fichier
         * @return ce bâtisseur
         */
        public Builder file(RootedPath path) {
            return add(new FileOutput(path));
        }

        /**
         * Déclare un dossier produit.
         *
         * @param path chemin ancré du dossier
         * @return ce bâtisseur
         */
        public Builder dir(RootedPath path) {
            return add(new DirOutput(path));
        }

        /**
         * Ajoute une sortie déjà construite ; un doublon est ignoré.
         *
         * @param output sortie à ajouter
         * @return ce bâtisseur
         */
        public Builder add(TaskOutput output) {
            Objects.requireNonNull(output, "output");
            for (TaskOutput existing : outputs) {
                if (existing.equals(output)) {
                    return this;
                }
            }
            outputs.add(output);
            return this;
        }

        /**
         * @return les sorties construites, immuables
         */
        public TaskOutputs build() {
            return new TaskOutputs(Collections.unmodifiableList(
                    new ArrayList<>(outputs)));
        }
    }
}
