package jo.joide.ide.editeur;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import jo.celeris.core.lsp.types.DiagnosticLsp;
import jo.celeris.core.lsp.types.EditionEspaceLsp;
import jo.celeris.core.lsp.types.ListeCompletion;
import jo.celeris.core.text.Document;
import jo.celeris.core.text.TextSession;
import jo.joide.ide.langages.MoteursLangage;
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
 * Le pont de session celeris ↔ moteurs JoIDE sur de VRAIS moteurs (lot
 * C) : diagnostics poussés après ouverture et après édition (fusion
 * des frappes), complétion/survol/signature par les futurs, et les
 * vides honnêtes de la couverture non servie. La publication des
 * diagnostics passe par l'exécuteur direct du test.
 */
final class SessionLangageCelerisTest {

    @TempDir
    Path tmp;

    private Path racine;
    private Path sources;
    private MoteursLangage moteurs;
    private SessionLangageCeleris session;

    @BeforeEach
    void preparer() throws IOException {
        racine = Files.createDirectories(tmp.resolve("projet"));
        sources = Files.createDirectories(
                racine.resolve("app/src/main/java"));
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
                Files.createDirectories(tmp.resolve("index")));
        moteurs.synchroniser(espace);
        session = new SessionLangageCeleris(moteurs,
                Runnable::run);
    }

    @AfterEach
    void fermer() {
        if (session != null) {
            session.close();
        }
        if (moteurs != null) {
            moteurs.close();
        }
    }

    /** Attend qu'un diagnostic de gravité donnée arrive (borné). */
    private List<DiagnosticLsp> attendreDiagnostics(String uri,
            int gravite) throws InterruptedException {
        AtomicReference<List<DiagnosticLsp>> recues =
                new AtomicReference<>(List.of());
        CountDownLatch verrou = new CountDownLatch(1);
        BiConsumer<String, List<DiagnosticLsp>> auditeur = (u, d) -> {
            recues.set(d);
            if (d.stream().anyMatch(x -> x.gravite() == gravite)) {
                verrou.countDown();
            }
        };
        session.poserAuditeurDiagnostics(auditeur);
        assertTrue(verrou.await(30, TimeUnit.SECONDS),
                "le diagnostic de gravité " + gravite
                        + " doit arriver (reçus : " + recues.get() + ")");
        return recues.get();
    }

    @Test
    void ouverturePousseLesDiagnosticsDErreur() throws Exception {
        Path fichier = sources.resolve("fr/exemple/Main.java");
        Files.createDirectories(fichier.getParent());
        String texte = """
                package fr.exemple;

                class Main {
                    void usage() {
                        TypeInconnu v = null;
                    }
                }
                """;
        Files.writeString(fichier, texte);
        String uri = fichier.toString();
        session.ouvrir(uri, "java", texte);
        List<DiagnosticLsp> diagnostics = attendreDiagnostics(uri,
                DiagnosticLsp.ERREUR);
        assertTrue(diagnostics.stream().anyMatch(
                d -> d.message().contains("TypeInconnu")),
                "l'erreur du type inconnu est publiée : " + diagnostics);
        assertEquals("joide", diagnostics.get(0).source());
    }

    @Test
    void synchronisationRefleteLEdition() throws Exception {
        Path fichier = sources.resolve("fr/exemple/Sain.java");
        Files.createDirectories(fichier.getParent());
        String sain = """
                package fr.exemple;

                class Sain {
                    int somme(int a, int b) {
                        return a + b;
                    }
                }
                """;
        String casse = sain.replace("return a + b;",
                "return a + b; TypeInconnu t = null;");
        Files.writeString(fichier, sain);
        String uri = fichier.toString();
        session.ouvrir(uri, "java", sain);
        // Le fichier sain ne publiera jamais d'erreur : l'attente est
        // sur l'ÉDITION qui introduit l'erreur.
        session.synchroniser(uri, casse);
        List<DiagnosticLsp> diagnostics = attendreDiagnostics(uri,
                DiagnosticLsp.ERREUR);
        assertTrue(diagnostics.stream().anyMatch(
                d -> d.message().contains("TypeInconnu")),
                "l'erreur introduite par l'édition est publiée : "
                        + diagnostics);
    }

    @Test
    void sertCompletionEtLaCompletionPasse() throws Exception {
        Path fichier = sources.resolve("fr/exemple/Usage.java");
        Files.createDirectories(fichier.getParent());
        String texte = """
                package fr.exemple;

                class Usage {
                    void methode() {
                        Lis
                    }
                }
                """;
        Files.writeString(fichier, texte);
        String uri = fichier.toString();
        session.ouvrir(uri, "java", texte);
        assertTrue(session.sertCompletion());
        TextSession edition = TextSession.of(texte, System::nanoTime);
        Document document = edition.document();
        int caret = texte.indexOf("Lis") + "Lis".length();
        ListeCompletion reponse = session.completion(uri, document,
                caret).get(30, TimeUnit.SECONDS);
        assertTrue(reponse.elements().stream().anyMatch(
                e -> e.libelle().startsWith("List")),
                "java.util.List est proposé : " + reponse.elements());
    }

    @Test
    void survolRendLeModelDeDocumentation() throws Exception {
        Path fichier = sources.resolve("fr/exemple/Survol.java");
        Files.createDirectories(fichier.getParent());
        String texte = """
                package fr.exemple;

                class Survol {
                    int somme(int a, int b) {
                        return a + b;
                    }
                    int appel() {
                        return somme(1, 2);
                    }
                }
                """;
        Files.writeString(fichier, texte);
        String uri = fichier.toString();
        session.ouvrir(uri, "java", texte);
        Document document = Document.of(texte);
        int caret = texte.indexOf("somme(1, 2)") + "somme".length();
        var modele = session.survol(uri, document, "somme", caret)
                .get(30, TimeUnit.SECONDS);
        assertNotNull(modele, "le survol de la méthode locale existe");
        assertTrue(modele.titre().contains("somme"),
                "le titre du modèle porte le symbole : " + modele.titre());
    }

    @Test
    void signatureRendLeModelDesParametres() throws Exception {
        Path fichier = sources.resolve("fr/exemple/Sig.java");
        Files.createDirectories(fichier.getParent());
        String texte = """
                package fr.exemple;

                class Sig {
                    int somme(int a, int b) {
                        return a + b;
                    }
                    int appel() {
                        return somme(1, 2);
                    }
                }
                """;
        Files.writeString(fichier, texte);
        String uri = fichier.toString();
        session.ouvrir(uri, "java", texte);
        Document document = Document.of(texte);
        int caret = texte.indexOf("somme(1, 2)") + "somme(".length();
        var modele = session.signature(uri, document, caret)
                .get(30, TimeUnit.SECONDS);
        assertNotNull(modele, "l'aide aux paramètres existe");
        assertTrue(modele.nombreSignatures() >= 1,
                "au moins une signature candidate");
        assertTrue(modele.libelle().contains("somme"),
                "la signature active porte la méthode : " + modele.libelle());
    }

    @Test
    void laCouvertureNonServieRendSesVides() throws Exception {
        Path fichier = sources.resolve("fr/exemple/Vides.java");
        Files.createDirectories(fichier.getParent());
        String texte = """
                package fr.exemple;

                class Vides {
                }
                """;
        Files.writeString(fichier, texte);
        String uri = fichier.toString();
        session.ouvrir(uri, "java", texte);
        assertTrue(session.symboles(uri).get(5, TimeUnit.SECONDS)
                .isEmpty());
        assertTrue(session.plis(uri).get(5, TimeUnit.SECONDS).isEmpty());
        assertEquals(EditionEspaceLsp.VIDE,
                session.rennomage(uri, Document.of(texte), 10, "nouveau")
                        .get(5, TimeUnit.SECONDS));
        assertNull(session.preparerRennomage(uri, Document.of(texte), 10)
                .get(5, TimeUnit.SECONDS));
        assertTrue(session.jetons(uri).get(5, TimeUnit.SECONDS)
                .jetons().isEmpty());
        assertTrue(session.references(uri, Document.of(texte), 10)
                .get(5, TimeUnit.SECONDS).isEmpty());
    }
}
