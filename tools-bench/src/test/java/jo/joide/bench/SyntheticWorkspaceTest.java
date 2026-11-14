package jo.joide.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import jo.joide.model.Module;
import jo.joide.model.ModuleTopology;
import jo.joide.model.Workspace;
import jo.joide.model.WorkspaceLoad;
import jo.joide.model.WorkspaceLoader;
import jo.joide.platform.Problem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Validation croisée : le lecteur de modèle charge un projet
 * synthétique généré par le banc d'essai — les fichiers écrits par le
 * générateur et le sous-ensemble lu par le modèle sont le même
 * contrat.
 */
class SyntheticWorkspaceTest {

    @Test
    void loadsGeneratedTierS(@TempDir Path dir) throws IOException {
        Path project = dir.resolve("projet");
        Files.createDirectory(project);
        ProjectGenerator.generate(TierSpec.parse("S"), project);

        WorkspaceLoad load = WorkspaceLoader.load(project);

        assertTrue(load.isUsable(),
                () -> problemText(load.problems()));
        Workspace workspace = load.workspace().orElseThrow();
        assertEquals(TierSpec.parse("S").modules,
                workspace.allModules().size());
        // la topologie du graphe planifié est cohérente : niveaux
        // calculables, aucun cycle
        ModuleTopology topology = ModuleTopology.of(workspace);
        assertEquals(TierSpec.parse("S").modules, topology.levels().size());
        // chaque module porte ses sources et sa facette android
        for (Module module : workspace.allModules().values()) {
            assertTrue(module.sourceSets().containsKey("main"),
                    "module sans ensemble main : " + module.path());
            assertTrue(module.facets().containsKey("android"),
                    "module sans facette android : " + module.path());
            assertTrue(module.type().id().equals("android-library"),
                    "type inattendu : " + module.type().id());
        }
    }

    private static String problemText(List<Problem> problems) {
        StringBuilder out = new StringBuilder();
        for (Problem problem : problems) {
            out.append(problem).append('\n');
        }
        return out.toString();
    }
}
