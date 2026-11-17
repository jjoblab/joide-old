package jo.joide.app.android.creation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.accueil.TypeProjet;
import jo.joide.model.WorkspaceLoad;
import jo.joide.model.WorkspaceLoader;

/**
 * Le générateur (ADR-É4) : modèle + identité → arborescence complète.
 * Les valeurs par défaut du modèle, la dérivation du paquet, les
 * substitutions, le retrait des fichiers de modules non conservés,
 * et le critère absolu : le projet généré SE RELIT tel quel par le
 * lecteur de workspace (l'aller-retour génération → disque → modèle
 * ne ment jamais).
 */
class GenerateurProjetTest {

    private static ModeleProjet.Modele android() throws IOException {
        return ModeleProjetTest.modelesAssets().get("application-android");
    }

    private static ModeleProjet.Modele console() throws IOException {
        return ModeleProjetTest.modelesAssets().get("console-jvm");
    }

    private static ModeleProjet.Modele bibliotheque() throws IOException {
        return ModeleProjetTest.modelesAssets().get("bibliotheque-jvm");
    }

    private static ModeleProjet.Modele vide() throws IOException {
        return ModeleProjetTest.modelesAssets().get("vide");
    }

    @Test
    void androidGenereUnWorkspaceRelisible() throws IOException {
        GenerateurProjet.Generation generation = GenerateurProjet.generer(
                android(), new GenerateurProjet.Identite(
                        "mon-application", List.of("app", "core"),
                        "jo.exemple.monapplication", 28));
        assertTrue(generation.exploitable(),
                String.join(", ", generation.problemes()));
        assertEquals(TypeProjet.ANDROID, generation.type());
        assertEquals(2, generation.nbModules());
        Path racine = ecrire(generation);
        WorkspaceLoad charge = WorkspaceLoader.load(racine);
        assertTrue(charge.isUsable(),
                () -> charge.problems().toString());
        assertTrue(charge.errors().isEmpty(),
                () -> charge.problems().toString());
        assertEquals(2, charge.workspace().orElseThrow()
                .allModules().size());
        assertTrue(charge.workspace().orElseThrow()
                .setting("android.min-sdk").isPresent());
        assertEquals("28", charge.workspace().orElseThrow()
                .setting("android.min-sdk").orElseThrow());
    }

    @Test
    void substitutionsPortentIdentiteEtPaquet() throws IOException {
        GenerateurProjet.Generation generation = GenerateurProjet.generer(
                android(), new GenerateurProjet.Identite(
                        "projet-essai", List.of("app", "core"),
                        "com.exemple.essai", 30));
        assertTrue(generation.exploitable(),
                String.join(", ", generation.problemes()));
        String manifeste = null;
        String activite = null;
        for (GenerateurProjet.FichierGenere fichier :
                generation.fichiers()) {
            if (fichier.chemin().endsWith("AndroidManifest.xml")) {
                manifeste = fichier.contenu();
            }
            if (fichier.chemin().endsWith("MainActivity.kt")) {
                activite = fichier.contenu();
            }
        }
        assertNotNull(manifeste);
        assertNotNull(activite);
        assertTrue(activite.contains("package com.exemple.essai"));
        assertTrue(activite.contains("setContentView(R.layout.activity_main)"));
        assertEquals("app/src/main/java/com/exemple/essai/MainActivity.kt",
                generation.fichierEntree(),
                "le fichier d'entrée suit le paquet");
        Path racine = ecrire(generation);
        assertTrue(Files.isRegularFile(racine.resolve(
                "app/src/main/java/com/exemple/essai/MainActivity.kt")));
        String joide = Files.readString(racine.resolve("joide.toml"));
        assertTrue(joide.contains("min-sdk = 30"));
    }

    @Test
    void retraitDUnModuleRetireSesFichiersEtSesDependances()
            throws IOException {
        // l'Android réduit à :core : les fichiers de :app ne sont
        // pas écrits, joide.toml n'inclut que core, plus de min-sdk
        GenerateurProjet.Generation reduction = GenerateurProjet.generer(
                android(), new GenerateurProjet.Identite("essai",
                        List.of("core"), null, 28));
        assertTrue(reduction.exploitable(),
                String.join(", ", reduction.problemes()));
        assertTrue(reduction.fichiers().stream().noneMatch(
                f -> f.chemin().startsWith("app/")),
                "les fichiers de :app suivent :app");
        Path racineReduite = ecrire(reduction);
        String joideReduit = Files.readString(
                racineReduite.resolve("joide.toml"));
        assertTrue(joideReduit.contains("\"core\""));
        assertFalse(joideReduit.contains("\"app\""));
        assertFalse(joideReduit.contains("min-sdk"),
                "sans module Android, pas de réglage min-sdk");
        WorkspaceLoad chargeReduit = WorkspaceLoader.load(racineReduite);
        assertTrue(chargeReduit.isUsable());
        assertEquals(1, chargeReduit.workspace().orElseThrow()
                .allModules().size());
        // la bibliothèque complète garde sa dépendance de test
        GenerateurProjet.Generation complet = GenerateurProjet.generer(
                bibliotheque(), new GenerateurProjet.Identite("socle",
                        List.of("socle"), null, 28));
        Path racine = ecrire(complet);
        String module = Files.readString(racine.resolve(
                "socle/joide-module.toml"));
        assertTrue(module.contains("junit-jupiter"));
        assertTrue(module.contains("test_implementation"));
        assertTrue(Files.isRegularFile(racine.resolve(
                "socle/src/test/kotlin/jo/exemple/socle/SocleTest.kt")));
    }

    @Test
    void consolePorteLaFacetteApplicationEtSeRelit() throws IOException {
        GenerateurProjet.Generation generation = GenerateurProjet.generer(
                console(), new GenerateurProjet.Identite("protocole",
                        List.of("console"), null, 28));
        assertTrue(generation.exploitable());
        Path racine = ecrire(generation);
        WorkspaceLoad charge = WorkspaceLoader.load(racine);
        assertTrue(charge.isUsable());
        assertEquals(1, charge.workspace().orElseThrow()
                .allModules().size());
        String module = Files.readString(racine.resolve(
                "console/joide-module.toml"));
        assertTrue(module.contains("[facets.application]"),
                "la facette application est écrite");
        assertTrue(module.contains("main-class"));
    }

    @Test
    void videGenereLeSeulJoideToml() throws IOException {
        GenerateurProjet.Generation generation = GenerateurProjet.generer(
                vide(), new GenerateurProjet.Identite("depart",
                        List.of(), null, 28));
        assertTrue(generation.exploitable(),
                String.join(", ", generation.problemes()));
        assertEquals(1, generation.fichiers().size());
        assertEquals("joide.toml", generation.fichiers().get(0).chemin());
        assertEquals(TypeProjet.VIDE, generation.type());
        assertNull(generation.fichierEntree());
        Path racine = ecrire(generation);
        WorkspaceLoad charge = WorkspaceLoader.load(racine);
        // l'avertissement « aucun module declare » est honnête pour
        // un départ vide — pas une erreur
        assertTrue(charge.problems().stream().anyMatch(
                p -> p.code().equals("workspace-empty")));
        assertTrue(charge.isUsable());
    }

    @Test
    void validationsRefusentLIdentiteInvalide() throws IOException {
        // nom vide
        assertFalse(GenerateurProjet.generer(android(),
                new GenerateurProjet.Identite("", List.of("app", "core"),
                        "jo.exemple.a", 28)).exploitable());
        // nom trop long
        assertFalse(GenerateurProjet.generer(android(),
                new GenerateurProjet.Identite("x".repeat(65),
                        List.of("app", "core"), "jo.exemple.a", 28))
                .exploitable());
        // paquet invalide (une seule segment, majuscule)
        assertFalse(GenerateurProjet.generer(android(),
                new GenerateurProjet.Identite("a", List.of("app", "core"),
                        "Majuscule", 28)).exploitable());
        // minSdk hors bornes (24 n'existe plus : ADR-009, Java 17)
        assertFalse(GenerateurProjet.generer(android(),
                new GenerateurProjet.Identite("a", List.of("app", "core"),
                        "jo.exemple.a", 24)).exploitable());
        // module requis retiré : :app sans :core est refusé
        GenerateurProjet.Generation generation = GenerateurProjet.generer(
                android(), new GenerateurProjet.Identite("a",
                        List.of("app"), "jo.exemple.a", 28));
        assertFalse(generation.exploitable());
        assertTrue(generation.problemes().stream().anyMatch(
                p -> p.contains("requiert")));
        // sans module alors que le modèle en propose
        assertFalse(GenerateurProjet.generer(android(),
                new GenerateurProjet.Identite("a", List.of(),
                        "jo.exemple.a", 28)).exploitable());
    }

    @Test
    void paquetDefautEtValidite() {
        assertEquals("jo.exemple.monapplication",
                GenerateurProjet.paquetDefaut("mon-application"));
        assertEquals("jo.exemple.projet",
                GenerateurProjet.paquetDefaut("??"));
        assertTrue(GenerateurProjet.paquetValide("jo.exemple.a"));
        assertTrue(GenerateurProjet.paquetValide("a.b.c2"));
        assertFalse(GenerateurProjet.paquetValide("Jo.exemple"));
        assertFalse(GenerateurProjet.paquetValide("seul"));
        assertFalse(GenerateurProjet.paquetValide("jo..exemple"));
        assertFalse(GenerateurProjet.paquetValide(null));
    }

    @Test
    void cheminHorsProjetRefuseAvantTouteEcriture() throws IOException {
        ModeleProjet.Analyse analyse = ModeleProjet.analyser(
                "schema = \"joide.template/1\"\nid = \"piege\"\n"
                        + "nom = \"P\"\ndescription = \"d\"\n"
                        + "categorie = \"bibliotheques\"\ntuile = \"console\"\n"
                        + "note = \"n\"\n"
                        + "[[modules]]\nid = \"a\"\n"
                        + "type = \"jvm-library\"\n"
                        + "[[fichiers]]\nchemin = \"../../ailleurs.txt\"\n"
                        + "lignes = ['x']\n");
        assertTrue(analyse.exploitable());
        GenerateurProjet.Generation generation = GenerateurProjet.generer(
                analyse.modele(), new GenerateurProjet.Identite("piege",
                        List.of("a"), null, 28));
        assertTrue(generation.exploitable());
        // l'écriture refuse le chemin qui sort du projet (zip-slip,
        // la leçon ADR-032)
        Path dossier = Files.createTempDirectory("joide-piege");
        EcrireProjet.Resultat resultat = EcrireProjet.ecrire(dossier,
                "piege", generation.fichiers());
        assertFalse(resultat.ecrit());
        assertTrue(resultat.erreur().contains("hors du projet"));
        assertFalse(Files.exists(dossier.resolve(
                "ailleurs.txt")));
    }

    @Test
    void lesRacinesDeclareesSansFichierExistentDesLaCreation()
            throws IOException {
        // rapport du 2026-11-17 : MyApp/core déclarait
        // src/main/kotlin, aucun fichier n'y vivait, le dossier
        // n'existait pas — le premier build mourrait sur « cannot
        // scan input directory ». La génération porte désormais SES
        // racines déclarées et l'écriture les crée, fichiers ou pas.
        GenerateurProjet.Generation generation = GenerateurProjet.generer(
                android(), new GenerateurProjet.Identite("MyApp",
                        List.of("app", "core"), "jo.exemple.myapp",
                        28));
        assertTrue(generation.exploitable(),
                String.join(", ", generation.problemes()));
        assertTrue(generation.dossiers().contains(
                "core/src/main/kotlin"),
                () -> generation.dossiers().toString());
        assertTrue(generation.dossiers().contains(
                "app/src/main/kotlin"),
                () -> generation.dossiers().toString());
        Path racine = ecrire(generation);
        assertTrue(Files.isDirectory(
                racine.resolve("core/src/main/kotlin")),
                "la racine déclarée sans fichier EXISTE après écriture");
        assertTrue(Files.isDirectory(
                racine.resolve("app/src/main/kotlin")));
        // un module écarté n'exige rien : ses racines ne suivent pas
        GenerateurProjet.Generation reduction = GenerateurProjet.generer(
                android(), new GenerateurProjet.Identite("Essai",
                        List.of("core"), null, 28));
        assertTrue(reduction.exploitable());
        assertTrue(reduction.dossiers().stream().noneMatch(
                d -> d.startsWith("app/")));
    }

    /** Écrit une génération en répertoire temporaire et le rend. */
    private static Path ecrire(GenerateurProjet.Generation generation)
            throws IOException {
        Path dossier = Files.createTempDirectory("joide-generation");
        EcrireProjet.Resultat resultat = EcrireProjet.ecrire(dossier,
                "genere", generation.fichiers(),
                generation.dossiers());
        assertTrue(resultat.ecrit(), resultat.erreur());
        return resultat.chemin();
    }
}
