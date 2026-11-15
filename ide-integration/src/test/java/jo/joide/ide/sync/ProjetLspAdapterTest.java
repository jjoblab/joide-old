package jo.joide.ide.sync;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jo.codeide.lsp.model.EnsemblesSources;
import jo.codeide.lsp.model.KindEntree;
import jo.codeide.lsp.model.ModuleLsp;
import jo.codeide.lsp.model.ProjetLsp;
import jo.codeide.lsp.model.TypeModuleLsp;
import jo.codeide.lsp.model.EntreeClasspathInstantane;
import jo.joide.deps.aar.AarContents;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests de l'adaptateur ProjetLsp : le modèle JoIDE (core-model) et
 * une résolution externe montée deviennent un instantané « ProjetLsp »
 * dont les règles du contrat (§6.8 règle 3) sont vérifiées une à une.
 */
final class ProjetLspAdapterTest {

    @TempDir
    Path tmp;

    private Path root;
    private Path coreDir;
    private Path appDir;
    private Path androidJar;
    private Path coreJar;
    private Path appJar;
    private Path aarFile;
    private AarContents aarContents;

    @BeforeEach
    void setUp() throws IOException {
        root = tmp.resolve("projet");
        coreDir = root.resolve("core");
        appDir = root.resolve("app");
        Files.createDirectories(coreDir.resolve("src/main/java"));
        Files.createDirectories(coreDir.resolve("src/test/java"));
        Files.createDirectories(appDir.resolve("src/main/java"));
        Files.createDirectories(appDir.resolve("src/main/res"));
        androidJar = tmp.resolve("android-35/android.jar");
        Files.createDirectories(androidJar.getParent());
        Files.write(androidJar, new byte[] {1, 2, 3});
        coreJar = tmp.resolve("core-1.0.jar");
        Files.write(coreJar, new byte[] {10, 20, 30});
        appJar = tmp.resolve("app-2.0.jar");
        Files.write(appJar, new byte[] {40, 50});
        aarFile = tmp.resolve("vue-1.1.aar");
        Files.write(aarFile, new byte[] {60, 70});
        Path extraction = tmp.resolve("extrait/vue-1.1");
        Files.createDirectories(extraction.resolve("libs"));
        Files.write(extraction.resolve("classes.jar"), new byte[] {1, 1});
        Files.write(extraction.resolve("libs/moteur.jar"), new byte[] {2, 2});
        aarContents = AarContents.of(extraction);
    }

    private Module coreModule() {
        Map<String, SourceSet> sets = new LinkedHashMap<>();
        sets.put("main", new SourceSet("main", List.of(
                new ContentRoot("src/main/java", ContentRole.SOURCE))));
        sets.put("test", new SourceSet("test", List.of(
                new ContentRoot("src/test/java", ContentRole.SOURCE))));
        List<DependencyRef> deps = List.of(
                DependencyRef.external("org.exemple:core:1.0",
                        DependencyScope.IMPLEMENTATION, java.util.Set.of()));
        return new Module(":core", "core", ModuleType.JVM_LIBRARY,
                sets, deps, Map.of());
    }

    private Module appModule() {
        Map<String, SourceSet> sets = new LinkedHashMap<>();
        sets.put("main", new SourceSet("main", List.of(
                new ContentRoot("src/main/java", ContentRole.SOURCE),
                new ContentRoot("src/main/res", ContentRole.ANDROID_RES))));
        List<DependencyRef> deps = List.of(
                DependencyRef.toModule(":core", DependencyScope.API,
                        java.util.Set.of()),
                DependencyRef.external("org.exemple:vue:1.1",
                        DependencyScope.IMPLEMENTATION, java.util.Set.of()),
                DependencyRef.external("org.exemple:runtime:9.9",
                        DependencyScope.RUNTIME_ONLY, java.util.Set.of()));
        Map<String, Facet> facets = new LinkedHashMap<>();
        facets.put("android", new Facet("android", Map.of(
                "namespace", "org.exemple.app",
                "application-id", "org.exemple.app.id",
                "min-sdk", "24",
                "target-sdk", "28",
                "compile-sdk", "35",
                "manifest", "src/main/AndroidManifest.xml",
                "build-types", "debug,release")));
        return new Module(":app", "app", ModuleType.ANDROID_APPLICATION,
                sets, deps, facets);
    }

    private Workspace workspace(Module... modules) {
        Map<String, Module> byPath = new LinkedHashMap<>();
        for (Module module : modules) {
            byPath.put(module.path(), module);
        }
        return new Workspace("projet",
                Map.of("main", new Project("main", byPath)), Map.of());
    }

    private ExternalResolution resolution(Path core, Path vue) {
        ExternalResolution.Builder builder = new ExternalResolution.Builder();
        if (core != null) {
            builder.add("org.exemple:core", core, false, null);
        }
        if (vue != null) {
            builder.add("org.exemple:vue", vue, true, aarContents);
        }
        return builder.build();
    }

    private ProjetLsp adapt(Workspace space, ExternalResolution externals,
            AndroidPlatform platform) {
        return new ProjetLspAdapter(ModuleDirectories.nested(root), platform)
                .adapt(root, space, externals);
    }

    private static ModuleLsp moduleNamed(ProjetLsp projet, String nom) {
        return projet.getModules().stream()
                .filter(m -> m.getNom().equals(nom)).findFirst()
                .orElseThrow();
    }

    @Test
    void modulesPlatsTypesEtVariante() {
        ProjetLsp projet = adapt(workspace(coreModule(), appModule()),
                resolution(coreJar, aarFile), AndroidPlatform.fixed(androidJar));
        assertEquals(List.of(":app", ":core"),
                projet.getModules().stream().map(ModuleLsp::getNom).toList());
        assertEquals(TypeModuleLsp.JVM, moduleNamed(projet, ":core").getType());
        ModuleLsp app = moduleNamed(projet, ":app");
        assertEquals(TypeModuleLsp.ANDROID, app.getType());
        assertEquals("debug", app.getVariante());
        assertEquals(androidJar.toString(), app.getAndroidJar());
        assertEquals(root.toString(), projet.getCheminRacine());
    }

    @Test
    void sourcesParRoleCheminsAbsolus() {
        ProjetLsp projet = adapt(workspace(coreModule(), appModule()),
                resolution(coreJar, aarFile), AndroidPlatform.fixed(androidJar));
        EnsemblesSources core = moduleNamed(projet, ":core").getSources();
        assertEquals(List.of(coreDir.resolve("src/main/java").toString()),
                core.getPrincipal());
        assertEquals(List.of(coreDir.resolve("src/test/java").toString()),
                core.getTest());
        assertTrue(core.getGeneres().isEmpty());
    }

    @Test
    void classpathIsoleParModuleEtAarNormalise() {
        ProjetLsp projet = adapt(workspace(coreModule(), appModule()),
                resolution(coreJar, aarFile), AndroidPlatform.fixed(androidJar));
        List<EntreeClasspathInstantane> app = moduleNamed(projet, ":app")
                .getClasspath().getEntrees();
        // W5 : le classpath de :app porte SES entrées — pas le jar de :core.
        assertTrue(app.stream().anyMatch(e ->
                e.getKind() == KindEntree.MODULE
                        && e.getChemin().equals(":core")));
        assertTrue(app.stream().anyMatch(e ->
                e.getKind() == KindEntree.JAR
                        && e.getChemin().equals(aarContents.classesJar.toString())
                        && aarFile.toString().equals(e.getAar())));
        assertTrue(app.stream().anyMatch(e ->
                e.getKind() == KindEntree.JAR
                        && e.getChemin().contains("moteur.jar")
                        && aarFile.toString().equals(e.getAar())));
        assertTrue(app.stream().noneMatch(e ->
                e.getKind() == KindEntree.AAR));
        // runtime-only : hors de la vue de compilation.
        assertTrue(app.stream().noneMatch(e ->
                e.getChemin().contains("runtime")));
        List<EntreeClasspathInstantane> core = moduleNamed(projet, ":core")
                .getClasspath().getEntrees();
        assertTrue(core.stream().anyMatch(e ->
                e.getKind() == KindEntree.JAR
                        && e.getChemin().equals(coreJar.toString())));
        assertTrue(core.stream().noneMatch(e -> e.getKind() == KindEntree.MODULE));
    }

    @Test
    void dependanceNonResolueSignaleeJamaisMuet() {
        ProjetLsp projet = adapt(workspace(coreModule(), appModule()),
                resolution(null, aarFile), AndroidPlatform.fixed(androidJar));
        ModuleLsp core = moduleNamed(projet, ":core");
        assertFalse(core.getIgnore());
        assertTrue(core.getAvertissements().stream()
                .anyMatch(a -> a.contains("unresolved dependency")
                        && a.contains("org.exemple:core")));
        assertTrue(core.getClasspath().getEntrees().isEmpty());
    }

    @Test
    void moduleSansRepertoireEcarteAvecRaison() throws IOException {
        Module fantome = new Module(":fantome", "fantome",
                ModuleType.JVM_LIBRARY,
                Map.of("main", new SourceSet("main", List.of(
                        new ContentRoot("src", ContentRole.SOURCE)))),
                List.of(), Map.of());
        ProjetLsp projet = adapt(workspace(coreModule(), fantome),
                resolution(coreJar, null), AndroidPlatform.fixed(androidJar));
        ModuleLsp module = moduleNamed(projet, ":fantome");
        assertTrue(module.getIgnore());
        assertNotNull(module.getRaisonIgnore());
        assertTrue(module.getRaisonIgnore().contains("not found"));
    }

    @Test
    void typeInconnuEcarteAvecRaison() throws IOException {
        Path dir = root.resolve("bizarre");
        Files.createDirectories(dir);
        Module bizarre = new Module(":bizarre", "bizarre",
                ModuleType.of("future-library"),
                Map.of("main", new SourceSet("main", List.of(
                        new ContentRoot("src", ContentRole.SOURCE)))),
                List.of(), Map.of());
        ProjetLsp projet = adapt(workspace(coreModule(), bizarre),
                resolution(coreJar, null), AndroidPlatform.fixed(androidJar));
        ModuleLsp module = moduleNamed(projet, ":bizarre");
        assertTrue(module.getIgnore());
        assertTrue(module.getRaisonIgnore().contains("unsupported module type"));
    }

    @Test
    void contratAndroidCompleteDepuisFacette() {
        ProjetLsp projet = adapt(workspace(coreModule(), appModule()),
                resolution(coreJar, aarFile), AndroidPlatform.fixed(androidJar));
        ModuleLsp app = moduleNamed(projet, ":app");
        assertNotNull(app.getAndroid());
        assertEquals("org.exemple.app", app.getAndroid().getNamespace());
        assertEquals("org.exemple.app.id", app.getAndroid().getApplicationId());
        assertEquals(Integer.valueOf(24),
                app.getAndroid().getMinSdk());
        assertEquals(Integer.valueOf(28),
                app.getAndroid().getTargetSdk());
        assertEquals(Integer.valueOf(35),
                app.getAndroid().getCompileSdk());
        assertEquals(List.of(appDir.resolve("src/main/res").toString()),
                app.getAndroid().getDossiersRes());
        assertEquals(appDir.resolve("src/main/AndroidManifest.xml").toString(),
                app.getAndroid().getManifest());
    }

    @Test
    void plateformeAbsenteModuleAndroidResteAndroidAverti() {
        ProjetLsp projet = adapt(workspace(coreModule(), appModule()),
                resolution(coreJar, aarFile), AndroidPlatform.absent());
        ModuleLsp app = moduleNamed(projet, ":app");
        assertEquals(TypeModuleLsp.ANDROID, app.getType());
        assertNull(app.getAndroidJar());
        assertEquals("debug", app.getVariante());
        assertTrue(app.getAvertissements().stream()
                .anyMatch(a -> a.contains("android platform")));
    }

    @Test
    void changerUneDependanceChangeLEmpreinteDuSeulModuleConcerne() {
        ProjetLsp avant = adapt(workspace(coreModule(), appModule()),
                resolution(coreJar, aarFile), AndroidPlatform.fixed(androidJar));
        // Le jar de :core change (contenu différent) ; :app ne bouge pas
        // (son entrée :core est nominale — empreinte du frère chez le frère).
        Path autreJar = tmp.resolve("core-1.1.jar");
        try {
            Files.write(autreJar, new byte[] {99, 98, 97, 96});
        } catch (IOException e) {
            fail(e);
        }
        ProjetLsp apres = adapt(workspace(coreModule(), appModule()),
                resolution(autreJar, aarFile), AndroidPlatform.fixed(androidJar));
        assertNotEquals(moduleNamed(avant, ":core").getEmpreinte(),
                moduleNamed(apres, ":core").getEmpreinte());
        assertEquals(moduleNamed(avant, ":app").getEmpreinte(),
                moduleNamed(apres, ":app").getEmpreinte());
        assertNotEquals(avant.getEmpreinte(), apres.getEmpreinte());
    }

    @Test
    void determinismeDeuxAdaptationsIdentiques() throws IOException {
        Workspace space = workspace(coreModule(), appModule());
        ExternalResolution externals = resolution(coreJar, aarFile);
        ProjetLsp premier = adapt(space, externals,
                AndroidPlatform.fixed(androidJar));
        ProjetLsp second = adapt(space, externals,
                AndroidPlatform.fixed(androidJar));
        assertEquals(premier.getEmpreinte(), second.getEmpreinte());
        assertEquals(moduleNamed(premier, ":app").getEmpreinte(),
                moduleNamed(second, ":app").getEmpreinte());
        assertEquals(moduleNamed(premier, ":core").getEmpreinte(),
                moduleNamed(second, ":core").getEmpreinte());
    }

    @Test
    void dependanceDeModuleInconnueSignalee() {
        Module orphelin = new Module(":orphelin", "orphelin",
                ModuleType.JVM_LIBRARY,
                Map.of("main", new SourceSet("main", List.of(
                        new ContentRoot("src", ContentRole.SOURCE)))),
                List.of(DependencyRef.toModule(":inconnu",
                        DependencyScope.IMPLEMENTATION, java.util.Set.of())),
                Map.of());
        try {
            Files.createDirectories(root.resolve("orphelin/src"));
        } catch (IOException e) {
            fail(e);
        }
        ProjetLsp projet = adapt(workspace(coreModule(), orphelin),
                resolution(coreJar, null), AndroidPlatform.fixed(androidJar));
        ModuleLsp module = moduleNamed(projet, ":orphelin");
        assertTrue(module.getAvertissements().stream()
                .anyMatch(a -> a.contains("unknown sibling module")
                        && a.contains(":inconnu")));
        assertTrue(module.getClasspath().getEntrees().stream()
                .noneMatch(e -> e.getKind() == KindEntree.MODULE));
    }

    @Test
    void cycleRejeteAvecAvertissement() {
        Module a = moduleAvecDeps(":a", ":b");
        Module b = moduleAvecDeps(":b", ":a");
        ProjetLsp projet = adapt(workspace(coreModule(), a, b),
                resolution(coreJar, null), AndroidPlatform.fixed(androidJar));
        ModuleLsp premier = moduleNamed(projet, ":a");
        ModuleLsp second = moduleNamed(projet, ":b");
        // Une des deux arêtes survit, l'autre est rejetée avec raison —
        // jamais les deux (le graphe reste acyclique).
        boolean aVersB = premier.getDependancesModules().stream()
                .anyMatch(d -> d.getCible().equals(":b"));
        boolean bVersA = second.getDependancesModules().stream()
                .anyMatch(d -> d.getCible().equals(":a"));
        assertNotEquals(aVersB, bVersA);
        assertTrue(premier.getAvertissements().stream()
                        .anyMatch(x -> x.contains("cyclique"))
                || second.getAvertissements().stream()
                        .anyMatch(x -> x.contains("cyclique")));
    }

    private Module moduleAvecDeps(String chemin, String cible) {
        try {
            Files.createDirectories(
                    root.resolve(chemin.substring(1)).resolve("src"));
        } catch (IOException e) {
            fail(e);
        }
        return new Module(chemin, chemin.substring(1), ModuleType.JVM_LIBRARY,
                Map.of("main", new SourceSet("main", List.of(
                        new ContentRoot("src", ContentRole.SOURCE)))),
                List.of(DependencyRef.toModule(cible,
                        DependencyScope.IMPLEMENTATION, java.util.Set.of())),
                Map.of());
    }
}
