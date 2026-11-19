package jo.joide.app.android.atelier.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import jo.joide.agent.ErreursLlm;
import jo.joide.agent.EvenementAgent;
import jo.joide.agent.Permissions;
import jo.joide.agent.UsageLlm;
import jo.joide.agent.espace.QuestionUtilisateur;
import jo.joide.app.android.atelier.agent.EtatAgentChat.ActionErreur;
import jo.joide.app.android.atelier.agent.EtatAgentChat.Carte;
import jo.joide.app.android.atelier.agent.EtatAgentChat.Genre;
import jo.joide.app.android.atelier.agent.EtatAgentChat.TourEtat;

/**
 * L'état du tiroir agent éprouvé par événements (lot P, ADR-É11) :
 * le transcript se CONSTRUIT, rien ne se déduit ; les quatorze
 * genres d'erreur ont leur verbe ; fermer la permission = refuser ;
 * le journal se REJOUE à l'identique (un écran qui revient retrouve
 * l'état exact).
 */
class EtatAgentChatTest {

    @Test
    void unTourCompletConstruitLeTranscriptDansLOrdre() {
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("ajoute la dépendance");
        etat.surEvenement(EvenementAgent.raisonnement("je regarde\n"));
        etat.surEvenement(EvenementAgent.raisonnement("le build."));
        etat.surEvenement(EvenementAgent.texte("C'est fait."));
        etat.surEvenement(EvenementAgent.outilDebut("a1", "read_file"));
        etat.surEvenement(EvenementAgent.outilFin("a1", "read_file",
                "42 lignes\n" + "…"));
        etat.surEvenement(EvenementAgent.fichiersChanges(
                "build.gradle.kts"));
        etat.surEvenement(EvenementAgent.fin(
                EvenementAgent.RaisonFin.TERMINE));
        etat.surUsage(new UsageLlm(14_120, 903, 11_240), 6);

        List<Carte> cartes = etat.cartes();
        assertEquals(6, cartes.size());
        assertEquals(Genre.MESSAGE_UTILISATEUR, cartes.get(0).genre);
        assertEquals(Genre.RAISONNEMENT, cartes.get(1).genre);
        assertEquals(2, cartes.get(1).lignesRaisonnement);
        assertEquals(Genre.REPONSE, cartes.get(2).genre);
        assertEquals("C'est fait.", cartes.get(2).reponse);
        assertEquals(Genre.OUTIL, cartes.get(3).genre);
        assertEquals(EtatAgentChat.EtatOutil.FINI,
                cartes.get(3).etatOutil);
        assertEquals(2, cartes.get(3).lignesResultat);
        assertEquals(Genre.FICHIERS, cartes.get(4).genre);
        assertEquals(Genre.USAGE, cartes.get(5).genre);
        assertEquals(TourEtat.TERMINE, etat.etat());
        assertEquals(6, cartes.get(5).requetes);
        assertEquals(11_240, cartes.get(5).usage.lecturesCache);
        assertEquals("build.gradle.kts", cartes.get(4).detail);
    }

    @Test
    void lesDeltasDeTexteSeComposentEnUneSeuleCarte() {
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("q");
        etat.surEvenement(EvenementAgent.texte("Bon"));
        etat.surEvenement(EvenementAgent.texte("jour"));
        etat.surEvenement(EvenementAgent.texte("."));
        List<Carte> cartes = etat.cartes();
        assertEquals(1, cartes.stream()
                .filter(c -> c.genre == Genre.REPONSE).count());
        assertEquals("Bonjour.", cartes.get(1).reponse);
    }

    @Test
    void unOutilRefusePorteSonMotif() {
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("q");
        etat.surEvenement(EvenementAgent.outilDebut("a", "edit_file"));
        etat.surEvenement(EvenementAgent.outilRefuse("a", "edit_file",
                Permissions.REFUS_GATE));
        Carte outil = etat.cartes().get(1);
        assertEquals(EtatAgentChat.EtatOutil.REFUSE, outil.etatOutil);
        assertTrue(outil.resultat.contains("declined"));
    }

    @Test
    void attenteEtCompactionSontVisiblesEtChiffrees() {
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("q");
        etat.surEvenement(EvenementAgent.attente(12));
        etat.surEvenement(EvenementAgent.compaction(128_400, 61_200));
        List<Carte> cartes = etat.cartes();
        assertEquals(Genre.ATTENTE, cartes.get(1).genre);
        assertEquals(12, cartes.get(1).secondes);
        assertEquals(Genre.COMPACTION, cartes.get(2).genre);
        assertEquals(128_400, cartes.get(2).avant);
        assertEquals(61_200, cartes.get(2).apres);
    }

    @Test
    void lesQuatorzeGenresOntLeurVerbe() {
        for (ErreursLlm.Genre genre : ErreursLlm.Genre.values()) {
            EtatAgentChat etat = new EtatAgentChat();
            etat.envoyer("q");
            etat.surEvenement(EvenementAgent.erreur(
                    new ErreursLlm.ErreurLlm(genre, "diag", -1)));
            Carte erreur = etat.cartes().get(1);
            assertEquals(Genre.ERREUR, erreur.genre);
            assertNotNull(erreur.action, genre + " sans verbe");
            assertEquals(TourEtat.ERREUR, etat.etat());
        }
    }

    @Test
    void laCleManquanteOuvreLesReglagesEtLeModeleInconnuChange() {
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("q");
        etat.surEvenement(EvenementAgent.erreur(
                new ErreursLlm.ErreurLlm(ErreursLlm.Genre.CLE_MANQUANTE,
                        "no key", -1)));
        assertEquals(ActionErreur.OUVRIR_REGLAGES,
                etat.cartes().get(1).action);

        etat.surEvenement(EvenementAgent.erreur(
                new ErreursLlm.ErreurLlm(ErreursLlm.Genre.MODELE_INCONNU,
                        "404", -1)));
        assertEquals(ActionErreur.CHANGER_MODELE,
                etat.cartes().get(2).action);
    }

    @Test
    void lesQuestionsSeRepondentEtSePassent() {
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("q");
        etat.poserQuestions(List.of(
                new QuestionUtilisateur("Parser JSON ou Gson ?",
                        List.of("Gson", "Maison")),
                new QuestionUtilisateur("Reformuler l'erreur ?",
                        List.of("Oui", "Non"))));
        // pas encore tout répondu
        assertNull(etat.reponses());
        etat.repondre(0, 0, null);
        etat.passer(1);
        List<String> reponses = etat.reponses();
        assertEquals(2, reponses.size());
        assertEquals("Parser JSON ou Gson ? : Gson", reponses.get(0));
        assertEquals("Reformuler l'erreur ? : (skipped)",
                reponses.get(1));
    }

    @Test
    void lautreEnTexteLibreGagne() {
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("q");
        etat.poserQuestions(List.of(new QuestionUtilisateur("Laquelle ?",
                List.of("A", "B"))));
        etat.repondre(0, -1, "une troisième voie");
        assertEquals("Laquelle ? : une troisième voie",
                etat.reponses().get(0));
    }

    @Test
    void fermerLaPermissionRevientARefuser() {
        EtatAgentChat etat = new EtatAgentChat();
        etat.poserPermission(new Permissions.DemandeEcriture(
                "edit_file", "src/A.java", "@@ -1 +1 @@"));
        assertNotNull(etat.permissionEnAttente());
        // fermer le dialogue sans choisir : REFUSER (défaut sûr)
        assertEquals(Permissions.Decision.REFUSEE,
                etat.repondrePermission(false));
        assertNull(etat.permissionEnAttente());
        assertEquals(Permissions.Decision.ACCORDEE,
                etat.repondrePermission(true));
    }

    @Test
    void lannulationMiOutilsResteHonnete() {
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("q");
        etat.surEvenement(EvenementAgent.outilDebut("a", "run_task"));
        etat.surEvenement(EvenementAgent.fin(
                EvenementAgent.RaisonFin.ANNULE));
        assertEquals(TourEtat.ANNULE, etat.etat());
        // la carte de l'outil interrompu reste EN COURS : le tour
        // s'est arrêté SOUS l'outil, jamais un résultat inventé
        assertEquals(EtatAgentChat.EtatOutil.EN_COURS,
                etat.cartes().get(1).etatOutil);
        // la conversation reprend : un nouvel envoi rouvre un tour
        etat.envoyer("et maintenant ?");
        assertEquals(TourEtat.EN_COURS, etat.etat());
    }

    @Test
    void leJournalSeRejoueAIdentique() {
        List<EvenementAgent> journal = List.of(
                EvenementAgent.texte("Je regarde."),
                EvenementAgent.outilDebut("a", "read_file"),
                EvenementAgent.outilFin("a", "read_file", "ok"),
                EvenementAgent.fin(EvenementAgent.RaisonFin.TERMINE));
        EtatAgentChat un = new EtatAgentChat();
        EtatAgentChat deux = new EtatAgentChat();
        for (EtatAgentChat etat : List.of(un, deux)) {
            etat.envoyer("pareil");
            for (EvenementAgent e : journal) {
                etat.surEvenement(e);
            }
            etat.surUsage(new UsageLlm(1, 2, 3), 1);
        }
        List<Carte> a = un.cartes();
        List<Carte> b = deux.cartes();
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).genre, b.get(i).genre);
            assertEquals(a.get(i).reponse, b.get(i).reponse);
            assertEquals(a.get(i).etatOutil, b.get(i).etatOutil);
        }
    }

    @Test
    void lePlafondDeCartesSeviqueEtSannonce() {
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("q");
        for (int i = 0; i < EtatAgentChat.PLAFOND_CARTES + 10; i++) {
            etat.surEvenement(EvenementAgent.attente(i));
        }
        assertTrue(etat.cartes().size()
                <= EtatAgentChat.PLAFOND_CARTES + 1,
                "le plafond tient (message courant inclus)");
        assertTrue(etat.evictions() >= 10);
        assertTrue(etat.plafondAnnonce());
    }

    @Test
    void lePreRemplissageQuoteLaSelection() {
        EtatAgentChat etat = new EtatAgentChat();
        etat.poserPreRemplissage("MainActivity.java", "int x = 1;");
        String pre = etat.preRemplissage();
        assertTrue(pre.contains("MainActivity.java"));
        assertTrue(pre.contains("int x = 1;"));
        // l'envoi le consomme : jamais deux fois
        assertNotNull(etat.prendrePreRemplissage());
        assertNull(etat.prendrePreRemplissage());
    }

    @Test
    void laReponseRestaureeResteUneReponsePasUnMessage() {
        EtatAgentChat etat = new EtatAgentChat();
        // le transcript persisté alterne message/réponse (lot S) —
        // la reprise de conversation rejoue les DEUX rôles
        etat.envoyer("corrige le build");
        etat.restaurerReponse("C'est corrigé.");
        etat.envoyer("merci, lance le build");
        etat.restaurerReponse("Build vert.");

        List<Carte> cartes = etat.cartes();
        assertEquals(4, cartes.size());
        assertEquals(Genre.MESSAGE_UTILISATEUR, cartes.get(0).genre);
        assertEquals(Genre.REPONSE, cartes.get(1).genre);
        assertEquals("C'est corrigé.", cartes.get(1).reponse);
        assertEquals(Genre.MESSAGE_UTILISATEUR, cartes.get(2).genre);
        assertEquals(Genre.REPONSE, cartes.get(3).genre);
        assertEquals(TourEtat.TERMINE, etat.etat());
    }

    // ------------------------------------------------------------------
    // Lot C4 (2026-11-20) — les TRANCHES DE TRAVAIL (le WorkBlock de
    // CodeAssist) : tout ce qui précède la DERNIÈRE carte d'outil d'un
    // tour se replie en un bloc ; ce qui suit reste au premier plan.
    // ------------------------------------------------------------------

    @Test
    void laTrancheCouvreJusquauDernierOutilDuTour() {
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("corrige le build");
        etat.surEvenement(EvenementAgent.raisonnement(
                "je regarde le build"));
        etat.surEvenement(EvenementAgent.outilDebut("a1", "read_file"));
        etat.surEvenement(EvenementAgent.outilFin("a1", "read_file",
                "build.gradle.kts"));
        etat.surEvenement(EvenementAgent.raisonnement(
                "la dépendance manque"));
        etat.surEvenement(EvenementAgent.outilDebut("a2", "edit_file"));
        etat.surEvenement(EvenementAgent.outilFin("a2", "edit_file",
                "ok"));
        etat.surEvenement(EvenementAgent.raisonnement(
                "voila le fix"));
        etat.surEvenement(EvenementAgent.texte("C'est corrigé."));
        etat.surEvenement(EvenementAgent.fin(
                jo.joide.agent.EvenementAgent.RaisonFin.TERMINE));

        // le transcript réel : 0 msg · 1 rai (les deltas
        // s'accumulent en UNE carte par tour) · 2 out1 · 3 out2 ·
        // 4 rep · 5 usage — la tranche couvre [1..3] : TOUT le
        // raisonnement du tour et JUSQU'AU dernier outil inclus, la
        // réponse reste au premier plan
        List<EtatAgentChat.Tranche> tranches = etat.tranches();
        assertEquals(1, tranches.size());
        assertEquals(new EtatAgentChat.Tranche(1, 3), tranches.get(0));
    }

    @Test
    void sansOutilAucuneTranche() {
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("bonjour");
        etat.surEvenement(EvenementAgent.raisonnement("salut"));
        etat.surEvenement(EvenementAgent.texte("Salut !"));
        etat.surEvenement(EvenementAgent.fin(
                jo.joide.agent.EvenementAgent.RaisonFin.TERMINE));
        assertTrue(etat.tranches().isEmpty());
    }

    @Test
    void uneQuestionCoupeLaTrancheElleResteAuPremierPlan() {
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("migre le projet");
        etat.surEvenement(EvenementAgent.outilDebut("a1", "read_file"));
        etat.surEvenement(EvenementAgent.outilFin("a1", "read_file",
                "lu"));
        etat.poserQuestions(List.of(new QuestionUtilisateur(
                "Quelle cible ?", List.of("jvm", "android"))));
        etat.surEvenement(EvenementAgent.outilDebut("a2", "edit_file"));
        etat.surEvenement(EvenementAgent.outilFin("a2", "edit_file",
                "ok"));

        // cartes : 0 msg · 1 out1 · 2 questions · 3 out2 — la question
        // ne se replie JAMAIS dans un bloc : DEUX tranches de part et
        // d'autre d'elle
        List<EtatAgentChat.Tranche> tranches = etat.tranches();
        assertEquals(2, tranches.size());
        assertEquals(new EtatAgentChat.Tranche(1, 1), tranches.get(0));
        assertEquals(new EtatAgentChat.Tranche(3, 3), tranches.get(1));
    }

    @Test
    void lesFichiersTouchesComptentLesCiblesDistinctes() {
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("corrige");
        etat.surEvenement(EvenementAgent.outilDebut("a1", "edit_file"));
        etat.surEvenement(EvenementAgent.outilFin("a1", "edit_file",
                "ok"));
        etat.cartes().get(1).cible = "build.gradle.kts";
        etat.surEvenement(EvenementAgent.outilDebut("a2", "edit_file"));
        etat.surEvenement(EvenementAgent.outilFin("a2", "edit_file",
                "ok"));
        etat.cartes().get(2).cible = "build.gradle.kts";
        etat.surEvenement(EvenementAgent.outilDebut("a3", "edit_file"));
        etat.surEvenement(EvenementAgent.outilFin("a3", "edit_file",
                "ok"));
        etat.cartes().get(3).cible = "settings.gradle.kts";

        List<Carte> cartes = etat.cartes();
        assertEquals(2, EtatAgentChat.fichiersTouches(cartes,
                new EtatAgentChat.Tranche(1, 3)));
    }

    @Test
    void lePliDeTravailVitParCleEtSurvitAuxIndices() {
        EtatAgentChat etat = new EtatAgentChat();
        etat.envoyer("corrige");
        etat.surEvenement(EvenementAgent.outilDebut("cle-42",
                "edit_file"));
        etat.surEvenement(EvenementAgent.outilFin("cle-42",
                "edit_file", "ok"));
        List<Carte> cartes = etat.cartes();
        String cle = EtatAgentChat.cleDeTranche(cartes,
                etat.tranches().get(0));

        assertEquals("cle-42", cle);
        assertFalse(etat.travailDeplie(cle));
        etat.poserTravailDeplie(cle, true);
        assertTrue(etat.travailDeplie(cle));
        etat.poserTravailDeplie(cle, false);
        assertFalse(etat.travailDeplie(cle));
        // clé vide : aucun pli (un appel sans identifiant ne plie pas)
        assertFalse(etat.travailDeplie(""));
        etat.poserTravailDeplie("", true);
        assertFalse(etat.travailDeplie(""));
    }
}
