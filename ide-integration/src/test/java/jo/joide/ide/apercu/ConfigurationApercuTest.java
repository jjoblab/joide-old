package jo.joide.ide.apercu;

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
import jo.joide.ide.sync.LanguageModelSnapshot;
import jo.joide.ide.sync.ModuleDirectories;
import jo.joide.ide.sync.ProjetLspAdapter;
import jo.joide.ide.sync.Resolutions;
import jo.joide.model.ContentRole;
import jo.joide.model.ContentRoot;
import jo.joide.model.DependencyRef;
import jo.joide.model.DependencyScope;
import jo.joide.model.Facet;
import jo.joide.model.Module;
import jo.joide.model.ModuleType;
import jo.joide.model.Project;
import jo.joide.model.SourceSet;
import jo.joide.model.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * La constitution d'une configuration d'aperçu DEPUIS le modèle de
 * langage : le premier module Android actif portant des ressources
 * fournit son dossier {@code res/} et les contenus de ses fichiers de
 * thèmes/styles/attributs (lus du disque) — un module JVM n'en fournit
 * aucune, un dossier absent est un écart signalement, jamais une
 * omission muette.
 */
final class ConfigurationApercuTest {

    @TempDir
    Path tmp;

    @Test
    void moduleAndroidFournitResEtContenus() throws IOException {
        Path racine = Files.createDirectories(tmp.resolve("projet"));
        Path res = Files.createDirectories(
                racine.resolve("app/src/main/res"));
        Path values = Files.createDirectories(res.resolve("values"));
        Files.writeString(values.resolve("themes.xml"),
                "<resources><style name=\"Theme.App\"/></resources>");
        Files.writeString(values.resolve("styles.xml"),
                "<resources><style name=\"Bouton\"/></resources>");

        Map<String, Facet> facets = new LinkedHashMap<>();
        facets.put("android", new Facet("android",
                Map.of("namespace", "org.exemple.app")));
        SourceSet main = new SourceSet("main", List.of(
                new ContentRoot("src/main/java", ContentRole.SOURCE),
                new ContentRoot("src/main/res", ContentRole.ANDROID_RES)));
        Module app = new Module(":app", "app",
                ModuleType.ANDROID_APPLICATION,
                Map.of("main", main), List.of(), facets);
        Map<String, Module> modules = new LinkedHashMap<>();
        modules.put(app.path(), app);
        Workspace espace = new Workspace("projet",
                Map.of("main", new Project("main", modules)), Map.of());

        LanguageModelSync sync = new LanguageModelSync(racine,
                new ProjetLspAdapter(ModuleDirectories.nested(racine),
                        AndroidPlatform.absent()),
                Resolutions.fixed(
                        new ExternalResolution.Builder().build()));
        LanguageModelSnapshot snapshot = sync.synchroniser(espace);

        ConfigurationApercu config =
                ConfigurationsApercu.depuisSnapshot(snapshot);
        assertEquals(res.toString(), config.dossierRes(),
                "le PREMIER dossier res/ du module Android");
        assertTrue(config.themesJour().contains("Theme.App"));
        assertTrue(config.styles().contains("Bouton"));
        assertNull(config.themesNuit(), "pas de values-night : null");
        assertNull(config.attrs(), "pas d'attrs.xml : null");
        assertNull(config.nomTheme(),
                "le thème n'est JAMAIS deviné — null, repli hôte");
        assertTrue(config.avertissements().isEmpty());
    }

    @Test
    void moduleJvmNeFournitRien() throws IOException {
        Path racine = Files.createDirectories(tmp.resolve("projet"));
        Path sources = Files.createDirectories(
                racine.resolve("core/src/main/java"));
        Files.writeString(sources.resolve("A.java"), "class A {}");
        SourceSet main = new SourceSet("main", List.of(
                new ContentRoot("src/main/java", ContentRole.SOURCE)));
        Module core = new Module(":core", "core",
                ModuleType.JVM_LIBRARY, Map.of("main", main), List.of(),
                Map.of());
        Map<String, Module> modules = new LinkedHashMap<>();
        modules.put(core.path(), core);
        Workspace espace = new Workspace("projet",
                Map.of("main", new Project("main", modules)), Map.of());
        LanguageModelSync sync = new LanguageModelSync(racine,
                new ProjetLspAdapter(ModuleDirectories.nested(racine),
                        AndroidPlatform.absent()),
                Resolutions.fixed(
                        new ExternalResolution.Builder().build()));
        LanguageModelSnapshot snapshot = sync.synchroniser(espace);

        ConfigurationApercu config =
                ConfigurationsApercu.depuisSnapshot(snapshot);
        assertNull(config.dossierRes());
        assertEquals(ConfigurationApercu.LARGEUR_DEFAUT,
                config.largeurCible());
    }

    @Test
    void dossierResAbsentEstUnEcartSignale() {
        ConfigurationApercu config = ConfigurationsApercu.deDossierRes(
                tmp.resolve("introuvable"), new java.util.ArrayList<>());
        assertNull(config.dossierRes());
        assertEquals(1, config.avertissements().size(),
                "l'absence est signalée, jamais muette : "
                        + config.avertissements());
        assertTrue(config.avertissements().get(0)
                .contains("introuvable"));
    }

    @Test
    void avecThemeEtModeNuitConstruisentDesVariantes() {
        ConfigurationApercu base = ConfigurationApercu.vide();
        ConfigurationApercu theme = base.avecTheme("Theme.MonApp");
        assertEquals("Theme.MonApp", theme.nomTheme());
        assertNull(base.nomTheme(), "la base est inchangée (immuable)");
        assertTrue(base.avecModeNuit(true).modeNuit());
        assertFalse(base.modeNuit());
    }

    @Test
    void lotLDensiteEtDimensionsSontDesReglesReels() {
        ConfigurationApercu base = ConfigurationApercu.vide();
        assertEquals(ConfigurationApercu.DENSITE_CONTEXTE,
                base.densiteRendu(),
                "par défaut : la densité du contexte (comportement lot E)");
        ConfigurationApercu densite2 = base.avecDensite(2f);
        assertEquals(2f, densite2.densiteRendu());
        assertEquals(ConfigurationApercu.DENSITE_CONTEXTE,
                base.densiteRendu(), "la base est inchangée (immuable)");
        assertEquals(2f, densite2.avecModeNuit(true).densiteRendu(),
                "les variantes propagent la densité");
        assertEquals(2f, densite2.avecTheme("T").densiteRendu());
        assertEquals(ConfigurationApercu.DENSITE_CONTEXTE,
                base.avecDensite(-1f).densiteRendu(),
                "une densité négative retombe sur le contexte");
        ConfigurationApercu dimensions = base.avecDimensions(720, 1280);
        assertEquals(720, dimensions.largeurCible());
        assertEquals(1280, dimensions.hauteurCible());
        assertEquals(1080, base.largeurCible());
    }
}
