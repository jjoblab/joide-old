package jo.joide.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import jo.joide.platform.CancelToken;

/**
 * La boucle d'agent aux évaluations à modèle scripté (ADR-É9) : on
 * juge l'ÉTAT FINAL (outils reçus, historique, usage), pas le
 * transcript. Les garde-fous : le PRÉFIXE STABLE (la requête N+1
 * contient la requête N octet pour octet en préfixe — une dérive
 * silencieuse ne se voit que sur la facture, le test échoue à sa
 * place), la PASSATION au plafond, la PATIENCE visible des 429, le
 * parallélisme lecture / la séquence mutation, les permissions.
 */
class BoucleAgentTest {

    /** Un port d'outils sur des outils en mémoire. */
    private static final class PortMemoire
            implements ExecuteurOutils.PortOutils {
        final List<OutilAgent> outils;

        PortMemoire(OutilAgent... outils) {
            this.outils = List.of(outils);
        }

        @Override
        public OutilAgent outil(String nom) {
            return outils.stream()
                    .filter(o -> o.description().nom.equals(nom))
                    .findFirst().orElse(null);
        }

        @Override
        public List<OutilAgent> outils() {
            return outils;
        }
    }

    private static BoucleAgent boucle(FournisseurScripte fournisseur,
                                      ExecuteurOutils executeur,
                                      BoucleAgent.Config config) {
        BoucleAgent boucle = new BoucleAgent(fournisseur, "modele-x",
                new FauxTransport(List.of()), executeur, config, null);
        // le sommeil des attentes est instantané : les tests ne
        // dorment jamais
        boucle.veille = millisecondes -> {
        };
        return boucle;
    }

    @Test
    void unTourAvecOutilsJugeLEtatFinal() {
        OutilMemoire lecture = new OutilMemoire("read_file", false,
                "contenu du fichier");
        FournisseurScripte fournisseur = new FournisseurScripte(
                List.of(
                        new MessageAgent(MessageAgent.Role.ASSISTANT,
                                List.of(PartieContenu.appel("a1",
                                        "read_file",
                                        "{\"path\":\"src/A.java\"}"))),
                        MessageAgent.assistant("J'ai lu le fichier.")));
        BoucleAgent boucle = boucle(fournisseur,
                new ExecuteurOutils(new PortMemoire(lecture)), null);
        HistoriqueAgent historique = HistoriqueAgent.vide();
        BoucleAgent.ResultatTour resultat = boucle.tour("lis le "
                        + "fichier", historique,
                Permissions.Mode.ACCEPTATION_AUTO, null, null,
                CancelToken.NEVER, null);
        assertEquals(EvenementAgent.RaisonFin.TERMINE,
                resultat.raison);
        assertEquals("J'ai lu le fichier.", resultat.texteFinal);
        assertEquals(1, lecture.invocations.size());
        assertEquals("{\"path\":\"src/A.java\"}",
                lecture.invocations.get(0).arguments(),
                "les arguments arrivent BRUTS, tels que le modèle "
                        + "les a dits");
        // l'historique : utilisateur, assistant (appel), résultats,
        // assistant (final) — append-only
        List<MessageAgent> messages = historique.messages();
        assertEquals(4, messages.size());
        assertEquals(PartieContenu.Genre.RESULTAT_OUTIL,
                messages.get(2).parties.get(0).genre);
        assertEquals("contenu du fichier",
                messages.get(2).parties.get(0).texte);
    }

    @Test
    void lePrefixeEstStableAuFilDeLaBoucleDoutils() {
        // la conversation la plus longue possible sous le seuil de
        // compaction : trois pas d'outils, un final
        OutilMemoire lecture = new OutilMemoire("read_file", false,
                "ok");
        List<MessageAgent> reponses = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            reponses.add(new MessageAgent(
                    MessageAgent.Role.ASSISTANT,
                    List.of(PartieContenu.appel("a" + i, "read_file",
                            "{\"n\":" + i + "}"))));
        }
        reponses.add(MessageAgent.assistant("fini"));
        FournisseurScripte fournisseur = new FournisseurScripte(
                reponses);
        BoucleAgent boucle = boucle(fournisseur,
                new ExecuteurOutils(new PortMemoire(lecture)), null);
        BoucleAgent.ResultatTour resultat = boucle.tour("vas-y",
                HistoriqueAgent.vide(),
                Permissions.Mode.ACCEPTATION_AUTO, null, null,
                CancelToken.NEVER, null);
        assertEquals(EvenementAgent.RaisonFin.TERMINE,
                resultat.raison);
        assertEquals(4, fournisseur.requetes.size(),
                "trois pas d'outils + la réponse finale");
        for (int i = 1; i < fournisseur.requetes.size(); i++) {
            String avant = fournisseur.requetes.get(i - 1)
                    .empreinteStable();
            String apres = fournisseur.requetes.get(i)
                    .empreinteStable();
            assertTrue(apres.startsWith(avant),
                    "la requête " + (i + 1) + " doit contenir la "
                            + "requête " + i
                            + " octet pour octet en préfixe "
                            + "(hors la note volatile qui avance par "
                            + "conception)");
        }
        // l'outillage proposé est identique à chaque pas
        for (RequeteLlm requete : fournisseur.requetes) {
            assertEquals(1, requete.outils.size());
            assertEquals("read_file", requete.outils.get(0).nom);
        }
    }

    @Test
    void lePlafondDiterationsTermineEnPassationSansOutils() {
        OutilMemoire lecture = new OutilMemoire("read_file", false,
                "ok");
        // le modèle appelle TOUJOURS un outil : le plafond arrivera
        List<MessageAgent> reponses = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            reponses.add(new MessageAgent(
                    MessageAgent.Role.ASSISTANT,
                    List.of(PartieContenu.appel("a" + i, "read_file",
                            "{\"n\":" + i + "}"))));
        }
        reponses.add(MessageAgent.assistant(
                "J'ai changé A.java, vérifié le build, il reste les "
                        + "tests."));
        FournisseurScripte fournisseur = new FournisseurScripte(
                reponses);
        BoucleAgent.Config config = new BoucleAgent.Config(4, 128, 6,
                60, false);
        BoucleAgent boucle = boucle(fournisseur,
                new ExecuteurOutils(new PortMemoire(lecture)), config);
        BoucleAgent.ResultatTour resultat = boucle.tour("continue",
                HistoriqueAgent.vide(),
                Permissions.Mode.ACCEPTATION_AUTO, null, null,
                CancelToken.NEVER, null);
        assertEquals(EvenementAgent.RaisonFin.PLAFOND,
                resultat.raison);
        assertEquals(4, fournisseur.requetes.size(),
                "quatre requêtes : trois avec outils, la passation");
        assertTrue(fournisseur.requetes.get(3).outils.isEmpty(),
                "la passation part SANS outils");
        assertTrue(resultat.texteFinal.contains("reste"),
                "l'utilisateur reçoit un état exploitable");
    }

    @Test
    void laLimiteDeDebitSePatienteVisiblementPuisReussit() {
        OutilMemoire lecture = new OutilMemoire("read_file", false,
                "ok");
        ErreursLlm.ErreurLlm limite = new ErreursLlm.ErreurLlm(
                ErreursLlm.Genre.LIMITE_DEBIT,
                "rate limited (429)", 2);
        List<MessageAgent> reponses = List.of(
                MessageAgent.assistant("première réponse"));
        List<RuntimeException> exceptions = List.of(
                limite, limite, limite);
        List<UsageLlm> usages = List.of(UsageLlm.zero());
        FournisseurScripte fournisseur = new FournisseurScripte(
                reponses, usages, exceptions);
        BoucleAgent boucle = boucle(fournisseur,
                new ExecuteurOutils(new PortMemoire(lecture)), null);
        List<EvenementAgent> evenements = new ArrayList<>();
        BoucleAgent.ResultatTour resultat = boucle.tour("fais",
                HistoriqueAgent.vide(),
                Permissions.Mode.ACCEPTATION_AUTO, null, null,
                CancelToken.NEVER,
                evenements::add);
        assertEquals(EvenementAgent.RaisonFin.TERMINE,
                resultat.raison,
                "après trois attentes visibles, la réponse passe");
        long attentes = evenements.stream()
                .filter(e -> e.genre == EvenementAgent.Genre.ATTENTE)
                .count();
        assertTrue(attentes >= 4,
                "le décompte annulable est émis : " + attentes
                        + " événements d'attente");
        // le décompte DÉCROÎT : 2 puis 1, par attente
        List<Long> decomptes = evenements.stream()
                .filter(e -> e.genre == EvenementAgent.Genre.ATTENTE)
                .map(e -> e.secondes).toList();
        assertTrue(decomptes.contains(2L) && decomptes.contains(1L),
                "chaque attente énonce son délai et décompte : "
                        + decomptes);
    }

    @Test
    void apresLeMaximumDattentesLErreurFinitLeTourEnLeDisant() {
        OutilMemoire lecture = new OutilMemoire("read_file", false,
                "ok");
        ErreursLlm.ErreurLlm limite = new ErreursLlm.ErreurLlm(
                ErreursLlm.Genre.LIMITE_DEBIT,
                "rate limited (429)", 1);
        FournisseurScripte fournisseur = new FournisseurScripte(
                List.of(), List.of(),
                List.of(limite, limite, limite, limite, limite,
                        limite, limite));
        BoucleAgent.Config config = new BoucleAgent.Config(4, 128, 3,
                60, false);
        BoucleAgent boucle = boucle(fournisseur,
                new ExecuteurOutils(new PortMemoire(lecture)), config);
        List<EvenementAgent> evenements = new ArrayList<>();
        try {
            boucle.tour("fais", HistoriqueAgent.vide(),
                    Permissions.Mode.ACCEPTATION_AUTO, null, null,
                    CancelToken.NEVER, evenements::add);
            throw new AssertionError("devrait lever");
        } catch (ErreursLlm.ErreurLlm erreur) {
            assertEquals(ErreursLlm.Genre.LIMITE_DEBIT, erreur.genre);
            assertTrue(evenements.stream().anyMatch(
                    e -> e.genre == EvenementAgent.Genre.ERREUR),
                    "l'erreur est un ÉVÉNEMENT, l'UI dira quoi faire");
        }
    }

    @org.junit.jupiter.api.Timeout(30)
    @Test
    void lesLecturesSontParallelesLesMutationsEnSequence() {
        // trois lectures qui se chevauchent réellement (barrière),
        // deux mutations : les threads des lectures DIFFÈRENT, celles
        // des mutations suivent l'ordre des appels
        CountDownLatch barriere = new CountDownLatch(3);
        OutilMemoire lecture = new OutilMemoire("read_file", false,
                "ok");
        lecture.effet = () -> {
            // rendez-vous : chaque lecture ANNONCE son arrivée puis
            // attend les autres — preuve de chevauchement réel
            barriere.countDown();
            try {
                barriere.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        OutilMemoire ecriture = new OutilMemoire("write_file", true,
                "written");
        AtomicInteger compte = new AtomicInteger();
        ecriture.effet = () -> {
            assertEquals(1, compte.incrementAndGet(),
                    "la première mutation ne démarre qu'après la "
                            + "précédente");
            compte.decrementAndGet();
        };
        MessageAgent pas = new MessageAgent(
                MessageAgent.Role.ASSISTANT,
                List.of(
                        PartieContenu.appel("l1", "read_file", "{}"),
                        PartieContenu.appel("l2", "read_file", "{}"),
                        PartieContenu.appel("l3", "read_file", "{}"),
                        PartieContenu.appel("w1", "write_file",
                                "{\"path\":\"a\"}"),
                        PartieContenu.appel("w2", "write_file",
                                "{\"path\":\"b\"}")));
        FournisseurScripte fournisseur = new FournisseurScripte(
                List.of(pas, MessageAgent.assistant("fini")));
        BoucleAgent boucle = boucle(fournisseur,
                new ExecuteurOutils(
                        new PortMemoire(lecture, ecriture), 4), null);
        HistoriqueAgent historique = HistoriqueAgent.vide();
        BoucleAgent.ResultatTour resultat = boucle.tour("tout",
                historique,
                Permissions.Mode.ACCEPTATION_AUTO, null, null,
                CancelToken.NEVER, null);
        assertEquals(EvenementAgent.RaisonFin.TERMINE,
                resultat.raison);
        assertEquals(3, lecture.invocations.size());
        assertEquals(2, ecriture.invocations.size());
        long filsDistincts = lecture.invocations.stream()
                .map(OutilMemoire.Invocation::fil).distinct().count();
        assertTrue(filsDistincts >= 2,
                "les lectures se chevauchent : " + filsDistincts
                        + " fils distincts pour 3 lectures");
        // l'ordre des résultats suit TOUJOURS l'ordre des appels
        List<PartieContenu> resultats = historique.messages().stream()
                .flatMap(m -> m.parties.stream())
                .filter(p -> p.genre
                        == PartieContenu.Genre.RESULTAT_OUTIL)
                .toList();
        assertEquals("l1", resultats.get(0).idAppel);
        assertEquals("l2", resultats.get(1).idAppel);
        assertEquals("l3", resultats.get(2).idAppel);
        assertEquals("w1", resultats.get(3).idAppel);
        assertEquals("w2", resultats.get(4).idAppel);
    }

    @Test
    void leModePlanSeulRefuseLesEcrituresAvecUnResultatQuiLeDit() {
        OutilMemoire ecriture = new OutilMemoire("write_file", true,
                "written");
        OutilMemoire lecture = new OutilMemoire("read_file", false,
                "ok");
        MessageAgent pas = new MessageAgent(
                MessageAgent.Role.ASSISTANT,
                List.of(
                        PartieContenu.appel("l1", "read_file", "{}"),
                        PartieContenu.appel("w1", "write_file", "{}")));
        FournisseurScripte fournisseur = new FournisseurScripte(
                List.of(pas, MessageAgent.assistant("je décris")));
        BoucleAgent boucle = boucle(fournisseur,
                new ExecuteurOutils(
                        new PortMemoire(lecture, ecriture)), null);
        HistoriqueAgent historique = HistoriqueAgent.vide();
        BoucleAgent.ResultatTour resultat = boucle.tour("plan",
                historique, Permissions.Mode.PLAN_SEULEMENT, null,
                null, CancelToken.NEVER, null);
        assertEquals(EvenementAgent.RaisonFin.TERMINE,
                resultat.raison);
        assertEquals(1, lecture.invocations.size(),
                "la lecture passe : elle ne mute rien");
        assertEquals(0, ecriture.invocations.size(),
                "l'écriture ne s'exécute PAS");
        PartieContenu refus = dernierResultat(historique);
        assertTrue(refus.erreur);
        assertTrue(refus.texte.contains("Plan-only"));
        assertTrue(refus.texte.contains("Describe"));
    }

    @Test
    void leGateRefuseEtLeDefautEstSur() {
        OutilMemoire ecriture = new OutilMemoire("write_file", true,
                "written");
        MessageAgent pas = new MessageAgent(
                MessageAgent.Role.ASSISTANT,
                List.of(PartieContenu.appel("w1", "write_file",
                        "{\"path\":\"a\"}")));
        FournisseurScripte fournisseur = new FournisseurScripte(
                List.of(pas, MessageAgent.assistant("compris")));
        BoucleAgent boucle = boucle(fournisseur,
                new ExecuteurOutils(new PortMemoire(ecriture)), null);
        HistoriqueAgent historique = HistoriqueAgent.vide();
        BoucleAgent.ResultatTour resultat = boucle.tour("écris",
                historique, Permissions.Mode.DEMANDE_CHAQUE,
                demande -> Permissions.Decision.REFUSEE, null,
                CancelToken.NEVER, null);
        assertEquals(EvenementAgent.RaisonFin.TERMINE,
                resultat.raison);
        assertEquals(0, ecriture.invocations.size());
        assertTrue(dernierResultat(historique).texte
                .contains("declined"));
    }

    @Test
    void leGateAccordeEtLeDiffEstPasseALaDemande() {
        OutilMemoire ecriture = new OutilMemoire("write_file", true,
                "written");
        MessageAgent pas = new MessageAgent(
                MessageAgent.Role.ASSISTANT,
                List.of(PartieContenu.appel("w1", "write_file",
                        "{\"path\":\"src/a.txt\"}")));
        FournisseurScripte fournisseur = new FournisseurScripte(
                List.of(pas, MessageAgent.assistant("fait")));
        BoucleAgent boucle = boucle(fournisseur,
                new ExecuteurOutils(new PortMemoire(ecriture)), null);
        List<Permissions.DemandeEcriture> demandes =
                new ArrayList<>();
        BoucleAgent.ResultatTour resultat = boucle.tour("écris",
                HistoriqueAgent.vide(),
                Permissions.Mode.DEMANDE_CHAQUE,
                demande -> {
                    demandes.add(demande);
                    return Permissions.Decision.ACCORDEE;
                }, null, CancelToken.NEVER, null);
        assertEquals(EvenementAgent.RaisonFin.TERMINE,
                resultat.raison);
        assertEquals(1, ecriture.invocations.size());
        assertEquals(1, demandes.size());
        assertEquals("src/a.txt", demandes.get(0).cible,
                "l'approbation porte sur la cible nommée");
        assertTrue(demandes.get(0).diffApercu
                        .contains("write_file"),
                "et sur le diff déplié de l'édition");
    }

    @Test
    void laReparationAvantLeTourApparieLesAppelsInterrompus() {
        OutilMemoire lecture = new OutilMemoire("read_file", false,
                "ok");
        // historique chargé d'une session interrompue : appel sans
        // réponse
        HistoriqueAgent historique = new HistoriqueAgent(List.of(
                MessageAgent.utilisateur("fais"),
                new MessageAgent(MessageAgent.Role.ASSISTANT,
                        List.of(PartieContenu.appel("a1",
                                "read_file", "{}")))));
        FournisseurScripte fournisseur = new FournisseurScripte(
                List.of(MessageAgent.assistant("reparti")));
        BoucleAgent boucle = boucle(fournisseur,
                new ExecuteurOutils(new PortMemoire(lecture)), null);
        BoucleAgent.ResultatTour resultat = boucle.tour("continue",
                historique, Permissions.Mode.ACCEPTATION_AUTO, null,
                null, CancelToken.NEVER, null);
        assertEquals(EvenementAgent.RaisonFin.TERMINE,
                resultat.raison);
        // le message utilisateur d'origine, le résultat interrompu
        // inséré, le message du tour, la réponse
        List<MessageAgent> messages = historique.messages();
        boolean resultatInterrompu = messages.stream()
                .flatMap(m -> m.parties.stream())
                .anyMatch(p -> p.genre
                        == PartieContenu.Genre.RESULTAT_OUTIL
                        && "a1".equals(p.idAppel) && p.erreur);
        assertTrue(resultatInterrompu,
                "l'appel interrompu a reçu son résultat honnête "
                        + "AVANT la requête — le fournisseur n'aura "
                        + "pas à refuser la conversation");
    }

    @Test
    void lusageSommeToutesLesRequetesDuTour() {
        OutilMemoire lecture = new OutilMemoire("read_file", false,
                "ok");
        FournisseurScripte fournisseur = new FournisseurScripte(
                List.of(
                        new MessageAgent(MessageAgent.Role.ASSISTANT,
                                List.of(PartieContenu.appel("a1",
                                        "read_file", "{}"))),
                        MessageAgent.assistant("fini")),
                List.of(new UsageLlm(100, 10, 250),
                        new UsageLlm(120, 5, 400)),
                java.util.Arrays.asList(null, null));
        BoucleAgent boucle = boucle(fournisseur,
                new ExecuteurOutils(new PortMemoire(lecture)), null);
        HistoriqueAgent historique = HistoriqueAgent.vide();
        BoucleAgent.ResultatTour resultat = boucle.tour("fais",
                historique, Permissions.Mode.ACCEPTATION_AUTO, null,
                null, CancelToken.NEVER, null);
        assertEquals(220, resultat.usage.entrees,
                "les DEUX requêtes du tour somment leurs entrées");
        assertEquals(15, resultat.usage.sorties);
        assertEquals(650, resultat.usage.lecturesCache,
                "les lectures de cache sont comptées et exposées");
    }

    private static PartieContenu dernierResultat(
            HistoriqueAgent historique) {
        List<PartieContenu> tous = historique.messages().stream()
                .flatMap(m -> m.parties.stream())
                .filter(p -> p.genre
                        == PartieContenu.Genre.RESULTAT_OUTIL)
                .toList();
        return tous.get(tous.size() - 1);
    }

    @Test
    void lordreDesResultatsSuitLordreDesAppelsDuModele() {
        // couvert par le test de parallélisme ; ce test épingle la
        // propriété sur un cas séquentiel simple pour le diagnostic
        OutilMemoire lecture = new OutilMemoire("read_file", false,
                "premier");
        OutilMemoire lecture2 = new OutilMemoire("list_dir", false,
                "second");
        MessageAgent pas = new MessageAgent(
                MessageAgent.Role.ASSISTANT,
                List.of(
                        PartieContenu.appel("l1", "list_dir", "{}"),
                        PartieContenu.appel("l2", "read_file", "{}")));
        FournisseurScripte fournisseur = new FournisseurScripte(
                List.of(pas, MessageAgent.assistant("fini")));
        BoucleAgent boucle = boucle(fournisseur,
                new ExecuteurOutils(
                        new PortMemoire(lecture, lecture2)), null);
        HistoriqueAgent historique = HistoriqueAgent.vide();
        boucle.tour("dans l'ordre", historique,
                Permissions.Mode.ACCEPTATION_AUTO, null, null,
                CancelToken.NEVER, null);
        List<PartieContenu> resultats = historique.messages().stream()
                .flatMap(m -> m.parties.stream())
                .filter(p -> p.genre
                        == PartieContenu.Genre.RESULTAT_OUTIL)
                .toList();
        assertEquals(List.of("l1", "l2"),
                resultats.stream().map(p -> p.idAppel).toList());
        assertEquals("second", resultats.get(0).texte);
        assertEquals("premier", resultats.get(1).texte);
        assertFalse(resultats.get(0).erreur);
    }
}
