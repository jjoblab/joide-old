package jo.joide.tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jo.joide.platform.RootedPath;
import jo.joide.tasks.TaskInput.DirInput;
import jo.joide.tasks.TaskInput.FileInput;
import jo.joide.tasks.TaskInput.PropertyInput;

/**
 * Ensemble ordonné et dédupliqué des entrées d'une tâche.
 *
 * <p>L'ordre d'ajout est conservé pour que deux exécutions de la même
 * tâche décrivent les entrées dans le même ordre : l'ordre fait partie
 * de l'identité de la tâche. Une propriété homonyme remplace la
 * précédente ; un chemin de fichier déclaré deux fois ne compte
 * qu'une fois.</p>
 */
public final class TaskInputs {

    private final List<TaskInput> inputs;
    private final Map<String, String> properties;

    private TaskInputs(List<TaskInput> inputs, Map<String, String> properties) {
        this.inputs = inputs;
        this.properties = properties;
    }

    /**
     * @return un bâtisseur d'entrées
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return les entrées dans l'ordre d'ajout, non modifiables
     */
    public List<TaskInput> asList() {
        return inputs;
    }

    /**
     * @return les propriétés scalaires par nom, non modifiables
     */
    public Map<String, String> properties() {
        return properties;
    }

    /**
     * @return les chemins des fichiers d'entrée, dans l'ordre d'ajout
     */
    public List<RootedPath> filePaths() {
        List<RootedPath> paths = new ArrayList<>();
        for (TaskInput input : inputs) {
            if (input instanceof FileInput file) {
                paths.add(file.path());
            }
        }
        return paths;
    }

    /**
     * @return les chemins des dossiers d'entrée, dans l'ordre d'ajout
     */
    public List<RootedPath> dirPaths() {
        List<RootedPath> paths = new ArrayList<>();
        for (TaskInput input : inputs) {
            if (input instanceof DirInput dir) {
                paths.add(dir.path());
            }
        }
        return paths;
    }

    /**
     * @return vrai si la tâche ne déclare aucune entrée
     */
    public boolean isEmpty() {
        return inputs.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TaskInputs)) {
            return false;
        }
        TaskInputs that = (TaskInputs) other;
        return inputs.equals(that.inputs) && properties.equals(that.properties);
    }

    @Override
    public int hashCode() {
        return inputs.hashCode() * 31 + properties.hashCode();
    }

    @Override
    public String toString() {
        return "TaskInputs" + inputs;
    }

    /**
     * Bâtisseur d'entrées de tâche.
     */
    public static final class Builder {

        private final List<TaskInput> inputs = new ArrayList<>();
        private final Map<String, String> properties = new LinkedHashMap<>();

        /**
         * Ajoute un fichier d'entrée dont le contenu participe à
         * l'empreinte.
         *
         * @param path chemin ancré du fichier
         * @return ce bâtisseur
         */
        public Builder file(RootedPath path) {
            return add(new FileInput(path, true));
        }

        /**
         * Ajoute un fichier d'entrée sans hachage de contenu : seuls
         * les signaux de changement (taille, date, clé) suffisent.
         *
         * @param path chemin ancré du fichier
         * @return ce bâtisseur
         */
        public Builder fileSignalsOnly(RootedPath path) {
            return add(new FileInput(path, false));
        }

        /**
         * Ajoute un dossier d'entrée.
         *
         * @param path chemin ancré du dossier
         * @return ce bâtisseur
         */
        public Builder dir(RootedPath path) {
            return add(new DirInput(path));
        }

        /**
         * Ajoute une propriété scalaire ; une propriété homonyme
         * remplace la précédente.
         *
         * @param name  nom de la propriété
         * @param value valeur textuelle
         * @return ce bâtisseur
         */
        public Builder property(String name, String value) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            properties.put(name, value);
            return this;
        }

        /**
         * Ajoute une entrée déjà construite.
         *
         * @param input entrée à ajouter
         * @return ce bâtisseur
         */
        public Builder add(TaskInput input) {
            Objects.requireNonNull(input, "input");
            for (TaskInput existing : inputs) {
                if (existing.equals(input)) {
                    return this;
                }
            }
            inputs.add(input);
            return this;
        }

        /**
         * @return les entrées construites, immuables
         */
        public TaskInputs build() {
            Map<String, String> frozen = Collections.unmodifiableMap(
                    new LinkedHashMap<>(properties));
            return new TaskInputs(Collections.unmodifiableList(
                    new ArrayList<>(inputs)), frozen);
        }
    }
}
