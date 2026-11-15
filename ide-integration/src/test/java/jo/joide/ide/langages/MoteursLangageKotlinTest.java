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
 * Le moteur Kotlin tourne RÉELLEMENT dans ces tests (session du SPI,
 * charge le noyau embarqué) : cycle ouvrir/modifier/fermer,
 * diagnostics syntaxiques et sémantiques par tirage.
 */
final class MoteursLangageKotlinTest {

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
    void erreurDeSyntaxeSignalee() throws IOException {
        Path fichier = sources.resolve("Main.kt");
        Files.writeString(fichier, """
                fun main() {
                    val x = "non fermé
                }
                """);
        moteurs = monter();
        moteurs.ouvrir(fichier.toString(), Files.readString(fichier));
        List<DiagnosticIde> diagnostics = moteurs.diagnostiquer(
                fichier.toString(), Files.readString(fichier));
        assertTrue(diagnostics.stream().anyMatch(
                d -> d.gravite() == DiagnosticIde.Gravite.ERREUR),
                "la chaîne non fermée doit être une erreur : "
                        + diagnostics);
    }

    @Test
    void importInutiliseAvertiAvecCodeStable() throws IOException {
        Path fichier = sources.resolve("Usage.kt");
        Files.writeString(fichier, """
                import java.util.ArrayList

                fun usage(): Int = 42
                """);
        moteurs = monter();
        moteurs.ouvrir(fichier.toString(), Files.readString(fichier));
        List<DiagnosticIde> diagnostics = moteurs.diagnostiquer(
                fichier.toString(), Files.readString(fichier));
        assertTrue(diagnostics.stream().anyMatch(
                d -> "kt.unusedImport".equals(d.code())),
                "l'import inutilisé porte son code stable : "
                        + diagnostics);
        moteurs.fermerDocument(fichier.toString());
    }

    @Test
    void modifierRemplaceLeContenuEtLesDiagnostics() throws IOException {
        Path fichier = sources.resolve("Main.kt");
        String sain = """
                fun main() {
                    println("bonjour")
                }
                """;
        String casse = """
                fun main() {
                    val x = "non fermé
                }
                """;
        Files.writeString(fichier, sain);
        moteurs = monter();
        moteurs.ouvrir(fichier.toString(), sain);
        assertTrue(moteurs.diagnostiquer(fichier.toString(), sain)
                .stream().noneMatch(d -> d.gravite()
                        == DiagnosticIde.Gravite.ERREUR),
                "le fichier sain n'a pas d'erreur");

        moteurs.modifier(fichier.toString(), casse);
        assertTrue(moteurs.diagnostiquer(fichier.toString(), casse)
                .stream().anyMatch(d -> d.gravite()
                        == DiagnosticIde.Gravite.ERREUR),
                "l'édition remplace le contenu : la casse est signalée");
        moteurs.fermerDocument(fichier.toString());
    }

    @Test
    void fichierHorsModuleDiagnostiqueQuandMeme() throws IOException {
        Path horsModule = tmp.resolve("hors/Notes.kt");
        Files.createDirectories(horsModule.getParent());
        Files.writeString(horsModule, """
                fun main() {
                    val x = "non fermé
                }
                """);
        moteurs = monter();
        // Aucun module ne couvre ce fichier : la session sans classpath
        // répond quand même aux requêtes syntaxiques (honnête, jamais
        // une exception).
        moteurs.ouvrir(horsModule.toString(), Files.readString(horsModule));
        List<DiagnosticIde> diagnostics = moteurs.diagnostiquer(
                horsModule.toString(), Files.readString(horsModule));
        assertTrue(diagnostics.stream().anyMatch(
                d -> d.gravite() == DiagnosticIde.Gravite.ERREUR),
                "la syntaxe est diagnostiquée hors module : "
                        + diagnostics);
    }
}
