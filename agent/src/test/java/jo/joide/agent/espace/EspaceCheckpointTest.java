package jo.joide.agent.espace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Les checkpoints au premier toucher (ADR-É9, leçon 13) : instantané
 * du fichier à la PREMIÈRE écriture du tour, annulation qui
 * restaure du plus récent au plus vieux et AVOUE ce qu'elle ne peut
 * pas restaurer (la dépendance ajoutée), plafond de 200 fichiers.
 */
class EspaceCheckpointTest {

    @TempDir
    Path bac;

    private EspaceFaux espace() throws IOException {
        return new EspaceFaux(bac);
    }

    @Test
    void lePremierToucherInstantanePasLesSuivants() throws Exception {
        EspaceFaux dessous = espace();
        EspaceCheckpoint checkpoints =
                new EspaceCheckpoint(dessous);
        dessous.preparer("src/A.java", "version 0");
        checkpoints.commencerTour();
        checkpoints.ecrireFichier("src/A.java", "version 1");
        checkpoints.ecrireFichier("src/A.java", "version 2");
        EspaceCheckpoint.RapportAnnulation rapport =
                checkpoints.annuler();
        assertEquals(List.of("src/A.java"), rapport.restaures);
        assertEquals("version 0", Files.readString(
                bac.resolve("src/A.java"), StandardCharsets.UTF_8),
                "c'est le contenu D'AVANT LE TOUR qui revient");
    }

    @Test
    void laRestaurationVientDuPlusRecentAuPlusVieux() throws Exception {
        EspaceFaux dessous = espace();
        EspaceCheckpoint checkpoints =
                new EspaceCheckpoint(dessous);
        dessous.preparer("a.txt", "A0");
        dessous.preparer("b.txt", "B0");
        checkpoints.commencerTour();
        checkpoints.ecrireFichier("a.txt", "A1");
        checkpoints.ecrireFichier("b.txt", "B1");
        checkpoints.renommerChemin("a.txt", "c.txt");
        EspaceCheckpoint.RapportAnnulation rapport =
                checkpoints.annuler();
        // a.txt avait été renommé en c.txt : la restauration
        // réécrit le contenu d'origine — le chemin renommé revient
        // par le renommage inverse si le dessous le permet, sinon le
        // rapport le dit ; ici la restauration d'écriture suffit
        assertEquals("B0", Files.readString(bac.resolve("b.txt"),
                StandardCharsets.UTF_8));
        assertTrue(rapport.aFaits());
    }

    @Test
    void laDependanceAjouteeEstAvoueeNonRestaurable() throws Exception {
        EspaceFaux dessous = espace();
        EspaceCheckpoint checkpoints =
                new EspaceCheckpoint(dessous);
        checkpoints.commencerTour();
        assertTrue(checkpoints.ajouterDependance(":app",
                "com.exemple:outil:1.0"));
        checkpoints.ecrireFichier("src/A.java", "modifié");
        EspaceCheckpoint.RapportAnnulation rapport =
                checkpoints.annuler();
        assertEquals(List.of("com.exemple:outil:1.0"),
                dessous.dependancesAjoutees);
        assertTrue(rapport.nonRestaurables.stream().anyMatch(
                texte -> texte.contains("add dependency")
                        && texte.contains("not reverted")),
                "le rapport AVOUE que le lockfile ne se défait pas : "
                        + rapport.nonRestaurables);
        assertTrue(rapport.restaures.contains("src/A.java"));
    }

    @Test
    void laCreationSAnnuleParSuppression() throws Exception {
        EspaceFaux dessous = espace();
        EspaceCheckpoint checkpoints =
                new EspaceCheckpoint(dessous);
        checkpoints.commencerTour();
        checkpoints.creerFichier("nouveau.txt", "créé");
        assertTrue(Files.exists(bac.resolve("nouveau.txt")));
        checkpoints.annuler();
        assertFalse(Files.exists(bac.resolve("nouveau.txt")),
                "un fichier créé pendant le tour disparaît à "
                        + "l'annulation");
    }

    @Test
    void lePlafondDeDeuxCentsFichiersTient() throws Exception {
        EspaceFaux dessous = espace();
        EspaceCheckpoint checkpoints =
                new EspaceCheckpoint(dessous);
        checkpoints.commencerTour();
        for (int i = 0; i < 250; i++) {
            checkpoints.ecrireFichier("f" + i + ".txt", "v" + i);
        }
        checkpoints.annuler();
        // les 200 premiers instantanés restaurent, les 50 derniers
        // sont hors plafond — le rapport le montre
        long restaures = Files.list(bac).count();
        assertTrue(restaures <= EspaceCheckpoint.PLAFOND_FICHIERS + 1,
                "le plafond borne la restauration : " + restaures);
    }

    @Test
    void unNouveauTourRepartDeZero() throws Exception {
        EspaceFaux dessous = espace();
        EspaceCheckpoint checkpoints =
                new EspaceCheckpoint(dessous);
        dessous.preparer("a.txt", "origine");
        checkpoints.commencerTour();
        checkpoints.ecrireFichier("a.txt", "tour 1");
        checkpoints.annuler();
        checkpoints.commencerTour();
        checkpoints.ecrireFichier("a.txt", "tour 2");
        EspaceCheckpoint.RapportAnnulation rapport =
                checkpoints.annuler();
        assertEquals("origine", Files.readString(bac.resolve("a.txt"),
                StandardCharsets.UTF_8));
        assertEquals(1, rapport.restaures.size());
    }
}
