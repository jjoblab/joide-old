package jo.joide.app.android.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.ConsoleBuild;
import jo.joide.app.android.console.EtatConsole.Etat;
import jo.joide.app.android.console.EtatConsole.Filtre;

/**
 * La « capture » du lot K contre la maquette
 * {@code docs/ecrans/design/console.html} : le test lit la maquette et
 * vérifie que l'état de console livré reproduit sa structure — états
 * échoué avec problème cliquable et en cours avec phases actives,
 * compteur d'erreurs exact, filtres, transcript coloré par nature,
 * plafond annoncé, résumé chiffré. Le protocole du démon reste
 * inchangé (critère du lot K).
 */
class MaquetteConsoleTest {

    @Test
    void laMaquettePorteLesDeuxEtats() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("problème cliquable"));
        assertTrue(maquette.contains("Pendant un build — phases actives"));
        assertTrue(maquette.contains("ADR-É6"));
    }

    @Test
    void etatEchoueProblemeCliquableEtCompteExact() throws IOException {
        String maquette = maquette();
        // la maquette : pilule « échoué · 6,2 s », filtre « Erreurs
        // (1) », ligne d'erreur, bouton « Ouvrir le fichier à la
        // ligne »
        assertTrue(maquette.contains("echec\"><i></i>échoué · 6,2 s"));
        assertTrue(maquette.contains("Erreurs (1)"));
        assertTrue(maquette.contains("symbole introuvable"));
        assertTrue(maquette.contains("Ouvrir le fichier à la ligne"));
        // le même état par la classe pure : le problème du bilan est
        // une ligne d'erreur, le compte est exact
        EtatConsole etat = new EtatConsole();
        etat.surEcarts(List.of(
                "app : module Android non construit sur appareil (P8)"));
        etat.surDemarrage(4, 1_000L);
        etat.surTache("app:compileKotlin", "SUCCEEDED", 1_400L);
        etat.surTermine(4, false, 12, 3, 2, 6_200L,
                List.of("app:compileJava : src/main/MainActivity.kt:12:1"
                        + " — symbole introuvable : R.layout.activity_main"),
                7_200L);
        assertEquals(Etat.ECHOUE, etat.etat());
        assertEquals(1, etat.compteErreurs(),
                "le compteur d'erreurs est exact");
        assertEquals("6,2 s", EtatConsole.secondes(6_200L));
        ConsoleBuild.Ligne probleme = etat.premierProblemeLocalise();
        assertEquals("src/main/MainActivity.kt:12", probleme.cible(),
                "le problème localisé de la maquette s'ouvre jusqu'à la"
                        + " ligne");
        etat.setFiltre(Filtre.ERREURS);
        assertEquals(1, etat.lignesFiltrees().size());
    }

    @Test
    void etatEnCoursPhasesActivesEtFiltreParPhase() throws IOException {
        String maquette = maquette();
        // la maquette : « en cours · compilation », phases cochées /
        // active / à venir
        assertTrue(maquette.contains(
                "encours\"><i></i>en cours · compilation"));
        assertTrue(maquette.contains(
                "● Compilation <span class=\"durée\">en cours…"));
        assertTrue(maquette.contains("Erreurs (0)"));
        EtatConsole etat = new EtatConsole();
        etat.surDemarrage(5, 0L);
        etat.surTache("app:generateSources", "STARTED", 50L);
        etat.surTache("app:compileKotlin", "STARTED", 800L);
        assertEquals(Etat.EN_COURS, etat.etat());
        assertEquals(ConsoleBuild.Phase.COMPILATION,
                etat.phaseCourante());
        assertEquals(0, etat.compteErreurs());
        // tap sur la phase : le transcript se filtre sur elle
        etat.setFiltrePhase(ConsoleBuild.Phase.COMPILATION);
        assertTrue(etat.lignesFiltrees().stream().allMatch(
                ligne -> ligne.phase()
                        == ConsoleBuild.Phase.COMPILATION));
        // retap : le filtre se retire
        etat.setFiltrePhase(ConsoleBuild.Phase.COMPILATION);
        assertNull(etat.filtrePhase());
    }

    @Test
    void transcriptColoreParNatureEnGrilleDeuxColonnes() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("class=\"sys\">système</span>"),
                "colonne de gauche : la nature");
        assertTrue(maquette.contains("class=\"err\">erreur</span>"));
        assertTrue(maquette.contains("class=\"phase-mot\">phase</span>"));
        // les natures de la maquette sont celles du formatage pur
        ConsoleBuild.Ligne systeme = ConsoleBuild.structurer("hello", -1,
                jo.joide.deps.json.Json.parse(
                        "{\"type\":\"hello\",\"maxHeapBytes\":"
                                + (512L * 1024L * 1024L) + "}").root)
                .get(0);
        assertEquals(ConsoleBuild.Nature.SYSTEME, systeme.nature());
        ConsoleBuild.Ligne tache = ConsoleBuild.structurer("task", 1,
                jo.joide.deps.json.Json.parse(
                        "{\"type\":\"task\",\"name\":\"app:compileJava\","
                                + "\"status\":\"SUCCEEDED\"}").root)
                .get(0);
        assertEquals(ConsoleBuild.Nature.TACHE, tache.nature());
    }

    @Test
    void plafondAnnonceEtResumeChiffre() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains(
                "affichage plafonné à 400 lignes"));
        assertTrue(maquette.contains(
                "le plafond est annoncé, jamais muet"));
        assertTrue(maquette.contains(
                "12 exécutées · 3 du cache · 2 à jour"));
        assertTrue(maquette.contains("Le résumé final est chiffré"));
        assertEquals(400, ConsoleBuild.PLAFOND_LIGNES);
        EtatConsole etat = new EtatConsole();
        etat.surDemarrage(4, 0L);
        etat.surTermine(4, true, 12, 3, 2, 6_200L, List.of(), 6_200L);
        String resume = etat.lignes().get(1).texte();
        assertTrue(resume.contains(
                "12 exécutées · 3 du cache · 2 à jour"), resume);
    }

    @Test
    void leProtocoleDuDemonResteInchange() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains(
                "conservation intégrale du lot F"));
        assertTrue(maquette.contains("jamais d'omission muette"));
        // les écarts s'affichent AVANT le lancement, « Rien à
        // construire » reste un refus motivé (comportement lot F)
        EtatConsole etat = new EtatConsole();
        etat.surEcarts(List.of("core : aucune racine de sources"));
        assertEquals(2, etat.lignes().size());
        etat.surRienAConstruire();
        assertTrue(etat.lignes().get(2).texte()
                .contains("rien à construire"));
    }

    private static String maquette() throws IOException {
        Path courant = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 5 && courant != null; i++) {
            Path candidate = courant.resolve(
                    "docs/ecrans/design/console.html");
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate,
                        StandardCharsets.UTF_8);
            }
            courant = courant.getParent();
        }
        throw new IOException("console.html not found from "
                + Paths.get("").toAbsolutePath());
    }
}
