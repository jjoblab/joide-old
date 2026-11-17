package jo.joide.app.android.accueil;

import jo.joide.app.android.ProjectDetector;
import jo.joide.model.Module;
import jo.joide.model.Workspace;

/**
 * La classification d'un projet pour sa carte d'accueil (ADR-É3) :
 * le type de tuile et le libellé de méta. Pure, lue sur le modèle
 * chargé — jamais d'analyse de sources.
 *
 * <p>Règles (ADR-049, la franchise avant tout) : un projet Gradle est
 * Gradle, quoi qu'il contienne ; un workspace avec un module
 * {@code android-*} est une application Android ; un workspace avec
 * un module portant la facette {@code application} (le point d'entrée
 * des consoles, écrit par les modèles du lot I) est une console JVM ;
 * un workspace avec des modules est une bibliothèque JVM ; sans
 * module, un projet vide. Un projet importé sans point d'entrée connu
 * est classé bibliothèque — la classification se lit sur le modèle
 * déclaratif, elle ne devine pas les sources.</p>
 */
public final class ClassifieurProjet {

    private ClassifieurProjet() {
    }

    /**
     * @param genre la détection à la racine (lot A)
     * @param espace le workspace chargé, ou null si illisible
     * @return le type de projet, jamais null
     */
    public static TypeProjet classifier(ProjectDetector.Kind genre,
                                        Workspace espace) {
        if (genre == ProjectDetector.Kind.GRADLE) {
            return TypeProjet.GRADLE;
        }
        if (espace == null) {
            return TypeProjet.VIDE;
        }
        boolean android = false;
        boolean application = false;
        boolean unModule = false;
        for (Module module : espace.allModules().values()) {
            unModule = true;
            if (module.type().id().startsWith("android-")) {
                android = true;
            }
            if (module.facets().containsKey("application")) {
                application = true;
            }
        }
        if (android) {
            return TypeProjet.ANDROID;
        }
        if (application) {
            return TypeProjet.CONSOLE;
        }
        return unModule ? TypeProjet.BIBLIOTHEQUE : TypeProjet.VIDE;
    }

    /**
     * @param espace le workspace chargé, ou null
     * @return le nombre de modules comptés dans la ligne de méta
     */
    public static int compterModules(Workspace espace) {
        return espace == null ? 0 : espace.allModules().size();
    }
}
