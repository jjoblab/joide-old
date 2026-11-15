package jo.joide.ide.sync;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jo.codeide.lsp.model.ProjetLsp;
import jo.joide.deps.cache.DependencyCache;
import jo.joide.deps.repo.Layout;
import jo.joide.deps.repo.Repository;
import jo.joide.deps.resolve.ResolverConfig;
import jo.joide.model.ContentRole;
import jo.joide.model.ContentRoot;
import jo.joide.model.DependencyRef;
import jo.joide.model.DependencyScope;
import jo.joide.model.Module;
import jo.joide.model.ModuleType;
import jo.joide.model.Project;
import jo.joide.model.SourceSet;
import jo.joide.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests de la synchronisation du modèle de langage : publication par
 * instantanés successifs (jamais de mutation), vue JoIDE sans type de
 * brique, et résolution réelle par le résolveur P4 sur un dépôt local.
 */
final class LanguageModelSyncTest {

    @TempDir
    Path tmp;

    private Path root;
    private Path moduleDir;
    private Path androidJar;

    @BeforeEach
    void setUp() throws IOException {
        root = tmp.resolve("projet");
        moduleDir = root.resolve("core");
        Files.createDirectories(moduleDir.resolve("src/main/java"));
        androidJar = tmp.resolve("android.jar");
        Files.write(androidJar, new byte[] {1});
    }

    private Workspace workspaceWithExternal(String coordinates)
            throws IOException {
        Module module = new Module(":core", "core", ModuleType.JVM_LIBRARY,
                Map.of("main", new SourceSet("main", List.of(
                        new ContentRoot("src/main/java", ContentRole.SOURCE)))),
                List.of(DependencyRef.external(coordinates,
                        DependencyScope.IMPLEMENTATION, java.util.Set.of())),
                Map.of());
        Map<String, Module> modules = new LinkedHashMap<>();
        modules.put(module.path(), module);
        return new Workspace("projet",
                Map.of("main", new Project("main", modules)), Map.of());
    }

    private LanguageModelSync syncWithFixed(ExternalResolution externals) {
        ProjetLspAdapter adapter = new ProjetLspAdapter(
                ModuleDirectories.nested(root), AndroidPlatform.absent());
        return new LanguageModelSync(root, adapter,
                Resolutions.fixed(externals));
    }

    @Test
    void publicationParInstantanesSuccessifsJamaisDeMutation()
            throws IOException {
        Path jar = tmp.resolve("biblio-1.0.jar");
        Files.write(jar, new byte[] {7, 7, 7});
        ExternalResolution withArtifact =
                new ExternalResolution.Builder()
                        .add("org.exemple:biblio", jar, false, null).build();
        ExternalResolution withoutArtifact =
                new ExternalResolution.Builder().build();
        // La résolution est volatile : le même dépôt publie deux
        // instantanés SUCCESSIFS — jamais de mutation du précédent.
        ExternalResolution[] current = {withArtifact};
        ProjetLspAdapter adapter = new ProjetLspAdapter(
                ModuleDirectories.nested(root), AndroidPlatform.absent());
        LanguageModelSync sync = new LanguageModelSync(root, adapter,
                workspace -> current[0]);
        Workspace space = workspaceWithExternal("org.exemple:biblio:1.0");
        LanguageModelSnapshot first = sync.synchroniser(space);
        assertEquals(first.fingerprint(),
                sync.snapshot().orElseThrow().fingerprint());

        current[0] = withoutArtifact;
        LanguageModelSnapshot second = sync.synchroniser(space);
        assertNotEquals(first.fingerprint(), second.fingerprint());
        assertEquals(second.fingerprint(),
                sync.snapshot().orElseThrow().fingerprint());
    }

    @Test
    void vueJoideSansTypeDeBrique() throws IOException {
        LanguageModelSync sync = syncWithFixed(
                new ExternalResolution.Builder().build());
        LanguageModelSnapshot snapshot =
                sync.synchroniser(workspaceWithExternal("org.exemple:x:1.0"));
        assertEquals(root.toString(), snapshot.rootDirectory());
        assertEquals(1, snapshot.modules().size());
        LanguageModelSnapshot.ModuleSummary summary =
                snapshot.module(":core").orElseThrow();
        assertEquals("JVM", summary.type());
        assertFalse(summary.ignored());
        assertEquals(64, snapshot.fingerprint().length());
    }

    @Test
    void resolutionReelleParLeResolveurP4DepotLocal() throws IOException {
        // Dépôt Maven local minimal : POM + jar sans checksums publiés
        // (première source fait foi : aucun checksum = rien à vérifier).
        Path repo = tmp.resolve("m2");
        String pom = "<project><groupId>org.exemple</groupId>"
                + "<artifactId>biblio</artifactId>"
                + "<version>1.4</version></project>";
        writeArtifactWithSha256(repo,
                Layout.pomPath("org.exemple", "biblio", "1.4"),
                pom.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        writeArtifactWithSha256(repo, Layout.artifactPath(
                "org.exemple", "biblio", "1.4", null, "jar"),
                new byte[] {9, 8, 7, 6});

        ResolverConfig config = new ResolverConfig.Builder()
                .repository(new Repository.Local("local", repo))
                .cache(new DependencyCache(tmp.resolve("cache")))
                .maxParallelDownloads(1)
                .build();
        ProjetLspAdapter adapter = new ProjetLspAdapter(
                ModuleDirectories.nested(root), AndroidPlatform.absent());
        LanguageModelSync sync = new LanguageModelSync(root, adapter,
                Resolutions.byResolver(config));

        LanguageModelSnapshot snapshot = sync.synchroniser(
                workspaceWithExternal("org.exemple:biblio:1.4"));
        LanguageModelSnapshot.ModuleSummary summary =
                snapshot.module(":core").orElseThrow();
        assertFalse(summary.ignored(), () -> summary.warnings().toString());
        // Le jar résolu figure au classpath du module : la vue récapitule
        // l'entrée par l'empreinte du module, non vide.
        assertTrue(snapshot.problems().isEmpty(),
                () -> "problemes inattendus : " + snapshot.problems());
        assertEquals(64, summary.fingerprint().length());
        // La vue brute (interne au module) porte bien l'entrée JAR
        // résolue : le fichier du cache adressé par contenu (P4).
        ProjetLsp raw = snapshot.raw();
        List<jo.codeide.lsp.model.EntreeClasspathInstantane> entrees =
                raw.getModules().get(0).getClasspath().getEntrees();
        assertEquals(1, entrees.size(),
                () -> "entrees : " + entrees);
        assertEquals(jo.codeide.lsp.model.KindEntree.JAR,
                entrees.get(0).getKind());
        assertTrue(entrees.get(0).getChemin()
                        .startsWith(tmp.resolve("cache").toString()),
                () -> "chemin hors cache : " + entrees.get(0).getChemin());
        assertTrue(java.nio.file.Files.isRegularFile(
                java.nio.file.Path.of(entrees.get(0).getChemin())),
                () -> "fichier absent : " + entrees.get(0).getChemin());
    }

    @Test
    void exigenceDeVersionInvalideTransporteeJamaisMuet() throws IOException {
        ResolverConfig config = new ResolverConfig.Builder()
                .repository(new Repository.Local("local", tmp.resolve("m2")))
                .cache(new DependencyCache(tmp.resolve("cache")))
                .build();
        ProjetLspAdapter adapter = new ProjetLspAdapter(
                ModuleDirectories.nested(root), AndroidPlatform.absent());
        LanguageModelSync sync = new LanguageModelSync(root, adapter,
                Resolutions.byResolver(config));
        // Une plage malformée (« (1.0 » non fermée) passe la validation
        // du modèle mais fait échouer l'exigence au rassemblement :
        // le problème est transporté, jamais une exception ni un silence.
        LanguageModelSnapshot snapshot = sync.synchroniser(
                workspaceWithExternal("org.exemple:biblio:(1.0"));
        assertTrue(snapshot.problems().stream()
                        .anyMatch(p -> p.contains("invalid")),
                () -> "problemes : " + snapshot.problems());
    }

    private static void writeText(Path repo, String relPath, String text)
            throws IOException {
        writeBytes(repo.resolve(relPath),
                text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void writeBytes(Path file, byte[] bytes)
            throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    /** Écrit un artefact avec son checksum .sha256 publié (exigé). */
    private static void writeArtifactWithSha256(Path repo, String relPath,
            byte[] bytes) throws IOException {
        writeBytes(repo.resolve(relPath), bytes);
        writeText(repo, relPath + ".sha256",
                jo.joide.deps.Checksums.sha256Hex(bytes) + "\n");
    }
}
