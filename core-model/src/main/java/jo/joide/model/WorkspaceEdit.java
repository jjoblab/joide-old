package jo.joide.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Une édition d'espace de travail : le nouvel espace et les fichiers à
 * écrire pour le matérialiser sur le disque.
 */
public final class WorkspaceEdit {

    private final Workspace workspace;
    private final Map<String, String> files;

    private WorkspaceEdit(Workspace workspace, Map<String, String> files) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.files = Collections.unmodifiableMap(new LinkedHashMap<>(files));
    }

    /**
     * Construit l'édition qui remplace un module : le fichier de module
     * réécrit et le fichier d'espace de travail mis à jour si le module
     * est nouveau.
     *
     * @param current espace courant
     * @param module  module remplacé ou ajouté
     * @return l'édition correspondante
     */
    public static WorkspaceEdit withModule(Workspace current, Module module) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(module, "module");
        Project project = current.projects().get(WorkspaceLoader.MAIN_PROJECT);
        if (project == null) {
            project = new Project(WorkspaceLoader.MAIN_PROJECT, Map.of());
        }
        Workspace next = current.withProject(project.withModule(module));
        Map<String, String> files = new LinkedHashMap<>();
        files.put(module.name() + "/" + WorkspaceLoader.MODULE_FILE,
                ModuleWriter.write(module));
        files.put(WorkspaceLoader.WORKSPACE_FILE, ModuleWriter.writeWorkspace(next));
        return new WorkspaceEdit(next, files);
    }

    /**
     * Construit l'édition qui retire un module.
     *
     * @param current   espace courant
     * @param modulePath chemin du module à retirer (ex. « :app »)
     * @return l'édition correspondante
     */
    public static WorkspaceEdit withoutModule(Workspace current, String modulePath) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(modulePath, "modulePath");
        Project project = current.projects().get(WorkspaceLoader.MAIN_PROJECT);
        if (project == null) {
            throw new ModelException("unknown module: " + modulePath);
        }
        Workspace next = current.withProject(project.withoutModule(modulePath));
        Map<String, String> files = new LinkedHashMap<>();
        files.put(WorkspaceLoader.WORKSPACE_FILE, ModuleWriter.writeWorkspace(next));
        return new WorkspaceEdit(next, files);
    }

    /** @return le nouvel espace de travail */
    public Workspace workspace() {
        return workspace;
    }

    /** @return les fichiers à écrire, par chemin relatif à la racine */
    public Map<String, String> files() {
        return files;
    }
}
