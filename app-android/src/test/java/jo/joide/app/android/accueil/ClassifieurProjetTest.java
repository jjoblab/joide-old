package jo.joide.app.android.accueil;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.ProjectDetector;
import jo.joide.model.Facet;
import jo.joide.model.Module;
import jo.joide.model.ModuleType;
import jo.joide.model.Project;
import jo.joide.model.Workspace;

/**
 * Classification des projets pour la carte d'accueil : lue sur le
 * modèle déclaratif, jamais devinée dans les sources (ADR-049).
 */
class ClassifieurProjetTest {

    private static Workspace espace(Module... modules) {
        Map<String, Module> parChemin = new java.util.LinkedHashMap<>();
        for (Module m : modules) {
            parChemin.put(m.path(), m);
        }
        return new Workspace("essai",
                Map.of("main", new Project("main", parChemin)), Map.of());
    }

    private static Module module(String chemin, ModuleType type,
                                 Map<String, Facet> facets) {
        return new Module(chemin, chemin.substring(1), type, Map.of(),
                List.of(), facets);
    }

    @Test
    void gradleResteGradleMemeAvecUnWorkspaceLisible() {
        assertEquals(TypeProjet.GRADLE,
                ClassifieurProjet.classifier(ProjectDetector.Kind.GRADLE,
                        espace(module(":app", ModuleType.JVM_LIBRARY,
                                Map.of()))));
    }

    @Test
    void moduleAndroidFaitApplicationAndroid() {
        Workspace espace = espace(
                module(":app", ModuleType.ANDROID_APPLICATION, Map.of()),
                module(":core", ModuleType.JVM_LIBRARY, Map.of()));
        assertEquals(TypeProjet.ANDROID,
                ClassifieurProjet.classifier(ProjectDetector.Kind.NATIVE,
                        espace));
        assertEquals(2, ClassifieurProjet.compterModules(espace));
    }

    @Test
    void facetteApplicationFaitConsole() {
        Workspace espace = espace(module(":console", ModuleType.JVM_LIBRARY,
                Map.of("application",
                        new Facet("application",
                                Map.of("main-class", "MainKt")))));
        assertEquals(TypeProjet.CONSOLE,
                ClassifieurProjet.classifier(ProjectDetector.Kind.NATIVE,
                        espace));
    }

    @Test
    void jvmSansPointDEntreeFaitBibliotheque() {
        Workspace espace = espace(module(":socle", ModuleType.JVM_LIBRARY,
                Map.of()));
        assertEquals(TypeProjet.BIBLIOTHEQUE,
                ClassifieurProjet.classifier(ProjectDetector.Kind.NATIVE,
                        espace));
        assertEquals(1, ClassifieurProjet.compterModules(espace));
    }

    @Test
    void sansModuleFaitVideEtZeroComptes() {
        Workspace espace = espace();
        assertEquals(TypeProjet.VIDE,
                ClassifieurProjet.classifier(ProjectDetector.Kind.NATIVE,
                        espace));
        assertEquals(0, ClassifieurProjet.compterModules(espace));
        assertEquals(TypeProjet.VIDE,
                ClassifieurProjet.classifier(ProjectDetector.Kind.NATIVE,
                        null));
        assertEquals(0, ClassifieurProjet.compterModules(null));
    }

    @Test
    void androidGagneSurConsoleSiLesDeuxExistent() {
        Workspace espace = espace(
                module(":app", ModuleType.ANDROID_APPLICATION, Map.of()),
                module(":console", ModuleType.JVM_LIBRARY,
                        Map.of("application", new Facet("application",
                                Map.of()))));
        assertEquals(TypeProjet.ANDROID,
                ClassifieurProjet.classifier(ProjectDetector.Kind.NATIVE,
                        espace));
    }
}
