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
import jo.joide.ide.sync.LanguageModelSnapshot;
import jo.joide.ide.sync.LanguageModelSync;
import jo.joide.ide.sync.ModuleDirectories;
import jo.joide.ide.sync.ProjetLspAdapter;
import jo.joide.ide.sync.Resolutions;
import jo.joide.model.ContentRole;
import jo.joide.model.ContentRoot;
import jo.joide.model.DependencyRef;
import jo.joide.model.DependencyScope;
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
 * Le moteur Java tourne RÉELLEMENT dans ces tests (ecj patché ART au
 * classpath de test) : diagnostics, complétion, survol, définition —
 * sur un projet à deux modules dont les classpaths sont ISOLÉS (W5) :
 * un jar visible du seul module qui le déclare.
 */
final class MoteursLangageJavaTest {

    @TempDir
    Path tmp;

    private Path racine;
    private Path sourcesA;
    private Path sourcesB;
    private Path stockageIndex;
    private MoteursLangage moteurs;

    @BeforeEach
    void preparer() throws IOException {
        racine = Files.createDirectories(tmp.resolve("projet"));
        sourcesA = Files.createDirectories(
                racine.resolve("a/src/main/java"));
        sourcesB = Files.createDirectories(
                racine.resolve("b/src/main/java"));
        stockageIndex = Files.createDirectories(tmp.resolve("index"));
    }

    @AfterEach
    void fermer() {
        if (moteurs != null) {
            moteurs.close();
        }
    }

    private void ecrireA(Path relatif, String contenu) throws IOException {
        Files.createDirectories(sourcesA.resolve(relatif).getParent());
        Files.writeString(sourcesA.resolve(relatif), contenu);
    }

    private void ecrireB(Path relatif, String contenu) throws IOException {
        Files.createDirectories(sourcesB.resolve(relatif).getParent());
        Files.writeString(sourcesB.resolve(relatif), contenu);
    }

    /** Deux modules JVM ; seul :b déclare la dépendance externe. */
    private Workspace espace() {
        SourceSet mainA = new SourceSet("main", List.of(
                new ContentRoot("src/main/java", ContentRole.SOURCE)));
        Module a = new Module(":a", "a", ModuleType.JVM_LIBRARY,
                Map.of("main", mainA), List.of(), Map.of());
        SourceSet mainB = new SourceSet("main", List.of(
                new ContentRoot("src/main/java", ContentRole.SOURCE)));
        Module b = new Module(":b", "b", ModuleType.JVM_LIBRARY,
                Map.of("main", mainB),
                List.of(DependencyRef.external("fr.exemple:outil-b:1.0",
                        DependencyScope.IMPLEMENTATION, java.util.Set.of())),
                Map.of());
        Map<String, Module> modules = new LinkedHashMap<>();
        modules.put(a.path(), a);
        modules.put(b.path(), b);
        return new Workspace("projet",
                Map.of("main", new Project("main", modules)), Map.of());
    }

    private MoteursLangage monter(Path jarB) {
        return monter(jarB, null);
    }

    /**
     * Monte les moteurs avec une résolution VOLATILE : le tableau
     * permet au test de changer la résolution entre deux sync (re-
     * nourrissage du contexte, jamais de gel du premier instantané).
     */
    private MoteursLangage monter(Path jarB,
            ExternalResolution[] volatileResolution) {
        ExternalResolution externals = jarB == null
                ? new ExternalResolution.Builder().build()
                : new ExternalResolution.Builder()
                        .add("fr.exemple:outil-b", jarB, false, null)
                        .build();
        java.util.function.Function<jo.joide.model.Workspace,
                ExternalResolution> resolution;
        if (volatileResolution == null) {
            resolution = Resolutions.fixed(externals);
        } else {
            volatileResolution[0] = externals;
            resolution = w -> volatileResolution[0];
        }
        LanguageModelSync sync = new LanguageModelSync(racine,
                new ProjetLspAdapter(ModuleDirectories.nested(racine),
                        AndroidPlatform.absent()),
                resolution);
        moteurs = new MoteursLangage(sync, stockageIndex);
        return moteurs;
    }

    @Test
    void diagnosticsDErreursAvecPlagesEtGravite() throws IOException {
        ecrireA(Path.of("fr/exemple/A.java"),
                """
                package fr.exemple;

                class A {
                    void usage() {
                        TypeInconnu v = null;
                    }
                }
                """);
        moteurs = monter(null);
        moteurs.synchroniser(espace());

        List<DiagnosticIde> diagnostics = moteurs.diagnostiquer(
                sourcesA.resolve("fr/exemple/A.java").toString(),
                Files.readString(
                        sourcesA.resolve("fr/exemple/A.java")));
        assertFalse(diagnostics.isEmpty(),
                "le type inconnu doit être signalé");
        assertTrue(diagnostics.stream().anyMatch(
                d -> d.gravite() == DiagnosticIde.Gravite.ERREUR),
                "au moins une erreur : " + diagnostics);
        for (DiagnosticIde diagnostic : diagnostics) {
            assertTrue(diagnostic.debut() >= 0);
            assertTrue(diagnostic.fin() >= diagnostic.debut());
            assertNotNull(diagnostic.message());
        }
    }

    @Test
    void fichierSainSansErreur() throws IOException {
        ecrireB(Path.of("fr/exemple/B.java"),
                """
                package fr.exemple;

                class B {
                    int somme(int a, int b) {
                        return a + b;
                    }
                }
                """);
        Path fichier = sourcesB.resolve("fr/exemple/B.java");
        moteurs = monter(null);
        moteurs.synchroniser(espace());
        List<DiagnosticIde> diagnostics = moteurs.diagnostiquer(
                fichier.toString(), Files.readString(fichier));
        assertTrue(diagnostics.stream().noneMatch(
                d -> d.gravite() == DiagnosticIde.Gravite.ERREUR),
                "aucune erreur attendue : " + diagnostics);
    }

    @Test
    void isolationParModuleLeJarNestVisibleQueDeSonModule()
            throws IOException {
        Path jar = CompilateurDeClasse.compilerEnJar(tmp, "OutilB.java",
                """
                package fr.exemple;

                class OutilB {
                    public static String saluer() {
                        return "salut";
                    }
                }
                """,
                "outil-b.jar");
        ecrireA(Path.of("fr/exemple/UtilisateurA.java"),
                """
                package fr.exemple;

                class UtilisateurA {
                    String appel() {
                        return OutilB.saluer();
                    }
                }
                """);
        ecrireB(Path.of("fr/exemple/UtilisateurB.java"),
                """
                package fr.exemple;

                class UtilisateurB {
                    String appel() {
                        return OutilB.saluer();
                    }
                }
                """);
        moteurs = monter(jar);
        moteurs.synchroniser(espace());

        Path fichierA = sourcesA.resolve("fr/exemple/UtilisateurA.java");
        List<DiagnosticIde> diagnostiquesA = moteurs.diagnostiquer(
                fichierA.toString(), Files.readString(fichierA));
        assertTrue(diagnostiquesA.stream().anyMatch(
                d -> d.gravite() == DiagnosticIde.Gravite.ERREUR),
                ":a ne déclare pas le jar — l'usage doit être une erreur : "
                        + diagnostiquesA);

        Path fichierB = sourcesB.resolve("fr/exemple/UtilisateurB.java");
        List<DiagnosticIde> diagnostiquesB = moteurs.diagnostiquer(
                fichierB.toString(), Files.readString(fichierB));
        assertTrue(diagnostiquesB.stream().noneMatch(
                d -> d.gravite() == DiagnosticIde.Gravite.ERREUR),
                ":b déclare le jar — l'usage doit être résolu : "
                        + diagnostiquesB);
    }

    @Test
    void reSynchronisationReNourritLeContexteDuModule() throws IOException {
        Path jar = CompilateurDeClasse.compilerEnJar(tmp, "OutilB.java",
                """
                package fr.exemple;

                class OutilB {
                    public static String saluer() {
                        return "salut";
                    }
                }
                """,
                "outil-b.jar");
        ecrireB(Path.of("fr/exemple/UtilisateurB.java"),
                """
                package fr.exemple;

                class UtilisateurB {
                    String appel() {
                        return OutilB.saluer();
                    }
                }
                """);
        Path fichier = sourcesB.resolve("fr/exemple/UtilisateurB.java");
        ExternalResolution[] resolution = new ExternalResolution[1];
        moteurs = monter(jar, resolution);
        moteurs.synchroniser(espace());
        assertTrue(moteurs.diagnostiquer(fichier.toString(),
                Files.readString(fichier)).stream().noneMatch(
                        d -> d.gravite() == DiagnosticIde.Gravite.ERREUR),
                "première sync : le jar est résolu");

        // Le jar DISPARAÎT de la résolution : la nouvelle sync doit
        // rendre l'usage non résolu — le contexte est re-nourri, pas
        // gelé sur le premier instantané.
        resolution[0] = new ExternalResolution.Builder().build();
        moteurs.synchroniser(espace());
        List<DiagnosticIde> apres = moteurs.diagnostiquer(
                fichier.toString(), Files.readString(fichier));
        assertTrue(apres.stream().anyMatch(
                d -> d.gravite() == DiagnosticIde.Gravite.ERREUR),
                "après re-sync sans jar, l'usage doit être une erreur : "
                        + apres);
    }

    @Test
    void completionProposeLesTypesDuClasspath() throws IOException {
        ecrireB(Path.of("fr/exemple/B.java"),
                """
                package fr.exemple;

                class B {
                    void usage() {
                        Lis
                    }
                }
                """);
        Path fichier = sourcesB.resolve("fr/exemple/B.java");
        String texte = Files.readString(fichier);
        moteurs = monter(null);
        moteurs.synchroniser(espace());
        int curseur = texte.indexOf("Lis") + "Lis".length();
        List<PropositionCompletion> propositions = moteurs.completer(
                fichier.toString(), texte, curseur);
        assertTrue(propositions.stream().anyMatch(
                p -> p.libelle().startsWith("List")),
                "java.util.List doit être proposé : " + propositions);
        PropositionCompletion liste = propositions.stream()
                .filter(p -> p.libelle().startsWith("List")).findFirst()
                .orElseThrow();
        assertNotNull(liste.importAuto(),
                "la proposition porte son import automatique");
    }

    @Test
    void definitionEtSurvolDansLeFichierCourant() throws IOException {
        ecrireB(Path.of("fr/exemple/B.java"),
                """
                package fr.exemple;

                class B {
                    int somme(int a, int b) {
                        return a + b;
                    }
                    int appel() {
                        return somme(1, 2);
                    }
                }
                """);
        Path fichier = sourcesB.resolve("fr/exemple/B.java");
        String texte = Files.readString(fichier);
        moteurs = monter(null);
        moteurs.synchroniser(espace());

        int curseurSomme = texte.indexOf("somme(1, 2)") + "somme".length();
        List<CibleNavigation> cibles = moteurs.definition(
                fichier.toString(), texte, curseurSomme);
        assertFalse(cibles.isEmpty(), "la méthode locale est navigable");
        assertEquals("SOURCE_COURANTE", cibles.get(0).genre());

        var survol = moteurs.survol(fichier.toString(), texte,
                curseurSomme);
        assertTrue(survol.isPresent(), "le survol de la méthode existe");
        assertTrue(survol.orElseThrow().signature().contains("somme"),
                "la signature mentionne la méthode : " + survol);
    }

    @Test
    void modifierRefleteLeNouveauTexte() throws IOException {
        ecrireB(Path.of("fr/exemple/B.java"),
                """
                package fr.exemple;

                class B {
                    void usage() {
                    }
                }
                """);
        Path fichier = sourcesB.resolve("fr/exemple/B.java");
        moteurs = monter(null);
        moteurs.synchroniser(espace());
        String sain = Files.readString(fichier);
        assertTrue(moteurs.diagnostiquer(fichier.toString(), sain)
                .stream().noneMatch(d -> d.gravite()
                        == DiagnosticIde.Gravite.ERREUR));

        String casse = sain.replace("void usage() {",
                "void usage() { TypeInconnu t = null;");
        moteurs.ouvrir(fichier.toString(), sain);
        moteurs.modifier(fichier.toString(), casse);
        assertTrue(moteurs.diagnostiquer(fichier.toString(), casse)
                .stream().anyMatch(d -> d.gravite()
                        == DiagnosticIde.Gravite.ERREUR),
                "l'erreur introduite par l'édition est diagnostiquée");
        moteurs.fermerDocument(fichier.toString());
    }

    @Test
    void lEtatDeLIndexDevientPretAvecLesEntreesReelles() throws Exception {
        Path jar = CompilateurDeClasse.compilerEnJar(tmp, "OutilB.java",
                """
                package fr.exemple;

                class OutilB {
                    public static String saluer() {
                        return "salut";
                    }
                }
                """,
                "outil-b.jar");
        moteurs = monter(jar);
        moteurs.synchroniser(espace());

        // La construction CIDX est asynchrone : attendre PRET (borné).
        EtatIndex etat = moteurs.etatIndex();
        long borne = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < borne) {
            etat = moteurs.etatIndex();
            if ("PRET".equals(etat.phase())
                    || "PARTIEL".equals(etat.phase())
                    || "ERREUR".equals(etat.phase())) {
                break;
            }
            Thread.sleep(100);
        }
        assertEquals("PRET", etat.phase(),
                "l'index réel doit devenir PRÊT : " + etat);
        assertTrue(etat.total() >= 1,
                "l'union indexable compte le jar : " + etat);
        assertTrue(etat.faits() >= 1, "au moins une entrée construite");
    }

    @Test
    void snapshotPublieApresSynchronisation() throws IOException {
        moteurs = monter(null);
        LanguageModelSnapshot vue = moteurs.synchroniser(espace());
        assertEquals(vue.fingerprint(),
                moteurs.snapshot().orElseThrow().fingerprint());
        assertTrue(vue.modules().stream().anyMatch(
                m -> m.name().equals(":a") && !m.ignored()));
    }
}
