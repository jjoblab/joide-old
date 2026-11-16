package jo.joide.ide.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.agent.espace.DiagnosticEspace;
import jo.joide.agent.espace.EspaceNonSupporte;
import jo.joide.agent.espace.OccurrenceEspace;
import jo.joide.agent.espace.PositionEspace;
import jo.joide.agent.espace.ResultatExecution;
import jo.joide.build.daemon.protocol.BuildSpec;
import jo.joide.build.daemon.runner.BuildRunner;
import jo.joide.deps.cache.DependencyCache;
import jo.joide.deps.repo.Layout;
import jo.joide.deps.repo.Repository;
import jo.joide.ide.langages.MoteursLangage;
import jo.joide.ide.sync.AndroidPlatform;
import jo.joide.ide.sync.ExternalResolution;
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
import jo.joide.model.WorkspaceEdit;

/**
 * L'espace de travail de l'agent SUR LES VRAIES BRIQUES (lot O, le
 * pattern du lot C) : diagnostics et définition par le moteur Java
 * RÉEL (ecj patché ART au classpath de test), exécution bornée par le
 * VRAI BuildRunner (compilation JVM complète), dépendance ajoutée par
 * le VRAI résolveur P4 sur un dépôt local (lockfile écrit, modèle
 * déclaratif réécrit). Ce que les briques ne servent pas reste
 * honnêtement non supporté.
 */
class EspaceTravailIdeTest {

    @TempDir
    Path tmp;

    private Path racine;
    private Path sources;
    private MoteursLangage moteurs;
    private Workspace espace;

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

    /** Un module JVM :app, aucune dépendance externe. */
    private Workspace espace() {
        SourceSet main = new SourceSet("main", List.of(
                new ContentRoot("src/main/java", ContentRole.SOURCE)));
        Module app = new Module(":app", "app", ModuleType.JVM_LIBRARY,
                Map.of("main", main), List.of(), Map.of());
        Map<String, Module> modules = new LinkedHashMap<>();
        modules.put(app.path(), app);
        return new Workspace("projet",
                Map.of("main", new Project("main", modules)), Map.of());
    }

    private MoteursLangage monter() throws IOException {
        LanguageModelSync sync = new LanguageModelSync(racine,
                new ProjetLspAdapter(ModuleDirectories.nested(racine),
                        AndroidPlatform.absent()),
                Resolutions.fixed(
                        new ExternalResolution.Builder().build()));
        return new MoteursLangage(sync,
                Files.createDirectories(tmp.resolve("index")));
    }

    private void ecrireSource(String nom, String contenu)
            throws IOException {
        Files.createDirectories(sources.getParent());
        Files.writeString(sources.resolve(nom), contenu);
    }

    @Test
    void lectureEcritureEtRechercheSurLeVraiDisqueConfinees()
            throws Exception {
        espace = espace();
        EspaceTravailIde espaceAgent =
                new EspaceTravailIde(racine, espace);
        espaceAgent.creerFichier("app/src/main/java/A.java",
                "class A {\n  int x = 1;\n}\n");
        assertEquals("class A {\n  int x = 1;\n}\n",
                espaceAgent.lireFichier(
                        "app/src/main/java/A.java"));
        List<String> racineListee = espaceAgent.listerDossier("");
        assertTrue(racineListee.contains("app/"));
        List<OccurrenceEspace> trouve = espaceAgent.chercherTexte(
                "int x", false, 10);
        assertEquals(1, trouve.size());
        assertEquals("app/src/main/java/A.java", trouve.get(0).fichier);
        assertEquals(2, trouve.get(0).ligne);
        // le confinement tient côté implémentation aussi
        try {
            espaceAgent.lireFichier("../fuite.txt");
            throw new AssertionError("devrait refuser");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("path"));
        }
    }

    @Test
    void lOverlayDeLEditeurPrimeSurLeDisque() throws Exception {
        espace = espace();
        EspaceTravailIde espaceAgent =
                new EspaceTravailIde(racine, espace);
        ecrireSource("A.java", "class A { int version = 1; }");
        espaceAgent.poserOverlayEditeur(
                chemin -> "app/src/main/java/A.java".equals(chemin)
                        ? "class A { int version = 2; }" : null);
        assertEquals("class A { int version = 2; }",
                espaceAgent.lireFichier("app/src/main/java/A.java"),
                "le texte ouvert prime — tampon et disque synchrones");
    }

    @Test
    void diagnosticsDuVraiMoteurJava() throws Exception {
        espace = espace();
        ecrireSource("A.java", """
                class A {
                    void usage() {
                        TypeInconnu v = null;
                    }
                }
                """);
        moteurs = monter();
        moteurs.synchroniser(espace);
        EspaceTravailIde espaceAgent =
                new EspaceTravailIde(racine, espace)
                        .poserMoteurs(moteurs);
        List<DiagnosticEspace> diagnostics = espaceAgent.diagnostics(
                "app/src/main/java/A.java");
        assertFalse(diagnostics.isEmpty(),
                "le type inconnu doit être signalé");
        assertTrue(diagnostics.stream().anyMatch(
                d -> "ERREUR".equals(d.gravite)));
        DiagnosticEspace erreur = diagnostics.stream()
                .filter(d -> "ERREUR".equals(d.gravite)).findFirst()
                .orElseThrow();
        assertEquals("app/src/main/java/A.java", erreur.fichier);
        assertTrue(erreur.ligne >= 1,
                "la conversion décalage vers ligne appartient à "
                        + "l'hôte : " + erreur.ligne);
    }

    @Test
    void definitionDuVraiMoteurJava() throws Exception {
        espace = espace();
        ecrireSource("A.java", """
                class A {
                    B b = new B();
                }
                """);
        ecrireSource("B.java", """
                class B {
                    int y = 0;
                }
                """);
        moteurs = monter();
        moteurs.synchroniser(espace);
        EspaceTravailIde espaceAgent =
                new EspaceTravailIde(racine, espace)
                        .poserMoteurs(moteurs);
        // « new B() » : ligne 2, colonne 17
        Optional<PositionEspace> cible = espaceAgent.definition(
                "app/src/main/java/A.java", 2, 17);
        assertTrue(cible.isPresent(),
                "la définition de B doit être trouvée");
        assertEquals("app/src/main/java/B.java", cible.get().fichier);
        assertEquals(1, cible.get().ligne);
        assertEquals("B", cible.get().nom);
    }

    @Test
    void lesReferencesRestentHonnementNonSupportees() throws Exception {
        espace = espace();
        EspaceTravailIde espaceAgent =
                new EspaceTravailIde(racine, espace);
        try {
            espaceAgent.references("app/src/main/java/A.java", 1, 1);
            throw new AssertionError("devrait refuser");
        } catch (EspaceNonSupporte e) {
            assertTrue(e.getMessage().contains("find references"),
                    "les vides honnêtes du lot D se disent : "
                            + e.getMessage());
        }
    }

    @Test
    void leVraiBuildRunnerCompileDansLaLimite() throws Exception {
        espace = espace();
        ecrireSource("A.java", "class A { int x = 1; }\n");
        Path sorties = Files.createDirectories(tmp.resolve("out"));
        Path m2 = Files.createDirectories(tmp.resolve("m2"));
        EspaceTravailIde espaceAgent =
                new EspaceTravailIde(racine, espace);
        espaceAgent.poserExecuteur((cible, delaiMaxSecondes) -> {
            BuildSpec spec = new BuildSpec(BuildSpec.Kind.JVM,
                    racine.toString(), sorties.toString(), m2.toString(),
                    "17", 2, 512,
                    List.of(new BuildSpec.ModuleSpec("app", false,
                            List.of("app/src/main/java"), List.of(),
                            null, null, List.of(), List.of(), List.of())),
                    List.of());
            long debut = System.currentTimeMillis();
            try (BuildRunner moteur = new BuildRunner(
                    outDir -> jo.joide.store.JournalBuildStore.open(
                            outDir.resolve("journal")), 2)) {
                List<String> journal = new java.util.ArrayList<>();
                BuildRunner.Outcome resultat = moteur.run(spec, null,
                        (nom, statut) -> journal.add(nom + ": "
                                + statut),
                        jo.joide.platform.CancelToken.NEVER);
                String sortie = String.join("\n", journal);
                return new ResultatExecution(resultat.success, 0,
                        System.currentTimeMillis() - debut,
                        sortie + "\n" + resultat.executed
                                + " tasks executed, "
                                + resultat.problems.size()
                                + " problems", false);
            } catch (Exception e) {
                return new ResultatExecution(false, -1,
                        System.currentTimeMillis() - debut,
                        String.valueOf(e.getMessage()), true);
            }
        });
        ResultatExecution resultat = espaceAgent.executer("build", 300);
        assertTrue(resultat.succes,
                "le VRAI moteur compile A.java : " + resultat.sortie);
        assertTrue(resultat.sortie.contains("tasks executed"));
        assertTrue(Files.exists(sorties),
                "les sorties du vrai moteur sont sur le disque");
    }

    @Test
    void leVraiResolveurAjouteLaDependanceAvecLockfileEtModele()
            throws Exception {
        espace = espace();
        ecrireSource("A.java", "class A { int x = 1; }\n");
        // un dépôt local maven avec un POM et un JAR réels
        Path depot = Files.createDirectories(tmp.resolve("depot"));
        String pom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fr.exemple</groupId>
                  <artifactId>outil</artifactId>
                  <version>1.0</version>
                  <packaging>jar</packaging>
                </project>
                """;
        Path cheminPom = depot.resolve(
                Layout.pomPath("fr.exemple", "outil", "1.0"));
        Files.createDirectories(cheminPom.getParent());
        Files.writeString(cheminPom, pom, StandardCharsets.UTF_8);
        Path cheminJar = depot.resolve(Layout.artifactPath(
                "fr.exemple", "outil", "1.0", null, "jar"));
        Files.createDirectories(cheminJar.getParent());
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(cheminJar))) {
            jar.putNextEntry(new JarEntry("fr/exemple/Outil.class"));
            jar.write(new byte[]{(byte) 0xca, (byte) 0xfe,
                    (byte) 0xba, (byte) 0xbe});
            jar.closeEntry();
        }
        DependencyCache cache = new DependencyCache(
                Files.createDirectories(tmp.resolve("m2")));
        Path lockfile = tmp.resolve("joide.lock");
        EspaceTravailIde espaceAgent =
                new EspaceTravailIde(racine, espace);
        Workspace[] modeleCourant = {espace};
        espaceAgent.poserDependances(EspaceTravailIde.resolveurP4(
                List.of(new Repository.Local("local", depot)), cache,
                lockfile));
        espaceAgent.poserEditeurModele(edition -> {
            for (Map.Entry<String, String> fichier
                    : edition.files().entrySet()) {
                try {
                    Files.createDirectories(
                            racine.resolve(fichier.getKey()).getParent());
                    Files.writeString(racine.resolve(fichier.getKey()),
                            fichier.getValue());
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            modeleCourant[0] = edition.workspace();
            return edition.workspace();
        });
        boolean ajoute = espaceAgent.ajouterDependance(":app",
                "fr.exemple:outil:1.0");
        assertTrue(ajoute, "le VRAI résolveur a couvert la demande");
        assertTrue(Files.exists(lockfile),
                "le lockfile joide.lock est écrit");
        Module app = modeleCourant[0].module(":app");
        assertNotNull(app);
        assertTrue(app.dependencies().stream().anyMatch(
                d -> !d.isModule()
                        && "fr.exemple:outil:1.0".equals(
                                d.coordinates())),
                "le modèle déclaratif porte la dépendance");
        // le joide-module.toml réécrit porte la dépendance
        String toml = Files.readString(
                racine.resolve("app/joide-module.toml"),
                StandardCharsets.UTF_8);
        assertTrue(toml.contains("fr.exemple:outil:1.0"));
        // une seconde demande ne refait rien
        assertFalse(espaceAgent.ajouterDependance(":app",
                "fr.exemple:outil:1.0"));
        // et l'espace de travail du test reste lisible via le port
        assertEquals(toml,
                espaceAgent.lireFichier("app/joide-module.toml"));
        // vérification WorkspaceEdit : les fichiers de l'édition
        WorkspaceEdit verification = WorkspaceEdit.withModule(
                modeleCourant[0], app);
        assertTrue(verification.files()
                .containsKey("app/joide-module.toml"));
    }

    @Test
    void laMemoireDuProjetVitDansAGENSMd() throws Exception {
        espace = espace();
        EspaceTravailIde espaceAgent =
                new EspaceTravailIde(racine, espace);
        assertEquals("", espaceAgent.lireMemoire(),
                "absente, la mémoire est VIDE, pas une erreur");
        espaceAgent.ecrireMemoire("# Mémoire\n- règle");
        assertEquals("# Mémoire\n- règle", espaceAgent.lireMemoire());
        assertEquals("# Mémoire\n- règle", Files.readString(
                racine.resolve("AGENTS.md"), StandardCharsets.UTF_8));
    }

    @Test
    void lapercuDuProjetNommeLesModulesReels() throws Exception {
        espace = espace();
        String apercu = new EspaceTravailIde(racine, espace)
                .apercuProjet();
        assertTrue(apercu.contains("modules: 1"));
        assertTrue(apercu.contains(":app"));
        assertTrue(apercu.contains("jvm-library"),
                "le type se dit par son identifiant : " + apercu);
    }
}
