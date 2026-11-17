package jo.joide.ide.langages;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jo.joide.ide.sync.AndroidPlatform;
import jo.joide.ide.sync.ExternalResolution;
import jo.joide.ide.sync.LanguageModelSync;
import jo.joide.ide.sync.ModuleDirectories;
import jo.joide.ide.sync.ProjetLspAdapter;
import jo.joide.ide.sync.Resolutions;
import jo.joide.model.ContentRole;
import jo.joide.model.ContentRoot;
import jo.joide.model.Module;
import jo.joide.model.ModuleType;
import jo.joide.model.Project;
import jo.joide.model.SourceSet;
import jo.joide.model.Workspace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Le moteur XML tourne RÉELLEMENT dans ces tests (session du service
 * unique) : diagnostics de structure par tirage, sans plateforme (les
 * comportements tributaires du catalogue d'attributs de la plateforme
 * sont exercés côté application, lot F).
 */
final class MoteursLangageXmlTest {

    @TempDir
    Path tmp;

    private Path racine;
    private Path sources;
    private MoteursLangage moteurs;

    @BeforeEach
    void preparer() throws IOException {
        racine = Files.createDirectories(tmp.resolve("projet"));
        sources = Files.createDirectories(
                racine.resolve("app/src/main/java"));
    }

    @AfterEach
    void fermer() {
        if (moteurs != null) {
            moteurs.close();
        }
    }

    private MoteursLangage monter() throws IOException {
        SourceSet main = new SourceSet("main", List.of(
                new ContentRoot("src/main/java", ContentRole.SOURCE)));
        Module app = new Module(":app", "app", ModuleType.JVM_LIBRARY,
                Map.of("main", main), List.of(), Map.of());
        Map<String, Module> modules = new LinkedHashMap<>();
        modules.put(app.path(), app);
        Workspace espace = new Workspace("projet",
                Map.of("main", new Project("main", modules)), Map.of());
        LanguageModelSync sync = new LanguageModelSync(racine,
                new ProjetLspAdapter(ModuleDirectories.nested(racine),
                        AndroidPlatform.absent()),
                Resolutions.fixed(
                        new ExternalResolution.Builder().build()));
        moteurs = new MoteursLangage(sync,
                Files.createDirectories(tmp.resolve("index")).toFile()
                        .toPath());
        moteurs.synchroniser(espace);
        return moteurs;
    }

    @Test
    void baliseNonFermeeSignalee() throws IOException {
        Path fichier = sources.resolve("activity_main.xml");
        Files.writeString(fichier, """
                <?xml version="1.0" encoding="utf-8"?>
                <LinearLayout>
                    <TextView>
                </LinearLayout>
                """);
        moteurs = monter();
        moteurs.ouvrir(fichier.toString(), Files.readString(fichier));
        List<DiagnosticIde> diagnostics = moteurs.diagnostiquer(
                fichier.toString(), Files.readString(fichier));
        assertTrue(diagnostics.stream().anyMatch(
                d -> d.gravite() == DiagnosticIde.Gravite.ERREUR),
                "la balise non fermée doit être une erreur : "
                        + diagnostics);
    }

    @Test
    void documentBienFormeSansErreur() throws IOException {
        Path fichier = sources.resolve("sain.xml");
        Files.writeString(fichier, """
                <?xml version="1.0" encoding="utf-8"?>
                <racine>
                    <enfant attr="valeur" />
                </racine>
                """);
        moteurs = monter();
        moteurs.ouvrir(fichier.toString(), Files.readString(fichier));
        List<DiagnosticIde> diagnostics = moteurs.diagnostiquer(
                fichier.toString(), Files.readString(fichier));
        assertTrue(diagnostics.stream().noneMatch(
                d -> d.gravite() == DiagnosticIde.Gravite.ERREUR),
                "le document bien formé n'a pas d'erreur : "
                        + diagnostics);
        moteurs.fermerDocument(fichier.toString());
    }

    @Test
    void completionNeLeveJamaisMemeSansPlateforme() throws IOException {
        Path fichier = sources.resolve("vide.xml");
        Files.writeString(fichier, """
                <?xml version="1.0" encoding="utf-8"?>
                <racine>
                    <
                </racine>
                """);
        moteurs = monter();
        moteurs.ouvrir(fichier.toString(), Files.readString(fichier));
        String texte = Files.readString(fichier);
        List<PropositionCompletion> propositions = moteurs.completer(
                fichier.toString(), texte, texte.indexOf("<\n"));
        // Sans plateforme, la complétion peut être vide — jamais une
        // exception (tolérance de version du contrat de session).
        assertNotNull(propositions);
    }
}
