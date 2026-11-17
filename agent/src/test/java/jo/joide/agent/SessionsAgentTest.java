package jo.joide.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Les sessions à double moitié (ADR-É9, leçon 14) : écriture entière
 * et atomique, relecture au format joide.session/1, tolérance
 * SIGNALÉE — un fichier cassé lève une SessionIllisible, jamais une
 * réparation silencieuse.
 */
class SessionsAgentTest {

    @TempDir
    Path bac;

    @Test
    void allerRetourCompletAvecSignaturesEtCompaction()
            throws IOException {
        SessionsAgent.Session session = new SessionsAgent.Session(
                "anthropic", "claude-x",
                List.of("utilisateur: fais", "assistant: fait"),
                List.of(
                        MessageAgent.utilisateur("fais"),
                        new MessageAgent(MessageAgent.Role.ASSISTANT,
                                List.of(
                                        PartieContenu.raisonnement(
                                                "je pense", "sig-1"),
                                        PartieContenu.texte("fait"),
                                        PartieContenu.appel("a1",
                                                "read_file",
                                                "{\"path\":\"a\"}"))),
                        new MessageAgent(
                                MessageAgent.Role.UTILISATEUR,
                                List.of(PartieContenu.resultat("a1",
                                        "contenu", false)))),
                Set.of("a0"),
                new UsageLlm(100, 20, 300));
        Path cible = bac.resolve("session.json");
        SessionsAgent.ecrire(cible, session);
        SessionsAgent.Session relue = SessionsAgent.lire(cible);
        assertEquals("anthropic", relue.fournisseur);
        assertEquals("claude-x", relue.modele);
        assertEquals(List.of("utilisateur: fais", "assistant: fait"),
                relue.transcript);
        assertEquals(3, relue.historique.size());
        PartieContenu raisonnement = relue.historique
                .get(1).parties.get(0);
        assertEquals("sig-1", raisonnement.signature,
                "la signature opaque survit à l'aller-retour");
        assertEquals(Set.of("a0"), relue.compactes);
        assertEquals(300, relue.usage.lecturesCache);
    }

    @Test
    void unJsonCasseEstSignaleJamaisRepare() throws IOException {
        Path cible = bac.resolve("casse.json");
        Files.write(cible, "{\"schema\": \"joide.session/1\", "
                        .getBytes(StandardCharsets.UTF_8));
        assertThrows(SessionsAgent.SessionIllisible.class,
                () -> SessionsAgent.lire(cible));
    }

    @Test
    void unSchemaInconnuEstRefuse() throws IOException {
        Path cible = bac.resolve("vieux.json");
        Files.write(cible, "{\"schema\":\"joide.session/9\"}"
                        .getBytes(StandardCharsets.UTF_8));
        SessionsAgent.SessionIllisible erreur = assertThrows(
                SessionsAgent.SessionIllisible.class,
                () -> SessionsAgent.lire(cible));
        assertTrue(erreur.getMessage().contains("joide.session/9"));
    }

    @Test
    void lecritureEstAtomiqueUnFichierTemporaireVoisin()
            throws IOException {
        Path cible = bac.resolve("session.json");
        SessionsAgent.ecrire(cible, new SessionsAgent.Session(
                "openai-compatible", "gpt-x", List.of(),
                List.of(MessageAgent.utilisateur("bonjour")),
                Set.of(), UsageLlm.zero()));
        assertTrue(Files.isRegularFile(cible));
        // aucun reste de fichier temporaire
        try (var fluide = Files.list(bac)) {
            assertEquals(1, fluide.count(),
                    "seul le fichier final existe après le renommage");
        }
        // réécrire par-dessus fonctionne (tour suivant)
        SessionsAgent.ecrire(cible, new SessionsAgent.Session(
                "openai-compatible", "gpt-x", List.of("encore"),
                List.of(MessageAgent.utilisateur("bonjour")),
                Set.of(), UsageLlm.zero()));
        assertEquals(1, SessionsAgent.lire(cible).transcript.size());
    }
}
