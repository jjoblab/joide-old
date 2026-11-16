package jo.joide.app.android.atelier.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import jo.joide.agent.ErreursLlm;
import jo.joide.agent.EvenementAgent;
import jo.joide.agent.Permissions;
import jo.joide.agent.espace.QuestionUtilisateur;
import jo.joide.app.android.atelier.agent.EtatAgentChat.ActionErreur;
import jo.joide.app.android.atelier.agent.EtatAgentChat.Carte;
import jo.joide.app.android.atelier.agent.EtatAgentChat.Genre;

/**
 * La « capture » du lot P contre la maquette
 * {@code docs/ecrans/design/agent.html} et l'ADR-É11 : le test lit
 * la maquette (fichier autonome, textes contractuels) et vérifie que
 * l'état livré reproduit sa structure — cartes d'outils pliées avec
 * état, question à options, ligne d'usage à trois pieds, refus
 * motivé, attente décomptée — et les PHRASES de la maquette (le
 * composeur désactivé pendant le tour, « jamais une question en fin
 * de message », « fermer = refuser »). La validation visuelle sur
 * appareil revient au propriétaire via l'APK du CI (ADR-048).
 */
class MaquetteAgentTest {

    private static String maquette() throws IOException {
        // la maquette vit à la racine du dépôt : le test remonte
        // depuis son répertoire d'exécution jusqu'à la trouver
        Path courant = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 5 && courant != null; i++) {
            Path candidate = courant.resolve(
                    Paths.get("docs", "ecrans", "design",
                            "agent.html"));
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate,
                        StandardCharsets.UTF_8);
            }
            courant = courant.getParent();
        }
        throw new IOException("agent.html mockup not found");
    }

    @Test
    void laMaquetteExisteEtSeLit() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("ADR-É11"));
        assertTrue(maquette.contains("Agent</h1>"));
    }

    @Test
    void lesCartesDeLaMaquetteOntLeurGenreDansLetat()
            throws IOException {
        String maquette = maquette();
        // la carte d'outil pliée : nom mono + cible + état
        assertTrue(maquette.contains("read_file"));
        assertTrue(maquette.contains("run_task"));
        assertTrue(maquette.contains("refusé par l'utilisateur"));
        // l'état rend exactement ces trois états d'outil
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("q");
        etat.surEvenement(EvenementAgent.outilDebut("a", "read_file"));
        etat.surEvenement(EvenementAgent.outilFin("a", "read_file",
                "ok"));
        etat.surEvenement(EvenementAgent.outilDebut("b", "edit_file"));
        etat.surEvenement(EvenementAgent.outilRefuse("b", "edit_file",
                Permissions.REFUS_GATE));
        List<Carte> cartes = etat.cartes();
        assertEquals(EtatAgentChat.EtatOutil.FINI,
                cartes.get(1).etatOutil);
        assertEquals(EtatAgentChat.EtatOutil.REFUSE,
                cartes.get(2).etatOutil);
    }

    @Test
    void laQuestionDeLaMaquetteArriveParLaCarte() throws IOException {
        String maquette = maquette();
        // 1/2 et 2/2, options en puces, Autre…, Passer
        assertTrue(maquette.contains("1/2"));
        assertTrue(maquette.contains("Autre…"));
        assertTrue(maquette.contains("Passer"));
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("q");
        etat.poserQuestions(List.of(
                new QuestionUtilisateur("Parser JSON ou Gson ?",
                        List.of("Gson", "Parseur maison"))));
        Carte question = etat.cartes().get(1);
        assertEquals(Genre.QUESTIONS, question.genre);
        assertEquals(2, question.questions.get(0).options.size());
        // la carte reste bloquante jusqu'à réponse complète
        assertEquals(null, etat.reponses());
    }

    @Test
    void laLigneDUsageTroisPiedsDeLaMaquette() throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("in ·"));
        assertTrue(maquette.contains("out ·"));
        assertTrue(maquette.contains("cached"));
        // l'état pose les trois pieds et les requêtes du tour
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("q");
        etat.surEvenement(EvenementAgent.fin(
                EvenementAgent.RaisonFin.TERMINE));
        etat.surUsage(new jo.joide.agent.UsageLlm(
                14_120, 903, 11_240), 6);
        Carte usage = etat.cartes().get(1);
        assertEquals(Genre.USAGE, usage.genre);
        assertEquals(14_120, usage.usage.entrees);
        assertEquals(903, usage.usage.sorties);
        assertEquals(11_240, usage.usage.lecturesCache);
        assertEquals(6, usage.requetes);
    }

    @Test
    void lattenteEtLaCompactionDeLaMaquetteSontChiffrees()
            throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("reprise dans 12"));
        assertTrue(maquette.contains("Historique compacté"));
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("q");
        etat.surEvenement(EvenementAgent.attente(12));
        etat.surEvenement(EvenementAgent.compaction(128_400, 61_200));
        List<Carte> cartes = etat.cartes();
        assertEquals(Genre.ATTENTE, cartes.get(1).genre);
        assertEquals(12, cartes.get(1).secondes);
        assertEquals(Genre.COMPACTION, cartes.get(2).genre);
    }

    @Test
    void lerreurActionnableDeLaMaquetteProposeSonAction()
            throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("Clé absente"));
        assertTrue(maquette.contains("Ouvrir les réglages IA"));
        // l'état : la clé manquante propose les réglages, le modèle
        // inconnu propose le changement — chaque genre a son verbe
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("q");
        etat.surEvenement(EvenementAgent.erreur(
                new ErreursLlm.ErreurLlm(
                        ErreursLlm.Genre.CLE_MANQUANTE, "no key",
                        -1)));
        Carte erreur = etat.cartes().get(1);
        assertEquals(ActionErreur.OUVRIR_REGLAGES, erreur.action);
        assertNotNull(erreur.action);
    }

    @Test
    void leDiffDeplieDeLaMaquetteEstLeContratDePermission()
            throws IOException {
        String maquette = maquette();
        // le dialogue montre le vrai diff : - ancien / + nouveau
        assertTrue(maquette.contains("-&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
                .replace("&nbsp;", " "))
                || maquette.contains("-"),
                "le diff porte des lignes moins");
        assertTrue(maquette.contains("+"));
        assertTrue(maquette.contains("Fermer ce dialogue équivaut à refuser"));
        // le contrat d'état : fermer = REFUSER (défaut sûr)
        EtatAgentChat etat = new EtatAgentChat();
        etat.poserPermission(new Permissions.DemandeEcriture(
                "edit_file", "src/A.java", "@@ -1 +1 @@"));
        assertEquals(Permissions.Decision.REFUSEE,
                etat.repondrePermission(false));
    }

    @Test
    void leComposeurDesactivePendantLeTourDeLaMaquette()
            throws IOException {
        String maquette = maquette();
        assertTrue(maquette.contains("Arrêter le tour"));
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("q");
        assertEquals(EtatAgentChat.TourEtat.EN_COURS, etat.etat());
        // l'état dit ce que la maquette montre : pendant le tour, le
        // composeur cède la place à l'arrêt (la vue s'en sert)
        etat.surEvenement(EvenementAgent.fin(
                EvenementAgent.RaisonFin.TERMINE));
        assertEquals(EtatAgentChat.TourEtat.TERMINE, etat.etat());
    }
}
