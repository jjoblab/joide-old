package jo.joide.app.android.accueil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Projets récents : l'aller-retour sérialisation, la tolérance aux
 * documents corrompus (une entrée invalide est signalée et sautée,
 * jamais réparée en silence — la règle du dépôt), l'écriture
 * atomique sur disque, les opérations de liste (ouverture, renommage,
 * retrait) et l'unicité des identifiants.
 */
class ProjetsRecentsTest {

    private static final long BASE = 1_700_000_000_000L;

    @Test
    void allerRetourSerialisation() {
        List<ProjetsRecents.Entree> entrees = List.of(
                new ProjetsRecents.Entree("mon-application",
                        "mon-application", TypeProjet.ANDROID,
                        "mon-application", BASE, 3),
                new ProjetsRecents.Entree("protocole-p8", "protocole-p8",
                        TypeProjet.CONSOLE, "protocole-p8", BASE - 3_600_000L,
                        1));
        String json = ProjetsRecents.serialiser(entrees);
        ProjetsRecents.Chargement lu = ProjetsRecents.analyser(json);
        assertTrue(lu.problemes().isEmpty(), lu.problemes().toString());
        assertEquals(entrees, lu.entrees());
    }

    @Test
    void nomAvecQuotesEtRetoursLigneSurvit() {
        ProjetsRecents.Entree piege = new ProjetsRecents.Entree(
                "a\"b\\c", "nom \"piège\"\nligne", TypeProjet.VIDE,
                "a\"b\\c", BASE, 0);
        ProjetsRecents.Chargement lu = ProjetsRecents.analyser(
                ProjetsRecents.serialiser(List.of(piege)));
        assertTrue(lu.problemes().isEmpty());
        assertEquals("nom \"piège\"\nligne",
                lu.entrees().get(0).nomAffiche());
    }

    @Test
    void fichierAbsentPremiereOuvertureSansProbleme() {
        ProjetsRecents.Chargement lu = ProjetsRecents.lire(
                Path.of("/introuvable", "projets-recents.json"));
        assertTrue(lu.entrees().isEmpty());
        assertTrue(lu.problemes().isEmpty());
    }

    @Test
    void entreeInvalideSignaleeEtSauteeJamaisReparee() {
        String json = "{\"schema\":\"joide.recents/1\",\"projets\":["
                + "{\"id\":\"ok\",\"nom\":\"ok\",\"type\":\"console\","
                + "\"dossier\":\"ok\",\"ouvert\":17,\"modules\":1},"
                + "{\"nom\":\"sans id\",\"type\":\"android\","
                + "\"dossier\":\"x\",\"ouvert\":18,\"modules\":2}]}";
        ProjetsRecents.Chargement lu = ProjetsRecents.analyser(json);
        assertEquals(1, lu.entrees().size());
        assertEquals("ok", lu.entrees().get(0).id());
        assertEquals(1, lu.problemes().size());
        assertTrue(lu.problemes().get(0).contains("sautee")
                || lu.problemes().get(0).contains("sautée"));
    }

    @Test
    void schemaInconnuRefuseAvecProbleme() {
        ProjetsRecents.Chargement lu = ProjetsRecents.analyser(
                "{\"schema\":\"autre\",\"projets\":[]}");
        assertTrue(lu.entrees().isEmpty());
        assertEquals(1, lu.problemes().size());
    }

    @Test
    void typeInconnuRetombeSurVideSansCasser() {
        String json = "{\"schema\":\"joide.recents/1\",\"projets\":["
                + "{\"id\":\"x\",\"nom\":\"x\",\"type\":\"exotique\","
                + "\"dossier\":\"x\",\"ouvert\":5,\"modules\":0}]}";
        assertEquals(TypeProjet.VIDE,
                ProjetsRecents.analyser(json).entrees().get(0).type());
    }

    @Test
    void doublonIdentifiantSignaleEtSaute() {
        String json = "{\"schema\":\"joide.recents/1\",\"projets\":["
                + "{\"id\":\"x\",\"nom\":\"un\",\"type\":\"vide\","
                + "\"dossier\":\"x\",\"ouvert\":5,\"modules\":0},"
                + "{\"id\":\"x\",\"nom\":\"deux\",\"type\":\"vide\","
                + "\"dossier\":\"x\",\"ouvert\":6,\"modules\":0}]}";
        ProjetsRecents.Chargement lu = ProjetsRecents.analyser(json);
        assertEquals(1, lu.entrees().size());
        assertFalse(lu.problemes().isEmpty());
    }

    @Test
    void ecritureAtomiquePuisLecture(@TempDir Path dossier)
            throws IOException {
        Path fichier = dossier.resolve("projets-recents.json");
        ProjetsRecents.Entree e = new ProjetsRecents.Entree("console-essai",
                "console-essai", TypeProjet.CONSOLE, "console-essai",
                BASE, 1);
        ProjetsRecents.ecrire(fichier, List.of(e));
        assertTrue(Files.isRegularFile(fichier));
        ProjetsRecents.Chargement lu = ProjetsRecents.lire(fichier);
        assertTrue(lu.problemes().isEmpty());
        assertEquals(List.of(e), lu.entrees());
        // réécriture : le fichier reste lisible (jamais de moitié écrite)
        ProjetsRecents.ecrire(fichier, List.of());
        assertTrue(ProjetsRecents.lire(fichier).entrees().isEmpty());
    }

    @Test
    void ouvrirMetAJourPuisTriDecroissant() {
        ProjetsRecents.Entree ancien = new ProjetsRecents.Entree("a", "a",
                TypeProjet.BIBLIOTHEQUE, "a", BASE, 2);
        ProjetsRecents.Entree recent = new ProjetsRecents.Entree("b", "b",
                TypeProjet.ANDROID, "b", BASE + 10_000L, 3);
        List<ProjetsRecents.Entree> apres =
                ProjetsRecents.ouvrir(List.of(ancien), recent);
        assertEquals(List.of(recent, ancien), apres);
        // réouverture de l'ancien : il passe devant, même identifiant
        ProjetsRecents.Entree ancienReouvert =
                new ProjetsRecents.Entree("a", "a", TypeProjet.BIBLIOTHEQUE,
                        "a", BASE + 60_000L, 2);
        assertEquals(List.of(ancienReouvert, recent),
                ProjetsRecents.ouvrir(apres, ancienReouvert));
    }

    @Test
    void ouvrirAjouteUnNouveauProjet() {
        ProjetsRecents.Entree nouveau = new ProjetsRecents.Entree("c", "c",
                TypeProjet.GRADLE, "c", BASE, 0);
        assertEquals(2,
                ProjetsRecents.ouvrir(List.of(
                        new ProjetsRecents.Entree("a", "a", TypeProjet.VIDE,
                                "a", BASE, 0)), nouveau).size());
    }

    @Test
    void renommerChangeLeNomPasLeDossier() {
        ProjetsRecents.Entree e = new ProjetsRecents.Entree("a", "a",
                TypeProjet.CONSOLE, "a", BASE, 1);
        List<ProjetsRecents.Entree> apres =
                ProjetsRecents.renommer(List.of(e), "a", "mon nom affiché");
        assertEquals("mon nom affiché", apres.get(0).nomAffiche());
        assertEquals("a", apres.get(0).dossier());
        assertEquals("a", apres.get(0).id());
    }

    @Test
    void renommerVideOuAbsentNeChangeRien() {
        ProjetsRecents.Entree e = new ProjetsRecents.Entree("a", "a",
                TypeProjet.VIDE, "a", BASE, 0);
        List<ProjetsRecents.Entree> une = List.of(e);
        assertEquals(une, ProjetsRecents.renommer(une, "a", "  "));
        assertEquals(une, ProjetsRecents.renommer(une, "absent", "x"));
    }

    @Test
    void retirerEnleveSansToucherAuxAutres() {
        ProjetsRecents.Entree a = new ProjetsRecents.Entree("a", "a",
                TypeProjet.VIDE, "a", BASE, 0);
        ProjetsRecents.Entree b = new ProjetsRecents.Entree("b", "b",
                TypeProjet.VIDE, "b", BASE, 0);
        assertEquals(List.of(b), ProjetsRecents.retirer(List.of(a, b), "a"));
    }

    @Test
    void assainirEtUnicite() {
        // convention P8 (ImporteurProjet) : l'espace devient souligné
        assertEquals("mon_application",
                ProjetsRecents.assainirId("mon application"));
        assertEquals("projet", ProjetsRecents.assainirId("  _  "));
        assertEquals("projet", ProjetsRecents.assainirId(null));
        ProjetsRecents.Entree a = new ProjetsRecents.Entree(
                "mon-application", "mon-application", TypeProjet.ANDROID,
                "mon-application", BASE, 2);
        assertEquals("mon-application-2",
                ProjetsRecents.idUnique(List.of(a), "mon-application"));
        assertEquals("autre",
                ProjetsRecents.idUnique(List.of(a), "autre"));
    }
}
