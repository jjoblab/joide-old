package jo.joide.app.android.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import jo.joide.app.android.ConsoleBuild;
import jo.joide.app.android.console.EtatConsole.Etat;
import jo.joide.app.android.console.EtatConsole.Filtre;
import jo.joide.app.android.console.EtatConsole.PhaseEtat;

/**
 * L'état du panneau console (lot K, ADR-É6) : compteur d'erreurs
 * exact, bande de phases avec durées mesurées, filtres, plafond
 * annoncé, résumé chiffré — la franchise du lot F conservée.
 */
class EtatConsoleTest {

    @Test
    void videAuDemarragePuisEcartsAvantLancement() {
        EtatConsole etat = new EtatConsole();
        assertEquals(Etat.VIDE, etat.etat());
        assertTrue(etat.lignes().isEmpty());
        etat.surEcarts(List.of(
                "app : module Android non construit sur appareil en P8"));
        assertEquals(2, etat.lignes().size(),
                "l'en-tête des écarts, puis chaque écart");
        assertEquals(ConsoleBuild.Nature.PHASE, etat.lignes().get(0)
                .nature());
        assertEquals(ConsoleBuild.Nature.SYSTEME, etat.lignes().get(1)
                .nature());
        assertTrue(etat.compteErreurs() == 0,
                "un écart n'est pas une erreur : le compteur reste exact");
    }

    @Test
    void rienAConstruireResteUnRefusMotive() {
        EtatConsole etat = new EtatConsole();
        etat.surRienAConstruire();
        assertEquals(Etat.ECHOUE, etat.etat());
        assertEquals(1, etat.compteErreurs());
        assertTrue(etat.lignes().get(0).texte()
                .contains("rien à construire"));
    }

    @Test
    void phasesSeTerminentAvecDureeMesuree() {
        EtatConsole etat = new EtatConsole();
        etat.surDemarrage(4, 1_000L);
        etat.surTache("app:generateSources", "STARTED", 1_100L);
        etat.surTache("app:generateSources", "SUCCEEDED", 1_300L);
        etat.surTache("app:compileKotlin", "STARTED", 1_400L);
        etat.surTache("app:compileJava", "STARTED", 2_000L);
        etat.surTache("app:jar", "STARTED", 4_500L);
        etat.surTermine(4, true, 12, 3, 2, 6_200L, List.of(), 7_200L);

        List<PhaseEtat> phases = etat.phases();
        assertEquals(3, phases.size(),
                "la bande porte les trois phases du pipeline");
        assertEquals(ConsoleBuild.Phase.PREPARATION, phases.get(0).phase());
        assertTrue(phases.get(0).terminee());
        assertEquals(300L, phases.get(0).dureeMs(),
                "préparation : de 1 100 (première tâche) à 1 400 (compilation)");
        assertTrue(phases.get(1).terminee());
        assertEquals(3_100L, phases.get(1).dureeMs(),
                "compilation : de 1 400 à 4 500 (jar)");
        assertTrue(phases.get(2).terminee());
        assertEquals(2_700L, phases.get(2).dureeMs(),
                "assemblage : de 4 500 à la fin 7 200");
        assertNull(etat.phaseCourante());
        assertEquals(Etat.REUSSI, etat.etat());
    }

    @Test
    void deuxTachesDeLaMemePhaseNeLaRouvrePas() {
        EtatConsole etat = new EtatConsole();
        etat.surDemarrage(1, 0L);
        etat.surTache("app:compileKotlin", "STARTED", 100L);
        etat.surTache("app:compileJava", "STARTED", 200L);
        List<PhaseEtat> phases = etat.phases();
        assertEquals(1, phases.stream()
                .filter(p -> p.phase() == ConsoleBuild.Phase.COMPILATION)
                .count());
        assertEquals(ConsoleBuild.Phase.COMPILATION, etat.phaseCourante());
    }

    @Test
    void phaseCourantePourLaPiluleDEnTete() {
        EtatConsole etat = new EtatConsole();
        etat.surDemarrage(5, 0L);
        etat.surTache("app:compileKotlin", "STARTED", 50L);
        assertEquals(Etat.EN_COURS, etat.etat());
        assertEquals(ConsoleBuild.Phase.COMPILATION,
                etat.phaseCourante());
    }

    @Test
    void filtresEtCompteurExact() {
        EtatConsole etat = new EtatConsole();
        etat.surDemarrage(1, 0L);
        etat.surTache("app:compileKotlin", "FAILED", 10L);
        etat.surTermine(1, false, 0, 0, 0, 100L,
                List.of("app:compileJava : unknown type Foo"), 200L);
        assertEquals(2, etat.compteErreurs(),
                "la tâche échouée ET le problème du bilan comptent"
                        + " tous deux : le compteur est exact");
        assertEquals(etat.lignes().size(),
                etat.lignesFiltrees().size(),
                "filtre Tout : tout est visible");
        etat.setFiltre(Filtre.ERREURS);
        assertTrue(etat.lignesFiltrees().stream().allMatch(
                l -> l.nature() == ConsoleBuild.Nature.ERREUR));
        etat.setFiltre(Filtre.SYSTEME);
        assertTrue(etat.lignesFiltrees().stream().allMatch(
                l -> l.nature() == ConsoleBuild.Nature.SYSTEME));
        etat.setFiltre(null);
        assertEquals(Filtre.TOUT, etat.filtre(),
                "un filtre null retombe sur Tout");
    }

    @Test
    void resumeFinalChiffreJamaisUnSuccesSansMesures() {
        EtatConsole etat = new EtatConsole();
        etat.surDemarrage(4, 0L);
        etat.surTermine(4, true, 12, 3, 2, 6_200L, List.of(), 6_200L);
        ConsoleBuild.Ligne resume = etat.lignes().get(
                etat.lignes().size() - 1);
        assertEquals(ConsoleBuild.Nature.SYSTEME, resume.nature());
        assertTrue(resume.texte().contains("build #4 réussi"));
        assertTrue(resume.texte().contains("12 exécutées · 3 du cache"
                + " · 2 à jour"), "le résumé est chiffré : " + resume);
        assertTrue(resume.texte().contains("6,2 s"));
        assertEquals("6,2 s", EtatConsole.secondes(6_200L));
        assertEquals("0,8 s", EtatConsole.secondes(800L));
    }

    @Test
    void mortDuServiceEstUnEvenementPasUnZombie() {
        EtatConsole etat = new EtatConsole();
        etat.surDemarrage(7, 0L);
        etat.surTache("app:compileKotlin", "STARTED", 10L);
        etat.surInterrompu();
        assertEquals(Etat.INTERROMPU, etat.etat());
        assertTrue(etat.lignes().get(etat.lignes().size() - 1).texte()
                .contains("l'IDE survit"));
    }

    @Test
    void premierProblemeLocalisePourLeBoutonOuvrir() {
        EtatConsole etat = new EtatConsole();
        etat.surDemarrage(1, 0L);
        assertNull(etat.premierProblemeLocalise(),
                "rien avant tout problème");
        etat.surTermine(1, false, 0, 0, 0, 50L,
                List.of("app:jar : entrée dupliquée",
                        "app:compileJava : src/A.java:9:1 : erreur"),
                100L);
        ConsoleBuild.Ligne premier = etat.premierProblemeLocalise();
        assertEquals("src/A.java:9", premier.cible(),
                "le premier problème LOCALISÉ seulement : le bouton"
                        + " ne ment jamais");
    }

    @Test
    void plafondRogneEtSeAnnonce() {
        EtatConsole etat = new EtatConsole();
        etat.surDemarrage(1, 0L);
        for (int i = 0; i < ConsoleBuild.PLAFOND_LIGNES + 40; i++) {
            etat.surTache("m:compileJava", "SUCCEEDED", i);
        }
        assertEquals(ConsoleBuild.PLAFOND_LIGNES, etat.lignes().size(),
                "le transcript garde les 400 dernières lignes");
        assertTrue(etat.plafonne(),
                "le plafond a rogné : il est annoncé, jamais muet");
        assertTrue(etat.lignes().get(0).texte().contains("compileJava"),
                "les plus anciennes lignes (le démarrage) sont rognées");
    }
}
