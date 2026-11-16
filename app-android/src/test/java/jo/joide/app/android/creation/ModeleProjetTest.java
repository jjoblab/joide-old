package jo.joide.app.android.creation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.ui.choix.ChoixCarte;

/**
 * Le format déclaratif des modèles (ADR-050) : l'analyseur accepte
 * les quatre modèles livrés (assets/modeles — les VRAIS fichiers,
 * jamais des copies de test), refuse les documents malformés avec
 * des problèmes nommés, et les invariants tiennent (franchise ou
 * note, fichier d'entrée, modules sans doublon).
 */
class ModeleProjetTest {

    @Test
    void lesQuatreModelesLivreSSontExploitables() throws IOException {
        Map<String, ModeleProjet.Modele> modeles = modelesAssets();
        assertEquals(4, modeles.size(),
                "quatre modèles v2 (ADR-É4) : " + modeles.keySet());
        ModeleProjet.Modele android = modeles.get("application-android");
        assertNotNull(android);
        assertEquals("Application Android", android.nom());
        assertEquals(ModeleProjet.Categorie.APPLICATIONS,
                android.categorie());
        assertEquals(ChoixCarte.TypeTuile.ANDROID, android.tuile());
        assertTrue(android.franchise() != null
                        && android.franchise().contains("appareil"),
                "la franchise de la carte Android nomme la limite");
        assertEquals(2, android.modules().size());
        assertEquals("app", android.modules().get(0).id());
        assertEquals("android-application",
                android.modules().get(0).type());
        assertTrue(android.modules().get(0).requiert()
                .contains("core"), ":app requiert :core");
        assertEquals("console", modeles.get("console-jvm")
                .modules().get(0).id());
        assertNotNull(modeles.get("console-jvm").modules().get(0)
                .classePrincipale(), "la console porte son main");
        assertEquals("jo.exemple.console.MainKt",
                modeles.get("console-jvm").modules().get(0)
                        .classePrincipale());
        assertEquals(1, modeles.get("bibliotheque-jvm").dependances()
                .size(), "la bibliothèque porte JUnit 5");
        assertEquals("org.junit.jupiter:junit-jupiter:5.10.2",
                modeles.get("bibliotheque-jvm").dependances().get(0)
                        .coordonnees());
        assertTrue(modeles.get("vide").modules().isEmpty(),
                "le modèle vide n'a aucun module");
        assertTrue(modeles.get("vide").fichiers().isEmpty());
    }

    @Test
    void franchiseOuNoteJamaisUnePromesseMuette() throws IOException {
        for (ModeleProjet.Modele modele : modelesAssets().values()) {
            assertTrue(modele.franchise() != null
                            || modele.note() != null,
                    modele.id() + " porte sa franchise ou sa note");
        }
        // les consoles et bibliothèques portent la note grise
        // « constructible sur l'appareil », l'Android porte l'alerte
        assertTrue(modelesAssets().get("console-jvm").note() != null);
        assertTrue(modelesAssets().get("bibliotheque-jvm").note() != null);
        assertTrue(modelesAssets().get("vide").note() != null);
        assertTrue(modelesAssets().get("application-android")
                .franchise() != null);
    }

    @Test
    void fichierDEntreePresentPourLesModelesAvecContenu()
            throws IOException {
        Map<String, ModeleProjet.Modele> modeles = modelesAssets();
        assertTrue(modeles.get("application-android").fichierEntree()
                .contains("{paquet-chemin}"));
        assertTrue(modeles.get("console-jvm").fichierEntree()
                .endsWith("Main.kt"));
        assertTrue(modeles.get("bibliotheque-jvm").fichierEntree()
                .endsWith("Socle.kt"));
    }

    @Test
    void schemaInconnuRefuse() {
        ModeleProjet.Analyse analyse = ModeleProjet.analyser(
                "schema = \"autre\"\nid = \"x\"");
        assertFalse(analyse.exploitable());
        assertFalse(analyse.problemes().isEmpty());
    }

    @Test
    void documentVideOuCorrompuRefuseAvecProblemes() {
        assertFalse(ModeleProjet.analyser("").exploitable());
        assertFalse(ModeleProjet.analyser(null).exploitable());
        ModeleProjet.Analyse analyse = ModeleProjet.analyser(
                "schema = \"joide.template/1\"\nid = \"x\"\n"
                        + "nom = \"X\"\ndescription = \"d\"\n"
                        + "categorie = \"applications\"\ntuile = \"android\"\n"
                        + "[[modules]]\n");
        assertFalse(analyse.exploitable(),
                "sans franchise ni note ni module complet : refusé");
        assertTrue(analyse.problemes().stream().anyMatch(
                p -> p.contains("franchise")));
    }

    @Test
    void clesInconnuesSignalees() {
        ModeleProjet.Analyse analyse = ModeleProjet.analyser(
                "schema = \"joide.template/1\"\nid = \"x\"\n"
                        + "nom = \"X\"\ndescription = \"d\"\n"
                        + "categorie = \"applications\"\ntuile = \"android\"\n"
                        + "franchise = \"f\"\nscript = \"rm -rf\"\n"
                        + "[[modules]]\nid = \"app\"\n"
                        + "type = \"android-application\"\n");
        assertFalse(analyse.exploitable());
        assertTrue(analyse.problemes().stream().anyMatch(
                p -> p.contains("cle inconnue")));
    }

    @Test
    void moduleEnDoubleSignale() {
        ModeleProjet.Analyse analyse = ModeleProjet.analyser(
                "schema = \"joide.template/1\"\nid = \"x\"\n"
                        + "nom = \"X\"\ndescription = \"d\"\n"
                        + "categorie = \"bibliotheques\"\ntuile = \"console\"\n"
                        + "note = \"n\"\n"
                        + "[[modules]]\nid = \"a\"\n"
                        + "type = \"jvm-library\"\n"
                        + "[[modules]]\nid = \"a\"\n"
                        + "type = \"jvm-library\"\n");
        assertFalse(analyse.exploitable());
        assertTrue(analyse.problemes().stream().anyMatch(
                p -> p.contains("doublon")));
    }

    /** Les modèles livrés, lus depuis les assets du dépôt. */
    static Map<String, ModeleProjet.Modele> modelesAssets()
            throws IOException {
        Path dossier = dossierModeles();
        Map<String, ModeleProjet.Modele> modeles = new java.util.TreeMap<>();
        try (var parcours = Files.list(dossier)) {
            for (Path fichier : parcours.sorted().toList()) {
                if (!fichier.getFileName().toString().endsWith(".toml")) {
                    continue;
                }
                ModeleProjet.Analyse analyse = ModeleProjet.analyser(
                        Files.readString(fichier, StandardCharsets.UTF_8));
                assertTrue(analyse.exploitable(),
                        fichier + " : " + analyse.problemes());
                assertEquals(0, analyse.problemes().size(),
                        fichier + " : " + analyse.problemes());
                modeles.put(analyse.modele().id(), analyse.modele());
            }
        }
        return modeles;
    }

    static Path dossierModeles() throws IOException {
        Path courant = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && courant != null; i++) {
            Path candidate = courant.resolve(
                    "app-android/src/main/assets/modeles");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            courant = courant.getParent();
        }
        throw new IOException("assets/modeles not found from "
                + Paths.get("").toAbsolutePath());
    }

    static List<ModeleProjet.Modele> listeModeles() throws IOException {
        return List.copyOf(modelesAssets().values());
    }
}
