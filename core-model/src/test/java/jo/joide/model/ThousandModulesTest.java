package jo.joide.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Chargement d'un espace de travail de mille modules : la cible est
 * d'une seconde, hors entrées-sorties froides.
 *
 * <p>La création des mille fichiers de module n'entre pas dans la
 * mesure : seul le chargement complet (lecture, analyse, construction
 * du modèle) est chronométré, système de fichiers déjà tiède. La
 * mesure est affichée pour le rapport de phase.</p>
 */
class ThousandModulesTest {

    private static final int MODULES = 1000;
    private static final long BUDGET_MS = 1_000L;

    @Test
    void loadThousandModulesWithinBudget(@TempDir Path root) throws IOException {
        StringBuilder include = new StringBuilder();
        for (int i = 0; i < MODULES; i++) {
            if (i > 0) {
                include.append(", ");
            }
            include.append('"').append("m").append(String.format("%04d", i)).append('"');
            Path dir = root.resolve("m" + String.format("%04d", i));
            Files.createDirectories(dir);
            String dependency = i == 0 ? ""
                    : "\n\n[[dependencies]]\npath = \":m" + String.format("%04d", i - 1)
                            + "\"\nscope = \"api\"\n";
            Files.writeString(dir.resolve("joide-module.toml"), """
                    schema = "joide.module/1"
                    name = "m%04d"
                    type = "jvm-library"

                    [[sources]]
                    name = "main"
                    source = ["src/main/java"]%s
                    """.formatted(i, dependency));
        }
        Files.writeString(root.resolve("joide.toml"), """
                schema = "joide.workspace/1"
                name = "large"

                [modules]
                include = [%s]
                """.formatted(include.toString()));

        // échauffement du système de fichiers puis mesure
        WorkspaceLoader.load(root);
        long start = System.nanoTime();
        WorkspaceLoad load = WorkspaceLoader.load(root);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(load.isUsable(), () -> load.problems().toString());
        assertEquals(MODULES, load.workspace().orElseThrow().allModules().size());
        assertTrue(elapsedMs <= BUDGET_MS,
                "chargement de 1000 modules : " + elapsedMs
                        + " ms (budget " + BUDGET_MS + " ms)");
        System.out.println("[mille-modules] 1000 modules charges en "
                + elapsedMs + " ms");

        // la topologie de la chaine reste coherente
        ModuleTopology topology = ModuleTopology.of(load.workspace().orElseThrow());
        assertEquals(MODULES, topology.levels().size());
    }
}
