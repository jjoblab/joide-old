package jo.joide.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Un projet : un groupe nommé de modules dans l'espace de travail.
 * Immuable.
 */
public final class Project {

    private final String name;
    private final Map<String, Module> modules;

    /**
     * @param name    nom du projet
     * @param modules modules du projet, par chemin
     */
    public Project(String name, Map<String, Module> modules) {
        if (name == null || !name.matches("[a-z][a-z0-9-]{0,30}")) {
            throw new IllegalArgumentException("invalid project name: " + name);
        }
        this.name = name;
        this.modules = Collections.unmodifiableMap(new TreeMap<>(modules));
    }

    /** @return le nom du projet */
    public String name() {
        return name;
    }

    /** @return les modules, par chemin, triés */
    public Map<String, Module> modules() {
        return modules;
    }

    /**
     * @param path chemin de module (ex. « :app »)
     * @return le module correspondant, ou {@code null}
     */
    public Module module(String path) {
        return modules.get(path);
    }

    /**
     * @param extra module à ajouter ou remplacer
     * @return un nouveau projet avec ce module
     */
    public Project withModule(Module extra) {
        Map<String, Module> next = new LinkedHashMap<>(modules);
        next.put(extra.path(), extra);
        return new Project(name, next);
    }

    /**
     * @param path chemin du module à retirer
     * @return un nouveau projet sans ce module
     */
    public Project withoutModule(String path) {
        Map<String, Module> next = new LinkedHashMap<>(modules);
        next.remove(path);
        return new Project(name, next);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Project)) {
            return false;
        }
        Project that = (Project) other;
        return name.equals(that.name) && modules.equals(that.modules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, modules);
    }

    @Override
    public String toString() {
        return "Project[" + name + " " + modules.size() + " modules]";
    }
}
