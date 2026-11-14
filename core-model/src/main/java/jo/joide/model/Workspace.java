package jo.joide.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * L'espace de travail : le sommet de la hiérarchie, ses projets, ses
 * modules et ses réglages généraux. Immuable.
 *
 * <p>Les lecteurs ne voient jamais un espace muter : toute édition
 * produit un nouvel espace, publié atomiquement par le dépôt.</p>
 */
public final class Workspace {

    private final String name;
    private final Map<String, Project> projects;
    private final Map<String, String> settings;

    /**
     * @param name     nom de l'espace de travail
     * @param projects projets, par nom
     * @param settings réglages généraux (ex. « android.min-sdk »)
     */
    public Workspace(String name, Map<String, Project> projects,
                     Map<String, String> settings) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("workspace name is required");
        }
        this.name = name;
        this.projects = Collections.unmodifiableMap(new TreeMap<>(projects));
        this.settings = Collections.unmodifiableMap(new LinkedHashMap<>(settings));
    }

    /** @return le nom */
    public String name() {
        return name;
    }

    /** @return les projets, par nom, triés */
    public Map<String, Project> projects() {
        return projects;
    }

    /** @return les réglages généraux */
    public Map<String, String> settings() {
        return settings;
    }

    /**
     * @param key clef de réglage (ex. « android.min-sdk »)
     * @return la valeur du réglage, si présente
     */
    public Optional<String> setting(String key) {
        return Optional.ofNullable(settings.get(key));
    }

    /**
     * @return tous les modules de tous les projets, par chemin
     */
    public Map<String, Module> allModules() {
        Map<String, Module> all = new TreeMap<>();
        for (Project project : projects.values()) {
            all.putAll(project.modules());
        }
        return all;
    }

    /**
     * @param path chemin de module (ex. « :app »)
     * @return le module correspondant, ou {@code null}
     */
    public Module module(String path) {
        for (Project project : projects.values()) {
            Module module = project.module(path);
            if (module != null) {
                return module;
            }
        }
        return null;
    }

    /**
     * @param project projet remplacé ou ajouté
     * @return un nouvel espace avec ce projet
     */
    public Workspace withProject(Project project) {
        Map<String, Project> next = new LinkedHashMap<>(projects);
        next.put(project.name(), project);
        return new Workspace(name, next, settings);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Workspace)) {
            return false;
        }
        Workspace that = (Workspace) other;
        return name.equals(that.name) && projects.equals(that.projects)
                && settings.equals(that.settings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, projects, settings);
    }

    @Override
    public String toString() {
        return "Workspace[" + name + " " + allModules().size() + " modules]";
    }
}
