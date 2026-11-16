package jo.joide.app.android.creation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * L'écriture atomique (ADR-É4 : tout ou rien) : l'arborescence
 * complète atterrit d'un seul déplacement ; l'échec au milieu ne
 * laisse RIEN (ni final, ni temporaire) ; le dossier existant est
 * refusé ; le chemin qui sort du projet est refusé (zip-slip).
 */
class EcrireProjetTest {

    private static List<GenerateurProjet.FichierGenere> arbreSimple() {
        return List.of(
                new GenerateurProjet.FichierGenere("joide.toml",
                        "schema = \"x\"\n"),
                new GenerateurProjet.FichierGenere(
                        "app/src/main/Kotlin.k", "fun main() {}\n"),
                new GenerateurProjet.FichierGenere(
                        "app/src/main/res/layout/main.xml", "<layout/>\n"));
    }

    @Test
    void ecritureCompletePuisDeplacementAtomique(@TempDir Path dossier)
            throws IOException {
        EcrireProjet.Resultat resultat = EcrireProjet.ecrire(dossier,
                "essai", arbreSimple());
        assertTrue(resultat.ecrit(), resultat.erreur());
        assertEquals(dossier.resolve("essai"), resultat.chemin());
        assertTrue(Files.isRegularFile(
                resultat.chemin().resolve("joide.toml")));
        assertTrue(Files.isRegularFile(resultat.chemin().resolve(
                "app/src/main/res/layout/main.xml")));
        // aucun temporaire résiduel
        try (var contenu = Files.list(dossier)) {
            assertTrue(contenu.allMatch(p -> !p.getFileName().toString()
                    .startsWith(".tmp-")));
        }
    }

    @Test
    void dossierExistantRefuse(@TempDir Path dossier) throws IOException {
        Files.createDirectories(dossier.resolve("essai"));
        EcrireProjet.Resultat resultat = EcrireProjet.ecrire(dossier,
                "essai", arbreSimple());
        assertFalse(resultat.ecrit());
        assertTrue(resultat.erreur().contains("existe deja"));
        // le dossier existant n'est pas touché
        assertTrue(Files.isDirectory(dossier.resolve("essai")));
    }

    @Test
    void cheminQuiSortDuProjetRefuseRienNEcrit(@TempDir Path dossier) {
        List<GenerateurProjet.FichierGenere> piege = List.of(
                new GenerateurProjet.FichierGenere("joide.toml", "x\n"),
                new GenerateurProjet.FichierGenere("../ailleurs.txt",
                        "piege\n"));
        EcrireProjet.Resultat resultat = EcrireProjet.ecrire(dossier,
                "piege", piege);
        assertFalse(resultat.ecrit());
        assertTrue(resultat.erreur().contains("hors du projet"));
        assertFalse(Files.exists(dossier.resolve("piege")));
        assertFalse(Files.exists(dossier.resolve("../ailleurs.txt")));
        assertFalse(Files.exists(dossier.resolve("ailleurs.txt")));
    }

    @Test
    void fichierNullOuContenuNullRefuseTout(@TempDir Path dossier) {
        List<GenerateurProjet.FichierGenere> casse = List.of(
                new GenerateurProjet.FichierGenere("a.txt", "a\n"));
        // un chemin non résolvable échoue proprement : rien n'écrit
        List<GenerateurProjet.FichierGenere> cheminVide = List.of(
                new GenerateurProjet.FichierGenere("", "a\n"));
        EcrireProjet.Resultat resultat = EcrireProjet.ecrire(dossier,
                "casse", cheminVide);
        assertFalse(resultat.ecrit());
        assertTrue(Files.notExists(dossier.resolve("casse")));
        // l'arbre sain reste utilisable après l'échec
        assertTrue(EcrireProjet.ecrire(dossier, "casse", casse).ecrit());
    }

    @Test
    void deuxEcrituresDUneMemeGenerationSontIdentiques(
            @TempDir Path dossier) throws IOException {
        GenerateurProjet.Generation generation;
        try {
            generation = GenerateurProjet.generer(
                    ModeleProjetTest.modelesAssets().get("console-jvm"),
                    new GenerateurProjet.Identite("console",
                            List.of("console"), null, 28));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        assertTrue(generation.exploitable());
        EcrireProjet.Resultat un = EcrireProjet.ecrire(dossier, "un",
                generation.fichiers());
        EcrireProjet.Resultat deux = EcrireProjet.ecrire(dossier, "deux",
                generation.fichiers());
        assertTrue(un.ecrit());
        assertTrue(deux.ecrit());
        for (GenerateurProjet.FichierGenere fichier :
                generation.fichiers()) {
            assertEquals(
                    Files.readString(un.chemin().resolve(fichier.chemin())),
                    Files.readString(deux.chemin().resolve(
                            fichier.chemin())),
                    fichier.chemin() + " : déterminisme du générateur");
        }
    }
}
