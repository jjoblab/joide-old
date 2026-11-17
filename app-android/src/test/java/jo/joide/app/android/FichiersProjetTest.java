package jo.joide.app.android;

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
 * Fichiers ouvrables du projet : parcours réel d'un répertoire
 * importé, filtre par extension, exclusions des sorties et caches,
 * plafond — et la détection des mises en page Android.
 */
class FichiersProjetTest {

    @TempDir
    Path temp;

    private void touch(String relatif) throws IOException {
        Path cible = temp.resolve(relatif);
        Files.createDirectories(cible.getParent());
        Files.writeString(cible, "x");
    }

    @Test
    void parcoursFiltreEtExclusions() throws Exception {
        touch("joide.toml");
        touch("app/src/main/java/demo/App.java");
        touch("app/src/main/res/layout/activity_main.xml");
        touch("app/src/main/res/values/themes.xml");
        touch("notes.md");
        touch("binaire.png");
        touch("sansextension");
        touch("build/intermediate/Gen.java");
        touch(".git/config");
        touch("build-joide/jvm/app/libs/app.jar");

        List<String> fichiers = FichiersProjet.lister(temp);

        assertTrue(fichiers.contains("joide.toml"));
        assertTrue(fichiers.contains(
                "app/src/main/java/demo/App.java"));
        assertTrue(fichiers.contains(
                "app/src/main/res/layout/activity_main.xml"));
        assertTrue(fichiers.contains("app/src/main/res/values/themes.xml"));
        assertTrue(fichiers.contains("notes.md"));
        assertFalse(fichiers.contains("binaire.png"));
        assertFalse(fichiers.contains("sansextension"));
        assertFalse(fichiers.contains("build/intermediate/Gen.java"));
        assertFalse(fichiers.contains(".git/config"));
        assertFalse(fichiers.contains("build-joide/jvm/app/libs/app.jar"));
        assertEquals(5, fichiers.size(), () -> fichiers.toString());
    }

    @Test
    void misesEnPageSeulesCandidatesALapercu() {
        assertTrue(FichiersProjet.estMiseEnPage(
                "app/src/main/res/layout/activity_main.xml"));
        assertTrue(FichiersProjet.estMiseEnPage(
                "res/layout-v21/autre.xml"));
        assertFalse(FichiersProjet.estMiseEnPage(
                "app/src/main/res/values/themes.xml"));
        assertFalse(FichiersProjet.estMiseEnPage(
                "app/src/main/java/demo/App.java"));
    }

    @Test
    void lectureEtOuvrables() throws Exception {
        touch("lire.txt");
        String contenu = FichiersProjet.lire(temp, "lire.txt");
        assertEquals("x", contenu);

        assertTrue(FichiersProjet.ouvrable("Main.java"));
        assertTrue(FichiersProjet.ouvrable("build.gradle.kts"));
        assertTrue(FichiersProjet.ouvrable("Layout.XML"));
        assertFalse(FichiersProjet.ouvrable("image.png"));
        assertFalse(FichiersProjet.ouvrable("sanspoint"));
        assertFalse(FichiersProjet.ouvrable("finpoint."));
    }
}
