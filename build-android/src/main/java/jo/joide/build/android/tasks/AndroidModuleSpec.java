package jo.joide.build.android.tasks;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jo.joide.build.android.variant.AndroidVariant;

/**
 * Description d'un module Android à construire : l'application (avec
 * manifeste et ressources) ou une bibliothèque. Les chemins sources
 * sont relatifs au projet ; l'assemblage en graphe de tâches se fait
 * dans la fabrique.
 */
public final class AndroidModuleSpec {

    /** Nom unique du module. */
    public final String name;
    /** Vrai si ce module est l'application (tâches package/sign/aab). */
    public final boolean application;
    /** Manifeste (relatif au projet), requis pour l'application. */
    public Path manifest;
    /** Racines de ressources Android (relatives au projet). */
    public final List<Path> resourceDirs = new ArrayList<>();
    /** Répertoire d'actifs (relatif au projet), ou {@code null}. */
    public Path assetsDir;
    /** Racines de sources Java/Kotlin (relatives au projet). */
    public final List<Path> sourceRoots = new ArrayList<>();
    /** Noms des modules JoIDE dont celui-ci dépend (ordre classpath). */
    public final List<String> moduleDependencies = new ArrayList<>();
    /** Jars externes (chemins absolus dans le dépôt de bibliothèques). */
    public final List<Path> externalJars = new ArrayList<>();
    /** Répertoires d'AAR extraits (classes.jar et libs/*.jar sont
     * consommés), chemins absolus. */
    public final List<Path> externalAarDirs = new ArrayList<>();
    /** Variantes construites pour ce module. */
    public final List<AndroidVariant> variants = new ArrayList<>();

    /**
     * @param name        nom unique
     * @param application vrai pour le module d'application
     */
    public AndroidModuleSpec(String name, boolean application) {
        this.name = name;
        this.application = application;
    }
}
