package jo.joide.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Le parseur SSE maison contre la grammaire du format : trames
 * nommées, données multi-lignes, commentaires de battement de cœur,
 * fin sans ligne vide — les trames que rejouent tous les tests des
 * adaptateurs.
 */
class ParseurSseTest {

    @Test
    void uneTrameSimpleAvecEvenementEtDonnees() throws IOException {
        String flux = "event: message_start\n"
                + "data: {\"type\":\"message_start\"}\n\n";
        List<TransportHttp.TrameSse> trames = ParseurSse.trames(
                new StringReader(flux));
        assertEquals(1, trames.size());
        assertEquals("message_start", trames.get(0).evenement);
        assertEquals("{\"type\":\"message_start\"}",
                trames.get(0).donnees);
    }

    @Test
    void lesDonneesMultiLignesSeConcatenentAvecSaut() throws IOException {
        String flux = "data: premiere ligne\ndata: seconde\n\n";
        List<TransportHttp.TrameSse> trames = ParseurSse.trames(
                new StringReader(flux));
        assertEquals(1, trames.size());
        assertEquals("premiere ligne\nseconde", trames.get(0).donnees);
        assertNull(trames.get(0).evenement);
    }

    @Test
    void lesCommentairesSontIgnoresJamaisUneErreur() throws IOException {
        String flux = ": ping\n: keep-alive\n\ndata: ok\n\n";
        List<TransportHttp.TrameSse> trames = ParseurSse.trames(
                new StringReader(flux));
        assertEquals(1, trames.size());
        assertEquals("ok", trames.get(0).donnees);
    }

    @Test
    void plusieursTramesDansLOrdre() throws IOException {
        String flux = "event: un\ndata: a\n\nevent: deux\ndata: b\n\n"
                + "data: c\n\n";
        List<TransportHttp.TrameSse> trames = ParseurSse.trames(
                new StringReader(flux));
        assertEquals(3, trames.size());
        assertEquals("a", trames.get(0).donnees);
        assertEquals("b", trames.get(1).donnees);
        assertEquals("c", trames.get(2).donnees);
        assertEquals("deux", trames.get(1).evenement);
    }

    @Test
    void uneFinSansLigneVideLivrelaDerniereTrame() throws IOException {
        String flux = "data: parti\ndata: [DONE]";
        List<TransportHttp.TrameSse> trames = ParseurSse.trames(
                new StringReader(flux));
        assertEquals(1, trames.size());
        assertEquals("parti\n[DONE]", trames.get(0).donnees);
    }

    @Test
    void lesLignesSansDeuxPointsSontDesChampsSansValeur()
            throws IOException {
        // « retry: 3000 » a deux points ; une ligne nue « data »
        // existe aussi dans la grammaire — donnée vide
        String flux = "data\n\n";
        List<TransportHttp.TrameSse> trames = ParseurSse.trames(
                new StringReader(flux));
        assertEquals(1, trames.size());
        assertEquals("", trames.get(0).donnees);
        assertTrue(trames.get(0).donnees.isEmpty());
    }

    @Test
    void lesHeadersSseReelsDOpenAiSeParsent() throws IOException {
        String flux = "data: {\"id\":\"chatcmpl-1\",\"choices\":"
                + "[{\"delta\":{\"role\":\"assistant\"}}]}\n\n"
                + "data: {\"id\":\"chatcmpl-1\",\"choices\":"
                + "[{\"delta\":{\"content\":\"Bonjour\"}}]}\n\n"
                + "data: {\"id\":\"chatcmpl-1\",\"choices\":"
                + "[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: [DONE]\n\n";
        List<TransportHttp.TrameSse> trames = ParseurSse.trames(
                new StringReader(flux));
        assertEquals(4, trames.size());
        assertEquals("[DONE]", trames.get(3).donnees);
    }

    @Test
    void unFluxVideNeDonneAucuneTrame() throws IOException {
        assertEquals(0, ParseurSse.trames(new StringReader("")).size());
        assertEquals(0, ParseurSse.trames(new StringReader("\n\n\n"))
                .size());
    }

    @Test
    void lesMajusculesEtEspacesDesChampsSontToleres() throws IOException {
        // « DATA: x » n'existe pas dans la grammaire (minuscules),
        // mais l'espace après le deux-points fait partie du format
        String flux = "data:avec espace\n\n";
        List<TransportHttp.TrameSse> trames = ParseurSse.trames(
                new StringReader(flux));
        assertEquals("avec espace", trames.get(0).donnees);
    }

    @Test
    void lesTramesAnthropiquesSeParsent() throws IOException {
        String flux = "event: content_block_delta\n"
                + "data: {\"type\":\"content_block_delta\",\"index\":0,"
                + "\"delta\":{\"type\":\"text_delta\",\"text\":\"Salut\"}}\n\n";
        List<TransportHttp.TrameSse> trames = ParseurSse.trames(
                new StringReader(flux));
        assertEquals("content_block_delta", trames.get(0).evenement);
        assertTrue(trames.get(0).donnees.contains("Salut"));
    }
}
