package jo.joide.app.android.atelier.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.joide.agent.HistoriqueAgent;
import jo.joide.agent.SessionsAgent;
import jo.joide.agent.UsageLlm;
import jo.joide.app.android.atelier.agent.ConversationsAgent.Resume;

/**
 * Les conversations de l'agent éprouvées sur un vrai répertoire
 * temporaire (lot S, ADR-064) : une conversation = un fichier, le
 * titre vient du premier message, l'ordre va du plus récent au plus
 * ancien, le plafond évique la plus ancienne, la migration du lot P
 * ne perd ni ne duplique, un fichier corrompu n'emporte pas
 * l'historique.
 */
class ConversationsAgentTest {

    @Test
    void leRepertoireVideRendUneIdentiteNeuveEtAucunResume(
            @TempDir Path dossier) {
        ConversationsAgent conversations =
                new ConversationsAgent(dossier, horloge(1_000));
        assertTrue(conversations.lister().isEmpty());
        assertEquals("1000", conversations.nouvelleIdentite());
        assertEquals("1000", conversations.activeOuNeuve());
    }

    @Test
    void uneConversationEcrireLireSupprimer(@TempDir Path dossier) {
        ConversationsAgent conversations =
                new ConversationsAgent(dossier, horloge(1_000));
        SessionsAgent.Session session = new SessionsAgent.Session(
                "ANTHROPIC", "claude-sonnet-4-5",
                List.of("corrige le build", "C'est corrigé."),
                HistoriqueAgent.vide().messages(),
                java.util.Set.of(), new UsageLlm(10, 5, 0));
        conversations.ecrire("1000", session);

        List<Resume> resumes = conversations.lister();
        assertEquals(1, resumes.size());
        assertEquals("1000", resumes.get(0).id);
        assertEquals("corrige le build", resumes.get(0).titre);
        assertEquals(1, resumes.get(0).echanges);

        SessionsAgent.Session relue = conversations.lire("1000");
        assertNotNull(relue);
        assertEquals(List.of("corrige le build", "C'est corrigé."),
                relue.transcript);

        assertTrue(conversations.supprimer("1000"));
        assertFalse(conversations.supprimer("1000"));
        assertTrue(conversations.lister().isEmpty());
    }

    @Test
    void lordreVaDuPlusRecentAuPlusAncienEtActiveSuit(
            @TempDir Path dossier) throws IOException {
        ConversationsAgent conversations =
                new ConversationsAgent(dossier, horloge(1_000));
        SessionsAgent.Session session = session("premier message");
        conversations.ecrire("1000", session);
        toucher(dossier.resolve("1000.json"), 1_000_000);
        conversations.ecrire("2000", session);
        toucher(dossier.resolve("2000.json"), 2_000_000);

        List<Resume> resumes = conversations.lister();
        assertEquals(2, resumes.size());
        assertEquals("2000", resumes.get(0).id);
        assertEquals("2000", conversations.activeOuNeuve());
    }

    @Test
    void leTitreEstLaPremiereLigneBornee(@TempDir Path dossier) {
        ConversationsAgent conversations =
                new ConversationsAgent(dossier, horloge(1_000));
        StringBuilder longMessage = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            longMessage.append("mot-").append(i).append(' ');
        }
        longMessage.append("\nla suite du message");
        conversations.ecrire("1000", session(longMessage.toString()));

        Resume resume = conversations.lister().get(0);
        assertEquals(ConversationsAgent.TITRE_MAX,
                resume.titre.length());
        assertTrue(resume.titre.endsWith("…"));
        assertFalse(resume.titre.contains("\n"));
    }

    @Test
    void uneConversationVideASansTitre(@TempDir Path dossier) {
        ConversationsAgent conversations =
                new ConversationsAgent(dossier, horloge(1_000));
        conversations.ecrire("1000", session(""));
        assertEquals("", conversations.lister().get(0).titre);
        assertEquals(0, conversations.lister().get(0).echanges);
    }

    @Test
    void lePlafondEviqueLaPlusAncienne(@TempDir Path dossier)
            throws IOException {
        ConversationsAgent conversations =
                new ConversationsAgent(dossier, horloge(1_000));
        for (int i = 0;
             i < ConversationsAgent.CONVERSATIONS_MAX + 3; i++) {
            conversations.ecrire(String.valueOf(i), session("n°" + i));
            toucher(dossier.resolve(i + ".json"), (i + 1) * 1_000L);
        }
        List<Resume> resumes = conversations.lister();
        assertEquals(ConversationsAgent.CONVERSATIONS_MAX,
                resumes.size());
        // les plus anciennes (0, 1, 2) sont parties
        assertNull(conversations.lire("0"));
        assertNull(conversations.lire("2"));
        assertNotNull(conversations.lire("3"));
    }

    @Test
    void laMigrationDuLotPDeplaceSansDupliquer(@TempDir Path dossier)
            throws IOException {
        Path ancienne = dossier.resolve("session.json");
        SessionsAgent.ecrire(ancienne, session("l'ancienne conversation"));
        toucher(ancienne, 1_234_567);
        ConversationsAgent conversations =
                new ConversationsAgent(dossier.resolve("conversations"),
                        horloge(1_000));
        conversations.migrerSessionUnique(ancienne);

        assertFalse(Files.exists(ancienne));
        List<Resume> resumes = conversations.lister();
        assertEquals(1, resumes.size());
        assertEquals("l'ancienne conversation",
                resumes.get(0).titre);
        // le même fichier qui reparaît (même instant de modification)
        // ne duplique pas : la cible existe, la source part
        SessionsAgent.ecrire(ancienne, session("encore"));
        toucher(ancienne, 1_234_567);
        conversations.migrerSessionUnique(ancienne);
        assertEquals(1, conversations.lister().size());
    }

    @Test
    void unFichierCorrompuNEmportePasLHistorique(
            @TempDir Path dossier) throws IOException {
        ConversationsAgent conversations =
                new ConversationsAgent(dossier, horloge(1_000));
        conversations.ecrire("1000", session("la bonne"));
        Files.writeString(dossier.resolve("corrompue.json"),
                "{pas du json");
        Files.writeString(dossier.resolve("etranger.txt"), "x");

        List<Resume> resumes = conversations.lister();
        assertEquals(1, resumes.size());
        assertEquals("1000", resumes.get(0).id);
    }

    @Test
    void uneIdentiteHostileNEchappePasAuRepertoire(
            @TempDir Path dossier) {
        ConversationsAgent conversations =
                new ConversationsAgent(dossier, horloge(1_000));
        conversations.ecrire("../../ailleurs", session("x"));
        // le nom neutralisé (aucun point, aucun séparateur) reste
        // DANS le répertoire
        assertTrue(Files.exists(
                dossier.resolve("______ailleurs.json")));
    }

    // ------------------------------------------------------------------
    // Aides
    // ------------------------------------------------------------------

    private static SessionsAgent.Session session(String premier) {
        return new SessionsAgent.Session(
                "ANTHROPIC", "claude-sonnet-4-5",
                premier.isEmpty() ? List.of()
                        : List.of(premier, "la réponse"),
                HistoriqueAgent.vide().messages(),
                java.util.Set.of(), new UsageLlm(10, 5, 0));
    }

    private static void toucher(Path fichier, long epochMs)
            throws IOException {
        Files.setLastModifiedTime(fichier,
                java.nio.file.attribute.FileTime.fromMillis(epochMs));
    }

    private static Clock horloge(long fixe) {
        return Clock.fixed(Instant.ofEpochMilli(fixe),
                ZoneId.of("UTC"));
    }
}
